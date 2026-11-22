package com.example.be_web_admin.dto.qbank;

import java.util.List;

/**
 * Kết quả xử lý hàng loạt: mỗi id hoặc thành công ({@code done}) hoặc bị bỏ qua kèm mã lỗi
 * ({@code skipped}). Không ném lỗi cho cả lô khi chỉ một vài id sai — giao diện cần biết id nào
 * đã xong để không hiển thị sai trạng thái.
 */
public record BulkQuestionResult(
        int requested,
        List<Long> done,
        List<Failure> skipped) {

    /** Một id không xử lý được. */
    public record Failure(Long questionId, String code, String message) {
    }
}
