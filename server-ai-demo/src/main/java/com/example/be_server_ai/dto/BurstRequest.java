package com.example.be_server_ai.dto;

/** Thân POST /web/ai/tasks/burst: tạo nhanh count task nl_search khác nhau để xem chia tải giữa các node. */
public record BurstRequest(Integer count) {
}
