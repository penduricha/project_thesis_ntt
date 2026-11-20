package com.example.be_server_ai.repository.redis;

import org.springframework.data.repository.CrudRepository;

import com.example.be_server_ai.model.redis.NlFilterCache;

public interface NlFilterCacheRepository extends CrudRepository<NlFilterCache, String> {
}
