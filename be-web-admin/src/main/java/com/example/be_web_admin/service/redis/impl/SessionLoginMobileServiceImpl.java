package com.example.be_web_admin.service.redis.impl;

import org.springframework.stereotype.Service;


import com.example.be_web_admin.model.redis.SessionLoginMobile;
import com.example.be_web_admin.repository.redis.SessionLoginMobileRepository;
import com.example.be_web_admin.service.redis.SessionLoginMobileService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class SessionLoginMobileServiceImpl implements SessionLoginMobileService {

    private static final int MAX_DEVICE_NAME_LENGTH = 100;
    private static final int MAX_PLATFORM_LENGTH = 20;

    private final SessionLoginMobileRepository sessionLoginMobileRepository;

    public SessionLoginMobileServiceImpl(SessionLoginMobileRepository sessionLoginMobileRepository) {
        this.sessionLoginMobileRepository = sessionLoginMobileRepository;
    }

    @Override
    public SessionLoginMobile createSession(Long adminId, String deviceName, String platform, String ip) {
        Instant now = Instant.now();
        SessionLoginMobile session = new SessionLoginMobile(
                adminId,
                truncate(deviceName, MAX_DEVICE_NAME_LENGTH),
                truncate(platform, MAX_PLATFORM_LENGTH),
                ip,
                now,
                now
        );
        return sessionLoginMobileRepository.save(session);
    }

    @Override
    public Optional<SessionLoginMobile> findByAdminId(Long adminId) {
        return sessionLoginMobileRepository.findById(adminId);
    }

    @Override
    public boolean existsByAdminId(Long adminId) {
        return sessionLoginMobileRepository.existsById(adminId);
    }

    @Override
    public boolean removeByAdminId(Long adminId) {
        if (!sessionLoginMobileRepository.existsById(adminId)) {
            return false;
        }
        sessionLoginMobileRepository.deleteById(adminId);
        return true;
    }

    @Override
    public void touch(Long adminId) {
        sessionLoginMobileRepository.findById(adminId).ifPresent(session -> {
            session.setLastSeenAt(Instant.now());
            // save lại để Redis đặt lại TTL theo @RedisHash(timeToLive)
            sessionLoginMobileRepository.save(session);
        });
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}