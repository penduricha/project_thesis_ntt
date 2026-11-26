package com.example.be_web_admin.dto.qbank;

import java.util.List;

/** Kết quả từng câu đã commit; failedAt là chỉ số bắt đầu từ 1, null nếu tất cả thành công. */
public record ImportQuestionsResult(int saved, Integer failedAt, boolean uncertain,
                                    Integer status, String errorCode, List<QuestionDetail> questions) {
    public ImportQuestionsResult {
        questions = List.copyOf(questions);
    }
}
