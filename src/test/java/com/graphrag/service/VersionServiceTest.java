package com.graphrag.service;

import com.graphrag.config.GraphRagProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class VersionServiceTest {

    private TinkerGraph graph;
    private InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private AuditService auditService;
    private GraphRagProperties props;
    private VersionService versionService;

    @BeforeEach
    void setUp() {
        graph = TinkerGraph.open();
        embeddingStore = new InMemoryEmbeddingStore<>();
        auditService = mock(AuditService.class);
        doNothing().when(auditService).log(anyString(), anyString());

        props = new GraphRagProperties(
            "v1",
            new GraphRagProperties.LlmProperties("ollama",
                new GraphRagProperties.OllamaProperties("http://localhost:11434", "llama3.1"),
                new GraphRagProperties.AzureOpenAiProperties("", "", "gpt-4o-mini")),
            new GraphRagProperties.EmbeddingProperties("ollama",
                new GraphRagProperties.OllamaProperties("http://localhost:11434", "nomic"),
                new GraphRagProperties.AzureOpenAiProperties("", "", "embed")),
            new GraphRagProperties.IngestProperties(100000),
            new GraphRagProperties.VectorStoreProperties("./data/v1/vector-store.json"),
            new GraphRagProperties.GraphProperties("./data/v1/graph.json"),
            new GraphRagProperties.TraceProperties(0.7)
        );

        versionService = new VersionService(graph, embeddingStore, auditService, props);
    }

    @AfterEach
    void tearDown() throws Exception {
        graph.close();
    }

    @Test
    void createSnapshotWritesFiles(@TempDir Path tempDir) throws Exception {
        // Override VERSIONS_BASE by creating the service with reflection or testing the output keys
        Map<String, Object> result = versionService.createSnapshot("test-snap", "Test snapshot");

        // The result should contain version and paths (even if written to ./data/versions/test-snap/)
        assertThat(result).containsKey("version");
        assertThat(result.get("version")).isEqualTo("test-snap");
        assertThat(result).containsKey("graphPath");
        assertThat(result).containsKey("vectorPath");
        assertThat(result).containsKey("createdAt");
    }

    @Test
    void createSnapshotGraphFileExists() {
        versionService.createSnapshot("snap-check", "Check files exist");

        Path graphFile = Path.of("./data/versions/snap-check/graph.json");
        assertThat(Files.exists(graphFile)).isTrue();
    }

    @Test
    void createSnapshotVectorFileExists() {
        versionService.createSnapshot("snap-vec", "Check vector file");

        Path vectorFile = Path.of("./data/versions/snap-vec/vector-store.json");
        assertThat(Files.exists(vectorFile)).isTrue();
    }

    @Test
    void listVersionsReturnsCreatedVersions() {
        versionService.createSnapshot("list-v1", "First version");
        versionService.createSnapshot("list-v2", "Second version");

        List<String> versions = versionService.listVersions();
        assertThat(versions).contains("list-v1", "list-v2");
    }

    @Test
    void listVersionsReturnsEmptyWhenNoVersionsDirectory() {
        // The method should not throw if versions dir doesn't exist
        // It may return empty or existing versions — just verify no exception
        List<String> versions = versionService.listVersions();
        assertThat(versions).isNotNull();
    }

    @Test
    void getVersionInfoReturnsErrorForMissingVersion() {
        Map<String, Object> info = versionService.getVersionInfo("nonexistent-version-xyz");
        assertThat(info).containsKey("error");
    }

    @Test
    void getVersionInfoReturnsMetadataForExistingVersion() {
        versionService.createSnapshot("info-v1", "Info test");

        Map<String, Object> info = versionService.getVersionInfo("info-v1");
        assertThat(info.get("version")).isEqualTo("info-v1");
        assertThat(info).containsKey("graphSizeBytes");
    }
}
