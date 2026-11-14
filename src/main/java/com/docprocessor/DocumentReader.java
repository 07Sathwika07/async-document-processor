package com.docprocessor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Common contract for both processing strategies so they can be compared fairly.
 * Each strategy reads every file and turns its text into per-document word frequencies.
 */
public interface DocumentReader {

    /** Human readable name, used in reports. */
    String name();

    /** Reads and analyses every given file; result keeps the input order. */
    Map<Path, Map<String, Integer>> analyzeAll(List<Path> files, WordFrequencyAnalyzer analyzer) throws Exception;
}
