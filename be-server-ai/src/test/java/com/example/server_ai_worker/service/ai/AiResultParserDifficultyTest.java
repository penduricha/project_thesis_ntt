package com.example.server_ai_worker.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.server_ai_worker.config.AiProperties;
import com.example.server_ai_worker.enums.SuggestionTargetField;
import com.example.server_ai_worker.model.jpa.AiSuggestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Hai hành vi của {@code AiResultParser} dễ bị hiểu sai khi đọc code:
 *
 * <ol>
 *   <li>Gợi ý độ khó chỉ gắn vào ĐÚNG MỘT đề xuất. Nhìn {@code accepted.isEmpty() ? d : null} nằm
 *       trong {@code accepted.add(...)} rất dễ tưởng là bug "luôn true", nhưng Java đánh giá đối
 *       số TRƯỚC khi gọi {@code add()}, nên nó đúng một lần. Test này ghim hành vi đó.</li>
 *   <li>Lỗi do tiền kiểm tất định bắt được thì LLM báo lại KHÔNG sinh thêm dòng thứ hai. Trước bản
 *       vá hai nhánh dùng khuôn khoá khác nhau ({@code field|issue} và {@code field|issue|span})
 *       nên không bao giờ trùng ⇒ người duyệt thấy 2 dòng cho cùng một lỗi.</li>
 * </ol>
 */
class AiResultParserDifficultyTest {

    private ObjectMapper om;
    private AiResultParser parser;

    @BeforeEach
    void setUp() {
        om = new ObjectMapper();
        AiProperties props = new AiProperties();
        props.getLlm().setSuggestDifficulty(true);
        parser = new AiResultParser(props, om, new RulePreflightChecker(), new SubjectCatalogResolver(props));
    }

    /** Đầu vào HỢP LỆ hoàn toàn: tiền kiểm không tìm được lỗi nào. */
    private ObjectNode cleanInput() {
        ObjectNode input = om.createObjectNode();
        input.put("stem", "Khóa chính trong bảng dùng để làm gì?");
        input.put("option_a", "Định danh duy nhất mỗi bản ghi");
        input.put("option_b", "Lưu mật khẩu người dùng");
        input.put("option_c", "Tạo chỉ mục toàn văn");
        input.put("option_d", "Xóa toàn bộ bảng");
        input.put("correct_label", "A");
        return input;
    }

    /** ChatResult(content, rawBody, modelName, modelDigest, runtimeVersion, evalCount,
     *             evalDurationNs, promptEvalCount, totalDurationNs, doneReason, wallMs). */
    private static ChatResult chatResult(String content) {
        return new ChatResult(content, content, "qwen", "digest", "0.1", 0, 0L, 0, 0L, "stop", 0L);
    }

    private static String chat(String suggestionsJson, String difficulty) {
        return """
                {
                  "analysis": {
                    "self_solve_reasoning": "Xét từng phương án.",
                    "predicted_answer": "A",
                    "matches_correct_label": true
                  },
                  "has_issue": true,
                  "suggested_difficulty": %s,
                  "suggestions": [%s]
                }
                """.formatted(difficulty, suggestionsJson);
    }

    private static String suggestion(String issue, String field, String original, String proposed, String why) {
        return """
                {
                  "issue_type": "%s",
                  "severity": "low",
                  "target_field": "%s",
                  "original_text": "%s",
                  "proposed_text": "%s",
                  "explanation": "%s"
                }
                """.formatted(issue, field, original, proposed, why);
    }

    @Test
    void suggestedDifficultyLandsOnExactlyOneSuggestion() {
        String json = chat(
                suggestion("spelling", "stem", "làm gì", "để làm gì", "Dùng từ cho rõ nghĩa.") + ","
                        + suggestion("grammar", "option_d", "Xóa toàn bộ bảng", "Xoá toàn bộ bảng", "Thống nhất chính tả."),
                "4");

        CheckOutcome out = parser.parseQuestionCheck("T1", cleanInput(), chatResult(json));

        List<AiSuggestion> list = out.suggestions();
        assertTrue(list.size() >= 2, "cần ít nhất 2 đề xuất để phép thử có nghĩa");
        long withDifficulty = list.stream().filter(s -> s.getSuggestedDifficulty() != null).count();
        assertEquals(1, withDifficulty,
                "gợi ý độ khó phải gắn vào đúng MỘT đề xuất (add() nhận đối số đã đánh giá xong)");
        assertEquals(4, list.get(0).getSuggestedDifficulty());
    }

    @Test
    void preflightFindingWinsOverLlmDuplicate() {
        // option_b để trống: RulePreflightChecker CHẮC CHẮN bắt được. LLM cũng báo cùng lỗi đó.
        ObjectNode input = cleanInput();
        input.put("option_b", "");

        String llmJson = chat(
                suggestion("logic", "option_b", "", "", "Phương án B đang bị trống."),
                "null");

        CheckOutcome out = parser.parseQuestionCheck("T2", input, chatResult(llmJson));

        List<AiSuggestion> onOptionB = out.suggestions().stream()
                .filter(s -> s.getTargetField() == SuggestionTargetField.OPTION_B
                        && "logic".equals(s.getIssueType()))
                .toList();
        assertEquals(1, onOptionB.size(),
                "cùng một lỗi (option_b, logic) không được hiện hai dòng cho người duyệt");
        assertNotNull(onOptionB.get(0).getExplanation());
    }
}
