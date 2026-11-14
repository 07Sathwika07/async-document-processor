package com.docprocessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Orchestrates: discover files (NIO Files/Path) -> read (sync or async) -> analyse -> index.
 */
public class DocumentProcessor {

    private final WordFrequencyAnalyzer analyzer = new WordFrequencyAnalyzer();

    /** Result of one full processing run. */
    public record ProcessingResult(Map<String, Integer> totalFrequencies,
                                   SearchIndex index,
                                   int documentCount,
                                   long totalBytes) {
    }

    /** Finds all .txt files directly inside the directory, sorted by name. */
    public List<Path> discoverDocuments(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("Not a directory: " + directory.toAbsolutePath());
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".txt"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    public ProcessingResult process(DocumentReader reader, List<Path> files) throws Exception {
        Map<Path, Map<String, Integer>> perDocument = reader.analyzeAll(files, analyzer);

        Map<String, Integer> total = new HashMap<>();
        SearchIndex index = new SearchIndex();
        long bytes = 0;
        for (var entry : perDocument.entrySet()) {
            analyzer.mergeInto(total, entry.getValue());
            index.addDocument(entry.getKey(), entry.getValue());
            bytes += Files.size(entry.getKey());
        }
        return new ProcessingResult(total, index, perDocument.size(), bytes);
    }
}
