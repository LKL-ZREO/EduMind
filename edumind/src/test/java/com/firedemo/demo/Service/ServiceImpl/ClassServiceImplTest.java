package com.firedemo.demo.Service.ServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.firedemo.demo.DTO.CreateClassDTO;
import com.firedemo.demo.DTO.ImportStudentsDTO;
import com.firedemo.demo.DTO.UpdateClassDTO;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.ClassStudentMapper;
import com.firedemo.demo.mapper.CourseMapper;
import com.firedemo.demo.mapper.StudentQqBindingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ClassServiceImpl 纯单元测试 — Mock 所有 Mapper 和外部依赖。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClassServiceImpl — 班级服务")
class ClassServiceImplTest {

    @Mock
    private ClassInfoMapper classInfoMapper;
    @Mock
    private ClassStudentMapper classStudentMapper;
    @Mock
    private StudentQqBindingMapper studentQqBindingMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private RBloomFilter<String> classIdBloomFilter;

    @InjectMocks
    private ClassServiceImpl classService;

    // ==================== 创建班级 ====================

    @Nested
    @DisplayName("createClass — 创建班级")
    class CreateClass {

        @Test
        @DisplayName("正常创建班级 → 返回 ClassInfo 并含邀请码")
        void shouldCreateClassWithInviteCode() {
            CreateClassDTO dto = new CreateClassDTO();
            dto.setName("计算机科学一班");
            dto.setDescription("2025 级");
            dto.setCourseGroup("计算机科学");

            when(classInfoMapper.selectCount(any())).thenReturn(0L); // 邀请码不冲突
            when(classInfoMapper.insert(any(ClassInfo.class))).thenReturn(1);

            ClassInfo result = classService.createClass(100L, dto);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("计算机科学一班");
            assertThat(result.getTeacherId()).isEqualTo(100L);
            assertThat(result.getInviteCode()).isNotBlank();
            assertThat(result.getInviteCode()).hasSize(6);
            assertThat(result.getStatus()).isEqualTo("ACTIVE");

            verify(classIdBloomFilter).add(anyString());
        }

        @Test
        @DisplayName("创建班级时 description 为 null → 设为空字符串")
        void shouldDefaultNullDescriptionToEmpty() {
            CreateClassDTO dto = new CreateClassDTO();
            dto.setName("测试班");
            dto.setDescription(null);

            when(classInfoMapper.selectCount(any())).thenReturn(0L);
            when(classInfoMapper.insert(any(ClassInfo.class))).thenReturn(1);

            ClassInfo result = classService.createClass(100L, dto);

            assertThat(result.getDescription()).isEqualTo("");
        }
    }

    // ==================== 查询班级 ====================

    @Nested
    @DisplayName("getClassById — 按 ID 查询")
    class GetClassById {

        @Test
        @DisplayName("存在则返回 ClassInfo")
        void shouldReturnClassWhenFound() {
            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setName("测试班");
            ci.setTeacherId(100L);
            when(classInfoMapper.selectById(1L)).thenReturn(ci);

            ClassInfo result = classService.getClassById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("测试班");
        }

        @Test
        @DisplayName("不存在 → 抛出 BusinessException")
        void shouldThrowWhenNotFound() {
            when(classInfoMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> classService.getClassById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("数据不存在");
        }
    }

    // ==================== 删除班级 ====================

    @Nested
    @DisplayName("deleteClass — 删除班级")
    class DeleteClass {

        @Test
        @DisplayName("空班 → 删除成功")
        void shouldDeleteEmptyClass() {
            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setName("空班");
            ci.setTeacherId(100L);

            when(classInfoMapper.selectById(1L)).thenReturn(ci);
            when(classStudentMapper.countByClassId(1L)).thenReturn(0); // 无学生
            when(classInfoMapper.deleteById(1L)).thenReturn(1);

            assertThatCode(() -> classService.deleteClass(1L, 100L))
                    .doesNotThrowAnyException();
            verify(classInfoMapper).deleteById(1L);
        }

