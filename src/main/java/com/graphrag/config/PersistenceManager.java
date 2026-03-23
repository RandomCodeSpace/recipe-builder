package com.graphrag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PreDestroy;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.tinkerpop.gremlin.structure.io.IoCore.graphson;

@Component
public class PersistenceManager {

    private static final Logger log = LoggerFactory.getLogger(PersistenceManager.class);

    private final TinkerGraph graph;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final String graphPath;
    private final String vectorStorePath;

    public PersistenceManager(
            TinkerGraph graph,
            InMemoryEmbeddingStore<TextSegment> embeddingStore,
            @Value("${graphrag.graph.persistence-path}") String graphPath,
            @Value("${graphrag.vector-store.persistence-path}") String vectorStorePath) {
        this.graph = graph;
        this.embeddingStore = embeddingStore;
        this.graphPath = graphPath;
        this.vectorStorePath = vectorStorePath;
    }

    @PreDestroy
    public void persistAll() {
        persistGraph();
        persistVectorStore();
    }

    private void persistGraph() {
        try {
            Path parent = Path.of(graphPath).getParent();
            if (parent != null) Files.createDirectories(parent);
            graph.io(graphson()).writeGraph(graphPath);
            log.info("Graph persisted to {}", graphPath);
        } catch (IOException e) {
            log.error("Failed to persist graph to {}", graphPath, e);
        }
    }

    private void persistVectorStore() {
        try {
            Path parent = Path.of(vectorStorePath).getParent();
            if (parent != null) Files.createDirectories(parent);
            embeddingStore.serializeToFile(vectorStorePath);
            log.info("Vector store persisted to {}", vectorStorePath);
        } catch (Exception e) {
            log.error("Failed to persist vector store to {}", vectorStorePath, e);
        }
    }
}
