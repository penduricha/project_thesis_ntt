package com.example.be_web_admin.service.redis;

import com.example.be_web_admin.service.redis.impl.EnrollSessionServiceImpl;

public interface EnrollSessionService {
    public EnrollSessionServiceImpl.StartedEnrollment startEnrollment(Long adminId, Integer periodSeconds);
}
