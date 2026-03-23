package com.graphrag.service;

import com.graphrag.model.ExecutionTrace;
import com.graphrag.model.TraceStep;
import com.graphrag.repository.GraphRepository;
import com.graphrag.repository.VectorRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Map;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ExecutionTraceServiceTest {

    private TinkerGraph graph;
    private GraphRepository graphRepo;
    private ExecutionTraceService service;

    @BeforeEach
    void setUp() {
        graph = TinkerGraph.open();
        GraphTraversalSource g = traversal().withEmbedded(graph);
        graphRepo = new GraphRepository(g);

        var vectorRepo = new VectorRepository(new InMemoryEmbeddingStore<>());
        var embeddingModel = mock(EmbeddingModel.class);
        float[] dummyVector = new float[]{0.5f, 0.5f, 0.5f};
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(dummyVector)));

        service = new ExecutionTraceService(graphRepo, vectorRepo, embeddingModel, 0.7);
    }

    @AfterEach
    void tearDown() throws Exception { graph.close(); }

    @Test
    void recordTraceCreatesGraphStructure() {
        var trace = new ExecutionTrace("trace-1", "Test task", "Planner",
                List.of(new TraceStep("search", "Used search tool", 1),
                        new TraceStep("analyze", "Analyzed results", 2)),
                true);

        Map<String, Object> result = service.recordTrace(trace);

        assertThat(result.get("trace_id")).isEqualTo("trace-1");
        assertThat(result.get("stored")).isEqualTo(true);

        // Verify graph structure
        GraphTraversalSource g = graphRepo.traversalSource();
        assertThat(g.V().hasLabel("ExecutionTrace").has("traceId", "trace-1").count().next()).isEqualTo(1L);
        assertThat(g.V().hasLabel("TraceStep").count().next()).isEqualTo(2L);
        assertThat(g.E().hasLabel("HAS_STEP").count().next()).isEqualTo(2L);
        assertThat(g.E().hasLabel("NEXT_STEP").count().next()).isEqualTo(1L);
    }
}
