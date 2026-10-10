import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * จุดเริ่มต้นของโปรแกรม
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ส่วนที่เขียนไว้ให้แล้วคือการรับค่า การโหลด workload และการแสดง error
 * ซึ่งไม่ใช่สิ่งที่โครงงานนี้วัด ส่วนที่เหลือเป็น TODO ทั้งหมด
 *
 * วิธีรัน:
 *   java Main jobs_standard.csv priority 3 1 2
 */

public class Main {
    public static void main(String[] args) {
        // ---------- 1. รับค่าจาก command line ----------
        final Config config;
        try {
            config = Config.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("ผิดพลาด: " + e.getMessage());
            System.err.println();
            System.err.println(Config.USAGE);
            System.exit(1);
            return;
        }

        // ---------- 2. เริ่มจับเวลาและโหลด workload ----------
        ProjectLogger logger = new ProjectLogger();
        final List<Job> jobs;
        try {
            jobs = new ArrayList<>(WorkloadLoader.load(config.workloadPath));
        } catch (WorkloadFormatException e) {
            System.err.println("ไฟล์ workload ผิดรูปแบบ — " + e.getMessage());
            System.exit(1);
            return;
        } catch (IOException e) {
            System.err.println("เปิดไฟล์ \"" + config.workloadPath + "\" ไม่ได้");
            System.err.println("ตรวจว่าไฟล์มีอยู่จริงและ path ถูกต้อง (สั่ง java จากโฟลเดอร์ใด)");
            System.exit(1);
            return;
        }

        logger.systemStart(config);
        logger.systemEvent("โหลดงานได้ " + jobs.size() + " ชิ้น");

        ResourceManager resources = new ResourceManager(config.printerPermits, config.databasePermits);
        ReadyQueue readyQueue = new ReadyQueue(config.policy);
        Statistics statistics = new Statistics(jobs.size());
        BlockingQueue<Job> arrivalQueue = new LinkedBlockingQueue<>();
        AtomicInteger runningCount = new AtomicInteger();

        List<Worker> workers = new ArrayList<>();
        for (int i = 0; i < config.workers; i++) {
            workers.add(new Worker("worker-" + (i + 1), readyQueue, resources,
                    statistics, logger, runningCount));
        }
        Scheduler scheduler = new Scheduler(readyQueue, logger, arrivalQueue, config.workers);
        Monitor monitor = new Monitor(readyQueue, resources, statistics, logger, runningCount);
        JobGenerator generator = new JobGenerator(jobs, logger, arrivalQueue);

        // Start all worker thread
        for (Worker worker : workers) {
            worker.start();
        }
        scheduler.start();
        monitor.start();
        generator.start();

        // ExecutorService pool = Executors.newFixedThreadPool(config.workers);
        // List<Worker> workerList = new ArrayList<Worker>();
        // for (int i = 0; i < config.workers; i++) {
        //     Worker worker = new Worker("worker-" + String.valueOf(i+1), readyQueue, null, null, logger);
        //     workerList.add(worker);
        // }
        // for (Worker worker : workerList) {
        //     pool.submit(worker);
        // }

        //List<Thread> pool = new ArrayList<Thread>(config.workers);

        try {
            statistics.awaitCompletion(); //wait till all worker done their job
            // The final job completion means the generator and scheduler have already
            // submitted all real jobs, and workers can now consume their poison pills.
            generator.join();
            scheduler.join();
            for (Worker worker : workers) worker.join();
            // join == main wait for all instance threads to be terminated
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.systemEvent("Main interrupted while waiting for completion");
            // Do not strand workers/resources if the main thread is interrupted.
            generator.interrupt();
            scheduler.interrupt();
            for (Worker worker : workers) worker.interrupt();
        } finally {
            monitor.interrupt();
            try {
                monitor.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long makespanMs = jobs.stream().mapToLong(Job::getFinishMs).max().orElse(logger.now());
        statistics.printSummary(jobs, makespanMs);
        logger.systemStop(statistics.completedCount(), jobs.size());
    }
}
