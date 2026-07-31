package com.firedemo.edumind.assistant;

import com.firedemo.edumind.assistant.context.AgentExecutionContext;
import com.firedemo.edumind.assistant.context.AgentRunTrace;
import com.firedemo.edumind.assistant.context.AgentUiEventBus;
import com.firedemo.edumind.assistant.langchain4j.AgentInvocationParameters;
import com.firedemo.edumind.assistant.langchain4j.LangChain4jToolBridge;
import com.firedemo.edumind.assistant.langchain4j.StatelessTeachingAgent;
import com.firedemo.edumind.assistant.langchain4j.StreamingTeachingAgent;
import com.firedemo.edumind.assistant.langchain4j.TeachingAgent;
import com.firedemo.edumind.assistant.memory.AgentMemoryId;
import com.firedemo.edumind.assistant.memory.PersistentAgentChatMemoryProvider;
import com.firedemo.edumind.shared.exception.BusinessException;
import com.firedemo.edumind.shared.exception.ErrorCode;
import com.firedemo.edumind.assistant.config.LlmProperties;
import com.firedemo.edumind.classroom.CourseMapper;
import com.firedemo.edumind.assistant.context.AgentSessionStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.annotation.Timed;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.*;

/**
 * LangChain4j 驱动的 Agent 服务，替代旧的手写 Agent 循环实现。
 *
 * <h3>核心架构</h3>
 * <pre>
 *   ChatModel (OpenAiChatModel)           ─→  AiServices  ─→  TeachingAgent 接口
 *   StreamingChatModel (OpenAiStreamingChatModel) ─→  AiServices  ─→  StreamingTeachingAgent 接口
 *   LangChain4jToolBridge (@Tool)        ─→  ToolDefinition Bean  ─→  实际业务逻辑
 * </pre>
 *
 * <h3>与旧实现的对比</h3>
 * <ul>
 *   <li>while 循环 → AiServices 内置工具调用循环</li>
 *   <li>手写 JSON Schema → @Tool 注解自动生成</li>
 *   <li>RestClient/WebClient → ChatLanguageModel / StreamingChatLanguageModel</li>
 *   <li>手写 SSE 解析 → TokenStream → Flux&lt;String&gt;</li>
 *   <li>executeToolsInParallel() → LangChain4j 框架内置并行工具执行</li>
 * </ul>
 *
 * <h3>保留能力</h3>
 * <ul>
 *   <li>自我反思（P0-1）：AiServices 拿到结果后，再用 ChatLanguageModel 做一轮审查</li>
 *   <li>动态 System Prompt：按课程加载，通过 systemMessageProvider 注入</li>
 *   <li>CircuitBreaker + @Timed：注解保留，行为一致</li>
 *   <li>Session 上下文：AgentExecutionContext + LangChain4j InvocationParameters</li>
 * </ul>
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "edumind.llm.backend", havingValue = "built-in", matchIfMissing = true)
public class LangChain4jAgentService implements AgentService {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingModel;
    private final LangChain4jToolBridge toolBridge;
    private final AgentSessionStore agentSessionStore;
    private final CourseMapper courseMapper;
    private final LlmProperties llmProperties;
    private final PersistentAgentChatMemoryProvider memoryProvider;
    private final AgentUiEventBus uiEventBus;

    // ── 一次性构建的 Agent 实例 ──
    private TeachingAgent sessionAgent;           // 带工具 + ChatMemory（per sessionId）
    private StatelessTeachingAgent statelessAgent; // 无工具 + 无记忆（结构化输出用）
    private StreamingTeachingAgent streamingAgent; // 流式 + 工具 + ChatMemory

    public LangChain4jAgentService(@Qualifier("chatLanguageModel") ChatModel chatModel,
                                   @Qualifier("streamingChatLanguageModel") StreamingChatModel streamingModel,
                                   LangChain4jToolBridge toolBridge,
                                   AgentSessionStore agentSessionStore,
                                   CourseMapper courseMapper,
                                   LlmProperties llmProperties,
                                   PersistentAgentChatMemoryProvider memoryProvider,
                                   AgentUiEventBus uiEventBus) {
        this.chatModel = chatModel;
        this.streamingModel = streamingModel;
        this.toolBridge = toolBridge;
        this.agentSessionStore = agentSessionStore;
        this.courseMapper = courseMapper;
        this.llmProperties = llmProperties;
        this.memoryProvider = memoryProvider;
        this.uiEventBus = uiEventBus;
    }

    @PostConstruct
    void init() {
        // ── 有状态的 Session Agent（工具 + ChatMemory + 动态 System Prompt）──
        this.sessionAgent = AiServices.builder(TeachingAgent.class)
                .chatModel(chatModel)
                .systemMessageProvider(this::resolveSystemPrompt)
                .tools(toolBridge)
                .chatMemoryProvider(memoryProvider)
                .maxSequentialToolsInvocations(llmProperties.getMaxSteps())
                .build();

        // ── 无状态的 Agent（无工具、无记忆，用于结构化输出 / 批改场景）──
        this.statelessAgent = AiServices.builder(StatelessTeachingAgent.class)
                .chatModel(chatModel)
                .systemMessageProvider(memoryId -> DEFAULT_SYSTEM_PROMPT)
                .build();

        // ── 流式 Session Agent ──
        this.streamingAgent = AiServices.builder(StreamingTeachingAgent.class)
                .streamingChatModel(streamingModel)
                .systemMessageProvider(this::resolveSystemPrompt)
                .tools(toolBridge)
                .chatMemoryProvider(memoryProvider)
                .maxSequentialToolsInvocations(llmProperties.getMaxSteps())
                .build();

        log.info("LangChain4jAgentService 初始化完成: model={}, maxSteps={}, selfReflection={}",
                llmProperties.getModel(), llmProperties.getMaxSteps(), llmProperties.isSelfReflection());
    }

    // ========================================================================
    //  非流式对话
    // ========================================================================

    /**
     * 2-arg 版本：无 session，无 tools（用于结构化输出场景如作业批改）。
     */
    @Override
    @Timed(value = "llm.chat", histogram = true)
    @CircuitBreaker(name = "llm", fallbackMethod = "chatFallback")
    public String chat(String message, String status) {
        log.info("LC4j chat (no-tools): status={}, msg={}", status, truncate(message, 50));
        return statelessAgent.chat(message);
    }

    /**
     * 3-arg 版本：带 session 上下文，带 tools（主要对话入口）。
     */
    @Override
    @Timed(value = "llm.chat", histogram = true)
    @CircuitBreaker(name = "llm", fallbackMethod = "chatFallback")
    public String chat(String message, AgentExecutionContext context, String status) {
        Objects.requireNonNull(context, "execution context is required");
        log.info("LC4j chat: sessionId={}, msg={}", context.sessionId(), truncate(message, 50));

        AgentRunTrace trace = new AgentRunTrace(context);
        AgentMemoryId memoryId = AgentMemoryId.from(context);
        try {
            String result = sessionAgent.chat(
                    memoryId, message, AgentInvocationParameters.create(context, trace));

            // P0-1 自我反思：仅在工具被调用过 + 回答篇幅足够时触发，
            // 避免简单问候/闲聊场景下白白增加延迟
            if (llmProperties.isSelfReflection()
                    && trace.hasToolCalls()
                    && result.length() >= 100) {
                String draft = result;
                result = performSelfReflection(memoryId, draft);
                if (!Objects.equals(draft, result)) {
                    memoryProvider.replaceLastAiMessage(memoryId, draft, result);
                }
            } else if (trace.hasToolCalls() && result.length() < 100) {
                log.debug("跳过自我反思: 回答过短 ({} 字 < 100)", result.length());
            }
            return result;
        } finally {
            logRunTrace(context, trace);
            uiEventBus.complete(context.traceId());
        }
    }

    // ========================================================================
    //  流式对话
    // ========================================================================

    @Override
    @Timed(value = "llm.stream", histogram = true)
    @CircuitBreaker(name = "llm", fallbackMethod = "streamChatFallback2")
    public Flux<String> streamChat(String message, AgentExecutionContext context, String status) {
        Objects.requireNonNull(context, "execution context is required");
        log.info("LC4j SSE stream: sessionId={}, msg={}",
                context.sessionId(), truncate(message, 50));

        AgentRunTrace trace = new AgentRunTrace(context);
        AgentMemoryId memoryId = AgentMemoryId.from(context);
        TokenStream tokenStream = streamingAgent.chat(
                memoryId, message, AgentInvocationParameters.create(context, trace));
        return toFlux(tokenStream)
                .doFinally(signalType -> {
                    logRunTrace(context, trace);
                    uiEventBus.complete(context.traceId());
                });
    }

    // ========================================================================
    //  会话上下文
    // ========================================================================

    @Override
    public void registerSessionContext(AgentExecutionContext context) {
        agentSessionStore.put(context);
    }

    @Override
    public void clearMemory(Long userId) {
        memoryProvider.clearByUserId(userId);
    }

    @Override
    public void clearMemory(Long userId, String sessionId) {
        memoryProvider.clear(userId, sessionId);
    }

    // ========================================================================
    //  健康检查
    // ========================================================================

    @Override
    public boolean checkConnection() {
        try {
            ChatResponse response = chatModel.chat(
                    UserMessage.from("hi")
            );
            return response != null && response.aiMessage() != null;
        } catch (RuntimeException e) {
            log.warn("LLM 连接检查失败: {}", e.getMessage());
            return false;
        }
    }

    // ========================================================================
    //  System Prompt 解析
    // ========================================================================

    static final String DEFAULT_SYSTEM_PROMPT = """
            你是教学助手。请严格遵循以下工具调用规则：

            1. 【必须检索知识库】涉及课程专业知识时，先调用 searchKnowledge
            2. 【必须查实时数据】班级/学生问题调用对应工具：
               - 班级整体情况 → queryClassStatus
               - 单个学生成绩 → queryStudentStats
               - 作业任务列表 → queryHomeworkTasks
            3. 【无需工具】打招呼、闲聊、感谢、简单追问
            关键原则：宁可多搜一次，不要凭记忆硬答。""";

    /**
     * 动态解析 System Prompt — 由 {@code systemMessageProvider} 在每次 Agent 调用时触发。
     */
    private static final String GLOBAL_TOOL_PROMPT_SUFFIX = """

            Global tool rules:
            - If the user sends an image, screenshot, photo of an exercise, table image, code screenshot,
              or a message containing [CQ:image,...url=...], call analyzeVisualContent first.
            - After analyzeVisualContent returns visual findings, call searchKnowledge if the question also
              involves course knowledge, homework requirements, or concept explanation.
            - Never guess image content only from a URL, file name, or CQ code.
            """;

    private String resolveSystemPrompt(Object memoryId) {
        String sessionId = memoryId instanceof AgentMemoryId id ? id.sessionId() : null;
        if (sessionId != null && !sessionId.isEmpty()) {
            Long courseId = agentSessionStore.getCourseId(sessionId);
            if (courseId != null) {
                String prompt = courseMapper.selectSystemPromptById(courseId);
                if (prompt != null && !prompt.isBlank()) {
                    log.debug("使用课程 System Prompt: courseId={}", courseId);
                    return prompt + GLOBAL_TOOL_PROMPT_SUFFIX;
                }
            }
        }

        return DEFAULT_SYSTEM_PROMPT + GLOBAL_TOOL_PROMPT_SUFFIX;
    }

    // ========================================================================
    //  自我反思
    // ========================================================================

    private static final String SELF_REFLECTION_PROMPT =
            "请以上面的回答草稿为基础，严格检查以下几点后直接输出修正后的回答：\n"
                    + "1. 是否引用了工具返回的具体数据（如确切的分数、人数、时间）？有遗漏请补上\n"
                    + "2. 是否有用户问题中提及但未充分回答的部分？有请补充\n"
                    + "3. 是否有任何猜测或不确定的内容？有请标注或删除\n\n"
                    + "重要规则：\n"
                    + "- 你现在处于纯文本审查模式，禁止调用任何工具、禁止输出工具调用指令\n"
                    + "- 直接基于草稿内容修改，不要试图重新查询数据\n"
                    + "- 直接输出修正后的最终回答内容，不要加【修正说明】【修改日志】等任何元信息\n"
                    + "- 不要输出【根据您的要求】【经过检查】等开场白\n"
                    + "- 不要用分隔线、标题来标记修改内容\n"
                    + "- 让读者感觉这就是原始回答，而非被修改过的版本";

    /**
     * 自我反思：拿到 AiServices 最终回答后，用 ChatModel 再做一轮质量审查。
     * 失败降级返回原始草稿。
     */
    private String performSelfReflection(AgentMemoryId memoryId, String draft) {
        log.info("LC4j 自我反思: draftLen={}", draft.length());
        long start = System.currentTimeMillis();

        try {
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(resolveSystemPrompt(memoryId)),
                    AiMessage.from(draft),
                    UserMessage.from(SELF_REFLECTION_PROMPT)
            );

            ChatResponse response = chatModel.chat(messages.toArray(new ChatMessage[0]));
            String refined = response.aiMessage().text();

            long elapsed = System.currentTimeMillis() - start;

            // 安全检查：防止模型幻觉出工具调用或输出异常短的结果
            if (refined == null || refined.isBlank()) {
                log.warn("自我反思返回空内容，降级使用原始草稿");
                return draft;
            }
            if (refined.contains("<tool_call") || refined.contains("</tool_call>")) {
                log.warn("自我反思幻觉出工具调用标记，降级使用原始草稿: refined={}", truncate(refined, 80));
                return draft;
            }
            if (refined.length() < draft.length() * 0.3) {
                log.warn("自我反思结果过短 ({} < {}*0.3={})，降级使用原始草稿",
                        refined.length(), draft.length(), (int)(draft.length() * 0.3));
                return draft;
            }

            log.info("LC4j 自我反思完成: draftLen={} → refinedLen={}, elapsed={}ms",
                    draft.length(), refined.length(), elapsed);
            return refined;
        } catch (Exception e) {
            log.warn("自我反思调用失败，降级使用原始草稿: {}", e.getMessage());
        }

        return draft;
    }

    // ========================================================================
    //  TokenStream → Flux<String> 转换
    // ========================================================================

    /**
     * 将 LangChain4j 的 {@link TokenStream} 转为 Spring WebFlux 的 {@link Flux}。
     * <p>
     * 不使用 {@code langchain4j-reactor} 模块（减少依赖），直接用 {@link Flux#create} 桥接。
     */
    private Flux<String> toFlux(TokenStream tokenStream) {
        return Flux.<String>create(emitter -> {
            tokenStream.onPartialResponse(token -> {
                        if (!emitter.isCancelled()) {
                            emitter.next(token);
                        }
                    })
                    .onCompleteResponse(response -> {
                        log.debug("TokenStream 完成: contentLen={}",
                                response.aiMessage() != null ? response.aiMessage().text().length() : 0);
                        emitter.complete();
                    })
                    .onError(error -> {
                        log.error("TokenStream 错误: {}", error.getMessage());
                        if (!emitter.isCancelled()) {
                            emitter.error(error);
                        }
                    })
                    .start();
        }, FluxSink.OverflowStrategy.BUFFER)
                .doOnError(e -> log.error("Flux 流错误: {}", e.getMessage()));
    }

    private void logRunTrace(AgentExecutionContext context, AgentRunTrace trace) {
        log.info("Agent run completed: traceId={}, sessionId={}, channel={}, toolCalls={}, elapsedMs={}",
                trace.traceId(), context.sessionId(), context.channel(),
                trace.toolCallCount(), trace.elapsedMillis());
    }

    /*
        纯粹的日志安全措施。避免把几百字的用户消息全文打到日志里（污染日志文件、泄露敏感信息、影响可读性）。截断成 50 字 + … 够你判断"大概是什么内容"即可。
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }

    // ========================================================================
    //  熔断 Fallback
    // ========================================================================

    @SuppressWarnings("unused")
    private String chatFallback(String message, AgentExecutionContext context, String status, Throwable t) {
        log.warn("LLM 服务熔断/降级: sessionId={}, error={}", context.sessionId(), t.getMessage());
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
    }

    @SuppressWarnings("unused")
    private String chatFallback(String message, String status, Throwable t) {
        log.warn("LLM 服务熔断/降级: error={}", t.getMessage());
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
    }

    @SuppressWarnings("unused")
    private Flux<String> streamChatFallback2(String message, AgentExecutionContext context,
                                             String status, Throwable t) {
        log.warn("LLM 流式服务熔断/降级: sessionId={}, error={}",
                context.sessionId(), t.getMessage());
        return Flux.just("AI 服务暂时不可用，请稍后重试。");
    }
}
