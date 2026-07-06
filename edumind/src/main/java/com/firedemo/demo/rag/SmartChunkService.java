package com.firedemo.demo.rag;

import com.firedemo.demo.Entity.DocumentChunk;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * RAG 文档切割服务
 * <p>
 * 按文档结构（Markdown标题 / 代码块 / 对话轮次 / 段落）切分，
 * 仅用 token 计数控制块大小，不调用 ONNX 做语义边界检测。
 */
@Slf4j
@Service
public class SmartChunkService {

    @Autowired
    private EmbeddingService embeddingService;

    // 默认最大 token 数（≈512 tokens，约1500中文字符，每个 chunk 覆盖1-2个知识点）
    private static final int DEFAULT_MAX_TOKENS = 512;
    // 默认重叠 token 数（也用作相邻 section chunk 之间的上下文窗口）
    private static final int DEFAULT_OVERLAP_TOKENS = 80;
    // 上下文摘要长度（字符数），100 太短 → 300 能覆盖 2-3 句中文
    private static final int SUMMARY_MAX_CHARS = 300;
    // Markdown 表格正则
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|.*\\|$", Pattern.MULTILINE);
    // 列表项正则（- * + 或 1. 2. 等）
    private static final Pattern LIST_ITEM = Pattern.compile("(?m)^(?:[-*+]|\\d+\\.)\\s");

    /**
     * 智能切割入口
     */
    public List<DocumentChunk> chunk(String content, ChunkConfig config) {
        if (config == null) {
            config = ChunkConfig.defaultConfig();
        }

        // 1. 文档类型检测
        DocType docType = detectDocType(content);
        log.info("Detected document type: {}", docType);

        // 2. 根据类型选择切割策略
        List<String> rawChunks;
        switch (docType) {
            case MARKDOWN:
                rawChunks = splitByMarkdownHeaders(content, config);
                break;
            case CODE:
                rawChunks = splitByCodeStructure(content, config);
                break;
            case CONVERSATION:
                rawChunks = splitByConversationTurns(content, config);
                break;
            default:
                rawChunks = splitByParagraphs(content, config);
        }

        // 3. 应用滑动窗口
        List<DocumentChunk> chunks = applySlidingWindow(rawChunks, config);

        // 4. 生成嵌入向量
        chunks.forEach(chunk -> {
            chunk.setEmbedding(embeddingService.embed(chunk.getContent()));
        });

        log.info("Chunked {} into {} chunks", docType, chunks.size());
        return chunks;
    }

    /**
     * 文档类型检测
     */
    private DocType detectDocType(String content) {
        // Markdown 优先级最高：中文教材/讲义通常有 # 标题 + 内嵌代码块
        if (content.contains("\n#") || content.contains("\n##")) {
            return DocType.MARKDOWN;
        }
        if (content.contains("```") || content.contains("def ") || content.contains("class ")) {
            return DocType.CODE;
        }
        if (content.contains("User:") || content.contains("Assistant:") ||
            content.contains(" Human:") || content.contains(" AI:")) {
            return DocType.CONVERSATION;
        }
        return DocType.GENERAL;
    }

    /**
     * Markdown结构切割 - 按标题层级
     */
    private List<String> splitByMarkdownHeaders(String content, ChunkConfig config) {
        // 匹配所有层级的标题（# ~ ######），保留 H1 作为顶级边界
        String[] sections = content.split("(?m)^(?=#{1,} )");

        List<String> chunks = new ArrayList<>();
        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) continue;

