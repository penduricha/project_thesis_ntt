package com.example.be_server_ai.service.redis;

import com.example.be_server_ai.model.redis.AiNodeState;

/** Ghi trạng thái node vào ai:node:{name} (không có URL/token). Chỉ để quan sát; lỗi Redis chỉ ghi log. */
public interface AiNodeStateService {

    void save(AiNodeState state);
}
