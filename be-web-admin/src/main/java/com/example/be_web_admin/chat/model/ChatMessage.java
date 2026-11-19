package com.example.be_web_admin.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một tin nhắn văn bản. Không có cột sửa, đã xóa hay updated_at: không có sửa tin và xóa là xóa cứng.
 * Thứ tự tin chính là thứ tự id (tự tăng), cũng là mốc phân trang.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ntt_chat_messages",
        indexes = @Index(name = "idx_chat_msg_conv_id", columnList = "conversation_id, id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_msg_client",
                columnNames = {"conversation_id", "sender_id", "client_msg_id"}))
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** Người gửi. Tin vẫn giữ sender kể cả khi người đó bị kick khỏi nhóm. */
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /** UUID do client sinh, dùng để gửi lại không tạo tin trùng. */
    @Column(name = "client_msg_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String clientMsgId;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;
}
