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
public class Worker extends Thread {

    // TODO: เก็บ ReadyQueue, ResourceManager, Statistics และ logger
    private ReadyQueue readyQueue;
    private ResourceManager resources;
    private Statistics statistics;
    private ProjectLogger logger;

    public Worker(String name, ReadyQueue readyQueue, ResourceManager resources, Statistics statistics,
            ProjectLogger logger) {

        super(name);

        // TODO
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;

        // throw new UnsupportedOperationException("TODO: Worker constructor");
    }

    @Override
    public void run() {
        // TODO: วนรับงานและเรียก processJob จนกว่าจะได้รับสัญญาณให้หยุด
        try {
            while (true) {

                // ขั้นที่1 รับงานจาก Ready Queue แล้วบันทึกเวลาเริ่ม
                Job job = readyQueue.take(); // ถ้ามีงาน เอางานมา ถ้ายังไม่มีงาน ให้รอ

                // ขั้นที่2-5
                processJob(job);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** ทำงานหนึ่งชิ้นให้จบตามลำดับ 5 ขั้นด้านบน */
    private void processJob(Job job) throws InterruptedException {

        // TODO
        boolean acquired = false;

        // ขั้นที่1 รับงานจาก Ready Queue แล้วบันทึกเวลาเริ่ม
        logger.jobStarted(job); // เริ่ม รับงานไปทำ

        // 2. จำลองงานหลักด้วย Thread.sleep(job.workMs)
        Thread.sleep(job.workMs); // นอน
        logger.workFinished(job); // งานเสร็จ

        // 3. ถ้า job.resource != NONE ให้บันทึกเวลาเริ่มรอ แล้ว acquire
        // ในไฟล์ ResourceType.java มันบอกอยู่ว่า มี 3 ประเภท NONE,PRINTER,DATABASE;
        // และไฟล์ job.java บอกว่า
        // /** ทรัพยากรร่วมที่ต้องใช้ หรือ NONE ถ้าไม่ต้องใช้ */
        // public final ResourceType resource;

        if (job.resource != ResourceType.NONE) {

            try {
                logger.resourceWaitStarted(job);

                long waitStart = logger.now(); // บันทึกเวลา

                resources.acquire(job.resource); // ขอใช้สิทธิ์ของ Resource ที่ Job นี้ต้องการ

                acquired = true;

                long waitedMs = logger.now() - waitStart;

                logger.resourceAcquired(job, waitedMs);

                // 4. จำลองการถือครองด้วย Thread.sleep(job.resourceMs)
                Thread.sleep(job.resourceMs);

            } finally {
                if (acquired) {
                    resources.release(job.resource); // คืนสิทธิ์
                    logger.resourceReleased(job); // บันทึกเหตุการณ์ว่า Resource ถูกคืนแล้ว
                }
            }
        }
         // 5. release แล้วบันทึกเวลาจบ
        logger.jobCompleted(job);
    }
}
