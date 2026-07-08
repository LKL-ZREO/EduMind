package com.firedemo.demo.rag;

import com.firedemo.demo.Entity.DocumentChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("RrfFusionService — RRF 多路检索融合")
class RrfFusionServiceTest {

    private final RrfFusionService service = new RrfFusionService();

    /** 快速构造一个只有 id 的 chunk */
    private static DocumentChunk chunk(String id) {
        DocumentChunk c = new DocumentChunk();
        c.setId(id);
        c.setContent("content-" + id);
        return c;
    }

    @Nested
    @DisplayName("基础融合")
    class BasicFusion {

        @Test
        @DisplayName("两路各 3 条 → 融合后按 RRF 分数降序")
        void shouldMergeAndRank() {
            List<DocumentChunk> vector = List.of(chunk("A"), chunk("B"), chunk("C"));
            List<DocumentChunk> keyword = List.of(chunk("B"), chunk("C"), chunk("D"));

            List<RrfFusionService.ScoredChunk> result = service.fuse(vector, keyword, 60);

            // 在两路都出现的排最前（RRF 分数最高）
            assertThat(result).hasSize(4);
            assertThat(result.get(0).chunk().getId()).isEqualTo("B"); // rank 1+2
            assertThat(result.get(1).chunk().getId()).isEqualTo("C"); // rank 3+3
            // 单路出现的排后面
            assertThat(result).extracting(c -> c.chunk().getId())
                    .containsExactlyInAnyOrder("A", "B", "C", "D");
        }

        @Test
        @DisplayName("只有一路有结果 → 原样返回")
        void shouldReturnSingleSourceUntouched() {
            List<DocumentChunk> vector = List.of(chunk("X"), chunk("Y"));
            List<DocumentChunk> keyword = List.of();

            List<RrfFusionService.ScoredChunk> result = service.fuse(vector, keyword);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).chunk().getId()).isEqualTo("X");
            assertThat(result.get(1).chunk().getId()).isEqualTo("Y");
        }

        @Test
        @DisplayName("空输入 → 空输出")
        void shouldReturnEmptyForEmptyInput() {
            assertThat(service.fuse(List.of(), List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("RRF 分数正确性")
    class ScoreCorrectness {

        @Test
        @DisplayName("k 值越大 → 排名靠前的绝对分数越低（更平滑）")
        void largerKProducesSmootherScores() {
            List<DocumentChunk> vector = List.of(chunk("A"), chunk("B"), chunk("C"));
            List<DocumentChunk> keyword = List.of(chunk("B"), chunk("A"));

            var rLargeK = service.fuse(vector, keyword, 120);
            var rSmallK = service.fuse(vector, keyword, 1);

            // k 越大，第一名分数越小（被平滑了）
            assertThat(rLargeK.get(0).score()).isLessThan(rSmallK.get(0).score());
        }
    }

    @Nested
    @DisplayName("去重逻辑")
    class Dedup {

        @Test
        @DisplayName("同一 chunk id 出现多次 → 分数累加，只保留一条")
        void shouldDedupById() {
            // 向量路和关键词路都返回了同一个 chunk
            List<DocumentChunk> vector = List.of(chunk("SAME"));
            List<DocumentChunk> keyword = List.of(chunk("SAME"));

            var result = service.fuse(vector, keyword);

            assertThat(result).hasSize(1);
            // 两路都有 → 分数 = 1/(60+1) + 1/(60+1) = 2/61 ≈ 0.0328
            assertThat(result.get(0).score()).isCloseTo(2.0 / 61, within(0.0001));
        }
    }

    @Nested
    @DisplayName("大数据量")
    class LargeDataset {

        @Test
        @DisplayName("500 条 → 不报错，结果按分数排序")
        void shouldHandleLargeInput() {
            List<DocumentChunk> vector = new ArrayList<>();
            List<DocumentChunk> keyword = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                vector.add(chunk("v" + i));
                if (i < 200) keyword.add(chunk("k" + i));
            }

            var result = service.fuse(vector, keyword);

            assertThat(result).isNotEmpty();
            // 验证降序排列：每个元素的 score 不大于前一个
            for (int i = 1; i < result.size(); i++) {
                assertThat(result.get(i).score())
                        .isLessThanOrEqualTo(result.get(i - 1).score());
            }
        }
    }
}
