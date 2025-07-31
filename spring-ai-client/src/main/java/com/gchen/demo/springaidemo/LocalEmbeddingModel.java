package com.gchen.demo.springaidemo;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.stereotype.Component;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LocalEmbeddingModel implements EmbeddingModel {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of("texts", texts);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "http://localhost:8001/embed", // 你的本地 Python 服务地址
                HttpMethod.POST,
                entity,
                Map.class
        );

        List<List<Number>> vectors = (List<List<Number>>) response.getBody().get("vectors");

        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            float[] floatArray = new float[vectors.get(i).size()];
            for (int j = 0; j < vectors.get(i).size(); j++) {
                floatArray[j] = vectors.get(i).get(j).floatValue(); // 注意可能精度损失
            }
            embeddings.add(new Embedding(floatArray, i));
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(String text) {
        return EmbeddingModel.super.embed(text);
    }

    @Override
    public float[] embed(Document document) {
        // 1. 构建 EmbeddingRequest
        EmbeddingOptions options = new EmbeddingOptions() {
            @Override
            public String getModel() {
                return "BAAI/bge-base-zh";
            }

            @Override
            public Integer getDimensions() {
                return 5;
            }
        };
        EmbeddingRequest request = new EmbeddingRequest(List.of(document.getFormattedContent()),options);

        // 2. 调用嵌入服务
        EmbeddingResponse response = this.call(request);  // 你自己注入的 client

        // 3. 取第一个向量（单个 Document 的情况下就一个向量）
        List<Embedding> results = response.getResults();
        if (results == null || results.isEmpty()) {
            return new float[0];  // 或者抛异常
        }

        return results.get(0).getOutput();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return EmbeddingModel.super.embed(texts);
    }

    @Override
    public List<float[]> embed(List<Document> documents, EmbeddingOptions options, BatchingStrategy batchingStrategy) {
        return EmbeddingModel.super.embed(documents, options, batchingStrategy);
    }

    @Override
    public EmbeddingResponse embedForResponse(List<String> texts) {
        return EmbeddingModel.super.embedForResponse(texts);
    }

    @Override
    public int dimensions() {
        return EmbeddingModel.super.dimensions();
    }
}

