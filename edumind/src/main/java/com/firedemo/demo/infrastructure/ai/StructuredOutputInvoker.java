package com.firedemo.demo.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * 结构化输出调用器 — LLM JSON 解析失败自动重试
 * <p>
 * 核心逻辑：
 * <ol>
 *   <li>调 LLM → 解析 JSON</li>
 *   <li>解析失败 → 把上一次的错误信息注入 prompt → 再调一次 LLM</li>
 *   <li>最多重试 N 次</li>
 * </ol>
 * <p>
 * 使用示例：
 * <pre>{@code
 *   EvaluationResultDTO result = invoker.invoke(
 *       prompt -> openClawService.chat(prompt, sessionId),
 *       prompt,
 *       EvaluationResultDTO.class,
 *       "作业批改"
 *   );
 * }</pre>
 */
@Slf4j
@Component
public class StructuredOutputInvoker {

    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    private static final int LOG_TRUNCATE_CHARS = 200;
    private static final int ERROR_MSG_TRUNCATE_CHARS = 300;

    public StructuredOutputInvoker(ObjectMapper objectMapper,
                                   @Value("${app.ai.structured-max-attempts:2}") int maxAttempts) {
        this.objectMapper = objectMapper;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /**
     * 调用 LLM 并解析为指定类型，失败自动重试
     *
     * @param llmCall LLM 调用函数（lambda），入参 prompt，出参 LLM 原始响应字符串
     * @param prompt  原始 prompt
     * @param clazz   目标 Java 类型
     * @param logCtx  日志上下文，如 "作业批改 submissionId=123"
     * @param <T>     目标类型
     * @return 解析后的对象
     * @throws RuntimeException 所有重试都失败时抛出
     */
    public <T> T invoke(Function<String, String> llmCall, String prompt,
                        Class<T> clazz, String logCtx) {
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            // 第 1 次：原始 prompt
            // 第 2+ 次：原始 prompt + 解析失败原因
            String finalPrompt = attempt == 1
                    ? prompt
                    : buildRetryPrompt(prompt, lastError);

            try {
                String rawResponse = llmCall.apply(finalPrompt);
                log.debug("{} LLM 响应 (attempt={}): {}", logCtx, attempt,
                        rawResponse != null ? rawResponse.substring(0, Math.min(LOG_TRUNCATE_CHARS, rawResponse.length())) : "null");

                // 提取纯 JSON（去掉 Markdown 代码块和前后废话）
                String json = extractJson(rawResponse);
                T result = objectMapper.readValue(json, clazz);
                log.info("{} 结构化输出成功 (attempt={})", logCtx, attempt);
                return result;

            } catch (JsonProcessingException | RuntimeException e) {
                lastError = (e instanceof RuntimeException r) ? r : new RuntimeException(e);
                log.warn("{} 结构化解析失败 (attempt={}/{}): {}",
                        logCtx, attempt, maxAttempts, e.getMessage());
            }
        }

        throw new RuntimeException(
                logCtx + " 结构化输出失败(已重试" + maxAttempts + "次): "
                        + (lastError != null ? lastError.getMessage() : "unknown"));
    }

    // ==================== 内部实现 ====================

    /**
     * 构建重试 prompt：保留原始指令，额外强调 JSON 格式要求，附带上一次的错误原因
     */
    private String buildRetryPrompt(String originalPrompt, RuntimeException lastError) {
        StringBuilder sb = new StringBuilder(originalPrompt);
        sb.append("\n\n");
        sb.append("⚠️ 重要：你的上一次输出无法被 JSON 解析器解析。请严格遵守以下规则：\n");
        sb.append("1. 只输出纯 JSON 对象，不要用 ```json 代码块包裹\n");
        sb.append("2. 不要输出任何解释文字、前缀或后缀\n");
        sb.append("3. 所有字符串内的双引号必须用反斜杠转义（如 \\\"）\n");
        sb.append("4. 确保 JSON 完整，不要截断\n");

        if (lastError != null && lastError.getMessage() != null) {
            String errMsg = lastError.getMessage();
            // 截断太长的错误信息，避免 prompt 过长
            if (errMsg.length() > ERROR_MSG_TRUNCATE_CHARS) {
                errMsg = errMsg.substring(0, ERROR_MSG_TRUNCATE_CHARS) + "...";
            }
            sb.append("\n上次解析失败原因：").append(errMsg);
        }

        return sb.toString();
    }

    /**
     * 从 LLM 原始响应中提取纯 JSON
     * <p>
     * 处理以下情况：
     * <pre>
     *   "{"totalScore": 85}"                     → 直接返回
     *   "```json\n{"totalScore": 85}\n```"      → 去掉 Markdown 代码块
     *   "好的，以下是结果：\n{"totalScore": 85}"  → 提取 { 到 } 部分
     * </pre>
     */
    /** 委托给 {@link JsonUtil#extractJson(String)} */
    String extractJson(String raw) {
        return JsonUtil.extractJson(raw);
    }
}
