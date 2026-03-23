package com.graphrag.service;

import com.graphrag.model.SearchResult;
import com.graphrag.repository.GraphRepository;
import com.graphrag.repository.VectorRepository;
import com.graphrag.service.EntityNormalizer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.*;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HybridSearchServiceTest {

    private TinkerGraph graph;
    private GraphRepository graphRepo;
    private VectorRepository vectorRepo;
    private EmbeddingModel embeddingModel;
    private HybridSearchService service;

    @BeforeEach
    void setUp() {
        graph = TinkerGraph.open();
        GraphTraversalSource g = traversal().withEmbedded(graph);
        graphRepo = new GraphRepository(g, new EntityNormalizer(), 0.85);
        vectorRepo = new VectorRepository(new InMemoryEmbeddingStore<>());
        embeddingModel = mock(EmbeddingModel.class);

        float[] dummyVector = new float[]{0.5f, 0.5f, 0.5f};
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(dummyVector)));

        service = new HybridSearchService(graphRepo, vectorRepo, embeddingModel);
    }

    @AfterEach
    void tearDown() throws Exception { graph.close(); }

    @Test
    void searchReturnsTextAndSubgraph() {
        // Seed data
        graphRepo.addEntity("Java", "technology", "codebase");
        graphRepo.addEntity("Spring", "framework", "codebase");
        graphRepo.addRelationship("Java", "Spring", "powers", "chunk-1");
        graphRepo.addTextChunkNode("chunk-1", "Java powers Spring", "test.txt", "codebase");
        graphRepo.linkEntityToChunk("Java", "chunk-1");
        graphRepo.linkEntityToChunk("Spring", "chunk-1");

        float[] vector = new float[]{0.5f, 0.5f, 0.5f};
        vectorRepo.store("chunk-1", "Java powers Spring", Embedding.from(vector), "test.txt", "codebase");

        SearchResult result = service.search("Java Spring", 5);
        assertThat(result.textContext()).contains("Java powers Spring");
        assertThat(result.nodes()).isNotEmpty();
    }

    @Test
    void searchEmptyStoreReturnsEmptyResult() {
        SearchResult result = service.search("anything", 5);
        assertThat(result.textContext()).isEmpty();
        assertThat(result.nodes()).isEmpty();
    }
}
