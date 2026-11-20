package com.example.be_server_ai.service.redis.impl;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.be_server_ai.enums.AiTaskStatus;
import com.example.be_server_ai.enums.AiTaskType;
import com.example.be_server_ai.model.redis.AiTaskStatusCache;
import com.example.be_server_ai.model.redis.NlFilterCache;
import com.example.be_server_ai.repository.redis.AiTaskStatusCacheRepository;
import com.example.be_server_ai.repository.redis.NlFilterCacheRepository;
import com.example.be_server_ai.service.redis.AiCacheService;

@Service
public class AiCacheServiceImpl implements AiCacheService {

    private static final Logger log = LoggerFactory.getLogger(AiCacheServiceImpl.class);

    private final AiTaskStatusCacheRepository statusRepo;
    private final NlFilterCacheRepository nlRepo;

    public AiCacheServiceImpl(AiTaskStatusCacheRepository statusRepo, NlFilterCacheRepository nlRepo) {
        this.statusRepo = statusRepo;
        this.nlRepo = nlRepo;
    }

    @Override
    public void putStatus(String taskId, AiTaskType type, AiTaskStatus status, Integer attemptCount, String errorCode) {
        try {
            statusRepo.save(new AiTaskStatusCache(taskId, type == null ? null : type.getValue(),
                    status.getValue(), attemptCount, errorCode, System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("Không ghi được ai:task:{}: {}", taskId, e.toString());
        }
    }

    @Override
    public Optional<AiTaskStatusCache> getStatus(String taskId) {
        try {
            return statusRepo.findById(taskId);
        } catch (Exception e) {
            log.warn("Không đọc được ai:task:{}: {}", taskId, e.toString());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getNlFilter(String queryHash) {
        try {
            return nlRepo.findById(queryHash).map(NlFilterCache::getFilterJson);
        } catch (Exception e) {
            log.warn("Không đọc được ai:cache:nl: {}", e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void putNlFilter(String queryHash, String filterJson, String promptVersion) {
        try {
            nlRepo.save(new NlFilterCache(queryHash, filterJson, promptVersion, System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("Không ghi được ai:cache:nl: {}", e.toString());
        }
    }
}
