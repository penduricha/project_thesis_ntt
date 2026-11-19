package com.example.be_web_admin.chat.dto;

import java.util.List;

/** Một trang tin, tin mới nhất đứng đầu. hasMore=false nghĩa là đã hết lịch sử. */
public record MessagesPageDto(List<MessageDto> messages, boolean hasMore) {
}
