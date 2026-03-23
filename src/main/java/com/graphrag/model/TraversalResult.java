package com.graphrag.model;
import java.util.List;
import java.util.Map;
public record TraversalResult(List<List<Map<String, Object>>> paths, List<Map<String, Object>> nodes, List<Map<String, Object>> edges, List<String> warnings) {}
