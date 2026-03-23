package com.graphrag.repository;

import com.graphrag.service.EntityNormalizer;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.Map;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.assertj.core.api.Assertions.assertThat;

class GraphRepositoryTest {

    private TinkerGraph graph;
    private GraphTraversalSource g;
    private GraphRepository repo;

    @BeforeEach
    void setUp() {
        graph = TinkerGraph.open();
        g = traversal().withEmbedded(graph);
        repo = new GraphRepository(g, new EntityNormalizer(), 0.85);
    }

    @AfterEach
    void tearDown() throws Exception {
        graph.close();
    }

    @Test
    void addEntityCreatesVertex() {
        repo.addEntity("Java", "technology", "codebase");
        assertThat(g.V().hasLabel("Entity").has("name", "Java").count().next()).isEqualTo(1L);
    }

    @Test
    void addEntityIsIdempotent() {
        repo.addEntity("Java", "technology", "codebase");
        repo.addEntity("Java", "technology", "codebase");
        assertThat(g.V().hasLabel("Entity").has("name", "Java").count().next()).isEqualTo(1L);
    }

    @Test
    void addRelationshipCreatesEdge() {
        repo.addEntity("Java", "technology", "codebase");
        repo.addEntity("Spring", "framework", "codebase");
        repo.addRelationship("Java", "Spring", "powers", "chunk-1");
        assertThat(g.E().hasLabel("RELATES_TO").has("relationship", "powers").count().next()).isEqualTo(1L);
    }

    @Test
    void addTextChunkNodeCreatesVertex() {
        repo.addTextChunkNode("chunk-1", "some text", "file.java", "codebase");
        assertThat(g.V().hasLabel("TextChunk").has("chunkId", "chunk-1").count().next()).isEqualTo(1L);
    }

    @Test
    void linkEntityToChunkCreatesEdge() {
        repo.addEntity("Java", "technology", "codebase");
        repo.addTextChunkNode("chunk-1", "Java is great", "file.java", "codebase");
        repo.linkEntityToChunk("Java", "chunk-1");
        assertThat(g.V().has("Entity", "name", "Java")
                .outE("MENTIONED_IN").count().next()).isEqualTo(1L);
    }

