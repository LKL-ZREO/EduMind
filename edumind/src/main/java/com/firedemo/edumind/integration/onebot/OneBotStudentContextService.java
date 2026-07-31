package com.firedemo.edumind.integration.onebot;

import com.firedemo.edumind.classroom.ClassInfo;
import com.firedemo.edumind.classroom.ClassInfoMapper;
import com.firedemo.edumind.classroom.ClassStudent;
import com.firedemo.edumind.classroom.ClassStudentMapper;
import com.firedemo.edumind.classroom.StudentQqBindingMapper;
import com.firedemo.edumind.knowledge.SharedKbMemberMapper;
import com.firedemo.edumind.teaching.StudentConfusionLog;
import com.firedemo.edumind.teaching.StudentConfusionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class OneBotStudentContextService {

    private static final Pattern CONFUSION_PATTERN =
            Pattern.compile("^(不懂|没听懂|没明白|听不懂|不明白|没理解|不理解)\\s*(.*)");

    private final StudentQqBindingMapper studentQqBindingMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassInfoMapper classInfoMapper;
    private final SharedKbMemberMapper sharedKbMemberMapper;
    private final StudentConfusionLogMapper confusionLogMapper;

    public StudentContext resolve(String qq) {
        try {
            String studentId = studentQqBindingMapper.selectStudentIdByQq(qq);
            if (studentId == null) {
                return StudentContext.anonymous();
            }
            ClassStudent student = classStudentMapper.selectByStudentId(studentId);
            if (student == null || student.getClassId() == null) {
                return new StudentContext(null, null, null, studentId);
            }
            ClassInfo classInfo = classInfoMapper.selectById(student.getClassId());
            if (classInfo == null || classInfo.getTeacherId() == null) {
                return new StudentContext(null, null, null, studentId);
            }
            Long teacherId = classInfo.getTeacherId();
            return new StudentContext(
                    teacherId,
                    sharedKbMemberMapper.selectKbIdsByUserId(teacherId),
                    classInfo.getCourseId(),
                    studentId);
        } catch (RuntimeException e) {
            log.debug("无法从 QQ 解析用户上下文，回退搜全库: qq={}", qq, e);
            return StudentContext.anonymous();
        }
    }

    public void recordConfusionIfNeeded(String qq, String message, String studentId) {
        if (message == null) {
            return;
        }
        var matcher = CONFUSION_PATTERN.matcher(message.trim());
        if (!matcher.matches()) {
            return;
        }

        String knowledgePoint = matcher.group(2).trim();
        if (knowledgePoint.isEmpty()) {
            knowledgePoint = "未指定";
        }

        Long classId = null;
        String studentName = null;
        try {
            if (studentId != null) {
                ClassStudent student = classStudentMapper.selectByStudentId(studentId);
                if (student != null) {
                    classId = student.getClassId();
                    studentName = student.getStudentName();
                }
            }
            confusionLogMapper.insert(StudentConfusionLog.builder()
                    .qqNumber(qq)
                    .studentName(studentName)
                    .classId(classId)
                    .question(message)
                    .knowledgePoint(knowledgePoint)
                    .build());
            log.info("记录不懂标记: qq={}, kp={}, classId={}", qq, knowledgePoint, classId);
        } catch (RuntimeException e) {
            log.warn("记录不懂标记失败: qq={}", qq, e);
        }
    }

    public record StudentContext(
            Long userId,
            Set<Long> accessibleKbIds,
            Long courseId,
            String studentId) {

        static StudentContext anonymous() {
            return new StudentContext(null, null, null, null);
        }
    }
}
