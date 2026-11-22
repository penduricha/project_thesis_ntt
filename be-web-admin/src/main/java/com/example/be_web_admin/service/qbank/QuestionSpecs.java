package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.enums.qbank.QuestionStatusD;
import com.example.be_web_admin.enums.qbank.ValidationStatusD;
import com.example.be_web_admin.model.jpa.qbank.Question;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bộ lọc cho bảng ngân hàng câu hỏi. Dùng Specification thay vì JPQL nhiều tham số optional để
 * tránh bẫy "so sánh với NULL" và để mỗi bộ lọc độc lập, dễ test.
 */
public final class QuestionSpecs {

    private QuestionSpecs() {
    }

    public static Specification<Question> filter(Long subjectId,
                                                 Long topicId,
                                                 QuestionStatusD status,
                                                 ValidationStatusD validationStatus,
                                                 String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (subjectId != null) {
                predicates.add(cb.equal(root.get("subjectId"), subjectId));
            }
            if (topicId != null) {
                predicates.add(cb.equal(root.get("topicId"), topicId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (validationStatus != null) {
                predicates.add(cb.equal(root.get("validationStatus"), validationStatus));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("stem")), pattern),
                        cb.like(cb.lower(root.get("title")), pattern)));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
