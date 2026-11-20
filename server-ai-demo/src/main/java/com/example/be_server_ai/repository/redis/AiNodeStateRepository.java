package com.example.be_server_ai.repository.redis;

import org.springframework.data.repository.CrudRepository;

import com.example.be_server_ai.model.redis.AiNodeState;

public interface AiNodeStateRepository extends CrudRepository<AiNodeState, String> {
}
