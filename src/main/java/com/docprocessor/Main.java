package com.docprocessor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Predicate;

/**
 * Console front-end for the Asynchronous Document Processing and Search System.
 *
 * Usage: java -jar async-document-processor-1.0.0.jar [documentsDirectory]
 */
public class Main {

    private static final int IO_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors());

    private final DocumentProcessor processor = new DocumentProcessor();
    private final DocumentReader syncReader = new SyncDocumentReader();
    private final DocumentReader asyncReader = new AsyncDocumentReader(IO_THREADS);
    private final Scanner in = new Scanner(System.in);

    private Path directory;
    private List<Path> files = List.of();
    private DocumentProcessor.ProcessingResult current;

    public static void main(String[] args) throws Exception {
        Main app = new Main();
        app.directory = Path.of(args.length > 0 ? args[0] : "sample-docs");
        app.run();
    }

    private void run() throws Exception {
        System.out.println("=== Asynchronous Document Processing and Search System ===");
        reload();
        while (true) {
            printMenu();
            String choice = in.nextLine().trim();
            try {
                switch (choice) {
                    case "1" -> processWith(asyncReader);
                    case "2" -> processWith(syncReader);
                    case "3" -> showReport();
                    case "4" -> searchWord();
                    case "5" -> evaluate();
                    case "6" -> generateSamples();
                    case "7" -> changeDirectory();
                    case "0" -> {
                        System.out.println("Goodbye!");
                        return;
                    }
                    default -> System.out.println("Invalid option.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("Documents folder: " + directory.toAbsolutePath() + "  (" + files.size() + " files)");
        System.out.println("1. Process documents (ASYNCHRONOUS)");
        System.out.println("2. Process documents (SYNCHRONOUS)");
        System.out.println("3. Show word-frequency report (filtered with lambdas)");
        System.out.println("4. Search a word / prefix");
        System.out.println("5. Evaluate sync vs async (time & resources)");
        System.out.println("6. Generate sample documents");
        System.out.println("7. Change documents folder");
        System.out.println("0. Exit");
        System.out.print("Choose: ");
    }

    private void reload() {
        try {
            files = processor.discoverDocuments(directory);
        } catch (Exception e) {
            files = List.of();
            System.out.println("Note: " + e.getMessage() + " (use option 6 to generate samples)");
        }
    }

    private void processWith(DocumentReader reader) throws Exception {
        requireFiles();
        long start = System.nanoTime();
        current = processor.process(reader, files);
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        System.out.printf("Processed %d documents (%d bytes) in %.2f ms using %s%n",
                current.documentCount(), current.totalBytes(), ms, reader.name());
    }

    private void showReport() {
        if (requireResult()) return;
        System.out.print("Top how many words? [20]: ");
        int top = readInt(20);
        System.out.print("Minimum frequency? [1]: ");
        int minFreq = readInt(1);
        System.out.print("Minimum word length? [1]: ");
        int minLen = readInt(1);
        System.out.print("Exclude stop words (the, and, of...)? [y/N]: ");
        boolean noStop = in.nextLine().trim().equalsIgnoreCase("y");

        // Lambda expressions composed into a single filter
        Predicate<Map.Entry<String, Integer>> filter =
                FrequencyReport.minFrequency(minFreq).and(FrequencyReport.minLength(minLen));
        if (noStop) filter = filter.and(FrequencyReport.excludeStopWords());

        FrequencyReport report = new FrequencyReport(current.totalFrequencies());
        System.out.println();
        System.out.print(report.render(top, filter));
    }

    private void searchWord() {
        if (requireResult()) return;
        System.out.print("Enter word to search (add * for prefix, e.g. thre*): ");
        String q = in.nextLine().trim();
        if (q.isEmpty()) return;
        if (q.endsWith("*")) {
            var matches = current.index().prefixSearch(q.substring(0, q.length() - 1));
            if (matches.isEmpty()) System.out.println("No words with that prefix.");
            matches.forEach((w, c) -> System.out.printf("  %-20s %d occurrences%n", w, c));
        } else {
            Map<Path, Integer> hits = current.index().search(q);
            if (hits.isEmpty()) System.out.println("'" + q + "' not found in any document.");
            hits.forEach((p, c) -> System.out.printf("  %-20s %d occurrences%n", p.getFileName(), c));
        }
    }

    private void evaluate() throws Exception {
        requireFiles();
        System.out.print("Measured runs? [5]: ");
        int runs = readInt(5);
        System.out.println("Running evaluation, please wait...");
        PerformanceEvaluator evaluator = new PerformanceEvaluator(processor, 2, runs);
        System.out.println();
        System.out.print(evaluator.compare(List.of(syncReader, asyncReader), files));
    }

    private void generateSamples() throws Exception {
        System.out.print("Number of files? [50]: ");
        int count = readInt(50);
        System.out.print("Words per file? [200000]: ");
        int words = readInt(200_000);
        SampleDataGenerator.generate(directory, count, words);
        reload();
        System.out.println("Generated " + count + " files in " + directory.toAbsolutePath());
    }

    private void changeDirectory() {
        System.out.print("New folder path: ");
        String p = in.nextLine().trim();
        if (!p.isEmpty()) {
            directory = Path.of(p);
            current = null;
            reload();
        }
    }

    private void requireFiles() {
        reload();
        if (files.isEmpty()) {
            throw new IllegalStateException("No .txt documents found in " + directory.toAbsolutePath());
        }
    }

    private boolean requireResult() {
        if (current == null) {
            System.out.println("Process the documents first (option 1 or 2).");
            return true;
        }
        return false;
    }

    private int readInt(int defaultValue) {
        String s = in.nextLine().trim();
        if (s.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
