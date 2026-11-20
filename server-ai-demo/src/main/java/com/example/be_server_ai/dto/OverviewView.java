package com.example.be_server_ai.dto;

import java.util.List;

/** GET /web/ai/overview: độ dài hàng đợi ai:queue và trạng thái 5 node (trang HTML gọi định kỳ khi demo). */
public record OverviewView(long queueLength, List<NodeView> nodes, long serverTimeMs) {
}
