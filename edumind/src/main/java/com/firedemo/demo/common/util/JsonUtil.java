package com.firedemo.demo.common.util;

/**
 * JSON 解析工具 — 从 LLM 原始响应中提取纯 JSON 对象
 */
public final class JsonUtil {

    private JsonUtil() {}

    /**
     * 从 LLM 响应中提取纯 JSON 字符串
     * <p>
     * 处理以下情况：
     * <pre>
     *   "{"totalScore": 85}"                     → 直接返回
     *   "```json\n{"totalScore": 85}\n```"      → 去掉 Markdown 代码块
     *   "好的，以下是结果：\n{"totalScore": 85}"  → 提取 { 到 } 部分
     * </pre>
     */
    public static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("LLM 返回空响应");
        }

        String trimmed = raw.trim();

        // 去掉 Markdown 代码块包裹
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        trimmed = trimmed.trim();

        // 找到第一个 { 和最后一个 }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');

        if (start == -1 || end == -1 || end <= start) {
            throw new IllegalArgumentException("响应中未找到合法 JSON 对象");
        }

        return trimmed.substring(start, end + 1);
    }
}
