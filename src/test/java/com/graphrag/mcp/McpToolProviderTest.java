package com.graphrag.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphrag.repository.GraphRepository;
import com.graphrag.repository.VectorRepository;
import com.graphrag.service.*;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpToolProviderTest {

    private McpToolProvider provider;
    private TinkerGraph graph;
    private GraphRepository graphRepo;

    @BeforeEach
    void setUp() {
        graph = TinkerGraph.open();
        GraphTraversalSource g = traversal().withEmbedded(graph);
        graphRepo = new GraphRepository(g);
        var vectorRepo = new VectorRepository(new InMemoryEmbeddingStore<>());
        var embeddingModel = mock(EmbeddingModel.class);
        float[] dummyVector = new float[384];
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(dummyVector)));

        var chunker = new TextChunker();
        var genService = new GraphGenerationService(graphRepo, vectorRepo, embeddingModel, chunker, null, 100000);
        var searchService = new HybridSearchService(graphRepo, vectorRepo, embeddingModel);
        var traversalService = new GraphTraversalService(graphRepo);
        var traceService = new ExecutionTraceService(graphRepo, vectorRepo, embeddingModel, 0.7);

        provider = new McpToolProvider(genService, searchService, traversalService, traceService, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception { graph.close(); }

    @Test
    void allToolsAreDefined() {
        assertThat(provider.hybridSearchTool().name()).isEqualTo("hybrid_graph_weave_search");
        assertThat(provider.traversalTool().name()).isEqualTo("creative_graph_traversal");
        assertThat(provider.recordTraceTool().name()).isEqualTo("record_execution_trace");
        assertThat(provider.ingestTextTool().name()).isEqualTo("ingest_text");
    }

    @Test
    void handleHybridSearchReturnsResult() {
        Map<String, Object> args = new HashMap<>();
        args.put("query", "test query");
        args.put("top_k", 5);
        CallToolResult result = provider.handleHybridSearch(args);
        assertThat(result.isError()).isFalse();
    }

    @Test
    void handleHybridSearchUsesDefaultTopK() {
        Map<String, Object> args = new HashMap<>();
        args.put("query", "test query");
        CallToolResult result = provider.handleHybridSearch(args);
        assertThat(result.isError()).isFalse();
    }

    @Test
    void handleHybridSearchReturnsErrorOnException() {
        // null query should cause NullPointerException
        Map<String, Object> args = new HashMap<>();
        CallToolResult result = provider.handleHybridSearch(args);
        assertThat(result.isError()).isTrue();
        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text).startsWith("Error:");
    }

    @Test
    void handleTraversalReturnsResult() {
        graphRepo.addEntity("X", "concept", "reference");
        graphRepo.addEntity("Y", "concept", "reference");
        graphRepo.addRelationship("X", "Y", "links", "c1");

        Map<String, Object> args = new HashMap<>();
        args.put("keywords", List.of("X", "Y"));
        args.put("algorithm", "BFS");
        args.put("semantic_stop", null);
        CallToolResult result = provider.handleTraversal(args);
        assertThat(result.isError()).isFalse();
    }

    @Test
    void handleTraversalReturnsErrorOnException() {
        Map<String, Object> args = new HashMap<>();
        // Missing required fields — keywords will be null, causing NullPointerException
        CallToolResult result = provider.handleTraversal(args);
        assertThat(result.isError()).isTrue();
    }

    @Test
    void handleRecordTraceReturnsResult() {
        Map<String, Object> step1 = new HashMap<>();
        step1.put("tool_name", "search");
        step1.put("reasoning", "searched for data");
        step1.put("order", 1);

        Map<String, Object> args = new HashMap<>();
        args.put("trace_id", "t-1");
        args.put("task_description", "test task");
        args.put("agent_persona", "Planner");
        args.put("successful", true);
        args.put("steps", List.of(step1));

        CallToolResult result = provider.handleRecordTrace(args);
        assertThat(result.isError()).isFalse();
        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text).contains("trace_id");
    }

    @Test
    void handleRecordTraceReturnsErrorOnException() {
        Map<String, Object> args = new HashMap<>();
        CallToolResult result = provider.handleRecordTrace(args);
        assertThat(result.isError()).isTrue();
    }

    @Test
    void handleIngestTextReturnsResult() {
        Map<String, Object> args = new HashMap<>();
        args.put("text", "Hello world. This is a test.");
        args.put("source", "test.txt");
        args.put("domain", "document");
        CallToolResult result = provider.handleIngestText(args);
        assertThat(result.isError()).isFalse();
        String text = ((TextContent) result.content().get(0)).text();
        assertThat(text).contains("chunks_created");
    }

    @Test
    void handleIngestTextReturnsErrorForTooLongText() {
        // Create provider with small limit
        TinkerGraph localGraph = TinkerGraph.open();
        var graphRepoLocal = new GraphRepository(traversal().withEmbedded(localGraph));
        var vectorRepo = new VectorRepository(new InMemoryEmbeddingStore<>());
        var embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[384])));
        var genService = new GraphGenerationService(graphRepoLocal, vectorRepo, embeddingModel, new TextChunker(), null, 10);
        var searchService = new HybridSearchService(graphRepoLocal, vectorRepo, embeddingModel);
        var traversalService = new GraphTraversalService(graphRepoLocal);
        var traceService = new ExecutionTraceService(graphRepoLocal, vectorRepo, embeddingModel, 0.7);
        var smallProvider = new McpToolProvider(genService, searchService, traversalService, traceService, new ObjectMapper());

        Map<String, Object> args = new HashMap<>();
        args.put("text", "This is way too long for the small limit");
        args.put("source", "test.txt");
        args.put("domain", "document");
        CallToolResult result = smallProvider.handleIngestText(args);
        assertThat(result.isError()).isTrue();

        localGraph.close();
    }

    @Test
    void handleIngestTextReturnsErrorOnException() {
        Map<String, Object> args = new HashMap<>();
        // Missing required fields
        CallToolResult result = provider.handleIngestText(args);
        assertThat(result.isError()).isTrue();
    }
}
