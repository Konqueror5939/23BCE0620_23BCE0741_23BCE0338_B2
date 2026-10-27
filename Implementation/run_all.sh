#!/usr/bin/env bash
set -e
mkdir -p out
javac -d out src/VirtualThreadGCBenchmark.java
rm -f results.csv
for gc in UseParallelGC UseG1GC UseZGC UseShenandoahGC; do
  for mode in platform virtual; do
    java -Xms1g -Xmx1g -XX:+$gc -cp out VirtualThreadGCBenchmark "$mode" 2000 256 5 results.csv
  done
done
