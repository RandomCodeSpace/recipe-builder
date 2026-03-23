package com.graphrag.model;
import java.util.List;
import java.util.Map;
public record SearchResult(String textContext, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {}
