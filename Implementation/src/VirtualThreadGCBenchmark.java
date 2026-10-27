import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Java 21 benchmark for comparing Platform Threads and Virtual Threads under
 * allocation-heavy concurrent workloads. Results are written as CSV.
 *
 * Usage:
 *   java -Xms1g -Xmx1g VirtualThreadGCBenchmark <platform|virtual> <tasks> <allocKB> <sleepMs> <output.csv>
 */
public class VirtualThreadGCBenchmark {
    record GcSnapshot(long count, long timeMs) {}

    static GcSnapshot gcSnapshot() {
        long count = 0, time = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (b.getCollectionCount() > 0) count += b.getCollectionCount();
            if (b.getCollectionTime() > 0) time += b.getCollectionTime();
        }
        return new GcSnapshot(count, time);
    }

    static long usedHeap() {
        MemoryUsage u = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return u.getUsed();
    }

    static long peakPoolUsed() {
        long peak = 0;
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage u = p.getPeakUsage();
            if (u != null) peak = Math.max(peak, u.getUsed());
        }
        return peak;
    }

    static void work(int allocKB, int sleepMs, AtomicLong checksum) {
        try {
            // Allocation-heavy workload: short-lived objects/arrays create GC pressure.
            byte[] data = new byte[allocKB * 1024];
            for (int i = 0; i < data.length; i += 4096) data[i] = (byte) (i * 31);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            checksum.addAndGet(digest[0] & 0xff);

            // Simulate blocking work common in server applications.
            if (sleepMs > 0) Thread.sleep(sleepMs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "virtual";
        int tasks = args.length > 1 ? Integer.parseInt(args[1]) : 2000;
        int allocKB = args.length > 2 ? Integer.parseInt(args[2]) : 256;
        int sleepMs = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        String output = args.length > 4 ? args[4] : "results.csv";

        System.out.println("Java: " + Runtime.version());
        System.out.println("Mode: " + mode + ", tasks=" + tasks + ", allocKB=" + allocKB + ", sleepMs=" + sleepMs);
        System.out.println("GCs: " + ManagementFactory.getGarbageCollectorMXBeans().stream().map(GarbageCollectorMXBean::getName).toList());

        System.gc();
        Thread.sleep(500);
        GcSnapshot beforeGc = gcSnapshot();
        long beforeHeap = usedHeap();
        AtomicLong checksum = new AtomicLong();

        ExecutorService executor = mode.equalsIgnoreCase("virtual")
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));

        long start = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>(tasks);
        for (int i = 0; i < tasks; i++) {
            futures.add(executor.submit(() -> work(allocKB, sleepMs, checksum)));
        }
        for (Future<?> f : futures) f.get();
        long elapsedNs = System.nanoTime() - start;
        executor.close();

        GcSnapshot afterGc = gcSnapshot();
        long afterHeap = usedHeap();
        long peak = peakPoolUsed();
        double seconds = elapsedNs / 1_000_000_000.0;
        double throughput = tasks / seconds;

        String gcNames = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName).reduce((a,b) -> a + " | " + b).orElse("unknown");

        String header = "timestamp,mode,tasks,allocKB,sleepMs,elapsedSec,throughputTasksPerSec,heapBeforeMB,heapAfterMB,peakPoolUsedMB,gcCollections,gcTimeMs,checksum,gcNames";
        String row = String.format(java.util.Locale.US,
                "%s,%s,%d,%d,%d,%.4f,%.2f,%.2f,%.2f,%.2f,%d,%d,%d,\"%s\"%n",
                java.time.Instant.now(), mode, tasks, allocKB, sleepMs, seconds, throughput,
                beforeHeap / 1048576.0, afterHeap / 1048576.0, peak / 1048576.0,
                afterGc.count() - beforeGc.count(), afterGc.timeMs() - beforeGc.timeMs(), checksum.get(), gcNames.replace("\"", "'"));

        java.nio.file.Path path = java.nio.file.Path.of(output);
        boolean exists = java.nio.file.Files.exists(path);
        java.nio.file.Files.writeString(path, (exists ? "" : header + "\n") + row,
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);

        System.out.println("Elapsed: " + String.format("%.3f s", seconds));
        System.out.println("Throughput: " + String.format("%.2f tasks/s", throughput));
        System.out.println("GC collections: " + (afterGc.count() - beforeGc.count()));
        System.out.println("GC time: " + (afterGc.timeMs() - beforeGc.timeMs()) + " ms");
        System.out.println("Heap before/after: " + String.format("%.1f / %.1f MB", beforeHeap/1048576.0, afterHeap/1048576.0));
        System.out.println("CSV: " + path.toAbsolutePath());
    }
}
