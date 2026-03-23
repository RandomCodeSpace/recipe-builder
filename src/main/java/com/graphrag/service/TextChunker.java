package com.graphrag.service;

import com.graphrag.model.TextChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class TextChunker {

    public List<TextChunk> chunk(String text, String source, String domain) {
        if (text == null || text.isBlank()) return List.of();

        String[] paragraphs = text.split("\n\n+");
        List<TextChunk> chunks = new ArrayList<>();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                chunks.add(new TextChunk(
                        UUID.randomUUID().toString(),
                        trimmed,
                        source,
                        domain));
            }
        }

        return chunks;
    }
}
