package com.example.server_ai_worker.service.ai;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.example.server_ai_worker.config.AiProperties;
import com.example.server_ai_worker.enums.AiErrorCode;
import com.example.server_ai_worker.enums.SuggestionSeverity;
import com.example.server_ai_worker.enums.SuggestionTargetField;
import com.example.server_ai_worker.model.jpa.AiSuggestion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Kiểm tra cấu trúc JSON trả về từ LLM, áp dụng tiền kiểm tất định, định vị span an toàn với chk_sugg_span,
 * và hậu kiểm chống ảo giác (không bịa trích dẫn, không sửa đáp án đúng, không lặp đề xuất vô ích).
 */
@Component
public class AiResultParser {

    private static final Set<String> ALLOWED_ISSUE_TYPES = Set.of(
            "spelling", "grammar", "logic", "distractor", "key_doubt");
    private static final Set<String> VALIDATION_STATUSES = Set.of(
            "not_checked", "clean", "has_issues", "failed", "stale");
    private static final Set<String> SEARCH_IN_VALUES = Set.of("stem", "options", "explanation", "all");
    private static final int MAX_SUGGESTIONS = 8;

    private final AiProperties props;
    private final ObjectMapper json;
    private final RulePreflightChecker preflight;
    private final SubjectCatalogResolver catalogResolver;

    public AiResultParser(AiProperties props, ObjectMapper json,
                          RulePreflightChecker preflight, SubjectCatalogResolver catalogResolver) {
        this.props = props;
        this.json = json;
        this.preflight = preflight;
        this.catalogResolver = catalogResolver;
    }

