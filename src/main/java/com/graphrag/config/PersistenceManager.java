package com.graphrag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PreDestroy;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
    public synchronized void persistAll() {
        persistGraph();
        persistVectorStore();
    }

    @Scheduled(fixedRateString = "${graphrag.persistence.interval-ms:300000}")
    public void autoSave() {
        log.debug("Auto-save triggered");
        persistAll();
    }

    public void persistGraph() {
        Path target = Path.of(graphPath);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            graph.io(graphson()).writeGraph(tmp.toString());
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("Graph persisted to {}", graphPath);
        } catch (IOException e) {
            log.error("Failed to persist graph to {}", graphPath, e);
            deleteSilently(tmp);
        }
    }

    public void persistVectorStore() {
        Path target = Path.of(vectorStorePath);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            embeddingStore.serializeToFile(tmp.toString());
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("Vector store persisted to {}", vectorStorePath);
        } catch (Exception e) {
            log.error("Failed to persist vector store to {}", vectorStorePath, e);
            deleteSilently(tmp);
        }
    }

    private static void deleteSilently(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
