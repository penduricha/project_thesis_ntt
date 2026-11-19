package com.example.be_web_admin.chat.dto;

/** Kết quả mở chat 1-1: created=true thì trả 201, ngược lại 200. */
public record ConversationResult(ConversationDto conversation, boolean created) {
}
