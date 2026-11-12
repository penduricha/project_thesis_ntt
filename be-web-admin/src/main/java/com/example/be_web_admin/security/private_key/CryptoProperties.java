package com.example.be_web_admin.security.private_key;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Ánh xạ khối app.crypto trong application.yml.
 * Giá trị khóa lấy từ biến môi trường, tuyệt đối không ghi vào mã nguồn hay Git.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

    /** Phiên bản khóa dùng để MÃ HÓA bản ghi mới (lưu vào ntt_totp_credentials.key_version). */
    private int activeVersion;

    /** version -> master key (Base64 của ít nhất 32 byte ngẫu nhiên). */
    private Map<Integer, String> keys = new HashMap<>();

    /** MASTER_SECRET cũ, chỉ dùng cho bước di chuyển dữ liệu một lần. Để trống khi không cần. */
    private String legacyMasterSecret;
}
