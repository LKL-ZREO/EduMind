package com.firedemo.demo.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.Service.ChatHistoryService;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.Service.UserService;
import com.firedemo.demo.agent.context.AgentChannel;
import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.agent.context.AgentExecutionContextFactory;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.vision.VisualAssetService;
import com.firedemo.demo.vision.VisionUnderstandingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatControllerStreamingTest {

    private OpenClawService agentService;
    private ChatHistoryService historyService;
    private ChatController controller;
    private AgentExecutionContext context;

    @BeforeEach
    void setUp() {
        agentService = mock(OpenClawService.class);
        historyService = mock(ChatHistoryService.class);
        AgentExecutionContextFactory contextFactory = mock(AgentExecutionContextFactory.class);
        context = new AgentExecutionContext(
                "session-1", null, null, Set.of(), AgentChannel.WEB, "trace-1");
        controller = new ChatController(
                agentService,
                mock(FileStorageService.class),
                historyService,
                mock(UserService.class),
                mock(ClassInfoMapper.class),
                mock(VisualAssetService.class),
                mock(VisionUnderstandingService.class),
                new ObjectMapper(),
                contextFactory);
        when(historyService.getHistory(any(), eq(10))).thenReturn(List.of());
        when(contextFactory.create(eq("session-1"), isNull(), isNull(), eq(AgentChannel.WEB)))
                .thenReturn(context);
    }

    @Test
    void writesEscapedTokenFramesAndDoneEventWithoutBufferingTheWholeAnswer() throws Exception {
        when(agentService.streamChat(eq("question"), any(List.class), eq(context)))
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
        when(agentService.streamChat(eq("question"), any(List.class), eq(context)))
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
}
