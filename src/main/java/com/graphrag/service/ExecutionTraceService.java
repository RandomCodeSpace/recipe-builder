package com.graphrag.service;

import com.graphrag.model.ExecutionTrace;
import com.graphrag.model.TraceStep;
import com.graphrag.repository.GraphRepository;
import com.graphrag.repository.VectorRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ExecutionTraceService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTraceService.class);

    private final GraphRepository graphRepo;
    private final VectorRepository vectorRepo;
    private final EmbeddingModel embeddingModel;
    private final double similarityThreshold;

    public ExecutionTraceService(
            GraphRepository graphRepo,
            VectorRepository vectorRepo,
            EmbeddingModel embeddingModel,
            @Value("${graphrag.trace.similarity-threshold:0.7}") double similarityThreshold) {
        this.graphRepo = graphRepo;
        this.vectorRepo = vectorRepo;
        this.embeddingModel = embeddingModel;
        this.similarityThreshold = similarityThreshold;
    }

    public Map<String, Object> recordTrace(ExecutionTrace trace) {
        // Create trace vertex
        Vertex traceVertex = graphRepo.addExecutionTraceVertex(
                trace.traceId(), trace.taskDescription(),
                trace.agentPersona(), trace.successful());

        // Create step vertices and link them
        Vertex previousStep = null;
        int entitiesLinked = 0;

        for (TraceStep step : trace.steps()) {
            String stepId = trace.traceId() + "-step-" + step.order();
            Vertex stepVertex = graphRepo.addTraceStepVertex(
                    stepId, step.toolName(), step.reasoning(), step.order());

            graphRepo.addEdge(traceVertex, stepVertex, "HAS_STEP");

            if (previousStep != null) {
                graphRepo.addEdge(previousStep, stepVertex, "NEXT_STEP");
            }
            previousStep = stepVertex;

            // Entity linking via embedding similarity
            try {
                Embedding reasoningEmbedding = embeddingModel.embed(step.reasoning()).content();
                var matches = vectorRepo.search(reasoningEmbedding, 3);
                for (var match : matches) {
                    if (match.score() >= similarityThreshold) {
                        // Find entities mentioned in this chunk
                        var entities = graphRepo.traversalSource()
                                .V().hasLabel("TextChunk").has("chunkId", match.chunkId())
                                .in("MENTIONED_IN").hasLabel("Entity")
                                .toList();
                        for (var entity : entities) {
                            graphRepo.addEdge(stepVertex, entity, "USED_ENTITY");
                            entitiesLinked++;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Entity linking failed for step {}: {}", stepId, e.getMessage());
            }
        }

        return Map.of(
                "trace_id", trace.traceId(),
                "stored", true,
                "entities_linked", entitiesLinked);
    }
}
