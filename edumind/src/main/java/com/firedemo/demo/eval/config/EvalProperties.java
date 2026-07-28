package com.firedemo.demo.eval.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 评估服务配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "edumind.eval")
public class EvalProperties {

    /** 检索 Top-K 默认值 */
    private int defaultTopK = 5;

    /** 是否默认启用 Reranker */
    private boolean defaultEnableReranker = true;

    /** Labels and model identifiers persisted with each experiment. */
    private String datasetVersion = "rag-eval-v1";
    private String gitCommit = "unknown";
    private String embeddingModel = "bge-small-zh-v1.5";
    private String rerankerModel = "bge-reranker-base";

    /** Default normalized quality gates (0..1). */
    private double minKeywordRecall = 0.30;
    private double minContentCoverage = 0.30;
    private double minHitRate = 0.60;
    private double minMrr = 0.20;
    private double minNdcg = 0.50;
    private double minFaithfulness = 0.70;
    private double minAnswerRelevancy = 0.70;
}
