package com.graphrag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreConfigTest {

    @Test
    void createsEmptyStoreWhenNoFileExists(@TempDir Path tempDir) {
        String path = tempDir.resolve("nonexistent.json").toString();
        var config = new VectorStoreConfig();
        InMemoryEmbeddingStore<TextSegment> store = config.embeddingStore(path);
        assertThat(store).isNotNull();
    }
}
