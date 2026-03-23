package com.graphrag.service;

import com.graphrag.model.EntityInfo;
import com.graphrag.model.ExtractionResult;
import com.graphrag.model.RelationshipInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeParsingServiceTest {

    private final CodeParsingService service = new CodeParsingService();

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<String> entityNames(ExtractionResult r) {
        return r.entities().stream().map(EntityInfo::name).toList();
    }

    private List<String> relTypes(ExtractionResult r) {
        return r.relationships().stream().map(RelationshipInfo::relationship).toList();
    }

    private boolean hasRel(ExtractionResult r, String src, String type, String tgt) {
        return r.relationships().stream().anyMatch(rel ->
                rel.source().equals(src) && rel.relationship().equals(type) && rel.target().equals(tgt));
    }

    // ── Java tests ────────────────────────────────────────────────────────────

    @Test
    void javaClassAndMethodEntitiesExtracted() {
        String javaCode = """
                package com.example;

                import java.util.List;
                import java.util.Map;

                public class UserService extends BaseService implements Serializable {
                    private String name;
                    private int age;

                    public List<User> findAll() {
                        return List.of();
                    }

                    public void save(User user) {
                    }
                }
                """;

        ExtractionResult result = service.parseCode(javaCode, "UserService.java");

        assertThat(entityNames(result)).contains("UserService");
        assertThat(entityNames(result)).contains("UserService.findAll");
        assertThat(entityNames(result)).contains("UserService.save");
    }

    @Test
    void javaFieldEntitiesExtracted() {
        String javaCode = """
                package com.example;

                public class UserService extends BaseService implements Serializable {
                    private String name;
                    private int age;

                    public void save(User user) {
                    }
                }
                """;

        ExtractionResult result = service.parseCode(javaCode, "UserService.java");

        assertThat(entityNames(result)).contains("UserService.name");
    }

    @Test
    void javaExtendsAndImplementsRelationshipsExtracted() {
        String javaCode = """
                package com.example;

                import java.util.List;
                import java.util.Map;

                public class UserService extends BaseService implements Serializable {
                    private String name;

                    public List<User> findAll() {
                        return List.of();
                    }
                }
                """;

        ExtractionResult result = service.parseCode(javaCode, "UserService.java");

        assertThat(hasRel(result, "UserService", "EXTENDS", "BaseService")).isTrue();
        assertThat(hasRel(result, "UserService", "IMPLEMENTS", "Serializable")).isTrue();
    }

    @Test
    void javaImportsExtracted() {
        String javaCode = """
                package com.example;

                import java.util.List;
                import java.util.Map;

                public class UserService {
                }
                """;

        ExtractionResult result = service.parseCode(javaCode, "UserService.java");

        assertThat(hasRel(result, "UserService.java", "IMPORTS", "java.util.List")).isTrue();
        assertThat(hasRel(result, "UserService.java", "IMPORTS", "java.util.Map")).isTrue();
    }

    @Test
    void javaHasMethodRelationshipExtracted() {
        String javaCode = """
                public class UserService {
                    public void save(User user) {
                    }
                }
                """;

        ExtractionResult result = service.parseCode(javaCode, "UserService.java");

        assertThat(hasRel(result, "UserService", "HAS_METHOD", "UserService.save")).isTrue();
    }

    @Test
    void javaInterfaceTypeExtracted() {
        String javaCode = """
                public interface UserRepository {
                    void save(Object o);
                }
                """;

        ExtractionResult result = service.parseCode(javaCode, "UserRepository.java");

        assertThat(result.entities()).anyMatch(e ->
                e.name().equals("UserRepository") && e.type().equals("interface"));
    }

    // ── Python tests ──────────────────────────────────────────────────────────

    @Test
    void pythonClassAndMethodEntitiesExtracted() {
        String pythonCode = """
                from flask import Flask
                import os

                class UserController(BaseController):
                    def __init__(self):
                        pass

                    def get_users(self):
                        pass

                def helper():
                    pass
                """;

        ExtractionResult result = service.parseCode(pythonCode, "controller.py");

        assertThat(entityNames(result)).contains("UserController");
        assertThat(entityNames(result)).contains("UserController.get_users");
    }

    @Test
    void pythonInheritanceRelationshipExtracted() {
        String pythonCode = """
                class UserController(BaseController):
                    def get_users(self):
                        pass
                """;

        ExtractionResult result = service.parseCode(pythonCode, "controller.py");

        assertThat(hasRel(result, "UserController", "EXTENDS", "BaseController")).isTrue();
    }

    @Test
    void pythonImportsExtracted() {
        String pythonCode = """
                from flask import Flask
                import os
                """;

        ExtractionResult result = service.parseCode(pythonCode, "app.py");

        assertThat(hasRel(result, "app.py", "IMPORTS", "flask")).isTrue();
        assertThat(hasRel(result, "app.py", "IMPORTS", "os")).isTrue();
    }

    // ── Markdown tests ────────────────────────────────────────────────────────

    @Test
    void parseMarkdownExtractsHeadings() {
        String md = """
                # Title
                ## Section One
                ### Subsection
                ## Section Two
                """;

        ExtractionResult result = service.parseCode(md, "doc.md");

        assertThat(entityNames(result)).contains("Title", "Section One", "Subsection", "Section Two");
        assertThat(result.entities()).hasSize(4);
        assertThat(hasRel(result, "Title", "CONTAINS", "Section One")).isTrue();
        assertThat(hasRel(result, "Section One", "CONTAINS", "Subsection")).isTrue();
        assertThat(hasRel(result, "Title", "CONTAINS", "Section Two")).isTrue();
    }

    @Test
    void parseMarkdownExtractsLinks() {
        String md = """
                # API Guide
                See [documentation](https://docs.example.com) for details.
                """;

        ExtractionResult result = service.parseCode(md, "api.md");

        assertThat(hasRel(result, "API Guide", "LINKS_TO", "https://docs.example.com")).isTrue();
    }

    @Test
    void parseEmptyMarkdown() {
        ExtractionResult result = service.parseCode("", "empty.md");

        assertThat(result.entities()).isEmpty();
        assertThat(result.relationships()).isEmpty();
    }

    // ── JSON tests ────────────────────────────────────────────────────────────

    @Test
    void parseJsonExtractsTopLevelKeys() {
        String json = """
                {"name": "GraphRAG", "version": "1.0", "config": {"port": 8080, "host": "localhost"}}
                """;

        ExtractionResult result = service.parseCode(json, "settings.json");

        assertThat(entityNames(result)).contains("name", "version", "config");
        assertThat(entityNames(result)).contains("config.port", "config.host");
    }

    @Test
    void parseInvalidJson() {
        ExtractionResult result = service.parseCode("{invalid json!!!", "bad.json");

        assertThat(result.entities()).isEmpty();
        assertThat(result.relationships()).isEmpty();
    }

    // ── YAML tests ────────────────────────────────────────────────────────────

    @Test
    void parseYamlExtractsStructure() {
        String yaml = """
                server:
                  port: 8080
                  host: localhost
                database:
                  url: jdbc:h2:file:./data
                """;

        ExtractionResult result = service.parseCode(yaml, "application.yaml");

        assertThat(entityNames(result)).contains("server", "database");
        assertThat(entityNames(result)).contains("server.port", "server.host", "database.url");
    }

    // ── JavaScript tests ──────────────────────────────────────────────────────

    @Test
    void javascriptClassAndFunctionEntitiesExtracted() {
        String jsCode = """
                import { useState } from 'react';
                import axios from 'axios';

                export class UserService extends BaseService {
                    fetchUsers() {}
                }

                export async function loadData() {}
                """;

        ExtractionResult result = service.parseCode(jsCode, "UserService.js");

        assertThat(entityNames(result)).contains("UserService");
        assertThat(entityNames(result)).contains("loadData");
        assertThat(hasRel(result, "UserService", "EXTENDS", "BaseService")).isTrue();
        assertThat(hasRel(result, "UserService.js", "IMPORTS", "react")).isTrue();
        assertThat(hasRel(result, "UserService.js", "IMPORTS", "axios")).isTrue();
    }
}
