package com.graphrag.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmConfigTest {

    @Test
    void throwsForUnknownProvider() {
        var props = new GraphRagProperties(
            "v1",
            new GraphRagProperties.LlmProperties("unknown",
                new GraphRagProperties.OllamaProperties("http://localhost:11434", "llama3.1"),
                new GraphRagProperties.AzureOpenAiProperties("endpoint", "key", "deploy")),
            new GraphRagProperties.EmbeddingProperties("ollama",
                new GraphRagProperties.OllamaProperties("http://localhost:11434", "nomic"),
                new GraphRagProperties.AzureOpenAiProperties("endpoint", "key", "deploy")),
            new GraphRagProperties.IngestProperties(100000),
            new GraphRagProperties.VectorStoreProperties("./data/v.json"),
            new GraphRagProperties.GraphProperties("./data/g.json"),
            new GraphRagProperties.TraceProperties(0.7)
        );

        var config = new LlmConfig();
        assertThatThrownBy(() -> config.chatLanguageModel(props))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown LLM provider");
    }
}
