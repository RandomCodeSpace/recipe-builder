package com.graphrag.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphrag.repository.GraphRepository;
import com.graphrag.repository.VectorRepository;
import com.graphrag.service.ExecutionTraceService;
import com.graphrag.service.GraphGenerationService;
import com.graphrag.service.GraphTraversalService;
import com.graphrag.service.HybridSearchService;
import com.graphrag.service.TextChunker;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpToolProviderTest {

    private McpToolProvider provider;

    @BeforeEach
    void setUp() {
        TinkerGraph graph = TinkerGraph.open();
        GraphTraversalSource g = traversal().withEmbedded(graph);
        var graphRepo = new GraphRepository(g);
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

    @Test
    void allToolsAreDefined() {
        assertThat(provider.hybridSearchTool().name()).isEqualTo("hybrid_graph_weave_search");
        assertThat(provider.traversalTool().name()).isEqualTo("creative_graph_traversal");
        assertThat(provider.recordTraceTool().name()).isEqualTo("record_execution_trace");
        assertThat(provider.ingestTextTool().name()).isEqualTo("ingest_text");
    }
}
