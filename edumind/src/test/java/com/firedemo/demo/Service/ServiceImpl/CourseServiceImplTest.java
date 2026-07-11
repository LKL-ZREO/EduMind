package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.Entity.Course;
import com.firedemo.demo.mapper.CourseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourseServiceImpl — 课程服务")
class CourseServiceImplTest {

    @Mock private CourseMapper courseMapper;
    @Mock private RedissonClient redissonClient;

    @InjectMocks
    private CourseServiceImpl courseService;

    private Course sample;

    @BeforeEach
    void setUp() {
        sample = new Course();
        sample.setId(1L);
        sample.setTeacherId(100L);
        sample.setName("Java 程序设计");
        sample.setSystemPrompt("你是Java教师");
        sample.setKnowledgeScope("基础语法,面向对象");
    }

    /** Mock 缓存返回 — 用于 getById 测试 */
    @SuppressWarnings("unchecked")
    private RBucket<Course> mockCourseCache(Course course) {
        RBucket<Course> bucket = mock(RBucket.class);
        when(bucket.get()).thenReturn(course);
        when(redissonClient.<Course>getBucket(startsWith("course:"))).thenReturn(bucket);
        return bucket;
    }

    /** Mock 缓存返回 null — 用于 getById 未命中 */
    @SuppressWarnings("unchecked")
    private RBucket<Course> mockCourseCacheMiss() {
        RBucket<Course> bucket = mock(RBucket.class);
        when(bucket.get()).thenReturn(null);
        when(redissonClient.<Course>getBucket(startsWith("course:"))).thenReturn(bucket);
        return bucket;
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("正常创建课程 → 返回 Course")
        void shouldCreate() {
            when(courseMapper.insert(any(Course.class))).thenReturn(1);

            Course result = courseService.create(100L, "Python入门", "你是Python教师", "基础");

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Python入门");
            assertThat(result.getTeacherId()).isEqualTo(100L);
            verify(courseMapper).insert(any(Course.class));
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("缓存命中 → 不查 DB")
        void shouldHitCache() {
            RBucket<Course> bucket = mockCourseCache(sample);

            Course result = courseService.getById(1L);

            assertThat(result).isSameAs(sample);
            verify(courseMapper, never()).selectById(anyLong());
        }

        @Test
        @DisplayName("缓存未命中 → 查 DB 并写缓存")
        void shouldMissCache() {
            RBucket<Course> bucket = mockCourseCacheMiss();
            when(courseMapper.selectById(1L)).thenReturn(sample);

            Course result = courseService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Java 程序设计");
            verify(courseMapper).selectById(1L);
            verify(bucket).set(eq(sample), any());
        }

        @Test
        @DisplayName("null id → 返回 null")
        void shouldReturnNull() {
            assertThat(courseService.getById(null)).isNull();
            verifyNoInteractions(redissonClient);
            verifyNoInteractions(courseMapper);
        }
    }

    @Nested
    @DisplayName("listByTeacherId")
    class ListByTeacher {

        @Test
        @DisplayName("返回该教师的课程列表")
        void shouldReturnList() {
            when(courseMapper.selectByTeacherId(100L)).thenReturn(List.of(sample));

            var result = courseService.listByTeacherId(100L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Java 程序设计");
        }

        @Test
        @DisplayName("null teacherId → 返回空列表")
        void shouldReturnEmpty() {
            assertThat(courseService.listByTeacherId(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("正常更新 → 缓存清理")
        void shouldUpdate() {
            RBucket<Course> bucket = mock(RBucket.class);
            when(redissonClient.<Course>getBucket(anyString())).thenReturn(bucket);
            when(courseMapper.selectById(1L)).thenReturn(sample);

            courseService.update(1L, 100L, "Java 高级", null, null);

            assertThat(sample.getName()).isEqualTo("Java 高级");
            verify(courseMapper).updateById(sample);
            verify(bucket).delete();
        }

        @Test
        @DisplayName("非创建者 → 抛异常")
        void shouldThrowWhenNotOwner() {
            when(courseMapper.selectById(1L)).thenReturn(sample);

            assertThatThrownBy(() -> courseService.update(1L, 999L, "改名", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("无权修改");
            verify(courseMapper, never()).updateById(any(Course.class));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("正常删除 → 清理缓存")
        void shouldDelete() {
            RBucket<Course> bucket = mock(RBucket.class);
            when(redissonClient.<Course>getBucket(anyString())).thenReturn(bucket);
            when(courseMapper.selectById(1L)).thenReturn(sample);

            courseService.delete(1L, 100L);

            verify(courseMapper).deleteById(eq(1L));
            verify(bucket).delete();
        }

        @Test
        @DisplayName("非创建者 → 抛异常")
        void shouldThrowWhenNotOwner() {
            when(courseMapper.selectById(1L)).thenReturn(sample);

            assertThatThrownBy(() -> courseService.delete(1L, 999L))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(courseMapper, never()).deleteById(anyLong());
        }
    }
}
