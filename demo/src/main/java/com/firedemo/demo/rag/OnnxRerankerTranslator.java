package com.firedemo.demo.rag;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.NoBatchifyTranslator;
import ai.djl.translate.TranslatorContext;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

/**
 * bge-reranker-base ONNX Cross-Encoder 翻译器
 * <p>
 * 输入：query + document 拼接 → [CLS] query [SEP] document [SEP]
 * 输出：[CLS] 位置的 logit → sigmoid → 0~1 相关性分数
 * <p>
 * 与 {@link OnnxEmbeddingTranslator} 的关键区别：
 * <ul>
 *   <li>Embedding 是 Bi-Encoder：query/doc 分别编码 → mean pooling</li>
 *   <li>Reranker 是 Cross-Encoder：query+doc 拼接编码 → [CLS] token → sigmoid</li>
 * </ul>
 */
@Slf4j
public class OnnxRerankerTranslator implements NoBatchifyTranslator<QueryDocPair, float[]> {

    private static final int MAX_LENGTH = 512;

    private final HuggingFaceTokenizer tokenizer;

    public OnnxRerankerTranslator(Path modelDir) {
        try {
            this.tokenizer = HuggingFaceTokenizer.newInstance(
                    modelDir,
                    java.util.Map.of("maxLength", String.valueOf(MAX_LENGTH)));
            log.info("Reranker tokenizer loaded from {}", modelDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load reranker tokenizer from " + modelDir, e);
        }
    }

    @Override
    public NDList processInput(TranslatorContext ctx, QueryDocPair input) {
        // Cross-Encoder: query 和 document 拼接为一个序列
        // tokenizer.encode(a, b) 自动插入 [SEP] 分隔符
        String query = input.query() != null ? input.query() : "";
        String document = input.document() != null ? input.document() : "";

        Encoding encoding;
        try {
            encoding = tokenizer.encode(query, document);
        } catch (Exception e) {
            // pair encode 失败时回退为手动拼接
            String combined = query + " [SEP] " + document;
            encoding = tokenizer.encode(combined);
        }

        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();

        NDManager manager = ctx.getNDManager();
        NDArray ids = manager.create(inputIds).reshape(1, inputIds.length);
        ids.setName("input_ids");

        NDArray mask = manager.create(attentionMask).reshape(1, attentionMask.length);
        mask.setName("attention_mask");

        return new NDList(ids, mask);
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        // ONNX 输出: logits [batch=1, num_labels=1]
        NDArray logits = list.get(0);
        float logit = logits.getFloat(0);

        // sigmoid → 0~1 相关性分数
        float score = 1.0f / (1.0f + (float) Math.exp(-logit));
        return new float[]{score};
    }
}
