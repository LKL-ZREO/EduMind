package com.firedemo.edumind.assistant.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.platform.storage.FileStorage;
import com.firedemo.edumind.assistant.AgentService;
import com.firedemo.edumind.auth.UserService;
import com.firedemo.edumind.assistant.context.AgentChannel;
import com.firedemo.edumind.assistant.context.AgentExecutionContext;
import com.firedemo.edumind.assistant.context.AgentExecutionContextFactory;
import com.firedemo.edumind.classroom.ClassService;
import com.firedemo.edumind.assistant.vision.VisualAssetService;
import com.firedemo.edumind.assistant.vision.VisionUnderstandingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerStreamingTest {

    private AgentService agentService;
    private ChatHistoryService historyService;
    private ChatController controller;
    private AgentExecutionContext context;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        historyService = mock(ChatHistoryService.class);
        AgentExecutionContextFactory contextFactory = mock(AgentExecutionContextFactory.class);
        context = new AgentExecutionContext(
                "session-1", null, null, Set.of(), AgentChannel.WEB, "trace-1");
        controller = new ChatController(
                agentService,
                mock(FileStorage.class),
                historyService,
                mock(UserService.class),
                mock(ClassService.class),
                mock(VisualAssetService.class),
                mock(VisionUnderstandingService.class),
                new ObjectMapper(),
                contextFactory);
        when(contextFactory.create(eq("session-1"), isNull(), isNull(), eq(AgentChannel.WEB)))
                .thenReturn(context);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void writesEscapedTokenFramesAndDoneEventWithoutBufferingTheWholeAnswer() throws Exception {
        when(agentService.streamChat(eq("question"), eq(context), isNull()))
                .thenReturn(Flux.just("first\nline", "second \"part\""));

        ResponseEntity<StreamingResponseBody> response =
                controller.streamMessage("question", "session-1");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);
        String stream = output.toString(StandardCharsets.UTF_8);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-cache, no-transform");
        assertThat(response.getHeaders().getFirst("X-Accel-Buffering")).isEqualTo("no");
        assertThat(stream).containsSubsequence(
                "event: token\ndata: {\"content\":\"first\\nline\"}\n\n",
                "event: token\ndata: {\"content\":\"second \\\"part\\\"\"}\n\n",
                "event: done\ndata: {}\n\n");
    }

    @Test
    void sendsStructuredErrorEventWhenTheModelStreamFails() throws Exception {
        when(agentService.streamChat(eq("question"), eq(context), isNull()))
                .thenReturn(Flux.error(new IllegalStateException("upstream failed")));

        ResponseEntity<StreamingResponseBody> response =
                controller.streamMessage("question", "session-1");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("event: error\n")
                .contains("data: {\"message\":\"服务暂时不可用\"}\n\n")
                .doesNotContain("event: done");
    }

    @Test
    void clearsBothAgentWorkingMemoryAndUserVisibleHistory() {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("teacher", "n/a", java.util.List.of());
        authentication.setDetails(42L);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ResponseEntity<java.util.Map<String, String>> response = controller.clearHistory();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(agentService).clearMemory(42L);
        verify(historyService).deleteByUserId(42L);
    }
}
