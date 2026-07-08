package com.firedemo.demo.common.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Prompt 模板加载器 — 将 Prompt 从 Java 代码解耦到 resource/prompts/ 目录
 * <p>
 * 用法：
 * <pre>{@code
 *   String template = promptLoader.load("grading-system.txt");
 *   String prompt = template.replace("{{var}}", value);
 * }</pre>
 */
@Slf4j
@Component
public class PromptLoader {

    /**
     * 加载 prompt 模板文件
     *
     * @param name 文件名，如 "grading-system.txt"
     * @return 模板内容
     */
    public String load(String name) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + name);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt template: {}", name, e);
            return "";
        }
    }
}
