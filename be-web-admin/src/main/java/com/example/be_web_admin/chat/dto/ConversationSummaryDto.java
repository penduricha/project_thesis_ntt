package com.example.be_web_admin.chat.dto;

import java.time.Instant;

/** Tóm tắt cuộc trò chuyện cho danh sách (endpoint 3). Với 1-1, name là tên người còn lại. */
public record ConversationSummaryDto(
        Long id,
        String type,
        String name,
        Long ownerId,
        int memberCount,
        MessageDto lastMessage,
        long unreadCount,
        Instant createdAt) {
}
