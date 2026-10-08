import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * ปล่อยงานเข้าสู่ระบบตามเวลา arrivalMs ของแต่ละ Job
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * หน้าที่ (หัวข้อ 3 ของโจทย์):
 * - รอจนถึงเวลา arrivalMs ของแต่ละงาน แล้วส่งงานต่อไปยัง Scheduler
 * อธิบายเพิ่มคือ
 * arrivalMs มันคือเวลา /ms ต่องานที่จะทำงานเสร็จ เช่น งาน A 0ms B 10ms
 * A ทำงานเสร็จ B ทำงานไป 10ms เสร็จปล่อย
 * // เริ่มระบบ
 * // │
 * // ├── เวลา 0 ms ──→ ส่ง Job A
 * // │
 * // ├── รอ
 * // │
 * // ├── เวลา 500 ms ──→ ส่ง Job B
 * // │
 * // ├── รอ
 * // │
 * // └── เวลา 1000 ms ──→ ส่ง Job C
 *
 * - บันทึกเวลาที่งานเข้าสู่ระบบ "จริง" ลงใน Job
 * (อาจไม่ตรงกับ arrivalMs เป๊ะ เพราะ Thread ถูกปลุกช้าได้)
 * - เรียก logger.jobArrived(job) ทุกครั้งที่ปล่อยงาน
 *
 * ข้อควรคิด:
 * - รายการงานที่ได้จาก WorkloadLoader เรียงตามลำดับในไฟล์ ไม่ได้เรียงตามเวลา
 * - เมื่อปล่อยงานครบทุกชิ้นแล้ว ต้องมีวิธีบอกระบบว่า "จะไม่มีงานเข้ามาอีก"
 * ดู TODO เรื่องการปิดระบบใน Main
 */
public class JobGenerator extends Thread {
    // TODO: เก็บรายการงาน, ช่องทางส่งงานไปยัง Scheduler และ logger
    // หมายเหตุ: constructor ด้านล่างยังไม่มี parameter สำหรับ "ช่องทางส่งงาน"
    // เพราะเป็นสิ่งที่กลุ่มต้องออกแบบเอง (หัวข้อ 2 ห้ามให้ JobGenerator
    // ใส่งานลง ReadyQueue โดยตรง ต้องผ่าน Scheduler เสมอ)
    // ให้เพิ่ม parameter เข้าไปตามที่ออกแบบ เช่น BlockingQueue<Job>
    // หรือคลาสของกลุ่มเอง — เพิ่ม parameter ได้ แต่อย่าเปลี่ยนชื่อคลาส

    /*
     * นี่คือ Note
     * arrivalMs = เวลาที่ควรปล่อย
     * logger.now() = เวลาจริงของระบบ
     * arrivalQueue = ช่องทางส่ง Job ให้ Scheduler
     * logger.jobArrived(job) = บันทึก event
     */
    private List<Job> jobs;
    private ProjectLogger logger;
    private BlockingQueue<Job> arrivalQueue;

    public JobGenerator(List<Job> jobs, ProjectLogger logger, BlockingQueue<Job> arrivalQueue) {
        super("generator");
        // TODO
        // throw new UnsupportedOperationException("TODO: JobGenerator constructor");
        this.jobs = jobs;
        this.logger = logger;
        this.arrivalQueue = arrivalQueue;
        
    }

    @Override
    public void run() {        // TODO: วนปล่อยงานตามเวลา แล้วแจ้งเมื่อปล่อยครบ
        System.out.println("hello guys, welcome to my minecraft channel");
        jobs.sort(Comparator.comparingLong(job -> job.arrivalMs));
        putJobToArrivalQueue();
    }

    private void putJobToArrivalQueue() {
        try {
            /*
             * Job A
             * │
             * ▼
             * arrivalMs - logger.now()
             * │
             * ┌───────┴───────┐
             * │ │
             * ค่าที่ได้ > 0 ms  หรือ   ค่าที่่ได้ <= 0 ms
             * │ │
             * ▼ │
             * Thread.sleep(arrivalMs) │
             * │ │
             * └───────┬───────┘
             * ▼
             * บันทึกเวลาจริง
             * │
             * ▼
             * ส่งให้ Scheduler
             */

            //ปล่อย Job ตามเวลา ได้แล้ว ✅ เขียนอธิบายทุกบรรทัดให้แล้ว
            for (Job job : jobs) {
                long waitMs = job.arrivalMs - logger.now(); // เวลาที่ควรเข้า - เวลาปัจจุบัน

                if (waitMs > 0) { // มากกว่า 0 ให้ นอนรอ
                    Thread.sleep(waitMs); // นอนรอ มันจะไม่ทำข้างล่างต่อ จนกว่าจะครบเวลา
                }
                job.setActualArrivalMs(logger.now()); // บันทึกเวลาที่ Job เข้าระบบจริง

                arrivalQueue.put(job); // เอา Job ส่งต่อไปให้ Scheduler

                logger.jobArrived(job); // บันทึกลง Log ว่า Job นี้เข้ามาแล้ว
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}