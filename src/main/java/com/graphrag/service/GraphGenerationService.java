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

    interface EntityRelationshipExtractor {
        @UserMessage("""
            Extract all named entities and their relationships from the following text.
            Return only entities that are specific nouns (people, technologies, concepts, organizations).

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
        this.graphRepo = graphRepo;
        this.vectorRepo = vectorRepo;
        this.embeddingModel = embeddingModel;
        this.textChunker = textChunker;
        this.maxTextLength = maxTextLength;

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
                try {
                    ExtractionResult result = extractor.extract(chunk.content());
                    entitiesCount += processExtraction(result, chunk.chunkId(), domain);
                    relationshipsCount += result.relationships().size();
                } catch (Exception e) {
                    log.warn("LLM extraction failed for chunk {}: {}", chunk.chunkId(), e.getMessage());
                }
            }
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
