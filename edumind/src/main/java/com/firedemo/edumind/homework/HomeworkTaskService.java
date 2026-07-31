package com.firedemo.edumind.homework;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeworkTaskService {

    private final HomeworkTaskMapper taskMapper;
    public HomeworkTask getById(Long id) {
        return taskMapper.selectById(id);
    }
    public void create(HomeworkTask task) {
        taskMapper.insert(task);
    }
    public void update(HomeworkTask task) {
        taskMapper.updateById(task);
    }
    public void delete(Long id) {
        taskMapper.deleteById(id);
    }
    public List<HomeworkTask> listByClassId(Long classId) {
        return taskMapper.selectByClassId(classId);
    }
    public List<HomeworkTask> listActiveWithDeadline() {
        return taskMapper.selectActiveWithDeadline();
    }
}
