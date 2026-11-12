package com.example.be_web_admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Ánh xạ khối app.totp trong application.yml. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.totp")
public class TotpProperties {

    /** Thời gian mobile phải xác nhận sau khi web bấm "Kích hoạt" (khớp đồng hồ "Còn 10:00"). */
    private int confirmWindowMinutes = 10;

    /** Số lần nhập sai mã xác nhận tối đa; vượt quá thì xóa phần cài dở, admin phải cài lại. */
    private int confirmMaxAttempts = 5;
}
