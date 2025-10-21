import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class ParallelCollatz {

    // Range limit
    private static final int LIMIT = 10_000_000;

    // Number of threads equal to available CPU cores by default or passed by user
    public static void main(String[] args) throws InterruptedException {
        final int threads = (args.length >= 1)
                ? Math.max(1, Integer.parseInt(args[0]))
                : Runtime.getRuntime().availableProcessors();

        // Size of one work chunk
        final int CHUNK = (args.length >= 2) ? Math.max(1, Integer.parseInt(args[1])) : 1024;

        System.out.printf("Threads: %d, CHUNK: %d, LIMIT: %,d%n", threads, CHUNK, LIMIT);

        long t0 = System.nanoTime();  // Start timing

        // Atomic counter for next set of tasks
        AtomicInteger nextNumber = new AtomicInteger(1);

        // LongAdder for avoiding contention
        LongAdder globalSum = new LongAdder();

        // Create a fixed-size thread pool
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        // Launch as many workers as threads
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                long localSum = 0L; // Each thread keeps its own local sum
                // Each thread continuously fetches a new "chunk" of numbers
                while (true) {
                    // Atomically claim the start of the next chunk
                    int start = nextNumber.getAndAdd(CHUNK);
                    if (start > LIMIT) break; // Stop when no more work left
                    int end = Math.min(start + CHUNK - 1, LIMIT); // Compute the end of this chunk
                    // Process each number in this chunk
                    for (int i = start; i <= end; i++) {
                        localSum += collatzSteps(i);
                    }
                }
                // When the thread finishes, add its local result to the global total
                globalSum.add(localSum);
            });
        }
        // Wait for all threads to finish
        pool.shutdown();
        pool.awaitTermination(365, TimeUnit.DAYS);

        // Combine results
        long totalSteps = globalSum.sum();
        double avg = totalSteps / (double) LIMIT;

        long t1 = System.nanoTime();
        double sec = (t1 - t0) / 1e9;

        System.out.printf("Total Collatz steps: %,d%n", totalSteps);
        System.out.printf("Average steps per number: %.6f%n", avg);
        System.out.printf("Total computation time: %.3f s%n", sec);
    }

    // Count amount of Collatz steps for n
    private static int collatzSteps(long n) {
        int steps = 0;
        while (n != 1) {
            if ((n & 1L) == 0) {
                n >>= 1;          // n = n / 2
            } else {
                n = 3L * n + 1L;  // n = 3n + 1
            }
            steps++;
        }
        return steps;
    }
}

