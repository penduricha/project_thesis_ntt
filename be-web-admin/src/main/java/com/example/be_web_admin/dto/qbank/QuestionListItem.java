package com.example.be_web_admin.dto.qbank;

import java.time.Instant;
import java.util.List;

/**
 * Một dòng của bảng "Ngân hàng câu hỏi" (màn 4 trong UI_Accept_v9).
 *
 * <h2>Vì sao có {@code options}</h2>
 * Trước đây bảng chỉ trả {@code stemPreview} (cắt ngắn), nên giao diện phải gọi thêm
 * {@code GET /questions/{id}} cho <b>từng</b> câu để lấy đủ 4 phương án — với 500 câu là
 * 500 request thừa (N+1). Khi client truyền {@code includeOptions=true}, mỗi dòng mang sẵn
 * {@code options} + {@code correctLabel}, nhờ đó màn danh sách chỉ cần <b>một</b> lượt gọi.
 *
 * <p>{@code options} là {@code null} khi client không yêu cầu (giữ nguyên kích thước response
 * cho các nơi chỉ cần xem nhanh). Thứ tự luôn là A, B, C, D.</p>
 */
public record QuestionListItem(
        Long questionId,
        Long subjectId,
        String subjectName,
        Long topicId,
        String topicName,
        String title,
        String stemPreview,
        String validationStatus,
        String status,
        Integer difficulty,
        String source,
        Instant updatedAt,
        Integer version,
        /* --- 4 trường dưới đây chỉ có giá trị khi includeOptions = true --- */
        String stem,
        List<String> options,
        String correctLabel,
        String generalFeedback,
        String contentFormat) {

    /** Dòng rút gọn (không kèm phương án) — giữ constructor cũ cho code đang gọi. */
    public QuestionListItem(Long questionId, Long subjectId, String subjectName,
                            Long topicId, String topicName, String title, String stemPreview,
                            String validationStatus, String status, Integer difficulty,
                            String source, Instant updatedAt, Integer version) {
        this(questionId, subjectId, subjectName, topicId, topicName, title, stemPreview,
                validationStatus, status, difficulty, source, updatedAt, version,
                null, null, null, null, null);
    }
}
