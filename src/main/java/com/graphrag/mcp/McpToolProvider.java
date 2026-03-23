package com.graphrag.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphrag.model.ExecutionTrace;
import com.graphrag.model.TraceStep;
import com.graphrag.service.ExecutionTraceService;
import com.graphrag.service.GraphGenerationService;
import com.graphrag.service.GraphTraversalService;
import com.graphrag.service.HybridSearchService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class McpToolProvider {

    private final GraphGenerationService graphGenerationService;
    private final HybridSearchService hybridSearchService;
    private final GraphTraversalService graphTraversalService;
    private final ExecutionTraceService executionTraceService;
    private final ObjectMapper objectMapper;

    public McpToolProvider(GraphGenerationService graphGenerationService,
                            HybridSearchService hybridSearchService,
                            GraphTraversalService graphTraversalService,
                            ExecutionTraceService executionTraceService,
                            ObjectMapper objectMapper) {
        this.graphGenerationService = graphGenerationService;
        this.hybridSearchService = hybridSearchService;
        this.graphTraversalService = graphTraversalService;
        this.executionTraceService = executionTraceService;
        this.objectMapper = objectMapper;
    }

    public Tool hybridSearchTool() {
        return new Tool(
                "hybrid_graph_weave_search",
                "Performs a Hybrid GraphRAG search: embeds the query, retrieves top-K matching text chunks via cosine similarity, and maps chunk IDs to retrieve the connected subgraph.",
                new JsonSchema("object",
                        Map.of("query", Map.of("type", "string", "description", "Search query"),
                                "top_k", Map.of("type", "integer", "default", 5, "description", "Number of results")),
                        List.of("query"),
                        null, null, null));
    }

    public CallToolResult handleHybridSearch(Map<String, Object> args) {
        try {
            String query = (String) args.get("query");
            int topK = args.containsKey("top_k")
                    ? ((Number) args.get("top_k")).intValue() : 5;

            var result = hybridSearchService.search(query, topK);
            String json = objectMapper.writeValueAsString(result);
            return new CallToolResult(List.of(new TextContent(json)), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public Tool traversalTool() {
        return new Tool(
                "creative_graph_traversal",
                "Executes graph traversal (BFS, DFS, or SHORTEST_PATH) between keyword nodes with optional semantic_stop constraint.",
                new JsonSchema("object",
                        Map.of("keywords", Map.of("type", "array", "items", Map.of("type", "string"),
                                        "description", "Entity names to find paths between"),
                                "algorithm", Map.of("type", "string", "enum", List.of("BFS", "DFS", "SHORTEST_PATH"),
                                        "description", "Traversal algorithm"),
                                "semantic_stop", Map.of("type", "string",
                                        "description", "Optional node that paths must pass through")),
                        List.of("keywords", "algorithm"),
                        null, null, null));
    }

    @SuppressWarnings("unchecked")
    public CallToolResult handleTraversal(Map<String, Object> args) {
        try {
            List<String> keywords = (List<String>) args.get("keywords");
            String algorithm = (String) args.get("algorithm");
            String semanticStop = (String) args.get("semantic_stop");

            var result = graphTraversalService.traverse(keywords, algorithm, semanticStop);
            String json = objectMapper.writeValueAsString(result);
            return new CallToolResult(List.of(new TextContent(json)), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public Tool recordTraceTool() {
        return new Tool(
                "record_execution_trace",
                "Records an agent's execution trace as a connected graph structure for long-term memory.",
                new JsonSchema("object",
                        Map.of("trace_id", Map.of("type", "string"),
                                "task_description", Map.of("type", "string"),
                                "agent_persona", Map.of("type", "string"),
                                "steps", Map.of("type", "array", "items", Map.of("type", "object")),
                                "successful", Map.of("type", "boolean")),
                        List.of("trace_id", "task_description", "agent_persona", "steps", "successful"),
                        null, null, null));
    }

    @SuppressWarnings("unchecked")
    public CallToolResult handleRecordTrace(Map<String, Object> args) {
        try {
            String traceId = (String) args.get("trace_id");
            String taskDesc = (String) args.get("task_description");
            String persona = (String) args.get("agent_persona");
            boolean successful = (Boolean) args.get("successful");
            List<Map<String, Object>> rawSteps =
                    (List<Map<String, Object>>) args.get("steps");

            List<TraceStep> steps = rawSteps.stream()
                    .map(s -> new TraceStep(
                            (String) s.get("tool_name"),
                            (String) s.get("reasoning"),
                            ((Number) s.get("order")).intValue()))
                    .toList();

            var trace = new ExecutionTrace(traceId, taskDesc, persona, steps, successful);
            var result = executionTraceService.recordTrace(trace);
            String json = objectMapper.writeValueAsString(result);
            return new CallToolResult(List.of(new TextContent(json)), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }

    public Tool ingestTextTool() {
        return new Tool(
                "ingest_text",
                "Ingests text into the GraphRAG system: chunks it, generates embeddings, extracts entities/relationships via LLM, and stores everything in the graph and vector databases.",
                new JsonSchema("object",
                        Map.of("text", Map.of("type", "string", "description", "Text to ingest"),
                                "source", Map.of("type", "string", "description", "Source identifier"),
                                "domain", Map.of("type", "string", "enum",
                                        List.of("codebase", "document", "recipe", "reference"),
                                        "description", "Memory domain")),
                        List.of("text", "source", "domain"),
                        null, null, null));
    }

    public CallToolResult handleIngestText(Map<String, Object> args) {
        try {
            String text = (String) args.get("text");
            String source = (String) args.get("source");
            String domain = (String) args.get("domain");

            var result = graphGenerationService.ingest(text, source, domain);
            if (result.containsKey("error")) {
                return new CallToolResult(
                        List.of(new TextContent((String) result.get("error"))), true);
            }
            String json = objectMapper.writeValueAsString(result);
            return new CallToolResult(List.of(new TextContent(json)), false);
        } catch (Exception e) {
            return new CallToolResult(List.of(new TextContent("Error: " + e.getMessage())), true);
        }
    }
}
