package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.PreLessonDTO;

/**
 * 备课学情服务 — 聚合班级多维度数据，为教师备课提供数据支撑和 AI 建议。
 */
public interface PreLessonService {

    /**
     * 获取班级备课学情全貌
     *
     * @param classId 班级ID
     * @return 聚合后的备课仪表盘数据（含作业、互动、AI 建议）
     */
    PreLessonDTO getPreLessonOverview(Long classId);

    /**
     * 异步获取 AI 备课建议（独立接口，避免主接口超时）
     *
     * @param classId 班级ID
     * @return AI 生成的备课建议文本
     */
    String getAiSuggestion(Long classId);
}
