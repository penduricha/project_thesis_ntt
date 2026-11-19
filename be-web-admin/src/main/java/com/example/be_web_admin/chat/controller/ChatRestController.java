package com.example.be_web_admin.chat.controller;

import com.example.be_web_admin.chat.dto.AdminLiteDto;
import com.example.be_web_admin.chat.dto.ConversationDto;
import com.example.be_web_admin.chat.dto.ConversationResult;
import com.example.be_web_admin.chat.dto.ConversationSummaryDto;
import com.example.be_web_admin.chat.dto.CreateDirectRequest;
import com.example.be_web_admin.chat.dto.CreateGroupRequest;
import com.example.be_web_admin.chat.dto.MarkReadRequest;
import com.example.be_web_admin.chat.dto.MessageDto;
import com.example.be_web_admin.chat.dto.MessagesPageDto;
import com.example.be_web_admin.chat.dto.SendMessageRequest;
import com.example.be_web_admin.chat.dto.SendResult;
import com.example.be_web_admin.chat.service.AdminDirectoryService;
import com.example.be_web_admin.chat.service.ConversationService;
import com.example.be_web_admin.chat.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST chat (endpoint 2 đến 12). Đường dẫn đầy đủ: /api/mobile/chat/... Controller chỉ lấy adminId từ token, gọi
 * service rồi trả DTO; mọi quy tắc nghiệp vụ nằm trong service. Lỗi do ChatExceptionHandler dựng thành
 * { "code": "...", "message": "..." }.
 */
@RestController
@RequestMapping("/mobile/chat")
public class ChatRestController {

    private final AdminDirectoryService directoryService;
    private final ConversationService conversationService;
    private final MessageService messageService;

    public ChatRestController(AdminDirectoryService directoryService,
                              ConversationService conversationService,
                              MessageService messageService) {
        this.directoryService = directoryService;
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    /** 2. Tìm admin để bắt đầu chat. */
    @GetMapping("/admins")
    public List<AdminLiteDto> searchAdmins(Authentication authentication,
                                           @RequestParam(name = "q", required = false) String q,
                                           @RequestParam(name = "limit", required = false) Integer limit) {
        return directoryService.search(CurrentAdmin.id(authentication), q, limit);
    }

    /** 3. Danh sách cuộc trò chuyện của tôi. */
    @GetMapping("/conversations")
    public List<ConversationSummaryDto> listConversations(Authentication authentication) {
        return conversationService.listMine(CurrentAdmin.id(authentication));
    }

    /** 4. Chi tiết một cuộc (kèm thành viên). */
    @GetMapping("/conversations/{id}")
    public ConversationDto getConversation(Authentication authentication, @PathVariable("id") Long id) {
        return conversationService.get(CurrentAdmin.id(authentication), id);
    }

    /** 5. Mở chat 1-1: 201 nếu mới tạo, 200 nếu đã có. */
    @PostMapping("/conversations/direct")
    public ResponseEntity<ConversationDto> openDirect(Authentication authentication,
                                                      @Valid @RequestBody CreateDirectRequest request) {
        ConversationResult result = conversationService.openDirect(CurrentAdmin.id(authentication), request.peerId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.conversation());
    }

    /** 6. Tạo nhóm. */
    @PostMapping("/conversations/group")
    public ResponseEntity<ConversationDto> createGroup(Authentication authentication,
                                                       @Valid @RequestBody CreateGroupRequest request) {
        ConversationDto created = conversationService.createGroup(CurrentAdmin.id(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** 7. Giải tán nhóm (chủ nhóm). */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> dissolveGroup(Authentication authentication, @PathVariable("id") Long id) {
        conversationService.dissolve(CurrentAdmin.id(authentication), id);
        return ResponseEntity.noContent().build();
    }

    /** 8. Kick thành viên (chủ nhóm). Trả cuộc trò chuyện sau khi kick. */
    @DeleteMapping("/conversations/{id}/members/{adminId}")
    public ConversationDto kickMember(Authentication authentication,
                                      @PathVariable("id") Long id,
                                      @PathVariable("adminId") Long targetAdminId) {
        return conversationService.kick(CurrentAdmin.id(authentication), id, targetAdminId);
    }

    /** 9. Tải trang tin, tin mới nhất đứng đầu. */
    @GetMapping("/conversations/{id}/messages")
    public MessagesPageDto listMessages(Authentication authentication,
                                        @PathVariable("id") Long id,
                                        @RequestParam(name = "before", required = false) Long before,
                                        @RequestParam(name = "limit", required = false) Integer limit) {
        return messageService.list(CurrentAdmin.id(authentication), id, before, limit);
    }

    /** 10. Gửi tin: 201 lần đầu, 200 nếu clientMsgId đã gửi trước đó. */
    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<MessageDto> sendMessage(Authentication authentication,
                                                  @PathVariable("id") Long id,
                                                  @RequestBody SendMessageRequest request) {
        SendResult result = messageService.send(CurrentAdmin.id(authentication), id, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.message());
    }

    /** 11. Xóa tin cho mọi người (người gửi). */
    @DeleteMapping("/conversations/{id}/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(Authentication authentication,
                                              @PathVariable("id") Long id,
                                              @PathVariable("messageId") Long messageId) {
        messageService.delete(CurrentAdmin.id(authentication), id, messageId);
        return ResponseEntity.noContent().build();
    }

    /** 12. Đánh dấu đã đọc tới một tin. */
    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Void> markRead(Authentication authentication,
                                         @PathVariable("id") Long id,
                                         @Valid @RequestBody MarkReadRequest request) {
        conversationService.markRead(CurrentAdmin.id(authentication), id, request.lastReadMessageId());
        return ResponseEntity.noContent().build();
    }
}
