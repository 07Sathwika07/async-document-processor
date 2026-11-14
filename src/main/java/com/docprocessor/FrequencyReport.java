package com.docprocessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Builds a ranked report. A TreeMap keyed by frequency (descending) arranges
 * the words, and lambda expressions (Predicates) filter which words are kept.
 */
public class FrequencyReport {

    public static final Set<String> STOP_WORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "for", "with",
            "is", "are", "was", "were", "be", "been", "it", "its", "this", "that", "these",
            "those", "as", "by", "from", "not", "so", "if", "than", "then", "there", "their",
            "which", "have", "has", "had", "i", "you", "he", "she", "we", "they", "do", "does");

    private final Map<String, Integer> frequencies;

    public FrequencyReport(Map<String, Integer> frequencies) {
        this.frequencies = frequencies;
    }

    // ----- lambda-based filters -------------------------------------------------

    public static Predicate<Map.Entry<String, Integer>> minFrequency(int min) {
        return e -> e.getValue() >= min;
    }

    public static Predicate<Map.Entry<String, Integer>> minLength(int len) {
        return e -> e.getKey().length() >= len;
    }

    public static Predicate<Map.Entry<String, Integer>> excludeStopWords() {
        return e -> !STOP_WORDS.contains(e.getKey());
    }

    // ----- TreeMap arrangement --------------------------------------------------

    /**
     * Frequency -> words with that frequency. TreeMap with a reversed comparator
     * keeps the most frequent words first; words with equal frequency are sorted alphabetically.
     */
    public TreeMap<Integer, List<String>> arrangeByFrequency(Predicate<Map.Entry<String, Integer>> filter) {
        TreeMap<Integer, List<String>> byFrequency = new TreeMap<>(Collections.reverseOrder());
        frequencies.entrySet().stream()
                .filter(filter)
                .forEach(e -> byFrequency.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey()));
        byFrequency.values().forEach(Collections::sort);
        return byFrequency;
    }

    /** Top-N words as "word -> count" lines. */
    public List<Map.Entry<String, Integer>> topN(int n, Predicate<Map.Entry<String, Integer>> filter) {
        List<Map.Entry<String, Integer>> result = new ArrayList<>();
        for (var group : arrangeByFrequency(filter).entrySet()) {
            for (String word : group.getValue()) {
                if (result.size() == n) return result;
                result.add(Map.entry(word, group.getKey()));
            }
        }
        return result;
    }

    public String render(int n, Predicate<Map.Entry<String, Integer>> filter) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-4s %-20s %8s%n", "#", "Word", "Count"));
        sb.append("-".repeat(34)).append(System.lineSeparator());
        int rank = 1;
        for (var e : topN(n, filter)) {
            sb.append(String.format("%-4d %-20s %8d%n", rank++, e.getKey(), e.getValue()));
        }
        long total = frequencies.values().stream().mapToLong(Integer::longValue).sum();
        sb.append("-".repeat(34)).append(System.lineSeparator());
        sb.append(String.format("Total words: %d | Unique words: %d%n", total, frequencies.size()));
        return sb.toString();
    }

    /** Words that appear at least {@code threshold} times (the "frequently occurring" words). */
    public List<String> frequentWords(int threshold) {
        return frequencies.entrySet().stream()
                .filter(minFrequency(threshold).and(excludeStopWords()))
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}
