package com.example.be_web_admin.chat.service;

import com.example.be_web_admin.chat.dto.ConversationDto;
import com.example.be_web_admin.chat.dto.MemberDto;
import com.example.be_web_admin.chat.dto.MessageDto;
import com.example.be_web_admin.chat.model.ChatConversation;
import com.example.be_web_admin.chat.model.ChatMember;
import com.example.be_web_admin.chat.model.ChatMessage;
import com.example.be_web_admin.chat.model.ConversationType;
import com.example.be_web_admin.chat.repository.ChatAdminRepository;
import com.example.be_web_admin.chat.repository.ChatMemberRepository;
import com.example.be_web_admin.chat.repository.ChatMessageRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dựng DTO từ entity. Open-in-view đang tắt nên việc ánh xạ phải làm trong giao dịch của service gọi vào đây.
 * Danh sách cuộc trò chuyện chỉ tốn 4 truy vấn gộp (thành viên, tin cuối, chưa đọc, tên admin), không lặp theo từng cuộc.
 */
@Component
public class ChatAssembler {

    private final ChatMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatAdminRepository adminRepository;

    public ChatAssembler(ChatMemberRepository memberRepository,
                         ChatMessageRepository messageRepository,
                         ChatAdminRepository adminRepository) {
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.adminRepository = adminRepository;
    }

    /* ------------------------------ tin nhắn ------------------------------ */

    public Map<Long, String> namesOf(Collection<Long> adminIds) {
        Map<Long, String> names = new HashMap<>();
        if (adminIds.isEmpty()) {
            return names;
        }
        for (ChatAdminRepository.AdminLite admin : adminRepository.findLiteByIds(adminIds)) {
            names.put(admin.getAdminId(), admin.getAdminName());
        }
        return names;
    }

    public String nameOf(Map<Long, String> names, Long adminId) {
        String name = names.get(adminId);
        return name != null && !name.isBlank() ? name : "Admin " + adminId;
    }

    public MessageDto toMessage(ChatMessage message, Map<Long, String> names) {
        return new MessageDto(
                message.getId(),
                message.getConversationId(),
                message.getSenderId(),
                nameOf(names, message.getSenderId()),
                message.getContent(),
                message.getCreatedAt(),
                message.getClientMsgId());
    }

    public MessageDto toMessage(ChatMessage message) {
        return toMessage(message, namesOf(List.of(message.getSenderId())));
    }

    public List<MessageDto> toMessages(List<ChatMessage> messages) {
        Set<Long> senderIds = new HashSet<>();
        for (ChatMessage message : messages) {
            senderIds.add(message.getSenderId());
        }
        Map<Long, String> names = namesOf(senderIds);
        List<MessageDto> result = new ArrayList<>(messages.size());
        for (ChatMessage message : messages) {
            result.add(toMessage(message, names));
        }
        return result;
    }

    /* --------------------------- cuộc trò chuyện --------------------------- */

    public ConversationDto toConversation(Long viewerId, ChatConversation conversation) {
        return toConversations(viewerId, List.of(conversation)).get(0);
    }

    /**
     * Dựng danh sách cuộc cho một người xem, sắp theo hoạt động mới nhất (thời điểm tin cuối, nếu chưa có tin thì
     * thời điểm tạo), cuộc mới hơn đứng trước.
     */
    public List<ConversationDto> toConversations(Long viewerId, Collection<ChatConversation> conversations) {
        if (conversations.isEmpty()) {
            return List.of();
        }
        List<Long> ids = conversations.stream().map(ChatConversation::getId).toList();

        Map<Long, List<ChatMember>> membersByConversation = new HashMap<>();
        Set<Long> adminIds = new HashSet<>();
        for (ChatMember member : memberRepository.findByConversationIds(ids)) {
            membersByConversation.computeIfAbsent(member.getId().getConversationId(), k -> new ArrayList<>()).add(member);
            adminIds.add(member.getId().getAdminId());
        }

        Map<Long, ChatMessage> lastByConversation = new HashMap<>();
        for (ChatMessage message : messageRepository.findLastMessages(ids)) {
            lastByConversation.put(message.getConversationId(), message);
            adminIds.add(message.getSenderId());
        }

        Map<Long, Long> unreadByConversation = new HashMap<>();
        for (Object[] row : messageRepository.countUnread(viewerId, ids)) {
            unreadByConversation.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        Map<Long, String> names = namesOf(adminIds);

        List<ConversationDto> result = new ArrayList<>(conversations.size());
        for (ChatConversation conversation : conversations) {
            List<ChatMember> members = membersByConversation.getOrDefault(conversation.getId(), List.of());
            ChatMessage last = lastByConversation.get(conversation.getId());
            result.add(build(viewerId, conversation, members, names, last,
                    unreadByConversation.getOrDefault(conversation.getId(), 0L)));
        }
        result.sort(Comparator
                .comparing((ConversationDto c) -> lastActivity(c), Comparator.reverseOrder())
                .thenComparing(ConversationDto::id, Comparator.reverseOrder()));
        return result;
    }

    private ConversationDto build(Long viewerId, ChatConversation conversation, List<ChatMember> members,
                                  Map<Long, String> names, ChatMessage last, long unread) {
        List<MemberDto> memberDtos = new ArrayList<>(members.size());
        Long otherId = null;
        for (ChatMember member : members) {
            Long adminId = member.getId().getAdminId();
            boolean owner = adminId.equals(conversation.getOwnerId());
            memberDtos.add(new MemberDto(adminId, nameOf(names, adminId), owner));
            if (!adminId.equals(viewerId)) {
                otherId = adminId;
            }
        }
        memberDtos.sort(Comparator.comparing((MemberDto m) -> !m.isOwner())
                .thenComparing(m -> m.adminName() == null ? "" : m.adminName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MemberDto::adminId));

        String name;
        if (conversation.getType() == ConversationType.DIRECT) {
            // Với 1-1, tên cuộc là tên người còn lại.
            name = otherId != null ? nameOf(names, otherId) : nameOf(names, viewerId);
        } else {
            name = conversation.getName();
        }
        MessageDto lastDto = last == null ? null : toMessage(last, names);
        return new ConversationDto(
                conversation.getId(),
                conversation.getType().name(),
                name,
                conversation.getOwnerId(),
                memberDtos.size(),
                lastDto,
                unread,
                conversation.getCreatedAt(),
                memberDtos);
    }

    private static Instant lastActivity(ConversationDto conversation) {
        return conversation.lastMessage() != null ? conversation.lastMessage().createdAt() : conversation.createdAt();
    }
}
