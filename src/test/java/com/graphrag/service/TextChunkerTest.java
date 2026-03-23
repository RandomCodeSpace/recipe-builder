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
}
