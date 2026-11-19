package com.example.be_web_admin.chat.controller;

import com.example.be_web_admin.chat.dto.WsTicketDto;
import com.example.be_web_admin.chat.service.WsTicketService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint 1: xin vé một lần để mở WebSocket (cần Bearer). Đường dẫn đầy đủ: POST /api/mobile/chat/ws-ticket. */
@RestController
@RequestMapping("/mobile/chat")
public class ChatWsTicketController {

    private final WsTicketService ticketService;

    public ChatWsTicketController(WsTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/ws-ticket")
    public WsTicketDto issue(Authentication authentication) {
        return ticketService.issue(CurrentAdmin.id(authentication));
    }
}
