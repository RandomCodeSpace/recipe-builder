package com.graphrag.service;

import com.graphrag.model.TextChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class TextChunker {

    // ~1500 tokens for nomic-embed-text (8192 token context); conservative to leave headroom
    static final int MAX_CHUNK_CHARS = 6000;
    static final int OVERLAP_CHARS = 200;

    public List<TextChunk> chunk(String text, String source, String domain) {
        if (text == null || text.isBlank()) return List.of();

        String[] paragraphs = text.split("\n\n+");
        List<TextChunk> chunks = new ArrayList<>();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.length() <= MAX_CHUNK_CHARS) {
                chunks.add(new TextChunk(
                        UUID.randomUUID().toString(),
                        trimmed,
                        source,
                        domain));
            } else {
                chunks.addAll(splitLargeText(trimmed, source, domain));
            }
        }

        return chunks;
    }

    private List<TextChunk> splitLargeText(String text, String source, String domain) {
        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_CHUNK_CHARS, text.length());

            // Try to break at a newline or sentence boundary
            if (end < text.length()) {
                int breakAt = findBreakPoint(text, start, end);
                if (breakAt > start) {
                    end = breakAt;
                }
            }

            chunks.add(new TextChunk(
                    UUID.randomUUID().toString(),
                    text.substring(start, end).trim(),
                    source,
                    domain));

            // If this was the last chunk, stop
            if (end >= text.length()) break;

            // Advance with overlap, but always guarantee forward progress
            int nextStart = end - OVERLAP_CHARS;
            start = Math.max(nextStart, start + 1);
        }
        return chunks;
    }

    private int findBreakPoint(String text, int start, int end) {
        // Prefer newline break
        int newline = text.lastIndexOf('\n', end - 1);
        if (newline > start + MAX_CHUNK_CHARS / 2) return newline + 1;

        // Fall back to sentence break (. ! ?)
        for (int i = end - 1; i > start + MAX_CHUNK_CHARS / 2; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }

        // Fall back to space
        int space = text.lastIndexOf(' ', end - 1);
        if (space > start + MAX_CHUNK_CHARS / 2) return space + 1;

        // Hard cut
        return end;
    }
}
