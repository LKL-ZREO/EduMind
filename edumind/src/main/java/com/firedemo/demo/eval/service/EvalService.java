package com.firedemo.demo.eval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.Entity.DocumentChunkEntity;
import com.firedemo.demo.eval.model.EvalCase;
import com.firedemo.demo.eval.model.EvalResponse;
import com.firedemo.demo.mapper.DocumentChunkMapper;
import com.firedemo.demo.rag.RagResult;
import com.firedemo.demo.rag.RagSearchRequest;
import com.firedemo.demo.rag.RagService;
import com.firedemo.demo.Service.OpenClawService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 评估编排服务（纯 Java，LLM-as-Judge）。
 *
 * <p>两个核心功能：
 * <ol>
 *   <li>{@code generateDatasetFromDb()} — 从 document_chunk 表自动生成评测数据集</li>
 *   <li>{@code runEvaluation()} — 走 RAG 管线 + LLM Judge 评 Faithfulness/AnswerRelevancy</li>
 * </ol>
 */
@Slf4j
@Service
public class EvalService {

    private final RagService ragService;
    private final OpenClawService openClawService;
    private final DocumentChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;

    private String faithfulnessPrompt;
    private String relevancePrompt;

    public EvalService(RagService ragService,
                       OpenClawService openClawService,
                       DocumentChunkMapper chunkMapper,
                       ObjectMapper objectMapper) {
        this.ragService = ragService;
        this.openClawService = openClawService;
        this.chunkMapper = chunkMapper;
        this.objectMapper = objectMapper;
        loadPrompts();
    }

    private void loadPrompts() {
        try {
            faithfulnessPrompt = loadResource("prompts/judge-faithfulness.txt");
            relevancePrompt = loadResource("prompts/judge-relevance.txt");
        } catch (Exception e) {
            log.warn("加载 Judge prompt 失败: {}", e.getMessage());
        }
    }

    private String loadResource(String path) throws Exception {
        InputStream in = new ClassPathResource(path).getInputStream();
        return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
    }

    // ======================== 数据集生成 ========================

    private static final String QA_GEN_PROMPT = """
            你是教育领域的测试题生成专家。请根据以下文档内容，生成 %d 个不同角度的问题。

            要求：
            1. 问题覆盖文档不同部分，不要集中在一处
            2. 类型多样化：定义类、概念对比类、应用类、因果类
            3. 每个问题配完整参考答案（答案必须能从文档中找到依据）
            4. 输出严格 JSON 数组，不要加 markdown 标记：
            [{"question": "...", "answer": "..."}]

            文档内容：
            %s

            JSON 输出：""";

