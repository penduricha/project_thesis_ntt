package com.example.be_web_admin.dto.qbank;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Thân PUT /web/admin/questions/{id}. {@code expectedVersion} là giá trị version client đang
 * giữ; lệch với DB thì trả 409 kèm version hiện tại (khoá lạc quan của ntt_questions.version).
 */
public record UpdateQuestionRequest(
        @NotNull Integer expectedVersion,
        Long topicId,
        @Size(max = 255) String title,
        @NotBlank @Size(max = 20000) String stem,
        @NotBlank @Size(max = 20000) String optionA,
        @NotBlank @Size(max = 20000) String optionB,
        @NotBlank @Size(max = 20000) String optionC,
        @NotBlank @Size(max = 20000) String optionD,
        @NotNull String correctLabel,
        String contentFormat,
        @Size(max = 4000) String generalFeedback,
        @Min(1) @Max(5) Integer difficulty,
        @Size(max = 255) String changeNote) {
}
