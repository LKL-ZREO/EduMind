package com.firedemo.demo.rag;

import com.firedemo.demo.Entity.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmartChunkService — 文档切割")
class SmartChunkServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @InjectMocks
    private SmartChunkService service;

    /** 假的嵌入向量，所有 chunk 返回同一个 */
    private static final float[] FAKE_EMBEDDING = new float[128];

    @BeforeEach
    void setUp() {
        when(embeddingService.embed(anyString())).thenReturn(FAKE_EMBEDDING);
    }

    @Nested
    @DisplayName("Markdown 文档切割")
    class Markdown {

        @Test
        @DisplayName("按标题层级切分 → 每个 # section 独立成块")
        void shouldSplitByHeaders() {
            String md = """
                    # 第一章：Java 基础
                    本章介绍 Java 的基本概念，包括面向对象编程、类与对象、继承与多态。

                    ## 1.1 面向对象
                    面向对象编程是一种编程范式，核心思想是将数据和行为封装在对象中。

                    ## 1.2 类与对象
                    类是对象的模板，定义了对象的属性和方法。对象是类的实例。

                    # 第二章：集合框架
                    Java 集合框架提供了一套接口和类，用于存储和操作一组对象。
                    """;

            List<DocumentChunk> chunks = service.chunk(md, null);

            // 至少切出 4 个 section（2 个 #、2 个 ##）
            assertThat(chunks).isNotEmpty();
            assertThat(chunks.size()).isGreaterThanOrEqualTo(4);
            // 每个 chunk 应有嵌入
            assertThat(chunks).allMatch(c -> c.getEmbedding() != null);
            // 第一个 chunk 应包含"第一章"
            assertThat(chunks.get(0).getContent()).contains("Java 基础");
        }

        @Test
        @DisplayName("纯文本（无标题）→ 按段落切分")
        void shouldSplitPlainTextByParagraphs() {
            String text = """
                    这是第一段内容。包含一些基本的介绍文字。

                    这是第二段内容。这一段与上一段之间有空行分隔。

                    这是第三段内容。最后一段文字。""";

            List<DocumentChunk> chunks = service.chunk(text, null);

            assertThat(chunks).isNotEmpty();
            // 三段 → 至少 3 个 chunk
            assertThat(chunks.size()).isGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("代码文档切割")
    class Code {

        @Test
        @DisplayName("含代码块的文本 → 保护代码不被拆散")
        void shouldPreserveCodeBlocks() {
            String mdWithCode = """
                    # 示例：二分查找

                    下面是二分查找的代码实现：

                    ```java
                    public static int binarySearch(int[] arr, int target) {
                        int left = 0;
                        int right = arr.length - 1;
                        while (left <= right) {
                            int mid = left + (right - left) / 2;
                            if (arr[mid] == target) return mid;
                            if (arr[mid] < target) left = mid + 1;
                            else right = mid - 1;
                        }
                        return -1;
                    }
                    ```

                    这个算法的时间复杂度是 O(log n)。""";

            List<DocumentChunk> chunks = service.chunk(mdWithCode, null);

            assertThat(chunks).isNotEmpty();
            // 代码块应作为一个整体存在于某个 chunk 中
            boolean hasCodeBlock = chunks.stream()
                    .anyMatch(c -> c.getContent().contains("binarySearch")
                                 && c.getContent().contains("return -1"));
            assertThat(hasCodeBlock).isTrue();
        }
    }

    @Nested
    @DisplayName("对话文档切割")
    class Conversation {

        @Test
        @DisplayName("User/Assistant 格式 → 按轮次切割")
        void shouldSplitByTurns() {
            String chat = """
                    User: 请问什么是 RAG？

                    Assistant: RAG（Retrieval-Augmented Generation）是一种结合检索和生成的 AI 技术。

                    User: RAG 有什么优点？

                    Assistant: RAG 可以减少大模型的幻觉，提高回答的准确性和时效性。""";

            List<DocumentChunk> chunks = service.chunk(chat, null);

            assertThat(chunks).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("边界条件")
    class EdgeCases {

        @Test
        @DisplayName("空字符串 → 不做切割")
        void shouldHandleEmptyContent() {
            List<DocumentChunk> chunks = service.chunk("   \n  ", null);

            // 空内容应返回空或极少 chunk
            assertThat(chunks.size()).isLessThanOrEqualTo(1);
        }

        @Test
        @DisplayName("自定义 maxTokens → 单块超出则再切分")
        void shouldRespectMaxTokens() {
            String longText = "这是很长的内容。".repeat(200); // ~1200 字符

            SmartChunkService.ChunkConfig config = new SmartChunkService.ChunkConfig();
            config.setMaxTokens(50); // ~150 字符 → 应切成多块

            List<DocumentChunk> chunks = service.chunk(longText, config);

            assertThat(chunks.size()).isGreaterThan(1);
        }

        @Test
        @DisplayName("相邻 chunk 有滑动窗口重叠")
        void shouldHaveOverlapBetweenNeighbors() {
            SmartChunkService.ChunkConfig config = new SmartChunkService.ChunkConfig();
            config.setMaxTokens(80);
            config.setOverlapTokens(30);

            // 一段中等长度的 Markdown
            String text = ("## 知识点\n这是一段测试内容。" +
                           "包含了足够多的文字以便触发切割。").repeat(20);

            List<DocumentChunk> chunks = service.chunk(text, config);

            assertThat(chunks.size()).isGreaterThan(1);
            // 前一个 chunk 的 nextSummary 应该指向后一个
            assertThat(chunks.get(0).getNextSummary()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Chunk 元数据正确性")
    class Metadata {

        @Test
        @DisplayName("每个 chunk 都有 id/tokenCount/charCount/sectionIndex")
        void shouldSetMetadata() {
            String text = "# 测试\n第一段。\n\n## 子标题\n第二段内容文字。";

            List<DocumentChunk> chunks = service.chunk(text, null);

            assertThat(chunks).allMatch(c -> c.getId() != null);
            assertThat(chunks).allMatch(c -> c.getTokenCount() > 0);
            assertThat(chunks).allMatch(c -> c.getCharCount() > 0);
        }
    }
}
