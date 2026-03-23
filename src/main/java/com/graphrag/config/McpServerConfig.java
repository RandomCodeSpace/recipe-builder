package com.graphrag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphrag.mcp.McpToolProvider;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class McpServerConfig {

    @Bean
    @Profile({"stdio", "default"})
    public McpServerTransportProvider stdioTransportProvider(ObjectMapper objectMapper) {
        return new StdioServerTransportProvider(objectMapper);
    }

    @Bean
    @Profile("sse")
    public WebMvcSseServerTransportProvider sseTransportProvider(ObjectMapper objectMapper) {
        return new WebMvcSseServerTransportProvider(objectMapper, "/mcp/message");
    }

    @Bean
    @Profile("sse")
    public RouterFunction<ServerResponse> mcpRouterFunction(
            WebMvcSseServerTransportProvider transport) {
        return transport.getRouterFunction();
    }

    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public McpSyncServer mcpSyncServer(McpServerTransportProvider transportProvider,
                                        McpToolProvider toolProvider) {
        return McpServer.sync(transportProvider)
                .serverInfo("graphrag-mcp-server", "1.0.0")
                .instructions("Hybrid GraphRAG memory server with 4 tools: hybrid_graph_weave_search, creative_graph_traversal, record_execution_trace, ingest_text")
                .tools(
                        new McpServerFeatures.SyncToolSpecification(
                                toolProvider.hybridSearchTool(),
                                (exchange, args) -> toolProvider.handleHybridSearch(args)),
                        new McpServerFeatures.SyncToolSpecification(
                                toolProvider.traversalTool(),
                                (exchange, args) -> toolProvider.handleTraversal(args)),
                        new McpServerFeatures.SyncToolSpecification(
                                toolProvider.recordTraceTool(),
                                (exchange, args) -> toolProvider.handleRecordTrace(args)),
                        new McpServerFeatures.SyncToolSpecification(
                                toolProvider.ingestTextTool(),
                                (exchange, args) -> toolProvider.handleIngestText(args))
                )
                .build();
    }
}
