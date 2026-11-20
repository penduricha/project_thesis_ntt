"""
Colab: chạy qwen2.5:14b-instruct-q4_K_M qua Ollama — node cho server-ai-worker
(dùng cho cả question_check production lẫn demo đánh giá AI trong luận văn).

Cách dùng trên Colab (GPU T4 16GB là yêu cầu tối thiểu):
    !python colab_qwen_ollama.py
(hoặc upload file này, hoặc copy từng khối vào notebook cell)

Quy trình:
  1. Dọn process cũ (ollama serve / uvicorn / cloudflared) nếu chạy lại
  2. Cài phụ thuộc: zstd, ollama, cloudflared, fastapi+uvicorn+httpx
  3. Khởi động Ollama API trên 127.0.0.1:11434 với NUM_PARALLEL slot (mặc định 2)
  4. ollama pull model (~9GB)
  5. Warm-up: nạp model vào VRAM với ĐÚNG num_ctx mà worker gửi lên (8192)
     → request đầu tiên của worker không phải chờ nạp model, tránh nạp instance sai num_ctx
  6. Mở proxy nội bộ (biên Bearer token + /health + /api/tags + /api/version) rồi
     expose qua cloudflared quick tunnel
  7. Kiểm tra end-to-end qua URL công khai, in ra 3 dòng cấu hình cho .env

Lưu ý vận hành:
  - URL quick tunnel ĐỔI MỖI LẦN runtime Colab khởi động lại → phải cập nhật .env và restart worker.
  - OLLAMA_NUM_PARALLEL=2 + OLLAMA_KV_CACHE_TYPE=q8_0 + num_ctx 8192 ≈ 12-14GB VRAM trên T4 16GB.
    Đổi NUM_PARALLEL=3 chỉ khi warm-up xong nvidia-smi vẫn còn trống, và lúc đó đặt
    AI_DEMO_EVAL_CONCURRENCY=3 ở server-ai-worker.
  - Worker gọi /api/chat với keep_alive từ cấu hình (12h), ở đây env OLLAMA_KEEP_ALIVE=-1
    nên model không bao giờ bị unload giữa run 1000 câu.
"""

import os
import re
import secrets
import subprocess
import sys
import time

import requests

MODEL = "qwen2.5:14b-instruct-q4_K_M"
NUM_CTX = 8192               # PHẢI trùng ai.llm.*.num-ctx của server-ai-worker
NUM_PARALLEL = os.environ.get("NUM_PARALLEL", "2")   # đặt "3" sau khi xác nhận VRAM
PROXY_PORT = 8000
TOKEN = secrets.token_urlsafe(24)   # 32 ký tự [A-Za-z0-9_-] — hợp với format token của worker


def run(*args):
    subprocess.run(list(args), check=True)


def pkill(pattern):
    subprocess.run(["pkill", "-f", pattern],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


print("== nvidia-smi ==")
subprocess.run(["nvidia-smi"], check=False)

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

print(f"== Khởi động Ollama (NUM_PARALLEL={NUM_PARALLEL}, KV q8_0, flash attention, keep_alive=-1) ==")
ollama_env = {**os.environ,
              "OLLAMA_HOST": "127.0.0.1:11434",   # chỉ nghe nội bộ; truy cập ngoài qua proxy + Bearer
              "OLLAMA_NUM_PARALLEL": NUM_PARALLEL,
              "OLLAMA_FLASH_ATTENTION": "1",
              "OLLAMA_KV_CACHE_TYPE": "q8_0",     # KV ~0.2MB/token → 3 slot @8192 vẫn vừa T4
              "OLLAMA_MAX_LOADED_MODELS": "1",
              "OLLAMA_KEEP_ALIVE": "-1"}
ollama_log = open("ollama.log", "w")
subprocess.Popen(["ollama", "serve"], env=ollama_env,
                 stdout=ollama_log, stderr=subprocess.STDOUT)

for _ in range(60):
    try:
        requests.get("http://127.0.0.1:11434/api/version", timeout=2)
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

print(f"== Warm-up: nạp model với num_ctx={NUM_CTX} (trùng worker) ==")
t0 = time.time()
resp = requests.post("http://127.0.0.1:11434/api/chat",
                     json={"model": MODEL,
                           "messages": [{"role": "user", "content": "Trả lời đúng 1 từ: ok"}],
                           "options": {"num_ctx": NUM_CTX, "temperature": 0.0},
                           "keep_alive": -1},
                     timeout=600)
resp.raise_for_status()
print(f"Model sẵn sàng sau {time.time() - t0:.0f}s — trả lời:",
      (resp.json().get("message") or {}).get("content", "").strip())
subprocess.run(["nvidia-smi", "--query-gpu=memory.used,memory.total", "--format=csv"], check=False)

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
    # Luôn trả 200 để worker giữ semantics "proxy reachable";
    # trạng thái Ollama thật được báo qua field ollama_up (worker tự probe /api/tags, /api/version).
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
        # Chuyển nguyên trạng body Ollama (luôn là JSON) — không cần parse lại
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
    r = requests.post(url + "/api/chat",
                      headers={"Authorization": f"Bearer {TOKEN}"},
                      json={"model": MODEL,
                            "messages": [{"role": "user", "content": "Trả lời đúng 1 từ: ok"}],
                            "options": {"num_ctx": NUM_CTX, "temperature": 0.0},
                            "keep_alive": -1},
                      timeout=180)
    print(f"POST /api/chat    -> {r.status_code} {(r.json().get('message') or {}).get('content', '').strip()}")
except Exception as e:
    print("KIỂM TRA END-TO-END THẤT BẠI:", e)

print()
print("=" * 64)
print("AI_NODE_1_URL=" + url)
print("AI_NODE_1_TOKEN=" + TOKEN)
print("AI_NODE_1_ENABLED=true")
print("=" * 64)
print("Dán 3 dòng trên vào .env của server-ai-worker (AI_NODE_2..5 lặp lại cho node khác),")
print("restart server, mở node-colab.html bấm 'Kiểm tra'. URL quick tunnel đổi mỗi")
print("lần runtime khởi động lại — phải cập nhật .env sau mỗi lần Colab reset.")
