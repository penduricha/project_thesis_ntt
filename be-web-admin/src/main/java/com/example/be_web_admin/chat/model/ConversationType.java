package com.example.be_web_admin.chat.model;

/** Loại cuộc trò chuyện. Lưu dạng VARCHAR(10), không dùng kiểu ENUM của MariaDB. */
public enum ConversationType {
    DIRECT,
    GROUP
}
