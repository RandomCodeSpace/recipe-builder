package com.graphrag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphrag.model.*;
import com.graphrag.repository.GraphRepository;
import com.graphrag.repository.VectorRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GraphGenerationService {

    private static final Logger log = LoggerFactory.getLogger(GraphGenerationService.class);

    private static final String EXTRACTION_PROMPT = """
            Extract named entities and relationships from the following text.

            Return ONLY valid JSON (no markdown, no explanation) in this exact format:
            {"entities":[{"name":"...","type":"...","confidence":0.9}],"relationships":[{"source":"...","relationship":"...","target":"...","confidence":0.8}]}

            Entity types: person, organization, technology, concept, location, document.
            Confidence: 0.0 to 1.0. Only include items with confidence >= 0.5.

            Text: %s
            """;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final GraphRepository graphRepo;
    private final VectorRepository vectorRepo;
    private final EmbeddingModel embeddingModel;
    private final TextChunker textChunker;
    private final ChatLanguageModel chatModel;
    private final int maxTextLength;
    private final ExtractionValidator validator;
    private final double confidenceThreshold;
    private final int maxRetries;

    @Autowired(required = false)
    private AuditService auditService;

    @Autowired(required = false)
    private CodeParsingService codeParsingService;

    public GraphGenerationService(
            GraphRepository graphRepo,
            VectorRepository vectorRepo,
            EmbeddingModel embeddingModel,
            TextChunker textChunker,
            ChatLanguageModel chatModel,
            @Value("${graphrag.ingest.max-text-length:100000}") int maxTextLength) {
        this(graphRepo, vectorRepo, embeddingModel, textChunker, chatModel, maxTextLength,
                new ExtractionValidator(), 0.6, 2);
    }

    @Autowired
    public GraphGenerationService(
            GraphRepository graphRepo,
            VectorRepository vectorRepo,
            EmbeddingModel embeddingModel,
            TextChunker textChunker,
            ChatLanguageModel chatModel,
            @Value("${graphrag.ingest.max-text-length:100000}") int maxTextLength,
            ExtractionValidator validator,
            @Value("${graphrag.extraction.confidence-threshold:0.6}") double confidenceThreshold,
            @Value("${graphrag.extraction.max-retries:2}") int maxRetries) {
        this.graphRepo = graphRepo;
        this.vectorRepo = vectorRepo;
        this.embeddingModel = embeddingModel;
        this.textChunker = textChunker;
        this.chatModel = chatModel;
        this.maxTextLength = maxTextLength;
        this.validator = validator;
        this.confidenceThreshold = confidenceThreshold;
        this.maxRetries = maxRetries;
    }

    public Map<String, Object> ingest(String text, String source, String domain) {
        if (text.length() > maxTextLength) {
            return Map.of("error", "Text exceeds maximum length of " + maxTextLength + " characters");
        }

        List<TextChunk> chunks = textChunker.chunk(text, source, domain);
        int entitiesCount = 0;
        int relationshipsCount = 0;
        List<String> chunkIds = new ArrayList<>();

        for (TextChunk chunk : chunks) {
            chunkIds.add(chunk.chunkId());

            // Embed and store in vector repo
            Embedding embedding = embeddingModel.embed(chunk.content()).content();
            vectorRepo.store(chunk.chunkId(), chunk.content(), embedding, source, domain);

            // Store text chunk node in graph
            graphRepo.addTextChunkNode(chunk.chunkId(), chunk.content(), source, domain);

            // Extract entities via AST (codebase domain) or LLM (other domains)
            if ("codebase".equals(domain) && codeParsingService != null) {
                // AST-based extraction for code — no LLM needed
                ExtractionResult result = codeParsingService.parseCode(chunk.content(), source);
                result = validator.filter(result, confidenceThreshold);
                entitiesCount += processExtraction(result, chunk.chunkId(), domain);
                relationshipsCount += result.relationships().size();
            } else if (chatModel != null) {
                // LLM extraction for document/recipe/reference
                ExtractionResult result = extractWithRetry(chunk.content(), chunk.chunkId());
                if (result != null) {
                    result = validator.filter(result, confidenceThreshold);
                    entitiesCount += processExtraction(result, chunk.chunkId(), domain);
                    relationshipsCount += result.relationships().size();
                }
            }
        }

        if (auditService != null) {
            auditService.log("INGEST", "source=" + source);
        }

        return Map.of(
                "chunks_created", chunks.size(),
                "entities_extracted", entitiesCount,
                "relationships_extracted", relationshipsCount,
                "chunk_ids", chunkIds);
    }

    /** For testing without LLM — accepts pre-built extraction result */
    public Map<String, Object> ingestWithExtractionResult(
            String text, String source, String domain, ExtractionResult extraction) {
        if (text.length() > maxTextLength) {
            return Map.of("error", "Text exceeds maximum length of " + maxTextLength + " characters");
        }

        List<TextChunk> chunks = textChunker.chunk(text, source, domain);
        List<String> chunkIds = new ArrayList<>();

        for (TextChunk chunk : chunks) {
            chunkIds.add(chunk.chunkId());
            Embedding embedding = embeddingModel.embed(chunk.content()).content();
            vectorRepo.store(chunk.chunkId(), chunk.content(), embedding, source, domain);
            graphRepo.addTextChunkNode(chunk.chunkId(), chunk.content(), source, domain);
            processExtraction(extraction, chunk.chunkId(), domain);
        }

        return Map.of(
                "chunks_created", chunks.size(),
                "entities_extracted", extraction.entities().size(),
                "relationships_extracted", extraction.relationships().size(),
                "chunk_ids", chunkIds);
    }

    private ExtractionResult extractWithRetry(String text, String chunkId) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String prompt = String.format(EXTRACTION_PROMPT, text);
                ChatResponse response = chatModel.chat(
                        ChatRequest.builder()
                                .messages(List.of(UserMessage.from(prompt)))
                                .build());
                String rawResponse = response.aiMessage().text();
                return parseExtractionResponse(rawResponse);
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.warn("LLM extraction failed for chunk {} after {} retries: {}",
                            chunkId, maxRetries + 1, e.getMessage());
                }
            }
        }
        return null;
    }

    /** Parse LLM response, handling markdown fences, extra text, and malformed JSON */
    private ExtractionResult parseExtractionResponse(String raw) {
        try {
            // Strip markdown code fences if present
            String json = raw.strip();
            if (json.contains("```")) {
                int start = json.indexOf("```");
                int end = json.lastIndexOf("```");
                if (start != end) {
                    json = json.substring(start, end);
                    // Remove the opening ```json or ```
                    json = json.replaceFirst("```\\w*\\s*", "").strip();
                }
            }

            // Find the first { and last } to extract JSON object
            int braceStart = json.indexOf('{');
            int braceEnd = json.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                json = json.substring(braceStart, braceEnd + 1);
            }

            JsonNode root = JSON.readTree(json);

            List<EntityInfo> entities = new ArrayList<>();
            JsonNode entitiesNode = root.get("entities");
            if (entitiesNode != null && entitiesNode.isArray()) {
                for (JsonNode e : entitiesNode) {
                    String name = e.has("name") ? e.get("name").asText() : null;
                    String type = e.has("type") ? e.get("type").asText() : "unknown";
                    double conf = e.has("confidence") ? e.get("confidence").asDouble(1.0) : 1.0;
                    if (name != null && !name.isBlank()) {
                        entities.add(new EntityInfo(name, type, conf));
                    }
                }
            }

            List<RelationshipInfo> relationships = new ArrayList<>();
            JsonNode relsNode = root.get("relationships");
            if (relsNode != null && relsNode.isArray()) {
                for (JsonNode r : relsNode) {
                    String source = r.has("source") ? r.get("source").asText() : null;
                    String rel = r.has("relationship") ? r.get("relationship").asText() : null;
                    String target = r.has("target") ? r.get("target").asText() : null;
                    double conf = r.has("confidence") ? r.get("confidence").asDouble(1.0) : 1.0;
                    if (source != null && rel != null && target != null) {
                        relationships.add(new RelationshipInfo(source, rel, target, conf));
                    }
                }
            }

            return new ExtractionResult(entities, relationships);
        } catch (Exception e) {
            log.debug("Failed to parse extraction response: {}", e.getMessage());
            throw new RuntimeException("Failed to parse LLM response: " + e.getMessage(), e);
        }
    }

    private int processExtraction(ExtractionResult result, String chunkId, String domain) {
        for (EntityInfo entity : result.entities()) {
            graphRepo.addEntity(entity.name(), entity.type(), domain);
            graphRepo.linkEntityToChunk(entity.name(), chunkId);
        }
        for (RelationshipInfo rel : result.relationships()) {
            graphRepo.addEntity(rel.source(), "unknown", domain);
            graphRepo.addEntity(rel.target(), "unknown", domain);
            graphRepo.addRelationship(rel.source(), rel.target(), rel.relationship(), chunkId);
        }
        return result.entities().size();
    }
}
