package com.graphrag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityNormalizerTest {

    private EntityNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new EntityNormalizer();
    }

    @Test
    void normalizeTrimsAndLowercases() {
        assertThat(normalizer.normalize("  Spring Boot  ")).isEqualTo("spring boot");
    }

    @Test
    void normalizeCollapsesWhitespace() {
        assertThat(normalizer.normalize("Spring   Boot")).isEqualTo("spring boot");
    }

    @Test
    void similarityExactMatch() {
        assertThat(normalizer.similarity("spring boot", "spring boot")).isEqualTo(1.0);
    }

    @Test
    void similarityCloseMatch() {
        // "springboot" vs "spring boot" — one space difference
        double sim = normalizer.similarity("springboot", "spring boot");
        assertThat(sim).isGreaterThan(0.8);
    }

    @Test
    void similarityDifferentStrings() {
        double sim = normalizer.similarity("java", "python");
        assertThat(sim).isLessThan(0.5);
    }

    @Test
    void levenshteinDistanceBasicCases() {
        // "springboot" (10 chars) vs "spring boot" (11 chars): 1 insertion
        // similarity = 1 - 1/11 ≈ 0.909
        double sim = normalizer.similarity("springboot", "spring boot");
        assertThat(sim).isGreaterThan(0.85);

        // identical strings: distance 0, similarity 1.0
        assertThat(normalizer.similarity("hello", "hello")).isEqualTo(1.0);

        // completely different short strings: distance should be high
        assertThat(normalizer.similarity("abc", "xyz")).isLessThan(0.5);

        // empty vs empty
        assertThat(normalizer.similarity("", "")).isEqualTo(1.0);

        // one empty
        assertThat(normalizer.similarity("abc", "")).isEqualTo(0.0);
    }
}
