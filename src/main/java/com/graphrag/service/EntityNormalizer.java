package com.graphrag.service;

import org.springframework.stereotype.Component;

@Component
public class EntityNormalizer {

    /**
     * Normalize: trim, collapse whitespace, lowercase.
     */
    public String normalize(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    /**
     * Levenshtein-based similarity: 0.0 to 1.0.
     * similarity = 1.0 - (editDistance / max(len1, len2))
     */
    public double similarity(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1.0;
        int dist = levenshteinDistance(a, b);
        return 1.0 - ((double) dist / maxLen);
    }

    /**
     * Compute Levenshtein edit distance between two strings.
     */
    private int levenshteinDistance(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        int[][] dp = new int[lenA + 1][lenB + 1];

        for (int i = 0; i <= lenA; i++) dp[i][0] = i;
        for (int j = 0; j <= lenB; j++) dp[0][j] = j;

        for (int i = 1; i <= lenA; i++) {
            for (int j = 1; j <= lenB; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                                    Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[lenA][lenB];
    }
}
