package com.example.server_ai_worker.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.Normalizer;

import org.junit.jupiter.api.Test;

class TextLocatorTest {

    @Test
    void exactMatchReturnsExactUtf16Offsets() {
        String text = "Trong mô hình quan hệ, khóa chính dùng để lam gì?";
        TextLocator.Span span = TextLocator.locate(text, "dùng để lam gì?");
        assertTrue(span.isFound());
        assertEquals("dùng để lam gì?", text.substring(span.start(), span.end()));
    }

    @Test
    void nfdFieldWithNfcSnippetMapsBackToRawIndices() {
        String rawNfd = Normalizer.normalize("Khóa chính xác định duy nhất mỗi bộ.", Normalizer.Form.NFD);
        String snippetNfc = Normalizer.normalize("xác định duy nhất", Normalizer.Form.NFC);
        TextLocator.Span span = TextLocator.locate(rawNfd, snippetNfc);
        assertTrue(span.isFound());
        assertEquals(span.matchedOriginalText(), rawNfd.substring(span.start(), span.end()));
        assertTrue(Normalizer.normalize(span.matchedOriginalText(), Normalizer.Form.NFC).contains("xác định"));
    }

    @Test
    void diacriticStrippedAndWhitespaceCollapsedMatchesOriginalText() {
        String raw = "Phương án   này  đồng nghĩa với đáp án đúng.";
        TextLocator.Span span = TextLocator.locate(raw, "dong nghia voi dap an");
        assertTrue(span.isFound());
        assertEquals("đồng nghĩa với đáp án", raw.substring(span.start(), span.end()));
    }

    @Test
    void levenshteinOneMatchesMinorQuoteDeviation() {
        String raw = "The quarterly financial report will be reviewed next Monday.";
        TextLocator.Span span = TextLocator.locate(raw, "finacial report");
        assertTrue(span.isFound());
        assertEquals("financial report", raw.substring(span.start(), span.end()));
    }

    @Test
    void missingSnippetReturnsEmptySpanSafeForDbCheck() {
        String raw = "Giao thức HTTP mặc định dùng cổng nào?";
        TextLocator.Span span = TextLocator.locate(raw, "chuỗi không hề tồn tại");
        assertFalse(span.isFound());
        assertNull(span.start());
        assertNull(span.end());
        assertNull(span.matchedOriginalText());
    }
}