    @Test
    void getSubgraphByChunkIdsReturnsConnectedEntities() {
        repo.addEntity("Java", "technology", "codebase");
        repo.addEntity("Spring", "framework", "codebase");
        repo.addRelationship("Java", "Spring", "powers", "chunk-1");
        repo.addTextChunkNode("chunk-1", "Java powers Spring", "file.java", "codebase");
        repo.linkEntityToChunk("Java", "chunk-1");
        repo.linkEntityToChunk("Spring", "chunk-1");

        var result = repo.getSubgraphByChunkIds(List.of("chunk-1"));
        assertThat(result.nodes()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.edges()).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void findVerticesByNameCaseInsensitive() {
        repo.addEntity("Java", "technology", "codebase");
        var vertices = repo.findVerticesByName("java");
        assertThat(vertices).isNotEmpty();
    }

    @Test
    void findVerticesByNameContainsMatch() {
        repo.addEntity("Spring Boot", "framework", "codebase");
        var vertices = repo.findVerticesByName("spring");
        assertThat(vertices).isNotEmpty();
    }

    @Test
    void addRelationshipIgnoresMissingSource() {
        // Only target entity exists — no source
        repo.addEntity("Spring", "framework", "codebase");
        repo.addRelationship("NonExistentSource", "Spring", "powers", "chunk-1");
        assertThat(g.E().hasLabel("RELATES_TO").count().next()).isEqualTo(0L);
    }

    @Test
    void addRelationshipIgnoresMissingTarget() {
        // Only source entity exists — no target
        repo.addEntity("Java", "technology", "codebase");
        repo.addRelationship("Java", "NonExistentTarget", "powers", "chunk-1");
        assertThat(g.E().hasLabel("RELATES_TO").count().next()).isEqualTo(0L);
    }

    @Test
    void linkEntityToChunkIgnoresMissingEntity() {
        // Chunk exists, entity does not
        repo.addTextChunkNode("chunk-1", "some text", "file.java", "codebase");
        repo.linkEntityToChunk("NonExistentEntity", "chunk-1");
        assertThat(g.E().hasLabel("MENTIONED_IN").count().next()).isEqualTo(0L);
    }

    @Test
    void linkEntityToChunkIgnoresMissingChunk() {
        // Entity exists, chunk does not
        repo.addEntity("Java", "technology", "codebase");
        repo.linkEntityToChunk("Java", "nonexistent-chunk");
        assertThat(g.E().hasLabel("MENTIONED_IN").count().next()).isEqualTo(0L);
    }

    @Test
    void addExecutionTraceVertexCreatesVertex() {
        var v = repo.addExecutionTraceVertex("t-1", "task desc", "Planner", true);
        assertThat(v).isNotNull();
        assertThat(g.V().hasLabel("ExecutionTrace").has("traceId", "t-1").count().next()).isEqualTo(1L);
        assertThat(v.property("taskDescription").value()).isEqualTo("task desc");
        assertThat(v.property("agentPersona").value()).isEqualTo("Planner");
        assertThat(v.property("successful").value()).isEqualTo(true);
    }

    @Test
    void addTraceStepVertexCreatesVertex() {
        var v = repo.addTraceStepVertex("step-1", "search", "searched for X", 1);
        assertThat(v).isNotNull();
        assertThat(g.V().hasLabel("TraceStep").has("stepId", "step-1").count().next()).isEqualTo(1L);
        assertThat(v.property("toolName").value()).isEqualTo("search");
        assertThat(v.property("reasoning").value()).isEqualTo("searched for X");
        assertThat(v.property("order").value()).isEqualTo(1);
    }

    @Test
    void addEdgeCreatesEdge() {
        repo.addEntity("Source", "concept", "test");
        repo.addEntity("Target", "concept", "test");
        var source = g.V().hasLabel("Entity").has("name", "Source").next();
        var target = g.V().hasLabel("Entity").has("name", "Target").next();
        repo.addEdge(source, target, "CUSTOM_EDGE");
        assertThat(g.E().hasLabel("CUSTOM_EDGE").count().next()).isEqualTo(1L);
    }

    @Test
    void vertexToMapReturnsCorrectFields() {
        repo.addEntity("TestEntity", "technology", "codebase");
        var v = g.V().hasLabel("Entity").has("name", "TestEntity").next();
        Map<String, Object> map = GraphRepository.vertexToMap(v);
        assertThat(map).containsKey("id");
        assertThat(map).containsKey("label");
        assertThat(map.get("label")).isEqualTo("Entity");
        assertThat(map.get("name")).isEqualTo("TestEntity");
    }

    @Test
    void edgeToMapReturnsCorrectFields() {
        repo.addEntity("Java", "technology", "codebase");
        repo.addEntity("Spring", "framework", "codebase");
        repo.addRelationship("Java", "Spring", "powers", "chunk-1");
        var e = g.E().hasLabel("RELATES_TO").next();
        Map<String, Object> map = GraphRepository.edgeToMap(e);
        assertThat(map).containsKey("id");
        assertThat(map).containsKey("label");
        assertThat(map).containsKey("source");
        assertThat(map).containsKey("target");
        assertThat(map.get("label")).isEqualTo("RELATES_TO");
    }

    @Test
    void getSubgraphByChunkIdsEmptyListReturnsEmpty() {
        var result = repo.getSubgraphByChunkIds(List.of());
        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
    }

    @Test
    void addEntityDeduplicatesNearMatches() {
        repo.addEntity("Spring Boot", "framework", "codebase");
        repo.addEntity("SpringBoot", "framework", "codebase");
        // "spring boot" vs "springboot" have similarity > 0.85 so second should be deduplicated
        long count = g.V().hasLabel("Entity").count().next();
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void addEntityPreservesOriginalName() {
        repo.addEntity("Spring Boot", "framework", "codebase");
        var v = g.V().hasLabel("Entity").has("normalizedName", "spring boot").next();
        assertThat(v.property("name").value()).isEqualTo("Spring Boot");
        assertThat(v.property("normalizedName").value()).isEqualTo("spring boot");
    }

    @Test
    void addEntityAllowsDifferentEntities() {
        repo.addEntity("Java", "technology", "codebase");
        repo.addEntity("Python", "technology", "codebase");
        long count = g.V().hasLabel("Entity").count().next();
        assertThat(count).isEqualTo(2L);
    }
}
