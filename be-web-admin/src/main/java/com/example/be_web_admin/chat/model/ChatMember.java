package com.example.be_web_admin.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Tư cách thành viên của một admin trong một cuộc trò chuyện. Không có cột vai trò: chủ nhóm suy ra từ
 * ChatConversation.ownerId để không lưu cùng một sự thật ở hai nơi.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ntt_chat_members",
        indexes = @Index(name = "idx_chat_member_admin", columnList = "admin_id"))
public class ChatMember implements Persistable<ChatMemberId> {

    @EmbeddedId
    private ChatMemberId id;

    @Column(name = "joined_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant joinedAt;

    /** Mốc đã đọc của riêng thành viên này. Chỉ tăng, không bao giờ giảm. */
    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    /**
     * Khóa chính do ứng dụng đặt (không tự sinh) nên Spring Data không phân biệt được "mới" hay "cũ" và sẽ gọi merge,
     * tức là SELECT trước rồi mới INSERT cho từng dòng. Persistable + cờ này báo thẳng "đây là dòng mới" để chỉ INSERT.
     */
    @Transient
    private boolean newEntity = true;

    public ChatMember(Long conversationId, Long adminId, Instant joinedAt) {
        this.id = new ChatMemberId(conversationId, adminId);
        this.joinedAt = joinedAt;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }
}
