package com.example.be_server_ai.dto;

import java.time.Instant;
import java.util.List;

import com.example.be_server_ai.model.jpa.AiSuggestion;
import com.example.be_server_ai.model.jpa.AiTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Một task gửi về trình duyệt. Không có raw_response (nội dung lớn). node lấy từ usage_json.node. */
public record TaskView(
        String taskId,
        String taskType,
        String status,
        Integer attemptCount,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Integer inferenceMs,
        String modelName,
        String node,
        JsonNode input,
        JsonNode result,
        JsonNode usage,
        List<SuggestionView> suggestions) {

    public static TaskView of(AiTask t, ObjectMapper om, List<AiSuggestion> suggestions) {
        JsonNode usage = parse(om, t.getUsageJson());
        String node = usage != null && usage.hasNonNull("node") ? usage.get("node").asText() : null;
        return new TaskView(
                t.getTaskId(),
                t.getTaskType() == null ? null : t.getTaskType().getValue(),
                t.getStatus() == null ? null : t.getStatus().getValue(),
                t.getAttemptCount(),
                t.getErrorCode(),
                t.getErrorMessage(),
                t.getCreatedAt(),
                t.getStartedAt(),
                t.getFinishedAt(),
                t.getInferenceMs(),
                t.getModelName(),
                node,
                parse(om, t.getInputJson()),
                parse(om, t.getResultJson()),
                usage,
                suggestions == null ? List.of() : suggestions.stream().map(SuggestionView::of).toList());
    }

    private static JsonNode parse(ObjectMapper om, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return om.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
