package com.graphrag.service;

import com.graphrag.model.*;
import com.graphrag.repository.GraphRepository;
import com.graphrag.repository.VectorRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GraphGenerationService {

    private static final Logger log = LoggerFactory.getLogger(GraphGenerationService.class);

    private final GraphRepository graphRepo;
    private final VectorRepository vectorRepo;
    private final EmbeddingModel embeddingModel;
    private final TextChunker textChunker;
    private final EntityRelationshipExtractor extractor;
    private final int maxTextLength;
    private final ExtractionValidator validator;
    private final double confidenceThreshold;
    private final int maxRetries;

    @Autowired(required = false)
    private AuditService auditService;

    interface EntityRelationshipExtractor {
        @UserMessage("""
            Extract named entities and relationships from the following text.
            Entity types: person, organization, technology, concept, location, document.
            For each entity and relationship, provide a confidence score between 0.0 and 1.0.
            Only include entities with confidence >= 0.5.

            Text: {{text}}
            """)
        ExtractionResult extract(@V("text") String text);
    }

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
        this.maxTextLength = maxTextLength;
        this.validator = validator;
        this.confidenceThreshold = confidenceThreshold;
        this.maxRetries = maxRetries;

        if (chatModel != null) {
            this.extractor = AiServices.create(EntityRelationshipExtractor.class, chatModel);
        } else {
            this.extractor = null;
        }
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

            // Extract entities via LLM
            if (extractor != null) {
                ExtractionResult result = null;
                for (int attempt = 0; attempt <= maxRetries; attempt++) {
                    try {
                        result = extractor.extract(chunk.content());
                        break;
                    } catch (Exception e) {
                        if (attempt == maxRetries) {
                            log.warn("LLM extraction failed for chunk {} after {} retries: {}",
                                    chunk.chunkId(), maxRetries + 1, e.getMessage());
                        }
                    }
                }
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
