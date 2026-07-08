package com.firedemo.demo.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * RAG 检索质量评测 — 真实管线版本。
 *
 * <p>连接本地 PostgreSQL（profile=local），走完整 RAG 管线：
 * Embedding → 向量检索 → 关键词检索 → RRF 融合 → Reranker 精排，
 * 使用 rag-eval-dataset.json 评测并输出 Keyword Recall、Content Coverage、MRR。</p>
 *
 * <p><b>前提条件：</b>本地 PostgreSQL 中已索引 C 语言课件文档（通过 DocumentService 导入）。</p>
 *
 * <p>默认跳过（需要本地 DB + 模型），手动跑：<pre>
 *   ./mvnw test -Dtest="RagEvalRunner" -DEVALUATION_ENABLED=true
 * </pre></p>
 */
@SpringBootTest
@ActiveProfiles("local")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "EVALUATION_ENABLED", matches = "true")
@DisplayName("RAG 检索质量评测（真实管线）")
class RagEvalRunner {

    @Autowired
    private RagService ragService;

    private List<EvalCase> cases;

    @BeforeAll
    @SuppressWarnings("unchecked")
    void loadDataset() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = new ClassPathResource("rag-eval-dataset.json").getInputStream()) {
            cases = mapper.readValue(in, new TypeReference<List<EvalCase>>() {});
        }

        System.out.println("\n========================================");
        System.out.println("  RAG 真实管线评测");
        System.out.println("  查询数: " + cases.size());
        System.out.println("  管线: Embedding → pgvector → Keyword → RRF → Reranker");
        System.out.println("========================================\n");
    }

    @Test
    @DisplayName("全管线检索质量")
    void evaluateRealPipeline() {
        int totalKeywords = 0, hitKeywords = 0;
        int totalContent = 0, hitContent = 0;
        double mrrSum = 0;
        int queriesWithHits = 0;

        System.out.printf("  %-4s %-28s %8s %8s %8s %8s%n",
                "ID", "Query", "KW Hit", "KW Total", "C Hit", "MRR");
        System.out.println("  " + "-".repeat(70));

        for (EvalCase c : cases) {
            // 走完整 RAG 管线
            RagResult result = ragService.search(RagSearchRequest.builder()
                    .query(c.query)
                    .topK(5)
                    .enableReranker(true)
                    .build());

            // 拼接 top-5 内容
            String concatenated = result.getResults().stream()
                    .map(sc -> sc.chunk().getContent())
                    .collect(Collectors.joining(" "));

            // 关键词命中
            int kwHit = 0;
            for (String kw : c.expectedKeywords) {
                if (concatenated.toLowerCase().contains(kw.toLowerCase())) {
                    kwHit++;
                }
            }
            totalKeywords += c.expectedKeywords.size();
            hitKeywords += kwHit;

            // 内容片段命中（模糊匹配）
            int cHit = 0;
            for (String expected : c.expectedContent) {
                if (containsFuzzy(concatenated, expected)) {
                    cHit++;
                }
            }
            totalContent += c.expectedContent.size();
            hitContent += cHit;

            // MRR
            double rr = 0;
            for (int rank = 0; rank < result.getResults().size(); rank++) {
                boolean hit = false;
                for (String expected : c.expectedContent) {
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
                    c.id, truncate(c.query, 28), kwHit,
                    c.expectedKeywords.size(), cHit, rr);
        }

        double kwRecall = (double) hitKeywords / Math.max(1, totalKeywords);
        double cCoverage = (double) hitContent / Math.max(1, totalContent);
        double mrr = mrrSum / cases.size();
        double hitRate = 100.0 * queriesWithHits / cases.size();

        System.out.println("\n  ========================================");
        System.out.printf("  命中率(≥1 keyword): %.0f%%%n", hitRate);
        System.out.printf("  Keyword Recall@5:   %.1f%% (%d/%d)%n",
                kwRecall * 100, hitKeywords, totalKeywords);
        System.out.printf("  Content Coverage@5: %.1f%% (%d/%d)%n",
                cCoverage * 100, hitContent, totalContent);
        System.out.printf("  MRR:                %.3f%n", mrr);
        System.out.println("  ========================================\n");

        // CI 门槛
        assertThat(kwRecall).as("Keyword Recall@5 应 ≥ 30%").isGreaterThanOrEqualTo(0.30);
        assertThat(hitRate).as("命中率应 ≥ 60%").isGreaterThanOrEqualTo(60.0);
    }

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

    public static class EvalCase {
        public int id;
        public String query;
        public List<String> expectedKeywords;
        public List<String> expectedContent;
        public int minChunksToCover;
    }
}
