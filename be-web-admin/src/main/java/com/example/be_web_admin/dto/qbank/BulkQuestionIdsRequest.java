package com.example.be_web_admin.dto.qbank;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Thân của POST /web/admin/questions/bulk-archive và /bulk-restore.
 * Giới hạn 100 id mỗi lượt: giao diện chọn nhiều dòng nhưng không nên gửi cả ngân hàng câu hỏi
 * trong một request (mỗi id sinh một revision + một bản ghi audit).
 */
public record BulkQuestionIdsRequest(
        @NotEmpty(message = "Danh sách id đang trống.")
        @Size(max = 100, message = "Mỗi lượt chỉ xử lý tối đa 100 câu hỏi.")
        List<@NotNull(message = "Id không được null.") Long> ids) {
}
