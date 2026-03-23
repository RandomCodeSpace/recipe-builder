package com.graphrag.service;

import com.graphrag.config.GraphRagProperties;
import com.graphrag.entity.AuditTrailEntity;
import com.graphrag.jpa.AuditTrailJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
    "graphrag.version=test-v1",
    "graphrag.llm.provider=ollama",
    "graphrag.llm.ollama.base-url=http://localhost:11434",
    "graphrag.llm.ollama.model-name=llama3.1",
    "graphrag.llm.azure-openai.endpoint=",
    "graphrag.llm.azure-openai.api-key=",
    "graphrag.llm.azure-openai.deployment-name=gpt-4o-mini",
    "graphrag.embedding.provider=ollama",
    "graphrag.embedding.ollama.base-url=http://localhost:11434",
    "graphrag.embedding.ollama.model-name=nomic-embed-text",
    "graphrag.embedding.azure-openai.endpoint=",
    "graphrag.embedding.azure-openai.api-key=",
    "graphrag.embedding.azure-openai.deployment-name=text-embedding-3-small",
    "graphrag.ingest.max-text-length=100000",
    "graphrag.vector-store.persistence-path=./data/test/vector-store.json",
    "graphrag.graph.persistence-path=./data/test/graph.json",
    "graphrag.trace.similarity-threshold=0.7"
})
class AuditServiceTest {

    @Autowired
    private AuditTrailJpaRepository auditRepo;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        GraphRagProperties props = new GraphRagProperties(
            "test-v1",
            new GraphRagProperties.LlmProperties("ollama",
                new GraphRagProperties.OllamaProperties("http://localhost:11434", "llama3.1", null),
                new GraphRagProperties.AzureOpenAiProperties("", "", "gpt-4o-mini")),
            new GraphRagProperties.EmbeddingProperties("ollama",
                new GraphRagProperties.OllamaProperties("http://localhost:11434", "nomic-embed-text", null),
                new GraphRagProperties.AzureOpenAiProperties("", "", "text-embedding-3-small")),
            new GraphRagProperties.IngestProperties(100000),
            new GraphRagProperties.VectorStoreProperties("./data/test/vector-store.json"),
            new GraphRagProperties.GraphProperties("./data/test/graph.json"),
            new GraphRagProperties.TraceProperties(0.7)
        );
        auditService = new AuditService(auditRepo, props);
    }

    @Test
    void logSavesAuditTrailEntry() {
        auditService.log("INGEST", "source=test.txt");

        List<AuditTrailEntity> trails = auditRepo.findByVersion("test-v1");
        assertThat(trails).hasSize(1);
        assertThat(trails.get(0).getAction()).isEqualTo("INGEST");
        assertThat(trails.get(0).getDetails()).isEqualTo("source=test.txt");
        assertThat(trails.get(0).getVersion()).isEqualTo("test-v1");
        assertThat(trails.get(0).getTimestamp()).isNotNull();
    }

    @Test
    void logMultipleEntriesForSameVersion() {
        auditService.log("INGEST", "source=a.txt");
        auditService.log("SEARCH", "query=hello");
        auditService.log("RECORD_TRACE", "traceId=t-1");

        List<AuditTrailEntity> trails = auditRepo.findByVersion("test-v1");
        assertThat(trails).hasSize(3);
    }

    @Test
    void getTrailsReturnsEntriesForVersion() {
        auditService.log("INGEST", "source=b.txt");

        List<AuditTrailEntity> trails = auditService.getTrails("test-v1");
        assertThat(trails).isNotEmpty();
        assertThat(trails.stream().allMatch(t -> "test-v1".equals(t.getVersion()))).isTrue();
    }

    @Test
    void getTrailsReturnsEmptyForUnknownVersion() {
        List<AuditTrailEntity> trails = auditService.getTrails("unknown-version");
        assertThat(trails).isEmpty();
    }

    @Test
    void findByActionReturnsMatchingEntries() {
        auditService.log("INGEST", "source=c.txt");
        auditService.log("SEARCH", "query=world");

        List<AuditTrailEntity> ingests = auditRepo.findByAction("INGEST");
        assertThat(ingests).isNotEmpty();
        assertThat(ingests.stream().allMatch(t -> "INGEST".equals(t.getAction()))).isTrue();
    }
}
