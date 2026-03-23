package com.graphrag.model;
public record RelationshipInfo(String source, String relationship, String target, double confidence) {
    public RelationshipInfo(String source, String relationship, String target) {
        this(source, relationship, target, 1.0);
    }
}
