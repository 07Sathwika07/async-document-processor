package com.docprocessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Creates sample .txt documents so the system can be demonstrated/benchmarked without real data.
 */
public class SampleDataGenerator {

    private static final String[] VOCAB = (
            "java asynchronous channel file path buffer thread lambda stream map tree hash "
            + "document search index report frequency word processing system performance memory "
            + "resource execution time read write completion handler future executor pool "
            + "the and of to in is that for with data analysis design evaluate compare result "
            + "network server client request response cache queue task parallel concurrent "
            + "algorithm structure collection generic interface class object method function").split(" ");

    public static void generate(Path directory, int fileCount, int wordsPerFile) throws IOException {
        Files.createDirectories(directory);
        Random random = new Random(42);
        for (int i = 1; i <= fileCount; i++) {
            StringBuilder sb = new StringBuilder(wordsPerFile * 7);
            for (int w = 0; w < wordsPerFile; w++) {
                // skewed distribution -> some words clearly more frequent than others
                int idx = (int) (VOCAB.length * Math.pow(random.nextDouble(), 2.2));
                sb.append(VOCAB[idx]);
                sb.append(w % 14 == 13 ? ".\n" : " ");
            }
            Files.writeString(directory.resolve(String.format("doc_%03d.txt", i)), sb.toString());
        }
    }
}
