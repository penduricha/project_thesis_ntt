package com.example.be_web_admin.chat.service;

import com.example.be_web_admin.chat.ChatProperties;
import com.example.be_web_admin.chat.dto.ConversationDto;
import com.example.be_web_admin.chat.dto.ConversationResult;
import com.example.be_web_admin.chat.dto.ConversationSummaryDto;
import com.example.be_web_admin.chat.dto.CreateGroupRequest;
import com.example.be_web_admin.chat.event.ChatDomainEvent;
import com.example.be_web_admin.chat.exception.ChatException;
import com.example.be_web_admin.chat.model.ChatConversation;
import com.example.be_web_admin.chat.model.ChatMember;
import com.example.be_web_admin.chat.model.ChatMemberId;
import com.example.be_web_admin.chat.model.ConversationType;
import com.example.be_web_admin.chat.repository.ChatAdminRepository;
import com.example.be_web_admin.chat.repository.ChatConversationRepository;
import com.example.be_web_admin.chat.repository.ChatMemberRepository;
import com.example.be_web_admin.chat.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cuộc trò chuyện: danh sách, chi tiết, mở chat 1-1, tạo nhóm, kick, giải tán, đánh dấu đã đọc.
 *
 * Mỗi thao tác ghi là một giao dịch NGẮN do TransactionTemplate điều khiển (không dùng @Transactional để tránh
 * lỗi tự gọi nội bộ và để bắt ngoại lệ UNIQUE ở NGOÀI giao dịch). Sự kiện WebSocket được publish trong giao dịch
 * và chỉ chạy sau khi commit (xem ChatEventDispatcher). Redis (giới hạn tốc độ) luôn gọi TRƯỚC khi mở giao dịch.
 * Thứ tự khóa luôn là dòng cuộc trò chuyện trước, rồi mới tới thành viên và tin, để không deadlock.
 */
