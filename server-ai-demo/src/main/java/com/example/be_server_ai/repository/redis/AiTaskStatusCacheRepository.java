package com.example.be_server_ai.repository.redis;

import org.springframework.data.repository.CrudRepository;

import com.example.be_server_ai.model.redis.AiTaskStatusCache;

/** Chỉ dùng findById, save, deleteById (khóa có TTL; tập id "ai:task" không tự dọn khi hết hạn). */
public interface AiTaskStatusCacheRepository extends CrudRepository<AiTaskStatusCache, String> {
}
