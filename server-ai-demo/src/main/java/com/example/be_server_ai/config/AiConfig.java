package com.example.be_server_ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bật gắn cấu hình "ai:" vào AiProperties. */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
}
