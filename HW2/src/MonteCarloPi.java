import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.io.PrintWriter;
import java.io.FileNotFoundException;

public class MonteCarloPi {

    // Загальна кількість точок для методу Монте-Карло
    static final long SAMPLES = 1_000_000L;

    // Кількість потоків для тестування
    static final int[] THREAD_COUNTS = {2, 4, 8, 16, 32, 64};

    // Кількість повторів для усереднення результатів
    static final int RUNS = 7;

    public static void main(String[] args) throws InterruptedException {
        // "Прогрів" JVM: запускаємо невелику кількість вибірок, щоб JIT-компілятор оптимізував байткод.
        runSingleThread(200_000);

        System.out.println("=== Monte Carlo Pi ===");
        System.out.println("Samples: " + SAMPLES + "  |  Repeats: " + RUNS);

        // Однопоточний запуск — базовий еталон для порівняння
        Stats singleStats = runMultipleTimes(1);
        System.out.printf("Single-thread: pi≈%.7f, mean=%d ms, σ=%.2f%n%n",
                singleStats.meanPi(), singleStats.mean(), singleStats.stddev());

        // Багатопоточно: збираємо результати в список
        List<Stats> allStats = new ArrayList<>();
        System.out.printf("%-8s | %-12s | %-12s | %-10s | %-8s%n",
                "Threads", "Mean (ms)", "StdDev (ms)", "Pi (avg)", "Speedup");
        System.out.println("---------------------------------------------------------------");

        for (int t : THREAD_COUNTS) {
            Stats stats = runMultipleTimes(t);
            allStats.add(stats);
            double speedup = (double) singleStats.mean() / stats.mean();
            System.out.printf("%-8d | %-12d | %-12.2f | %-10.7f | %-8.2f%n",
                    t, stats.mean(), stats.stddev(), stats.meanPi(), speedup);
        }

        // Запис у CSV файл
        try (PrintWriter out = new PrintWriter("results.csv")) {
            out.println("threads,mean_time_ms,stddev_ms,mean_pi,speedup");

            // Однопоточний результат
            out.printf("%d,%d,%.2f,%.7f,%.2f%n",
                    1, singleStats.mean(), singleStats.stddev(),
                    singleStats.meanPi(), 1.0);

            // Багатопоточні результати
            for (int i = 0; i < THREAD_COUNTS.length; i++) {
                int t = THREAD_COUNTS[i];
                Stats stats = allStats.get(i);
                double speedup = (double) singleStats.mean() / stats.mean();
                out.printf("%d,%d,%.2f,%.7f,%.2f%n",
                        t, stats.mean(), stats.stddev(),
                        stats.meanPi(), speedup);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /* ---------------- Усереднені запуски ---------------- */

    // Запускає тест кілька разів і збирає середні значення
    static Stats runMultipleTimes(int threads) throws InterruptedException {
        List<Long> times = new ArrayList<>();
        List<Double> pis = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            // Якщо один потік — запускаємо single
            Result r = (threads == 1)
                    ? timedSingle(SAMPLES)
                    : timedParallel(SAMPLES, threads);
            times.add(r.millis);
            pis.add(r.pi);
        }
        return new Stats(times, pis);
    }

    /* ---------------- Однопоточно ---------------- */

    // Метод Монте-Карло для одного потоку
    static long runSingleThread(long samples) {
        long hits = 0;
        SplittableRandom rnd = new SplittableRandom(); // генератор випадкових чисел
        for (long i = 0; i < samples; i++) {
            double x = rnd.nextDouble(-1.0, 1.0);
            double y = rnd.nextDouble(-1.0, 1.0);
            // Якщо точка в колі (x^2 + y^2 <= 1), то зараховуємо
            if (x * x + y * y <= 1.0) hits++;
        }
        return hits;
    }

    // Замір часу для однопоточного варіанту
    static Result timedSingle(long samples) {
        long t0 = System.nanoTime();
        long hits = runSingleThread(samples);
        long t1 = System.nanoTime();
        double pi = 4.0 * ((double) hits / samples);
        return new Result(pi, nanosToMillis(t1 - t0));
    }

    /* ---------------- Багатопоточно ---------------- */

    // Метод Монте-Карло на N потоках
    static Result timedParallel(long totalSamples, int threads) throws InterruptedException {
        List<Worker> workers = new ArrayList<>(threads);
        List<Thread> threadList = new ArrayList<>(threads);

        // Розподіл точок між потоками
        long base = totalSamples / threads;
        long rem = totalSamples % threads;

        for (int i = 0; i < threads; i++) {
            long part = base + (i < rem ? 1 : 0); // частка для конкретного потоку
            Worker w = new Worker(part);
            workers.add(w);
            threadList.add(new Thread(w, "mc-" + i));
        }

        long t0 = System.nanoTime();
        for (Thread th : threadList) th.start(); // старт усіх потоків
        for (Thread th : threadList) th.join();  // чекаємо завершення
        long t1 = System.nanoTime();

        // Збираємо локальні результати після завершення
        long hits = 0;
        for (Worker w : workers) hits += w.getHits();

        double pi = 4.0 * ((double) hits / totalSamples);
        return new Result(pi, nanosToMillis(t1 - t0));
    }


    // Переводимо наносекунди у мілісекунди
    static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    // Клас, який виконує роботу у потоці
    static class Worker implements Runnable {
        private final long samples;     // скільки точок обробляє цей потік
        private long hits;              // локальні попадання
        private final SplittableRandom rnd; // власний генератор випадкових чисел

        Worker(long samples) {
            this.samples = samples;
            this.rnd = new SplittableRandom();
        }

        @Override
        public void run() {
            long localHits = 0;
            for (long i = 0; i < samples; i++) {
                double x = rnd.nextDouble(-1.0, 1.0);
                double y = rnd.nextDouble(-1.0, 1.0);
                if (x * x + y * y <= 1.0) localHits++;
            }
            // Після завершення зберігаємо результат у поле
            this.hits = localHits;
        }

        long getHits() {
            return hits;
        }
    }

    // Результат одного запуску
    static class Result {
        final double pi;
        final long millis;
        Result(double pi, long millis) { this.pi = pi; this.millis = millis; }
    }

    // Статистика після кількох запусків
    static class Stats {
        private final List<Long> times;
        private final List<Double> pis;

        Stats(List<Long> times, List<Double> pis) {
            this.times = times;
            this.pis = pis;
        }

        // Середній час
        int mean() {
            return (int) Math.round(times.stream().mapToLong(x -> x).average().orElse(0));
        }

        // Стандартне відхилення часу
        double stddev() {
            double mean = mean();
            double variance = times.stream()
                    .mapToDouble(x -> (x - mean) * (x - mean))
                    .average().orElse(0);
            return Math.sqrt(variance);
        }

        // Середнє значення π
        double meanPi() {
            return pis.stream().mapToDouble(x -> x).average().orElse(0);
        }
    }
}
