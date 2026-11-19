package com.example.be_web_admin.chat.service;

import com.example.be_web_admin.chat.exception.ChatException;

import java.util.regex.Pattern;

/**
 * Chuẩn hóa và kiểm tra dữ liệu người dùng gõ: nội dung tin, tên nhóm, clientMsgId.
 * Độ dài đo theo ký tự Unicode (code point), không theo đơn vị UTF-16 của Java, để một emoji tính là 1 ký tự
 * và khớp VARCHAR(2000) của MariaDB utf8mb4.
 */
public final class ChatContent {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private ChatContent() {
    }

    /**
     * Cắt khoảng trắng hai đầu, đổi \r\n thành \n, bỏ NUL và mọi ký tự điều khiển trừ xuống dòng và tab.
     * Rỗng thì 400 EMPTY_CONTENT, quá dài thì 400 CONTENT_TOO_LONG.
     */
    public static String normalizeMessage(String raw, int maxLength) {
        if (raw == null) {
            throw ChatException.badRequest("EMPTY_CONTENT", "Nội dung tin nhắn không được để trống.");
        }
        // Chặn sớm chuỗi khổng lồ để không phải quét cả chục MB. 4 đơn vị UTF-16 mỗi ký tự là mức dư dả
        // (một ký tự Unicode chiếm tối đa 2), phần dư cho khoảng trắng hai đầu sẽ bị cắt ở bước dưới.
        if (raw.length() > (long) maxLength * 4) {
            throw tooLong(maxLength);
        }
        String text = stripControlChars(raw.replace("\r\n", "\n")).strip();
        if (text.isEmpty()) {
            throw ChatException.badRequest("EMPTY_CONTENT", "Nội dung tin nhắn không được để trống.");
        }
        if (text.codePointCount(0, text.length()) > maxLength) {
            throw tooLong(maxLength);
        }
        return text;
    }

    /** Tên nhóm: bỏ ký tự điều khiển (kể cả xuống dòng), cắt hai đầu, không rỗng, không quá maxLength ký tự. */
    public static String normalizeGroupName(String raw, int maxLength) {
        if (raw == null) {
            throw ChatException.validation("Tên nhóm không được để trống.");
        }
        if (raw.length() > (long) maxLength * 4) {
            throw ChatException.validation("Tên nhóm tối đa " + maxLength + " ký tự.");
        }
        StringBuilder sb = new StringBuilder(raw.length());
        raw.codePoints().forEach(cp -> {
            if (!Character.isISOControl(cp)) {
                sb.appendCodePoint(cp);
            }
        });
        String name = sb.toString().strip();
        if (name.isEmpty()) {
            throw ChatException.validation("Tên nhóm không được để trống.");
        }
        if (name.codePointCount(0, name.length()) > maxLength) {
            throw ChatException.validation("Tên nhóm tối đa " + maxLength + " ký tự.");
        }
        return name;
    }

    public static String requireClientMsgId(String clientMsgId) {
        if (clientMsgId == null || !UUID_FORMAT.matcher(clientMsgId).matches()) {
            throw ChatException.badRequest("INVALID_CLIENT_MSG_ID", "clientMsgId phải là UUID.");
        }
        return clientMsgId;
    }

    private static String stripControlChars(String text) {
        StringBuilder sb = null;
        int length = text.length();
        for (int i = 0; i < length; ) {
            int cp = text.codePointAt(i);
            int width = Character.charCount(cp);
            boolean drop = Character.isISOControl(cp) && cp != '\n' && cp != '\t';
            if (drop && sb == null) {
                sb = new StringBuilder(length);
                sb.append(text, 0, i);
            } else if (!drop && sb != null) {
                sb.appendCodePoint(cp);
            }
            i += width;
        }
        return sb == null ? text : sb.toString();
    }

    private static ChatException tooLong(int maxLength) {
        return ChatException.badRequest("CONTENT_TOO_LONG", "Tin nhắn tối đa " + maxLength + " ký tự.");
    }
}