    public CheckOutcome parseQuestionCheck(String taskId, JsonNode input, ChatResult chat) {
        JsonNode root = parseJsonPayload(chat);

        JsonNode analysisNode = root.get("analysis");
        JsonNode suggestionsNode = root.get("suggestions");
        if (suggestionsNode == null || !suggestionsNode.isArray()) {
            throw new LlmException(AiErrorCode.LLM_SCHEMA_MISMATCH,
                    "Thiếu mảng suggestions trong JSON question_check", chat.rawBody(), false);
        }

        Map<SuggestionTargetField, String> fieldTexts = extractFieldTexts(input);
        List<Candidate> accepted = new ArrayList<>();
        Set<String> dedupKeys = new LinkedHashSet<>();

        // 1) Gộp kết quả tiền kiểm tất định (nếu bật)
        if (props.getLlm().isRulePreflight()) {
            for (RulePreflightChecker.Finding f : preflight.check(input)) {
                TextLocator.Span span = f.originalText() == null
                        ? TextLocator.Span.empty()
                        : TextLocator.locate(fieldTexts.get(f.targetField()), f.originalText());
                String key = f.targetField().getValue() + "|" + f.issueType();
                if (dedupKeys.add(key)) {
                    accepted.add(new Candidate(
                            f.issueType(),
                            f.severity(),
                            f.targetField(),
                            span.isFound() ? span.matchedOriginalText() : null,
                            span.start(),
                            span.end(),
                            f.proposedText(),
                            f.explanation(),
                            null));
                }
            }
        }

        Integer suggestedDifficulty = null;
        if (props.getLlm().isSuggestDifficulty()) {
            JsonNode diffNode = root.get("suggested_difficulty");
            if (diffNode != null && diffNode.isInt()) {
                int d = diffNode.asInt();
                if (d >= 1 && d <= 5) {
                    suggestedDifficulty = d;
                }
            }
        }

        // 2) Phân tích & hậu kiểm các gợi ý từ LLM
        for (JsonNode item : suggestionsNode) {
            if (accepted.size() >= MAX_SUGGESTIONS) {
                break;
            }
            if (!item.isObject()) {
                throw new LlmException(AiErrorCode.LLM_SCHEMA_MISMATCH,
                        "Phần tử suggestions không phải object", chat.rawBody(), false);
            }

            String rawIssue = textOrNull(item, "issue_type");
            if (rawIssue == null) {
                continue;
            }
            String issueType = rawIssue.trim().toLowerCase(Locale.ROOT);
            if ("none".equals(issueType)) {
                continue;
            }
            if (!ALLOWED_ISSUE_TYPES.contains(issueType)) {
                throw new LlmException(AiErrorCode.LLM_SCHEMA_MISMATCH,
                        "issue_type không hợp lệ: " + rawIssue, chat.rawBody(), false);
            }

            String rawSeverity = textOrNull(item, "severity");
            SuggestionSeverity severity = rawSeverity == null
                    ? SuggestionSeverity.MEDIUM
                    : SuggestionSeverity.fromValue(rawSeverity.trim().toLowerCase(Locale.ROOT));
            if (severity == null) {
                throw new LlmException(AiErrorCode.LLM_SCHEMA_MISMATCH,
                        "severity không hợp lệ: " + rawSeverity, chat.rawBody(), false);
            }

            String rawField = textOrNull(item, "target_field");
            SuggestionTargetField targetField = rawField == null
                    ? null
                    : SuggestionTargetField.fromValue(rawField.trim().toLowerCase(Locale.ROOT));
            if (targetField == null) {
                throw new LlmException(AiErrorCode.LLM_SCHEMA_MISMATCH,
                        "target_field không hợp lệ hoặc cố sửa correct_label: " + rawField, chat.rawBody(), false);
            }

            String explanation = textOrNull(item, "explanation");
            if (explanation == null) {
                continue;
            }

            String rawOriginal = textOrNull(item, "original_text");
            String proposed = textOrNull(item, "proposed_text");

            // Loại bỏ gợi ý vô nghĩa khi đề xuất sửa giống hệt bản gốc
            if (rawOriginal != null && proposed != null && rawOriginal.equals(proposed)) {
                continue;
            }

            // Định vị span trong field mục tiêu, có fallback sang các field khác nếu bị nhầm target_field
            LocatedField located = locateWithFallback(fieldTexts, targetField, rawOriginal);
            SuggestionTargetField resolvedField = located.field();
            TextLocator.Span span = located.span();

            // Nếu thay thế làm nguyên văn trường không đổi -> loại bỏ
            String currentFieldValue = fieldTexts.get(resolvedField);
            if (currentFieldValue != null && proposed != null
                    && currentFieldValue.trim().equals(proposed)
                    && (span.isFound() && span.matchedOriginalText().equals(currentFieldValue))) {
                continue;
            }

            // Chống ảo giác trích dẫn: spelling/grammar có original_text nhưng không tìm thấy trong đề -> bỏ
            if (rawOriginal != null && !span.isFound()
                    && ("spelling".equals(issueType) || "grammar".equals(issueType))) {
                continue;
            }

            String finalOriginal = span.isFound() ? span.matchedOriginalText() : null;
            Integer spanStart = span.isFound() ? span.start() : null;
            Integer spanEnd = span.isFound() ? span.end() : null;

            String dedupKey = resolvedField.getValue() + "|" + issueType + "|"
                    + (spanStart != null ? spanStart : explanation.substring(0, Math.min(24, explanation.length())));
            if (!dedupKeys.add(dedupKey)) {
                continue;
            }

            accepted.add(new Candidate(
                    issueType,
                    severity,
                    resolvedField,
                    finalOriginal,
                    spanStart,
                    spanEnd,
                    proposed,
                    explanation,
                    accepted.isEmpty() ? suggestedDifficulty : null));
        }

        List<AiSuggestion> entities = new ArrayList<>(accepted.size());
        ArrayNode outSuggestions = json.createArrayNode();
        for (int i = 0; i < accepted.size(); i++) {
            Candidate c = accepted.get(i);
            int seqNo = i + 1;
            entities.add(new AiSuggestion(
                    taskId,
                    seqNo,
                    c.issueType(),
                    c.severity(),
                    c.targetField(),
                    c.originalText(),
                    c.spanStart(),
                    c.spanEnd(),
                    c.explanation(),
                    c.proposedText(),
                    c.suggestedDifficulty()));

            ObjectNode sNode = outSuggestions.addObject();
            sNode.put("seq_no", seqNo);
            sNode.put("issue_type", c.issueType());
            sNode.put("severity", c.severity().getValue());
            sNode.put("target_field", c.targetField().getValue());
            putNullableText(sNode, "original_text", c.originalText());
            if (c.spanStart() != null && c.spanEnd() != null) {
                sNode.put("span_start", c.spanStart());
                sNode.put("span_end", c.spanEnd());
            } else {
                sNode.putNull("span_start");
                sNode.putNull("span_end");
            }
            putNullableText(sNode, "proposed_text", c.proposedText());
            sNode.put("explanation", c.explanation());
        }

        boolean hasIssue = !accepted.isEmpty();
        ObjectNode outRoot = json.createObjectNode();
        ObjectNode outAnalysis = outRoot.putObject("analysis");
        outAnalysis.put("self_solve_reasoning", textOrDefault(analysisNode, "self_solve_reasoning", ""));
        String predicted = normalizePredictedAnswer(textOrNull(analysisNode, "predicted_answer"));
        putNullableText(outAnalysis, "predicted_answer", predicted);
        outAnalysis.put("matches_correct_label",
                analysisNode != null && analysisNode.has("matches_correct_label")
                        ? analysisNode.path("matches_correct_label").asBoolean(true)
                        : matchesCorrectLabel(predicted, textOrNull(input, "correct_label")));

        outRoot.put("has_issue", hasIssue);
        outRoot.put("summary", buildSummary(accepted));
        if (suggestedDifficulty != null) {
            outRoot.put("suggested_difficulty", suggestedDifficulty);
        } else {
            outRoot.putNull("suggested_difficulty");
        }
        outRoot.set("suggestions", outSuggestions);

        try {
            return new CheckOutcome(json.writeValueAsString(outRoot), entities);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không tuần tự hóa được result_json", e);
        }
    }

