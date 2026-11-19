package com.example.be_web_admin.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Thành viên của một cuộc trò chuyện. isOwner chỉ có ý nghĩa với nhóm. */
public record MemberDto(
        Long adminId,
        String adminName,
        @JsonProperty("isOwner") boolean isOwner) {
}
