package com.example.server_ai_worker.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.server_ai_worker.enums.SuggestionTargetField;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class RulePreflightCheckerTest {

    private final ObjectMapper om = new ObjectMapper();
    private final RulePreflightChecker checker = new RulePreflightChecker();

    @Test
    void cleanQuestionHasNoPreflightFindings() {
        ObjectNode q = om.createObjectNode();
        q.put("stem", "Giao thức HTTP sử dụng cổng mặc định nào?");
        q.put("option_a", "21");
        q.put("option_b", "22");
        q.put("option_c", "80");
        q.put("option_d", "443");
        q.put("correct_label", "C");

        assertTrue(checker.check(q).isEmpty());
    }

    @Test
    void detectsDuplicateOptionsAndAnswerLeakInStem() {
        ObjectNode q = om.createObjectNode();
        q.put("stem", "Thủ đô của Việt Nam là gì? (Đáp án: A)");
        q.put("option_a", "A. Hà Nội");
        q.put("option_b", "Đà Nẵng");
        q.put("option_c", "hà   nội");
        q.put("option_d", "Cần Thơ");
        q.put("correct_label", "A");

        List<RulePreflightChecker.Finding> findings = checker.check(q);
        assertEquals(2, findings.size());
        assertTrue(findings.stream().anyMatch(f ->
                "logic".equals(f.issueType()) && f.targetField() == SuggestionTargetField.STEM));
        assertTrue(findings.stream().anyMatch(f ->
                "distractor".equals(f.issueType()) && f.targetField() == SuggestionTargetField.OPTION_C));
    }
}
