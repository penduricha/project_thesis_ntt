package com.example.be_web_admin.service.redis;

import com.example.be_web_admin.model.redis.SessionLoginMobile;

import java.util.Optional;

public interface SessionLoginMobileService {

    /**
     * Giành phiên mobile cho admin: kiểm tra tồn tại rồi tạo phiên MỘT THẦN TẮC (atomic) và trả về true.
     * Nếu phiên đã tồn tại (thiết bị khác) thì KHÔNG ghi đè, trả về false — caller phải thu hồi token mới cấp.
     * Tránh race khi 2 thiết bị cùng đăng nhập: nếu không kiểm tra nguyên tử, cả 2 đều thấy chưa có phiên
     * rồi ghi đè, để lại 2 cặp token sống trong Redis.
     */
    public boolean claimSession(Long adminId, String deviceName, String platform, String ip);

    public Optional<SessionLoginMobile> findByAdminId(Long adminId);

    public boolean existsByAdminId(Long adminId);

    /**
     * Xoá phiên khi đăng xuất. Trả về true nếu có phiên để xoá.
     */
    public void removeByAdminId(Long adminId);

    /** Cập nhật lastSeenAt và gia hạn TTL (gọi khi auto login / làm mới token). */
    public void touch(Long adminId);
}
