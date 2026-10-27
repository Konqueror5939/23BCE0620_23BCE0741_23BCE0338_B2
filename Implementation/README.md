# Java Virtual Threads + Garbage Collection Benchmark

## Purpose
A small Java 21 benchmark supporting the project question: how Virtual Threads affect runtime, memory allocation and GC behaviour compared with platform threads under high concurrency.

## Requirements
- JDK 21 or newer
- No external libraries required

## Compile
```bash
javac -d out src/VirtualThreadGCBenchmark.java
```

## Run one experiment
```bash
java -Xms1g -Xmx1g -XX:+UseG1GC -cp out VirtualThreadGCBenchmark virtual 2000 256 5 results.csv
```

Arguments:
1. `platform` or `virtual`
2. number of tasks
3. allocation per task in KB
4. simulated blocking time in ms
5. CSV output filename

## Compare the four collectors
Run the same workload for each collector:

```bash
java -Xms1g -Xmx1g -XX:+UseSerialGC    -cp out VirtualThreadGCBenchmark virtual 2000 256 5 results.csv
java -Xms1g -Xmx1g -XX:+UseParallelGC  -cp out VirtualThreadGCBenchmark virtual 2000 256 5 results.csv
java -Xms1g -Xmx1g -XX:+UseG1GC       -cp out VirtualThreadGCBenchmark virtual 2000 256 5 results.csv
java -Xms1g -Xmx1g -XX:+UseZGC        -cp out VirtualThreadGCBenchmark virtual 2000 256 5 results.csv
java -Xms1g -Xmx1g -XX:+UseShenandoahGC -cp out VirtualThreadGCBenchmark virtual 2000 256 5 results.csv
```

Then repeat with `platform` instead of `virtual`.

## Metrics
- elapsed time
- throughput (tasks/sec)
- heap before/after
- peak memory-pool usage
- GC collection count
- GC time

## Experimental controls
Keep JDK version, heap size, task count, allocation size and sleep time constant. Run each configuration at least 3 times and report mean and standard deviation.

## Important limitation
This is a core JVM benchmark, not a Spring Boot/Quarkus/Netty benchmark. Do not claim it evaluates framework stacks unless those frameworks are added separately.
