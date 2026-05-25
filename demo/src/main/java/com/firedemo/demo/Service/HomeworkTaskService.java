package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.HomeworkTask;

import java.util.List;

/**
 * 作业任务 Service
 */
public interface HomeworkTaskService {

    HomeworkTask getById(Long id);

    void create(HomeworkTask task);

    void update(HomeworkTask task);

    void delete(Long id);

    List<HomeworkTask> listByClassId(Long classId);

    List<HomeworkTask> listActiveWithDeadline();
}
