package com.firedemo.edumind.knowledge.retrieval;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.translate.NoBatchifyTranslator;
import ai.djl.translate.TranslatorContext;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * ONNX 嵌入模型翻译器
 * <p>将文本转为 input_ids/attention_mask，推理后按 BGE 规范做 CLS pooling + L2 归一化。
 * 适配 bge-small-zh-v1.5 / bge-base-zh-v1.5 等 BERT 架构模型。
 */
@Slf4j
public class OnnxEmbeddingTranslator implements NoBatchifyTranslator<String, float[]> {

    private static final int MAX_LENGTH = 512;

    private final Path modelDir;
    private final ThreadLocal<HuggingFaceTokenizer> tokenizerHolder;

    public OnnxEmbeddingTranslator(Path modelDir) {
        this.modelDir = modelDir;
        tokenizerHolder = ThreadLocal.withInitial(() -> {
            try {
                if (Files.exists(this.modelDir.resolve("tokenizer.json"))) {
                    return HuggingFaceTokenizer.newInstance(this.modelDir,
                            Map.of("maxLength", String.valueOf(MAX_LENGTH)));
                }
                throw new IllegalStateException("tokenizer.json not found in " + this.modelDir);
            } catch (Exception exception) {
                log.warn("Failed to load tokenizer: {}", exception.getMessage());
                return null;
            }
        });
    }

    @Override
    public NDList processInput(TranslatorContext ctx, String input) {
        HuggingFaceTokenizer tk = tokenizerHolder.get();
        if (tk == null) {
            throw new IllegalStateException("Tokenizer not available");
        }

        Encoding encoding = tk.encode(input);
        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();

        NDManager manager = ctx.getNDManager();

        NDArray ids = manager.create(inputIds).reshape(1, inputIds.length);
        ids.setName("input_ids");

        NDArray mask = manager.create(attentionMask).reshape(1, attentionMask.length);
        mask.setName("attention_mask");

        // token_type_ids: 全零（单序列，无 segment 区分）
        NDArray typeIds = manager.zeros(ids.getShape(), ids.getDataType());
        typeIds.setName("token_type_ids");

        return new NDList(ids, mask, typeIds);
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        // ONNX output: last_hidden_state [1, seq_len, hidden_size].
        NDArray hidden = list.get(0);
        NDArray clsEmbedding = hidden.get(new NDIndex(":, 0"));
        float[] vec = clsEmbedding.toFloatArray();
        return l2Normalize(vec);
    }

    /**
     * L2 归一化
     */
    static float[] l2Normalize(float[] vec) {
        double norm = 0.0;
        for (float v : vec) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);
        if (norm < 1e-10) return vec;

        float[] result = new float[vec.length];
        for (int i = 0; i < vec.length; i++) {
            result[i] = (float) (vec[i] / norm);
        }
        return result;
    }
}
