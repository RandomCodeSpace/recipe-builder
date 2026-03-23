package com.graphrag.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.graphrag.model.EntityInfo;
import com.graphrag.model.ExtractionResult;
import com.graphrag.model.RelationshipInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CodeParsingService {

    private static final Logger log = LoggerFactory.getLogger(CodeParsingService.class);

    public ExtractionResult parseCode(String content, String filename) {
        if (content == null || content.isBlank()) {
            return new ExtractionResult(List.of(), List.of());
        }
        String ext = getExtension(filename).toLowerCase();
        try {
            return switch (ext) {
                case "java" -> parseJava(content, filename);
                case "py" -> parsePython(content, filename);
                case "js", "ts", "jsx", "tsx" -> parseJavaScript(content, filename);
                default -> new ExtractionResult(List.of(), List.of());
            };
        } catch (Exception e) {
            log.warn("Code parsing failed for {}: {}", filename, e.getMessage());
            return new ExtractionResult(List.of(), List.of());
        }
    }

    private ExtractionResult parseJava(String content, String filename) {
        List<EntityInfo> entities = new ArrayList<>();
        List<RelationshipInfo> relationships = new ArrayList<>();

        CompilationUnit cu = StaticJavaParser.parse(content);

        // Extract imports
        cu.getImports().forEach(imp -> {
            String imported = imp.getNameAsString();
            relationships.add(new RelationshipInfo(filename, "IMPORTS", imported, 1.0));
        });

        // Extract classes, interfaces, enums, records
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            String className = cls.getNameAsString();
            String type = cls.isInterface() ? "interface" : "class";
            entities.add(new EntityInfo(className, type, 1.0));

            // Extends
            cls.getExtendedTypes().forEach(ext -> {
                relationships.add(new RelationshipInfo(className, "EXTENDS", ext.getNameAsString(), 1.0));
            });

            // Implements
            cls.getImplementedTypes().forEach(impl -> {
                relationships.add(new RelationshipInfo(className, "IMPLEMENTS", impl.getNameAsString(), 1.0));
            });

            // Methods
            cls.getMethods().forEach(method -> {
                String methodName = className + "." + method.getNameAsString();
                entities.add(new EntityInfo(methodName, "function", 1.0));
                relationships.add(new RelationshipInfo(className, "HAS_METHOD", methodName, 1.0));

                // Return type
                String returnType = method.getTypeAsString();
                if (!"void".equals(returnType)) {
                    relationships.add(new RelationshipInfo(methodName, "RETURN_TYPE", returnType, 0.9));
                }

                // Parameter types
                method.getParameters().forEach(param -> {
                    relationships.add(new RelationshipInfo(methodName, "PARAM_TYPE", param.getTypeAsString(), 0.9));
                });
            });

            // Fields
            cls.getFields().forEach(field -> {
                field.getVariables().forEach(var -> {
                    String fieldName = className + "." + var.getNameAsString();
                    entities.add(new EntityInfo(fieldName, "field", 1.0));
                    relationships.add(new RelationshipInfo(className, "HAS_FIELD", fieldName, 1.0));
                    relationships.add(new RelationshipInfo(fieldName, "FIELD_TYPE", field.getElementType().asString(), 0.9));
                });
            });
        });

        // Enums
        cu.findAll(EnumDeclaration.class).forEach(en -> {
            entities.add(new EntityInfo(en.getNameAsString(), "class", 1.0));
        });

        // Records (JavaParser treats them as ClassOrInterfaceDeclaration in newer versions,
        // but also check RecordDeclaration if available)
        try {
            cu.findAll(RecordDeclaration.class).forEach(rec -> {
                entities.add(new EntityInfo(rec.getNameAsString(), "class", 1.0));
            });
        } catch (Exception ignored) {
            // RecordDeclaration may not be available in all JavaParser versions
        }

        return new ExtractionResult(entities, relationships);
    }

    // Python regex patterns
    private static final Pattern PY_CLASS = Pattern.compile("^class\\s+(\\w+)(?:\\(([\\w,\\s.]+)\\))?\\s*:", Pattern.MULTILINE);
    private static final Pattern PY_FUNCTION = Pattern.compile("^def\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
    private static final Pattern PY_METHOD = Pattern.compile("^\\s+def\\s+(\\w+)\\s*\\(self", Pattern.MULTILINE);
    private static final Pattern PY_IMPORT = Pattern.compile("^(?:from\\s+(\\S+)\\s+)?import\\s+(.+)", Pattern.MULTILINE);

    private ExtractionResult parsePython(String content, String filename) {
        List<EntityInfo> entities = new ArrayList<>();
        List<RelationshipInfo> relationships = new ArrayList<>();
        String currentClass = null;

        // Classes
        Matcher classMatcher = PY_CLASS.matcher(content);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            entities.add(new EntityInfo(className, "class", 1.0));
            currentClass = className;

            // Parent classes
            if (classMatcher.group(2) != null) {
                for (String parent : classMatcher.group(2).split(",")) {
                    parent = parent.trim();
                    if (!parent.isEmpty() && !parent.equals("object")) {
                        relationships.add(new RelationshipInfo(className, "EXTENDS", parent, 1.0));
                    }
                }
            }
        }

        // Top-level functions
        Matcher funcMatcher = PY_FUNCTION.matcher(content);
        while (funcMatcher.find()) {
            String funcName = funcMatcher.group(1);
            if (!funcName.startsWith("_") || funcName.equals("__init__")) {
                entities.add(new EntityInfo(funcName, "function", 1.0));
            }
        }

        // Methods (indented def with self)
        Matcher methodMatcher = PY_METHOD.matcher(content);
        while (methodMatcher.find()) {
            String methodName = methodMatcher.group(1);
            if (currentClass != null) {
                String fullName = currentClass + "." + methodName;
                entities.add(new EntityInfo(fullName, "function", 1.0));
                relationships.add(new RelationshipInfo(currentClass, "HAS_METHOD", fullName, 1.0));
            }
        }

        // Imports
        Matcher importMatcher = PY_IMPORT.matcher(content);
        while (importMatcher.find()) {
            String module = importMatcher.group(1) != null ? importMatcher.group(1) : importMatcher.group(2).trim();
            relationships.add(new RelationshipInfo(filename, "IMPORTS", module, 1.0));
        }

        return new ExtractionResult(entities, relationships);
    }

    // JS/TS regex patterns
    private static final Pattern JS_CLASS = Pattern.compile("^(?:export\\s+)?class\\s+(\\w+)(?:\\s+extends\\s+(\\w+))?", Pattern.MULTILINE);
    private static final Pattern JS_FUNCTION = Pattern.compile("^(?:export\\s+)?(?:async\\s+)?function\\s+(\\w+)", Pattern.MULTILINE);
    private static final Pattern JS_IMPORT = Pattern.compile("^import\\s+.+\\s+from\\s+['\"](.+)['\"]", Pattern.MULTILINE);

    private ExtractionResult parseJavaScript(String content, String filename) {
        List<EntityInfo> entities = new ArrayList<>();
        List<RelationshipInfo> relationships = new ArrayList<>();

        // Classes
        Matcher classMatcher = JS_CLASS.matcher(content);
        while (classMatcher.find()) {
            String className = classMatcher.group(1);
            entities.add(new EntityInfo(className, "class", 1.0));
            if (classMatcher.group(2) != null) {
                relationships.add(new RelationshipInfo(className, "EXTENDS", classMatcher.group(2), 1.0));
            }
        }

        // Functions
        Matcher funcMatcher = JS_FUNCTION.matcher(content);
        while (funcMatcher.find()) {
            entities.add(new EntityInfo(funcMatcher.group(1), "function", 1.0));
        }

        // Imports
        Matcher importMatcher = JS_IMPORT.matcher(content);
        while (importMatcher.find()) {
            relationships.add(new RelationshipInfo(filename, "IMPORTS", importMatcher.group(1), 1.0));
        }

        return new ExtractionResult(entities, relationships);
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }
}
