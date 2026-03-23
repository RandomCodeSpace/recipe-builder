package com.graphrag.config;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class GraphDatabaseConfigTest {

    @Test
    void createsGraphAndTraversalSource(@TempDir Path tempDir) {
        var config = new GraphDatabaseConfig();
        String path = tempDir.resolve("graph.json").toString();
        TinkerGraph graph = config.tinkerGraphStdio(path);
        GraphTraversalSource g = config.graphTraversalSource(graph);

        assertThat(graph).isNotNull();
        assertThat(g).isNotNull();
        assertThat(g.V().count().next()).isEqualTo(0L);

        graph.close();
    }
}
