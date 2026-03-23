package com.graphrag.service;

import com.graphrag.model.TraversalResult;
import com.graphrag.repository.GraphRepository;
import org.apache.tinkerpop.gremlin.process.traversal.Path;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GraphTraversalService {

    private static final int MAX_DEPTH = 10;

    private final GraphRepository graphRepo;

    public GraphTraversalService(GraphRepository graphRepo) {
        this.graphRepo = graphRepo;
    }

    public TraversalResult traverse(List<String> keywords, String algorithm,
                                     String semanticStop) {
        List<String> warnings = new ArrayList<>();
        GraphTraversalSource g = graphRepo.traversalSource();

        // Resolve keywords to vertices
        List<Vertex> resolved = new ArrayList<>();
        for (String keyword : keywords) {
            List<Vertex> found = graphRepo.findVerticesByName(keyword);
            if (found.isEmpty()) {
                warnings.add("Keyword not found in graph: " + keyword);
            } else {
                resolved.addAll(found);
            }
        }

        if (resolved.size() < 2) {
            return new TraversalResult(List.of(), List.of(), List.of(), warnings);
        }

        // Resolve semantic stop
        Vertex stopVertex = null;
        if (semanticStop != null && !semanticStop.isBlank()) {
            List<Vertex> stopCandidates = graphRepo.findVerticesByName(semanticStop);
            if (stopCandidates.isEmpty()) {
                warnings.add("Semantic stop node not found: " + semanticStop + " (constraint ignored)");
            } else {
                stopVertex = stopCandidates.get(0);
            }
        }

        // Compute pairwise paths
        Set<Map<String, Object>> allNodes = new LinkedHashSet<>();
        Set<Map<String, Object>> allEdges = new LinkedHashSet<>();
        List<List<Map<String, Object>>> allPaths = new ArrayList<>();

        for (int i = 0; i < resolved.size(); i++) {
            for (int j = i + 1; j < resolved.size(); j++) {
                List<Path> paths;
                if (stopVertex != null) {
                    // Force through stop: A→stop + stop→B
                    List<Path> toStop = findPaths(g, resolved.get(i), stopVertex, algorithm);
                    List<Path> fromStop = findPaths(g, stopVertex, resolved.get(j), algorithm);
                    paths = new ArrayList<>();
                    paths.addAll(toStop);
                    paths.addAll(fromStop);
                } else {
                    paths = findPaths(g, resolved.get(i), resolved.get(j), algorithm);
                }

                for (Path path : paths) {
                    List<Map<String, Object>> pathRepr = new ArrayList<>();
                    for (Object obj : path) {
                        if (obj instanceof Vertex v) {
                            Map<String, Object> map = GraphRepository.vertexToMap(v);
                            allNodes.add(map);
                            pathRepr.add(map);
                        } else if (obj instanceof Edge e) {
                            allEdges.add(GraphRepository.edgeToMap(e));
                        }
                    }
                    if (!pathRepr.isEmpty()) allPaths.add(pathRepr);
                }
            }
        }

        return new TraversalResult(allPaths, new ArrayList<>(allNodes),
                new ArrayList<>(allEdges), warnings);
    }

    private List<Path> findPaths(GraphTraversalSource g, Vertex source, Vertex target,
                                  String algorithm) {
        try {
            return switch (algorithm.toUpperCase()) {
                case "BFS" -> g.V(source.id())
                        .repeat(__.both().simplePath())
                        .until(__.hasId(target.id()).or().loops().is(MAX_DEPTH))
                        .hasId(target.id())
                        .path()
                        .toList();
                case "DFS" -> g.V(source.id())
                        .emit(__.hasId(target.id()))
                        .repeat(__.both().simplePath())
                        .until(__.loops().is(MAX_DEPTH))
                        .hasId(target.id())
                        .path()
                        .limit(5)
                        .toList();
                case "SHORTEST_PATH" -> {
                    List<Path> paths = g.V(source.id())
                            .repeat(__.both().simplePath())
                            .until(__.hasId(target.id()).or().loops().is(MAX_DEPTH))
                            .hasId(target.id())
                            .path()
                            .toList();
                    // Return only shortest
                    if (paths.isEmpty()) yield paths;
                    int minLen = paths.stream().mapToInt(Path::size).min().orElse(Integer.MAX_VALUE);
                    yield paths.stream().filter(p -> p.size() == minLen).toList();
                }
                default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
            };
        } catch (Exception e) {
            return List.of();
        }
    }
}
