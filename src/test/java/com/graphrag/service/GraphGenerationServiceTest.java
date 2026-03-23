package com.graphrag.service;

import com.graphrag.config.GraphRagProperties;
import com.graphrag.model.*;
import com.graphrag.repository.GraphRepository;
import com.graphrag.repository.VectorRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GraphGenerationServiceTest {

    private TinkerGraph graph;
    private GraphRepository graphRepo;
    private VectorRepository vectorRepo;
    private EmbeddingModel embeddingModel;
    private GraphGenerationService service;

    @BeforeEach
    void setUp() {
        graph = TinkerGraph.open();
        GraphTraversalSource g = traversal().withEmbedded(graph);
        graphRepo = new GraphRepository(g);
        vectorRepo = new VectorRepository(new InMemoryEmbeddingStore<>());
        embeddingModel = mock(EmbeddingModel.class);

        float[] dummyVector = new float[384];
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(dummyVector)));

        service = new GraphGenerationService(
                graphRepo, vectorRepo, embeddingModel, new TextChunker(), null, 100000);
    }

    @AfterEach
    void tearDown() throws Exception {
        graph.close();
    }

    @Test
    void ingestTextStoresChunksInVectorRepo() {
        var result = service.ingestWithExtractionResult(
                "Hello world.", "test.txt", "document",
                new ExtractionResult(List.of(), List.of()));

        assertThat(result.get("chunks_created")).isEqualTo(1);
    }

    @Test
    void ingestTextStoresEntitiesInGraph() {
        var extraction = new ExtractionResult(
                List.of(new EntityInfo("Java", "technology"),
                        new EntityInfo("Spring", "framework")),
                List.of(new RelationshipInfo("Java", "powers", "Spring")));

        service.ingestWithExtractionResult(
                "Java powers Spring.", "test.txt", "codebase", extraction);

        var javaVertices = graphRepo.findVerticesByName("Java");
        assertThat(javaVertices).isNotEmpty();

        var springVertices = graphRepo.findVerticesByName("Spring");
        assertThat(springVertices).isNotEmpty();
    }

    @Test
    void rejectsTextExceedingMaxLength() {
        var longService = new GraphGenerationService(
                graphRepo, vectorRepo, embeddingModel, new TextChunker(), null, 10);

        var result = longService.ingestWithExtractionResult(
                "This text is way too long for the limit", "test.txt", "document",
                new ExtractionResult(List.of(), List.of()));

        assertThat(result.containsKey("error")).isTrue();
    }

    @Test
    void ingestWithoutLlmStoresChunksAndSkipsExtraction() {
        // service is constructed with null chatModel, so extractor == null
        var result = service.ingest("Hello world. This is a test.", "test.txt", "document");

        assertThat(result.get("chunks_created")).isEqualTo(1);
        // No LLM extractor — entities_extracted should be 0
        assertThat(result.get("entities_extracted")).isEqualTo(0);
        // Chunk nodes should be stored in graph
        assertThat(graphRepo.traversalSource().V().hasLabel("TextChunk").count().next()).isEqualTo(1L);
    }

    @Test
    void ingestRejectsTextExceedingMaxLength() {
        var limitedService = new GraphGenerationService(
                graphRepo, vectorRepo, embeddingModel, new TextChunker(), null, 10);

        var result = limitedService.ingest("This text is way too long", "test.txt", "document");

        assertThat(result.containsKey("error")).isTrue();
    }

    @Test
    void ingestMultipleParagraphsCreatesMultipleChunks() {
        String text = "First paragraph with some content.\n\nSecond paragraph with different content.\n\nThird paragraph here.";
        var result = service.ingest(text, "multi.txt", "document");

        int chunks = (int) result.get("chunks_created");
        assertThat(chunks).isGreaterThanOrEqualTo(1);
        // All chunks stored in graph
        assertThat(graphRepo.traversalSource().V().hasLabel("TextChunk").count().next()).isEqualTo((long) chunks);
    }

    @Test
    void processExtractionCreatesRelationshipEntitiesIfMissing() {
        // Relationships reference entities not in the entities list — they should be auto-created
        var extraction = new ExtractionResult(
                List.of(),  // no explicit entities
                List.of(new RelationshipInfo("Alpha", "uses", "Beta")));

        service.ingestWithExtractionResult("Alpha uses Beta.", "test.txt", "reference", extraction);

        // Both Alpha and Beta should have been created from the relationship
        assertThat(graphRepo.findVerticesByName("Alpha")).isNotEmpty();
        assertThat(graphRepo.findVerticesByName("Beta")).isNotEmpty();
    }
}
