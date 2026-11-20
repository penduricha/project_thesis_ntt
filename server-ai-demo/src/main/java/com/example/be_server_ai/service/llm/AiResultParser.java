package com.example.be_server_ai.service.llm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.be_server_ai.enums.AiErrorCode;
import com.example.be_server_ai.enums.SuggestionSeverity;
import com.example.be_server_ai.enums.SuggestionTargetField;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Đọc và kiểm tra kết quả model. Model trả sai dạng thì ném LlmException (LLM_BAD_JSON hoặc LLM_SCHEMA_MISMATCH,
 * kèm nội dung thô), task sẽ được thử lại trên node khác.
 *
 * <p>Quy ước chuyển kết quả question_check thành đề xuất (ntt_ai_suggestions): mỗi trường khác bản gốc
 * (sentence -> stem, A..D -> option_a..option_d) là một đề xuất; đổi đáp án đúng (correct) là một đề xuất
 * target_field = 'question'. Có lỗi mà model không đưa bản sửa thì ghi một đề xuất chỉ có giải thích.
 * Mức độ: logic = high, presentation = medium, spelling = low, loại khác = medium. Không lỗi (none) thì không có đề xuất.
 */
@Component
public class AiResultParser {

    private static final Map<String, SuggestionSeverity> SEVERITY = Map.of(
            "logic", SuggestionSeverity.HIGH,
            "presentation", SuggestionSeverity.MEDIUM,
            "spelling", SuggestionSeverity.LOW);
    private static final Set<String> DIFFICULTY = Set.of("easy", "medium", "hard");
    private static final Set<String> SEARCH_IN = Set.of("question", "options", "all");

    private final ObjectMapper om;

    public AiResultParser(ObjectMapper om) {
        this.om = om;
    }

    public CheckOutcome parseQuestionCheck(String content, Collection<String> issueTypes) {
        JsonNode root = readJson(content);
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty() || !results.get(0).isObject()) {
            throw mismatch("Thiếu mảng results.", content);
        }
        JsonNode item = results.get(0);
        String errorType = item.path("error_type").asText(null);
        if (!item.path("has_error").isBoolean() || errorType == null) {
            throw mismatch("Thiếu has_error hoặc error_type.", content);
        }
        if (!"none".equals(errorType) && !issueTypes.contains(errorType)) {
            throw mismatch("error_type '" + errorType + "' không có trong ntt_issue_types.", content);
        }
        JsonNode original = item.path("original");
        JsonNode fixed = item.path("fixed");
        if (!original.isObject() || !fixed.isObject()) {
            throw mismatch("Thiếu original hoặc fixed.", content);
        }

        List<SuggestionDraft> drafts = new ArrayList<>();
        if (item.get("has_error").asBoolean() && !"none".equals(errorType)) {
            String explanation = item.path("explanation").asText("").trim();
            if (explanation.isEmpty()) {
                explanation = "AI phát hiện lỗi loại " + errorType + ".";
            }
            SuggestionSeverity severity = SEVERITY.getOrDefault(errorType, SuggestionSeverity.MEDIUM);
            diff(drafts, errorType, severity, SuggestionTargetField.STEM, original, fixed, "sentence", explanation);
            diff(drafts, errorType, severity, SuggestionTargetField.OPTION_A, original, fixed, "A", explanation);
            diff(drafts, errorType, severity, SuggestionTargetField.OPTION_B, original, fixed, "B", explanation);
            diff(drafts, errorType, severity, SuggestionTargetField.OPTION_C, original, fixed, "C", explanation);
            diff(drafts, errorType, severity, SuggestionTargetField.OPTION_D, original, fixed, "D", explanation);
            diff(drafts, errorType, severity, SuggestionTargetField.QUESTION, original, fixed, "correct", explanation);
            if (drafts.isEmpty()) {
                drafts.add(new SuggestionDraft(errorType, severity, SuggestionTargetField.QUESTION, null, null, explanation));
            }
        }
        ObjectNode result = ((ObjectNode) item.deepCopy());
        result.put("suggestion_count", drafts.size());
        return new CheckOutcome(result, drafts);
    }

    /** Kiểm tra bộ lọc tìm kiếm tự nhiên theo schema của notebook test_llm. */
    public JsonNode parseNlFilter(String content) {
        JsonNode f = readJson(content);
        if (!f.isObject() || !f.path("keywords").isTextual() || !f.path("tags").isArray()
                || !f.path("search_in").isArray()) {
            throw mismatch("Bộ lọc thiếu keywords, tags hoặc search_in.", content);
        }
        JsonNode difficulty = f.path("difficulty");
        if (!difficulty.isMissingNode() && !difficulty.isNull() && !DIFFICULTY.contains(difficulty.asText())) {
            throw mismatch("difficulty ngoài easy, medium, hard.", content);
        }
        for (JsonNode s : f.get("search_in")) {
            if (!SEARCH_IN.contains(s.asText())) {
                throw mismatch("search_in có giá trị lạ.", content);
            }
        }
        return f;
    }

    private static void diff(List<SuggestionDraft> out, String type, SuggestionSeverity sev,
                             SuggestionTargetField target, JsonNode original, JsonNode fixed, String key,
                             String explanation) {
        String a = original.path(key).asText("").trim();
        String b = fixed.path(key).asText("").trim();
        // Nhãn đáp án đúng (A..D) so không phân biệt hoa thường; văn bản thì so đúng từng ký tự.
        boolean same = target == SuggestionTargetField.QUESTION ? a.equalsIgnoreCase(b) : a.equals(b);
        if (b.isEmpty() || same) {
            return;
        }
        out.add(new SuggestionDraft(type, sev, target, a.isEmpty() ? null : a, b, explanation));
    }

    private JsonNode readJson(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            return om.readTree(text);
        } catch (JsonProcessingException e) {
            throw new LlmException(AiErrorCode.LLM_BAD_JSON, "Model trả về không phải JSON hợp lệ.", content);
        }
    }

    private static LlmException mismatch(String message, String raw) {
        return new LlmException(AiErrorCode.LLM_SCHEMA_MISMATCH, "Kết quả sai schema: " + message, raw);
    }
}
