package com.example.be_server_ai.dto;

/** reachable: /health trả 200. authorized: node chấp nhận token (không phải 401/403). */
public record NodeCheckResponse(boolean reachable, boolean authorized, Long latencyMs, String error) {
}
