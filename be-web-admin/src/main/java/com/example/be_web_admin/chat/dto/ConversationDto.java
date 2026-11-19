package com.example.be_web_admin.chat.dto;

import java.time.Instant;
import java.util.List;

/** Chi tiết cuộc trò chuyện: mọi trường của tóm tắt cộng danh sách thành viên. */
public record ConversationDto(
        Long id,
        String type,
        String name,
        Long ownerId,
        int memberCount,
        MessageDto lastMessage,
        long unreadCount,
        Instant createdAt,
        List<MemberDto> members) {

    public ConversationSummaryDto toSummary() {
        return new ConversationSummaryDto(id, type, name, ownerId, memberCount, lastMessage, unreadCount, createdAt);
    }
}
