"""
Colab: qwen2.5:14b-instruct-q4_K_M trên 1 GPU T4 16GB — tối ưu thông lượng cho việc chấm 1000 câu.
Chạy MỘT LẦN cho MỖI notebook Colab (5 tài khoản = 5 node = 5 lần chạy).

    !python colab_qwen_ollama.py

Biến môi trường (tùy chọn, có default hợp lý cho T4 16GB):
    NODE_INDEX=1..5   số thứ tự node → in ra đúng AI_NODE_<n>_* cho .env (default: 1)
    NUM_PARALLEL=3    số slot suy luận song song của Ollama (default: 3).
    NUM_CTX=8192      context mỗi slot — PHẢI trùng ai.llm.*.num-ctx ở worker (default: 8192).
    MODEL=...         thay model nếu cần.

Ngân sách VRAM trên T4 16GB (qwen2.5-14b-instruct-q4_K_M ≈ 9.0GB weights):
    KV cache q8_0 (OLLAMA_KV_CACHE_TYPE) ≈ 0.19 MB/token/slot
      3 slot × 8192 ctx ≈ 1.6 GB/slot → 9.0 + 4.7 ≈ 13.7 GB + ~0.5–1 GB compute ≈ 14–15 GB ✓
      4 slot × 8192        ≈ 9.0 + 6.3 ≈ 15.3 GB + compute ≈ 16 GB  (sát trần — tự rủi)
      3 slot × 8192 q4_0 KV ≈ 9.0 + 2.4 ≈ 11.4 GB (tiết kiệm hơn nhưng KV q4 làm giảm chất lượng — tránh)
    Flash attention (OLLAMA_FLASH_ATTENTION=1) giảm bộ nhớ phụ + tăng tốc prefill.

Vì sao giữ 8192 thay vì chạy 4096 × nhiều slot hơn: prompt few_cot ~2–4K token, 4096 dễ
truncate → retry → CHẬM HƠN. 8192 + 3 slot là điểm cân bằng tốt nhất trên T4 16GB.

Quy trình:
  1. Kiểm tra GPU (cảnh báo nếu không phải T4 16GB) và dọn process cũ
  2. Cài phụ thuộc (zstd, ollama, cloudflared, pip deps)
  3. ollama serve với NUM_PARALLEL slot, KV q8_0, flash attention, keep_alive=-1
  4. ollama pull model
  5. WARM-UP ĐỒNG THỜI NUM_PARALLEL slot với đúng num_ctx của worker (chặn lỗi OOM ngay từ đầu,
     tránh việc slot chỉ được nạp khi request đầu tiên tới)
  6. Proxy nội bộ (Bearer + /health + /api/tags + /api/version) + cloudflared quick tunnel
  7. Verify end-to-end qua URL công khai + in nguyên khối cấu hình .env cho worker

Vận hành với 5 tài khoản:
  - Chạy script trên cả 5 runtime (NODE_INDEX=1..5).
  - Mỗi runtime in ra 1 block AI_NODE_n_URL/TOKEN/ENABLED → dán vào .env của server-ai-worker.
  - Đặt AI_DEMO_EVAL_CONCURRENCY=10 (5 node × 3 slot, chừa 1 slot/node đệm) và giữ nguyên
    max-concurrency: 3 mỗi node trong application.yml (đúng bằng NUM_PARALLEL).
  - Quick tunnel đổi URL mỗi lần runtime reset → cập nhật lại .env và restart worker.
  - Colab free có thể preempt GPU bất cứ lúc nào; run AI giữa chừng chết thì worker tự đánh dấu
    error/failed — chạy lại (các câu đã xong vẫn được lưu trong ntt_demo_eval_items).
"""

import os
import re
import secrets
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor

import requests

MODEL = os.environ.get("MODEL", "qwen2.5:14b-instruct-q4_K_M")
NUM_CTX = int(os.environ.get("NUM_CTX", "8192"))
NUM_PARALLEL = int(os.environ.get("NUM_PARALLEL", "3"))
NODE_INDEX = int(os.environ.get("NODE_INDEX", "1"))
PROXY_PORT = 8000
OLLAMA = "http://127.0.0.1:11434"
TOKEN = secrets.token_urlsafe(24)   # 32 ký tự [A-Za-z0-9_-] — đúng format worker


def run(*args):
    subprocess.run(list(args), check=True)


