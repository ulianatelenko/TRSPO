import java.util.Arrays;

public class ParallelCollatzNoSync {

    private static final int DEFAULT_LIMIT = 10_000_000;

    public static void main(String[] args) throws InterruptedException {

        // Number of threads equal to available CPU cores by default or passed by user
        final int threads = (args.length >= 1)
                ? Math.max(1, Integer.parseInt(args[0]))
                : Runtime.getRuntime().availableProcessors();

        // Determine the upper limit of numbers, 10 millions by default
        final int LIMIT = (args.length >= 2)
                ? Math.max(1, Integer.parseInt(args[1]))
                : DEFAULT_LIMIT;

        // Generate all numbers in the main thread
        int[] numbers = new int[LIMIT];
        for (int i = 0; i < LIMIT; i++) {
            numbers[i] = i + 1;
        }

        System.out.printf("Threads: %d, LIMIT: %,d%n", threads, LIMIT);

        long t0 = System.nanoTime(); // Start timing

        // Сreate an array where each thread will store its partial sum
        final long[] partialSums = new long[threads];
        Thread[] workers = new Thread[threads];

        // Compute how many numbers each thread should process
        int base = LIMIT / threads;
        int extra = LIMIT % threads; // If LIMIT is not divisible by threads, the first (LIMIT % threads) threads get 1 extra number.
        int start = 0; // starting index for each thread’s chunk

        for (int ti = 0; ti < threads; ti++) {
            final int begin = start;
            final int len = base + (ti < extra ? 1 : 0); //  Size of this thread's chunk
            final int endEx = begin + len;
            start = endEx;

            final int index = ti;

            // Create and start a worker thread
            workers[ti] = new Thread(() -> {
                long local = 0;
                // Each thread computes Collatz steps for its own portion of the array
                for (int pos = begin; pos < endEx; pos++) {
                    local += collatzSteps(numbers[pos]);
                }
                // Store the partial result into this thread’s own slot in the array
                partialSums[index] = local;

            }, "collatz-worker-" + ti);

            workers[ti].start();
        }

        // Wait for all worker threads to finish
        for (Thread w : workers) {
            w.join();
        }

        // Combine results from all threads
        long totalSteps = 0;
        for (long s : partialSums) {
            totalSteps += s;
        }

        long t1 = System.nanoTime(); // End timing

        // Calculate average number of steps per number
        double avgSteps = totalSteps / (double) LIMIT;

        // Compute total runtime in milliseconds
        double ms = (t1 - t0) / 1_000_000.0;

        // Print results
        System.out.printf("Threads used: %d%n", threads);
        System.out.printf("Total Collatz steps: %,d%n", totalSteps);
        System.out.printf("Average steps per number: %.4f%n", avgSteps);
        System.out.printf("Total computation time (no-sync): %.2f ms%n", ms);
        
    }


    // Count amount of Collatz steps for n
    private static int collatzSteps(long n) {
        int steps = 0;
        while (n != 1) {
            if ((n & 1L) == 0L) {
                n >>= 1;          // n = n/2
            } else {
                n = 3L * n + 1L;  // n = 3n + 1
            }
            steps++;
        }
        return steps;
    }
}
