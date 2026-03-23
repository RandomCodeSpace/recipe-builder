package com.graphrag.service;

import com.graphrag.model.EntityInfo;
import com.graphrag.model.ExtractionResult;
import com.graphrag.model.RelationshipInfo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ExtractionValidator {

    public ExtractionResult filter(ExtractionResult raw, double threshold) {
        // Filter entities below threshold
        List<EntityInfo> filteredEntities = raw.entities().stream()
                .filter(e -> e.confidence() >= threshold)
                .toList();

        // Collect remaining entity names
        Set<String> validNames = filteredEntities.stream()
                .map(EntityInfo::name)
                .collect(Collectors.toSet());

        // Filter relationships: keep only if both source and target survive
        List<RelationshipInfo> filteredRels = raw.relationships().stream()
                .filter(r -> r.confidence() >= threshold)
                .filter(r -> validNames.contains(r.source()) && validNames.contains(r.target()))
                .toList();

        return new ExtractionResult(filteredEntities, filteredRels);
    }
}
