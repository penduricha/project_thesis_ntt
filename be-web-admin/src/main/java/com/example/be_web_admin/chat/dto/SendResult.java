package com.example.be_web_admin.chat.dto;

/** Kết quả gửi tin: created=true thì trả 201; gửi lại cùng clientMsgId thì created=false và trả 200. */
public record SendResult(MessageDto message, boolean created) {
}
