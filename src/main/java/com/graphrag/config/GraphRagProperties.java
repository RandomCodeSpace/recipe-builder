package com.graphrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "graphrag")
public record GraphRagProperties(
    String version,
    LlmProperties llm,
    EmbeddingProperties embedding,
    IngestProperties ingest,
    VectorStoreProperties vectorStore,
    GraphProperties graph,
    TraceProperties trace
) {
    public record LlmProperties(String provider, OllamaProperties ollama, AzureOpenAiProperties azureOpenai) {}
    public record EmbeddingProperties(String provider, OllamaProperties ollama, AzureOpenAiProperties azureOpenai) {}
    public record OllamaProperties(String baseUrl, String modelName) {}
    public record AzureOpenAiProperties(String endpoint, String apiKey, String deploymentName) {}
    public record IngestProperties(int maxTextLength) {}
    public record VectorStoreProperties(String persistencePath) {}
    public record GraphProperties(String persistencePath) {}
    public record TraceProperties(double similarityThreshold) {}
}
