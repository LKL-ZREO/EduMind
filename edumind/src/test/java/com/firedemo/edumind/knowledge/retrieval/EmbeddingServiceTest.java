package com.firedemo.edumind.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class EmbeddingServiceTest {

    @Test
    void buildsModelFileUriWithNormalizedBaseUrl() {
        URI uri = EmbeddingService.modelFileUri(
                "https://huggingface.co/",
                "Xenova/bge-small-zh-v1.5",
                "main",
                "onnx/model.onnx");

        assertThat(uri).hasToString(
                "https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/main/onnx/model.onnx");
    }

    @Test
    void mapsRemoteOnnxSubdirectoryToLocalModelName() {
        assertThat(EmbeddingService.modelFiles("onnx/model.onnx"))
                .extracting(EmbeddingService.ModelFile::remotePath, EmbeddingService.ModelFile::localName)
                .containsExactly(
                        tuple("onnx/model.onnx", "model.onnx"),
                        tuple("tokenizer.json", "tokenizer.json"));
    }

    @Test
    void normalizesEmbeddingForCosineSearch() {
        float[] normalized = OnnxEmbeddingTranslator.l2Normalize(new float[]{3, 4});

        assertThat(normalized).containsExactly(0.6f, 0.8f);
    }
}
