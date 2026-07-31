package com.firedemo.edumind.assistant.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.assistant.observability.HeliconeHeadersFactory;
import com.firedemo.edumind.assistant.config.HeliconeProperties;
import com.firedemo.edumind.assistant.config.LlmProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jHeliconeRoutingTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void routesTextVisionAndStreamingModelsThroughHelicone() throws Exception {
        List<CapturedRequest> requests = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> handleRequest(exchange, requests));
        server.start();

        try {
            LangChain4jConfig config = configuration(server.getAddress().getPort());

            assertThat(config.chatLanguageModel().chat("hello")).isEqualTo("ok");
            ChatResponse visionResponse = config.visionChatLanguageModel().chat(UserMessage.from(List.of(
                    ImageContent.from("aGVsbG8=", "image/png"),
                    TextContent.from("describe image"))));
            assertThat(visionResponse.aiMessage().text()).isEqualTo("ok");
            assertStreamingResponse(config, "stream-ok");

            assertThat(requests).hasSize(3);
            assertCommonHeaders(requests.get(0), "https://text-provider.example/v1", "text", "text-provider-key");
            assertCommonHeaders(requests.get(1), "https://vision-provider.example/v1", "vision", "vision-provider-key");
            assertThat(requests.get(1).body()).contains("data:image/png;base64,aGVsbG8=");
            assertCommonHeaders(requests.get(2), "https://text-provider.example/v1", "streaming", "text-provider-key");
            assertThat(OBJECT_MAPPER.readTree(requests.get(2).body()).path("stream").asBoolean()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void keepsDirectProviderRoutingWhenHeliconeIsDisabled() throws Exception {
        List<CapturedRequest> requests = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> handleRequest(exchange, requests));
        server.start();

        try {
            LlmProperties llm = new LlmProperties();
            llm.setBaseUrl("http://localhost:" + server.getAddress().getPort() + "/v1");
            llm.setApiKey("provider-key");
            llm.setTextModel("test-text-model");
            llm.setReadTimeout(Duration.ofSeconds(5));

            HeliconeProperties helicone = new HeliconeProperties();
            LangChain4jConfig config = new LangChain4jConfig(
                    llm, new HeliconeHeadersFactory(helicone));

            assertThat(config.chatLanguageModel().chat("hello")).isEqualTo("ok");
            assertThat(requests).singleElement().satisfies(request -> {
                assertThat(request.authorization()).isEqualTo("Bearer provider-key");
                assertThat(request.heliconeAuth()).isNull();
                assertThat(request.targetUrl()).isNull();
                assertThat(request.modelRole()).isNull();
                assertThat(request.omitRequest()).isNull();
                assertThat(request.omitResponse()).isNull();
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsPlaceholderCredentialsBeforeCreatingModels() {
        LlmProperties llm = new LlmProperties();
        llm.setApiKey("change-me");

        assertThatThrownBy(() -> new LangChain4jConfig(
                llm, new HeliconeHeadersFactory(new HeliconeProperties())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LLM_API_KEY")
                .hasMessageContaining("placeholder")
                .hasMessageNotContaining("change-me");
    }

    private LangChain4jConfig configuration(int port) {
        LlmProperties llm = new LlmProperties();
        llm.setBaseUrl("https://text-provider.example/v1");
        llm.setApiKey("text-provider-key");
        llm.setTextModel("test-text-model");
        llm.setVisionBaseUrl("https://vision-provider.example/v1");
        llm.setVisionApiKey("vision-provider-key");
        llm.setVisionModel("test-vision-model");
        llm.setReadTimeout(Duration.ofSeconds(5));

        HeliconeProperties helicone = new HeliconeProperties();
        helicone.setEnabled(true);
        helicone.setGatewayUrl("http://localhost:" + port + "/v1");
        helicone.setApiKey("helicone-key");
        helicone.setEnvironment("test");

        return new LangChain4jConfig(llm, new HeliconeHeadersFactory(helicone));
    }

    private void assertStreamingResponse(LangChain4jConfig config, String expected) throws Exception {
        StringBuilder partialResponse = new StringBuilder();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        config.streamingChatLanguageModel().chat("stream", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(PartialResponse partial, PartialResponseContext context) {
                partialResponse.append(partial.text());
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                completed.countDown();
            }

            @Override
            public void onError(Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });

        assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(failure.get()).isNull();
        assertThat(partialResponse).hasToString(expected);
    }

    private void assertCommonHeaders(CapturedRequest request,
                                     String targetUrl,
                                     String modelRole,
                                     String providerKey) {
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.authorization()).isEqualTo("Bearer " + providerKey);
        assertThat(request.heliconeAuth()).isEqualTo("Bearer helicone-key");
        assertThat(request.targetUrl()).isEqualTo(targetUrl);
        assertThat(request.modelRole()).isEqualTo(modelRole);
        assertThat(request.omitRequest()).isEqualTo("true");
        assertThat(request.omitResponse()).isEqualTo("true");
    }

    private void handleRequest(HttpExchange exchange, List<CapturedRequest> requests) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new CapturedRequest(
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Helicone-Auth"),
                exchange.getRequestHeaders().getFirst("Helicone-Target-URL"),
                exchange.getRequestHeaders().getFirst("Helicone-Property-ModelRole"),
                exchange.getRequestHeaders().getFirst("Helicone-Omit-Request"),
                exchange.getRequestHeaders().getFirst("Helicone-Omit-Response"),
                body));

        if (OBJECT_MAPPER.readTree(body).path("stream").asBoolean()) {
            writeStreamingResponse(exchange);
        } else {
            writeChatResponse(exchange);
        }
    }

    private void writeChatResponse(HttpExchange exchange) throws IOException {
        byte[] response = ("""
                {"id":"chatcmpl-test","object":"chat.completion","created":1,
                 "model":"test-model","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},
                 "finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private void writeStreamingResponse(HttpExchange exchange) throws IOException {
        String response = """
                data: {"id":"chatcmpl-stream","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{"role":"assistant","content":"stream-ok"},"finish_reason":null}]}

                data: {"id":"chatcmpl-stream","object":"chat.completion.chunk","created":1,"model":"test-model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

                data: [DONE]

                """;
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
        exchange.close();
    }

    private record CapturedRequest(
            String path,
            String authorization,
            String heliconeAuth,
            String targetUrl,
            String modelRole,
            String omitRequest,
            String omitResponse,
            String body) {
    }
}
