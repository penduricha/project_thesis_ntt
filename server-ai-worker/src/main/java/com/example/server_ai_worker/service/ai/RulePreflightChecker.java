package com.example.server_ai_worker.service.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.example.server_ai_worker.enums.SuggestionSeverity;
import com.example.server_ai_worker.enums.SuggestionTargetField;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Tiền kiểm tất định (không cần LLM) các lỗi cấu trúc chắc chắn sai 100%:
 * - Câu dẫn hoặc phương án A..D bị rỗng
 * - Hai phương án trùng nội dung nhau sau chuẩn hóa
 * - Lộ đáp án rõ ràng trong câu dẫn (vd: "Đáp án: B", "[Answer: A]")
 */
@Component
public class RulePreflightChecker {

    private static final Pattern OPTION_PREFIX = Pattern.compile("^[A-Da-d][.)\\]:]\\s*");
    private static final Pattern LEAK_PATTERN = Pattern.compile(
            "(?i)(?:đáp\\s*án|answer|key)\\s*[:=]\\s*[A-D]\\b");

    public record Finding(
            String issueType,
            SuggestionSeverity severity,
            SuggestionTargetField targetField,
            String originalText,
            String proposedText,
            String explanation) {
    }

    public List<Finding> check(JsonNode input) {
        List<Finding> findings = new ArrayList<>();
        String stem = text(input, "stem");

        if (stem == null || stem.isBlank()) {
            findings.add(new Finding(
                    "logic",
                    SuggestionSeverity.HIGH,
                    SuggestionTargetField.STEM,
                    null,
                    null,
                    "Câu dẫn (stem) đang bị trống, cần bổ sung nội dung câu hỏi."));
        } else {
            var matcher = LEAK_PATTERN.matcher(stem);
            if (matcher.find()) {
                findings.add(new Finding(
                        "logic",
                        SuggestionSeverity.HIGH,
                        SuggestionTargetField.STEM,
                        matcher.group(),
                        "",
                        "Câu dẫn đang bị lộ đáp án trực tiếp (" + matcher.group() + ")."));
            }
        }

        Map<SuggestionTargetField, String> options = new LinkedHashMap<>();
        options.put(SuggestionTargetField.OPTION_A, text(input, "option_a"));
        options.put(SuggestionTargetField.OPTION_B, text(input, "option_b"));
        options.put(SuggestionTargetField.OPTION_C, text(input, "option_c"));
        options.put(SuggestionTargetField.OPTION_D, text(input, "option_d"));

        Map<String, SuggestionTargetField> seen = new LinkedHashMap<>();
        for (var entry : options.entrySet()) {
            SuggestionTargetField field = entry.getKey();
            String val = entry.getValue();
            if (val == null || val.isBlank()) {
                findings.add(new Finding(
                        "logic",
                        SuggestionSeverity.HIGH,
                        field,
                        null,
                        null,
                        "Phương án " + optionLabel(field) + " đang bị trống."));
                continue;
            }
            String norm = normalizeOption(val);
            SuggestionTargetField first = seen.putIfAbsent(norm, field);
            if (first != null) {
                findings.add(new Finding(
                        "distractor",
                        SuggestionSeverity.HIGH,
                        field,
                        val,
                        null,
                        "Phương án " + optionLabel(field) + " trùng nội dung với phương án "
                                + optionLabel(first) + "."));
            }
        }

        return findings;
    }

    private static String normalizeOption(String raw) {
        String nfc = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
        String withoutPrefix = OPTION_PREFIX.matcher(nfc).replaceFirst("");
        return withoutPrefix.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String optionLabel(SuggestionTargetField field) {
        return switch (field) {
            case OPTION_A -> "A";
            case OPTION_B -> "B";
            case OPTION_C -> "C";
            case OPTION_D -> "D";
            default -> field.getValue();
        };
    }

    private static String text(JsonNode input, String key) {
        if (input == null) {
            return null;
        }
        JsonNode node = input.get(key);
        return node == null || node.isNull() ? null : node.asText();
    }
}