    public String parseNlSearch(JsonNode input, ChatResult chat) {
        JsonNode root = parseJsonPayload(chat);
        SubjectCatalogResolver.Catalog catalog = catalogResolver.resolve(input);

        String subjectCode = textOrNull(root, "subject_code");
        if (subjectCode != null && !catalog.allowedCodes().isEmpty()
                && !catalog.allowedCodes().contains(subjectCode)) {
            subjectCode = null;
        }

        String topicName = textOrNull(root, "topic_name");
        if (topicName != null && !catalog.allowedTopics().isEmpty()) {
            if (!catalog.allowedTopics().contains(topicName)) {
                topicName = null;
            } else if (subjectCode != null) {
                Set<String> subjectTopics = catalog.topicsByCode().get(subjectCode);
                if (subjectTopics != null && !subjectTopics.isEmpty() && !subjectTopics.contains(topicName)) {
                    topicName = null;
                }
            }
        }

        Integer difficulty = null;
        JsonNode diffNode = root.get("difficulty");
        if (diffNode != null && diffNode.isInt()) {
            int d = diffNode.asInt();
            if (d >= 1 && d <= 5) {
                difficulty = d;
            }
        }

        String validationStatus = textOrNull(root, "validation_status");
        if (validationStatus != null) {
            validationStatus = validationStatus.toLowerCase(Locale.ROOT);
            if (!VALIDATION_STATUSES.contains(validationStatus)) {
                validationStatus = null;
            }
        }

        Set<String> kwSet = new LinkedHashSet<>();
        JsonNode kwNode = root.get("keywords");
        if (kwNode != null && kwNode.isArray()) {
            for (JsonNode k : kwNode) {
                if (k.isTextual() && !k.asText().isBlank()) {
                    kwSet.add(k.asText().trim());
                    if (kwSet.size() >= 8) {
                        break;
                    }
                }
            }
        }

        String searchIn = textOrNull(root, "search_in");
        if (searchIn != null) {
            searchIn = searchIn.toLowerCase(Locale.ROOT);
        }
        if (searchIn == null || !SEARCH_IN_VALUES.contains(searchIn)) {
            searchIn = "all";
        }

        LocalDate fromDate = parseIsoDate(textOrNull(root, "created_from"));
        LocalDate toDate = parseIsoDate(textOrNull(root, "created_to"));
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            LocalDate tmp = fromDate;
            fromDate = toDate;
            toDate = tmp;
        }

        ObjectNode out = json.createObjectNode();
        putNullableText(out, "subject_code", subjectCode);
        putNullableText(out, "topic_name", topicName);
        if (difficulty != null) {
            out.put("difficulty", difficulty);
        } else {
            out.putNull("difficulty");
        }
        putNullableText(out, "validation_status", validationStatus);
        ArrayNode kwArr = out.putArray("keywords");
        kwSet.forEach(kwArr::add);
        out.put("search_in", searchIn);
        putNullableText(out, "created_from", fromDate == null ? null : fromDate.toString());
        putNullableText(out, "created_to", toDate == null ? null : toDate.toString());

