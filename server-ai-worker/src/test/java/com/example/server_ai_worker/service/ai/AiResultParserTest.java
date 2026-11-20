package com.example.server_ai_worker.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.server_ai_worker.config.AiProperties;
import com.example.server_ai_worker.enums.AiErrorCode;
import com.example.server_ai_worker.enums.SuggestionTargetField;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class AiResultParserTest {

    private ObjectMapper om;
    private AiResultParser parser;

    @BeforeEach
    void setUp() {
        om = new ObjectMapper();
        AiProperties props = new AiProperties();
        AiProperties.SubjectCatalog s1 = new AiProperties.SubjectCatalog();
        s1.setSubjectCode("INT3202");
        s1.setSubjectName("Cơ sở dữ liệu");
        s1.setTopics(List.of("Ngôn ngữ truy vấn SQL", "Chuẩn hóa dữ liệu"));
        props.getNlSearch().setDefaultSubjects(List.of(s1));
        parser = new AiResultParser(props, om, new RulePreflightChecker(), new SubjectCatalogResolver(props));
    }

    @Test
    void parsesMultiSuggestionFixesCrossFieldAndDropsHallucinatedSpellingQuote() throws Exception {
        ObjectNode input = om.createObjectNode();
        input.put("stem", "Khóa chính dùng để lam gì trong bảng?");
        input.put("option_a", "Định danh duy nhất bản ghi");
        input.put("option_b", "Lưu trữ mật khẩu người dung");
        input.put("option_c", "Tạo chỉ mục toàn văn");
        input.put("option_d", "Xóa bảng tự động");
        input.put("correct_label", "A");

        String llmJson = """
                {
                  "analysis": {
                    "self_solve_reasoning": "Khóa chính định danh duy nhất mỗi bản ghi.",
                    "predicted_answer": "A",
                    "matches_correct_label": true
                  },
                  "has_issue": true,
                  "suggested_difficulty": null,
                  "suggestions": [
                    {
                      "issue_type": "spelling",
                      "severity": "low",
                      "target_field": "stem",
                      "original_text": "dùng để lam gì",
                      "proposed_text": "dùng để làm gì",
                      "explanation": "Sai chính tả từ 'lam' -> 'làm'."
                    },
                    {
                      "issue_type": "spelling",
                      "severity": "low",
                      "target_field": "stem",
                      "original_text": "người dung",
                      "proposed_text": "người dùng",
                      "explanation": "LLM nhầm target_field sang stem dù lỗi nằm ở option_b."
                    },
                    {
                      "issue_type": "grammar",
                      "severity": "medium",
                      "target_field": "stem",
                      "original_text": "cụm từ bịa không có trong đề",
                      "proposed_text": "cụm từ khác",
                      "explanation": "Trích dẫn ảo giác phải bị lọc bỏ."
                    }
                  ]
                }""";

        ChatResult chat = new ChatResult(llmJson, llmJson, "qwen2.5:14b-instruct-q4_K_M",
                null, null, 120, 2_000_000_000L, 200, 2_500_000_000L, "stop", 2500L);

        CheckOutcome outcome = parser.parseQuestionCheck("task-1", input, chat);
        assertEquals(2, outcome.suggestions().size());

        var first = outcome.suggestions().get(0);
        assertEquals(SuggestionTargetField.STEM, first.getTargetField());
        assertEquals("dùng để lam gì",
                input.get("stem").asText().substring(first.getSpanStart(), first.getSpanEnd()));

        var second = outcome.suggestions().get(1);
        assertEquals(SuggestionTargetField.OPTION_B, second.getTargetField());
        assertEquals("người dung",
                input.get("option_b").asText().substring(second.getSpanStart(), second.getSpanEnd()));

        JsonNode res = om.readTree(outcome.resultJson());
        assertTrue(res.get("has_issue").asBoolean());
        assertEquals("A", res.path("analysis").path("predicted_answer").asText());
    }

    @Test
    void rejectsAttemptToModifyCorrectLabel() {
        ObjectNode input = om.createObjectNode();
        input.put("stem", "Câu hỏi mẫu?");
        input.put("option_a", "A");
        input.put("option_b", "B");
        input.put("option_c", "C");
        input.put("option_d", "D");
        input.put("correct_label", "A");

        String badJson = """
                {
                  "analysis": {"self_solve_reasoning": "...", "predicted_answer": "B", "matches_correct_label": false},
                  "has_issue": true,
                  "suggested_difficulty": null,
                  "suggestions": [
                    {
                      "issue_type": "key_doubt",
                      "severity": "high",
                      "target_field": "correct_label",
                      "original_text": "A",
                      "proposed_text": "B",
                      "explanation": "Không được phép trả target_field=correct_label."
                    }
                  ]
                }""";

        ChatResult chat = new ChatResult(badJson, badJson, "qwen2.5:14b-instruct-q4_K_M",
                null, null, 50, 1_000_000_000L, 100, 1_200_000_000L, "stop", 1200L);

        LlmException ex = assertThrows(LlmException.class,
                () -> parser.parseQuestionCheck("task-2", input, chat));
        assertEquals(AiErrorCode.LLM_SCHEMA_MISMATCH, ex.getCode());
    }

    @Test
    void detectsTruncatedOutputWhenDoneReasonIsLength() {
        ObjectNode input = om.createObjectNode();
        input.put("query", "Tìm câu hỏi SQL");
        String partial = "{\"subject_code\":\"INT3202\"";
        ChatResult chat = new ChatResult(partial, partial, "qwen2.5:14b-instruct-q4_K_M",
                null, null, 350, 3_000_000_000L, 100, 3_200_000_000L, "length", 3200L);

        LlmException ex = assertThrows(LlmException.class,
                () -> parser.parseNlSearch(input, chat));
        assertEquals(AiErrorCode.LLM_TRUNCATED, ex.getCode());
    }

    @Test
    void validatesNlSearchCatalogAndSwapsReversedDates() throws Exception {
        ObjectNode input = om.createObjectNode();
        input.put("query", "Tìm câu hỏi SQL");

        String llmJson = """
                {
                  "subject_code": "INT3202",
                  "topic_name": "Chủ đề bịa không có",
                  "difficulty": 4,
                  "validation_status": "invalid_status",
                  "keywords": ["SQL", "JOIN", "SQL"],
                  "search_in": "stem",
                  "created_from": "2026-11-20",
                  "created_to": "2026-11-01"
                }""";

        ChatResult chat = new ChatResult(llmJson, llmJson, "qwen2.5:14b-instruct-q4_K_M",
                null, null, 80, 1_000_000_000L, 120, 1_100_000_000L, "stop", 1100L);

        String normalized = parser.parseNlSearch(input, chat);
        JsonNode out = om.readTree(normalized);
        assertEquals("INT3202", out.get("subject_code").asText());
        assertTrue(out.get("topic_name").isNull());
        assertEquals(4, out.get("difficulty").asInt());
        assertTrue(out.get("validation_status").isNull());
        assertEquals(2, out.get("keywords").size());
        assertEquals("2026-11-01", out.get("created_from").asText());
        assertEquals("2026-11-20", out.get("created_to").asText());
    }
}
