package com.graphrag.repository;

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
        repo = new GraphRepository(g);
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
}
