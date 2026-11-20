package com.example.server_ai_worker.service.ai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.server_ai_worker.config.AiProperties;
import com.example.server_ai_worker.dto.ai.NodeCheckResponse;
import com.example.server_ai_worker.dto.ai.NodeView;
import com.example.server_ai_worker.dto.ai.OverviewView;
import com.example.server_ai_worker.dto.ai.TaskCreateRequest;
import com.example.server_ai_worker.dto.ai.TaskView;
import com.example.server_ai_worker.enums.AiTaskStatus;
import com.example.server_ai_worker.enums.AiTaskType;
import com.example.server_ai_worker.model.jpa.AiSuggestion;
import com.example.server_ai_worker.model.jpa.AiTask;
import com.example.server_ai_worker.model.jpa.IssueType;
import com.example.server_ai_worker.model.redis.AiTaskStatusCache;
import com.example.server_ai_worker.repository.jpa.AiSuggestionRepository;
import com.example.server_ai_worker.repository.jpa.AiTaskRepository;
import com.example.server_ai_worker.repository.jpa.IssueTypeRepository;
import com.example.server_ai_worker.service.redis.AiCacheService;
import com.example.server_ai_worker.service.redis.AiQueueService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Tạo và truy vấn tác vụ AI (hỗ trợ chạy độc lập trên ntt_demo_llm và kiểm tra trạng thái).
 */
@Service
public class AiTaskService {

    private static final List<String> BURST_NL_SAMPLES = List.of(
            "Tìm câu hỏi môn Cơ sở dữ liệu mức độ khó đã duyệt",
            "Các câu hỏi Lập trình Java chủ đề Spring Boot & REST API",
            "Câu hỏi TOEIC Part 5 về ngữ pháp mức trung bình",
            "Tìm câu hỏi Mạng máy tính về giao thức HTTP tạo tháng này",
            "Câu hỏi Trí tuệ nhân tạo về mô hình ngôn ngữ lớn ở trạng thái nháp",
            "Tìm câu có từ khóa Primary Key trong phần câu dẫn môn INT3202",
            "Bộ câu hỏi chuẩn hóa dữ liệu 3NF mức độ dễ",
            "Tìm câu hỏi Java OOP chưa được duyệt gần đây"
    );

    private final AiTaskRepository tasks;
    private final AiSuggestionRepository suggestions;
    private final IssueTypeRepository issueTypes;
    private final AiQueueService queue;
    private final AiCacheService cache;
    private final NodePool nodePool;
    private final OllamaClient ollama;
    private final AiProperties props;
    private final ObjectMapper json;

    public AiTaskService(AiTaskRepository tasks,
                         AiSuggestionRepository suggestions,
                         IssueTypeRepository issueTypes,
                         AiQueueService queue,
                         AiCacheService cache,
                         NodePool nodePool,
                         OllamaClient ollama,
                         AiProperties props,
                         ObjectMapper json) {
        this.tasks = tasks;
        this.suggestions = suggestions;
        this.issueTypes = issueTypes;
        this.queue = queue;
        this.cache = cache;
        this.nodePool = nodePool;
        this.ollama = ollama;
        this.props = props;
        this.json = json;
    }

    public TaskView submit(TaskCreateRequest req) {
        if (req == null || req.taskType() == null || req.taskType().isBlank()) {
            throw new IllegalArgumentException("taskType phải là 'nl_search' hoặc 'question_check'.");
        }
        String typeRaw = req.taskType().trim().toLowerCase(Locale.ROOT);
        AiTaskType type = switch (typeRaw) {
            case "nl_search" -> AiTaskType.NL_SEARCH;
            case "question_check" -> AiTaskType.QUESTION_CHECK;
            default -> throw new IllegalArgumentException("taskType phải là 'nl_search' hoặc 'question_check'.");
        };

        String inputJson = buildAndValidateInputJson(type, req);
        long createdBy = req.createdBy() != null ? req.createdBy() : props.getDemo().getCreatedBy();
        String id = UUID.randomUUID().toString();
        AiTask entity = new AiTask(id, type, req.questionId(), req.questionRevisionId(), inputJson, createdBy);
        tasks.saveAndFlush(entity);
        cache.putStatus(id, type, AiTaskStatus.PENDING, 1, null);
        queue.push(id);
        return TaskView.of(entity, json, List.of());
    }

