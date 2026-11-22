package com.example.be_web_admin.qbank;

import com.example.be_web_admin.enums.qbank.ContentFormatD;
import com.example.be_web_admin.enums.qbank.OptionLabelD;
import com.example.be_web_admin.service.qbank.QuestionContentHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Khoá công thức content_hash bằng test vector. Nếu ai đổi thứ tự trường, ký tự phân tách
 * hoặc cách chuẩn hoá thì test này đỏ ngay — cần vậy vì worker/script Python phải tính ra
 * CÙNG một hash để chống trùng và so khớp round-trip Moodle.
 */
class QuestionContentHasherTest {

    private static final String STEM = "Thủ đô của Việt Nam là gì?";
    private static final String A = "Hà Nội";
    private static final String B = "Huế";
    private static final String C = "Đà Nẵng";
    private static final String D = "TP. Hồ Chí Minh";

    /** Vector chuẩn — tính độc lập bằng Python (hashlib.sha256) theo đúng đặc tả trong javadoc. */
    private static final String EXPECTED = "46e00b1b19a54b94dae191a555b7299a6cb1821a180c8148669ace2c9f3ef727";

    @Test
    @DisplayName("Hash khớp vector đã chốt trong tài liệu")
    void matchesDocumentedVector() {
        assertEquals(EXPECTED, QuestionContentHasher.hash(STEM, A, B, C, D,
                OptionLabelD.A, ContentFormatD.PLAIN));
    }

    @Test
    @DisplayName("Chuỗi NFD và NFC cho cùng hash (dấu tiếng Việt gõ kiểu khác nhau)")
    void nfdAndNfcGiveSameHash() {
        String nfd = Normalizer.normalize(STEM, Normalizer.Form.NFD);
        assertNotEquals(STEM, nfd, "điều kiện test: hai dạng phải khác byte");
        assertEquals(
                QuestionContentHasher.hash(STEM, A, B, C, D, OptionLabelD.A, ContentFormatD.PLAIN),
                QuestionContentHasher.hash(nfd, A, B, C, D, OptionLabelD.A, ContentFormatD.PLAIN));
    }

    @Test
    @DisplayName("Khoảng trắng thừa và xuống dòng không làm đổi hash")
    void whitespaceIsNormalized() {
        String messy = "  Thủ đô của Việt Nam\n là gì?  ";
        assertEquals(
                QuestionContentHasher.hash(STEM, A, B, C, D, OptionLabelD.A, ContentFormatD.PLAIN),
                QuestionContentHasher.hash(messy, A, B, C, D, OptionLabelD.A, ContentFormatD.PLAIN));
    }

    @Test
    @DisplayName("Đổi đáp án đúng hoặc content_format đều phải đổi hash")
    void labelAndFormatParticipate() {
        String base = QuestionContentHasher.hash(STEM, A, B, C, D, OptionLabelD.A, ContentFormatD.PLAIN);
        assertNotEquals(base, QuestionContentHasher.hash(STEM, A, B, C, D, OptionLabelD.B, ContentFormatD.PLAIN));
        assertNotEquals(base, QuestionContentHasher.hash(STEM, A, B, C, D, OptionLabelD.A, ContentFormatD.HTML));
    }

    @Test
    @DisplayName("Ký tự phân tách chặn va chạm kiểu ghép chuỗi")
    void separatorPreventsConcatenationCollision() {
        String first = QuestionContentHasher.hash("ab", "c", "x", "y", "z", OptionLabelD.A, ContentFormatD.PLAIN);
        String second = QuestionContentHasher.hash("a", "bc", "x", "y", "z", OptionLabelD.A, ContentFormatD.PLAIN);
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("Hash luôn là 64 ký tự hex thường — khớp CHAR(64) + chk_q_hash_len")
    void hashLengthMatchesColumn() {
        String hash = QuestionContentHasher.hash("a", "b", "c", "d", "e", OptionLabelD.D, ContentFormatD.PLAIN);
        assertEquals(64, hash.length());
        assertFalse(hash.chars().anyMatch(Character::isUpperCase));
    }
}
