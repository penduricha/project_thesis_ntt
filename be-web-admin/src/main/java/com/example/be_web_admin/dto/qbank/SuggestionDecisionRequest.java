package com.example.be_web_admin.dto.qbank;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Thân POST /web/admin/ai/suggestions/{id}/decision.
 *
 * <p>{@code decision} = "accepted" | "rejected". Khi accepted phải có nội dung để áp
 * ({@code finalText}, mặc định lấy {@code proposedText} của AI nếu bỏ trống). Với rejected,
 * {@code rejectReason} nên có để phục vụ thống kê "AI đề xuất sai".</p>
 */
public record SuggestionDecisionRequest(
        @NotBlank String decision,
        @Size(max = 20000) String finalText,
        @Size(max = 255) String rejectReason,
        Integer expectedVersion) {
}
