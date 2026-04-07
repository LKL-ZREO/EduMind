package com.firedemo.demo.rag;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG智能上下文切割服务
 * 结合语义分析、结构感知和滑动窗口策略
 */
@Slf4j
@Service
public class SmartChunkService {

    @Autowired
    private EmbeddingService embeddingService;

    // 语义相似度阈值
    private static final double SEMANTIC_THRESHOLD = 0.7;
    // 默认最大token数
    private static final int DEFAULT_MAX_TOKENS = 512;
    // 默认重叠token数
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
                rawChunks = splitBySemanticBoundaries(content, config);
        }

        // 3. 应用滑动窗口和语义边界优化
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
        if (content.contains("```") || content.contains("def ") || content.contains("class ")) {
            return DocType.CODE;
        }
        if (content.contains("#") && content.contains("\n#")) {
            return DocType.MARKDOWN;
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
        // 按 ## 标题分割，保留层级结构
        String[] sections = content.split("(?m)^(?=##+ )");
        
        List<String> chunks = new ArrayList<>();
        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) continue;
            
            // 如果section太大，进一步按语义切割
            if (estimateTokens(section) > config.getMaxTokens()) {
                chunks.addAll(splitBySemanticBoundaries(section, config));
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
        
        // 按代码块分割
        String[] codeBlocks = content.split("(?m)^```");
        
        for (String block : codeBlocks) {
            block = block.trim();
            if (block.isEmpty()) continue;
            
            // 检测语言标记
            String lang = "";
            int newlineIdx = block.indexOf('\n');
            if (newlineIdx > 0 && newlineIdx < 20) {
                lang = block.substring(0, newlineIdx).trim();
                block = block.substring(newlineIdx + 1);
            }
            
            // 按函数/类定义分割
            List<String> units = splitCodeByFunctions(block, lang);
            
            for (String unit : units) {
                if (estimateTokens(unit) > config.getMaxTokens()) {
                    chunks.addAll(splitBySemanticBoundaries(unit, config));
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
        
        // 通用函数/类定义模式
        Pattern pattern = Pattern.compile(
            "(?m)^((?:public|private|protected|static|def|class|function|func|void|int|String)\\s+[^\\n]*\\{?\\s*$)",
            Pattern.MULTILINE
        );
        
        java.util.regex.Matcher matcher = pattern.matcher(code);
        int lastEnd = 0;
        
        while (matcher.find()) {
            if (lastEnd > 0) {
                functions.add(code.substring(lastEnd, matcher.start()).trim());
            }
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
        // 按对话角色分割
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
            
            // 保持对话轮次完整性
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
     * 语义边界切割 - 核心算法
     * 使用句子边界 + 语义相似度
     */
    private List<String> splitBySemanticBoundaries(String content, ChunkConfig config) {
        // 1. 按句子分割
        String[] sentences = content.split("(?<=[。！？.!?])\\s+");
        
        if (sentences.length == 0) {
            return Collections.singletonList(content);
        }
        
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        int currentTokens = 0;
        
        // 获取句子嵌入向量（批量优化）
        List<float[]> embeddings = embeddingService.embedBatch(Arrays.asList(sentences));
        
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i].trim();
            if (sentence.isEmpty()) continue;
            
            int sentenceTokens = estimateTokens(sentence);
            
            // 检查语义边界
            boolean isBoundary = false;
            if (i > 0 && currentChunk.length() > 0) {
                double similarity = cosineSimilarity(embeddings.get(i), embeddings.get(i-1));
                // 语义相似度低 = 主题转换 = 切割点
                if (similarity < SEMANTIC_THRESHOLD) {
                    isBoundary = true;
                }
            }
            
            // 如果超出token限制或遇到语义边界，保存当前chunk
            if ((currentTokens + sentenceTokens > config.getMaxTokens() || isBoundary) 
                && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                currentTokens = 0;
            }
            
            currentChunk.append(sentence).append(" ");
            currentTokens += sentenceTokens;
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }

    /**
     * 滑动窗口 - 保留上下文连贯性
     */
    private List<DocumentChunk> applySlidingWindow(List<String> rawChunks, ChunkConfig config) {
        List<DocumentChunk> chunks = new ArrayList<>();
        
        for (int i = 0; i < rawChunks.size(); i++) {
            String content = rawChunks.get(i);
            
            // 如果内容仍太大，进一步分割
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
            
            // 添加上下文摘要
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
        int maxChars = config.getMaxTokens() * 4; // 粗略估算
        int overlapChars = config.getOverlapTokens() * 4;
        
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + maxChars, content.length());
            
            // 尝试在句子边界结束
            if (end < content.length()) {
                int lastSentenceEnd = findLastSentenceEnd(content, start, end);
                if (lastSentenceEnd > start) {
                    end = lastSentenceEnd;
                }
            }
            
            chunks.add(content.substring(start, end).trim());
            start = end - overlapChars;
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
        // 简单摘要：取前100个字符
        if (content.length() <= 100) {
            return content;
        }
        return content.substring(0, 100) + "...";
    }

    /**
     * 估算token数量（粗略）
     */
    private int estimateTokens(String text) {
        // 中文约1.5字符/token，英文约4字符/token
        // 简化计算：平均3.5字符/token
        return text.length() / 3;
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        
        double dot = 0;
        double normA = 0;
        double normB = 0;
        
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
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
        private boolean useSemanticBoundaries = true;

        public static ChunkConfig defaultConfig() {
            return new ChunkConfig();
        }
    }
}
