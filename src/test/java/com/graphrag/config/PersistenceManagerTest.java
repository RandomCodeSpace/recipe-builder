package com.graphrag.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class PersistenceManagerTest {

    private TinkerGraph graph;
    private InMemoryEmbeddingStore<TextSegment> store;

    @BeforeEach
    void setUp() {
        graph = TinkerGraph.open();
        store = new InMemoryEmbeddingStore<>();
    }

    @AfterEach
    void tearDown() throws Exception {
        graph.close();
    }

    @Test
    void persistAllWritesGraphFile(@TempDir Path tempDir) {
        graph.addVertex("test");
        String graphPath = tempDir.resolve("graph.json").toString();
        String vectorPath = tempDir.resolve("vector.json").toString();
        var pm = new PersistenceManager(graph, store, graphPath, vectorPath);
        pm.persistAll();
        assertThat(Files.exists(Path.of(graphPath))).isTrue();
    }

    @Test
    void persistAllWritesVectorStoreFile(@TempDir Path tempDir) {
        Metadata meta = new Metadata();
        meta.put("chunkId", "c1");
        store.add(Embedding.from(new float[]{0.1f, 0.2f}), TextSegment.from("test", meta));
        String graphPath = tempDir.resolve("graph.json").toString();
        String vectorPath = tempDir.resolve("vector.json").toString();
        var pm = new PersistenceManager(graph, store, graphPath, vectorPath);
        pm.persistAll();
        assertThat(Files.exists(Path.of(vectorPath))).isTrue();
    }

    @Test
    void persistAllCreatesParentDirectories(@TempDir Path tempDir) {
        String graphPath = tempDir.resolve("sub/dir/graph.json").toString();
        String vectorPath = tempDir.resolve("sub/dir/vector.json").toString();
        var pm = new PersistenceManager(graph, store, graphPath, vectorPath);
        pm.persistAll();
        assertThat(Files.exists(Path.of(graphPath))).isTrue();
    }

    @Test
    void persistAllHandlesErrorGracefully() {
        // Invalid path — should log error, not throw
        var pm = new PersistenceManager(graph, store,
                "/dev/null/impossible/graph.json",
                "/dev/null/impossible/vector.json");
        assertThatNoException().isThrownBy(pm::persistAll);
    }
}
