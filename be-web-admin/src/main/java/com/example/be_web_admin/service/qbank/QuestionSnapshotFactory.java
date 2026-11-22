package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.model.jpa.qbank.Question;
import com.example.be_web_admin.model.jpa.qbank.Subject;
import com.example.be_web_admin.model.jpa.qbank.Topic;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Sinh snapshot_json cho ntt_question_revisions (append-only).
 *
 * <p>Bộ khoá ở đây CHÍNH LÀ hợp đồng đầu vào của Server 2: nó được gửi nguyên văn làm
 * {@code inputJson} cho POST /web/ai/tasks, và AiTaskService bên worker kiểm tra bắt buộc
 * stem, option_a..option_d, correct_label. Vì vậy đổi tên khoá ở đây là đổi hợp đồng AI —
 * sửa cả hai phía.</p>
 */
@Component
public class QuestionSnapshotFactory {

    private final ObjectMapper objectMapper;

    public QuestionSnapshotFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(Question question, Subject subject, Topic topic) {
        ObjectNode node = objectMapper.createObjectNode();
        put(node, "stem", question.getStem());
        put(node, "option_a", question.getOptionA());
        put(node, "option_b", question.getOptionB());
        put(node, "option_c", question.getOptionC());
        put(node, "option_d", question.getOptionD());
        put(node, "correct_label", question.getCorrectLabel() == null ? null : question.getCorrectLabel().dbValue());
        put(node, "explanation", question.getGeneralFeedback());
        put(node, "subject_code", subject == null ? null : subject.getSubjectCode());
        put(node, "subject_name", subject == null ? null : subject.getSubjectName());
        put(node, "topic_name", topic == null ? null : topic.getTopicName());
        if (question.getDifficulty() != null) {
            node.put("difficulty", question.getDifficulty());
        }
        return write(node);
    }

    /** Dùng khi cần snapshot "nội dung sẽ áp dụng" trước khi ghi câu hỏi (vd: duyệt đề xuất). */
    public String write(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không tuần tự hoá được snapshot_json", e);
        }
    }

    private static void put(ObjectNode node, String key, String value) {
        if (value != null && !value.isBlank()) {
            node.put(key, value.trim());
        }
    }
}
