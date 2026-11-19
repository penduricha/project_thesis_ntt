package com.example.be_web_admin.chat.dto;

/** Vé mở WebSocket, dùng một lần, hết hạn sau expiresInSec giây. */
public record WsTicketDto(String ticket, int expiresInSec) {
}
