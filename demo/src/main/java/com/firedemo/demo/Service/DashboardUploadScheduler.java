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
            // 仪表盘 RAG 上传已禁用，数据应通过 MCP 工具查询
            log.info("仪表盘 RAG 上传已禁用，跳过: classId={}, className={}", cls.getId(), cls.getName());
            skipped++;
        }

        log.info("定时上传完成: 成功={}, 跳过={}, 失败={}", success, skipped, failed);
    }

    /**
     * 仪表盘 RAG 上传已禁用，保留方法签名以兼容编译。
     */
    @Deprecated
    private DashboardUploadDTO buildUploadData(ClassInfo cls) {
        return new DashboardUploadDTO();
    }
}
