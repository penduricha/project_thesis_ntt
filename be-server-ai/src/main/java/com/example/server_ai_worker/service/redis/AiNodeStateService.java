package com.example.server_ai_worker.service.redis;

import com.example.server_ai_worker.model.redis.AiNodeState;

public interface AiNodeStateService {

    void save(AiNodeState state);
}
