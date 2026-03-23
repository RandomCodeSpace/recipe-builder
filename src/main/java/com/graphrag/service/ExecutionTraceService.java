package com.graphrag.service;

import com.graphrag.config.GraphRagProperties;
import com.graphrag.entity.RecipeEntity;
import com.graphrag.jpa.RecipeJpaRepository;
import com.graphrag.model.ExecutionTrace;
import com.graphrag.model.TraceStep;
import com.graphrag.repository.GraphRepository;
import com.graphrag.repository.VectorRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class ExecutionTraceService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTraceService.class);

    private final GraphRepository graphRepo;
    private final VectorRepository vectorRepo;
    private final EmbeddingModel embeddingModel;
    private final double similarityThreshold;

    @Autowired(required = false)
    private RecipeJpaRepository recipeRepo;

    @Autowired(required = false)
    private GraphRagProperties graphRagProperties;

    @Autowired(required = false)
    private AuditService auditService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

        // Persist recipe to H2 if repo is available
        if (recipeRepo != null) {
            try {
                RecipeEntity recipe = new RecipeEntity();
                recipe.setTraceId(trace.traceId());
                recipe.setTaskDescription(trace.taskDescription());
                recipe.setAgentPersona(trace.agentPersona());
                recipe.setSuccessful(trace.successful());
                recipe.setSourceVersion(graphRagProperties != null ? graphRagProperties.version() : "unknown");
                recipe.setCreatedAt(Instant.now());
                try {
                    recipe.setStepsJson(objectMapper.writeValueAsString(trace.steps()));
                } catch (Exception e) {
                    recipe.setStepsJson("[]");
                }
                recipeRepo.save(recipe);
            } catch (Exception e) {
                log.warn("Failed to persist recipe to H2: {}", e.getMessage());
            }
        }

        // Audit logging
        if (auditService != null) {
            auditService.log("RECORD_TRACE", "traceId=" + trace.traceId());
        }

        return Map.of(
                "trace_id", trace.traceId(),
                "stored", true,
                "entities_linked", entitiesLinked);
    }
}
