package com.firedemo.demo.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/** Manages Redis-backed teacher sessions through Spring Session's principal index. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositoryProvider;

    public int revokeByUsername(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        FindByIndexNameSessionRepository<? extends Session> repository =
                repositoryProvider.getIfAvailable();
        if (repository == null) {
            log.debug("Spring Session repository is not active; no sessions revoked for {}", username);
            return 0;
        }
        var sessions = repository.findByPrincipalName(username);
        sessions.keySet().forEach(repository::deleteById);
        log.info("Revoked {} teacher session(s): username={}", sessions.size(), username);
        return sessions.size();
    }
}
