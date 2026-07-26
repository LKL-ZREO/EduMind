package com.firedemo.demo.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.eval.model.EvalCase;
import com.firedemo.demo.eval.model.EvalResponse;
import com.firedemo.demo.eval.service.EvalService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 检索 + 生成质量评测。
 *
 * <p>两个测试方法：
 * <ol>
 *   <li>{@code evaluateRealPipeline()} — 检索层指标（Keyword Recall, Content Coverage, MRR）</li>
 *   <li>{@code evaluateGenerationQuality()} — 生成层指标（Faithfulness, AnswerRelevancy）
 *       通过 LLM-as-Judge 评分</li>
 * </ol>
 *
 * <p>前提：本地 PostgreSQL 中已索引文档 + rag-eval-dataset.json 有数据。</p>
 *
 * <p>运行：
 * <pre>
 *   ./mvnw test -Dtest="RagEvalRunner" -DEVALUATION_ENABLED=true -Dspring.profiles.active=local
 * </pre></p>
 */
@SpringBootTest
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "EVALUATION_ENABLED", matches = "true")
@DisplayName("RAG 质量评测（检索 + LLM-as-Judge）")
class RagEvalRunner {

    @Autowired
    private RagService ragService;

    @Autowired
    private EvalService evalService;

    private List<EvalCase> cases;

    @BeforeAll
    @SuppressWarnings("unchecked")
    void loadDataset() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = new ClassPathResource("rag-eval-dataset.json").getInputStream()) {
            cases = mapper.readValue(in, new TypeReference<List<EvalCase>>() {});
        }

        System.out.println("\n========================================");
        System.out.println("  RAG 质量评测");
        System.out.println("  查询数: " + cases.size());
        System.out.println("  管线: Embedding → pgvector → Keyword → RRF → Reranker");
        System.out.println("  Judge: LLM-as-Judge (纯 Java)");
        System.out.println("========================================\n");
    }

    // ======================== 检索层指标 ========================

    @Test
    @DisplayName("检索质量（Keyword Recall, Content Coverage, MRR, Hit Rate）")
    void evaluateRealPipeline() {
        if (cases.isEmpty()) {
            System.out.println("  [跳过] 无评测数据\n");
            return;
        }

        int totalKeywords = 0, hitKeywords = 0;
        int totalContent = 0, hitContent = 0;
        double mrrSum = 0;
        int queriesWithHits = 0;

        System.out.printf("  %-4s %-28s %8s %8s %8s %8s%n",
                "ID", "Query", "KW Hit", "KW Total", "C Hit", "MRR");
        System.out.println("  " + "-".repeat(70));

        for (EvalCase c : cases) {
            RagResult result = ragService.search(RagSearchRequest.builder()
                    .query(c.getQuery())
                    .topK(5)
                    .enableReranker(true)
                    .build());

            String concatenated = result.getResults().stream()
                    .map(sc -> sc.chunk().getContent())
                    .collect(Collectors.joining(" "));

            int kwHit = 0;
            for (String kw : c.getExpectedKeywords()) {
                if (concatenated.toLowerCase().contains(kw.toLowerCase())) {
                    kwHit++;
                }
            }
            totalKeywords += c.getExpectedKeywords().size();
            hitKeywords += kwHit;

            int cHit = 0;
            for (String expected : c.getExpectedContent()) {
                if (containsFuzzy(concatenated, expected)) {
                    cHit++;
                }
            }
            totalContent += c.getExpectedContent().size();
            hitContent += cHit;

            double rr = 0;
            for (int rank = 0; rank < result.getResults().size(); rank++) {
                boolean hit = false;
                for (String expected : c.getExpectedContent()) {
                    if (containsFuzzy(result.getResults().get(rank).chunk().getContent(), expected)) {
                        hit = true;
                        break;
                    }
                }
                if (hit) { rr = 1.0 / (rank + 1); break; }
            }
            mrrSum += rr;
            if (kwHit > 0) queriesWithHits++;

            System.out.printf("  %-4d %-28s %8d %8d %8d %8.3f%n",
                    c.getId(), truncate(c.getQuery(), 28), kwHit,
                    c.getExpectedKeywords().size(), cHit, rr);
        }

        double kwRecall = (double) hitKeywords / Math.max(1, totalKeywords);
        double cCoverage = (double) hitContent / Math.max(1, totalContent);
        double mrr = mrrSum / cases.size();
        double hitRate = 100.0 * queriesWithHits / cases.size();

        System.out.println("\n  ╔══════════════════════════════╗");
        System.out.printf("  ║  检索层指标                   ║%n");
        System.out.println("  ╠══════════════════════════════╣");
        System.out.printf("  ║  Hit Rate(≥1 kw):   %5.0f%%  ║%n", hitRate);
        System.out.printf("  ║  Keyword Recall@5:  %5.1f%%  ║%n", kwRecall * 100);
        System.out.printf("  ║  ContentCoverage@5: %5.1f%%  ║%n", cCoverage * 100);
        System.out.printf("  ║  MRR:               %7.3f  ║%n", mrr);
        System.out.println("  ╚══════════════════════════════╝\n");

        assertThat(kwRecall).as("Keyword Recall@5 应 ≥ 30%").isGreaterThanOrEqualTo(0.30);
        assertThat(hitRate).as("命中率应 ≥ 60%").isGreaterThanOrEqualTo(60.0);
    }

    // ======================== 生成层指标（LLM-as-Judge） ========================

    @Test
    @DisplayName("生成质量（LLM-as-Judge: Faithfulness + AnswerRelevancy）")
    void evaluateGenerationQuality() {
        if (cases.isEmpty()) {
            System.out.println("  [跳过] 无评测数据\n");
            return;
        }

        EvalResponse response = evalService.runEvaluation(
                List.of("faithfulness", "answer_relevancy"),
                5,      // topK
                true,   // enableReranker
                true    // generateAnswers
        );

        // CI 门槛
        if (response.isOk() && response.faithfulness() != null) {
            assertThat(response.faithfulness())
                    .as("Faithfulness 应 ≥ 0.70")
                    .isGreaterThanOrEqualTo(0.70);
        }
    }

    // ======================== 工具方法 ========================

    private static boolean containsFuzzy(String text, String snippet) {
        String clean1 = text.replaceAll("[\\s，。！？、\"'（）《》]", "");
        String clean2 = snippet.replaceAll("[\\s，。！？、\"'（）《》]", "");
        if (clean2.length() <= 4) return clean1.contains(clean2);
        int lcs = lcsLength(clean1, clean2);
        return (double) lcs / clean2.length() >= 0.5;
    }

    private static int lcsLength(String a, String b) {
        int m = a.length(), n = b.length();
        int[] prev = new int[n + 1];
        for (int i = 1; i <= m; i++) {
            int[] curr = new int[n + 1];
            for (int j = 1; j <= n; j++) {
                curr[j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? prev[j - 1] + 1
                        : Math.max(prev[j], curr[j - 1]);
            }
            prev = curr;
        }
        return prev[n];
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }
}
