package com.example.be_web_admin.chat.event;

import com.example.be_web_admin.chat.dto.ConversationDto;
import com.example.be_web_admin.chat.dto.MessageDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Sự kiện nội bộ do service phát NGAY TRONG giao dịch. ChatEventDispatcher chỉ gửi ra WebSocket sau khi giao dịch
 * commit thành công; rollback thì không phát gì. recipients() là danh sách adminId nhận, đã chụp sẵn trong giao dịch
 * (kick và giải tán phải chụp TRƯỚC khi xóa dòng thành viên để người bị kick vẫn nhận được sự kiện).
 * type() và data() khớp bảng sự kiện ở mục 7 của tài liệu thiết kế.
 */
public sealed interface ChatDomainEvent {

    Collection<Long> recipients();

    String type();

    Object data();

    record MessageNew(Collection<Long> recipients, MessageDto message) implements ChatDomainEvent {
        @Override
        public String type() {
            return "message.new";
        }

        @Override
        public Object data() {
            return Map.of("message", message);
        }
    }

    record MessageDeleted(Collection<Long> recipients, Long conversationId, Long messageId) implements ChatDomainEvent {
        @Override
        public String type() {
            return "message.deleted";
        }

        @Override
        public Object data() {
            return Map.of("conversationId", conversationId, "messageId", messageId);
        }
    }

    record ConversationCreated(Collection<Long> recipients, ConversationDto conversation) implements ChatDomainEvent {
        @Override
        public String type() {
            return "conversation.created";
        }

        @Override
        public Object data() {
            return Map.of("conversation", conversation);
        }
    }

    record ConversationDissolved(Collection<Long> recipients, Long conversationId) implements ChatDomainEvent {
        @Override
        public String type() {
            return "conversation.dissolved";
        }

        @Override
        public Object data() {
            return Map.of("conversationId", conversationId);
        }
    }

    record MemberRemoved(Collection<Long> recipients, Long conversationId, Long adminId) implements ChatDomainEvent {
        @Override
        public String type() {
            return "member.removed";
        }

        @Override
        public Object data() {
            return Map.of("conversationId", conversationId, "adminId", adminId);
        }
    }

    record ConversationRead(Long adminId, Long conversationId, Long lastReadMessageId) implements ChatDomainEvent {
        @Override
        public Collection<Long> recipients() {
            return List.of(adminId);
        }

        @Override
        public String type() {
            return "conversation.read";
        }

        @Override
        public Object data() {
            return Map.of("conversationId", conversationId, "lastReadMessageId", lastReadMessageId);
        }
    }
}
