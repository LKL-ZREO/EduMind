package com.firedemo.demo.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 简化版嵌入服务 - 使用关键词匹配代替深度学习模型
 * 避免 HuggingFace 下载问题，适合国内网络环境
 */
@Slf4j
@Service
public class EmbeddingService {

    // 停用词
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "了", "在", "是", "我", "有", "和", "就", "不", "人",
        "都", "一", "一个", "上", "也", "很", "到", "说", "要", "去",
        "你", "会", "着", "没有", "看", "好", "自己", "这", "那", "之"
    );

    /**
     * 简化版嵌入 - 基于词频的稀疏向量
     * 把文本转成词频向量，用于余弦相似度计算
     */
    public float[] embed(String text) {
        try {
            // 提取关键词
            Map<String, Integer> wordFreq = extractKeywords(text);
            
            // 构建固定维度的向量（用哈希映射到固定位置）
            float[] vector = new float[384]; // 保持和原来一样的维度
            
            for (Map.Entry<String, Integer> entry : wordFreq.entrySet()) {
                // 用词的哈希值决定位置
                int idx = Math.abs(entry.getKey().hashCode()) % 384;
                vector[idx] += entry.getValue();
            }
            
            // L2 归一化
            return normalize(vector);
            
        } catch (Exception e) {
            log.error("Failed to embed text", e);
            // 返回零向量作为降级
            return new float[384];
        }
    }

    /**
     * 批量嵌入
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    /**
     * 提取关键词
     */
    private Map<String, Integer> extractKeywords(String text) {
        Map<String, Integer> freq = new HashMap<>();
        
        // 分词：按非中文字符分割
        String[] tokens = text.toLowerCase()
            .replaceAll("[^\\u4e00-\\u9fa5a-z0-9]", " ")
            .split("\\s+");
        
        for (String token : tokens) {
            if (token.length() < 2 || STOP_WORDS.contains(token)) {
                continue;
            }
            freq.merge(token, 1, Integer::sum);
        }
        
        // 提取 2-gram
        String cleanText = text.replaceAll("[^\\u4e00-\\u9fa5]", "");
        for (int i = 0; i < cleanText.length() - 1; i++) {
            String bigram = cleanText.substring(i, i + 2);
            freq.merge(bigram, 1, Integer::sum);
        }
        
        return freq;
    }

    /**
     * L2 归一化
     */
    private float[] normalize(float[] vector) {
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        
        if (norm < 1e-10) {
            return vector; // 避免除零
        }
        
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }
}
