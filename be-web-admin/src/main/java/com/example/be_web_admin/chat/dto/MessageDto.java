package com.example.be_web_admin.chat.dto;

import java.time.Instant;

/** Một tin nhắn. createdAt là ISO-8601 UTC, id là số nguyên do server cấp. */
public record MessageDto(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String content,
        Instant createdAt,
        String clientMsgId) {
}
