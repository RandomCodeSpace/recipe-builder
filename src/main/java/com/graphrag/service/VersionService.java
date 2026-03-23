package com.graphrag.service;

import com.graphrag.config.GraphRagProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.tinkerpop.gremlin.structure.io.IoCore.graphson;

@Service
public class VersionService {

    private static final Logger log = LoggerFactory.getLogger(VersionService.class);
    private static final String VERSIONS_BASE = "./data/versions/";

    private final TinkerGraph graph;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final AuditService auditService;
    private final GraphRagProperties props;

    public VersionService(TinkerGraph graph,
                          InMemoryEmbeddingStore<TextSegment> embeddingStore,
                          AuditService auditService,
                          GraphRagProperties props) {
        this.graph = graph;
        this.embeddingStore = embeddingStore;
        this.auditService = auditService;
        this.props = props;
    }

    public Map<String, Object> createSnapshot(String versionName, String description) {
        Path versionDir = Path.of(VERSIONS_BASE + versionName);
        try {
            Files.createDirectories(versionDir);

            // Write graph
            String graphPath = versionDir.resolve("graph.json").toString();
            graph.io(graphson()).writeGraph(graphPath);

            // Write vector store
            String vectorPath = versionDir.resolve("vector-store.json").toString();
            embeddingStore.serializeToFile(vectorPath);

            // Audit
            auditService.log("CREATE_SNAPSHOT", "version=" + versionName + " description=" + description);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("version", versionName);
            result.put("description", description);
            result.put("graphPath", graphPath);
            result.put("vectorPath", vectorPath);
            result.put("createdAt", Instant.now().toString());
            return result;
        } catch (Exception e) {
            log.error("Failed to create snapshot {}: {}", versionName, e.getMessage());
            return Map.of("error", "Failed to create snapshot: " + e.getMessage());
        }
    }

    public List<String> listVersions() {
        Path versionsDir = Path.of(VERSIONS_BASE);
        List<String> versions = new ArrayList<>();
        if (Files.exists(versionsDir)) {
            File[] dirs = versionsDir.toFile().listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    versions.add(dir.getName());
                }
            }
        }
        return versions;
    }

    public Map<String, Object> getVersionInfo(String versionName) {
        Path versionDir = Path.of(VERSIONS_BASE + versionName);
        if (!Files.exists(versionDir)) {
            return Map.of("error", "Version not found: " + versionName);
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", versionName);

        Path graphFile = versionDir.resolve("graph.json");
        Path vectorFile = versionDir.resolve("vector-store.json");

        try {
            if (Files.exists(graphFile)) {
                info.put("graphSizeBytes", Files.size(graphFile));
                info.put("graphCreatedAt", Files.getLastModifiedTime(graphFile).toInstant().toString());
            }
            if (Files.exists(vectorFile)) {
                info.put("vectorSizeBytes", Files.size(vectorFile));
                info.put("vectorCreatedAt", Files.getLastModifiedTime(vectorFile).toInstant().toString());
            }
        } catch (IOException e) {
            log.warn("Failed to read version info for {}: {}", versionName, e.getMessage());
        }

        return info;
    }
}
