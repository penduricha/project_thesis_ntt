package com.example.be_web_admin.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Ánh xạ khối app.totp trong application.yml. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.totp")
@Validated
public class TotpProperties {
    @Min(1) @Max(60)
    private int confirmWindowMinutes = 10;

    @Min(1) @Max(10)
    private int confirmMaxAttempts = 5;
}
