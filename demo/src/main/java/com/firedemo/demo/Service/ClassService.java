package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.ClassInfo;

import java.util.List;
import java.util.Map;

/**
 * 班级域 Service（班级信息 + 学生 + QQ绑定）
 */
public interface ClassService {

    // ========== 班级信息 ==========
    List<ClassInfo> listAll();

    ClassInfo getClassById(Long id);

    ClassInfo getClassByName(String name);

    String getQqGroupId(Long classId);

    List<Long> listAllClassIds();

    List<ClassInfo> listByTeacherId(Long teacherId);

    // ========== 班级学生 ==========
    Integer countStudentsByClassId(Long classId);

    Integer countSubmittedByTaskId(Long classId, Long taskId);

    List<Map<String, Object>> listUnsubmittedByTaskId(Long classId, Long taskId);

    // ========== QQ绑定 ==========
    String getQqByStudentId(String studentId);

    void bindQq(String studentId, String qqNumber, String studentName);
}
