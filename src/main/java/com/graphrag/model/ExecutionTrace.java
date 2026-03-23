package com.graphrag.model;
import java.util.List;
public record ExecutionTrace(String traceId, String taskDescription, String agentPersona, List<TraceStep> steps, boolean successful) {}