@Slf4j
@Service
public class ConversationService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatAdminRepository adminRepository;
    private final ChatAssembler assembler;
    private final ChatRateLimiter rateLimiter;
    private final ChatProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public ConversationService(ChatConversationRepository conversationRepository,
                               ChatMemberRepository memberRepository,
                               ChatMessageRepository messageRepository,
                               ChatAdminRepository adminRepository,
                               ChatAssembler assembler,
                               ChatRateLimiter rateLimiter,
                               ChatProperties properties,
                               ApplicationEventPublisher events,
                               PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.adminRepository = adminRepository;
        this.assembler = assembler;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    /* ================================ đọc ================================ */

    /** Endpoint 3. Cuộc 1-1 chưa có tin nào thì không trả (người kia không thấy phòng rỗng). */
    public List<ConversationSummaryDto> listMine(Long adminId) {
        return readTx.execute(status -> {
            List<Long> ids = memberRepository.findByAdminId(adminId).stream()
                    .map(m -> m.getId().getConversationId())
                    .toList();
            if (ids.isEmpty()) {
                return List.<ConversationSummaryDto>of();
            }
            List<ConversationDto> dtos = assembler.toConversations(adminId, conversationRepository.findAllById(ids));
            return dtos.stream()
                    .filter(c -> ConversationType.GROUP.name().equals(c.type()) || c.lastMessage() != null)
                    .map(ConversationDto::toSummary)
                    .toList();
        });
    }

    /** Endpoint 4. Không phải thành viên thì 404 giống như cuộc không tồn tại. */
    public ConversationDto get(Long adminId, Long conversationId) {
        return readTx.execute(status -> {
            requireMember(conversationId, adminId);
            ChatConversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(ChatException::notFound);
            return assembler.toConversation(adminId, conversation);
        });
    }

    /* ============================== chat 1-1 ============================== */

    /** Endpoint 5. Idempotent: đã có thì trả lại (200), chưa có thì tạo (201). Không phát sự kiện (mục 9 của thiết kế). */
    public ConversationResult openDirect(Long adminId, Long peerId) {
        if (peerId == null) {
            throw ChatException.validation("Thiếu peerId.");
        }
        if (peerId.equals(adminId)) {
            throw ChatException.badRequest("CANNOT_CHAT_SELF", "Không thể tự nhắn tin cho chính mình.");
        }
        String directKey = directKey(adminId, peerId);

        ConversationDto existing = findDirect(adminId, directKey);
        if (existing != null) {
            return new ConversationResult(existing, false);
        }
        if (adminRepository.countById(peerId) == 0) {
            throw ChatException.notFound("PEER_NOT_FOUND", "Không tìm thấy người dùng này.");
        }
        rateLimiter.check("new", adminId, properties.getRate().getNewConversationsPerMinute(), 60);

        try {
            ConversationDto created = tx.execute(status -> {
                ChatConversation conversation = new ChatConversation();
                conversation.setType(ConversationType.DIRECT);
                conversation.setDirectKey(directKey);
                conversation.setCreatedAt(now());
                conversation = conversationRepository.saveAndFlush(conversation);
                Instant joined = now();
                memberRepository.saveAll(List.of(
                        new ChatMember(conversation.getId(), adminId, joined),
                        new ChatMember(conversation.getId(), peerId, joined)));
                memberRepository.flush();
                return assembler.toConversation(adminId, conversation);
            });
            return new ConversationResult(created, true);
        } catch (DataIntegrityViolationException e) {
            // Hai người cùng mở lần đầu: giao dịch thua bị UNIQUE(direct_key) từ chối. Giao dịch đã rollback,
            // đọc lại trong giao dịch mới và trả cuộc do người thắng tạo.
            ConversationDto winner = findDirect(adminId, directKey);
            if (winner == null) {
                // Vi phạm ràng buộc khác (ví dụ khóa ngoại: peer vừa bị xóa) thì không che giấu.
                throw e;
            }
            return new ConversationResult(winner, false);
        }
    }

    private ConversationDto findDirect(Long adminId, String directKey) {
        return readTx.execute(status -> conversationRepository.findByDirectKey(directKey)
                .filter(c -> memberRepository.existsById(new ChatMemberId(c.getId(), adminId)))
                .map(c -> assembler.toConversation(adminId, c))
                .orElse(null));
    }

    /* ================================ nhóm ================================ */

    /** Endpoint 6. memberIds không gồm người tạo; tổng thành viên (cả người tạo) từ min-members. */
    public ConversationDto createGroup(Long adminId, CreateGroupRequest request) {
        ChatProperties.Group limits = properties.getGroup();
        String name = ChatContent.normalizeGroupName(request.name(), limits.getMaxNameLength());

        List<Long> memberIds = request.memberIds();
        if (memberIds == null) {
            throw ChatException.validation("Thiếu danh sách thành viên.");
        }
        Set<Long> unique = new HashSet<>();
        for (Long id : memberIds) {
            if (id == null) {
                throw ChatException.validation("Danh sách thành viên có phần tử rỗng.");
            }
            if (id.equals(adminId)) {
                throw ChatException.validation("Danh sách thành viên không được chứa chính bạn.");
            }
            if (!unique.add(id)) {
                throw ChatException.validation("Danh sách thành viên bị trùng.");
            }
        }
        int total = unique.size() + 1;
        if (total < limits.getMinMembers()) {
            throw ChatException.badRequest("GROUP_MIN_SIZE",
                    "Nhóm phải có ít nhất " + limits.getMinMembers() + " thành viên (kể cả bạn).");
        }
        if (total > limits.getMaxMembers()) {
            throw ChatException.badRequest("GROUP_MAX_SIZE",
                    "Nhóm có tối đa " + limits.getMaxMembers() + " thành viên.");
        }
        if (adminRepository.countByIds(unique) != unique.size()) {
            throw ChatException.badRequest("UNKNOWN_MEMBER", "Có thành viên không tồn tại.");
        }
        rateLimiter.check("new", adminId, properties.getRate().getNewConversationsPerMinute(), 60);

        return tx.execute(status -> {
            Instant now = now();
            ChatConversation conversation = new ChatConversation();
            conversation.setType(ConversationType.GROUP);
            conversation.setName(name);
            conversation.setOwnerId(adminId);
            conversation.setCreatedAt(now);
            conversation = conversationRepository.saveAndFlush(conversation);

            List<ChatMember> members = new ArrayList<>(total);
            members.add(new ChatMember(conversation.getId(), adminId, now));
            for (Long id : unique) {
                members.add(new ChatMember(conversation.getId(), id, now));
            }
            memberRepository.saveAll(members);
            memberRepository.flush();

            ConversationDto dto = assembler.toConversation(adminId, conversation);
            List<Long> recipients = unique.stream().toList();
            events.publishEvent(new ChatDomainEvent.ConversationCreated(recipients, dto));
            return dto;
        });
    }

    /** Endpoint 8. Khóa độc quyền trên dòng cuộc: chờ mọi người đang gửi tin xong rồi chặn người gửi mới. */
    public ConversationDto kick(Long adminId, Long conversationId, Long targetId) {
        int minMembers = properties.getGroup().getMinMembers();
        // Người ngoài cuộc bị từ chối ở giao dịch riêng trước khi lấy khóa, để dò id không giữ được khóa độc quyền.
        readTx.executeWithoutResult(status -> requireMember(conversationId, adminId));
        return tx.execute(status -> {
            // Câu đầu tiên của giao dịch phải là câu lấy khóa (FOR UPDATE), rồi mới đọc thành viên: nếu đọc thường trước
            // thì REPEATABLE READ lập ảnh chụp cũ và mọi kiểm tra dưới khóa (số thành viên, ai còn trong nhóm) đọc phải
            // dữ liệu trước khi người giữ khóa trước ta commit.
            ChatConversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                    .orElseThrow(ChatException::notFound);
            requireMember(conversationId, adminId);
            requireOwner(conversation, adminId);

            if (targetId == null || targetId.equals(conversation.getOwnerId())) {
                throw ChatException.badRequest("TARGET_IS_OWNER", "Không thể kick chủ nhóm.");
            }
            ChatMemberId targetKey = new ChatMemberId(conversationId, targetId);
            if (!memberRepository.existsById(targetKey)) {
                throw ChatException.badRequest("NOT_A_MEMBER", "Người này không phải thành viên của nhóm.");
            }
            long count = memberRepository.countByConversationId(conversationId);
            if (count - 1 < minMembers) {
                throw ChatException.conflict("GROUP_MIN_SIZE", "Nhóm phải còn ít nhất " + minMembers + " thành viên.");
            }
            // Chụp người nhận TRƯỚC khi xóa để người bị kick vẫn nhận được member.removed.
            List<Long> recipients = memberRepository.findAdminIdsByConversationId(conversationId);
            memberRepository.deleteById(targetKey);
            memberRepository.flush();
            events.publishEvent(new ChatDomainEvent.MemberRemoved(recipients, conversationId, targetId));
            return assembler.toConversation(adminId, conversation);
        });
    }

    /** Endpoint 7. Xóa cứng: tin, rồi thành viên, rồi dòng cuộc, theo thứ tự rõ ràng (không dựa vào cascade). */
    public void dissolve(Long adminId, Long conversationId) {
        readTx.executeWithoutResult(status -> requireMember(conversationId, adminId));
        tx.executeWithoutResult(status -> {
            // Lấy khóa trước rồi mới đọc thành viên (lý do ở kick).
            ChatConversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                    .orElseThrow(ChatException::notFound);
            requireMember(conversationId, adminId);
            if (conversation.getType() != ConversationType.GROUP) {
                throw ChatException.badRequest("NOT_A_GROUP", "Chỉ giải tán được nhóm.");
            }
            requireOwner(conversation, adminId);

            List<Long> recipients = memberRepository.findAdminIdsByConversationId(conversationId);
            messageRepository.deleteAllByConversationId(conversationId);
            memberRepository.deleteAllByConversationId(conversationId);
            conversationRepository.delete(conversation);
            conversationRepository.flush();
            events.publishEvent(new ChatDomainEvent.ConversationDissolved(recipients, conversationId));
        });
    }

    /* ============================== đã đọc ============================== */

    /** Endpoint 12. Kẹp mốc theo id lớn nhất của cuộc, chỉ nâng lên, và chỉ phát sự kiện khi mốc thật sự đổi. */
    public void markRead(Long adminId, Long conversationId, Long lastReadMessageId) {
        if (lastReadMessageId == null || lastReadMessageId <= 0) {
            throw ChatException.validation("lastReadMessageId không hợp lệ.");
        }
        tx.executeWithoutResult(status -> {
            requireMember(conversationId, adminId);
            Long max = messageRepository.findMaxId(conversationId);
            if (max == null) {
                return;
            }
            long target = Math.min(lastReadMessageId, max);
            int changed = memberRepository.advanceLastRead(conversationId, adminId, target);
            if (changed > 0) {
                events.publishEvent(new ChatDomainEvent.ConversationRead(adminId, conversationId, target));
            }
        });
    }

    /* ============================== tiện ích ============================== */

    private void requireMember(Long conversationId, Long adminId) {
        if (!memberRepository.existsById(new ChatMemberId(conversationId, adminId))) {
            throw ChatException.notFound();
        }
    }

    private static void requireOwner(ChatConversation conversation, Long adminId) {
        if (!adminId.equals(conversation.getOwnerId())) {
            throw ChatException.forbidden("NOT_OWNER", "Chỉ chủ nhóm mới được thực hiện thao tác này.");
        }
    }

    static String directKey(Long a, Long b) {
        return Math.min(a, b) + "_" + Math.max(a, b);
    }

    /** Cắt về micro giây để giá trị trả về khớp đúng giá trị lưu trong DATETIME(6). */
    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
