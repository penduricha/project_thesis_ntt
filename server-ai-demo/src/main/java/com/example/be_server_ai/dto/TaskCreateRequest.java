package com.example.be_server_ai.dto;

/**
 * Thân POST /web/ai/tasks (chỉ dùng demo: Server AI đóng vai Server 1 để tạo task).
 * taskType: "question_check" hoặc "nl_search".
 * question_check cần questionId + questionRevisionId có thật (CSDL ràng buộc khóa ngoại) cùng nội dung câu hỏi.
 * nl_search chỉ cần query.
 */
public record TaskCreateRequest(
        String taskType,
        Long questionId,
        Long questionRevisionId,
        Long createdBy,
        String stem,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String correctLabel,
        String query) {
}
