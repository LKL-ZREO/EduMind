package com.firedemo.demo.rag;

import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.infrastructure.prompt.PromptLoader;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

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

    /** 改写结果本地缓存：同一查询 5 分钟内不重复调用 LLM */
    private final Cache<String, String> rewriteCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(200)
            .build();

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

        // 查缓存 — 同一查询 5 分钟内不重复调用 LLM 改写
        String cached = rewriteCache.getIfPresent(originalQuery);
        if (cached != null) {
            log.debug("Query rewrite cache hit: \"{}\" → \"{}\"", originalQuery, cached);
            return cached;
        }

        try {
            String template = promptLoader.load("query-rewrite.txt");
            String prompt = template.replace("{{query}}", originalQuery);
            String rewritten = openClawService.chat(prompt, null);

            if (rewritten != null && !rewritten.isBlank()
                    && rewritten.length() < originalQuery.length() * 3) {
                String result = rewritten.trim();
                rewriteCache.put(originalQuery, result);
                log.debug("Query rewritten: \"{}\" → \"{}\"", originalQuery, result);
                return result;
            }
        } catch (Exception e) {
            log.debug("Query rewrite failed, using original: {}", e.getMessage());
        }

        return originalQuery;
    }

    /**
     * 规则判断是否需要 LLM 改写（纯 Java 字符串操作，~5 微秒）。
     *
     * <p>满足以下任一条件即需要改写：
     * <ol>
     *   <li>包含指代词（这个/那个/上次/前面…）—— 需要消解</li>
     *   <li>长度 &lt; 8 字 —— 信息量不足</li>
     *   <li>不含任何技术术语 —— 口语化描述，需要翻译为术语</li>
     * </ol>
     *
     * <p>不满足则跳过改写，省掉 LLM 调用。
     */
    public boolean needsRewrite(String query) {
        if (query == null || query.isBlank()) return false;
        // 1. 指代词 → 需要消解
        if (containsPronouns(query)) return true;
        // 2. 太短 → 信息量不够
        if (query.length() < 8) return true;
        // 3. 没有任何技术术语 → 口语化，要翻译
        if (!containsTechTerms(query)) return true;
        // 已经够明确
        return false;
    }

    // ==================== 检测工具 ====================

    private boolean containsPronouns(String text) {
        for (String pronoun : PRONOUNS) {
            if (text.contains(pronoun)) return true;
        }
        return false;
    }

    /** 检查文本是否包含至少一个 C/C++/编程相关术语 */
    private boolean containsTechTerms(String text) {
        for (String term : C_TECH_TERMS) {
            if (text.contains(term)) return true;
        }
        return false;
    }

    private static final String[] PRONOUNS = {
            "这个", "那个", "上次", "之前", "刚才", "刚刚", "前面", "上回",
            "它", "他", "她", "这道", "那道", "这个题", "那道题"
    };

    /** C/C++/编程常用术语（小写匹配） */
    private static final java.util.Set<String> C_TECH_TERMS = java.util.Set.of(
            // 数据类型
            "int", "char", "float", "double", "void", "long", "short", "unsigned", "bool",
            "数组", "指针", "结构体", "枚举", "联合体", "字符串", "变量", "常量", "函数",
            "内存", "地址", "引用", "类型", "定义", "声明",
            // 运算符 & 控制流
            "运算符", "表达式", "循环", "条件", "分支", "跳转", "返回", "赋值",
            "sizeof", "typedef",
            // 内存管理
            "malloc", "calloc", "realloc", "free", "栈", "堆", "分配", "释放", "泄漏",
            // 指针相关
            "解引用", "取地址", "二级指针", "空指针", "野指针", "悬空指针",
            "const", "static", "extern", "register", "volatile",
            // 数据结构
            "链表", "队列", "树", "图", "哈希", "排序", "查找",
            // 文件 & IO
            "printf", "scanf", "fopen", "fclose", "文件", "输入", "输出", "流",
            // 预处理器
            "宏", "include", "define", "ifdef", "ifndef", "头文件", "预处理",
            // 算法
            "递归", "迭代", "算法", "复杂度", "二分", "冒泡", "快排",
            // 其他
            "位运算", "进制", "进制转换", "编译", "调试", "断点",
            "struct", "union", "enum", "return", "break", "continue", "switch", "case",
            "default", "goto", "null", "stdio", "stdlib", "string"
    );
}
