package com.firedemo.demo.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.mcp.ToolDefinition;
import com.firedemo.demo.vision.VisionAnalysisRequest;
import com.firedemo.demo.vision.VisualObservation;
import com.firedemo.demo.vision.VisionUnderstandingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class VisionAnalyzeTool implements ToolDefinition {

    private final VisionUnderstandingService visionUnderstandingService;
    private final ObjectMapper objectMapper;

    public VisionAnalyzeTool(VisionUnderstandingService visionUnderstandingService,
                             ObjectMapper objectMapper) {
        this.visionUnderstandingService = visionUnderstandingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "analyzeVisualContent";
    }

    @Override
    public String description() {
        return """
                分析已导入的视觉资产。优先传入 assetId；task 支持 describe、ocr、table、formula、code、homework。
                兼容旧调用：可传 sourceType=url/base64/cq 和 source，工具会先导入资产再分析。
                用户发送图片、截图、题目、表格、公式或代码截图时使用本工具。
                """;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "assetId", Map.of(
                                "type", "string",
                                "description", "视觉资产导入层返回的 assetId，优先使用"
                        ),
                        "task", Map.of(
                                "type", "string",
                                "description", "视觉任务类型",
                                "enum", List.of("describe", "ocr", "table", "formula", "code", "homework")
                        ),
                        "sourceType", Map.of(
                                "type", "string",
                                "description", "兼容参数：url、base64 或 cq",
                                "enum", List.of("url", "base64", "cq")
                        ),
                        "source", Map.of(
                                "type", "string",
                                "description", "兼容参数：图片 URL、base64、data URL 或完整 CQ 图片消息"
                        ),
                        "question", Map.of(
                                "type", "string",
                                "description", "用户关于视觉资产的问题"
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public String execute(Map<String, Object> arguments, AgentExecutionContext context) {
        String assetId = getString(arguments, "assetId");
        String task = getString(arguments, "task");
        String sourceType = getString(arguments, "sourceType");
        String source = getString(arguments, "source");
        String question = getString(arguments, "question");
        if (question == null) question = getString(arguments, "prompt");

        log.info("MCP Tool analyzeVisualContent: assetId={}, task={}, sourceType={}, questionLen={}",
                abbreviateAssetId(assetId), task, sourceType, question != null ? question.length() : 0);

        try {
            VisualObservation observation = visionUnderstandingService.analyzeObservation(
                    new VisionAnalysisRequest(assetId, task, sourceType, source, question));
            return objectMapper.writeValueAsString(observation);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize visual observation", e);
            return "Visual analysis result serialization failed";
        } catch (Exception e) {
            log.error("Visual analysis failed", e);
            return "Visual analysis failed: " + e.getMessage();
        }
    }

    private String getString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private String abbreviateAssetId(String assetId) {
        if (assetId == null || assetId.length() <= 12) return assetId;
        return assetId.substring(0, 12);
    }
}
