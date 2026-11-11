package com.example.be_web_admin.model.redis;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Phiên đăng nhập ngắn hạn của app Authenticator (Mobile), sinh sau khi app ký challenge
 * bằng khoá riêng của thiết bị và được server xác minh với model.jpa.MobileDevice.publicKey.
 *
 * Key: session-mobile:{jti}, TTL 1 giờ. Redis tự xoá khi hết hạn.
 * lastSeenAt ghi liên tục ở đây; định kỳ (hoặc khi đăng xuất) mới ghi xuống ntt_mobile_devices.last_seen_at.
 *
 * Thay thế class MobileSession ở bản trước.
 */
//@Getter
//@Setter
//@NoArgsConstructor
//@AllArgsConstructor
//@RedisHash(value = "session-mobile", timeToLive = 3600)
//public class SessionLoginMobile implements Serializable {
//
//    @Serial
//    private static final long serialVersionUID = 1L;
//
//    // jti của access token mobile.
//    @Id
//    private String jti;
//
//    // Tương ứng ntt_mobile_devices.device_id. Có index để thu hồi: findByDeviceId(...) rồi xoá hết.
//    @Indexed
//    private Long deviceId;
//
//    // Tương ứng ntt_admins.admin_id. Có index để đăng xuất app trên mọi thiết bị của một admin.
//    @Indexed
//    private Long adminId;
//
//    private String ipAddress;
//
//    private Instant createdAt;
//
//    private Instant lastSeenAt;
//}

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Phiên đăng nhập của app Mobile. Mỗi tài khoản chỉ có tối đa 1 phiên mobile.
 *
 * Key: session-mobile:{adminId}. Dùng adminId làm @Id nên:
 *  - kiểm tra "đã đăng nhập ở thiết bị khác" chỉ là existsById / findById (nhanh, không cần index);
 *  - đăng nhập lại thì không thể tạo thêm bản ghi thứ hai.
 *
 * TTL 30 ngày, bằng thời hạn refresh token (đổi cho khớp cấu hình của bạn).
 * Mỗi lần làm mới token thì save lại để gia hạn TTL.
 *
 * Thay thế bản cũ (id = jti) và có thể xoá luôn class MobileSession.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash(value = "session_mobile", timeToLive = 2_592_000)
public class SessionLoginMobile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long adminId;

    // Do app gửi lên khi đăng nhập (không bắt buộc), dùng để hiển thị cảnh báo.
    private String deviceName;

    private String platform;

    private String ip;

    private Instant createdAt;

    private Instant lastSeenAt;
}