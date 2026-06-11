package com.firedemo.demo.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 测试用例定义
 */
@Data
class TestCase {
    private int id;
    private String query;
    /** 关键词检索期望命中 */
    private List<String> expectedKeywords;
    /** 内容片段期望命中 */
    private List<String> expectedContent;
    /** 至少覆盖多少个 chunk */
    private int minChunksToCover;
}

/**
 * 评估数据集加载器
 */
@Component
public class EvalDataset {

    private final List<TestCase> cases;

    public EvalDataset(ObjectMapper objectMapper) throws IOException {
        ClassPathResource resource = new ClassPathResource("rag-eval-dataset.json");
        this.cases = objectMapper.readValue(resource.getInputStream(),
                new TypeReference<List<TestCase>>() {
                });
    }

    public List<TestCase> getAll() {
        return cases;
    }

    public List<TestCase> sampleForGenRound(int count) {
        return cases.size() <= count ? cases : cases.subList(0, count);
    }

    public int size() {
        return cases.size();
    }
}
