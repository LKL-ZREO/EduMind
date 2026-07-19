package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.PreviewTaskDTO;

import java.util.List;

/**
 * 预习任务服务 — AI 生成课前预习材料并管理生命周期。
 */
public interface PreviewTaskService {

    /**
     * AI 生成预习任务
     * @param sourceDocId 可选，关联的来源文档ID（如PPT）
     */
    PreviewTaskDTO createPreviewTask(Long teacherId, Long classId, String knowledgePoint, String topic, String sourceDocId);

    /**
     * 获取班级所有活跃预习任务
     */
    List<PreviewTaskDTO> listByClassId(Long classId);

    /**
     * 获取单个预习任务详情（学生查看）
     */
    PreviewTaskDTO getById(Long taskId);

    /**
     * 关闭预习任务
     */
    void closeTask(Long taskId, Long teacherId);
}
