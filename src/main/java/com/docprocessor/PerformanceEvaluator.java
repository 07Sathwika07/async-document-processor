package com.docprocessor;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates reading strategies on execution time and resource utilisation
 * (heap memory, CPU time, peak thread count).
 */
public class PerformanceEvaluator {

    public record Measurement(String strategy,
                              double avgMillis,
                              double minMillis,
                              double maxMillis,
                              double heapUsedMb,
                              double cpuMillis,
                              int peakThreads,
                              int documents,
                              long totalWords) {
    }

    private final DocumentProcessor processor;
    private final int warmupRuns;
    private final int measuredRuns;

    public PerformanceEvaluator(DocumentProcessor processor, int warmupRuns, int measuredRuns) {
        this.processor = processor;
        this.warmupRuns = warmupRuns;
        this.measuredRuns = measuredRuns;
    }

    public Measurement measure(DocumentReader reader, List<Path> files) throws Exception {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        DocumentProcessor.ProcessingResult last = null;

        for (int i = 0; i < warmupRuns; i++) {          // let the JIT warm up
            last = processor.process(reader, files);
        }

        List<Double> times = new ArrayList<>();
        long cpuBefore = totalCpuNanos(threads);
        long heapDeltaSum = 0;
        threads.resetPeakThreadCount();

        for (int i = 0; i < measuredRuns; i++) {
            System.gc();
            long heapBefore = usedHeap();
            long start = System.nanoTime();
            last = processor.process(reader, files);
            long elapsed = System.nanoTime() - start;
            heapDeltaSum += Math.max(0, usedHeap() - heapBefore);
            times.add(elapsed / 1_000_000.0);
        }

        long cpuNanos = totalCpuNanos(threads) - cpuBefore;
        double avg = times.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double min = times.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = times.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        long words = last.totalFrequencies().values().stream().mapToLong(Integer::longValue).sum();

        return new Measurement(reader.name(), avg, min, max,
                heapDeltaSum / (double) measuredRuns / (1024 * 1024),
                cpuNanos / 1_000_000.0 / measuredRuns,
                threads.getPeakThreadCount(),
                last.documentCount(), words);
    }

    /** Compares several strategies and returns a formatted table plus a verdict. */
    public String compare(List<DocumentReader> readers, List<Path> files) throws Exception {
        List<Measurement> results = new ArrayList<>();
        for (DocumentReader reader : readers) {
            results.add(measure(reader, files));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Files: %d | warm-up runs: %d | measured runs: %d%n%n",
                files.size(), warmupRuns, measuredRuns));
        sb.append(String.format("%-52s %9s %9s %9s %10s %9s %8s%n",
                "Strategy", "Avg ms", "Min ms", "Max ms", "Heap MB", "CPU ms", "Threads"));
        sb.append("-".repeat(113)).append(System.lineSeparator());
        for (Measurement m : results) {
            sb.append(String.format("%-52s %9.2f %9.2f %9.2f %10.2f %9.2f %8d%n",
                    m.strategy(), m.avgMillis(), m.minMillis(), m.maxMillis(),
                    m.heapUsedMb(), m.cpuMillis(), m.peakThreads()));
        }
        sb.append(System.lineSeparator());

        Measurement fastest = results.stream()
                .min((a, b) -> Double.compare(a.avgMillis(), b.avgMillis())).orElseThrow();
        Measurement baseline = results.get(0);
        sb.append("Fastest: ").append(fastest.strategy()).append(System.lineSeparator());
        if (fastest != baseline) {
            sb.append(String.format("Speed-up over %s: %.2fx%n", baseline.strategy(),
                    baseline.avgMillis() / fastest.avgMillis()));
        }
        sb.append("Note: async wins when many/large files are read (I/O overlaps); for a few small files,")
                .append(System.lineSeparator())
                .append("thread-pool and callback overhead can make synchronous reading as fast or faster.")
                .append(System.lineSeparator());
        return sb.toString();
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    /** CPU time consumed by the whole JVM process (includes short-lived pool threads). */
    private static long totalCpuNanos(ThreadMXBean ignored) {
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            return Math.max(0, sun.getProcessCpuTime());
        }
        return 0;
    }
}
