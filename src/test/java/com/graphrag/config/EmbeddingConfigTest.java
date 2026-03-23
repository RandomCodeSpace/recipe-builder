package com.graphrag.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingConfigTest {

    @Test
    void throwsForUnknownProvider() {
        var props = new GraphRagProperties(
            "v1",
            new GraphRagProperties.LlmProperties("ollama",
                new GraphRagProperties.OllamaProperties("http://localhost:11434", "llama3.1"),
                new GraphRagProperties.AzureOpenAiProperties("endpoint", "key", "deploy")),
            new GraphRagProperties.EmbeddingProperties("unknown",
                new GraphRagProperties.OllamaProperties("http://localhost:11434", "nomic"),
                new GraphRagProperties.AzureOpenAiProperties("endpoint", "key", "deploy")),
            new GraphRagProperties.IngestProperties(100000),
            new GraphRagProperties.VectorStoreProperties("./data/v.json"),
            new GraphRagProperties.GraphProperties("./data/g.json"),
            new GraphRagProperties.TraceProperties(0.7)
        );

        var config = new EmbeddingConfig();
        assertThatThrownBy(() -> config.embeddingModel(props))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown embedding provider");
    }
}
