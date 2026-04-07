package com.firedemo.demo.rag;

import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * 嵌入向量服务
 * 使用本地轻量级模型生成文本嵌入
 */
@Slf4j
@Service
public class EmbeddingService {

    // 使用轻量级中文模型
    private static final String MODEL_NAME = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2";
    private static final int MAX_LENGTH = 512;
    
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    @PostConstruct
    public void init() throws ModelNotFoundException, MalformedModelException, IOException {
        log.info("Loading embedding model: {}", MODEL_NAME);
        
        Criteria<String, float[]> criteria = Criteria.builder()
            .setTypes(String.class, float[].class)
            .optModelUrls("djl://ai.djl.huggingface.pytorch/" + MODEL_NAME)
            .optTranslator(new EmbeddingTranslator())
            .optEngine("PyTorch")
            .optProgress(new ProgressBar())
            .build();
        
        model = criteria.loadModel();
        predictor = model.newPredictor();
        
        log.info("Embedding model loaded successfully");
    }

    @PreDestroy
    public void destroy() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
    }

    /**
     * 单文本嵌入
     */
    public float[] embed(String text) {
        try {
            return predictor.predict(text);
        } catch (Exception e) {
            log.error("Failed to embed text", e);
            throw new RuntimeException("Embedding failed", e);
        }
    }

    /**
     * 批量嵌入（更高效）
     */
    public List<float[]> embedBatch(List<String> texts) {
        try {
            return predictor.batchPredict(texts);
        } catch (Exception e) {
            log.error("Failed to batch embed texts", e);
            throw new RuntimeException("Batch embedding failed", e);
        }
    }

    /**
     * 自定义Translator用于生成嵌入向量
     */
    private static class EmbeddingTranslator implements Translator<String, float[]> {
        
        private HuggingFaceTokenizer tokenizer;

        @Override
        public void prepare(TranslatorContext ctx) throws Exception {
            tokenizer = HuggingFaceTokenizer.builder()
                .optTokenizerName(MODEL_NAME)
                .optMaxLength(MAX_LENGTH)
                .optPadToMaxLength()
                .optTruncation(true)
                .build();
        }

        @Override
        public NDList processInput(TranslatorContext ctx, String input) throws Exception {
            NDManager manager = ctx.getNDManager();
            
            // Tokenize - DJL 0.28.0 API
            ai.djl.huggingface.tokenizers.Encoding encoding = tokenizer.encode(input);
            long[] inputIds = encoding.getIds();
            
            NDArray inputIdsArray = manager.create(inputIds).expandDims(0);
            
            return new NDList(inputIdsArray);
        }

        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) throws Exception {
            NDArray embeddings = list.singletonOrThrow();
            
            // 平均池化（mean pooling）
            embeddings = embeddings.mean(new int[]{1});
            
            // L2归一化
            embeddings = embeddings.normalize(2, 1);
            
            return embeddings.toFloatArray();
        }

        @Override
        public Batchifier getBatchifier() {
            return Batchifier.STACK;
        }
    }
}
