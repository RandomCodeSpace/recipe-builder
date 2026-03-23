package com.graphrag.model;
public record EntityInfo(String name, String type, double confidence) {
    public EntityInfo(String name, String type) {
        this(name, type, 1.0);
    }
}
