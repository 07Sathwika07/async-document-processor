# Asynchronous Document Processing and Search System

Java 21 · Maven · BTL-5 (Evaluate)

Reads many text documents, builds word-frequency reports, supports word search, and
**evaluates synchronous vs asynchronous file processing** on execution time and resource use.

## Concepts mapped to code

| Concept | Where |
|---|---|
| Java NIO `Files` and `Path` | `DocumentProcessor` (discovery with `Files.list`), `SyncDocumentReader` (`Files.readString`), `SampleDataGenerator` |
| `AsynchronousFileChannel` | `AsyncDocumentReader` – chunked reads via `CompletionHandler`, exposed as `CompletableFuture` |
| `HashMap` | `WordFrequencyAnalyzer` (word counts), `SearchIndex` (inverted index) |
| `TreeMap` | `FrequencyReport.arrangeByFrequency` (frequency → words, descending); `SearchIndex.prefixSearch` (alphabetical) |
| Lambda expressions | `FrequencyReport` predicates (`minFrequency`, `minLength`, `excludeStopWords`) composed with `.and()`; `merge(word, 1, Integer::sum)`; comparators |
| Evaluation | `PerformanceEvaluator` – avg/min/max time, heap, CPU time, peak threads |

## Architecture

```
Main (console UI)
 └─ DocumentProcessor ── discover files → reader.analyzeAll → merge totals + build SearchIndex
        ├─ DocumentReader (interface)
        │    ├─ SyncDocumentReader   blocking, one file after another
        │    └─ AsyncDocumentReader  all reads submitted at once; analysis chained on completion
        ├─ WordFrequencyAnalyzer     HashMap counting
        ├─ FrequencyReport           TreeMap ranking + lambda filters
        └─ SearchIndex               word → (document → count)
PerformanceEvaluator  compares any list of DocumentReader strategies
SampleDataGenerator   creates demo .txt files
```

## Build & run

```bash
mvn clean package
java -jar target/async-document-processor-1.0.0.jar            # uses ./sample-docs
java -jar target/async-document-processor-1.0.0.jar /path/to/docs
```

Without Maven:

```bash
mkdir -p out && javac -d out src/main/java/com/docprocessor/*.java
java -cp out com.docprocessor.Main
```

## Using it

1. Menu **6** – generate sample documents (e.g. 50 files × 200000 words).
2. Menu **1** / **2** – process asynchronously / synchronously.
3. Menu **3** – ranked report with filters (top N, min frequency, min length, stop words).
4. Menu **4** – search `java` (which documents, how often) or `thre*` (prefix).
5. Menu **5** – benchmark sync vs async and print a comparison table.

## Evaluation notes (for your report)

* Async overlaps I/O waiting with CPU work: while one file is being counted, other files are still being read.
  Benefit grows with **many files, large files, slow disks/network storage, and multiple CPU cores**.
* On a single core, or with small files cached in RAM, the async version has no I/O to hide, so thread-pool and
  callback overhead make it equal or slightly slower, and it uses more heap (all file bytes in flight at once) and more threads.
* Sync is simplest and has the lowest memory footprint; async trades memory/complexity for throughput.
* Run the benchmark on **your** machine with a large data set (e.g. 200+ files) and put the table in your report;
  the first runs are warm-up (JIT), so results are averaged over measured runs after warm-up.

## Possible extensions

* Write the report to a file with `AsynchronousFileChannel` (async write).
* Stream very large files line-by-line instead of loading the whole file.
* Virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) as a third strategy to compare.
