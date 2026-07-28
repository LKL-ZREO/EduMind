package com.firedemo.demo.eval.service;

import com.firedemo.demo.eval.model.EvalCase;
import com.firedemo.demo.rag.RrfFusionService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Deterministic retrieval metrics, independent from LLM-as-Judge. */
@Component
public class RetrievalMetricsCalculator {

    public Metrics calculate(EvalCase evalCase, List<RrfFusionService.ScoredChunk> results) {
        List<RrfFusionService.ScoredChunk> safeResults = results == null ? List.of() : results;
        String combined = safeResults.stream()
                .map(result -> result.chunk().getContent())
                .collect(Collectors.joining(" "));

        double keywordRecall = coverage(combined, evalCase.getExpectedKeywords(), false);
        double contentCoverage = coverage(combined, evalCase.getExpectedContent(), true);

        List<Double> relevance = new ArrayList<>();
        double reciprocalRank = 0;
        for (int rank = 0; rank < safeResults.size(); rank++) {
            RrfFusionService.ScoredChunk result = safeResults.get(rank);
            double score = relevance(evalCase, result);
            relevance.add(score);
            if (reciprocalRank == 0 && score > 0) reciprocalRank = 1.0 / (rank + 1);
        }

        double ndcg = ndcg(relevance);
        return new Metrics(keywordRecall, contentCoverage, reciprocalRank, ndcg, reciprocalRank > 0);
    }

    private double relevance(EvalCase evalCase, RrfFusionService.ScoredChunk result) {
        if (evalCase.getExpectedContent() != null && !evalCase.getExpectedContent().isEmpty()) {
            long matches = evalCase.getExpectedContent().stream()
                    .filter(expected -> containsFuzzy(result.chunk().getContent(), expected))
                    .count();
            return (double) matches / evalCase.getExpectedContent().size();
        }
        if (hasText(evalCase.getSourceDocId())) {
            return evalCase.getSourceDocId().equals(result.chunk().getDocumentId()) ? 1.0 : 0.0;
        }
        return 0.0;
    }

    private double coverage(String text, List<String> expected, boolean fuzzy) {
        if (expected == null || expected.isEmpty()) return -1;
        long hits = expected.stream()
                .filter(value -> fuzzy
                        ? containsFuzzy(text, value)
                        : text.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT)))
                .count();
        return (double) hits / expected.size();
    }

    private double ndcg(List<Double> relevance) {
        if (relevance.isEmpty()) return 0;
        double dcg = dcg(relevance);
        List<Double> ideal = relevance.stream().sorted(Comparator.reverseOrder()).toList();
        double idealDcg = dcg(ideal);
        return idealDcg == 0 ? 0 : dcg / idealDcg;
    }

    private double dcg(List<Double> relevance) {
        double score = 0;
        for (int i = 0; i < relevance.size(); i++) {
            score += relevance.get(i) / (Math.log(i + 2) / Math.log(2));
        }
        return score;
    }

    static boolean containsFuzzy(String text, String expected) {
        if (!hasText(text) || !hasText(expected)) return false;
        String cleanText = normalize(text);
        String cleanExpected = normalize(expected);
        if (cleanExpected.length() <= 4) return cleanText.contains(cleanExpected);
        return (double) lcsLength(cleanText, cleanExpected) / cleanExpected.length() >= 0.5;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s，。！？、\"'（）《》]", "");
    }

    private static int lcsLength(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            for (int j = 1; j <= right.length(); j++) {
                current[j] = left.charAt(i - 1) == right.charAt(j - 1)
                        ? previous[j - 1] + 1
                        : Math.max(previous[j], current[j - 1]);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Metrics(
            double keywordRecall,
            double contentCoverage,
            double reciprocalRank,
            double ndcg,
            boolean hit
    ) {}
}
