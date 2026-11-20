package com.example.server_ai_worker.service.ai;

import com.example.server_ai_worker.config.AiProperties;

public record ChatSpec(
        String systemPrompt,
        String userPrompt,
        String formatSchemaJson,
        AiProperties.Options options) {
}
