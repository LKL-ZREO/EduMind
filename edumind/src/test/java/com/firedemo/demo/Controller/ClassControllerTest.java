package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.CreateClassDTO;
import com.firedemo.demo.DTO.JoinByInviteDTO;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.ClassStudent;
import com.firedemo.demo.Service.ClassService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.common.result.Result;
import com.firedemo.demo.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ClassController 纯单元测试 — 手动构造 Controller，Mock 依赖。
 */
@DisplayName("ClassController — 班级控制器")
class ClassControllerTest {

    private ClassService classService;
    private JwtUtil jwtUtil;
    private ClassController controller;

    @BeforeEach
    void setUp() {
        classService = mock(ClassService.class);
        jwtUtil = mock(JwtUtil.class);
        controller = new ClassController(classService, jwtUtil);
    }

    // ==================== 查询班级列表 ====================

    @Nested
    @DisplayName("GET /api/teacher/classes")
    class ListClasses {

        @Test
        @DisplayName("已认证 → 返回班级列表")
        void shouldReturnClassList() {
            var request = mock(HttpServletRequest.class);
            when(jwtUtil.getUserIdFromRequest(request)).thenReturn(100L);
            when(classService.listGroupedByCourse(100L)).thenReturn(new ArrayList<>());

            var result = controller.listClasses(request);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData()).isNotNull();
        }

        @Test
        @DisplayName("未认证 → 401")
        void shouldReturn401() {
            var request = mock(HttpServletRequest.class);
            when(jwtUtil.getUserIdFromRequest(request)).thenReturn(null);

            var result = controller.listClasses(request);

            assertThat(result.getCode()).isEqualTo(401);
        }
    }

    // ==================== 创建班级 ====================

    @Nested
    @DisplayName("POST /api/teacher/classes")
    class CreateClass {

        @Test
        @DisplayName("正常创建 → 200")
        void shouldCreateClass() {
            var request = mock(HttpServletRequest.class);
            when(jwtUtil.getUserIdFromRequest(request)).thenReturn(100L);

            CreateClassDTO dto = new CreateClassDTO();
            dto.setName("计算机一班");

            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setName("计算机一班");
            ci.setInviteCode("ABC123");
            ci.setStatus("ACTIVE");
            when(classService.createClass(eq(100L), any())).thenReturn(ci);

            Result<?> result = controller.createClass(dto, request);

            assertThat(result.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("未登录 → 401")
        void shouldReturn401WhenNotLoggedIn() {
            var request = mock(HttpServletRequest.class);
            when(jwtUtil.getUserIdFromRequest(request)).thenReturn(null);
            CreateClassDTO dto = new CreateClassDTO();
            dto.setName("测试班");

            Result<?> result = controller.createClass(dto, request);

            assertThat(result.getCode()).isEqualTo(401);
            verify(classService, never()).createClass(anyLong(), any());
        }
    }

    // ==================== 查看班级详情 ====================

    @Nested
    @DisplayName("GET /api/teacher/classes/{id}")
    class GetClassDetail {

        @Test
        @DisplayName("本人班级 → 200")
        void shouldReturnDetailForOwnClass() {
            var request = mock(HttpServletRequest.class);
            when(jwtUtil.getUserIdFromRequest(request)).thenReturn(100L);

            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setName("我的班");
            ci.setTeacherId(100L);
            when(classService.getClassById(1L)).thenReturn(ci);
            when(classService.listStudentsByClassId(1L)).thenReturn(Collections.emptyList());

            Result<Map<String, Object>> result = controller.getClassDetail(1L, request);

            assertThat(result.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("他人班级 → 403")
        void shouldReturn403ForOthersClass() {
            var request = mock(HttpServletRequest.class);
            when(jwtUtil.getUserIdFromRequest(request)).thenReturn(100L);

            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setTeacherId(999L);
            when(classService.getClassById(1L)).thenReturn(ci);

            Result<Map<String, Object>> result = controller.getClassDetail(1L, request);

            assertThat(result.getCode()).isEqualTo(403);
        }
    }

    // ==================== 删除班级 ====================

    @Nested
    @DisplayName("DELETE /api/teacher/classes/{id}")
    class DeleteClass {

        @Test
        @DisplayName("删除成功 → 200")
        void shouldDeleteClass() {
            var request = mock(HttpServletRequest.class);
            when(jwtUtil.getUserIdFromRequest(request)).thenReturn(100L);
            doNothing().when(classService).deleteClass(1L, 100L);

            Result<Void> result = controller.deleteClass(1L, request);

            assertThat(result.getCode()).isEqualTo(200);
        }
    }

    // ==================== 邀请码加入（公开接口） ====================

    @Nested
    @DisplayName("POST /api/teacher/classes/join")
    class JoinByInvite {

        @Test
        @DisplayName("有效邀请码 → 200")
        void shouldJoinByValidCode() {
            JoinByInviteDTO dto = new JoinByInviteDTO();
            dto.setInviteCode("ABC123");
            dto.setStudentId("student001");
            dto.setStudentName("小明");

            when(classService.joinByInviteCode("ABC123", "student001", "小明"))
                    .thenReturn("Python 入门班");

            Result<Map<String, String>> result = controller.joinByInvite(dto);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().get("className")).isEqualTo("Python 入门班");
        }

        @Test
        @DisplayName("无效邀请码 → 抛出异常")
        void shouldThrowForInvalidCode() {
            JoinByInviteDTO dto = new JoinByInviteDTO();
            dto.setInviteCode("INVALID");
            dto.setStudentId("s1");
            dto.setStudentName("test");

            when(classService.joinByInviteCode(anyString(), anyString(), anyString()))
                    .thenThrow(new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "邀请码无效"));

            assertThatThrownBy(() -> controller.joinByInvite(dto))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