def pkill(pattern):
    subprocess.run(["pkill", "-f", pattern],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def gpu_info():
    try:
        out = subprocess.check_output(
            ["nvidia-smi", "--query-gpu=name,memory.total,memory.used", "--format=csv,noheader"],
            text=True).strip()
        return out
    except Exception:
        return "nvidia-smi failed"


print("== GPU ==", gpu_info())
try:
    mem_mib = int(re.search(r"(\d+)\s*MiB", gpu_info()).group(1))
    if mem_mib < 15000:
        print(f"CẢNH BÁO: GPU chỉ có {mem_mib} MiB, không đủ chuẩn T4 16GB — kết quả có thể tràn VRAM.")
except Exception:
    pass

print("== Dọn process cũ (nếu chạy lại) ==")
pkill("ollama serve")
pkill("uvicorn proxy:app")
pkill("cloudflared tunnel")

print("== Cài zstd / pciutils / ollama / cloudflared / pip deps ==")
run("apt-get", "install", "-y", "-qq", "zstd", "pciutils")
subprocess.run("curl -fsSL https://ollama.com/install.sh | sh", shell=True, check=True)
run("wget", "-q", "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64",
    "-O", "cloudflared")
run("chmod", "+x", "cloudflared")
run(sys.executable, "-m", "pip", "install", "-q", "fastapi", "uvicorn", "httpx")

print(f"== Khởi động Ollama: NUM_PARALLEL={NUM_PARALLEL}, KV q8_0, flash attention, keep_alive=-1 ==")
ollama_env = {**os.environ,
              "OLLAMA_HOST": "127.0.0.1:11434",
              "OLLAMA_NUM_PARALLEL": str(NUM_PARALLEL),
              "OLLAMA_FLASH_ATTENTION": "1",
              "OLLAMA_KV_CACHE_TYPE": "q8_0",
              "OLLAMA_MAX_LOADED_MODELS": "1",
              "OLLAMA_KEEP_ALIVE": "-1"}
ollama_log = open("ollama.log", "w")
subprocess.Popen(["ollama", "serve"], env=ollama_env,
                 stdout=ollama_log, stderr=subprocess.STDOUT)

for _ in range(60):
    try:
        requests.get(OLLAMA + "/api/version", timeout=2)
        print("Ollama đã sẵn sàng")
        break
    except Exception:
        time.sleep(1)
else:
    print("Ollama KHỞI ĐỘNG THẤT BẠI — đoạn cuối ollama.log:")
    print(open("ollama.log").read()[-3000:])
    sys.exit(1)

print("== Pull model (~9GB, lần đầu mất vài phút) ==")
subprocess.run(["ollama", "pull", MODEL], check=True)

print(f"== Warm-up {NUM_PARALLEL} slot ĐỒNG THỜI với num_ctx={NUM_CTX} (trùng worker) ==")
t0 = time.time()
def warm(slot):
    r = requests.post(OLLAMA + "/api/chat",
                      json={"model": MODEL,
                            "messages": [{"role": "user", "content": f"slot {slot}: trả lời đúng 1 từ ok"}],
                            "options": {"num_ctx": NUM_CTX, "temperature": 0.0},
                            "keep_alive": -1},
                      timeout=600)
    return slot, r.status_code
with ThreadPoolExecutor(max_workers=NUM_PARALLEL) as pool:
    for slot, status in pool.map(warm, range(1, NUM_PARALLEL + 1)):
        print(f"  warm-up slot {slot}: HTTP {status}")
print(f"Warm-up xong sau {time.time() - t0:.0f}s (KV đã được cấp phát đủ {NUM_PARALLEL} slot)")
subprocess.run(["nvidia-smi", "--query-gpu=memory.used,memory.total", "--format=csv"], check=False)
print("Nếu dòng trên >15GB hoặc có slot warm-up != 200 → tải NUM_PARALLEL=2 và chạy lại.")

print("== Mở proxy nội bộ (Bearer + /health + /api/tags + /api/version) ==")
PROXY = '''
import os, time
import httpx
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import Response, JSONResponse

TOKEN = os.environ["WORKER_TOKEN"]
OLLAMA = "http://127.0.0.1:11434"

_cache = {"t": 0.0, "version": None}


async def _ollama_version():
    now = time.time()
    if now - _cache["t"] > 10.0:
        try:
            async with httpx.AsyncClient(timeout=2) as c:
                r = await c.get(OLLAMA + "/api/version")
            _cache["version"] = r.json().get("version")
        except Exception:
            _cache["version"] = None
        _cache["t"] = now
    return _cache["version"]


app = FastAPI()


@app.get("/health")
async def health():
    # Luôn 200 (semantics "proxy reachable" với worker); ollama_up báo trạng thái thật.
    version = await _ollama_version()
    return {"ok": True, "ollama_up": version is not None,
            "runtime_version": version or "unknown"}


@app.get("/api/tags")
async def tags(req: Request):
    if req.headers.get("authorization") != f"Bearer {TOKEN}":
        raise HTTPException(401, "unauthorized")
    try:
        async with httpx.AsyncClient(timeout=5) as c:
            r = await c.get(OLLAMA + "/api/tags")
        return JSONResponse(r.json(), status_code=r.status_code)
    except Exception:
        return JSONResponse({"error": "ollama unreachable"}, status_code=502)


@app.get("/api/version")
async def version(req: Request):
    if req.headers.get("authorization") != f"Bearer {TOKEN}":
        raise HTTPException(401, "unauthorized")
    try:
        async with httpx.AsyncClient(timeout=5) as c:
            r = await c.get(OLLAMA + "/api/version")
        return JSONResponse(r.json(), status_code=r.status_code)
    except Exception:
        return JSONResponse({"error": "ollama unreachable"}, status_code=502)


@app.post("/api/chat")
async def chat(req: Request):
    if req.headers.get("authorization") != f"Bearer {TOKEN}":
        raise HTTPException(401, "unauthorized")
    try:
        body = await req.json()
    except Exception:
        return JSONResponse({"error": "body không phải JSON"}, status_code=400)
    body["stream"] = False
    try:
        async with httpx.AsyncClient(timeout=180) as c:
            r = await c.post(OLLAMA + "/api/chat", json=body)
        return Response(content=r.content, status_code=r.status_code,
                        media_type=r.headers.get("content-type", "application/json"))
    except httpx.TimeoutException:
        return JSONResponse({"error": "ollama timeout"}, status_code=504)
    except httpx.ConnectError:
        return JSONResponse({"error": "ollama unreachable (chưa chạy hoặc bị tắt)"}, status_code=502)
'''

with open("proxy.py", "w") as f:
    f.write(PROXY)

subprocess.Popen(["uvicorn", "proxy:app", "--host", "127.0.0.1", "--port", str(PROXY_PORT)],
                 env={**os.environ, "WORKER_TOKEN": TOKEN},
                 stdout=open("proxy.log", "w"), stderr=subprocess.STDOUT)
subprocess.Popen(["./cloudflared", "tunnel", "--url", f"http://127.0.0.1:{PROXY_PORT}"],
                 stdout=open("cf.log", "w"), stderr=subprocess.STDOUT)

url = None
for _ in range(60):
    time.sleep(1)
    try:
        m = re.search(r"https://[a-z0-9-]+\.trycloudflare\.com", open("cf.log").read())
        if m:
            url = m.group(0)
            break
    except FileNotFoundError:
        pass
if url is None:
    print("cloudflared CHƯA MỞ URL — đoạn cuối cf.log:")
    print(open("cf.log").read()[-2000:])
    sys.exit(1)

print("== Kiểm tra end-to-end qua URL công khai ==")
try:
    r = requests.get(url + "/health", timeout=30)
    print(f"GET  /health      -> {r.status_code} {r.text}")
    t0 = time.time()
    r = requests.post(url + "/api/chat",
                      headers={"Authorization": f"Bearer {TOKEN}"},
                      json={"model": MODEL,
                            "messages": [{"role": "user", "content": "Trả lời đúng 1 từ: ok"}],
                            "options": {"num_ctx": NUM_CTX, "temperature": 0.0},
                            "keep_alive": -1},
                      timeout=180)
    print(f"POST /api/chat    -> {r.status_code} ({time.time() - t0:.1f}s) "
          f"{(r.json().get('message') or {}).get('content', '').strip()}")
except Exception as e:
    print("KIỂM TRA END-TO-END THẤT BẠI:", e)

print()
print("=" * 64)
print(f"NODE {NODE_INDEX} — dán vào .env của server-ai-worker:")
print(f"AI_NODE_{NODE_INDEX}_URL={url}")
print(f"AI_NODE_{NODE_INDEX}_TOKEN={TOKEN}")
print(f"AI_NODE_{NODE_INDEX}_ENABLED=true")
print("=" * 64)
print("Sau khi có đủ 5 node (NODE_INDEX=1..5), .env cần:")
print("  AI_DEMO_EVAL_CONCURRENCY=10   # 5 node × 3 slot, chừa đệm")
print("  và application.yml giữ nguyên max-concurrency: 3 mỗi node (trùng NUM_PARALLEL).")
print("URL quick tunnel đổi mỗi lần runtime reset — nhớ cập nhật lại .env.")
