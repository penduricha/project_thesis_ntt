package com.example.be_web_admin.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/** Khóa chính kép (conversation_id, admin_id) của bảng thành viên. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChatMemberId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;
}
