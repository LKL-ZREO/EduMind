package com.firedemo.demo.rag;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.NoBatchifyTranslator;
import ai.djl.translate.TranslatorContext;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * ONNX 嵌入模型翻译器
 * <p>将文本转为 input_ids/attention_mask，推理后做 mean pooling + L2 归一化。
 * 适配 bge-small-zh-v1.5 / bge-base-zh-v1.5 等 BERT 架构模型。
 */
@Slf4j
public class OnnxEmbeddingTranslator implements NoBatchifyTranslator<String, float[]> {

    private static final String MODEL_NAME = "BAAI/bge-small-zh-v1.5";
    private static final String LOCAL_DIR_NAME = MODEL_NAME.replace("/", "_");
    private static final int MAX_LENGTH = 512;

    private final ThreadLocal<HuggingFaceTokenizer> tokenizerHolder = ThreadLocal.withInitial(() -> {
        try {
            Path localPath = Path.of(System.getProperty("user.home"), ".djl", "models", LOCAL_DIR_NAME);
            if (Files.exists(localPath.resolve("tokenizer.json"))) {
                return HuggingFaceTokenizer.newInstance(localPath,
                        Map.of("maxLength", String.valueOf(MAX_LENGTH)));
            }
            // 本地没有则尝试在线加载
            System.setProperty("HF_ENDPOINT", "https://hf-mirror.com");
            return HuggingFaceTokenizer.newInstance(MODEL_NAME,
                    Map.of("maxLength", String.valueOf(MAX_LENGTH)));
        } catch (Exception e) {
            log.warn("Failed to load tokenizer: {}", e.getMessage());
            return null;
        }
    });

    // 用于跨 processInput/processOutput 传递 attention_mask
    private static final String ATTACHMENT_MASK = "attention_mask";

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

        // 存入 context，供 processOutput 使用
        ctx.setAttachment(ATTACHMENT_MASK, mask);

        return new NDList(ids, mask);
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        // ONNX 输出: last_hidden_state [1, seq_len, 768] (bge-small 是 512-dim，bge-base 是 768-dim)
        NDArray hidden = list.get(0);
        NDArray mask = (NDArray) ctx.getAttachment(ATTACHMENT_MASK);

        // Mean pooling with attention mask
        // masked = hidden * mask[:, :, None]
        NDArray maskExpanded = mask.expandDims(-1);       // [1, seq_len, 1]
        NDArray masked = hidden.mul(maskExpanded);         // [1, seq_len, hidden_size]
        NDArray summed = masked.sum(new int[]{1});         // [1, hidden_size]
        NDArray maskSum = mask.sum(new int[]{1});          // [1]
        // avoid division by zero on empty mask
        maskSum = maskSum.clip(1e-9, Float.MAX_VALUE);
        NDArray pooled = summed.div(maskSum.expandDims(-1)); // [1, hidden_size]

        float[] vec = pooled.toFloatArray();
        return l2Normalize(vec);
    }

    /**
     * L2 归一化
     */
    private float[] l2Normalize(float[] vec) {
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