    /**
     * 从数据库已索引的文档直接生成评测数据集。
     *
     * @param docIds        指定文档 ID（空 = 取全部）
     * @param questionCount 每个文档生成几个问题
     * @return 生成的用例列表
     */
    public List<EvalCase> generateDatasetFromDb(List<String> docIds, int questionCount) {
        // 1. 从 DB 查所有 document_chunk，按文档分组
        LambdaQueryWrapper<DocumentChunkEntity> qw = new LambdaQueryWrapper<>();
        qw.isNotNull(DocumentChunkEntity::getContent)
          .ne(DocumentChunkEntity::getContent, "");
        if (docIds != null && !docIds.isEmpty()) {
            qw.in(DocumentChunkEntity::getDocumentId, docIds);
        }

        List<DocumentChunkEntity> allChunks = chunkMapper.selectList(qw);
        if (allChunks.isEmpty()) {
            log.warn("数据库中没有已索引的文档");
            return List.of();
        }

        // 按 doc_id 分组，合并内容
        Map<String, List<DocumentChunkEntity>> byDoc = allChunks.stream()
                .collect(Collectors.groupingBy(DocumentChunkEntity::getDocumentId));

        log.info("从数据库读取 {} 个文档，共 {} 个 chunk，开始生成数据集...",
                byDoc.size(), allChunks.size());

        int totalDocs = byDoc.size();
        int processed = 0;

        // 2. 对每个文档调 LLM 生成 QA
        List<EvalCase> cases = new ArrayList<>();
        int caseId = 0;

        for (Map.Entry<String, List<DocumentChunkEntity>> entry : byDoc.entrySet()) {
            String docId = entry.getKey();
            List<DocumentChunkEntity> chunks = entry.getValue();

            // 按 sectionIndex 排序后拼接内容
            chunks.sort(Comparator.comparing(DocumentChunkEntity::getSectionIndex,
                    Comparator.nullsLast(Comparator.naturalOrder())));

            String docName = chunks.get(0).getDocumentName();
            String content = chunks.stream()
                    .map(DocumentChunkEntity::getContent)
                    .collect(Collectors.joining("\n"));

            // 截断过长内容
            if (content.length() > 15000) {
                content = content.substring(0, 7000)
                        + "\n...(中间内容已截断)...\n"
                        + content.substring(content.length() - 7000);
            }

            processed++;
            log.info("  [{}/{}] 生成中: {} ({} chunks, {} 字符)",
                    processed, totalDocs, docName, chunks.size(), content.length());

            try {
                String prompt = String.format(QA_GEN_PROMPT, questionCount, content);
                String raw = openClawService.chat(prompt, "eval-dataset-gen");

                // 清理 markdown
                raw = raw.trim();
                if (raw.startsWith("```")) {
                    raw = raw.substring(raw.indexOf("\n") + 1);
                    if (raw.endsWith("```")) raw = raw.substring(0, raw.lastIndexOf("```"));
                }

                @SuppressWarnings("unchecked")
                List<Map<String, String>> qaList = objectMapper.readValue(raw, List.class);

                for (Map<String, String> qa : qaList) {
                    caseId++;
                    cases.add(EvalCase.builder()
                            .id(caseId)
                            .query(qa.getOrDefault("question", "").trim())
                            .answer(qa.getOrDefault("answer", "").trim())
                            .sourceDocId(docId)
                            .sourceDocName(docName)
                            .build());
                }
                log.info("    → 生成 {} 题", qaList.size());

            } catch (Exception e) {
                log.warn("    → 生成失败: {}", e.getMessage());
            }
        }

        log.info("数据集生成完成: 共 {} 题，来自 {} 个文档", cases.size(), byDoc.size());
        return cases;
    }

    // ======================== 评估执行 ========================

