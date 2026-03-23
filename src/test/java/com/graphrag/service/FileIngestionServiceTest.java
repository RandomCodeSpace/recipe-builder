package com.graphrag.service;

import com.graphrag.model.ExtractionResult;
import com.graphrag.repository.GraphRepository;
import com.graphrag.repository.VectorRepository;
import com.graphrag.service.EntityNormalizer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FileIngestionServiceTest {

    private TinkerGraph graph;
    private GraphGenerationService graphGenerationService;
    private FileIngestionService fileIngestionService;

    @BeforeEach
    void setUp() {
        graph = TinkerGraph.open();
        GraphTraversalSource g = traversal().withEmbedded(graph);
        GraphRepository graphRepo = new GraphRepository(g, new EntityNormalizer(), 0.85);
        VectorRepository vectorRepo = new VectorRepository(new InMemoryEmbeddingStore<>());
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        float[] dummyVector = new float[384];
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(dummyVector)));

        graphGenerationService = new GraphGenerationService(
                graphRepo, vectorRepo, embeddingModel, new TextChunker(), null, 100000);
        fileIngestionService = new FileIngestionService(graphGenerationService);
    }

    @AfterEach
    void tearDown() throws Exception {
        graph.close();
    }

    // Helper to build in-memory ZIP files for testing
    private MockMultipartFile createZipFile(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (var entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return new MockMultipartFile("file", "test.zip", "application/zip", baos.toByteArray());
    }

    @Test
    void ingestSingleTextFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain",
                "Hello world. This is a test document.".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = fileIngestionService.ingestFile(file, "document");

        assertThat(result).doesNotContainKey("error");
        assertThat(result).containsKey("chunks_created");
        assertThat(((Number) result.get("chunks_created")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void ingestZipWithMultipleFiles() throws Exception {
        MockMultipartFile zip = createZipFile(Map.of(
                "src/Main.java", "public class Main { public static void main(String[] args) {} }",
                "src/Helper.java", "public class Helper { public void help() {} }"
        ));

        Map<String, Object> result = fileIngestionService.ingestFile(zip, "codebase");

        assertThat(result).doesNotContainKey("error");
        assertThat(result).containsKey("files_processed");
        assertThat(((Number) result.get("files_processed")).intValue()).isEqualTo(2);
    }

    @Test
    void ingestZipSkipsBinaryFiles() throws Exception {
        MockMultipartFile zip = createZipFile(Map.of(
                "Main.class", "\u00CA\u00FE\u00BA\u00BE binary class data",
                "README.md", "# My Project\n\nThis is documentation."
        ));

        Map<String, Object> result = fileIngestionService.ingestFile(zip, "document");

        assertThat(result).doesNotContainKey("error");
        assertThat(((Number) result.get("files_skipped")).intValue()).isEqualTo(1);
        assertThat(((Number) result.get("files_processed")).intValue()).isEqualTo(1);
    }

    @Test
    void ingestZipSkipsDirectories() throws Exception {
        // Build ZIP with a directory entry and one real file
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Directory entry
            ZipEntry dir = new ZipEntry("src/");
            zos.putNextEntry(dir);
            zos.closeEntry();
            // File entry
            zos.putNextEntry(new ZipEntry("src/App.java"));
            zos.write("public class App {}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        MockMultipartFile zip = new MockMultipartFile("file", "test.zip", "application/zip", baos.toByteArray());

        Map<String, Object> result = fileIngestionService.ingestFile(zip, "codebase");

        assertThat(result).doesNotContainKey("error");
        // Directory was ignored; the one java file was processed
        assertThat(((Number) result.get("files_processed")).intValue()).isEqualTo(1);
    }

    @Test
    void ingestEmptyFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        Map<String, Object> result = fileIngestionService.ingestFile(file, "document");

        // Empty file should be handled gracefully — either 0 chunks or an error key
        assertThat(result).isNotNull();
    }

    @Test
    void ingestZipWithMixedContent() throws Exception {
        MockMultipartFile zip = createZipFile(Map.of(
                "src/Main.java", "public class Main {}",
                "Main.class", "\u00CA\u00FE\u00BA\u00BE binary",
                "docs/README.txt", "This is documentation text."
        ));

        Map<String, Object> result = fileIngestionService.ingestFile(zip, "codebase");

        assertThat(result).doesNotContainKey("error");
        // .java and .txt are supported; .class is not
        assertThat(((Number) result.get("files_processed")).intValue()).isEqualTo(2);
        assertThat(((Number) result.get("files_skipped")).intValue()).isEqualTo(1);
    }
}
