package com.graphrag.config;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.apache.tinkerpop.gremlin.structure.io.IoCore.graphson;
import static org.assertj.core.api.Assertions.assertThat;

class PersistenceManagerSchedulingTest {

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
    void autoSaveCallsPersistAll(@TempDir Path tempDir) {
        graph.addVertex("ingredient");
        Metadata meta = new Metadata();
        meta.put("chunkId", "c1");
        store.add(Embedding.from(new float[]{0.1f, 0.2f}), TextSegment.from("pasta", meta));

        String graphPath = tempDir.resolve("graph.json").toString();
        String vectorPath = tempDir.resolve("vector.json").toString();
        var pm = new PersistenceManager(graph, store, graphPath, vectorPath);

        pm.autoSave();

        assertThat(Files.exists(Path.of(graphPath))).isTrue();
        assertThat(Files.exists(Path.of(vectorPath))).isTrue();
    }

    @Test
    void atomicWriteProducesValidGraphFile(@TempDir Path tempDir) throws Exception {
        graph.addVertex("recipe");
        graph.addVertex("ingredient");

        String graphPath = tempDir.resolve("graph.json").toString();
        String vectorPath = tempDir.resolve("vector.json").toString();
        var pm = new PersistenceManager(graph, store, graphPath, vectorPath);

        pm.persistGraph();

        assertThat(Files.exists(Path.of(graphPath))).isTrue();
        // Verify no leftover .tmp file
        assertThat(Files.exists(Path.of(graphPath + ".tmp"))).isFalse();

        // Verify file can be read back by TinkerGraph
        TinkerGraph reloaded = TinkerGraph.open();
        try {
            reloaded.io(graphson()).readGraph(graphPath);
            long vertexCount = reloaded.traversal().V().count().next();
            assertThat(vertexCount).isEqualTo(2);
        } finally {
            reloaded.close();
        }
    }

    @Test
    void atomicWriteProducesValidVectorStoreFile(@TempDir Path tempDir) {
        Metadata meta = new Metadata();
        meta.put("chunkId", "c42");
        store.add(Embedding.from(new float[]{0.5f, 0.6f, 0.7f}), TextSegment.from("tomato sauce", meta));

        String graphPath = tempDir.resolve("graph.json").toString();
        String vectorPath = tempDir.resolve("vector.json").toString();
        var pm = new PersistenceManager(graph, store, graphPath, vectorPath);

        pm.persistVectorStore();

        assertThat(Files.exists(Path.of(vectorPath))).isTrue();
        // Verify no leftover .tmp file
        assertThat(Files.exists(Path.of(vectorPath + ".tmp"))).isFalse();

        // Verify file can be deserialized back by InMemoryEmbeddingStore
        InMemoryEmbeddingStore<TextSegment> reloaded =
                InMemoryEmbeddingStore.fromFile(vectorPath);
        assertThat(reloaded).isNotNull();
        // A non-null store that was loaded means the file is valid JSON
    }

    @Test
    void persistSurvivesConcurrentAutoSave(@TempDir Path tempDir) throws Exception {
        graph.addVertex("concurrent-node");
        Metadata meta = new Metadata();
        meta.put("chunkId", "c-concurrent");
        store.add(Embedding.from(new float[]{0.9f}), TextSegment.from("concurrent", meta));

        String graphPath = tempDir.resolve("graph.json").toString();
        String vectorPath = tempDir.resolve("vector.json").toString();
        var pm = new PersistenceManager(graph, store, graphPath, vectorPath);

        int threadCount = 2;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                pm.autoSave();
            }));
        }

        startLatch.countDown();
        for (Future<?> f : futures) {
            f.get(); // propagates any exception from the thread
        }
        executor.shutdown();

        // Both files must exist and be non-empty after concurrent saves
        assertThat(Files.exists(Path.of(graphPath))).isTrue();
        assertThat(Files.size(Path.of(graphPath))).isGreaterThan(0);
        assertThat(Files.exists(Path.of(vectorPath))).isTrue();
        assertThat(Files.size(Path.of(vectorPath))).isGreaterThan(0);

        // No stray .tmp files
        assertThat(Files.exists(Path.of(graphPath + ".tmp"))).isFalse();
        assertThat(Files.exists(Path.of(vectorPath + ".tmp"))).isFalse();
    }
}
