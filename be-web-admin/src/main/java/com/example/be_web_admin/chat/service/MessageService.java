package com.example.be_web_admin.chat.service;

import com.example.be_web_admin.chat.ChatProperties;
import com.example.be_web_admin.chat.dto.MessageDto;
import com.example.be_web_admin.chat.dto.MessagesPageDto;
import com.example.be_web_admin.chat.dto.SendMessageRequest;
import com.example.be_web_admin.chat.dto.SendResult;
import com.example.be_web_admin.chat.event.ChatDomainEvent;
import com.example.be_web_admin.chat.exception.ChatException;
import com.example.be_web_admin.chat.model.ChatMemberId;
import com.example.be_web_admin.chat.model.ChatMessage;
import com.example.be_web_admin.chat.repository.ChatConversationRepository;
import com.example.be_web_admin.chat.repository.ChatMemberRepository;
import com.example.be_web_admin.chat.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;

/**
 * Tin nhắn văn bản: tải trang, gửi (idempotent theo clientMsgId), xóa cho mọi người (xóa cứng, không để dấu vết).
 * Gửi và xóa lấy khóa CHIA SẺ trên dòng cuộc trò chuyện; kick và giải tán lấy khóa độc quyền (ConversationService),
 * nên không thể có tin mồ côi trong nhóm vừa bị giải tán, hay người vừa bị kick vẫn gửi thêm được tin.
 */
