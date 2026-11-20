package com.example.server_ai_worker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bật gắn cấu hình "ai:" vào AiProperties. */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
}
