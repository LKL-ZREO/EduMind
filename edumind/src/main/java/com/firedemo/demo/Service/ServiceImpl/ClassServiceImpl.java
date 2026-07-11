package com.firedemo.demo.Service.ServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.ClassStudent;
import com.firedemo.demo.Entity.Course;
import com.firedemo.demo.Service.ClassService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.ClassStudentMapper;
import com.firedemo.demo.mapper.CourseMapper;
import com.firedemo.demo.mapper.StudentQqBindingMapper;
import org.redisson.api.RBloomFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 班级 service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassServiceImpl implements ClassService {

    private final ClassInfoMapper classInfoMapper;
    private final ClassStudentMapper classStudentMapper;
    private final StudentQqBindingMapper studentQqBindingMapper;
    private final CourseMapper courseMapper;
    private final RBloomFilter<String> classIdBloomFilter;

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LEN = 6;
    private static final int MAX_RETRY = 10;
    private static final SecureRandom RAND = new SecureRandom();

    // ========== 班级信息 ==========

    @Override
    public List<ClassInfo> listAll() {
        return classInfoMapper.selectList(null);
    }

    @Override
    public ClassInfo getClassById(Long id) {
        ClassInfo ci = classInfoMapper.selectById(id);
        if (ci == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND);
        }
        return ci;
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

    @Override
    @Cacheable(value = "teacherClasses", key = "#teacherId", sync = true)
    public List<ClassGroupDTO> listGroupedByCourse(Long teacherId) {
        List<ClassInfo> classes = classInfoMapper.selectByTeacherId(teacherId);

        // 预加载课程名（courseId → name）
        Map<Long, String> courseNames = new HashMap<>();
        for (ClassInfo c : classes) {
            if (c.getCourseId() != null && !courseNames.containsKey(c.getCourseId())) {
                Course course = courseMapper.selectById(c.getCourseId());
                if (course != null) courseNames.put(c.getCourseId(), course.getName());
            }
        }

        // 分组：优先 courseGroup 字符串，其次 courseId 查出的课程名
        Map<String, List<ClassInfo>> grouped = classes.stream()
                .collect(Collectors.groupingBy(
                        c -> {
                            String cg = c.getCourseGroup();
                            if (cg != null && !cg.isEmpty()) return cg;
                            if (c.getCourseId() != null && courseNames.containsKey(c.getCourseId()))
                                return courseNames.get(c.getCourseId());
                            return "__ungrouped__";
                        },
                        LinkedHashMap::new,
                        Collectors.toList()));

        // 批量查询学生数（一次查询替代 N+1）
        List<Long> classIds = classes.stream().map(ClassInfo::getId).collect(Collectors.toList());
        Map<Long, Integer> countMap = classIds.isEmpty()
                ? Map.of()
                : classStudentMapper.countByClassIds(classIds).stream()
                        .collect(Collectors.toMap(
                                row -> ((Number) row.get("class_id")).longValue(),
                                row -> ((Number) row.get("cnt")).intValue()));

        // 按课程名排序，未分组放最后
        return grouped.entrySet().stream()
                .sorted((a, b) -> {
                    if ("__ungrouped__".equals(a.getKey())) return 1;
                    if ("__ungrouped__".equals(b.getKey())) return -1;
                    return a.getKey().compareTo(b.getKey());
                })
                .map(entry -> {
                    ClassGroupDTO dto = new ClassGroupDTO();
                    dto.setCourseGroup("__ungrouped__".equals(entry.getKey()) ? null : entry.getKey());
                    dto.setClasses(entry.getValue().stream()
                            .sorted((a, b) -> {
                                if (!a.getStatus().equals(b.getStatus())) {
                                    return "ACTIVE".equals(a.getStatus()) ? -1 : 1;
                                }
                                return b.getCreatedAt().compareTo(a.getCreatedAt());
                            })
                            .map(ci -> {
                                ClassGroupDTO.ClassItem item = new ClassGroupDTO.ClassItem();
                                item.setId(ci.getId());
                                item.setName(ci.getName());
                                item.setDescription(ci.getDescription());
                                item.setCourseGroup(ci.getCourseGroup());
                                item.setCourseId(ci.getCourseId());
                                item.setQqGroupId(ci.getQqGroupId());
                                item.setStudentCount(countMap.getOrDefault(ci.getId(), 0));
                                item.setInviteCode(ci.getInviteCode());
                                item.setStatus(ci.getStatus());
                                item.setCreatedAt(ci.getCreatedAt());
                                return item;
                            })
                            .collect(Collectors.toList()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "teacherClasses", key = "#teacherId")
    public ClassInfo createClass(Long teacherId, CreateClassDTO dto) {
        ClassInfo ci = new ClassInfo();
        ci.setName(dto.getName());
        ci.setTeacherId(teacherId);
        ci.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        ci.setCourseGroup(dto.getCourseGroup() != null ? dto.getCourseGroup() : "");
        ci.setCourseId(dto.getCourseId());
        ci.setQqGroupId(dto.getQqGroupId());
        ci.setInviteCode(generateInviteCode());
        ci.setStatus("ACTIVE");
        classInfoMapper.insert(ci);
        classIdBloomFilter.add(String.valueOf(ci.getId()));
        log.info("教师 {} 创建班级: {} (id={})", teacherId, ci.getName(), ci.getId());
        return ci;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "teacherClasses", key = "#teacherId")
    public void updateClass(Long classId, Long teacherId, UpdateClassDTO dto) {
        ClassInfo ci = getClassById(classId);
        if (!ci.getTeacherId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LambdaUpdateWrapper<ClassInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ClassInfo::getId, classId);
        if (dto.getName() != null) wrapper.set(ClassInfo::getName, dto.getName());
        if (dto.getCourseGroup() != null) wrapper.set(ClassInfo::getCourseGroup, dto.getCourseGroup());
        if (dto.getDescription() != null) wrapper.set(ClassInfo::getDescription, dto.getDescription());
        if (dto.getQqGroupId() != null) wrapper.set(ClassInfo::getQqGroupId, dto.getQqGroupId());
        if (dto.getCourseId() != null) wrapper.set(ClassInfo::getCourseId, dto.getCourseId());
        classInfoMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "teacherClasses", key = "#teacherId")
    public void deleteClass(Long classId, Long teacherId) {
        ClassInfo ci = getClassById(classId);
        if (!ci.getTeacherId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        int studentCount = classStudentMapper.countByClassId(classId);
        if (studentCount > 0) {
            throw new BusinessException(400, "班级下还有 " + studentCount + " 名学生，请先移除所有学生后再删除");
        }
        classInfoMapper.deleteById(classId);
        log.info("教师 {} 删除班级: {} (id={})", teacherId, ci.getName(), classId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "teacherClasses", key = "#teacherId")
    public void toggleArchive(Long classId, Long teacherId) {
        ClassInfo ci = getClassById(classId);
        if (!ci.getTeacherId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String newStatus = "ACTIVE".equals(ci.getStatus()) ? "ARCHIVED" : "ACTIVE";
        LambdaUpdateWrapper<ClassInfo> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ClassInfo::getId, classId).set(ClassInfo::getStatus, newStatus);
        classInfoMapper.update(null, wrapper);
        log.info("教师 {} {}班级: {} (id={})",
                teacherId, "ACTIVE".equals(newStatus) ? "取消归档" : "归档", ci.getName(), classId);
    }

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

    @Override
    public List<ClassStudent> listStudentsByClassId(Long classId) {
        return classStudentMapper.selectByClassId(classId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeStudent(Long classId, String studentId, Long teacherId) {
        ClassInfo ci = getClassById(classId);
        if (!ci.getTeacherId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LambdaQueryWrapper<ClassStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassStudent::getClassId, classId).eq(ClassStudent::getStudentId, studentId);
        int deleted = classStudentMapper.delete(wrapper);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND);
        }
        log.info("教师 {} 从班级 {} 移除学生 {}", teacherId, classId, studentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> importStudents(Long classId, Long teacherId, List<ImportStudentsDTO.StudentItem> items) {
        ClassInfo ci = getClassById(classId);
        if (!ci.getTeacherId().equals(teacherId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        int imported = 0;
        int skipped = 0;
        for (var item : items) {
            try {
                classStudentMapper.insertIgnore(classId, item.getStudentId(),
                        item.getStudentName(), "manual");
                imported++;
            } catch (Exception e) {
                skipped++;
                log.debug("跳过重复学生: classId={} studentId={}", classId, item.getStudentId());
            }
        }
        log.info("教师 {} 批量导入班级 {}: 成功 {} 跳过 {}", teacherId, classId, imported, skipped);
        Map<String, Integer> result = new HashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String joinByInviteCode(String inviteCode, String studentId, String studentName) {
        ClassInfo ci = classInfoMapper.selectOne(
                new LambdaQueryWrapper<ClassInfo>()
                        .eq(ClassInfo::getInviteCode, inviteCode.toUpperCase().trim()));
        if (ci == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "邀请码无效");
        }
        if ("ARCHIVED".equals(ci.getStatus())) {
            throw new BusinessException(400, "班级已归档，无法加入");
        }
        classStudentMapper.insertIgnore(ci.getId(), studentId, studentName, "manual");
        return ci.getName();
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

    // ========== 内部工具 ==========

    private String generateInviteCode() {
        for (int retry = 0; retry < MAX_RETRY; retry++) {
            StringBuilder sb = new StringBuilder(CODE_LEN);
            for (int i = 0; i < CODE_LEN; i++) {
                sb.append(CHARS.charAt(RAND.nextInt(CHARS.length())));
            }
            String code = sb.toString();
            Long exists = classInfoMapper.selectCount(
                    new LambdaQueryWrapper<ClassInfo>().eq(ClassInfo::getInviteCode, code));
            if (exists == 0) {
                return code;
            }
        }
        throw new BusinessException(500, "生成邀请码失败，请重试");
    }
}
