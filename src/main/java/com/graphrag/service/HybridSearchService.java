package com.graphrag.service;

import com.graphrag.model.SearchResult;
import com.graphrag.repository.GraphRepository;
import com.graphrag.repository.VectorRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HybridSearchService {

    private final GraphRepository graphRepo;
    private final VectorRepository vectorRepo;
    private final EmbeddingModel embeddingModel;

    @Autowired(required = false)
    private AuditService auditService;

    public HybridSearchService(GraphRepository graphRepo,
                                VectorRepository vectorRepo,
                                EmbeddingModel embeddingModel) {
        this.graphRepo = graphRepo;
        this.vectorRepo = vectorRepo;
        this.embeddingModel = embeddingModel;
    }

    public SearchResult search(String query, int topK) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        var vectorResults = vectorRepo.search(queryEmbedding, topK);

        if (vectorResults.isEmpty()) {
            return new SearchResult("", List.of(), List.of());
        }

        List<String> chunkIds = vectorResults.stream()
                .map(VectorRepository.ChunkSearchResult::chunkId)
                .toList();

        String textContext = vectorResults.stream()
                .map(VectorRepository.ChunkSearchResult::content)
                .collect(Collectors.joining("\n\n"));

        if (auditService != null) {
            auditService.log("SEARCH", "query=" + query);
        }

        SearchResult graphResult = graphRepo.getSubgraphByChunkIds(chunkIds);

        return new SearchResult(textContext, graphResult.nodes(), graphResult.edges());
    }
}