    public List<String> submitBurst(int rawCount, String rawTaskType) {
        int count = Math.max(1, Math.min(60, rawCount));
        String type = (rawTaskType == null || rawTaskType.isBlank()) ? "nl_search" : rawTaskType.trim();
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String q = BURST_NL_SAMPLES.get(i % BURST_NL_SAMPLES.size()) + " [mẫu #" + (i + 1) + "]";
            TaskCreateRequest req = new TaskCreateRequest(
                    type, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null,
                    q, null);
            ids.add(submit(req).taskId());
        }
        return ids;
    }

    public Optional<TaskView> getTask(String taskId) {
        return tasks.findById(taskId).map(t -> {
            List<AiSuggestion> list = t.getTaskType() == AiTaskType.QUESTION_CHECK
                    ? suggestions.findByTaskIdOrderBySeqNo(taskId)
                    : List.of();
            return TaskView.of(t, json, list);
        });
    }

    public Optional<AiTaskStatusCache> getTaskStatusFast(String taskId) {
        Optional<AiTaskStatusCache> cached = cache.getStatus(taskId);
        if (cached.isPresent()) {
            return cached;
        }
        return tasks.findById(taskId).map(t -> {
            AiTaskStatusCache built = new AiTaskStatusCache(
                    t.getTaskId(),
                    t.getTaskType().getValue(),
                    t.getStatus().getValue(),
                    t.getAttemptCount(),
                    t.getErrorCode(),
                    System.currentTimeMillis());
            cache.putStatus(t.getTaskId(), t.getTaskType(), t.getStatus(), t.getAttemptCount(), t.getErrorCode());
            return built;
        });
    }

    public List<TaskView> recentTasks() {
        List<AiTask> list = tasks.findTop30ByOrderByCreatedAtDesc();
        List<String> checkIds = list.stream()
                .filter(t -> t.getTaskType() == AiTaskType.QUESTION_CHECK)
                .map(AiTask::getTaskId)
                .toList();
        Map<String, List<AiSuggestion>> byTask = new LinkedHashMap<>();
        if (!checkIds.isEmpty()) {
            for (AiSuggestion s : suggestions.findByTaskIdInOrderByTaskIdAscSeqNoAsc(checkIds)) {
                byTask.computeIfAbsent(s.getTaskId(), ignored -> new ArrayList<>()).add(s);
            }
        }
        return list.stream()
                .map(t -> TaskView.of(t, json, byTask.getOrDefault(t.getTaskId(), List.of())))
                .toList();
    }

    public List<IssueType> listIssueTypes() {
        return issueTypes.findAllByOrderBySortOrderAscIssueTypeAsc();
    }

    public List<NodeView> listNodes() {
        return nodePool.views();
    }

    public NodeView upsertNode(String name, String url, String token, Boolean enabled, Integer maxConcurrency) {
        return nodePool.upsert(name, url, token, enabled, maxConcurrency);
    }

    public NodeView removeNode(String name) {
        return nodePool.remove(name);
    }

    public NodeCheckResponse checkNode(String rawUrl, String rawToken) {
        String url = nodePool.normalizeAndValidateUrl(rawUrl);
        String token = nodePool.validateToken(rawToken);
        return ollama.check(url, token);
    }

    public OverviewView overview() {
        return new OverviewView(
                queue.length(),
                props.getLlm().getModel(),
                props.getLlm().getPromptVersion(),
                props.getLlm().getQuestionCheckMode(),
                nodePool.views(),
                System.currentTimeMillis());
    }

    private String buildAndValidateInputJson(AiTaskType type, TaskCreateRequest req) {
        JsonNode root;
        if (req.inputJson() != null && !req.inputJson().isBlank()) {
            try {
                root = json.readTree(req.inputJson());
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("inputJson không phải JSON hợp lệ.");
            }
            if (!root.isObject()) {
                throw new IllegalArgumentException("inputJson phải là object JSON.");
            }
        } else {
            ObjectNode obj = json.createObjectNode();
            if (type == AiTaskType.NL_SEARCH) {
                putIfNotBlank(obj, "query", req.query());
                if (req.subjects() != null && !req.subjects().isNull()) {
                    obj.set("subjects", req.subjects());
                }
            } else {
                putIfNotBlank(obj, "stem", req.stem());
                putIfNotBlank(obj, "option_a", req.optionA());
                putIfNotBlank(obj, "option_b", req.optionB());
                putIfNotBlank(obj, "option_c", req.optionC());
                putIfNotBlank(obj, "option_d", req.optionD());
                putIfNotBlank(obj, "correct_label",
                        req.correctLabel() == null ? null : req.correctLabel().trim().toUpperCase(Locale.ROOT));
                putIfNotBlank(obj, "explanation", req.explanation());
                putIfNotBlank(obj, "subject_code", req.subjectCode());
                putIfNotBlank(obj, "subject_name", req.subjectName());
                putIfNotBlank(obj, "topic_name", req.topicName());
                if (req.difficulty() != null) {
                    obj.put("difficulty", req.difficulty());
                }
            }
            root = obj;
        }

        if (type == AiTaskType.QUESTION_CHECK) {
            if (isMissingText(root, "stem")
                    || isMissingText(root, "option_a")
                    || isMissingText(root, "option_b")
                    || isMissingText(root, "option_c")
                    || isMissingText(root, "option_d")
                    || isMissingText(root, "correct_label")) {
                throw new IllegalArgumentException(
                        "question_check bắt buộc có stem, option_a..option_d và correct_label.");
            }
            String cl = root.path("correct_label").asText("").trim().toUpperCase(Locale.ROOT);
            if (!List.of("A", "B", "C", "D").contains(cl)) {
                throw new IllegalArgumentException("correct_label phải là A, B, C hoặc D.");
            }
        } else {
            if (isMissingText(root, "query")) {
                throw new IllegalArgumentException("nl_search bắt buộc có query không rỗng.");
            }
        }

        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không tuần tự hóa được input_json.", e);
        }
    }

    private static void putIfNotBlank(ObjectNode obj, String key, String value) {
        if (value != null && !value.isBlank()) {
            obj.put(key, value.trim());
        }
    }

    private static boolean isMissingText(JsonNode node, String key) {
        JsonNode child = node.get(key);
        return child == null || child.isNull() || child.asText("").isBlank();
    }
}
