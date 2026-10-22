package com.company.leave.security;

import org.springframework.data.repository.Repository;

interface ActiveSessionRepository extends Repository<ActiveSession, String> {

    ActiveSession save(ActiveSession activeSession);

    java.util.Optional<ActiveSession> findById(String tokenId);

    void deleteByUserId(long userId);
}
