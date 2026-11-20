package com.example.be_server_ai.service.redis.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.be_server_ai.model.redis.AiNodeState;
import com.example.be_server_ai.repository.redis.AiNodeStateRepository;
import com.example.be_server_ai.service.redis.AiNodeStateService;

@Service
public class AiNodeStateServiceImpl implements AiNodeStateService {

    private static final Logger log = LoggerFactory.getLogger(AiNodeStateServiceImpl.class);

    private final AiNodeStateRepository repo;

    public AiNodeStateServiceImpl(AiNodeStateRepository repo) {
        this.repo = repo;
    }

    @Override
    public void save(AiNodeState state) {
        try {
            repo.save(state);
        } catch (Exception e) {
            log.debug("Không ghi được ai:node:{}: {}", state.getName(), e.toString());
        }
    }
}
