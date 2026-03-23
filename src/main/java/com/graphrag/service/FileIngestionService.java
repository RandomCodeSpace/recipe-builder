package com.graphrag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FileIngestionService.class);

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".go", ".rs", ".c", ".cpp", ".h", ".rb",
            ".kt", ".scala", ".xml", ".json", ".yaml", ".yml", ".properties", ".sql",
            ".sh", ".md", ".txt", ".html", ".css", ".toml", ".gradle", ".cfg", ".ini"
    );

    /** Common patterns that should always be skipped (equivalent to default .gitignore) */
    private static final List<String> DEFAULT_IGNORE_PATTERNS = List.of(
            // Build outputs
            "target/", "build/", "dist/", "out/", "bin/", ".gradle/",
            // Dependencies
            "node_modules/", "vendor/", ".venv/", "venv/", "__pycache__/",
            ".m2/", "bower_components/",
            // IDE
            ".idea/", ".vscode/", ".eclipse/", "*.iml",
            // Version control
            ".git/", ".svn/", ".hg/",
            // OS files
            ".DS_Store", "Thumbs.db",
            // Package files
            "*.jar", "*.war", "*.ear", "*.class", "*.pyc", "*.pyo",
            "*.so", "*.dll", "*.dylib", "*.exe", "*.o", "*.a",
            // Archives inside archives
            "*.zip", "*.tar", "*.gz", "*.bz2", "*.rar",
            // Images and binaries
            "*.png", "*.jpg", "*.jpeg", "*.gif", "*.ico", "*.svg",
            "*.woff", "*.woff2", "*.ttf", "*.eot",
            "*.pdf", "*.doc", "*.docx", "*.xls", "*.xlsx",
            // Lock files
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "Gemfile.lock",
            "poetry.lock", "Pipfile.lock", "composer.lock",
            // Minified files
            "*.min.js", "*.min.css", "*.bundle.js",
            // Generated
            "*.map", "*.d.ts"
    );

    private final GraphGenerationService graphGenerationService;
    private List<String> gitignorePatterns = new ArrayList<>();

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

        // Two-pass approach: first read .gitignore if present, then process files
        // For simplicity, do single pass but buffer .gitignore patterns from early entries
        gitignorePatterns = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();

                // Parse .gitignore files to collect ignore patterns
                if (entryName.endsWith(".gitignore")) {
                    String content = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8))
                            .lines().collect(Collectors.joining("\n"));
                    parseGitignore(content);
                    log.info("Loaded .gitignore with {} patterns", gitignorePatterns.size());
                    zis.closeEntry();
                    continue;
                }

                // Skip ignored files
                if (isIgnored(entryName)) {
                    filesSkipped++;
                    zis.closeEntry();
                    continue;
                }

                // Skip unsupported extensions
                if (!isSupportedFile(entryName)) {
                    filesSkipped++;
                    zis.closeEntry();
                    continue;
                }

                try {
                    String content = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8))
                            .lines().collect(Collectors.joining("\n"));

                    if (content.isBlank()) {
                        filesSkipped++;
                        zis.closeEntry();
                        continue;
                    }

                    // Skip very large files (>50KB) to avoid slow embedding
                    if (content.length() > 50_000) {
                        log.info("Skipping large file {} ({} chars)", entryName, content.length());
                        filesSkipped++;
                        fileResults.add(Map.of("file", entryName, "status", "skipped",
                                "message", "File too large: " + content.length() + " chars"));
                        zis.closeEntry();
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

                    if (filesProcessed % 20 == 0) {
                        log.info("Progress: {} files processed, {} skipped", filesProcessed, filesSkipped);
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

        log.info("ZIP ingestion complete: {} processed, {} skipped", filesProcessed, filesSkipped);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("files_processed", filesProcessed);
        summary.put("files_skipped", filesSkipped);
        summary.put("total_chunks_created", totalChunks);
        summary.put("total_entities_extracted", totalEntities);
        summary.put("total_relationships_extracted", totalRelationships);
        summary.put("file_details", fileResults);
        return summary;
    }

    /** Parse .gitignore content into patterns */
    private void parseGitignore(String content) {
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            gitignorePatterns.add(line);
        }
    }

    /** Check if a file path should be ignored based on .gitignore + default patterns */
    private boolean isIgnored(String path) {
        String lower = path.toLowerCase();

        // Always skip hidden files/directories
        for (String part : path.split("/")) {
            if (part.startsWith(".") && !part.equals(".gitignore")) return true;
        }

        // Check default ignore patterns
        for (String pattern : DEFAULT_IGNORE_PATTERNS) {
            if (pattern.endsWith("/")) {
                // Directory pattern: check if any path segment matches
                String dir = pattern.substring(0, pattern.length() - 1);
                if (lower.contains("/" + dir + "/") || lower.startsWith(dir + "/")) return true;
            } else if (pattern.startsWith("*.")) {
                // Extension pattern
                if (lower.endsWith(pattern.substring(1))) return true;
            } else {
                // Exact filename match
                if (lower.endsWith("/" + pattern.toLowerCase()) || lower.equals(pattern.toLowerCase())) return true;
            }
        }

        // Check .gitignore patterns from the ZIP
        for (String pattern : gitignorePatterns) {
            if (matchesGitignorePattern(path, pattern)) return true;
        }

        return false;
    }

    /** Simple .gitignore pattern matching */
    private boolean matchesGitignorePattern(String path, String pattern) {
        boolean negated = pattern.startsWith("!");
        if (negated) return false; // Skip negation patterns for simplicity

        pattern = pattern.trim();
        if (pattern.endsWith("/")) {
            // Directory pattern
            String dir = pattern.substring(0, pattern.length() - 1);
            return path.contains("/" + dir + "/") || path.startsWith(dir + "/");
        } else if (pattern.startsWith("*.")) {
            return path.toLowerCase().endsWith(pattern.substring(1).toLowerCase());
        } else if (pattern.contains("/")) {
            return path.startsWith(pattern) || path.contains("/" + pattern);
        } else {
            // Filename or directory name
            return path.endsWith("/" + pattern) || path.equals(pattern)
                    || path.contains("/" + pattern + "/") || path.startsWith(pattern + "/");
        }
    }

    private boolean isSupportedFile(String filename) {
        String lower = filename.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }
}
