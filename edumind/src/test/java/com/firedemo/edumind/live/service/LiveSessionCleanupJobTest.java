package com.firedemo.edumind.live.service;

import com.firedemo.edumind.live.ClassroomSession;
import com.firedemo.edumind.live.ClassroomSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveSessionCleanupJobTest {

    @Test
    void endsClassroomsWhoseTeacherHasBeenOfflineForOneHour() {
        ClassroomSessionMapper mapper = mock(ClassroomSessionMapper.class);
        LiveSessionService service = mock(LiveSessionService.class);
        ClassroomSession expired = ClassroomSession.builder().id(99L).status("ACTIVE").build();
        when(mapper.findOfflineBefore(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(expired));

        new LiveSessionCleanupJob(mapper, service).endTeacherOfflineSessions();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).findOfflineBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(
                LocalDateTime.now().minusHours(1).minusSeconds(2),
                LocalDateTime.now().minusHours(1).plusSeconds(2));
        verify(service).autoEndSession(99L);
    }
}
