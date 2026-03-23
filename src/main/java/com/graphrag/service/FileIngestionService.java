package com.graphrag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FileIngestionService.class);

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".go", ".rs", ".c", ".cpp", ".h", ".rb",
            ".kt", ".scala", ".xml", ".json", ".yaml", ".yml", ".properties", ".sql",
            ".sh", ".md", ".txt", ".html", ".css", ".toml", ".gradle", ".cfg", ".ini"
    );

    private final GraphGenerationService graphGenerationService;

    public FileIngestionService(GraphGenerationService graphGenerationService) {
        this.graphGenerationService = graphGenerationService;
    }

    public Map<String, Object> ingestFile(MultipartFile file, String domain) {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".zip")) {
            return ingestZip(file, domain);
        }
        return ingestSingleFile(file, domain);
    }

    private Map<String, Object> ingestSingleFile(MultipartFile file, String domain) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
            return graphGenerationService.ingest(content, filename, domain);
        } catch (Exception e) {
            log.error("Failed to ingest file {}: {}", file.getOriginalFilename(), e.getMessage());
            return Map.of("error", "Failed to read file: " + e.getMessage());
        }
    }

    private Map<String, Object> ingestZip(MultipartFile file, String domain) {
        List<Map<String, Object>> fileResults = new ArrayList<>();
        int totalChunks = 0;
        int totalEntities = 0;
        int totalRelationships = 0;
        int filesProcessed = 0;
        int filesSkipped = 0;

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                if (!isSupportedFile(entryName)) {
                    filesSkipped++;
                    continue;
                }

                try {
                    String content = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8))
                            .lines().collect(Collectors.joining("\n"));

                    if (content.isBlank()) {
                        filesSkipped++;
                        continue;
                    }

                    Map<String, Object> result = graphGenerationService.ingest(content, entryName, domain);

                    if (result.containsKey("error")) {
                        fileResults.add(Map.of("file", entryName, "status", "error", "message", result.get("error")));
                    } else {
                        filesProcessed++;
                        totalChunks += ((Number) result.get("chunks_created")).intValue();
                        totalEntities += ((Number) result.get("entities_extracted")).intValue();
                        totalRelationships += ((Number) result.get("relationships_extracted")).intValue();
                        fileResults.add(Map.of("file", entryName, "status", "ok",
                                "chunks", result.get("chunks_created")));
                    }
                } catch (Exception e) {
                    log.warn("Failed to process ZIP entry {}: {}", entryName, e.getMessage());
                    fileResults.add(Map.of("file", entryName, "status", "error", "message", e.getMessage()));
                }

                zis.closeEntry();
            }
        } catch (Exception e) {
            log.error("Failed to process ZIP file: {}", e.getMessage());
            return Map.of("error", "Failed to process ZIP: " + e.getMessage());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("files_processed", filesProcessed);
        summary.put("files_skipped", filesSkipped);
        summary.put("total_chunks_created", totalChunks);
        summary.put("total_entities_extracted", totalEntities);
        summary.put("total_relationships_extracted", totalRelationships);
        summary.put("file_details", fileResults);
        return summary;
    }

    private boolean isSupportedFile(String filename) {
        String lower = filename.toLowerCase();
        // Skip hidden files and common non-text files
        if (lower.contains("/.") || lower.startsWith(".")) return false;
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }
}
