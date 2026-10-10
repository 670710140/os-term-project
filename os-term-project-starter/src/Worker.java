import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread ที่ดึงงานจาก Ready Queue ไปทำจนเสร็จ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ลำดับการทำงานของ Job หนึ่งชิ้น บังคับตามหัวข้อ 6 ของโจทย์:
 * 1. รับงานจาก Ready Queue แล้วบันทึกเวลาเริ่ม
 * 2. จำลองงานหลักด้วย Thread.sleep(job.workMs)
 * 3. ถ้า job.resource != NONE ให้บันทึกเวลาเริ่มรอ แล้ว acquire
 * 4. จำลองการถือครองด้วย Thread.sleep(job.resourceMs)
 * 5. release แล้วบันทึกเวลาจบ
 *
 * ห้ามสลับขั้นที่ 2 กับ 3 เพราะจะทำให้ผลของทุกกลุ่มเทียบกันไม่ได้
 *
 * จุดที่มักพลาด:
 * - ถ้า exception หรือ interrupt เกิดขึ้นหลัง acquire แต่ก่อน release
 * permit จะค้างถาวรและระบบจะแขวน ต้องออกแบบให้คืนได้เสมอ
 * - Worker ต้องหยุดเองได้เมื่อไม่มีงานเหลือแล้ว ไม่ใช่วนรอตลอดไป
 */

/** hreads execute job, always return acquired resource permits?? */
public class Worker extends Thread {

    // TODO: เก็บ ReadyQueue, ResourceManager, Statistics และ logger
    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;
    private final AtomicInteger runningCount;

    // public Worker(String name, ReadyQueue readyQueue, ResourceManager resources, Statistics statistics, ProjectLogger logger) {
    //     this(name, readyQueue, resources, statistics, logger, new AtomicInteger());
    // }

    public Worker(String name, ReadyQueue readyQueue, ResourceManager resources,
                  Statistics statistics, ProjectLogger logger, AtomicInteger runningCount) {
        super(name);

        // TODO
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
        this.runningCount = runningCount;

        // throw new UnsupportedOperationException("TODO: Worker constructor");
    }

    @Override
    public void run() {
        // System.out.println("hi guys");
        // TODO: วนรับงานและเรียก processJob จนกว่าจะได้รับสัญญาณให้หยุด
        try {
            while (!Thread.currentThread().isInterrupted()) {
                // shutdown down condition

                // ขั้นที่1 รับงานจาก Ready Queue แล้วบันทึกเวลาเริ่ม
                Job job = readyQueue.take();

                
                if (job.isPoisonPill()) return;
                runningCount.incrementAndGet();
                try {
                    processJob(job);
                } finally {
                    runningCount.decrementAndGet();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** order: work/zZzZz, resource wait/use Zzz, completion. */
    /** ทำงานหนึ่งชิ้นให้จบตามลำดับ 5 ขั้นด้านบน */
    private void processJob(Job job) throws InterruptedException {
        job.setStartMs(logger.now());

        // ขั้นที่1 รับงานจาก Ready Queue แล้วบันทึกเวลาเริ่ม
        logger.jobStarted(job); // เริ่ม รับงานไปทำ

        // 2. จำลองงานหลักด้วย Thread.sleep(job.workMs)
        Thread.sleep(job.workMs); // นอน  aka : working
        logger.workFinished(job); // งานเสร็จ

        // 3. ถ้า job.resource != NONE ให้บันทึกเวลาเริ่มรอ แล้ว acquire
        // ในไฟล์ ResourceType.java มันบอกอยู่ว่า มี 3 ประเภท NONE,PRINTER,DATABASE;
        // และไฟล์ job.java บอกว่า
        // /** ทรัพยากรร่วมที่ต้องใช้ หรือ NONE ถ้าไม่ต้องใช้ */
        // public final ResourceType resource;

        if (job.resource != ResourceType.NONE) {
            long waitStart = logger.now(); // starting request
            logger.resourceWaitStarted(job);
            boolean acquired = false;

            try {
                acquired = resources.acquire(job.resource); // request resource

                long waitedMs = logger.now() - waitStart; 

                job.setResourceWaitMs(waitedMs);

                logger.resourceAcquired(job, waitedMs);
                
                // 4. จำลองการถือครองด้วย Thread.sleep(job.resourceMs)
                Thread.sleep(job.resourceMs);
            } finally {
                if (acquired) {
                    resources.release(job.resource); // คืนสิทธิ์
                    logger.resourceReleased(job); // บันทึกเหตุการณ์ว่า Resource ถูกคืนแล้ว
                }
            }
        } else {
            job.setResourceWaitMs(0);
        }

        // 5. release แล้วบันทึกเวลาจบ
        job.setFinishMs(logger.now());
        logger.jobCompleted(job);
        statistics.recordCompletion(job); // this will CountDownLatch by 1
        //System.out.println(job.toString());
    }
}
