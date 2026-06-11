package com.firedemo.demo.rag;

import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.common.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Query Rewrite — 把口语化/模糊问题改写为检索友好的关键词
 * <p>
 * 内部 LLM 调用，用户无感知。只在 query 短且可能模糊时触发。
 * Prompt 模板存放在 resource/prompts/query-rewrite.txt，修改无需重新编译。
 * <pre>
 *   "上次那个数组的题" → "数组排序 冒泡排序 选择排序"
 *   "这道题怎么解"     → 有历史上下文时补全指代
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriter {

    private final OpenClawService openClawService;
    private final PromptLoader promptLoader;

    /**
     * 改写 query
     *
     * @param originalQuery 原始问题
     * @return 改写后的检索关键词，失败时返回原始 query
     */
    public String rewrite(String originalQuery) {
        if (originalQuery == null || originalQuery.isBlank()) {
            return originalQuery;
        }

        // 不需要改写的场景：已经足够具体、关键词密集
        if (!needsRewrite(originalQuery)) {
            return originalQuery;
        }

        try {
            String template = promptLoader.load("query-rewrite.txt");
            String prompt = template.replace("{{query}}", originalQuery);
            String rewritten = openClawService.chat(prompt, null);
            // null 会话 → 不污染对话历史

            if (rewritten != null && !rewritten.isBlank()
                    && rewritten.length() < originalQuery.length() * 3) {
                log.debug("Query rewritten: \"{}\" → \"{}\"", originalQuery, rewritten);
                return rewritten.trim();
            }
        } catch (Exception e) {
            log.debug("Query rewrite failed, using original: {}", e.getMessage());
        }

        return originalQuery;
    }

    /**
     * 判断是否需要改写：
     * 短 query（<15字）或包含指代词时触发
     */
    private boolean needsRewrite(String query) {
        if (query.length() >= 15 && !containsPronouns(query)) {
            return false;
        }
        return !query.endsWith("?") || query.length() < 10;
    }

    private boolean containsPronouns(String text) {
        for (String pronoun : PRONOUNS) {
            if (text.contains(pronoun)) return true;
        }
        return false;
    }

    private static final String[] PRONOUNS = {
            "这个", "那个", "上次", "之前", "刚才", "刚刚", "前面",
            "它", "他", "她", "这道", "那道", "这个题", "那道题"
    };
}