        try {
            return json.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không tuần tự hóa được bộ lọc nl_search", e);
        }
    }

    private JsonNode parseJsonPayload(ChatResult chat) {
        String content = chat.content() == null ? "" : stripCodeFence(chat.content().trim());
        JsonNode root;
        try {
            root = json.readTree(content);
        } catch (JsonProcessingException e) {
            if ("length".equalsIgnoreCase(chat.doneReason())) {
                throw new LlmException(AiErrorCode.LLM_TRUNCATED,
                        "Phản hồi LLM bị cắt giữa chừng do chạm giới hạn num_predict", chat.rawBody(), false);
            }
            throw new LlmException(AiErrorCode.LLM_BAD_JSON,
                    "Nội dung LLM không phải JSON hợp lệ: " + e.getOriginalMessage(), chat.rawBody(), false);
        }
        if (!root.isObject()) {
            throw new LlmException(AiErrorCode.LLM_SCHEMA_MISMATCH,
                    "JSON gốc từ LLM phải là object", chat.rawBody(), false);
        }
        return root;
    }

    private static LocatedField locateWithFallback(Map<SuggestionTargetField, String> fieldTexts,
                                                   SuggestionTargetField primaryField,
                                                   String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return new LocatedField(primaryField, TextLocator.Span.empty());
        }
        if (primaryField != SuggestionTargetField.QUESTION) {
            TextLocator.Span direct = TextLocator.locate(fieldTexts.get(primaryField), snippet);
            if (direct.isFound()) {
                return new LocatedField(primaryField, direct);
            }
        }

        // Thử tìm trong các trường text cụ thể nếu target_field ban đầu là "question" hoặc model chỉ nhầm trường
        List<LocatedField> matches = new ArrayList<>();
        for (SuggestionTargetField candidate : List.of(
                SuggestionTargetField.STEM,
                SuggestionTargetField.OPTION_A,
                SuggestionTargetField.OPTION_B,
                SuggestionTargetField.OPTION_C,
                SuggestionTargetField.OPTION_D)) {
            if (candidate == primaryField) {
                continue;
            }
            TextLocator.Span span = TextLocator.locate(fieldTexts.get(candidate), snippet);
            if (span.isFound()) {
                matches.add(new LocatedField(candidate, span));
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        return new LocatedField(primaryField, TextLocator.Span.empty());
    }

    private static Map<SuggestionTargetField, String> extractFieldTexts(JsonNode input) {
        Map<SuggestionTargetField, String> map = new LinkedHashMap<>();
        map.put(SuggestionTargetField.STEM, textOrNull(input, "stem"));
        map.put(SuggestionTargetField.OPTION_A, textOrNull(input, "option_a"));
        map.put(SuggestionTargetField.OPTION_B, textOrNull(input, "option_b"));
        map.put(SuggestionTargetField.OPTION_C, textOrNull(input, "option_c"));
        map.put(SuggestionTargetField.OPTION_D, textOrNull(input, "option_d"));
        return map;
    }

    private static String buildSummary(List<Candidate> accepted) {
        if (accepted.isEmpty()) {
            return "Không phát hiện lỗi.";
        }
        String types = accepted.stream()
                .map(Candidate::issueType)
                .distinct()
                .collect(Collectors.joining(", "));
        return "Phát hiện " + accepted.size() + " vấn đề (" + types + ").";
    }

    private static String normalizePredictedAnswer(String raw) {
        if (raw == null) {
            return null;
        }
        String up = raw.trim().toUpperCase(Locale.ROOT);
        return ("A".equals(up) || "B".equals(up) || "C".equals(up) || "D".equals(up)) ? up : null;
    }

    private static boolean matchesCorrectLabel(String predicted, String correctLabel) {
        if (predicted == null || correctLabel == null) {
            return true;
        }
        return predicted.equalsIgnoreCase(correctLabel.trim());
    }

    private static LocalDate parseIsoDate(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stripCodeFence(String s) {
        if (!s.startsWith("```")) {
            return s;
        }
        int firstNewline = s.indexOf('\n');
        int lastFence = s.lastIndexOf("```");
        if (firstNewline > 0 && lastFence > firstNewline) {
            return s.substring(firstNewline + 1, lastFence).trim();
        }
        return s;
    }

    private static String textOrNull(JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        JsonNode child = node.get(key);
        if (child == null || child.isNull() || !child.isTextual()) {
            return null;
        }
        String s = child.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static String textOrDefault(JsonNode node, String key, String fallback) {
        String val = textOrNull(node, key);
        return val != null ? val : fallback;
    }

    private static void putNullableText(ObjectNode node, String key, String value) {
        if (value == null) {
            node.putNull(key);
        } else {
            node.put(key, value);
        }
    }

    private record LocatedField(SuggestionTargetField field, TextLocator.Span span) {
    }

    private record Candidate(
            String issueType,
            SuggestionSeverity severity,
            SuggestionTargetField targetField,
            String originalText,
            Integer spanStart,
            Integer spanEnd,
            String proposedText,
            String explanation,
            Integer suggestedDifficulty) {
    }
}
