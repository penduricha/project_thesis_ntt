package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.exception.qbank.QbankException;
import com.example.be_web_admin.model.jpa.qbank.Question;
import com.example.be_web_admin.model.jpa.qbank.QuestionRevision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cầu nối Server 1 -> Server 2 (Colab/Ollama), endpoint POST /web/ai/tasks.
 *
 * <p>Hiện worker TỰ sinh task_id và tự ghi ntt_ai_tasks, nên Server 1 chỉ gửi nội dung rồi lưu
 * task_id trả về vào Redis để giao diện polling. Khi chuyển sang mô hình "Server 1 sở hữu
 * task" (khuyến nghị trong tài liệu đối chiếu), chỉ cần sinh task_id ở đây, ghi DB trước, rồi
 * gọi worker kèm task_id (phải bổ sung field đó vào TaskCreateRequest của worker).</p>
 *
 * <p>Chưa cấu hình {@code app.ai-worker.base-url} thì coi như tắt: trả 503 với mã
 * AI_WORKER_NOT_CONFIGURED chứ không tạo task "treo" không ai xử lý.</p>
 */
@Slf4j
@Component
public class AiWorkerClient {

    private static final String CREATE_TASK_PATH = "/web/ai/tasks";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String internalToken;

    public AiWorkerClient(RestClient.Builder builder,
                          ObjectMapper objectMapper,
                          @Value("${app.ai-worker.base-url:}") String baseUrl,
                          @Value("${app.ai-worker.internal-token:}") String internalToken,
                          @Value("${app.ai-worker.connect-timeout-ms:3000}") int connectTimeoutMs,
                          @Value("${app.ai-worker.read-timeout-ms:20000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.internalToken = internalToken == null ? "" : internalToken.trim();
        String url = baseUrl == null ? "" : baseUrl.trim();
        if (url.isEmpty()) {
            this.restClient = null;
        } else {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
            factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
            this.restClient = builder.clone().baseUrl(url).requestFactory(factory).build();
        }
    }

    public boolean isConfigured() {
        return restClient != null;
    }

    /** Kết quả tối thiểu cần cho giao diện. */
    public record WorkerTask(String taskId, String taskType, String status) {
    }

    /**
     * Gửi yêu cầu kiểm định 1 câu hỏi theo ĐÚNG revision đã chốt: inputJson lấy nguyên văn
     * snapshot_json của revision, nên AI không bao giờ đọc "câu hỏi hiện tại" khác bản đã gửi.
     */
    public WorkerTask submitQuestionCheck(Question question, QuestionRevision revision, Long adminId) {
        if (restClient == null) {
            throw QbankException.serviceUnavailable("AI_WORKER_NOT_CONFIGURED",
                    "Chưa cấu hình app.ai-worker.base-url (Server 2).");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskType", "question_check");
        payload.put("questionId", question.getQuestionId());
        payload.put("questionRevisionId", revision.getRevisionId());
        payload.put("createdBy", adminId);
        payload.put("inputJson", revision.getSnapshotJson());

        try {
            String raw = restClient.post()
                    .uri(CREATE_TASK_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", internalToken)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            JsonNode node = raw == null ? null : objectMapper.readTree(raw);
            if (node == null || node.path("taskId").asText("").isBlank()) {
                throw QbankException.serviceUnavailable("AI_WORKER_BAD_RESPONSE",
                        "Server 2 không trả taskId hợp lệ.");
            }
            return new WorkerTask(
                    node.path("taskId").asText(),
                    node.path("taskType").asText("question_check"),
                    node.path("status").asText("pending"));
        } catch (ResourceAccessException e) {
            log.warn("AI worker không truy cập được: {}", e.toString());
            throw QbankException.serviceUnavailable("AI_WORKER_UNAVAILABLE",
                    "Không nhận được phản hồi từ AI worker; task có thể đã được tạo. Kiểm tra trạng thái trước khi thử lại.");
        } catch (RestClientResponseException e) {
            log.warn("AI worker từ chối yêu cầu: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw QbankException.serviceUnavailable("AI_WORKER_REJECTED",
                    "Server 2 từ chối yêu cầu (" + e.getStatusCode().value() + "): "
                            + shorten(e.getResponseBodyAsString()));
        } catch (QbankException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Lỗi khi gọi AI worker: {}", e.toString());
            throw QbankException.serviceUnavailable("AI_WORKER_ERROR", "Lỗi khi gọi Server 2: " + e.getMessage());
        }
    }

    private static String shorten(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.replaceAll("\\s+", " ").trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
    }
}
