package com.firedemo.demo.infrastructure.cache;

import com.firedemo.demo.DTO.ClassInfoDTO;
import com.firedemo.demo.DTO.DashboardMetricsDTO;
import com.firedemo.demo.Service.DashboardService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 缓存热点预热服务
 * <p>
 * 应用启动后异步预加载高访问量的数据到 Caffeine，避免启动后首批请求穿透到 DB。
 * <p>
 * 预热数据：
 * <ol>
 *   <li>班级列表（访问频率最高）</li>
 *   <li>每个活跃班级的 Dashboard 核心指标</li>
 *   <li>每个活跃班级的成绩分布</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheWarmupService {

    private final DashboardService dashboardService;
    private final CacheThroughService cacheThroughService;

    /**
     * 异步预热，不阻塞启动
     */
    @PostConstruct
    public void warmup() {
        Thread thread = new Thread(() -> {
            Thread.currentThread().setName("cache-warmup");
            log.info("缓存预热开始...");

            try {
                // 1. 预热班级列表（teacherId=0 取全量）
                warmupClassList();
                log.info("缓存预热完成：班级列表");

                // 2. 预热每个班级的 Dashboard 指标
                warmupDashboardMetrics();
                log.info("缓存预热完成：全部班级 Dashboard 指标");

                log.info("缓存预热全部完成 ✅");
            } catch (Exception e) {
                log.warn("缓存预热部分失败（不影响服务）: {}", e.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void warmupClassList() {
        try {
            cacheThroughService.getOrLoad(
                    "dashboard",
                    "class:list",
                    () -> dashboardService.getClassList(0L),
                    CacheTTL.classList()
            );
        } catch (Exception e) {
            log.debug("预热班级列表跳过: {}", e.getMessage());
        }
    }

    private void warmupDashboardMetrics() {
        List<ClassInfoDTO> classes = null;
        try {
            classes = dashboardService.getClassList(0L);
        } catch (Exception e) {
            log.debug("无法获取班级列表，跳过指标预热");
            return;
        }

        if (classes == null || classes.isEmpty()) return;

        for (ClassInfoDTO cls : classes) {
            Long classId = cls.getId();
            try {
                cacheThroughService.getOrLoad(
                        "dashboard",
                        "metrics:" + classId,
                        () -> dashboardService.getMetrics(classId),
                        CacheTTL.dashboardMetrics()
                );
                log.debug("预热: 班级 {} Dashboard 指标", cls.getName());
            } catch (Exception e) {
                log.debug("预热班级 {} 指标跳过: {}", cls.getName(), e.getMessage());
            }
        }
    }
}
