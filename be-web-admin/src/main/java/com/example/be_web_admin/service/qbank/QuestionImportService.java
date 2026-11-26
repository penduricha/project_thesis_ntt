package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.dto.qbank.ImportQuestionsRequest;
import com.example.be_web_admin.dto.qbank.ImportQuestionsResult;
import com.example.be_web_admin.dto.qbank.QuestionDetail;
import com.example.be_web_admin.exception.qbank.QbankException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Nhận nội dung file đã chuẩn hóa từ Vue. Mỗi câu dùng đúng transaction create hiện có,
 * vì vậy revision, phân quyền endpoint, ràng buộc môn/chương và xử lý trùng đều như tạo thủ công.
 * Không có transaction bao quanh cả vòng lặp: khi lỗi, các câu đã commit được báo chính xác.
 */
@Slf4j
@Service
public class QuestionImportService {
    private static final long MAX_CHARACTERS = 2L * 1024 * 1024;
    private final QuestionService questions;

    public QuestionImportService(QuestionService questions) {
        this.questions = questions;
    }

    public ImportQuestionsResult importQuestions(ImportQuestionsRequest request, Long adminId) {
        // @Valid chạy tại controller; các kiểm tra liên bản ghi phải hoàn tất trước khi ghi.
        long length = 0;
        Set<String> titles = new HashSet<>();
        for (ImportQuestionsRequest.Row row : request.questions()) {
            for (String value : new String[]{row.title(), row.stem(), row.optionA(), row.optionB(),
                    row.optionC(), row.optionD(), row.correctLabel(), row.generalFeedback()}) {
                if (value != null) length += value.length();
            }
            if (length > MAX_CHARACTERS) {
                throw QbankException.badRequest("IMPORT_TOO_LARGE", "Dữ liệu nhập vượt giới hạn cho phép.");
            }
            if (row.title() != null && !row.title().isBlank()) {
                String normalized = row.title().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
                if (!titles.add(normalized)) {
                    throw QbankException.badRequest("IMPORT_DUPLICATE_TITLE", "Tiêu đề câu hỏi bị trùng trong file.");
                }
            }
        }
        List<QuestionDetail> saved = new ArrayList<>();
        for (ImportQuestionsRequest.Row row : request.questions()) {
            try {
                // QuestionService là Spring proxy: mỗi create tự commit/rollback trước khi trả về.
                saved.add(questions.create(row.toCreate(request.subjectId(), request.topicId(), request.difficulty()), adminId));
            } catch (QbankException e) {
                return new ImportQuestionsResult(saved.size(), saved.size() + 1, false,
                        e.getStatus().value(), e.getCode(), saved);
            } catch (RuntimeException e) {
                // Không khẳng định rollback sau lỗi kết nối/commit; không tự gửi lại.
                log.error("Question import failed at row {} after {} confirmed commits", saved.size() + 1, saved.size(), e);
                return new ImportQuestionsResult(saved.size(), saved.size() + 1, true, null, null, saved);
            }
        }
        return new ImportQuestionsResult(saved.size(), null, false, null, null, saved);
    }
}
