package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.security.jwt.user.AdminJwtTokenProvider;
import com.example.be_web_admin.service.redis.impl.AdminTokenServiceImpl;
import com.example.be_web_admin.service.redis.impl.TwoFaEventServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Luồng sự kiện trạng thái 2FA cho trang thông tin admin (Vue): mobile xác nhận xong thì web tự chuyển
 * từ "Đang chờ xác nhận" sang "Đã kích hoạt" mà không cần tải lại trang.
 */
@Slf4j
@RestController
@RequestMapping("/web/admin")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class TwoFaEventControllerWeb {

    private final TwoFaEventServiceImpl eventService;
    private final AdminJwtTokenProvider adminJwtTokenProvider;
    private final AdminTokenServiceImpl adminTokenService;

    public TwoFaEventControllerWeb(TwoFaEventServiceImpl eventService,
                                   AdminJwtTokenProvider adminJwtTokenProvider,
                                   AdminTokenServiceImpl adminTokenService) {
        this.eventService = eventService;
        this.adminJwtTokenProvider = adminJwtTokenProvider;
        this.adminTokenService = adminTokenService;
    }

    // GET /api/web/admin/2fa/events     Header: Authorization: Bearer <accessToken>
    // Trình duyệt dùng fetch (không dùng EventSource vì EventSource không gắn được header Authorization).
    // Luồng text/event-stream: "ready" khi nối xong, "2fa-status" {status: PENDING|ENABLED|NOT_ENABLED} khi đổi.
    // 401 token sai (thân rỗng, vì produces là event-stream nên không trả JSON).
    @GetMapping(value = "/2fa/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        Long adminId = resolveAdminId(authHeader);
        if (adminId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no") // nginx: đừng gom đệm, đẩy sự kiện đi ngay
                .body(eventService.subscribe(adminId));
    }

    private Long resolveAdminId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String jwtToken = authHeader.substring(7);
        try {
            if (!adminJwtTokenProvider.validateToken(jwtToken)) {
                return null;
            }
            if (!adminTokenService.findAdminAccessTokenByJwtToken(jwtToken)) {
                return null;
            }
            return adminJwtTokenProvider.getAdminIdFromToken(jwtToken);
        } catch (Exception e) {
            log.warn("Can't resolve adminId from token: {}", e.getMessage());
            return null;
        }
    }
}
