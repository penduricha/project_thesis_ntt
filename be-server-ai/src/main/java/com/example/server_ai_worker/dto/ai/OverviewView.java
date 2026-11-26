package com.example.server_ai_worker.dto.ai;

import java.util.List;

public record OverviewView(
        long queueLength,
        String model,
        String promptVersion,
        String questionCheckMode,
        List<NodeView> nodes,
        long serverTimeMs) {
}
