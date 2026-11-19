package com.example.be_web_admin.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Cuộc trò chuyện 1-1 (DIRECT) hoặc nhóm (GROUP). Khớp bảng ntt_chat_conversations (xem sql/2026-11-18-chat-tables.sql).
 * Không dùng quan hệ JPA: các cột *_id là số thường, quan hệ do service kiểm tra, khóa ngoại do file SQL tạo.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ntt_chat_conversations",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_conv_direct_key", columnNames = "direct_key"))
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // columnDefinition ép VARCHAR để Hibernate không sinh kiểu ENUM (bẫy ghi trong sql/2026-11-14-totp-disable-events.sql).
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10, columnDefinition = "varchar(10)")
    private ConversationType type;

    /** Tên nhóm. NULL với DIRECT. */
    @Column(name = "name", length = 100)
    private String name;

    /** "idNhỏ_idLớn" của hai admin. Chỉ DIRECT, UNIQUE. NULL với GROUP (UNIQUE cho phép nhiều NULL). */
    @Column(name = "direct_key", length = 40)
    private String directKey;

    /** Chủ nhóm. NULL với DIRECT. */
    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;
}
