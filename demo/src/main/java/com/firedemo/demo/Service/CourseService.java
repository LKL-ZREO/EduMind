package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.Course;

import java.util.List;
import java.util.Map;

/**
 * 课程服务
 */
public interface CourseService {

    /** 根据ID查课程 */
    Course getById(Long courseId);

    /** 根据班级ID查所属课程 */
    Course getByClassId(Long classId);

    /** 查教师的所有课程 */
    List<Course> listByTeacherId(Long teacherId);

    /** 创建课程 */
    Course create(Long teacherId, String name, String systemPrompt, String knowledgeScope);

    /** 更新课程 */
    void update(Long courseId, Long teacherId, String name, String systemPrompt, String knowledgeScope);

    /** 删除课程 */
    void delete(Long courseId, Long teacherId);

    /** 更新 System Prompt */
    void updatePrompt(Long courseId, String systemPrompt, String knowledgeScope);

    /** 获取所有预设模板 */
    Map<String, PresetTemplate> getPresets();

    /** 获取指定预设模板 */
    PresetTemplate getPreset(String key);

    record PresetTemplate(String key, String name, String prompt) {}
}
