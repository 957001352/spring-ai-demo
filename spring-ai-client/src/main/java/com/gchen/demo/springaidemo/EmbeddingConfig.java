package com.gchen.demo.springaidemo;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Bean
    public OllamaApi ollamaApi() {
        return OllamaApi.builder().baseUrl("http://localhost:11434").build();// Ollama服务URL
    }

    @Bean
    public EmbeddingModel embeddingModel(OllamaApi ollamaApi) {
        return OllamaEmbeddingModel.builder().ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder().model("qwen3-embedding").build()).build();
    }
}

