package com.company.leave.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionRegistry {

    static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final ActiveSessionRepository activeSessionRepository;

    SessionRegistry(ActiveSessionRepository activeSessionRepository) {
        this.activeSessionRepository = activeSessionRepository;
    }

    @Transactional
    public String createSession(long userId) {
        String tokenId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        activeSessionRepository.deleteByExpiresAtBefore(now);
        activeSessionRepository.save(new ActiveSession(tokenId, userId, now, now.plus(IDLE_TIMEOUT)));
        return tokenId;
    }

    @Transactional
    public boolean touchOrReject(String tokenId) {
        return activeSessionRepository.findById(tokenId)
                .filter(session -> {
                    Instant now = Instant.now();
                    return now.isBefore(session.getExpiresAt())
                            && Duration.between(session.getLastSeenAt(), now).compareTo(IDLE_TIMEOUT) <= 0;
                })
                .map(session -> {
                    Instant now = Instant.now();
                    session.touch(now);
                    session.slideExpiry(now.plus(IDLE_TIMEOUT));
                    activeSessionRepository.save(session);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void revokeAllForUser(long userId) {
        activeSessionRepository.deleteByUserId(userId);
    }
}
