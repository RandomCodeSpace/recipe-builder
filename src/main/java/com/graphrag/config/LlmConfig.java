package com.graphrag.config;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class LlmConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(GraphRagProperties props) {
        return switch (props.llm().provider()) {
            case "ollama" -> OllamaChatModel.builder()
                    .baseUrl(props.llm().ollama().baseUrl())
                    .modelName(props.llm().ollama().modelName())
                    .supportedCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            case "azure-openai" -> AzureOpenAiChatModel.builder()
                    .endpoint(props.llm().azureOpenai().endpoint())
                    .apiKey(props.llm().azureOpenai().apiKey())
                    .deploymentName(props.llm().azureOpenai().deploymentName())
                    .supportedCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
                    .strictJsonSchema(true)
                    .logRequestsAndResponses(true)
                    .build();
            default -> throw new IllegalArgumentException(
                    "Unknown LLM provider: " + props.llm().provider());
        };
    }
}
