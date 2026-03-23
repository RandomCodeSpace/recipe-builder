package com.graphrag.service;

import com.graphrag.model.TextChunk;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker();

    @Test
    void splitsByParagraph() {
        String text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph.";
        List<TextChunk> chunks = chunker.chunk(text, "test.txt", "document");
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).content()).isEqualTo("First paragraph.");
        assertThat(chunks.get(1).content()).isEqualTo("Second paragraph.");
    }

    @Test
    void assignsUniqueChunkIds() {
        String text = "Para one.\n\nPara two.";
        List<TextChunk> chunks = chunker.chunk(text, "test.txt", "document");
        assertThat(chunks.get(0).chunkId()).isNotEqualTo(chunks.get(1).chunkId());
    }

    @Test
    void singleParagraphReturnsOneChunk() {
        String text = "Just one paragraph with no double newlines.";
        List<TextChunk> chunks = chunker.chunk(text, "test.txt", "document");
        assertThat(chunks).hasSize(1);
    }

    @Test
    void emptyTextReturnsEmpty() {
        List<TextChunk> chunks = chunker.chunk("", "test.txt", "document");
        assertThat(chunks).isEmpty();
    }

    @Test
    void preservesSourceAndDomain() {
        List<TextChunk> chunks = chunker.chunk("Hello.", "myfile.java", "codebase");
        assertThat(chunks.get(0).source()).isEqualTo("myfile.java");
        assertThat(chunks.get(0).domain()).isEqualTo("codebase");
    }

    @Test
    void splitsLargeTextExceedingMaxChunkSize() {
        // Create text larger than MAX_CHUNK_CHARS with no double newlines
        String largeText = "word ".repeat(2000); // ~10000 chars, well over 6000 limit
        List<TextChunk> chunks = chunker.chunk(largeText, "big.txt", "document");
        assertThat(chunks.size()).isGreaterThan(1);
        for (TextChunk chunk : chunks) {
            assertThat(chunk.content().length()).isLessThanOrEqualTo(TextChunker.MAX_CHUNK_CHARS);
        }
    }

    @Test
    void largeTextWithNoBreakPointsStillChunks() {
        // Continuous text with no spaces/newlines — forces hard cuts
        String largeText = "x".repeat(TextChunker.MAX_CHUNK_CHARS * 2);
        List<TextChunk> chunks = chunker.chunk(largeText, "src.java", "codebase");
        assertThat(chunks.size()).isGreaterThan(1);
        for (TextChunk chunk : chunks) {
            assertThat(chunk.content().length()).isLessThanOrEqualTo(TextChunker.MAX_CHUNK_CHARS);
            assertThat(chunk.source()).isEqualTo("src.java");
            assertThat(chunk.domain()).isEqualTo("codebase");
        }
    }
}
