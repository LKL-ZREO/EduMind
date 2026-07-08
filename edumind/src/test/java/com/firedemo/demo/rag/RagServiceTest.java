package com.firedemo.demo.rag;

import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.Service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RagService — RAG 检索编排")
class RagServiceTest {

    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStoreService vectorStoreService;
    @Mock private RrfFusionService rrfFusionService;
    @Mock private RerankerService rerankerService;
    @Mock private QueryRewriter queryRewriter;
    @Mock private CourseService courseService;

    @InjectMocks
    private RagService ragService;

    private static final float[] FAKE_EMBEDDING = new float[128];

    private static DocumentChunk chunk(String id) {
        DocumentChunk c = new DocumentChunk();
        c.setId(id);
        c.setContent("内容-" + id);
        return c;
    }

    @BeforeEach
    void setUp() {
        when(embeddingService.embedQuery(anyString())).thenReturn(FAKE_EMBEDDING);
        when(queryRewriter.needsRewrite(anyString())).thenReturn(false);
        // 默认：模型就绪，精排直接透传（不过滤、不重排）
        when(rerankerService.isModelReady()).thenReturn(true);
        when(rerankerService.rerank(anyString(), anyList(), anyInt(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    List<RrfFusionService.ScoredChunk> in = inv.getArgument(1);
                    int topK = inv.getArgument(2);
                    return in.size() <= topK ? in : in.subList(0, topK);
                });
    }

    @Nested
    @DisplayName("基础检索路径")
    class BasicRetrieval {

        @Test
        @DisplayName("向量 + 关键词均有结果 → RRF 融合 → Reranker 精排")
        void shouldFuseAndRerank() {
            when(vectorStoreService.similaritySearch(any(), anyInt(), any(), any()))
                    .thenReturn(List.of(chunk("A"), chunk("B"), chunk("C")));
            when(vectorStoreService.keywordSearch(anyString(), anyInt(), any(), any()))
                    .thenReturn(List.of(
                            scoredKeyword("B", 0.8),
                            scoredKeyword("D", 0.5)));
            when(rrfFusionService.fuse(anyList(), anyList()))
                    .thenReturn(List.of(
                            scoredRrf("B", 0.03), scoredRrf("A", 0.025),
                            scoredRrf("D", 0.02), scoredRrf("C", 0.015)));

            RagResult result = ragService.search(RagSearchRequest.builder()
                    .query("Java 多态")
                    .topK(5)
                    .build());

            assertThat(result).isNotNull();
            assertThat(result.getResults()).isNotEmpty();
            verify(rrfFusionService).fuse(anyList(), anyList());
            verify(rerankerService).rerank(anyString(), anyList(), anyInt(), any());
        }

        @Test
        @DisplayName("Reranker 模型不可用 → 跳过精排，直接返回 RRF 结果")
        void shouldSkipRerankerWhenModelNotReady() {
            when(rerankerService.isModelReady()).thenReturn(false);
            when(vectorStoreService.similaritySearch(any(), anyInt(), any(), any()))
                    .thenReturn(List.of(chunk("A")));
            when(vectorStoreService.keywordSearch(anyString(), anyInt(), any(), any()))
                    .thenReturn(List.of());
            when(rrfFusionService.fuse(anyList(), anyList()))
                    .thenReturn(List.of(scoredRrf("A", 0.02)));

            RagResult result = ragService.search(RagSearchRequest.builder()
                    .query("测试").topK(3).build());

            assertThat(result).isNotNull();
            // 没有调用 rerank（因为模型不可用）
            verify(rerankerService, never()).rerank(anyString(), anyList(), anyInt(), any());
        }

        @Test
        @DisplayName("禁用 Reranker → 跳过精排")
        void shouldSkipRerankerWhenDisabled() {
            when(vectorStoreService.similaritySearch(any(), anyInt(), any(), any()))
                    .thenReturn(List.of(chunk("A")));
            when(vectorStoreService.keywordSearch(anyString(), anyInt(), any(), any()))
                    .thenReturn(List.of());
            when(rrfFusionService.fuse(anyList(), anyList()))
                    .thenReturn(List.of(scoredRrf("A", 0.02)));

            ragService.search(RagSearchRequest.builder()
                    .query("test").topK(3).enableReranker(false).build());

            verify(rerankerService, never()).rerank(anyString(), anyList(), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("空结果场景")
    class EmptyResults {

        @Test
        @DisplayName("向量和关键词都无结果 → 返回空，不触发 RRF/Reranker")
        void shouldReturnEmptyEarly() {
            when(vectorStoreService.similaritySearch(any(), anyInt(), any(), any()))
                    .thenReturn(List.of());
            when(vectorStoreService.keywordSearch(anyString(), anyInt(), any(), any()))
                    .thenReturn(List.of());

            RagResult result = ragService.search(RagSearchRequest.builder()
                    .query("不存在的查询").topK(5).build());

            assertThat(result).isNotNull();
            assertThat(result.getResults()).isEmpty();
            verify(rrfFusionService, never()).fuse(anyList(), anyList());
        }
    }

    @Nested
    @DisplayName("查询改写")
    class QueryRewrite {

        @Test
        @DisplayName("模糊查询 → 关键词检索用改写后的 query")
        void shouldRewriteVagueQuery() {
            when(queryRewriter.needsRewrite("那个")).thenReturn(true);
            when(queryRewriter.rewrite("那个")).thenReturn("Java 多态 面向对象");
            when(vectorStoreService.similaritySearch(any(), anyInt(), any(), any()))
                    .thenReturn(List.of(chunk("A")));
            when(vectorStoreService.keywordSearch(eq("Java 多态 面向对象"), anyInt(), any(), any()))
                    .thenReturn(List.of());
            when(rrfFusionService.fuse(anyList(), anyList()))
                    .thenReturn(List.of(scoredRrf("A", 0.02)));

            ragService.search(RagSearchRequest.builder()
                    .query("那个").topK(3).build());

            // 关键词检索应收到改写后的 query
            verify(vectorStoreService).keywordSearch(contains("Java"), anyInt(), any(), any());
        }

        @Test
        @DisplayName("改写抛出异常 → 静默降级，不中断检索")
        void shouldFallbackOnRewriteError() {
            when(queryRewriter.needsRewrite("xxx")).thenReturn(true);
            when(queryRewriter.rewrite("xxx")).thenThrow(new RuntimeException("LLM 超时"));
            when(vectorStoreService.similaritySearch(any(), anyInt(), any(), any()))
                    .thenReturn(List.of(chunk("A")));
            when(vectorStoreService.keywordSearch(eq("xxx"), anyInt(), any(), any()))
                    .thenReturn(List.of());
            when(rrfFusionService.fuse(anyList(), anyList()))
                    .thenReturn(List.of(scoredRrf("A", 0.02)));

            assertThatCode(() -> ragService.search(RagSearchRequest.builder()
                    .query("xxx").topK(3).build()))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("低置信度兜底")
    class LowConfidenceFallback {

        @Test
        @DisplayName("top-1 Reranker 分数 < 0.3 → 触发改写 + 追加检索 + 重新融合")
        void shouldTriggerFallbackOnLowConfidence() {
            when(vectorStoreService.similaritySearch(any(), anyInt(), any(), any()))
                    .thenReturn(List.of(chunk("A"), chunk("B")));
            when(vectorStoreService.keywordSearch(anyString(), anyInt(), any(), any()))
                    .thenReturn(List.of());

            // RRF 融合结果
            when(rrfFusionService.fuse(anyList(), anyList()))
                    .thenReturn(List.of(scoredRrf("A", 0.015), scoredRrf("B", 0.01)));

            // Reranker 第一次返回 low-confidence
            when(rerankerService.rerank(anyString(), anyList(), anyInt(), any()))
                    .thenReturn(List.of(scoredRrf("A", 0.2), scoredRrf("B", 0.15)))
                    .thenReturn(List.of(scoredRrf("A", 0.7), scoredRrf("B", 0.5)));

            when(queryRewriter.rewrite(anyString())).thenReturn("改写后的查询");

            // 追加的关键词检索
            when(vectorStoreService.keywordSearch(eq("改写后的查询"), anyInt(), any(), any()))
                    .thenReturn(List.of(scoredKeyword("C", 0.6)));

            // 重新 RRF
            when(rrfFusionService.fuse(anyList(), anyList()))
                    .thenReturn(List.of(scoredRrf("C", 0.04), scoredRrf("A", 0.03)));

            RagResult result = ragService.search(RagSearchRequest.builder()
                    .query("模糊问题").topK(3).build());

            assertThat(result).isNotNull();
            // verify 改写被触发了
            verify(queryRewriter).rewrite("模糊问题");
        }

        @Test
        @DisplayName("top-1 分数 ≥ 0.3 → 不触发兜底")
        void shouldNotTriggerFallbackWhenConfident() {
            when(vectorStoreService.similaritySearch(any(), anyInt(), any(), any()))
                    .thenReturn(List.of(chunk("A")));
            when(vectorStoreService.keywordSearch(anyString(), anyInt(), any(), any()))
                    .thenReturn(List.of());
            when(rrfFusionService.fuse(anyList(), anyList()))
                    .thenReturn(List.of(scoredRrf("A", 0.02)));

            // Reranker 返回高分
            when(rerankerService.rerank(anyString(), anyList(), anyInt(), any()))
                    .thenReturn(List.of(scoredRrf("A", 0.85)));

            ragService.search(RagSearchRequest.builder()
                    .query("明确的查询").topK(3).build());

            // 不应触发改写兜底
            verify(queryRewriter, never()).rewrite(anyString());
        }
    }

    // ---- 辅助方法 ----

    private static VectorStoreService.ScoredChunk scoredKeyword(String id, double score) {
        return new VectorStoreService.ScoredChunk(chunk(id), score);
    }

    private static RrfFusionService.ScoredChunk scoredRrf(String id, double score) {
        return new RrfFusionService.ScoredChunk(chunk(id), score);
    }
}
