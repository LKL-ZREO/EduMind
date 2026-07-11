package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.Entity.Course;
import com.firedemo.demo.Service.CourseService;
import com.firedemo.demo.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final RedissonClient redissonClient;

    private static final String CACHE_KEY_PREFIX = "course:";
    private static final String CACHE_KEY_BY_CLASS = "course:byClass:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    @Override
    public Course getById(Long courseId) {
        if (courseId == null) return null;
        RBucket<Course> bucket = redissonClient.getBucket(CACHE_KEY_PREFIX + courseId);
        Course cached = bucket.get();
        if (cached != null) return cached;

        Course course = courseMapper.selectById(courseId);
        if (course != null) {
            bucket.set(course, CACHE_TTL);
        }
        return course;
    }

    @Override
    public Course getByClassId(Long classId) {
        if (classId == null) return null;
        RBucket<Long> idBucket = redissonClient.getBucket(CACHE_KEY_BY_CLASS + classId);
        Long courseId = idBucket.get();
        if (courseId != null) {
            return getById(courseId);
        }

        Course course = courseMapper.selectByClassId(classId);
        if (course != null) {
            idBucket.set(course.getId(), CACHE_TTL);
            // 同时缓存课程对象
            redissonClient.<Course>getBucket(CACHE_KEY_PREFIX + course.getId()).set(course, CACHE_TTL);
        }
        return course;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "teacherCourses", key = "#teacherId")
    public Course create(Long teacherId, String name, String systemPrompt, String knowledgeScope) {
        Course course = new Course();
        course.setTeacherId(teacherId);
        course.setName(name);
        course.setSystemPrompt(systemPrompt);
        course.setKnowledgeScope(knowledgeScope);
        courseMapper.insert(course);
        log.info("Course created: id={}, name={}, teacherId={}", course.getId(), name, teacherId);
        return course;
    }

    @Override
    @Cacheable(value = "teacherCourses", key = "#teacherId", sync = true)
    public List<Course> listByTeacherId(Long teacherId) {
        if (teacherId == null) return List.of();
        return courseMapper.selectByTeacherId(teacherId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "teacherCourses", key = "#teacherId")
    public void update(Long courseId, Long teacherId, String name,
                       String systemPrompt, String knowledgeScope) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) throw new IllegalArgumentException("课程不存在: " + courseId);
        if (!teacherId.equals(course.getTeacherId())) throw new IllegalArgumentException("无权修改此课程");

        if (name != null && !name.isBlank()) course.setName(name);
        if (systemPrompt != null && !systemPrompt.isBlank()) course.setSystemPrompt(systemPrompt);
        if (knowledgeScope != null) course.setKnowledgeScope(knowledgeScope);
        courseMapper.updateById(course);
        redissonClient.getBucket(CACHE_KEY_PREFIX + courseId).delete();
        log.info("Course updated: id={}, name={}", courseId, course.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "teacherCourses", key = "#teacherId")
    public void delete(Long courseId, Long teacherId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) throw new IllegalArgumentException("课程不存在: " + courseId);
        if (!teacherId.equals(course.getTeacherId())) throw new IllegalArgumentException("无权删除此课程");
        courseMapper.deleteById(courseId);
        redissonClient.getBucket(CACHE_KEY_PREFIX + courseId).delete();
        log.info("Course deleted: id={}, name={}", courseId, course.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePrompt(Long courseId, String systemPrompt, String knowledgeScope) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new IllegalArgumentException("课程不存在: " + courseId);
        }
        course.setSystemPrompt(systemPrompt);
        course.setKnowledgeScope(knowledgeScope);
        courseMapper.updateById(course);
        // 清除缓存
        redissonClient.getBucket(CACHE_KEY_PREFIX + courseId).delete();
        log.info("Course prompt updated: id={}, name={}", courseId, course.getName());
    }

    // ==================== 预设模板 ====================

    @Override
    public Map<String, PresetTemplate> getPresets() {
        Properties props = loadPresets();
        Map<String, PresetTemplate> result = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.endsWith(".name")) {
                String presetKey = key.substring(0, key.length() - 5); // strip ".name"
                String name = props.getProperty(key);
                String prompt = props.getProperty(presetKey + ".prompt", "");
                result.put(presetKey, new PresetTemplate(presetKey, name, prompt));
            }
        }
        return result;
    }

    @Override
    public PresetTemplate getPreset(String key) {
        Properties props = loadPresets();
        String name = props.getProperty(key + ".name");
        String prompt = props.getProperty(key + ".prompt");
        if (name == null || prompt == null) return null;
        return new PresetTemplate(key, name, prompt);
    }

    private Properties loadPresets() {
        Properties props = new Properties();
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("presets/course-presets.properties")) {
            if (is != null) {
                props.load(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            log.warn("Failed to load course presets", e);
        }
        return props;
    }
}
