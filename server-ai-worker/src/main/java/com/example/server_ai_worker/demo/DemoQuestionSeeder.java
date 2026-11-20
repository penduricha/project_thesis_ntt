package com.example.server_ai_worker.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nạp bộ 1000 câu hỏi demo từ classpath:demo/questions-1000.json vào ntt_demo_questions
 * khi bảng còn trống. Idempotent — không đè dữ liệu hiện có. Dùng endpoint
 * POST /web/ai/demo/seed?force=true để nạp lại từ đầu (xóa cả runs/items).
 */
@Component
public class DemoQuestionSeeder implements ApplicationRunner {

    public static final String RESOURCE = "demo/questions-1000.json";
    private static final Logger log = LoggerFactory.getLogger(DemoQuestionSeeder.class);

    private final DemoQuestionRepository questions;
    private final ObjectMapper json;

    public DemoQuestionSeeder(DemoQuestionRepository questions, ObjectMapper json) {
        this.questions = questions;
        this.json = json;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = questions.count();
        if (existing > 0) {
            log.info("ntt_demo_questions đã có {} câu — bỏ qua seed.", existing);
            return;
        }
        int loaded = loadFromResource();
        log.info("Đã nạp {} câu hỏi demo vào ntt_demo_questions từ {}", loaded, RESOURCE);
    }

    /** Nạp lại từ đầu: xóa runs/items do caller xử lý; ở đây chỉ xóa câu hỏi và nạp lại. */
    @Transactional
    public int reseed() {
        questions.deleteAllInBatch();
        return loadFromResource();
    }

    private int loadFromResource() {
        try (var in = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode root = json.readTree(in);
            JsonNode arr = root.path("questions");
            if (!arr.isArray()) {
                throw new IllegalStateException("File " + RESOURCE + " thiếu mảng 'questions'.");
            }
            int n = 0;
            for (JsonNode q : arr) {
                DemoQuestion e = new DemoQuestion();
                e.setExtId(q.path("id").asText());
                e.setSubjectCode(q.path("subject_code").asText());
                e.setSubjectName(q.path("subject_name").asText());
                e.setTopicName(q.path("topic_name").asText(null));
                e.setStem(q.path("stem").asText());
                e.setOptionA(q.path("option_a").asText());
                e.setOptionB(q.path("option_b").asText());
                e.setOptionC(q.path("option_c").asText());
                e.setOptionD(q.path("option_d").asText());
                e.setCorrectIdx(q.path("correct_idx").asInt());
                e.setTrueCorrectIdx(q.path("true_correct_idx").asInt());
                e.setCorrectLabel(q.path("correct_label").asText());
                e.setDifficulty(q.path("difficulty").asInt());
                e.setExplanation(q.path("explanation").asText(null));
                e.setStemEn(q.path("stem_en").asText(null));
                e.setGoldIssueType(q.path("gold_issue_type").asText());
                questions.save(e);
                n++;
            }
            return n;
        } catch (Exception e) {
            throw new IllegalStateException("Không nạp được " + RESOURCE + ": " + e.getMessage(), e);
        }
    }
}
