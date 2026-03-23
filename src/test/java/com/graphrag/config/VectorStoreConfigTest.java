package com.graphrag.config;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
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

    @Test
    void loadsStoreFromExistingFile(@TempDir Path tempDir) {
        // Serialize a store to file first
        String storePath = tempDir.resolve("store.json").toString();
        InMemoryEmbeddingStore<TextSegment> sourceStore = new InMemoryEmbeddingStore<>();
        Metadata meta = new Metadata();
        meta.put("chunkId", "c1");
        sourceStore.add(Embedding.from(new float[]{0.1f, 0.2f, 0.3f}), TextSegment.from("hello", meta));
        sourceStore.serializeToFile(storePath);

        // Now load via config
        var config = new VectorStoreConfig();
        InMemoryEmbeddingStore<TextSegment> loaded = config.embeddingStore(storePath);

        assertThat(loaded).isNotNull();
        // Verify the loaded store has the data
        var results = loaded.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(new float[]{0.1f, 0.2f, 0.3f}))
                .maxResults(1)
                .build());
        assertThat(results.matches()).hasSize(1);
        assertThat(results.matches().get(0).embedded().text()).isEqualTo("hello");
    }
}
