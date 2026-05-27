package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.rag.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 仪表盘数据定时上传调度器
 * 每天凌晨自动将所有班级数据上传到 RAG 知识库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardUploadScheduler {

    private final ClassInfoMapper classInfoMapper;
    private final DashboardService dashboardService;
    private final DashboardRagService dashboardRagService;
    private final VectorStoreService vectorStoreService;

    /**
     * 每天凌晨 2:00 自动上传所有班级仪表盘数据
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Shanghai")
    public void scheduledUpload() {
        log.info("定时上传仪表盘数据开始");

        List<ClassInfo> classes = classInfoMapper.selectList(null);
        if (classes.isEmpty()) {
            log.info("无班级数据，跳过");
            return;
        }

        int success = 0;
        int skipped = 0;
        int failed = 0;

        for (ClassInfo cls : classes) {
            String docIdPrefix = "dashboard_" + cls.getId();

            // 今天已上传则跳过
            if (vectorStoreService.existsToday(docIdPrefix)) {
                log.info("今天已上传，跳过: classId={}, className={}", cls.getId(), cls.getName());
                skipped++;
                continue;
            }

            try {
                DashboardUploadDTO data = buildUploadData(cls);
                dashboardRagService.uploadDashboard(data);
                success++;
                log.info("上传成功: classId={}, className={}", cls.getId(), cls.getName());
            } catch (Exception e) {
                failed++;
                log.error("上传失败: classId={}, className={}", cls.getId(), cls.getName(), e);
            }
        }

        log.info("定时上传完成: 成功={}, 跳过={}, 失败={}", success, skipped, failed);
    }

    /**
     * 构建上传数据
     */
    private DashboardUploadDTO buildUploadData(ClassInfo cls) {
        Long classId = cls.getId();

        DashboardUploadDTO data = new DashboardUploadDTO();
        data.setClassId(classId);
        data.setClassName(cls.getName());
        data.setExportTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        try {
            data.setMetrics(dashboardService.getMetrics(classId));
        } catch (Exception e) {
            log.warn("获取核心指标失败: classId={}", classId, e);
        }

        try {
            data.setScoreDistribution(dashboardService.getScoreDistribution(classId));
        } catch (Exception e) {
            log.warn("获取成绩分布失败: classId={}", classId, e);
        }

        try {
            data.setKnowledgeMastery(dashboardService.getKnowledgeMastery(classId));
        } catch (Exception e) {
            log.warn("获取知识点掌握度失败: classId={}", classId, e);
        }

        try {
            data.setFrequentErrors(dashboardService.getFrequentErrors(classId, null));
        } catch (Exception e) {
            log.warn("获取高频错题失败: classId={}", classId, e);
        }

        try {
            data.setStudents(dashboardService.getStudentOverview(classId, "score", null));
        } catch (Exception e) {
            log.warn("获取学生概览失败: classId={}", classId, e);
        }

        return data;
    }
}
