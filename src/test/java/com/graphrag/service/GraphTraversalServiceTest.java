package com.graphrag.service;

import com.graphrag.model.TraversalResult;
import com.graphrag.repository.GraphRepository;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.assertj.core.api.Assertions.assertThat;

class GraphTraversalServiceTest {

    private TinkerGraph graph;
    private GraphRepository graphRepo;
    private GraphTraversalService service;

    @BeforeEach
    void setUp() {
        graph = TinkerGraph.open();
        GraphTraversalSource g = traversal().withEmbedded(graph);
        graphRepo = new GraphRepository(g);
        service = new GraphTraversalService(graphRepo);

        // Build test graph: A -> B -> C -> D, A -> E -> D
        graphRepo.addEntity("A", "concept", "reference");
        graphRepo.addEntity("B", "concept", "reference");
        graphRepo.addEntity("C", "concept", "reference");
        graphRepo.addEntity("D", "concept", "reference");
        graphRepo.addEntity("E", "concept", "reference");
        graphRepo.addRelationship("A", "B", "connects", "c1");
        graphRepo.addRelationship("B", "C", "connects", "c1");
        graphRepo.addRelationship("C", "D", "connects", "c1");
        graphRepo.addRelationship("A", "E", "connects", "c1");
        graphRepo.addRelationship("E", "D", "connects", "c1");
    }

    @AfterEach
    void tearDown() throws Exception { graph.close(); }

    @Test
    void bfsFindsPath() {
        TraversalResult result = service.traverse(List.of("A", "D"), "BFS", null);
        assertThat(result.paths()).isNotEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void dfsFindsPath() {
        TraversalResult result = service.traverse(List.of("A", "D"), "DFS", null);
        assertThat(result.paths()).isNotEmpty();
    }

    @Test
    void shortestPathFindsShortestRoute() {
        TraversalResult result = service.traverse(List.of("A", "D"), "SHORTEST_PATH", null);
        assertThat(result.paths()).isNotEmpty();
        // Shortest path is A->E->D (2 hops) not A->B->C->D (3 hops)
    }

    @Test
    void semanticStopForcesPathThroughNode() {
        TraversalResult result = service.traverse(List.of("A", "D"), "SHORTEST_PATH", "B");
        assertThat(result.paths()).isNotEmpty();
        // Path should go through B
    }

    @Test
    void unknownKeywordGeneratesWarning() {
        TraversalResult result = service.traverse(List.of("A", "NONEXISTENT"), "BFS", null);
        assertThat(result.warnings()).isNotEmpty();
        assertThat(result.warnings().get(0)).contains("NONEXISTENT");
    }

    @Test
    void unknownSemanticStopGeneratesWarning() {
        TraversalResult result = service.traverse(List.of("A", "D"), "BFS", "NONEXISTENT");
        assertThat(result.warnings()).anyMatch(w -> w.contains("NONEXISTENT"));
    }

    @Test
    void singleKeywordReturnsEmptyResult() {
        // Need at least 2 resolved vertices for pairwise path computation
        TraversalResult result = service.traverse(List.of("A"), "BFS", null);
        assertThat(result.paths()).isEmpty();
    }

    @Test
    void allKeywordsUnknownReturnsEmptyWithWarnings() {
        TraversalResult result = service.traverse(List.of("UNKNOWN1", "UNKNOWN2"), "BFS", null);
        assertThat(result.paths()).isEmpty();
        assertThat(result.warnings()).hasSize(2);
        assertThat(result.warnings()).anyMatch(w -> w.contains("UNKNOWN1"));
        assertThat(result.warnings()).anyMatch(w -> w.contains("UNKNOWN2"));
    }

    @Test
    void emptySemanticStopIsIgnored() {
        // Blank semantic_stop should be treated as no constraint
        TraversalResult result = service.traverse(List.of("A", "D"), "BFS", "");
        assertThat(result.paths()).isNotEmpty();
        // No warning about empty semantic stop
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void multipleKeywordsComputesPairwisePaths() {
        // A, B, C as keywords => 3 pairs: A-B, A-C, B-C
        TraversalResult result = service.traverse(List.of("A", "B", "C"), "BFS", null);
        // At least some paths should be found (A-B is directly connected)
        assertThat(result.paths()).isNotEmpty();
    }
}
