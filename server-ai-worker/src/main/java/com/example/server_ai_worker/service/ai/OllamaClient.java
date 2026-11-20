package com.example.server_ai_worker.service.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.server_ai_worker.config.AiProperties;
import com.example.server_ai_worker.dto.ai.NodeCheckResponse;
import com.example.server_ai_worker.enums.AiErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Gọi node Colab qua HTTPS: POST {url}/api/chat với Authorization: Bearer {token}.
 * Kiểm tra node bằng GET /health và POST /api/chat ("{}"), đồng thời thu thập cơ hội
 * modelDigest / runtimeVersion từ /health, /api/tags, /api/version nếu proxy có mở.
 *
 * <p>Không bao giờ ghi token hoặc nội dung đề thi đầy đủ vào thông báo lỗi. Không theo redirect.
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private static final long META_CACHE_TTL_MS = 10 * 60 * 1000L;

    private record NodeMeta(String modelDigest, String runtimeVersion, long fetchedAtMs) {
        boolean isFresh() {
            return System.currentTimeMillis() - fetchedAtMs < META_CACHE_TTL_MS;
        }
    }

    private final AiProperties props;
    private final ObjectMapper json;
    private final HttpClient http;
    private final Map<String, NodeMeta> metaCache = new ConcurrentHashMap<>();

    public OllamaClient(AiProperties props, ObjectMapper json) {
        this.props = props;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.getLlm().getConnectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public ChatResult chat(String baseUrl, String token, ChatSpec spec) {
        AiProperties.Llm llm = props.getLlm();
        String body = buildChatBody(spec);
        HttpRequest req = HttpRequest.newBuilder(uri(baseUrl + "/api/chat"))
                .timeout(llm.getReadTimeout())
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        long t0 = System.nanoTime();
        HttpResponse<String> res = send(req);
        long wallMs = (System.nanoTime() - t0) / 1_000_000;
        int sc = res.statusCode();
        String raw = res.body() == null ? "" : res.body();

        if (sc == 401 || sc == 403) {
            throw new LlmException(AiErrorCode.LLM_UNAUTHORIZED, "Node từ chối token (HTTP " + sc + ").");
        }
        if (sc == 404 || sc == 530) {
            throw new LlmException(AiErrorCode.LLM_UNREACHABLE,
                    "Node không còn phản hồi (HTTP " + sc + "). Colab đã tắt hoặc URL đã đổi.");
        }
        if (sc == 504 || sc == 524) {
            throw new LlmException(AiErrorCode.LLM_TIMEOUT,
                    "Cloudflare báo quá thời gian chờ (HTTP " + sc + ").");
        }
        if (sc == 429 || sc >= 500) {
            throw new LlmException(AiErrorCode.LLM_HTTP_5XX,
                    "Node trả HTTP " + sc + ": " + snippet(raw));
        }
        if (sc >= 400) {
            throw new LlmException(AiErrorCode.LLM_BAD_REQUEST,
                    "Node từ chối yêu cầu (HTTP " + sc + "): " + snippet(raw));
        }

        JsonNode root;
        try {
            root = json.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new LlmException(AiErrorCode.LLM_BAD_JSON,
                    "Phản hồi của node không phải JSON.", raw, false);
        }

        String content = root.path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new LlmException(AiErrorCode.LLM_BAD_JSON,
                    "Phản hồi Ollama thiếu message.content.", raw, false);
        }

        NodeMeta meta = resolveMetadata(baseUrl, token, root);
        return new ChatResult(
                content,
                raw,
                trimTo(root.path("model").asText(llm.getModel()), 100),
                meta.modelDigest(),
                meta.runtimeVersion(),
                root.path("eval_count").asInt(0),
                root.path("eval_duration").asLong(0L),
                root.path("prompt_eval_count").asInt(0),
                root.path("total_duration").asLong(0L),
                root.path("done_reason").asText(""),
                wallMs);
    }

    /**
     * Kiểm tra node mà không nạp mô hình:
     * 1) GET /health (kiểm tra kết nối + đọc metadata nếu có);
     * 2) POST /api/chat với thân "{}" (xác thực Bearer token trước khi Ollama đọc body);
     * 3) Thử nhẹ /api/tags & /api/version để lấy modelDigest/runtimeVersion (không bắt buộc proxy phải mở).
     */
    public NodeCheckResponse check(String baseUrl, String token) {
        long t0 = System.nanoTime();
        HttpResponse<String> healthRes;
        try {
            healthRes = send(HttpRequest.newBuilder(uri(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build());
        } catch (LlmException e) {
            return new NodeCheckResponse(false, false, null, null, null, null, e.getMessage());
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (healthRes.statusCode() != 200) {
            return new NodeCheckResponse(false, false, null, ms, null, null,
                    "Node trả HTTP " + healthRes.statusCode() + ". Colab đã tắt hoặc URL đã đổi.");
        }

        String healthDigest = null;
        String healthVersion = null;
        try {
            if (healthRes.body() != null && !healthRes.body().isBlank()) {
                JsonNode h = json.readTree(healthRes.body());
                healthDigest = normalizeDigest(textOrNull(h, "model_digest", "digest"));
                healthVersion = trimTo(textOrNull(h, "runtime_version", "ollama_version"), 30);
            }
        } catch (Exception ignored) {
        }

        try {
            int sc = send(HttpRequest.newBuilder(uri(baseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                    .build()).statusCode();
            if (sc == 401 || sc == 403) {
                return new NodeCheckResponse(true, false, null, ms, null, null,
                        "Token bị từ chối (HTTP " + sc + ").");
            }
            if (sc >= 500) {
                return new NodeCheckResponse(true, true, null, ms, null, null,
                        "Token đúng nhưng proxy trả HTTP " + sc + ". Ollama trên Colab có thể chưa chạy.");
            }

            TagProbe tags = probeTagsQuietly(baseUrl, token);
            String version = healthVersion != null ? healthVersion : probeVersionQuietly(baseUrl, token);
            String digest = healthDigest != null ? healthDigest : tags.digest();
            if (digest != null || version != null) {
                metaCache.put(baseUrl, new NodeMeta(digest, version, System.currentTimeMillis()));
            }
            return new NodeCheckResponse(true, true, tags.modelPresent(), ms, digest, version, null);
        } catch (LlmException e) {
            return new NodeCheckResponse(false, false, null, ms, null, null, e.getMessage());
        }
    }

    private NodeMeta resolveMetadata(String baseUrl, String token, JsonNode chatRoot) {
        NodeMeta cached = metaCache.get(baseUrl);
        if (cached != null && cached.isFresh()) {
            return cached;
        }
        String digest = normalizeDigest(textOrNull(chatRoot, "model_digest", "digest"));
        if (digest == null) {
            digest = probeTagsQuietly(baseUrl, token).digest();
        }
        String version = probeVersionQuietly(baseUrl, token);
        NodeMeta meta = new NodeMeta(digest, version, System.currentTimeMillis());
        metaCache.put(baseUrl, meta);
        return meta;
    }

    private record TagProbe(Boolean modelPresent, String digest) {
    }

    private TagProbe probeTagsQuietly(String baseUrl, String token) {
        try {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(uri(baseUrl + "/api/tags"))
                            .timeout(Duration.ofSeconds(2))
                            .header("Authorization", "Bearer " + token)
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() >= 200 && res.statusCode() < 300 && res.body() != null) {
                JsonNode root = json.readTree(res.body());
                String wanted = props.getLlm().getModel();
                for (JsonNode m : root.path("models")) {
                    String name = m.path("name").asText("");
                    if (wanted.equals(name) || name.startsWith(wanted)) {
                        return new TagProbe(true, normalizeDigest(m.path("digest").asText(null)));
                    }
                }
                return new TagProbe(false, null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("Bỏ qua probe /api/tags trên {}: {}", baseUrl, e.getMessage());
        }
        return new TagProbe(null, null);
    }

    private String probeVersionQuietly(String baseUrl, String token) {
        try {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(uri(baseUrl + "/api/version"))
                            .timeout(Duration.ofSeconds(2))
                            .header("Authorization", "Bearer " + token)
                            .header("Accept", "application/json")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() >= 200 && res.statusCode() < 300 && res.body() != null) {
                String ver = json.readTree(res.body()).path("version").asText(null);
                return ver == null || ver.isBlank() ? null : trimTo("ollama/" + ver.trim(), 30);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("Bỏ qua probe /api/version trên {}: {}", baseUrl, e.getMessage());
        }
        return null;
    }

    private String buildChatBody(ChatSpec spec) {
        try {
            AiProperties.Llm llm = props.getLlm();
            ObjectNode root = json.createObjectNode();
            root.put("model", llm.getModel());
            ArrayNode messages = root.putArray("messages");
            messages.addObject().put("role", "system").put("content", spec.systemPrompt());
            messages.addObject().put("role", "user").put("content", spec.userPrompt());
            root.set("format", json.readTree(spec.formatSchemaJson()));
            root.put("stream", false);
            root.put("keep_alive", llm.getKeepAlive());

            ObjectNode options = root.putObject("options");
            options.put("num_ctx", spec.options().getNumCtx());
            options.put("num_predict", spec.options().getNumPredict());
            options.put("temperature", spec.options().getTemperature());
            if (spec.options().getSeed() != null) {
                options.put("seed", spec.options().getSeed());
            }
            return json.writeValueAsString(root);
        } catch (Exception e) {
            throw new LlmException(AiErrorCode.INTERNAL_ERROR, "Không tạo được JSON yêu cầu.");
        }
    }

    private HttpResponse<String> send(HttpRequest req) {
        try {
            return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException e) {
            throw new LlmException(AiErrorCode.LLM_UNREACHABLE,
                    "Không kết nối được tới node (quá thời gian kết nối).");
        } catch (HttpTimeoutException e) {
            throw new LlmException(AiErrorCode.LLM_TIMEOUT, "Node không trả lời kịp.");
        } catch (IOException e) {
            throw new LlmException(AiErrorCode.LLM_UNREACHABLE,
                    "Không kết nối được tới node (" + e.getClass().getSimpleName() + ").");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(AiErrorCode.INTERNAL_ERROR, "Lượt gọi AI bị ngắt khi tắt hệ thống.");
        }
    }

    private static URI uri(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new LlmException(AiErrorCode.LLM_UNREACHABLE, "URL node không đúng dạng.");
        }
    }

    static String normalizeDigest(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("sha256:")) {
            cleaned = cleaned.substring("sha256:".length());
        }
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64);
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String textOrNull(JsonNode node, String k1, String k2) {
        if (node == null) {
            return null;
        }
        JsonNode a = node.get(k1);
        if (a != null && a.isTextual() && !a.asText().isBlank()) {
            return a.asText().trim();
        }
        JsonNode b = node.get(k2);
        if (b != null && b.isTextual() && !b.asText().isBlank()) {
            return b.asText().trim();
        }
        return null;
    }

    private static String trimTo(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 300 ? one.substring(0, 300) + "…" : one;
    }
}
