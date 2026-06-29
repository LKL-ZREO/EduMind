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
import java.util.*;
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
     * 保存文档块（兼容旧调用，不传用户信息）
     */
    public void saveChunks(String documentId, List<DocumentChunk> chunks) {
        saveChunks(documentId, chunks, null, null);
    }

    /**
     * 保存文档块（带用户隔离信息）
     *
     * @param documentId 文档ID
     * @param chunks     文档块列表
     * @param userId     上传者ID
     * @param kbId       共享知识库ID，NULL=私人文档
     */
    public void saveChunks(String documentId, List<DocumentChunk> chunks, Long userId, Long kbId) {
        for (DocumentChunk chunk : chunks) {
            chunk.setDocumentId(documentId);
            chunk.setUserId(userId);
            chunk.setKbId(kbId);

            if (pgvectorEnabled) {
                insertWithVector(chunk);
            } else {
                // 回退到普通插入（需同步更新 entity）
                DocumentChunkEntity entity = toEntity(chunk);
                entity.setUserId(userId);
                entity.setKbId(kbId);
                documentChunkMapper.insert(entity);
            }
        }
        log.info("Saved {} chunks for document {} (userId={}, kbId={})", chunks.size(), documentId, userId, kbId);
    }

    /**
     * 使用原生 SQL 插入，支持 pgvector
     */
    private void insertWithVector(DocumentChunk chunk) {
        String sql = """
            INSERT INTO document_chunk 
            (id, doc_id, doc_name, chunk_index, sub_index, content, token_count, char_count, 
             embedding, embedding_vec, prev_summary, next_summary, user_id, kb_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?, ?, ?, ?, NOW())
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
            chunk.getNextSummary(),
            chunk.getUserId(),
            chunk.getKbId()
        );
    }

    /**
     * 相似度搜索 - 按用户权限过滤
     *
     * @param queryEmbedding   查询向量
     * @param topK             返回条数
     * @param userId           当前用户ID，NULL=不过滤（兼容模式）
     * @param accessibleKbIds  用户可访问的共享知识库ID集合，NULL/空=不查共享库
     */
    public List<DocumentChunk> similaritySearch(float[] queryEmbedding, int topK,
                                                 Long userId, Set<Long> accessibleKbIds) {
        if (!pgvectorEnabled) {
            return similaritySearchFallback(queryEmbedding, topK, userId, accessibleKbIds);
        }

        String vectorStr = vectorToString(queryEmbedding);
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, doc_id, doc_name, chunk_index, sub_index, content, ")
           .append("token_count, char_count, embedding, prev_summary, next_summary, created_at, ")
           .append("user_id, kb_id ")
           .append("FROM document_chunk ");
        
        List<Object> params = new ArrayList<>();
        
        // 构建权限过滤
        String whereClause = buildAccessWhereClause(userId, accessibleKbIds, params);
        if (!whereClause.isEmpty()) {
            sql.append("WHERE ").append(whereClause).append(" ");
        }
        
        sql.append("ORDER BY embedding_vec <=> ?::vector LIMIT ?");
        params.add(vectorStr);
        params.add(topK);
        
        try {
            return jdbcTemplate.query(sql.toString(), new DocumentChunkRowMapper(), params.toArray());
        } catch (Exception e) {
            log.error("pgvector search failed, falling back", e);
            return similaritySearchFallback(queryEmbedding, topK, userId, accessibleKbIds);
        }
    }

    /**
     * 回退方案：全表扫描（带权限过滤）
     */
    private List<DocumentChunk> similaritySearchFallback(float[] queryEmbedding, int topK,
                                                          Long userId, Set<Long> accessibleKbIds) {
        log.warn("Using fallback similarity search (full scan)");
        
        List<DocumentChunkEntity> allEntities = documentChunkMapper.selectList(null);
        
        if (allEntities.isEmpty()) {
            return Collections.emptyList();
        }
        
        return allEntities.stream()
            .map(entity -> {
                DocumentChunk chunk = fromEntity(entity);
                if (chunk.getEmbedding() != null && hasAccess(chunk, userId, accessibleKbIds)) {
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
    private static float[] decodeEmbedding(String str) {
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
     * 构建 WHERE 子句实现知识库权限隔离
     * <p>规则：
     * <ul>
     *   <li>userId == null → 不过滤（兼容模式）</li>
     *   <li>userId != null && accessibleKbIds 为空 → 仅查私人文档 (user_id = ? AND kb_id IS NULL)</li>
     *   <li>userId != null && accessibleKbIds 非空 → 私人文档 + 指定共享库</li>
     * </ul>
     *
     * @return WHERE 子句字符串（不含 WHERE 关键字），无过滤时返回空串
     */
    private String buildAccessWhereClause(Long userId, Set<Long> accessibleKbIds, List<Object> params) {
        if (userId == null) {
            return "";
        }

        // 私人文档：user_id = ? AND kb_id IS NULL
        StringBuilder clause = new StringBuilder();
        clause.append("(user_id = ? AND kb_id IS NULL)");
        params.add(userId);

        // 共享知识库：kb_id IN (...)
        if (accessibleKbIds != null && !accessibleKbIds.isEmpty()) {
            clause.append(" OR kb_id IN (");
            int idx = 0;
            for (Long kbId : accessibleKbIds) {
                if (idx++ > 0) clause.append(", ");
                clause.append("?");
                params.add(kbId);
            }
            clause.append(")");
        }

        return clause.toString();
    }

    /**
     * 检查 chunk 是否对当前用户可见（回退模式用）
     */
    private boolean hasAccess(DocumentChunk chunk, Long userId, Set<Long> accessibleKbIds) {
        if (userId == null) {
            return true;
        }
        // 私人文档：上传者本人
        if (chunk.getKbId() == null) {
            return userId.equals(chunk.getUserId());
        }
        // 共享知识库：用户在成员列表中
        return accessibleKbIds != null && accessibleKbIds.contains(chunk.getKbId());
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
            
            // userId, kbId 可能为 NULL
            Long userId = rs.getLong("user_id");
            chunk.setUserId(rs.wasNull() ? null : userId);
            Long kbId = rs.getLong("kb_id");
            chunk.setKbId(rs.wasNull() ? null : kbId);

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

    /**
     * 关键词检索 —— 按用户权限过滤
     * <p>从查询中提取关键词，通过 ILIKE 匹配 chunk 内容，按命中数排序。
     * 中文场景下 pg_trgm/tsvector 分词效果不佳，ILIKE 是最务实的选择。
     */
    public List<ScoredChunk> keywordSearch(String query, int topK,
                                            Long userId, Set<Long> accessibleKbIds) {
        Set<String> keywords = extractKeywords(query);
        if (keywords.isEmpty()) {
            return Collections.emptyList();
        }

        // 动态构建 SQL：每个关键词一个 CASE WHEN，求和作为 keyword_score
        StringBuilder sql = new StringBuilder();
        sql.append("""
            SELECT id, doc_id, doc_name, chunk_index, sub_index, content,
                   token_count, char_count, embedding, prev_summary, next_summary, created_at,
                   user_id, kb_id,
                   (""");

        List<String> kwList = new ArrayList<>(keywords);
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < kwList.size(); i++) {
            if (i > 0) sql.append(" + ");
            sql.append("CASE WHEN content ILIKE ? THEN 1 ELSE 0 END");
            params.add("%" + kwList.get(i) + "%");
        }

        sql.append("""
            ) AS keyword_score
            FROM document_chunk
            WHERE (""");

        for (int i = 0; i < kwList.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("content ILIKE ?");
            params.add("%" + kwList.get(i) + "%");
        }

        sql.append(")");

        // 权限过滤
        String accessClause = buildAccessWhereClause(userId, accessibleKbIds, params);
        if (!accessClause.isEmpty()) {
            sql.append(" AND (").append(accessClause).append(")");
        }

        sql.append("""
           \sORDER BY keyword_score DESC
            LIMIT ?
            """);
        params.add(topK);

        try {
            return jdbcTemplate.query(sql.toString(), new ScoredChunkRowMapper(), params.toArray());
        } catch (Exception e) {
            log.error("Keyword search failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * 提取关键词（中文按字切 2-gram + 英文按空格分词）
     */
    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return keywords;
        }

        String lower = text.toLowerCase();

        // 英文/数字分词
        String[] tokens = lower.replaceAll("[^\\u4e00-\\u9fa5a-z0-9]", " ")
                .split("\\s+");
        for (String token : tokens) {
            if (token.length() >= 2) {
                keywords.add(token);
            }
        }

        // 中文 2-gram
        String chinese = lower.replaceAll("[^\\u4e00-\\u9fa5]", "");
        for (int i = 0; i < chinese.length() - 1; i++) {
            keywords.add(chinese.substring(i, i + 2));
        }

        return keywords;
    }

    /**
     * RRF 用打分 chunk
     */
    public record ScoredChunk(DocumentChunk chunk, double score) {}

    /**
     * 带 keyword_score 列的 RowMapper
     */
    private static class ScoredChunkRowMapper implements RowMapper<ScoredChunk> {
        @Override
        public ScoredChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
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

            // userId, kbId 可能为 NULL
            Long userId = rs.getLong("user_id");
            chunk.setUserId(rs.wasNull() ? null : userId);
            Long kbId = rs.getLong("kb_id");
            chunk.setKbId(rs.wasNull() ? null : kbId);

            String embeddingStr = rs.getString("embedding");
            if (embeddingStr != null) {
                chunk.setEmbedding(decodeEmbedding(embeddingStr));
            }

            double score = rs.getDouble("keyword_score");
            return new ScoredChunk(chunk, score);
        }
    }
}
