package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.TimelineDTO;

/**
 * 教学进度时间线服务 — 聚合课堂/作业/预习任务，按时间排序展示。
 */
public interface TimelineService {

    /**
     * 获取班级最近教学时间线
     *
     * @param classId 班级ID
     * @param limit   最多返回条数
     */
    TimelineDTO getTimeline(Long classId, int limit);
}
