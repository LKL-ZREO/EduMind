package com.firedemo.demo.Service.ServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Service.ClassService;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.ClassStudentMapper;
import com.firedemo.demo.mapper.StudentQqBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClassServiceImpl implements ClassService {

    private final ClassInfoMapper classInfoMapper;
    private final ClassStudentMapper classStudentMapper;
    private final StudentQqBindingMapper studentQqBindingMapper;

    // ========== 班级信息 ==========

    @Override
    public List<ClassInfo> listAll() {
        return classInfoMapper.selectList(null);
    }

    @Override
    public ClassInfo getClassById(Long id) {
        return classInfoMapper.selectById(id);
    }

    @Override
    public ClassInfo getClassByName(String name) {
        return classInfoMapper.selectOne(
                new LambdaQueryWrapper<ClassInfo>().eq(ClassInfo::getName, name));
    }

    @Override
    public String getQqGroupId(Long classId) {
        return classInfoMapper.selectQqGroupIdById(classId);
    }
//
//    @Override
//    public List<Long> listAllClassIds() {
//        return classInfoMapper.selectAllIds();
//    }
//
//    @Override
//    public List<ClassInfo> listByTeacherId(Long teacherId) {
//        return classInfoMapper.selectByTeacherId(teacherId);
//    }

    // ========== 班级学生 ==========

    @Override
    public Integer countStudentsByClassId(Long classId) {
        return classStudentMapper.countByClassId(classId);
    }

    @Override
    public Integer countSubmittedByTaskId(Long classId, Long taskId) {
        return classStudentMapper.countSubmittedByTaskId(classId, taskId);
    }

    @Override
    public List<Map<String, Object>> listUnsubmittedByTaskId(Long classId, Long taskId) {
        return classStudentMapper.selectUnsubmittedByTaskId(classId, taskId);
    }

    // ========== QQ绑定 ==========

    @Override
    public String getQqByStudentId(String studentId) {
        return studentQqBindingMapper.selectQqByStudentId(studentId);
    }

    @Override
    public void bindQq(String studentId, String qqNumber, String studentName) {
        studentQqBindingMapper.insertOrUpdate(studentId, qqNumber, studentName);
    }
}
