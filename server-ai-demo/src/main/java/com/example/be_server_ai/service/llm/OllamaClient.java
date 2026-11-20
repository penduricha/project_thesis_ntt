package com.example.be_server_ai.service.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.example.be_server_ai.config.AiProperties;
import com.example.be_server_ai.dto.NodeCheckResponse;
import com.example.be_server_ai.enums.AiErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Gọi một node Colab: POST {url}/api/chat với Bearer token (cùng định dạng như notebook test_llm.ipynb).
 * Proxy FastAPI trên Colab chỉ có GET /health (không cần token) và POST /api/chat (cần token), nên việc
 * kiểm tra node dùng đúng hai đường đó.
 *
 * <p>Không bao giờ ghi token hoặc nội dung request vào log hay thông báo lỗi. Không theo chuyển hướng.
 */
@Component
public class OllamaClient {

    private final ObjectMapper om;
    private final AiProperties props;
    private final HttpClient http;

    public OllamaClient(ObjectMapper om, AiProperties props) {
        this.om = om;
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.getLlm().getConnectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Một lượt chat (stream=false). Ném LlmException với mã lỗi phù hợp ER nếu thất bại. */
    public ChatResult chat(String baseUrl, String token, ChatSpec spec) {
        AiProperties.Llm llm = props.getLlm();
        ObjectNode body = om.createObjectNode();
        body.put("model", llm.getModel());
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", spec.system());
        messages.addObject().put("role", "user").put("content", spec.user());
        body.set("format", spec.format());
        body.put("stream", false);
        body.put("keep_alive", llm.getKeepAlive());
        ObjectNode options = body.putObject("options");
        options.put("num_ctx", spec.options().getNumCtx());
        options.put("num_predict", spec.options().getNumPredict());
        options.put("temperature", spec.options().getTemperature());

        long t0 = System.nanoTime();
        HttpResponse<String> res = send(post(baseUrl + "/api/chat", token, toJson(body), llm.getReadTimeout()));
        long wallMs = (System.nanoTime() - t0) / 1_000_000;
        int sc = res.statusCode();

        if (sc == 401 || sc == 403) {
            throw new LlmException(AiErrorCode.LLM_UNAUTHORIZED, "Node từ chối token (HTTP " + sc + ").");
        }
        if (sc == 404 || sc == 530) {
            throw new LlmException(AiErrorCode.LLM_UNREACHABLE,
                    "Node không còn phản hồi (HTTP " + sc + "). Colab đã tắt hoặc URL đã đổi.");
        }
        if (sc == 504 || sc == 524) {
            throw new LlmException(AiErrorCode.LLM_TIMEOUT, "Cloudflare báo quá thời gian chờ (HTTP " + sc + ").");
        }
        if (sc == 429 || sc >= 500) {
            throw new LlmException(AiErrorCode.LLM_HTTP_5XX, "Node trả HTTP " + sc + ": " + snippet(res.body()));
        }
        if (sc >= 400) {
            throw new LlmException(AiErrorCode.LLM_BAD_REQUEST, "Node từ chối yêu cầu (HTTP " + sc + "): " + snippet(res.body()));
        }

        JsonNode root;
        try {
            root = om.readTree(res.body());
        } catch (JsonProcessingException e) {
            throw new LlmException(AiErrorCode.LLM_BAD_JSON, "Phản hồi của node không phải JSON.", snippet(res.body()));
        }
        String content = root.path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new LlmException(AiErrorCode.LLM_BAD_JSON, "Phản hồi không có message.content.", snippet(res.body()));
        }
        long evalNs = root.path("eval_duration").asLong(0);
        int evalCount = root.path("eval_count").asInt(0);
        double tps = evalNs > 0 ? evalCount / (evalNs / 1e9) : 0;
        return new ChatResult(
                content,
                root.path("model").asText(llm.getModel()),
                root.path("total_duration").asLong(0) / 1_000_000,
                root.path("load_duration").asLong(0) / 1_000_000,
                root.path("prompt_eval_count").asInt(0),
                evalCount,
                tps,
                wallMs,
                res.body());
    }

    /**
     * Kiểm tra một cặp URL + token mà KHÔNG nạp mô hình: GET /health, rồi POST /api/chat với thân "{}".
     * Proxy kiểm token trước khi đọc thân nên token sai trả 401; token đúng thì Ollama báo thiếu model (HTTP 400)
     * ngay lập tức.
     */
    public NodeCheckResponse check(String baseUrl, String token) {
        long t0 = System.nanoTime();
        int healthStatus;
        try {
            healthStatus = send(get(baseUrl + "/health", Duration.ofSeconds(10))).statusCode();
        } catch (LlmException e) {
            return new NodeCheckResponse(false, false, null, e.getMessage());
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (healthStatus != 200) {
            return new NodeCheckResponse(false, false, ms,
                    "Node trả HTTP " + healthStatus + ". Colab đã tắt hoặc URL đã đổi.");
        }
        try {
            int sc = send(post(baseUrl + "/api/chat", token, "{}", Duration.ofSeconds(15))).statusCode();
            if (sc == 401 || sc == 403) {
                return new NodeCheckResponse(true, false, ms, "Token bị từ chối (HTTP " + sc + ").");
            }
            if (sc >= 500) {
                return new NodeCheckResponse(true, true, ms,
                        "Token đúng nhưng proxy trả HTTP " + sc + ". Ollama trên Colab có thể chưa chạy.");
            }
            return new NodeCheckResponse(true, true, ms, null);
        } catch (LlmException e) {
            return new NodeCheckResponse(false, false, ms, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ nội bộ

    private HttpRequest get(String url, Duration timeout) {
        return HttpRequest.newBuilder(uri(url)).timeout(timeout).header("Accept", "application/json").GET().build();
    }

    private HttpRequest post(String url, String token, String json, Duration timeout) {
        return HttpRequest.newBuilder(uri(url))
                .timeout(timeout)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
    }

    private static URI uri(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new LlmException(AiErrorCode.LLM_UNREACHABLE, "URL node không đúng dạng.");
        }
    }

    private HttpResponse<String> send(HttpRequest req) {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException e) {
            throw new LlmException(AiErrorCode.LLM_UNREACHABLE, "Không kết nối được tới node (quá thời gian kết nối).");
        } catch (HttpTimeoutException e) {
            throw new LlmException(AiErrorCode.LLM_TIMEOUT, "Node không trả lời kịp.");
        } catch (IOException e) {
            throw new LlmException(AiErrorCode.LLM_UNREACHABLE,
                    "Không kết nối được tới node (" + e.getClass().getSimpleName() + ").");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(AiErrorCode.LLM_TIMEOUT, "Lượt gọi bị ngắt.");
        }
    }

    private String toJson(JsonNode node) {
        try {
            return om.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new LlmException(AiErrorCode.INTERNAL_ERROR, "Không tạo được JSON yêu cầu.");
        }
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 300 ? one.substring(0, 300) + "…" : one;
    }
}
