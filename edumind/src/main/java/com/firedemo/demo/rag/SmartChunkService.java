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
    // 默认重叠 token 数
    private static final int DEFAULT_OVERLAP_TOKENS = 50;

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
        String[] sections = content.split("(?m)^(?=##+ )");

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
                // 段落太大 → 按句子边界再切
                chunks.addAll(splitBySentence(para, config));
            }
        }

        return chunks.isEmpty() ? Collections.singletonList(content) : chunks;
    }

    /**
     * 按句子边界切分一段过大的文本
     */
    private List<String> splitBySentence(String text, ChunkConfig config) {
        List<String> result = new ArrayList<>();
        String[] sentences = text.split("(?<=[。！？.!?])");
        StringBuilder buf = new StringBuilder();
        int bufTokens = 0;

        for (String s : sentences) {
            s = s.trim();
            if (s.isEmpty()) continue;
            int st = estimateTokens(s);
            if (bufTokens + st > config.getMaxTokens() && buf.length() > 0) {
                result.add(buf.toString().trim());
                buf = new StringBuilder();
                bufTokens = 0;
            }
            buf.append(s);
            bufTokens += st;
        }
        if (buf.length() > 0) {
            result.add(buf.toString().trim());
        }
        return result;
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

        // 添加相邻chunk的上下文引用
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);

            if (i > 0) {
                chunk.setPrevSummary(summarize(chunks.get(i - 1).getContent()));
            }
            if (i < chunks.size() - 1) {
                chunk.setNextSummary(summarize(chunks.get(i + 1).getContent()));
            }
        }

        return chunks;
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
        if (content.length() <= 100) {
            return content;
        }
        return content.substring(0, 100) + "...";
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
