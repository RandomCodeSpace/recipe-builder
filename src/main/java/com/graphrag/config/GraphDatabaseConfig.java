package com.graphrag.config;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal;
import static org.apache.tinkerpop.gremlin.structure.io.IoCore.graphson;

@Configuration
public class GraphDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(GraphDatabaseConfig.class);

    @Bean
    @Profile({"stdio", "default"})
    public TinkerGraph tinkerGraphStdio(
            @Value("${graphrag.graph.persistence-path}") String persistencePath) {
        TinkerGraph graph = TinkerGraph.open();
        loadGraphFromFile(graph, persistencePath);
        return graph;
    }

    @Bean
    @Profile("sse")
    public TinkerGraph tinkerGraphSse(
            @Value("${graphrag.graph.persistence-path}") String persistencePath) {
        TinkerGraph graph = TinkerGraph.open();
        loadGraphFromFile(graph, persistencePath);
        return graph;
    }

    @Bean
    public GraphTraversalSource graphTraversalSource(TinkerGraph graph) {
        return traversal().withEmbedded(graph);
    }

    private void loadGraphFromFile(TinkerGraph graph, String persistencePath) {
        Path path = Path.of(persistencePath);
        if (Files.exists(path)) {
            try {
                graph.io(graphson()).readGraph(persistencePath);
                log.info("Graph loaded from {}", persistencePath);
            } catch (Exception e) {
                log.warn("Failed to load graph from {}: {}", persistencePath, e.getMessage());
            }
        }
    }
}
