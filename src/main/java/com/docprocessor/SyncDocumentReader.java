package com.docprocessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Baseline: blocking, one-file-after-another reading using java.nio.file.Files.
 */
public class SyncDocumentReader implements DocumentReader {

    @Override
    public String name() {
        return "Synchronous (Files.readString)";
    }

    @Override
    public Map<Path, Map<String, Integer>> analyzeAll(List<Path> files, WordFrequencyAnalyzer analyzer)
            throws IOException {
        Map<Path, Map<String, Integer>> result = new LinkedHashMap<>();
        for (Path file : files) {                       // strictly one file after another
            String text = Files.readString(file, StandardCharsets.UTF_8);
            result.put(file, analyzer.analyze(text));
        }
        return result;
    }
}
