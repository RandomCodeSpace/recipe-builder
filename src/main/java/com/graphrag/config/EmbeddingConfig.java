package com.graphrag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel(GraphRagProperties props) {
        return switch (props.embedding().provider()) {
            case "ollama" -> OllamaEmbeddingModel.builder()
                    .baseUrl(props.embedding().ollama().baseUrl())
                    .modelName(props.embedding().ollama().modelName())
                    .build();
            case "azure-openai" -> AzureOpenAiEmbeddingModel.builder()
                    .endpoint(props.embedding().azureOpenai().endpoint())
                    .apiKey(props.embedding().azureOpenai().apiKey())
                    .deploymentName(props.embedding().azureOpenai().deploymentName())
                    .build();
            default -> throw new IllegalArgumentException(
                    "Unknown embedding provider: " + props.embedding().provider());
        };
    }
}
