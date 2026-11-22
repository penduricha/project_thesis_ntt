package com.example.be_web_admin.service.qbank;

import com.example.be_web_admin.enums.qbank.ContentFormatD;
import com.example.be_web_admin.enums.qbank.OptionLabelD;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Tính ntt_questions.content_hash — cột NOT NULL, dùng để cảnh báo trùng nội dung và so khớp
 * round-trip khi xuất/nhập Moodle.
 *
 * <p>Đặc tả (phải giữ nguyên khi có code khác cũng tính hash, vd worker hoặc script Python):</p>
 * <pre>
 *   chuẩn hoá = NFC, trim, gộp mọi khoảng trắng liên tiếp thành 1 dấu cách
 *   payload   = norm(stem) + 0x01 + norm(A) + 0x01 + norm(B) + 0x01 + norm(C) + 0x01 + norm(D)
 *             + 0x01 + correctLabel + 0x01 + contentFormat
 *   hash      = SHA-256(payload UTF-8) dạng hex thường (64 ký tự)
 * </pre>
 *
 * <p>0x01 là ký tự phân tách không thể có trong văn bản người dùng nhập, nên hai câu khác nhau
 * không thể sinh cùng payload. Hoa/thường được GIỮ NGUYÊN (chỉ dấu tiếng Việt được chuẩn hoá về
 * NFC) để "Hà Nội" và "hà nội" vẫn là hai nội dung khác nhau — đúng ngữ nghĩa môn học.</p>
 */
public final class QuestionContentHasher {

    /** Ký tự phân tách trường (U+0001). */
    public static final String SEPARATOR = "\u0001";

    private QuestionContentHasher() {
    }

    public static String hash(String stem,
                              String optionA,
                              String optionB,
                              String optionC,
                              String optionD,
                              OptionLabelD correctLabel,
                              ContentFormatD contentFormat) {
        String payload = normalize(stem)
                + SEPARATOR + normalize(optionA)
                + SEPARATOR + normalize(optionB)
                + SEPARATOR + normalize(optionC)
                + SEPARATOR + normalize(optionD)
                + SEPARATOR + (correctLabel == null ? "" : correctLabel.dbValue())
                + SEPARATOR + (contentFormat == null ? "" : contentFormat.dbValue());
        return sha256Hex(payload);
    }

    /** NFC + trim + gộp khoảng trắng. Public để test và để script khác dùng lại công thức. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        return nfc.trim().replaceAll("\\s+", " ");
    }

    public static String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM thiếu SHA-256", e);
        }
    }

    /** Câu hỏi coi là "trùng" khi hash khớp — so sánh không phân biệt hoa/thường của hex. */
    public static boolean sameContent(String hashA, String hashB) {
        return hashA != null && hashB != null && hashA.toLowerCase(Locale.ROOT).equals(hashB.toLowerCase(Locale.ROOT));
    }
}
