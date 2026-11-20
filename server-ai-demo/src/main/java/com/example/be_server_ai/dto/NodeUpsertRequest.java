package com.example.be_server_ai.dto;

/** Thân PUT /web/ai/nodes/{name}. token trống hoặc null nghĩa là giữ token cũ. */
public record NodeUpsertRequest(String url, String token, Boolean enabled, Integer maxConcurrency) {
}
