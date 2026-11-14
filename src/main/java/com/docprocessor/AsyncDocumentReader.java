package com.docprocessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Non-blocking reading using AsynchronousFileChannel.
 *
 * All files are submitted at once; each file is read chunk by chunk through a
 * CompletionHandler (the next read is issued from the callback of the previous one),
 * and the result is exposed as a CompletableFuture.
 */
public class AsyncDocumentReader implements DocumentReader {

    private static final int CHUNK_SIZE = 64 * 1024;

    private final int ioThreads;

    public AsyncDocumentReader(int ioThreads) {
        this.ioThreads = ioThreads;
    }

    @Override
    public String name() {
        return "Asynchronous (AsynchronousFileChannel, " + ioThreads + " I/O threads)";
    }

    @Override
    public Map<Path, Map<String, Integer>> analyzeAll(List<Path> files, WordFrequencyAnalyzer analyzer)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(ioThreads);
        try {
            // Fire off every read first and chain the analysis onto its completion:
            // while one file is being counted, other files are still being read.
            Map<Path, CompletableFuture<Map<String, Integer>>> futures = new LinkedHashMap<>();
            for (Path file : files) {
                futures.put(file, readFile(file, pool).thenApplyAsync(analyzer::analyze, pool));
            }
            // Join at the end; the order of the result map matches the input order.
            Map<Path, Map<String, Integer>> result = new LinkedHashMap<>();
            for (var entry : futures.entrySet()) {
                result.put(entry.getKey(), entry.getValue().join());
            }
            return result;
        } finally {
            pool.shutdown();
        }
    }

    /** Reads one file asynchronously. */
    public CompletableFuture<String> readFile(Path file, ExecutorService pool) {
        CompletableFuture<String> future = new CompletableFuture<>();
        AsynchronousFileChannel channel;
        try {
            channel = AsynchronousFileChannel.open(file, java.util.Set.of(StandardOpenOption.READ), pool);
        } catch (IOException e) {
            future.completeExceptionally(e);
            return future;
        }

        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(CHUNK_SIZE);

        CompletionHandler<Integer, Long> handler = new CompletionHandler<>() {
            @Override
            public void completed(Integer bytesRead, Long position) {
                if (bytesRead < 0) {                       // end of file
                    closeQuietly(channel);
                    future.complete(collected.toString(StandardCharsets.UTF_8));
                    return;
                }
                buffer.flip();
                collected.write(buffer.array(), 0, buffer.limit());
                buffer.clear();
                channel.read(buffer, position + bytesRead, position + bytesRead, this);
            }

            @Override
            public void failed(Throwable exc, Long position) {
                closeQuietly(channel);
                future.completeExceptionally(exc);
            }
        };

        channel.read(buffer, 0L, 0L, handler);
        return future;
    }

    private static void closeQuietly(AsynchronousFileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }
}
