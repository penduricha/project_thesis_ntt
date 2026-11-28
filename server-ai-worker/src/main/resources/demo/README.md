# Module DEMO đánh giá AI trên bộ 1000 câu hỏi (`demo`)

**Chỉ dùng cho demo/nghiệm thu luận văn — không tích hợp vào pipeline production.**
Không đọc/ghi bảng ER v4 (`ntt_ai_tasks`, `ntt_ai_suggestions`, …); toàn bộ dữ liệu nằm ở
3 bảng riêng trong DB demo `ntt_demo_llm`:

- `ntt_demo_questions` — bộ câu hỏi gold (được seed tự động từ `classpath:demo/questions-1000.json`
  khi bảng trống; ép nạp lại: `POST /api/web/ai/demo/seed?force=true`)
- `ntt_demo_eval_runs` — một lần chạy đánh giá (mode `rule` | `ai`, status `running|done|stopped|failed`)
- `ntt_demo_eval_items` — dự đoán của từng câu trong một run (kèm latency, node, error_code)

## Cách mở trang demo

Chỉ dùng trên môi trường thử nghiệm cô lập với credential thử nghiệm riêng; không nhập token nội bộ production vào trình duyệt hoặc công khai cổng worker. JWT người dùng không được chấp nhận.

```bash
cd server-ai-worker && ./gradlew bootRun   # hoặc build & run bình thường
# → http://localhost:8082/api/demo-eval.html
```

Trang tĩnh tự chứa (inline CSS/JS), cùng origin với API `/api/web/ai/demo/*`; có ô nhập
API base URL nếu cần trỏ sang máy khác (mặc định `http://localhost:8082/api`).

## Hai chế độ chấm

| Chế độ | Đường chấm | Tốc độ | Yêu cầu |
|---|---|---|---|
| `rule` | `RulePreflightChecker` (tất định: stem trống / lộ đáp án / phương án trống / trùng phương án → `logic`|`distractor`) | ~1000 câu trong vài giây | không |
| `ai` | Tiền kiểm `RulePreflightChecker` + gọi Ollama qua `NodePool` (giống hệt pipeline `question_check`, gồm `few_cot` prompt, parse `AiResultParser`) | vài giây–vài phút / câu | ≥ 1 node Colab đang bật (trang `node-colab.html`) |

Dừng run: `POST /api/web/ai/demo/runs/{id}/stop` (cooperative giữa hai câu). Tối đa 2 lần
thử AI cho một câu; lỗi node (`LLM_UNREACHABLE/TIMEOUT/UNAUTHORIZED/HTTP_5XX`) bị loại khỏi
pool cho câu đó; 5 lần liên tiếp không lấy được node → run `failed`.

## Predict label — quy tắc gán nhãn

1. Không có suggestion (rule và AI đều sạch) ⇒ `none`.
2. Có suggestion ⇒ lấy suggestion **severity cao nhất** (hoà thì theo `seq_no`).
   `key_doubt` được quy đổi thành `logic` (gold taxonomy không tách `key_doubt`).
3. Câu lỗi kỹ thuật (LLM unreachable/schema…) ⇒ `error`, **loại khỏi** accuracy/confusion,
   đếm riêng `error_count`.

## Metrics

`GET /api/web/ai/demo/runs/{id}/metrics` — confusion matrix 5×5, per-label
Precision/Recall/F1/support, Macro-Precision/Recall/F1, Micro-F1 (= accuracy với
phân loại đơn nhãn), accuracy theo môn. Tính on-read từ `ntt_demo_eval_items` nên
luôn khớp dữ liệu hiện có (kể cả run bị dừng giữa chừng).

Baseline kỳ vọng của chế độ `rule`: bắt ~102/215 distractor (subset trùng phương án),
0/200 logic, 0/170 spelling, 0/165 grammar, 0 false-positive trên gold `none` — tức là
mốc dưới cố ý; giá trị thật của demo nằm ở chế độ `ai`.

## Gold dataset

`questions-1000.json` — sinh bởi `python_demo_llm/gen_eval_questions.py` (seed 20261120):
1000 câu, 10 môn (100 câu/môn), 5 nhãn (none 250 / distractor 215 / logic 200 /
spelling 170 / grammar 165), option-order mặc định theo file; **102/1000 câu có phương
án nhiễu trùng do thiết kế** (toàn bộ là gold `distractor`) — không sửa dữ liệu này khi
chưa muốn đổi gold label.
