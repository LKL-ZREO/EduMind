package com.firedemo.edumind.live.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.firedemo.edumind.live.LiveJoinDTO;
import com.firedemo.edumind.classroom.ClassInfo;
import com.firedemo.edumind.classroom.ClassStudent;
import com.firedemo.edumind.live.ClassroomSession;
import com.firedemo.edumind.shared.exception.BusinessException;
import com.firedemo.edumind.live.security.LiveSessionTokenService;
import com.firedemo.edumind.classroom.ClassInfoMapper;
import com.firedemo.edumind.classroom.ClassStudentMapper;
import com.firedemo.edumind.live.ClassroomSessionMapper;
import com.firedemo.edumind.auth.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveSessionServiceJoinTest {

    private ClassroomSessionMapper sessionMapper;
    private ClassInfoMapper classInfoMapper;
    private ClassStudentMapper classStudentMapper;
    private LiveSessionTokenService classroomTokens;
    private LiveSessionService service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(ClassroomSessionMapper.class);
        classInfoMapper = mock(ClassInfoMapper.class);
        classStudentMapper = mock(ClassStudentMapper.class);
        classroomTokens = mock(LiveSessionTokenService.class);
        service = new LiveSessionService(
                sessionMapper,
                classInfoMapper,
                classStudentMapper,
                mock(InteractionService.class),
                mock(UserMapper.class),
                classroomTokens,
                mock(LiveNotificationService.class),
                mock(HandRaiseService.class),
                mock(StudentPresenceService.class),
                mock(SimpMessagingTemplate.class));
    }

    @Test
    void usesCanonicalRosterNameInsteadOfClientSuppliedName() {
        arrangeActiveClassroom();
        ClassStudent rosterStudent = new ClassStudent();
        rosterStudent.setClassId(10L);
        rosterStudent.setStudentId("S001");
        rosterStudent.setStudentName("张三");
        when(classStudentMapper.countManagedByClassId(10L)).thenReturn(30);
        when(classStudentMapper.selectOne(any(Wrapper.class))).thenReturn(rosterStudent);
        when(classroomTokens.issue("S001", "张三", 99L)).thenReturn("classroom-token");

        var result = service.joinSession(join(" a3f8k2 ", " S001 ", "错误姓名"));

        assertThat(result.getStudentId()).isEqualTo("S001");
        assertThat(result.getStudentName()).isEqualTo("张三");
        assertThat(result.getToken()).isEqualTo("classroom-token");
        verify(classStudentMapper, never()).insertIgnore(any(), any(), any(), any());
    }

    @Test
    void rejectsUnknownStudentWhenRosterExists() {
        arrangeActiveClassroom();
        when(classStudentMapper.countManagedByClassId(10L)).thenReturn(30);
        when(classStudentMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.joinSession(join("A3F8K2", "S999", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("学号不在班级名单中，请联系老师添加");
        verify(classroomTokens, never()).issue(any(), any(), any());
    }

    @Test
    void allowsSelfRegistrationWhileManagedRosterIsEmpty() {
        arrangeActiveClassroom();
        when(classStudentMapper.countManagedByClassId(10L)).thenReturn(0);
        when(classStudentMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(classroomTokens.issue("S001", "张三", 99L)).thenReturn("classroom-token");

        var result = service.joinSession(join("A3F8K2", "S001", " 张三 "));

        assertThat(result.getStudentName()).isEqualTo("张三");
        verify(classStudentMapper).insertIgnore(10L, "S001", "张三", "live");
    }

    private void arrangeActiveClassroom() {
        ClassroomSession session = ClassroomSession.builder()
                .id(99L)
                .classId(10L)
                .sessionCode("A3F8K2")
                .title("高数课堂")
                .status("ACTIVE")
                .build();
        ClassInfo classInfo = new ClassInfo();
        classInfo.setId(10L);
        classInfo.setName("高数一班");
        when(sessionMapper.findByCode("A3F8K2")).thenReturn(session);
        when(classInfoMapper.selectById(10L)).thenReturn(classInfo);
    }

    private LiveJoinDTO join(String code, String studentId, String studentName) {
        LiveJoinDTO dto = new LiveJoinDTO();
        dto.setCode(code);
        dto.setStudentId(studentId);
        dto.setStudentName(studentName);
        return dto;
    }
}
