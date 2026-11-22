package com.example.be_web_admin.dto.qbank;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Instant;
import java.util.Map;

/**
 * Số liệu cho màn Dashboard (màn 1). Đây cũng chính là payload được cache ở Redis
 * {@code ui:dashboard:{scope}} với TTL ngắn — UI đọc thẻ KPI và biểu đồ tròn từ 1 request.
 *
 * <p>byValidation dùng mã CSDL ("not_checked", "clean", "has_issues", "failed", "stale") để
 * Vue không phải map lại.</p>
 */
@JsonPropertyOrder({"scope", "totalActive", "totalArchived", "byValidation", "updatedLast7Days",
        "pendingSuggestions", "runningTasks", "subjectCount", "generatedAt", "cached"})
public record DashboardSummary(
        String scope,
        long totalActive,
        long totalArchived,
        Map<String, Long> byValidation,
        long updatedLast7Days,
        long pendingSuggestions,
        long runningTasks,
        long subjectCount,
        Instant generatedAt,
        boolean cached) {

    /** Bản copy đánh dấu "lấy từ Redis" để UI/debug thấy rõ cache có tác dụng. */
    public DashboardSummary asCached() {
        return new DashboardSummary(scope, totalActive, totalArchived, byValidation, updatedLast7Days,
                pendingSuggestions, runningTasks, subjectCount, generatedAt, true);
    }
}
