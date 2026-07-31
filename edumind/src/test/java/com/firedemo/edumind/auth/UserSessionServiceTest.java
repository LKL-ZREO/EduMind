package com.firedemo.edumind.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSessionServiceTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void revokesEveryIndexedSessionForTheTeacher() {
        ObjectProvider provider = mock(ObjectProvider.class);
        FindByIndexNameSessionRepository repository =
                mock(FindByIndexNameSessionRepository.class);
        Session first = mock(Session.class);
        Session second = mock(Session.class);
        when(provider.getIfAvailable()).thenReturn(repository);
        when(repository.findByPrincipalName("teacher"))
                .thenReturn(Map.of("session-1", first, "session-2", second));
        UserSessionService service = new UserSessionService(provider);

        int revoked = service.revokeByUsername("teacher");

        assertThat(revoked).isEqualTo(2);
        verify(repository).deleteById("session-1");
        verify(repository).deleteById("session-2");
    }
}
