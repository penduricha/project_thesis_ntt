package com.example.be_web_admin.chat.dto;

/** Việc kiểm tra rỗng, quá dài, sai định dạng do service làm để trả đúng mã lỗi (EMPTY_CONTENT, ...). */
public record SendMessageRequest(String clientMsgId, String content) {
}
