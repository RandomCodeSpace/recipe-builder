package com.graphrag.model;
import java.util.List;
public record ExtractionResult(List<EntityInfo> entities, List<RelationshipInfo> relationships) {}
