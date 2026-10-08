/**
 * คิวงานที่พร้อมถูกหยิบไปทำ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * สิ่งที่คลาสนี้ต้องทำได้:
 *   - เก็บงานที่รอ Worker อยู่ ✅
 *   - หยิบงานถัดไปตามนโยบายที่เลือก (FCFS หรือ Priority) ✅
 *   - ถูกเรียกจากหลาย Thread พร้อมกันได้อย่างปลอดภัย
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้:
 *   - หัวข้อ 4: priority = 1 สูงสุด เมื่อเท่ากันต้องมีกติกาตัดสินลำดับ (tie-break)
 *     ที่ตัดสินจากข้อมูลของ Job ไม่ขึ้นกับว่า Thread ใดเข้าถึงคิวก่อน
 *   - หัวข้อ 7: ห้ามวนลูปเช็กแบบกิน CPU (busy waiting) — Worker ที่ไม่มีงานทำ
 *     ต้องถูกพักไว้ ไม่ใช่วนถามซ้ำ ๆ
 *
 * จะออกแบบเป็นคลาสเดียวที่รับนโยบายเข้ามา หรือแยกเป็นสองคลาส
 * หรือใช้โครงสร้างข้อมูลสำเร็จรูปของ Java ก็ได้ ขอให้อธิบายเหตุผลได้ใน Demo
 */

/*
    ใช้ BlockingQueue สำหรับ การเขียน FCFS
 */
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ReadyQueue {
    // TODO: เก็บนโยบาย (Config.Policy) และโครงสร้างข้อมูลที่ใช้เก็บงาน
    private BlockingQueue<Job> queue;
    private Config.Policy  policy;
    
    public ReadyQueue(Config.Policy policy) {
        // TODO
        queue = new LinkedBlockingQueue<>();
        this.policy = policy;
        //throw new UnsupportedOperationException("TODO: ReadyQueue constructor");
    }

    /** ใส่งานเข้าคิว เรียกโดย Scheduler Thread */
    public void add(Job job) {
        // TODO
        //queue.add(job); //บรรทัดนี่นะ มันคือการ เอาค่า จาก add จาก เมธอด add มาใส่ใน ✅ เก็บงาน
        switch (policy) {
            case FCFS -> queue.add(job);
            case PRIORITY -> addPRIORITY(job);
            // case AGING -> addAGING(job);
            // case MILF -> addMILF(job);
        };;

        //throw new UnsupportedOperationException("TODO: ReadyQueue.add");
    }

    /**
     * หยิบงานถัดไปตามนโยบาย เรียกโดย Worker Thread
     *
     * ถ้ายังไม่มีงาน ต้องรอโดยไม่กิน CPU
     * ต้องคิดด้วยว่าจะบอก Worker อย่างไรเมื่อไม่มีงานเหลือแล้วและควรหยุดทำงาน
     */
    public Job take() throws InterruptedException {
        // TODO
        return switch (policy) {
            case FCFS -> queue.take();
            case PRIORITY -> takePRIORITY();
            case AGING -> null;
            case MILF -> null;
        };
        //return queue.take(); //เอางานไปทำ✅
        //throw new UnsupportedOperationException("TODO: ReadyQueue.take");
    }

    /** จำนวนงานที่รออยู่ตอนนี้ ใช้โดย Monitor — ต้องอ่านได้อย่างปลอดภัย */
    public int size() {
        // TODO
        return queue.size();
        //throw new UnsupportedOperationException("TODO: ReadyQueue.size");
    }

    // private void FCFS(Job job) {
        
    // }

    // private Job takeFCFS() {
         
    // }

    private int minIntPriority = 128;
    private void addPRIORITY(Job job) {
        if (job.priority < minIntPriority) {
            minIntPriority = job.priority;
        }
        queue.add(job);
    }

    private Job takePRIORITY() {
        Job highest = null;
        for (Job job : queue) {
            if (job.priority < highest.priority) {
                highest = job;
            }
        }
        queue.remove(highest);
        return highest;
    }

    private void AGING(Job job) {

    }

    private void MILF(Job job) {

    }

    
}
