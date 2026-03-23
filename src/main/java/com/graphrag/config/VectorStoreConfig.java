package com.graphrag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class VectorStoreConfig {

    @Bean
    public InMemoryEmbeddingStore<TextSegment> embeddingStore(
            @Value("${graphrag.vector-store.persistence-path}") String persistencePath) {
        Path path = Path.of(persistencePath);
        if (Files.exists(path)) {
            return InMemoryEmbeddingStore.fromFile(path.toString());
        }
        return new InMemoryEmbeddingStore<>();
    }
}
