package com.docprocessor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Counts words with a HashMap (O(1) average insert/lookup).
 */
public class WordFrequencyAnalyzer {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}']+");

    /** Word frequencies of a single text. */
    public Map<String, Integer> analyze(String text) {
        Map<String, Integer> freq = new HashMap<>();
        for (String token : NON_WORD.split(text.toLowerCase(Locale.ROOT))) {
            String word = stripApostrophes(token);
            if (!word.isEmpty()) {
                freq.merge(word, 1, Integer::sum);
            }
        }
        return freq;
    }

    /** Adds the counts of {@code source} into {@code target}. */
    public void mergeInto(Map<String, Integer> target, Map<String, Integer> source) {
        source.forEach((word, count) -> target.merge(word, count, Integer::sum));
    }

    private static String stripApostrophes(String s) {
        int start = 0, end = s.length();
        while (start < end && s.charAt(start) == '\'') start++;
        while (end > start && s.charAt(end - 1) == '\'') end--;
        return s.substring(start, end);
    }
}
