package com.graphrag.repository;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VectorRepository {

    private final InMemoryEmbeddingStore<TextSegment> store;

    public VectorRepository(InMemoryEmbeddingStore<TextSegment> store) {
        this.store = store;
    }

    public record ChunkSearchResult(String chunkId, String content, double score) {}

    public void store(String chunkId, String text, Embedding embedding,
                       String source, String domain) {
        Metadata metadata = new Metadata();
        metadata.put("chunkId", chunkId);
        metadata.put("source", source);
        metadata.put("domain", domain);
        TextSegment segment = TextSegment.from(text, metadata);
        store.add(embedding, segment);
    }

    public List<ChunkSearchResult> search(Embedding queryEmbedding, int topK) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .build();

        return store.search(request).matches().stream()
                .map(match -> new ChunkSearchResult(
                        match.embedded().metadata().getString("chunkId"),
                        match.embedded().text(),
                        match.score()))
                .toList();
    }

    public InMemoryEmbeddingStore<TextSegment> getStore() {
        return store;
    }
}
