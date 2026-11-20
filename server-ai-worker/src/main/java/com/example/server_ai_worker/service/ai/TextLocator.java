package com.example.server_ai_worker.service.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Định vị chuỗi trích dẫn của LLM trong văn bản gốc, trả về vị trí ký tự (UTF-16 index trên chuỗi gốc)
 * thỏa ràng buộc DB chk_sugg_span: (span_start >= 0 AND span_end > span_start) hoặc cả hai đều null.
 *
 * <p>Hỗ trợ khớp 4 tầng:
 * 1) Chính xác nguyên văn;
 * 2) Không phân biệt hoa/thường;
 * 3) Chuẩn hóa NFC + gộp khoảng trắng + bỏ dấu tiếng Việt (giữ ánh xạ ngược về chỉ số chuỗi gốc);
 * 4) Khoảng cách Levenshtein = 1 trên chuỗi đã bỏ dấu (cho trích dẫn >= 5 ký tự bị lệch 1 ký tự).
 */
public final class TextLocator {

    public record Span(Integer start, Integer end, String matchedOriginalText) {
        public static Span empty() {
            return new Span(null, null, null);
        }

        public boolean isFound() {
            return start != null && end != null && start >= 0 && end > start;
        }
    }

    private TextLocator() {
    }

    public static Span locate(String fieldText, String snippet) {
        if (fieldText == null || fieldText.isEmpty() || snippet == null || snippet.isBlank()) {
            return Span.empty();
        }
        String trimmed = snippet.trim();
        if (trimmed.isEmpty()) {
            return Span.empty();
        }

        // Tầng 1: Khớp chính xác
        int exactIdx = fieldText.indexOf(trimmed);
        if (exactIdx >= 0) {
            return spanOf(fieldText, exactIdx, exactIdx + trimmed.length());
        }

        // Tầng 2: Khớp NFC + gộp khoảng trắng + không phân biệt hoa/thường
        IndexedText indexed = indexText(fieldText);
        String normSnippet = normalizeQuery(trimmed, false);
        if (normSnippet.isEmpty()) {
            return Span.empty();
        }

        int normIdx = indexed.normalized.indexOf(normSnippet);
        if (normIdx >= 0) {
            return spanFromIndexed(indexed, fieldText, normIdx, normIdx + normSnippet.length());
        }

        // Tầng 3: Bỏ dấu tiếng Việt + đ->d (vẫn map chính xác về index chuỗi gốc có dấu)
        String strippedSnippet = normalizeQuery(trimmed, true);
        if (strippedSnippet.isEmpty()) {
            return Span.empty();
        }
        int strippedIdx = indexed.stripped.indexOf(strippedSnippet);
        if (strippedIdx >= 0) {
            return spanFromIndexed(indexed, fieldText, strippedIdx, strippedIdx + strippedSnippet.length());
        }

        // Tầng 4: Levenshtein <= 1 cho trích dẫn >= 5 ký tự (xử lý lệch dấu câu/1 ký tự của LLM)
        if (strippedSnippet.length() >= 5 && strippedSnippet.length() <= 120) {
            Span fuzzy = locateLevenshtein1(indexed, fieldText, strippedSnippet);
            if (fuzzy.isFound()) {
                return fuzzy;
            }
        }

        return Span.empty();
    }

    private static Span spanOf(String raw, int start, int end) {
        if (start < 0 || end <= start || end > raw.length()) {
            return Span.empty();
        }
        return new Span(start, end, raw.substring(start, end));
    }

    private static Span spanFromIndexed(IndexedText indexed, String raw, int normStart, int normEndExclusive) {
        if (normStart < 0 || normEndExclusive <= normStart || normEndExclusive > indexed.rawStarts.length) {
            return Span.empty();
        }
        int rawStart = indexed.rawStarts[normStart];
        int rawEnd = indexed.rawEnds[normEndExclusive - 1];
        return spanOf(raw, rawStart, rawEnd);
    }

