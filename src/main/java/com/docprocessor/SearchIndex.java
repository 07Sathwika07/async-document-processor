package com.docprocessor;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Inverted index: word -> (document -> occurrences). Powers the "search" part of the system.
 */
public class SearchIndex {

    private final Map<String, Map<Path, Integer>> index = new HashMap<>();

    public void addDocument(Path doc, Map<String, Integer> docFrequencies) {
        docFrequencies.forEach((word, count) ->
                index.computeIfAbsent(word, k -> new HashMap<>()).put(doc, count));
    }

    /** Documents containing the word, ordered best match (highest count) first. */
    public Map<Path, Integer> search(String word) {
        Map<Path, Integer> hits = index.getOrDefault(word.toLowerCase(Locale.ROOT).trim(), Map.of());
        Map<Path, Integer> sorted = new LinkedHashMap<>();
        hits.entrySet().stream()
                .sorted(Map.Entry.<Path, Integer>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().toString()))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    /** Words starting with a prefix (TreeMap gives sorted, alphabetical suggestions). */
    public TreeMap<String, Integer> prefixSearch(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT).trim();
        TreeMap<String, Integer> result = new TreeMap<>();
        index.forEach((word, docs) -> {
            if (word.startsWith(p)) {
                result.put(word, docs.values().stream().mapToInt(Integer::intValue).sum());
            }
        });
        return result;
    }

    public int size() {
        return index.size();
    }
}