        @Test
        @DisplayName("非本人班级 → 抛出 FORBIDDEN")
        void shouldThrowWhenNotOwner() {
            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setTeacherId(200L); // 属于另一个老师

            when(classInfoMapper.selectById(1L)).thenReturn(ci);

            assertThatThrownBy(() -> classService.deleteClass(1L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权限");
            verify(classInfoMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("有学生的班级 → 抛出异常")
        void shouldThrowWhenClassHasStudents() {
            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setTeacherId(100L);

            when(classInfoMapper.selectById(1L)).thenReturn(ci);
            when(classStudentMapper.countByClassId(1L)).thenReturn(5); // 有 5 个学生

            assertThatThrownBy(() -> classService.deleteClass(1L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("5 名学生");
            verify(classInfoMapper, never()).deleteById(anyLong());
        }
    }

    // ==================== 归档 ====================

    @Nested
    @DisplayName("toggleArchive — 归档/取消归档")
    class ToggleArchive {

        @Test
        @DisplayName("非本人班级 → 抛出 FORBIDDEN")
        void shouldThrowWhenNotOwner() {
            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setTeacherId(999L);

            when(classInfoMapper.selectById(1L)).thenReturn(ci);

            assertThatThrownBy(() -> classService.toggleArchive(1L, 100L))
                    .isInstanceOf(BusinessException.class);
        }

        // NOTE: toggleArchive 成功路径依赖 LambdaUpdateWrapper（需要 MyBatis-Plus 初始化 lambda 缓存），
        // 纯单元测试中无法运行，建议通过 BaseIntegrationTest 集成测试覆盖。
    }

    // ==================== 邀请码加入 ====================

    @Nested
    @DisplayName("joinByInviteCode — 邀请码加入")
    class JoinByInviteCode {

        @Test
        @DisplayName("有效邀请码 → 加入成功返回班级名")
        void shouldJoinWithValidCode() {
            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setName("Python 入门班");
            ci.setStatus("ACTIVE");

            when(classInfoMapper.selectOne(any())).thenReturn(ci);
            when(classStudentMapper.insertIgnore(anyLong(), anyString(), anyString(), anyString())).thenReturn(1);

            String className = classService.joinByInviteCode("ABC123", "student001", "小明");

            assertThat(className).isEqualTo("Python 入门班");
            verify(classStudentMapper).insertIgnore(1L, "student001", "小明", "manual");
        }

        @Test
        @DisplayName("邀请码不存在 → 抛出异常")
        void shouldThrowWhenCodeInvalid() {
            when(classInfoMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> classService.joinByInviteCode("INVALID", "s1", "test"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("邀请码无效");
        }

        @Test
        @DisplayName("班级已归档 → 抛出异常")
        void shouldThrowWhenArchived() {
            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setStatus("ARCHIVED");

            when(classInfoMapper.selectOne(any())).thenReturn(ci);

            assertThatThrownBy(() -> classService.joinByInviteCode("ABC123", "s1", "test"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已归档");
        }
    }

    // ==================== 批量导入学生 ====================

    @Nested
    @DisplayName("importStudents — 批量导入")
    class ImportStudents {

        @Test
        @DisplayName("成功导入 → 返回导入/跳过计数")
        void shouldImportStudentsAndReturnCount() {
            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setTeacherId(100L);

            ImportStudentsDTO.StudentItem item1 = new ImportStudentsDTO.StudentItem();
            item1.setStudentId("s001");
            item1.setStudentName("张三");
            ImportStudentsDTO.StudentItem item2 = new ImportStudentsDTO.StudentItem();
            item2.setStudentId("s002");
            item2.setStudentName("李四");

            when(classInfoMapper.selectById(1L)).thenReturn(ci);
            when(classStudentMapper.insertIgnore(anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn(1, 1);

            Map<String, Integer> result = classService.importStudents(
                    1L, 100L, List.of(item1, item2));

            assertThat(result.get("imported")).isEqualTo(2);
            assertThat(result.get("skipped")).isEqualTo(0);
        }

        @Test
        @DisplayName("非本人班级 → 抛出 FORBIDDEN")
        void shouldThrowWhenNotOwner() {
            ClassInfo ci = new ClassInfo();
            ci.setId(1L);
            ci.setTeacherId(999L);

            when(classInfoMapper.selectById(1L)).thenReturn(ci);

            assertThatThrownBy(() -> classService.importStudents(1L, 100L, List.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无权限");
        }
    }
}
