package com.example.server_ai_worker.service.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.server_ai_worker.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Chuẩn hóa danh mục môn học / chủ đề cho nl_search từ input_json.subjects (nếu Server 1 truyền)
 * hoặc cấu hình mặc định ai.nl-search.default-subjects khi chạy demo độc lập.
 */
@Component
public class SubjectCatalogResolver {

    public record SubjectEntry(String code, String name, List<String> topics) {
    }

    public record Catalog(List<SubjectEntry> subjects, Set<String> allowedCodes,
                          Set<String> allowedTopics, Map<String, Set<String>> topicsByCode,
                          String catalogHash) {
    }

    private final AiProperties props;

    public SubjectCatalogResolver(AiProperties props) {
        this.props = props;
    }

    public Catalog resolve(JsonNode input) {
        List<SubjectEntry> entries = new ArrayList<>();
        JsonNode rawSubjects = input == null ? null : input.get("subjects");
        if (rawSubjects != null && rawSubjects.isArray() && !rawSubjects.isEmpty()) {
            for (JsonNode s : rawSubjects) {
                String code = firstNonBlank(s, "subject_code", "code");
                String name = firstNonBlank(s, "subject_name", "name");
                if (code == null && name == null) {
                    continue;
                }
                List<String> topics = new ArrayList<>();
                JsonNode rawTopics = s.get("topics");
                if (rawTopics != null && rawTopics.isArray()) {
                    for (JsonNode t : rawTopics) {
                        if (t.isTextual() && !t.asText().isBlank()) {
                            topics.add(t.asText().trim());
                        } else if (t.isObject()) {
                            String tName = firstNonBlank(t, "topic_name", "name");
                            if (tName != null) {
                                topics.add(tName);
                            }
                        }
                    }
                }
                entries.add(new SubjectEntry(
                        code != null ? code : name,
                        name != null ? name : code,
                        List.copyOf(topics)));
            }
        }

        if (entries.isEmpty()) {
            for (AiProperties.SubjectCatalog cfg : props.getNlSearch().getDefaultSubjects()) {
                if (cfg.getSubjectCode() != null && !cfg.getSubjectCode().isBlank()) {
                    List<String> topics = cfg.getTopics() == null ? List.of() : cfg.getTopics().stream()
                            .filter(t -> t != null && !t.isBlank())
                            .map(String::trim)
                            .toList();
                    entries.add(new SubjectEntry(
                            cfg.getSubjectCode().trim(),
                            cfg.getSubjectName() != null ? cfg.getSubjectName().trim() : cfg.getSubjectCode().trim(),
                            topics));
                }
            }
        }

        Set<String> codes = new LinkedHashSet<>();
        Set<String> allTopics = new LinkedHashSet<>();
        Map<String, Set<String>> byCode = new LinkedHashMap<>();
        StringBuilder hashInput = new StringBuilder();
        for (SubjectEntry e : entries) {
            codes.add(e.code());
            Set<String> tSet = byCode.computeIfAbsent(e.code(), ignored -> new LinkedHashSet<>());
            hashInput.append(e.code()).append('|').append(e.name()).append(':');
            for (String t : e.topics()) {
                allTopics.add(t);
                tSet.add(t);
                hashInput.append(t).append(',');
            }
            hashInput.append(';');
        }

        return new Catalog(
                Collections.unmodifiableList(entries),
                Collections.unmodifiableSet(codes),
                Collections.unmodifiableSet(allTopics),
                Collections.unmodifiableMap(byCode),
                sha256Short(hashInput.toString()));
    }

    private static String firstNonBlank(JsonNode node, String k1, String k2) {
        JsonNode a = node.get(k1);
        if (a != null && a.isTextual() && !a.asText().isBlank()) {
            return a.asText().trim();
        }
        JsonNode b = node.get(k2);
        if (b != null && b.isTextual() && !b.asText().isBlank()) {
            return b.asText().trim();
        }
        return null;
    }

    private static String sha256Short(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8 && i < digest.length; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
