package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.Service.HomeworkTaskService;
import com.firedemo.demo.mapper.HomeworkTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeworkTaskServiceImpl implements HomeworkTaskService {

    private final HomeworkTaskMapper taskMapper;

    @Override
    public HomeworkTask getById(Long id) {
        return taskMapper.selectById(id);
    }

    @Override
    public void create(HomeworkTask task) {
        taskMapper.insert(task);
    }

    @Override
    public void update(HomeworkTask task) {
        taskMapper.updateById(task);
    }

    @Override
    public void delete(Long id) {
        taskMapper.deleteById(id);
    }

    @Override
    public List<HomeworkTask> listByClassId(Long classId) {
        return taskMapper.selectByClassId(classId);
    }

    @Override
    public List<HomeworkTask> listActiveWithDeadline() {
        return taskMapper.selectActiveWithDeadline();
    }
}