    public EvalResponse runEvaluation(List<String> metrics, int topK,
                                      boolean enableReranker, boolean generateAnswers) {
        long startMs = System.currentTimeMillis();

        // 1. 加载数据集（优先 DB 自动生成，其次磁盘文件）
        List<EvalCase> cases = loadDataset();
        if (cases.isEmpty()) {
            cases = generateDatasetFromDb(null, 3);
            if (cases.isEmpty()) {
                return EvalResponse.builder()
                        .status("error")
                        .error("评测数据集为空且数据库无已索引文档")
                        .build();
            }
        }

        log.info("LLM-as-Judge 评估: {} 用例, topK={}, metrics={}", cases.size(), topK, metrics);

        // 2. 逐个评估
        int faithfulnessPass = 0, faithfulnessTotal = 0;
        int relevancePass = 0, relevanceTotal = 0;

        for (EvalCase c : cases) {
            RagResult result = ragService.search(RagSearchRequest.builder()
                    .query(c.getQuery())
                    .topK(topK)
                    .enableReranker(enableReranker)
                    .build());

            List<String> contexts = result.getResults().stream()
                    .map(sc -> sc.chunk().getContent())
                    .collect(Collectors.toList());

            String answer = c.getAnswer();
            if (generateAnswers && (answer == null || answer.isBlank()) && result.isHasContext()) {
                answer = generateAnswer(c.getQuery(), contexts);
            }
            if (answer == null) answer = "";

            if (metrics.contains("faithfulness") && !contexts.isEmpty() && !answer.isBlank()) {
                boolean pass = judgeFaithfulness(c.getQuery(), answer,
                        String.join("\n---\n", contexts));
                faithfulnessTotal++;
                if (pass) faithfulnessPass++;
            }

            if (metrics.contains("answer_relevancy") && !answer.isBlank()) {
                boolean pass = judgeRelevancy(c.getQuery(), answer);
                relevanceTotal++;
                if (pass) relevancePass++;
            }
        }

        // 3. 汇总
        long elapsedMs = System.currentTimeMillis() - startMs;
        double faithPct = faithfulnessTotal > 0
                ? (double) faithfulnessPass / faithfulnessTotal * 100 : -1;
        double relevPct = relevanceTotal > 0
                ? (double) relevancePass / relevanceTotal * 100 : -1;

        String summary = String.format(
                "\n  ╔══════════════════════════════════╗\n" +
                "  ║     RAG 评估 (LLM-as-Judge)      ║\n" +
                "  ╠══════════════════════════════════╣\n" +
                "  ║  Faithfulness:       %5.1f%%     ║\n" +
                "  ║  Answer Relevancy:   %5.1f%%     ║\n" +
                "  ║  用例数:             %-5d      ║\n" +
                "  ║  耗时:               %5.1fs     ║\n" +
                "  ╚══════════════════════════════════╝\n",
                faithPct, relevPct, cases.size(), elapsedMs / 1000.0);

        log.info(summary);
        System.out.println(summary);

        return EvalResponse.builder()
                .status("ok")
                .summary(Map.of(
                        "faithfulness_pct", Math.round(faithPct * 10) / 10.0,
                        "answer_relevancy_pct", Math.round(relevPct * 10) / 10.0,
                        "faithfulness_pass", (double) faithfulnessPass,
                        "faithfulness_total", (double) faithfulnessTotal,
                        "relevance_pass", (double) relevancePass,
                        "relevance_total", (double) relevanceTotal))
                .numCases(cases.size())
                .evalDurationSeconds(elapsedMs / 1000.0)
                .build();
    }

    // ---- Judge 调用 ----

    boolean judgeFaithfulness(String query, String answer, String context) {
        String prompt = faithfulnessPrompt
                .replace("{{query}}", query)
                .replace("{{answer}}", answer)
                .replace("{{context}}", truncate(context, 3000));
        return callJudge(prompt);
    }

    boolean judgeRelevancy(String query, String answer) {
        String prompt = relevancePrompt
                .replace("{{query}}", query)
                .replace("{{answer}}", answer);
        return callJudge(prompt);
    }

    private boolean callJudge(String prompt) {
        try {
            String raw = openClawService.chat(prompt, "eval-judge");
            raw = raw.trim();
            if (raw.startsWith("```")) {
                raw = raw.substring(raw.indexOf("\n") + 1);
                if (raw.endsWith("```")) raw = raw.substring(0, raw.lastIndexOf("```"));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> json = objectMapper.readValue(raw, Map.class);
            Object score = json.get("score");
            if (score instanceof Number n) return n.intValue() >= 1;
            if (score instanceof String s) return "1".equals(s) || "pass".equalsIgnoreCase(s);
        } catch (Exception e) {
            log.warn("Judge 解析失败: {}", e.getMessage());
        }
        return false;
    }

    // ---- 工具方法 ----

    private String generateAnswer(String query, List<String> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下参考资料回答问题。\n\n参考资料：\n");
        for (int i = 0; i < Math.min(contexts.size(), 3); i++) {
            sb.append("---\n").append(contexts.get(i)).append("\n");
        }
        sb.append("\n问题：").append(query).append("\n\n请直接回答：");
        try {
            return openClawService.chat(sb.toString(), "eval-gen");
        } catch (Exception e) {
            return "";
        }
    }

    List<EvalCase> loadDataset() {
        try (InputStream in = new ClassPathResource("rag-eval-dataset.json").getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<EvalCase>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n...(截断)";
    }
}
