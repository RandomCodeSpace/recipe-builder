package com.graphrag.service;

import com.graphrag.model.EntityInfo;
import com.graphrag.model.ExtractionResult;
import com.graphrag.model.RelationshipInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionValidatorTest {

    private final ExtractionValidator validator = new ExtractionValidator();

    @Test
    void filterRemovesLowConfidenceEntities() {
        var entities = List.of(
                new EntityInfo("LowConf", "concept", 0.3),
                new EntityInfo("HighConf", "concept", 0.9)
        );
        var raw = new ExtractionResult(entities, List.of());

        ExtractionResult filtered = validator.filter(raw, 0.5);

        assertThat(filtered.entities()).hasSize(1);
        assertThat(filtered.entities().get(0).name()).isEqualTo("HighConf");
    }

    @Test
    void filterRemovesOrphanedRelationships() {
        var entities = List.of(
                new EntityInfo("LowConf", "concept", 0.3),
                new EntityInfo("HighConf", "concept", 0.9)
        );
        var relationships = List.of(
                new RelationshipInfo("HighConf", "uses", "LowConf", 0.9)
        );
        var raw = new ExtractionResult(entities, relationships);

        ExtractionResult filtered = validator.filter(raw, 0.5);

        // LowConf is filtered out, so the relationship referencing it should also be removed
        assertThat(filtered.relationships()).isEmpty();
    }

    @Test
    void filterKeepsHighConfidenceResults() {
        var entities = List.of(
                new EntityInfo("Alpha", "concept", 0.8),
                new EntityInfo("Beta", "concept", 0.9)
        );
        var relationships = List.of(
                new RelationshipInfo("Alpha", "links", "Beta", 0.7)
        );
        var raw = new ExtractionResult(entities, relationships);

        ExtractionResult filtered = validator.filter(raw, 0.5);

        assertThat(filtered.entities()).hasSize(2);
        assertThat(filtered.relationships()).hasSize(1);
    }

    @Test
    void filterWithEmptyInput() {
        var raw = new ExtractionResult(List.of(), List.of());

        ExtractionResult filtered = validator.filter(raw, 0.5);

        assertThat(filtered.entities()).isEmpty();
        assertThat(filtered.relationships()).isEmpty();
    }

    @Test
    void backwardCompatDefaultConfidence() {
        var entity = new EntityInfo("X", "t");
        assertThat(entity.confidence()).isEqualTo(1.0);
    }
}
