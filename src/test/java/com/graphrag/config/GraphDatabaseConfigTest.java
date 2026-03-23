package com.graphrag.config;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.apache.tinkerpop.gremlin.structure.io.IoCore.graphson;
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

    @Test
    void loadsGraphFromExistingFile(@TempDir Path tempDir) throws Exception {
        // First, write a GraphSON file with a vertex
        String graphPath = tempDir.resolve("existing.json").toString();
        TinkerGraph sourceGraph = TinkerGraph.open();
        sourceGraph.addVertex("TestLabel");
        sourceGraph.io(graphson()).writeGraph(graphPath);
        sourceGraph.close();

        // Now load it via config
        var config = new GraphDatabaseConfig();
        TinkerGraph loaded = config.tinkerGraphStdio(graphPath);
        GraphTraversalSource g = traversal().withEmbedded(loaded);

        assertThat(g.V().count().next()).isEqualTo(1L);
        loaded.close();
    }

    @Test
    void handlesCorruptedFileGracefully(@TempDir Path tempDir) throws Exception {
        // Write garbage content to a file
        Path corruptFile = tempDir.resolve("corrupt.json");
        Files.writeString(corruptFile, "NOT VALID GRAPHSON DATA {{{");

        // Config should not throw — it logs a warning and returns empty graph
        var config = new GraphDatabaseConfig();
        TinkerGraph graph = config.tinkerGraphStdio(corruptFile.toString());

        assertThat(graph).isNotNull();
        // Graph should be empty (corrupted file was ignored)
        GraphTraversalSource g = traversal().withEmbedded(graph);
        assertThat(g.V().count().next()).isEqualTo(0L);

        graph.close();
    }
}
