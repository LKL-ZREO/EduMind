package com.firedemo.demo.rag;

import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.Entity.DocumentChunkEntity;
import com.firedemo.demo.mapper.DocumentChunkMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.SQL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


/**
 * 向量存储服务 - 使用 PostgreSQL + pgvector 进行高效向量检索
 */
@Slf4j
@Service
public class VectorStoreService {

    @Autowired
    private DocumentChunkMapper documentChunkMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private boolean pgvectorEnabled = false;

    @PostConstruct
    public void init() {
        try {
            // 检查是否支持 pgvector
            jdbcTemplate.queryForObject("SELECT 1 FROM pg_extension WHERE extname = 'vector'", Integer.class);
            pgvectorEnabled = true;
            log.info("VectorStoreService initialized with PostgreSQL + pgvector");
        } catch (Exception e) {
            pgvectorEnabled = false;
            log.warn("pgvector extension not available, falling back to full scan mode");
        }
    }

    /**
     * 保存文档块
     */
    public void saveChunks(String documentId, List<DocumentChunk> chunks) {
        for (DocumentChunk chunk : chunks) {
            chunk.setDocumentId(documentId);
            
            if (pgvectorEnabled) {
                // 使用原生 SQL 插入，支持 vector 类型
                insertWithVector(chunk);
            } else {
                // 回退到普通插入
                documentChunkMapper.insert(toEntity(chunk));
            }
        }
        log.info("Saved {} chunks for document {}", chunks.size(), documentId);
    }

    /**
     * 使用原生 SQL 插入，支持 pgvector
     */
    private void insertWithVector(DocumentChunk chunk) {
        String sql = """
            INSERT INTO document_chunk 
            (id, doc_id, doc_name, chunk_index, sub_index, content, token_count, char_count, 
             embedding, embedding_vec, prev_summary, next_summary, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?, ?, NOW())
            """;
        
        String embeddingStr = encodeEmbedding(chunk.getEmbedding());
        String vectorStr = vectorToString(chunk.getEmbedding());
        
        jdbcTemplate.update(sql,
            chunk.getId(),
            chunk.getDocumentId(),
            chunk.getDocumentName(),
            chunk.getSectionIndex(),
            chunk.getSubIndex(),
            chunk.getContent(),
            chunk.getTokenCount(),
            chunk.getCharCount(),
            embeddingStr,
            vectorStr,
            chunk.getPrevSummary(),
            chunk.getNextSummary()
        );
    }

    /**
     * 相似度搜索 - 使用 pgvector 索引
     */
    public List<DocumentChunk> similaritySearch(float[] queryEmbedding, int topK) {
        if (!pgvectorEnabled) {
            // 回退到全表扫描
            return similaritySearchFallback(queryEmbedding, topK);
        }

        String vectorStr = vectorToString(queryEmbedding);
        String sql = """
            SELECT id, doc_id, doc_name, chunk_index, sub_index, content, 
                   token_count, char_count, embedding, prev_summary, next_summary, created_at
            FROM document_chunk
            ORDER BY embedding_vec <=> ?::vector
            LIMIT ?
            """;
        
        try {
            return jdbcTemplate.query(sql, new DocumentChunkRowMapper(), vectorStr, topK);
        } catch (Exception e) {
            log.error("pgvector search failed, falling back", e);
            return similaritySearchFallback(queryEmbedding, topK);
        }
    }

    /**
     * 回退方案：全表扫描（兼容模式）
     */
    private List<DocumentChunk> similaritySearchFallback(float[] queryEmbedding, int topK) {
        log.warn("Using fallback similarity search (full scan)");
        
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
            .filter(java.util.Objects::nonNull)
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(topK)
            .map(sc -> sc.chunk)
            .collect(Collectors.toList());
    }

    /**
     * 根据文档ID获取所有chunk
     */
    public List<DocumentChunk> getChunksByDocument(String documentId) {
        String sql = "SELECT * FROM document_chunk WHERE doc_id = ?";
        return jdbcTemplate.query(sql, new DocumentChunkRowMapper(), documentId);
    }

    /**
     * 根据ID获取chunk
     */
    public DocumentChunk getChunkById(String chunkId) {
        String sql = "SELECT * FROM document_chunk WHERE id = ?";
        List<DocumentChunk> results = jdbcTemplate.query(sql, new DocumentChunkRowMapper(), chunkId);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 删除文档的所有chunk
     */
    public void deleteDocument(String documentId) {
        String sql = "DELETE FROM document_chunk WHERE doc_id = ?";
        int count = jdbcTemplate.update(sql, documentId);
        log.info("Deleted {} chunks for document {}", count, documentId);
    }

    /**
     * 检查今天是否已存在该前缀的文档
     */
    public boolean existsToday(String docIdPrefix) {
        String today = java.time.LocalDate.now().toString();
        String pattern = docIdPrefix + "_" + today + "%";
        
        String sql = "SELECT COUNT(*) FROM document_chunk WHERE doc_id LIKE ? LIMIT 1";
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, pattern);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Failed to check exists today, assuming not exists", e);
            return false;
        }
    }

    /**
     * 将 float[] 转成 pgvector 字符串格式 [1.0,2.0,3.0,...]
     */
    private String vectorToString(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 编码嵌入向量（逗号分隔，兼容旧数据）
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
        if (str == null || str.isEmpty()) return null;
        String[] parts = str.split(",");
        float[] embedding = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            embedding[i] = Float.parseFloat(parts[i].trim());
        }
        return embedding;
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
     * DocumentChunk 转 Entity（兼容模式）
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
     * 计算余弦相似度（回退模式用）
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

    /**
     * JDBC RowMapper
     */
    private static class DocumentChunkRowMapper implements RowMapper<DocumentChunk> {
        @Override
        public DocumentChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(rs.getString("id"));
            chunk.setDocumentId(rs.getString("doc_id"));
            chunk.setDocumentName(rs.getString("doc_name"));
            chunk.setSectionIndex(rs.getInt("chunk_index"));
            chunk.setSubIndex(rs.getInt("sub_index"));
            chunk.setContent(rs.getString("content"));
            chunk.setTokenCount(rs.getInt("token_count"));
            chunk.setCharCount(rs.getInt("char_count"));
            chunk.setPrevSummary(rs.getString("prev_summary"));
            chunk.setNextSummary(rs.getString("next_summary"));
            
            String embeddingStr = rs.getString("embedding");
            if (embeddingStr != null) {
                chunk.setEmbedding(decodeEmbeddingStatic(embeddingStr));
            }
            
            return chunk;
        }
        
        private static float[] decodeEmbeddingStatic(String str) {
            if (str == null || str.isEmpty()) return null;
            String[] parts = str.split(",");
            float[] embedding = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                embedding[i] = Float.parseFloat(parts[i].trim());
            }
            return embedding;
        }
    }

    private record ScoredChunk(DocumentChunk chunk, double score) {}
}
