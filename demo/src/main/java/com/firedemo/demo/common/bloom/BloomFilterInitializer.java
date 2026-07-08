package com.firedemo.demo.common.bloom;

import com.firedemo.demo.mapper.ClassInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 布隆过滤器初始化 — 启动时加载所有班级ID
 * <p>
 * 用于缓存穿透防护：不存在的 classId 直接拦截，避免打到 DB
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterInitializer implements ApplicationRunner {

    private final RBloomFilter<String> classIdBloomFilter;
    private final ClassInfoMapper classInfoMapper;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> ids = classInfoMapper.selectAllIds();
        for (Long id : ids) {
            classIdBloomFilter.add(String.valueOf(id));
        }
        log.info("布隆过滤器初始化完成 — 加载 {} 个班级ID, 误判率 3%", ids.size());
    }
}
