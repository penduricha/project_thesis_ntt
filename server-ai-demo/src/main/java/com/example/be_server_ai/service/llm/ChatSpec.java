package com.example.be_server_ai.service.llm;

import com.example.be_server_ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;

/** Yêu cầu một lượt chat gửi Ollama: system, user, schema ràng buộc đầu ra ("format") và tham số sinh. */
public record ChatSpec(String system, String user, JsonNode format, AiProperties.Options options) {
}
