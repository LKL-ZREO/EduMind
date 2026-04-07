package com.firedemo.demo.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.firedemo.demo.mapper.DocumentChunkMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


/**
 * 向量存储服务 - 使用 PostgreSQL 存储文档块和向量
 */
@Slf4j
@Service
public class VectorStoreService {

    @Autowired
    private DocumentChunkMapper documentChunkMapper;

    @PostConstruct
    public void init() {
        log.info("VectorStoreService initialized with PostgreSQL");
    }

    /**
     * 保存文档块
     */
    public void saveChunks(String documentId, List<DocumentChunk> chunks) {
        for (DocumentChunk chunk : chunks) {
            chunk.setId(UUID.randomUUID().toString());
            chunk.setDocumentId(documentId);
            documentChunkMapper.insert(toEntity(chunk));
        }
        log.info("Saved {} chunks for document {}", chunks.size(), documentId);
    }

    /**
     * 根据文档ID获取所有chunk
     */
    public List<DocumentChunk> getChunksByDocument(String documentId) {
        LambdaQueryWrapper<DocumentChunkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunkEntity::getDocumentId, documentId);
        List<DocumentChunkEntity> entities = documentChunkMapper.selectList(wrapper);
        return entities.stream().map(this::fromEntity).collect(Collectors.toList());
    }

    /**
     * 根据ID获取chunk
     */
    public DocumentChunk getChunkById(String chunkId) {
        DocumentChunkEntity entity = documentChunkMapper.selectById(chunkId);
        return entity != null ? fromEntity(entity) : null;
    }

    /**
     * 相似度搜索 - 全量遍历（适合小规模数据）
     * 生产环境建议使用 PGVector 扩展或专用向量数据库
     */
    public List<DocumentChunk> similaritySearch(float[] queryEmbedding, int topK) {
        // 获取所有 chunk（简化实现，小数据量可用）
        List<DocumentChunkEntity> allEntities = documentChunkMapper.selectList(null);
        
        if (allEntities.isEmpty()) {
            return Collections.emptyList();
        }
        
        return allEntities.stream()
            .map(entity -> {
                DocumentChunk chunk = fromEntity(entity);
                if (chunk.getEmbedding() != null) {
                    double score = cosineSimilarity(queryEmbedding, chunk.getEmbedding());
                    return new ScoredChunk(chunk, score);
                }
                return null;
            })
            .filter(Objects::nonNull)
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(topK)
            .map(sc -> sc.chunk)
            .collect(Collectors.toList());
    }

    /**
     * 删除文档的所有chunk
     */
    public void deleteDocument(String documentId) {
        LambdaQueryWrapper<DocumentChunkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunkEntity::getDocumentId, documentId);
        int count = documentChunkMapper.delete(wrapper);
        log.info("Deleted {} chunks for document {}", count, documentId);
    }

    /**
     * DocumentChunk 转 Entity
     */
    private DocumentChunkEntity toEntity(DocumentChunk chunk) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.setId(chunk.getId());
        entity.setContent(chunk.getContent());
        entity.setDocumentId(chunk.getDocumentId());
        entity.setDocumentName(chunk.getDocumentName());
        entity.setSectionIndex(chunk.getSectionIndex());
        entity.setSubIndex(chunk.getSubIndex());
        entity.setTokenCount(chunk.getTokenCount());
        entity.setCharCount(chunk.getCharCount());
        entity.setPrevSummary(chunk.getPrevSummary());
        entity.setNextSummary(chunk.getNextSummary());
        
        if (chunk.getEmbedding() != null) {
            entity.setEmbedding(encodeEmbedding(chunk.getEmbedding()));
        }
        
        return entity;
    }

    /**
     * Entity 转 DocumentChunk
     */
    private DocumentChunk fromEntity(DocumentChunkEntity entity) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(entity.getId());
        chunk.setContent(entity.getContent());
        chunk.setDocumentId(entity.getDocumentId());
        chunk.setDocumentName(entity.getDocumentName());
        chunk.setSectionIndex(entity.getSectionIndex());
        chunk.setSubIndex(entity.getSubIndex());
        chunk.setTokenCount(entity.getTokenCount());
        chunk.setCharCount(entity.getCharCount());
        chunk.setPrevSummary(entity.getPrevSummary());
        chunk.setNextSummary(entity.getNextSummary());
        
        if (entity.getEmbedding() != null) {
            chunk.setEmbedding(decodeEmbedding(entity.getEmbedding()));
        }
        
        return chunk;
    }

    /**
     * 编码嵌入向量
     */
    private String encodeEmbedding(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        return sb.toString();
    }

    /**
     * 解码嵌入向量
     */
    private float[] decodeEmbedding(String str) {
        String[] parts = str.split(",");
        float[] embedding = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            embedding[i] = Float.parseFloat(parts[i]);
        }
        return embedding;
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        
        double dot = 0;
        double normA = 0;
        double normB = 0;
        
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {}
}