    private static Span locateLevenshtein1(IndexedText indexed, String raw, String pattern) {
        String text = indexed.stripped;
        int n = text.length();
        int m = pattern.length();
        if (n < Math.max(1, m - 1)) {
            return Span.empty();
        }

        int bestStart = -1;
        int bestEnd = -1;
        int bestDist = 2;

        for (int start = 0; start < n; start++) {
            // Ưu tiên bắt đầu ở ranh giới từ để tránh cắt ngang từ
            if (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))
                    && Character.isLetterOrDigit(text.charAt(start))) {
                continue;
            }
            for (int len = Math.max(1, m - 1); len <= m + 1 && start + len <= n; len++) {
                int end = start + len;
                if (end < n && Character.isLetterOrDigit(text.charAt(end - 1))
                        && Character.isLetterOrDigit(text.charAt(end))) {
                    continue;
                }
                int dist = boundedEditDistance1(text, start, end, pattern);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestStart = start;
                    bestEnd = end;
                    if (dist == 0) {
                        return spanFromIndexed(indexed, raw, bestStart, bestEnd);
                    }
                }
            }
        }

        if (bestDist <= 1 && bestStart >= 0) {
            return spanFromIndexed(indexed, raw, bestStart, bestEnd);
        }
        return Span.empty();
    }

    private static int boundedEditDistance1(String s, int sStart, int sEnd, String p) {
        int lenS = sEnd - sStart;
        int lenP = p.length();
        if (Math.abs(lenS - lenP) > 1) {
            return 2;
        }
        int i = 0;
        int j = 0;
        int edits = 0;
        while (i < lenS && j < lenP) {
            if (s.charAt(sStart + i) == p.charAt(j)) {
                i++;
                j++;
            } else {
                edits++;
                if (edits > 1) {
                    return 2;
                }
                if (lenS > lenP) {
                    i++;
                } else if (lenP > lenS) {
                    j++;
                } else {
                    i++;
                    j++;
                }
            }
        }
        if (i < lenS || j < lenP) {
            edits++;
        }
        return edits;
    }

    private static String normalizeQuery(String input, boolean stripDiacritics) {
        String nfc = Normalizer.normalize(input, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(nfc.length());
        boolean lastSpace = true;
        for (int i = 0; i < nfc.length(); i++) {
            char ch = nfc.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!lastSpace) {
                    out.append(' ');
                    lastSpace = true;
                }
                continue;
            }
            lastSpace = false;
            char mapped = normalizeSingleChar(ch, stripDiacritics);
            if (mapped != 0) {
                out.append(mapped);
            }
        }
        int len = out.length();
        if (len > 0 && out.charAt(len - 1) == ' ') {
            out.setLength(len - 1);
        }
        return out.toString();
    }

    private static IndexedText indexText(String raw) {
        StringBuilder norm = new StringBuilder(raw.length());
        StringBuilder stripped = new StringBuilder(raw.length());
        List<Integer> starts = new ArrayList<>(raw.length());
        List<Integer> ends = new ArrayList<>(raw.length());

        int i = 0;
        boolean lastSpace = true;
        while (i < raw.length()) {
            int clusterStart = i;
            char c0 = raw.charAt(i);
            if (Character.isWhitespace(c0)) {
                while (i < raw.length() && Character.isWhitespace(raw.charAt(i))) {
                    i++;
                }
                if (!lastSpace) {
                    norm.append(' ');
                    stripped.append(' ');
                    starts.add(clusterStart);
                    ends.add(i);
                    lastSpace = true;
                }
                continue;
            }

            i++;
            while (i < raw.length() && Character.getType(raw.charAt(i)) == Character.NON_SPACING_MARK) {
                i++;
            }
            String cluster = raw.substring(clusterStart, i);
            String nfcCluster = Normalizer.normalize(cluster, Normalizer.Form.NFC);
            lastSpace = false;
            for (int k = 0; k < nfcCluster.length(); k++) {
                char nc = nfcCluster.charAt(k);
                char normC = normalizeSingleChar(nc, false);
                char stripC = normalizeSingleChar(nc, true);
                if (normC != 0 && stripC != 0) {
                    norm.append(normC);
                    stripped.append(stripC);
                    starts.add(clusterStart);
                    ends.add(i);
                }
            }
        }

        int n = norm.length();
        if (n > 0 && norm.charAt(n - 1) == ' ') {
            norm.setLength(n - 1);
            stripped.setLength(n - 1);
            starts.remove(n - 1);
            ends.remove(n - 1);
        }

        int[] rawStarts = new int[starts.size()];
        int[] rawEnds = new int[ends.size()];
        for (int idx = 0; idx < starts.size(); idx++) {
            rawStarts[idx] = starts.get(idx);
            rawEnds[idx] = ends.get(idx);
        }
        return new IndexedText(norm.toString(), stripped.toString(), rawStarts, rawEnds);
    }

    private static char normalizeSingleChar(char ch, boolean stripDiacritics) {
        char lower = Character.toLowerCase(ch);
        if (!stripDiacritics) {
            return lower;
        }
        if (lower == 'đ') {
            return 'd';
        }
        String decomposed = Normalizer.normalize(String.valueOf(lower), Normalizer.Form.NFD);
        for (int i = 0; i < decomposed.length(); i++) {
            char d = decomposed.charAt(i);
            if (Character.getType(d) != Character.NON_SPACING_MARK) {
                return d;
            }
        }
        return 0;
    }

    private record IndexedText(String normalized, String stripped, int[] rawStarts, int[] rawEnds) {
    }
}
