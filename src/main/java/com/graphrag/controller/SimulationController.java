package com.graphrag.controller;

import com.graphrag.model.ExecutionTrace;
import com.graphrag.model.TraceStep;
import com.graphrag.service.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Profile("sse")
public class SimulationController {

    private final GraphGenerationService graphGenerationService;
    private final HybridSearchService hybridSearchService;
    private final GraphTraversalService graphTraversalService;
    private final ExecutionTraceService executionTraceService;
    private final VersionService versionService;
    private final FileIngestionService fileIngestionService;

    public SimulationController(GraphGenerationService graphGenerationService,
                                 HybridSearchService hybridSearchService,
                                 GraphTraversalService graphTraversalService,
                                 ExecutionTraceService executionTraceService,
                                 VersionService versionService,
                                 FileIngestionService fileIngestionService) {
        this.graphGenerationService = graphGenerationService;
        this.hybridSearchService = hybridSearchService;
        this.graphTraversalService = graphTraversalService;
        this.executionTraceService = executionTraceService;
        this.versionService = versionService;
        this.fileIngestionService = fileIngestionService;
    }

    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, String> body) {
        return graphGenerationService.ingest(
                body.get("text"), body.get("source"), body.get("domain"));
    }

    @PostMapping("/ingest/upload")
    public Map<String, Object> ingestUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("domain") String domain) {
        return fileIngestionService.ingestFile(file, domain);
    }

    @PostMapping("/search")
    public Object search(@RequestBody Map<String, Object> body) {
        String query = (String) body.get("query");
        int topK = body.containsKey("top_k") ? ((Number) body.get("top_k")).intValue() : 5;
        return hybridSearchService.search(query, topK);
    }

    @PostMapping("/traverse")
    @SuppressWarnings("unchecked")
    public Object traverse(@RequestBody Map<String, Object> body) {
        List<String> keywords = (List<String>) body.get("keywords");
        String algorithm = (String) body.get("algorithm");
        String semanticStop = (String) body.get("semantic_stop");
        return graphTraversalService.traverse(keywords, algorithm, semanticStop);
    }

    @PostMapping("/trace")
    @SuppressWarnings("unchecked")
    public Map<String, Object> recordTrace(@RequestBody Map<String, Object> body) {
        String traceId = (String) body.get("trace_id");
        String taskDesc = (String) body.get("task_description");
        String persona = (String) body.get("agent_persona");
        boolean successful = (Boolean) body.get("successful");
        List<Map<String, Object>> rawSteps = (List<Map<String, Object>>) body.get("steps");

        List<TraceStep> steps = rawSteps.stream()
                .map(s -> new TraceStep(
                        (String) s.get("tool_name"),
                        (String) s.get("reasoning"),
                        ((Number) s.get("order")).intValue()))
                .toList();

        return executionTraceService.recordTrace(
                new ExecutionTrace(traceId, taskDesc, persona, steps, successful));
    }

    @PostMapping("/versions/snapshot")
    public Map<String, Object> createSnapshot(@RequestBody Map<String, String> body) {
        return versionService.createSnapshot(body.get("version_name"), body.get("description"));
    }

    @GetMapping("/versions")
    public List<String> listVersions() {
        return versionService.listVersions();
    }

    @GetMapping("/versions/{name}")
    public Map<String, Object> getVersionInfo(@PathVariable String name) {
        return versionService.getVersionInfo(name);
    }
}
