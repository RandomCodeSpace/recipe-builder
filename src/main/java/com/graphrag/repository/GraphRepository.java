package com.graphrag.repository;

import com.graphrag.model.SearchResult;
import com.graphrag.service.EntityNormalizer;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class GraphRepository {

    private static final Logger log = LoggerFactory.getLogger(GraphRepository.class);

    private final GraphTraversalSource g;
    private final EntityNormalizer normalizer;
    private final double similarityThreshold;

    public GraphRepository(GraphTraversalSource g,
                           EntityNormalizer normalizer,
                           @Value("${graphrag.dedup.similarity-threshold:0.85}") double similarityThreshold) {
        this.g = g;
        this.normalizer = normalizer;
        this.similarityThreshold = similarityThreshold;
    }

    public void addEntity(String name, String type, String domain) {
        String normalized = normalizer.normalize(name);

        // Fast path: exact match on normalizedName
        if (g.V().hasLabel("Entity").has("normalizedName", normalized).hasNext()) return;

        // Similarity scan over existing Entity vertices
        List<Vertex> allEntities = g.V().hasLabel("Entity").toList();
        for (Vertex existing : allEntities) {
            if (!existing.property("normalizedName").isPresent()) continue;
            String existingNormalized = (String) existing.property("normalizedName").value();
            if (normalizer.similarity(normalized, existingNormalized) > similarityThreshold) {
                log.info("Deduplicating entity '{}' — merged with existing '{}'",
                        name, existing.property("name").value());
                return;
            }
        }

        // No match found — create new vertex
        g.addV("Entity")
                .property("name", name)
                .property("normalizedName", normalized)
                .property("type", type)
                .property("domain", domain)
                .property("createdAt", Instant.now().toString())
                .iterate();
    }

    public void addRelationship(String sourceName, String targetName,
                                 String relationship, String chunkId) {
        Optional<Vertex> s = g.V().hasLabel("Entity").has("name", sourceName).tryNext();
        Optional<Vertex> t = g.V().hasLabel("Entity").has("name", targetName).tryNext();
        if (s.isEmpty() || t.isEmpty()) return;

        g.addE("RELATES_TO").from(s.get()).to(t.get())
                .property("relationship", relationship)
                .property("chunkId", chunkId)
                .iterate();
    }

    public void addTextChunkNode(String chunkId, String content, String source, String domain) {
        g.addV("TextChunk")
                .property("chunkId", chunkId)
                .property("content", content)
                .property("source", source)
                .property("domain", domain)
                .property("createdAt", Instant.now().toString())
                .iterate();
    }

    public void linkEntityToChunk(String entityName, String chunkId) {
        Optional<Vertex> e = g.V().hasLabel("Entity").has("name", entityName).tryNext();
        Optional<Vertex> c = g.V().hasLabel("TextChunk").has("chunkId", chunkId).tryNext();
        if (e.isEmpty() || c.isEmpty()) return;

        g.addE("MENTIONED_IN").from(e.get()).to(c.get()).iterate();
    }

    public SearchResult getSubgraphByChunkIds(List<String> chunkIds) {
        Set<Map<String, Object>> nodes = new LinkedHashSet<>();
        Set<Map<String, Object>> edges = new LinkedHashSet<>();

        for (String chunkId : chunkIds) {
            // Get entities mentioned in this chunk
            List<Vertex> entities = g.V().hasLabel("TextChunk").has("chunkId", chunkId)
                    .in("MENTIONED_IN").hasLabel("Entity").toList();

            for (Vertex entity : entities) {
                nodes.add(vertexToMap(entity));

                // Get relationships from this entity
                List<Edge> rels = g.V(entity.id()).bothE("RELATES_TO").toList();
                for (Edge rel : rels) {
                    edges.add(edgeToMap(rel));
                    Vertex other = rel.inVertex().id().equals(entity.id())
                            ? rel.outVertex() : rel.inVertex();
                    nodes.add(vertexToMap(other));
                }
            }
        }

        return new SearchResult("", new ArrayList<>(nodes), new ArrayList<>(edges));
    }

    public List<Vertex> findVerticesByName(String name) {
        // Case-insensitive exact match first
        List<Vertex> results = g.V().hasLabel("Entity")
                .toList().stream()
                .filter(v -> {
                    String vName = (String) v.property("name").value();
                    return vName.equalsIgnoreCase(name);
                })
                .collect(Collectors.toList());

        // Fallback: contains match
        if (results.isEmpty()) {
            String lowerName = name.toLowerCase();
            results = g.V().hasLabel("Entity")
                    .toList().stream()
                    .filter(v -> {
                        String vName = (String) v.property("name").value();
                        return vName.toLowerCase().contains(lowerName);
                    })
                    .collect(Collectors.toList());
        }

        return results;
    }

    public Vertex addExecutionTraceVertex(String traceId, String taskDescription,
                                            String agentPersona, boolean successful) {
        return g.addV("ExecutionTrace")
                .property("traceId", traceId)
                .property("taskDescription", taskDescription)
                .property("agentPersona", agentPersona)
                .property("successful", successful)
                .property("timestamp", Instant.now().toString())
                .next();
    }

    public Vertex addTraceStepVertex(String stepId, String toolName,
                                       String reasoning, int order) {
        return g.addV("TraceStep")
                .property("stepId", stepId)
                .property("toolName", toolName)
                .property("reasoning", reasoning)
                .property("order", order)
                .next();
    }

    public void addEdge(Vertex from, Vertex to, String label) {
        g.addE(label).from(from).to(to).iterate();
    }

    public GraphTraversalSource traversalSource() {
        return g;
    }

    public static Map<String, Object> vertexToMap(Vertex v) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", v.id().toString());
        map.put("label", v.label());
        v.properties().forEachRemaining(p -> map.put(p.key(), p.value()));
        return map;
    }

    public static Map<String, Object> edgeToMap(Edge e) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", e.id().toString());
        map.put("label", e.label());
        map.put("source", e.outVertex().id().toString());
        map.put("target", e.inVertex().id().toString());
        e.properties().forEachRemaining(p -> map.put(p.key(), p.value()));
        return map;
    }
}