@Slf4j
@Service
public class MessageService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMemberRepository memberRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatAssembler assembler;
    private final ChatRateLimiter rateLimiter;
    private final ChatProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public MessageService(ChatConversationRepository conversationRepository,
                          ChatMemberRepository memberRepository,
                          ChatMessageRepository messageRepository,
                          ChatAssembler assembler,
                          ChatRateLimiter rateLimiter,
                          ChatProperties properties,
                          ApplicationEventPublisher events,
                          PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.assembler = assembler;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    /* ============================== tải trang ============================== */

    /** Endpoint 9. Tin mới nhất đứng đầu; before là id của tin cũ nhất client đang có (keyset, không dùng offset). */
    public MessagesPageDto list(Long adminId, Long conversationId, Long before, Integer limit) {
        ChatProperties.Page page = properties.getPage();
        int size = limit == null ? page.getDefaultSize() : Math.min(Math.max(limit, 1), page.getMaxSize());
        return readTx.execute(status -> {
            requireMember(conversationId, adminId);
            // Lấy dư 1 dòng để biết còn trang cũ hơn hay không mà không cần câu COUNT.
            PageRequest request = PageRequest.of(0, size + 1);
            List<ChatMessage> rows = before == null
                    ? messageRepository.findLatest(conversationId, request)
                    : messageRepository.findBefore(conversationId, before, request);
            boolean hasMore = rows.size() > size;
            List<ChatMessage> window = hasMore ? rows.subList(0, size) : rows;
            return new MessagesPageDto(assembler.toMessages(window), hasMore);
        });
    }

    /* ================================= gửi ================================= */

    /** Endpoint 10. 201 cho lần gửi đầu; gửi lại cùng clientMsgId thì 200 cùng tin cũ và KHÔNG phát lại sự kiện. */
    public SendResult send(Long adminId, Long conversationId, SendMessageRequest request) {
        String clientMsgId = ChatContent.requireClientMsgId(request.clientMsgId()).toLowerCase(Locale.ROOT);
        String content = ChatContent.normalizeMessage(request.content(), properties.getMessage().getMaxLength());
        rateLimiter.check("send", adminId, properties.getRate().getMessages(), properties.getRate().getWindowSeconds());

        // Người ngoài cuộc bị từ chối ở giao dịch riêng, trước khi lấy khóa (xem insertOrFind về lý do tách riêng).
        readTx.executeWithoutResult(status -> requireMember(conversationId, adminId));

        try {
            return tx.execute(status -> insertOrFind(adminId, conversationId, clientMsgId, content));
        } catch (DataIntegrityViolationException e) {
            // Hai yêu cầu cùng clientMsgId chạy song song: một giao dịch thua UNIQUE rồi rollback. Đọc lại tin
            // do giao dịch thắng đã lưu và trả như một lần gửi lại.
            MessageDto existing = readTx.execute(status -> {
                requireMember(conversationId, adminId);
                return messageRepository
                        .findByConversationIdAndSenderIdAndClientMsgId(conversationId, adminId, clientMsgId)
                        .map(assembler::toMessage)
                        .orElse(null);
            });
            if (existing == null) {
                throw e;
            }
            return new SendResult(existing, false);
        }
    }

    /**
     * Câu đầu tiên của giao dịch PHẢI là câu lấy khóa. MariaDB mặc định REPEATABLE READ: ảnh chụp dữ liệu của giao dịch
     * được lập ở lần SELECT thường đầu tiên; câu khóa (LOCK IN SHARE MODE) thì đọc bản mới nhất và không lập ảnh chụp.
     * Lấy khóa trước rồi mới kiểm tra thành viên thì kiểm tra này thấy đúng kết quả của kick/giải tán vừa commit.
     * Đảo thứ tự (SELECT thường trước, khóa sau) sẽ khiến kiểm tra lại dưới khóa đọc phải dữ liệu cũ và vô tác dụng.
     */
    private SendResult insertOrFind(Long adminId, Long conversationId, String clientMsgId, String content) {
        conversationRepository.findByIdForShare(conversationId).orElseThrow(ChatException::notFound);
        requireMember(conversationId, adminId);

        ChatMessage existing = messageRepository
                .findByConversationIdAndSenderIdAndClientMsgId(conversationId, adminId, clientMsgId)
                .orElse(null);
        if (existing != null) {
            return new SendResult(assembler.toMessage(existing), false);
        }

        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderId(adminId);
        message.setClientMsgId(clientMsgId);
        message.setContent(content);
        message.setCreatedAt(ConversationService.now());
        message = messageRepository.saveAndFlush(message);

        MessageDto dto = assembler.toMessage(message);
        List<Long> recipients = memberRepository.findAdminIdsByConversationId(conversationId);
        events.publishEvent(new ChatDomainEvent.MessageNew(recipients, dto));
        return new SendResult(dto, true);
    }

    /* ================================= xóa ================================= */

    /**
     * Endpoint 11. Chỉ người gửi, đang là thành viên, mới xóa được (D11). Xóa cứng đúng 1 dòng rồi báo message.deleted
     * cho mọi thành viên. Tin đã không còn thì 404 (client coi là đã xóa xong).
     */
    public void delete(Long adminId, Long conversationId, Long messageId) {
        readTx.executeWithoutResult(status -> requireMember(conversationId, adminId));
        tx.executeWithoutResult(status -> {
            // Lấy khóa trước, rồi mới kiểm tra thành viên (lý do ở insertOrFind).
            conversationRepository.findByIdForShare(conversationId).orElseThrow(ChatException::notFound);
            requireMember(conversationId, adminId);

            ChatMessage message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                    .orElseThrow(ChatException::notFound);
            if (!message.getSenderId().equals(adminId)) {
                throw ChatException.forbidden("NOT_SENDER", "Bạn chỉ xóa được tin nhắn của chính mình.");
            }
            List<Long> recipients = memberRepository.findAdminIdsByConversationId(conversationId);
            int deleted = messageRepository.deleteOwn(messageId, conversationId, adminId);
            if (deleted == 0) {
                // Một yêu cầu xóa khác vừa xóa mất tin này giữa hai câu lệnh.
                throw ChatException.notFound();
            }
            events.publishEvent(new ChatDomainEvent.MessageDeleted(recipients, conversationId, messageId));
        });
    }

    /* ============================== tiện ích ============================== */

    private void requireMember(Long conversationId, Long adminId) {
        if (!memberRepository.existsById(new ChatMemberId(conversationId, adminId))) {
            throw ChatException.notFound();
        }
    }
}
