package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.Service.DashboardRagService;
import com.firedemo.demo.rag.EmbeddingService;
import com.firedemo.demo.rag.SmartChunkService;
import com.firedemo.demo.rag.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘数据RAG服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardRagServiceImpl implements DashboardRagService {

    private final SmartChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    @Override
    public Map<String, Object> uploadDashboard(DashboardUploadDTO data) {
        String docIdPrefix = "dashboard_" + data.getClassId();
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String docId = docIdPrefix + "_" + today + "_" + System.currentTimeMillis();
        
        log.info("Uploading dashboard data for class {}: {}", data.getClassId(), data.getClassName());
        
        // 1. 格式化数据为文本
        String content = formatDashboardContent(data);
        
        // 2. 智能切割
        SmartChunkService.ChunkConfig config = new SmartChunkService.ChunkConfig();
        config.setMaxTokens(512);
        config.setOverlapTokens(50);
        List<DocumentChunk> chunks = chunkService.chunk(content, config);
        
        // 3. 添加元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "dashboard");
        metadata.put("classId", data.getClassId());
        metadata.put("className", data.getClassName());
        metadata.put("date", today);
        metadata.put("dataVersion", "v1");
        metadata.put("exportTime", data.getExportTime());
        metadata.put("studentCount", data.getStudents() != null ? data.getStudents().size() : 0);
        
        chunks.forEach(chunk -> {
            chunk.setDocumentId(docId);
            chunk.setDocumentName(data.getClassName() + "_班级数据_" + today);
            chunk.setMetadata(metadata);
        });
        
        // 4. 生成向量并保存
        chunks.forEach(chunk -> {
            float[] embedding = embeddingService.embed(chunk.getContent());
            chunk.setEmbedding(embedding);
        });
        
        vectorStoreService.saveChunks(docId, chunks);
        
        log.info("Uploaded {} chunks for dashboard data: {}", chunks.size(), docId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("documentId", docId);
        result.put("chunkCount", chunks.size());
        result.put("classId", data.getClassId());
        result.put("className", data.getClassName());
        result.put("date", today);
        
        return result;
    }
    
    /**
     * 格式化仪表盘数据为文本
     */
    private String formatDashboardContent(DashboardUploadDTO data) {
        StringBuilder sb = new StringBuilder();
        
        // 标题
        sb.append("# ").append(data.getClassName()).append(" 班级数据报告\n\n");
        sb.append("导出时间: ").append(data.getExportTime()).append("\n\n");
        
        // 核心指标
        if (data.getMetrics() != null) {
            sb.append("## 核心指标\n\n");
            DashboardMetricsDTO m = data.getMetrics();
            sb.append("- 学生总数: ").append(m.getTotalStudents()).append("人\n");
            sb.append("- 作业总数: ").append(m.getTotalHomework()).append("份\n");
            sb.append("- 平均正确率: ").append(m.getAvgScore()).append("%\n");
            sb.append("- 需关注学生: ").append(m.getWarningStudents()).append("人\n");
            sb.append("\n");
        }
        
        // 成绩分布
        if (data.getScoreDistribution() != null && !data.getScoreDistribution().isEmpty()) {
            sb.append("## 成绩分布\n\n");
            for (ScoreDistributionDTO dist : data.getScoreDistribution()) {
                sb.append(String.format("- %s: %d人 (%.1f%%)\n", 
                    dist.getRange(), dist.getCount(), dist.getPercentage()));
            }
            sb.append("\n");
        }
        
        // 知识点掌握度
        if (data.getKnowledgeMastery() != null && !data.getKnowledgeMastery().isEmpty()) {
            sb.append("## 知识点掌握度\n\n");
            for (KnowledgeMasteryDTO km : data.getKnowledgeMastery()) {
                sb.append(String.format("- %s: %d%%\n", km.getName(), km.getMastery()));
            }
            sb.append("\n");
        }
        
        // 高频错题
        if (data.getFrequentErrors() != null && !data.getFrequentErrors().isEmpty()) {
            sb.append("## 高频错题 TOP10\n\n");
            int rank = 1;
            for (FrequentErrorDTO error : data.getFrequentErrors()) {
                sb.append(String.format("%d. %s (错误率%d%%, %d人错)\n",
                    rank++, error.getQuestion(), error.getErrorRate(), error.getErrorCount()));
            }
            sb.append("\n");
        }
        
        // 学生详情
        if (data.getStudents() != null && !data.getStudents().isEmpty()) {
            sb.append("## 学生学情详情\n\n");
            for (StudentOverviewDTO student : data.getStudents()) {
                sb.append(String.format("- %s: 平均分%d, 作业%d份, 错题%d个, 趋势%s\n",
                    student.getName(), student.getAvgScore(), student.getHomeworkCount(),
                    student.getErrorCount(), student.getTrend() > 0 ? "上升" : "下降"));
            }
        }
        
        return sb.toString();
    }
}
