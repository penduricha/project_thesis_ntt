package com.example.be_web_admin.chat.service;

import com.example.be_web_admin.chat.ChatProperties;
import com.example.be_web_admin.chat.dto.AdminLiteDto;
import com.example.be_web_admin.chat.repository.ChatAdminRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Tìm admin để bắt đầu chat (endpoint 2). Từ khóa để trống thì trả danh sách admin khác (theo tên, tối đa
 * {@link #MAX_LIMIT}) để màn "Tin nhắn mới" có sẵn danh sách chọn, không bắt gõ trước.
 */
@Service
public class AdminDirectoryService {

    public static final int MAX_LIMIT = 50;
    private static final int MAX_QUERY_LENGTH = 100;

    private final ChatAdminRepository adminRepository;
    private final ChatRateLimiter rateLimiter;
    private final ChatProperties properties;

    public AdminDirectoryService(ChatAdminRepository adminRepository, ChatRateLimiter rateLimiter, ChatProperties properties) {
        this.adminRepository = adminRepository;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<AdminLiteDto> search(Long me, String q, Integer limit) {
        String query = q == null ? "" : q.strip();
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }
        rateLimiter.check("search", me, properties.getRate().getSearchesPer10Seconds(), 10);

        int size = limit == null ? MAX_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        // Để trống: "%" khớp mọi tên và mọi mã, tức là liệt kê tất cả admin khác.
        String escaped = escapeLike(query.toLowerCase(Locale.ROOT));
        String namePattern = query.isEmpty() ? "%" : "%" + escaped + "%";
        String idPattern = query.isEmpty() ? "%" : escaped + "%";
        return adminRepository.search(me, namePattern, idPattern, PageRequest.of(0, size)).stream()
                .map(a -> new AdminLiteDto(a.getAdminId(), a.getAdminName()))
                .toList();
    }

    /** Thoát ký tự đặc biệt của LIKE bằng '!' (khớp "escape '!'" trong ChatAdminRepository). */
    private static String escapeLike(String s) {
        return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
