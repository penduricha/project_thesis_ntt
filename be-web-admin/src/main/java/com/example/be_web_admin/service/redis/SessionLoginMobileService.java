package com.example.be_web_admin.service.redis;

import com.example.be_web_admin.model.redis.SessionLoginMobile;

import java.util.Optional;

public interface SessionLoginMobileService {

    /**
     * Tạo phiên mobile mới cho admin (ghi đè nếu đã có, nên kiểm tra trước bằng findByAdminId).
     */
    public void createSession(Long adminId, String deviceName, String platform, String ip);

    public Optional<SessionLoginMobile> findByAdminId(Long adminId);

    public boolean existsByAdminId(Long adminId);

    /**
     * Xoá phiên khi đăng xuất. Trả về true nếu có phiên để xoá.
     */
    public void removeByAdminId(Long adminId);

    /** Cập nhật lastSeenAt và gia hạn TTL (gọi khi auto login / làm mới token). */
    public void touch(Long adminId);
}
