package com.company.leave.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionRegistry {

    static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);
    static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);

    private final ActiveSessionRepository activeSessionRepository;

    SessionRegistry(ActiveSessionRepository activeSessionRepository) {
        this.activeSessionRepository = activeSessionRepository;
    }

    @Transactional
    public String createSession(long userId) {
        String tokenId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        activeSessionRepository.save(new ActiveSession(tokenId, userId, now, now.plus(TOKEN_LIFETIME)));
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
                    session.touch(Instant.now());
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
