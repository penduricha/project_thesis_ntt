package com.example.server_ai_worker.dto.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Thân POST /web/ai/tasks (demo / test độc lập).
 * Hỗ trợ truyền trực tiếp inputJson hoặc các trường phẳng (query / stem, optionA..D, correctLabel...).
 */
public record TaskCreateRequest(
        String taskType,
        Long questionId,
        Long questionRevisionId,
        Long createdBy,
        String inputJson,
        String stem,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String correctLabel,
        String explanation,
        String subjectCode,
        String subjectName,
        String topicName,
        Integer difficulty,
        String query,
        JsonNode subjects) {
}
