package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.KnowledgeReclassificationTaskDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 启动并查询知识点历史错误重分类任务。 */
@Service
@RequiredArgsConstructor
public class KnowledgeReclassificationService {

    private final KnowledgeReclassificationTaskRegistry registry;
    private final KnowledgeReclassificationWorker worker;

    public KnowledgeReclassificationTaskDTO start(Long classId) {
        KnowledgeReclassificationTaskDTO task = registry.create(classId);
        if ("PENDING".equals(task.getStatus())) {
            worker.execute(task.getTaskId(), classId);
        }
        return task;
    }

    public KnowledgeReclassificationTaskDTO get(String taskId) {
        return registry.get(taskId);
    }
}