            if (estimateTokens(section) > config.getMaxTokens()) {
                chunks.addAll(splitByParagraphs(section, config));
            } else {
                chunks.add(section);
            }
        }
        return chunks;
    }

    /**
     * 代码结构切割 - 按函数/类
     */
    private List<String> splitByCodeStructure(String content, ChunkConfig config) {
        List<String> chunks = new ArrayList<>();

        String[] codeBlocks = content.split("(?m)^```");

        for (String block : codeBlocks) {
            block = block.trim();
            if (block.isEmpty()) continue;

            String lang = "";
            int newlineIdx = block.indexOf('\n');
            if (newlineIdx > 0 && newlineIdx < 20) {
                lang = block.substring(0, newlineIdx).trim();
                block = block.substring(newlineIdx + 1);
            }

            List<String> units = splitCodeByFunctions(block, lang);

            for (String unit : units) {
                if (estimateTokens(unit) > config.getMaxTokens()) {
                    chunks.addAll(splitByParagraphs(unit, config));
                } else {
                    chunks.add("```" + lang + "\n" + unit + "\n```");
                }
            }
        }

        return chunks;
    }

    /**
     * 按函数/类定义分割代码
     */
    private List<String> splitCodeByFunctions(String code, String lang) {
        List<String> functions = new ArrayList<>();

        Pattern pattern = Pattern.compile(
            "(?m)^((?:public|private|protected|static|def|class|function|func|void|int|String)\\s+[^\\n]*\\{?\\s*$)",
            Pattern.MULTILINE
        );

        java.util.regex.Matcher matcher = pattern.matcher(code);
        int lastEnd = 0;

        while (matcher.find()) {
            functions.add(code.substring(lastEnd, matcher.start()).trim());
            lastEnd = matcher.start();
        }

        if (lastEnd < code.length()) {
            functions.add(code.substring(lastEnd).trim());
        }

        return functions.isEmpty() ? Collections.singletonList(code) : functions;
    }

    /**
     * 对话切割 - 按轮次
     */
    private List<String> splitByConversationTurns(String content, ChunkConfig config) {
        Pattern pattern = Pattern.compile(
            "(?m)^(?:(?:User|Human|Assistant|AI|System):\\s*)",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
        );

        String[] turns = pattern.split(content);
        List<String> chunks = new ArrayList<>();

        StringBuilder currentChunk = new StringBuilder();
        int currentTokens = 0;

        for (String turn : turns) {
            turn = turn.trim();
            if (turn.isEmpty()) continue;

            int turnTokens = estimateTokens(turn);

            if (currentTokens + turnTokens > config.getMaxTokens() && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                currentTokens = 0;
            }

            currentChunk.append(turn).append("\n\n");
            currentTokens += turnTokens;
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    /**
     * 段落切割 —— 按双换行（自然段落边界）切分
     * <p>
     * 不再对每句话做嵌入推理（之前 splitBySemanticBoundaries 的 ONNX 调用已移除）。
     * 段落是教学文档最自然的语义边界，免费且准确。
     * 段落过大时按句子边界再切。
     */
    private List<String> splitByParagraphs(String content, ChunkConfig config) {
        List<String> chunks = new ArrayList<>();

        String[] paragraphs = content.split("\\n\\s*\\n");
        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            if (estimateTokens(para) <= config.getMaxTokens()) {
                chunks.add(para);
            } else {
                // 段落太大 → 先提取表格，再对剩余文本按句子切分
                chunks.addAll(splitWithTableProtection(para, config));
            }
        }

        return chunks.isEmpty() ? Collections.singletonList(content) : chunks;
    }

    /**
     * 保护 Markdown 表格不被拆散：提取表格 → 独立 chunk，剩余文本按句子切。
     */
    private List<String> splitWithTableProtection(String text, ChunkConfig config) {
        List<String> result = new ArrayList<>();
        java.util.regex.Matcher tm = TABLE_ROW.matcher(text);

        int lastEnd = 0;
        int tableStart = -1;
        boolean inTable = false;

        // 找连续 |...| 行组成的表格块
        while (tm.find()) {
            if (!inTable) {
                // 表格前的内容先处理
                String before = text.substring(lastEnd, tm.start()).trim();
                if (!before.isEmpty()) {
                    result.addAll(splitBySentence(before, config));
                }
                tableStart = tm.start();
                inTable = true;
            }
            // 检查下一行是否还是表格行
            int nextLineStart = tm.end() + 1; // skip \n
            if (nextLineStart >= text.length() ||
                !TABLE_ROW.matcher(text.substring(nextLineStart)).lookingAt()) {
                // 表格结束
                String tableBlock = text.substring(tableStart, tm.end()).trim();
                if (!tableBlock.isEmpty()) {
                    result.add(tableBlock);
                }
                lastEnd = tm.end();
                inTable = false;
            }
        }

        // 尾部剩余文本
        if (lastEnd < text.length()) {
            String after = text.substring(lastEnd).trim();
            if (!after.isEmpty()) {
                result.addAll(splitBySentence(after, config));
            }
        } else if (!inTable && result.isEmpty()) {
            // 没有表格 → 回退到普通句子切割
            result.addAll(splitBySentence(text, config));
        }

        return result;
    }

    /**
     * 按句子边界切分一段过大的文本
     */
    private List<String> splitBySentence(String text, ChunkConfig config) {
        // 先按列表项边界切分，列表项作为原子单元
        List<String> atomicUnits = splitByListItems(text);

        List<String> result = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int bufTokens = 0;

        for (String unit : atomicUnits) {
            int ut = estimateTokens(unit);
            // 单个单元本身就超过上限 → 按句子切
            if (ut > config.getMaxTokens()) {
                if (buf.length() > 0) {
                    result.add(buf.toString().trim());
                    buf = new StringBuilder();
                    bufTokens = 0;
                }
                // 对于超长列表项，不再按句子切（列表项内部句子通常不能独立存在）
                // 而是按固定大小切
                if (LIST_ITEM.matcher(unit).find()) {
                    result.addAll(splitByFixedSize(unit, config));
                } else {
                    // 普通文本：按句子切
                    String[] sentences = unit.split("(?<=[。！？.!?])");
                    for (String s : sentences) {
                        s = s.trim();
                        if (s.isEmpty()) continue;
                        result.add(s);
                    }
                }
            } else if (bufTokens + ut > config.getMaxTokens() && buf.length() > 0) {
                result.add(buf.toString().trim());
                buf = new StringBuilder();
                buf.append(unit);
                bufTokens = ut;
            } else {
                buf.append(unit);
                bufTokens += ut;
            }
        }
        if (buf.length() > 0) {
            result.add(buf.toString().trim());
        }
        return result.isEmpty() ? Collections.singletonList(text) : result;
    }

    /**
     * 按列表项边界切分，每个 `- item` / `1. item` 作为原子单元。
     * 连续列表项之间的文本（如引文）单独处理。
     */
    private List<String> splitByListItems(String text) {
        List<String> units = new ArrayList<>();
        java.util.regex.Matcher m = LIST_ITEM.matcher(text);
        int lastEnd = 0;

        while (m.find()) {
            // 列表项前面的非列表文本
            String before = text.substring(lastEnd, m.start());
            if (!before.isBlank()) {
                units.add(before);
            }
            // 列表项本身（可能跨多行，取到行尾）
            int lineEnd = text.indexOf('\n', m.end());
            int itemEnd = lineEnd > 0 ? lineEnd : m.end();
            units.add(text.substring(m.start(), itemEnd).trim());
            lastEnd = itemEnd;
        }

        // 尾部剩余文本
        if (lastEnd < text.length()) {
            String tail = text.substring(lastEnd).trim();
            if (!tail.isBlank()) {
                units.add(tail);
            }
        }

        return units.isEmpty() ? Collections.singletonList(text) : units;
    }

    /**
     * 滑动窗口 - 保留上下文连贯性
     */
    private List<DocumentChunk> applySlidingWindow(List<String> rawChunks, ChunkConfig config) {
        List<DocumentChunk> chunks = new ArrayList<>();

        for (int i = 0; i < rawChunks.size(); i++) {
            String content = rawChunks.get(i);

            if (estimateTokens(content) > config.getMaxTokens()) {
                List<String> subChunks = splitByFixedSize(content, config);
                for (int j = 0; j < subChunks.size(); j++) {
                    chunks.add(createChunk(subChunks.get(j), i, j, rawChunks.size()));
                }
            } else {
                chunks.add(createChunk(content, i, 0, rawChunks.size()));
            }
        }

        // 相邻 chunk：添加上下文引用 + 文本重叠
        int overlapChars = config.getOverlapTokens() * 3; // token→字符近似
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);

            if (i > 0) {
                DocumentChunk prev = chunks.get(i - 1);
                chunk.setPrevSummary(summarize(prev.getContent()));
                // 在 chunk 内容前拼接前一个 chunk 末尾的 overlap，用于 embedding 捕获边界上下文
                String prevTail = tailChars(prev.getContent(), overlapChars);
                if (prevTail.length() > 20) {
                    chunk.setContent(prevTail + "\n\n" + chunk.getContent());
                }
            }
            if (i < chunks.size() - 1) {
                DocumentChunk next = chunks.get(i + 1);
                chunk.setNextSummary(summarize(next.getContent()));
                // 在 chunk 内容后拼接下一个 chunk 开头的 overlap
                String nextHead = headChars(next.getContent(), overlapChars);
                if (nextHead.length() > 20) {
                    chunk.setContent(chunk.getContent() + "\n\n" + nextHead);
                }
            }
            // 重新计算 token 数（因为内容变化了）
            chunk.setTokenCount(estimateTokens(chunk.getContent()));
            chunk.setCharCount(chunk.getContent().length());
        }

        return chunks;
    }

    /** 取文本末尾 N 字符，尽量在句子边界截断 */
    private String tailChars(String text, int n) {
        if (text.length() <= n) return text;
        int start = text.length() - n;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '\n' || c == '！' || c == '？') {
                return text.substring(i + 1);
            }
        }
        return text.substring(start);
    }

    /** 取文本开头 N 字符，尽量在句子边界截断 */
    private String headChars(String text, int n) {
        if (text.length() <= n) return text;
        int end = n;
        for (int i = end; i > end - 80 && i > 0; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '\n' || c == '！' || c == '？') {
                return text.substring(0, i + 1);
            }
        }
        return text.substring(0, end);
    }

    /**
     * 固定大小分割（最后手段）
     */
    private List<String> splitByFixedSize(String content, ChunkConfig config) {
        List<String> chunks = new ArrayList<>();
        int maxChars = config.getMaxTokens() * 4;
        int overlapChars = config.getOverlapTokens() * 4;

        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + maxChars, content.length());

            if (end < content.length()) {
                int lastSentenceEnd = findLastSentenceEnd(content, start, end);
                if (lastSentenceEnd > start) {
                    end = lastSentenceEnd;
                }
            }

            chunks.add(content.substring(start, end).trim());

            // 已到达内容末尾，退出，避免 start 被 overlap 拉回导致死循环
            if (end >= content.length()) {
                break;
            }
            start = Math.max(0, end - overlapChars);
        }

        return chunks;
    }

    /**
     * 查找最后一个句子结束位置
     */
    private int findLastSentenceEnd(String content, int start, int end) {
        String substring = content.substring(start, end);
        int lastPeriod = substring.lastIndexOf('。');
        int lastExclaim = substring.lastIndexOf('！');
        int lastQuestion = substring.lastIndexOf('？');
        int lastDot = substring.lastIndexOf('.');

        int lastEnd = Math.max(Math.max(lastPeriod, lastExclaim),
                               Math.max(lastQuestion, lastDot));

        return lastEnd > 0 ? start + lastEnd + 1 : end;
    }

    /**
     * 创建DocumentChunk对象
     */
    private DocumentChunk createChunk(String content, int sectionIndex, int subIndex, int totalSections) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(java.util.UUID.randomUUID().toString());
        chunk.setContent(content);
        chunk.setSectionIndex(sectionIndex);
        chunk.setSubIndex(subIndex);
        chunk.setTotalSections(totalSections);
        chunk.setTokenCount(estimateTokens(content));
        chunk.setCharCount(content.length());
        return chunk;
    }

    /**
     * 生成摘要（用于上下文引用）
     */
    private String summarize(String content) {
        if (content.length() <= SUMMARY_MAX_CHARS) {
            return content;
        }
        // 尽量在句子边界截断
        int cut = SUMMARY_MAX_CHARS;
        for (int i = cut; i > cut - 100 && i > 0; i--) {
            char c = content.charAt(i);
            if (c == '。' || c == '\n' || c == '！' || c == '？' || c == '.') {
                cut = i + 1;
                break;
            }
        }
        return content.substring(0, cut) + "\n...(上文摘要)";
    }

    /**
     * 估算token数量（粗略）
     */
    private int estimateTokens(String text) {
        return text.length() / 3;
    }

    /**
     * 文档类型枚举
     */
    public enum DocType {
        MARKDOWN, CODE, CONVERSATION, GENERAL
    }

    /**
     * 配置类
     */
    @Data
    public static class ChunkConfig {
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private int overlapTokens = DEFAULT_OVERLAP_TOKENS;
        private boolean preserveStructure = true;

        public static ChunkConfig defaultConfig() {
            return new ChunkConfig();
        }
    }
}
