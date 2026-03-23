package com.graphrag.repository;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class VectorRepositoryTest {

    private VectorRepository repo;

    @BeforeEach
    void setUp() {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        repo = new VectorRepository(store);
    }

    @Test
    void storeAndRetrieveChunk() {
        float[] vector = new float[]{0.1f, 0.2f, 0.3f};
        Embedding embedding = Embedding.from(vector);
        repo.store("chunk-1", "Hello world", embedding, "test.txt", "document");

        var results = repo.search(embedding, 5);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).chunkId()).isEqualTo("chunk-1");
    }

    @Test
    void searchReturnsTopK() {
        for (int i = 0; i < 10; i++) {
            float[] vector = new float[]{(float) i / 10, 0.5f, 0.5f};
            repo.store("chunk-" + i, "text " + i, Embedding.from(vector), "test.txt", "document");
        }
        float[] query = new float[]{0.9f, 0.5f, 0.5f};
        var results = repo.search(Embedding.from(query), 3);
        assertThat(results).hasSize(3);
    }

    @Test
    void searchEmptyStoreReturnsEmpty() {
        float[] query = new float[]{0.1f, 0.2f, 0.3f};
        var results = repo.search(Embedding.from(query), 5);
        assertThat(results).isEmpty();
    }
}
