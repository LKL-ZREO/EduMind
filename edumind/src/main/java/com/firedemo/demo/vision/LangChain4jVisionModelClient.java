package com.firedemo.demo.vision;

import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.config.properties.LlmProperties;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

@Slf4j
@Component
public class LangChain4jVisionModelClient implements VisionModelClient {

    private final ChatModel visionChatModel;
    private final LlmProperties llmProperties;

    public LangChain4jVisionModelClient(
            @Qualifier("visionChatLanguageModel") ChatModel visionChatModel,
            LlmProperties llmProperties) {
        this.visionChatModel = visionChatModel;
        this.llmProperties = llmProperties;
    }

    @Override
    public VisualObservation analyze(VisualAsset asset, VisionTask task, String question) {
        long startedAt = System.currentTimeMillis();
        String prompt = question != null && !question.isBlank()
                ? question
                : defaultQuestion(task);

        List<Content> contents = List.of(
                ImageContent.from(
                        Base64.getEncoder().encodeToString(asset.content()),
                        asset.mimeType()),
                TextContent.from(buildPrompt(task, prompt))
        );
        List<ChatMessage> messages = List.of(
                SystemMessage.from("""
                        你是 EduMind 的视觉理解服务。
                        只根据图片中可观察到的内容回答，不要根据文件名、URL 或上下文猜测。
                        无法确认的内容必须明确标注不确定。
                        """),
                UserMessage.from(contents)
        );

        ChatResponse response = visionChatModel.chat(messages);
        String answer = response.aiMessage() != null ? response.aiMessage().text() : null;
        if (answer == null || answer.isBlank()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
        }

        String extractedText = task == VisionTask.OCR ? answer : "";
        return new VisualObservation(
                asset.assetId(),
                task,
                answer,
                extractedText,
                List.of(),
                1.0,
                List.of(),
                llmProperties.resolveVisionModel(),
                System.currentTimeMillis() - startedAt
        );
    }

    private String buildPrompt(VisionTask task, String question) {
        return """
                任务类型：%s
                用户问题：%s

                请直接给出分析结果。若图片包含文字、表格、公式、代码或题目，
                先提取关键内容，再回答问题。
                """.formatted(task.name().toLowerCase(), question);
    }

    private String defaultQuestion(VisionTask task) {
        return switch (task) {
            case OCR -> "提取图片中的全部可读文字。";
            case TABLE -> "提取并整理图片中的表格。";
            case FORMULA -> "识别图片中的数学公式，并使用 LaTeX 表示。";
            case CODE -> "识别图片中的代码，保留缩进和语言结构。";
            case HOMEWORK -> "识别题目内容并分析解题要求。";
            case DESCRIBE -> "描述并分析图片中的主要内容。";
        };
    }
}
