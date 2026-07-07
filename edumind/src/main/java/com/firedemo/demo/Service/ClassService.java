package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.ClassStudent;

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

    /** 新增：按课程分组返回（前端班级列表页） */
    List<ClassGroupDTO> listGroupedByCourse(Long teacherId);

    /** 新增：创建班级（自动生成邀请码） */
    ClassInfo createClass(Long teacherId, CreateClassDTO dto);

    /** 新增：编辑班级（鉴权：只能改自己的） */
    void updateClass(Long classId, Long teacherId, UpdateClassDTO dto);

    /** 新增：删除班级（仅空班） */
    void deleteClass(Long classId, Long teacherId);

    /** 新增：切换归档状态 */
    void toggleArchive(Long classId, Long teacherId);

    // ========== 班级学生 ==========
    Integer countStudentsByClassId(Long classId);

    Integer countSubmittedByTaskId(Long classId, Long taskId);

    List<Map<String, Object>> listUnsubmittedByTaskId(Long classId, Long taskId);

    /** 新增：查询班级学生列表 */
    List<ClassStudent> listStudentsByClassId(Long classId);

    /** 新增：移除学生 */
    void removeStudent(Long classId, String studentId, Long teacherId);

    /** 新增：批量导入学生 */
    Map<String, Integer> importStudents(Long classId, Long teacherId, List<ImportStudentsDTO.StudentItem> items);

    /** 新增：通过邀请码加入 */
    String joinByInviteCode(String inviteCode, String studentId, String studentName);

    // ========== QQ绑定 ==========
    String getQqByStudentId(String studentId);

    void bindQq(String studentId, String qqNumber, String studentName);
}
