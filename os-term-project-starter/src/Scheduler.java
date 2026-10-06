import java.util.concurrent.BlockingQueue;

/**
 * รับงานจาก JobGenerator แล้วจัดเข้า Ready Queue
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ (หัวข้อ 2 และ 4):
 * - Scheduler เป็น Thread บังคับ ห้ามให้ JobGenerator ใส่งานลง Ready Queue
 * โดยตรง
 * - รับผิดชอบการจัดลำดับตามนโยบาย FCFS หรือ Priority
 *
 * ข้อควรคิด:
 * - Scheduler รับงานจาก JobGenerator ผ่านอะไร และรอโดยไม่กิน CPU อย่างไร
 * - เมื่อ JobGenerator ปล่อยงานครบแล้ว Scheduler รู้ได้อย่างไรว่าควรหยุด
 */
public class Scheduler extends Thread {

    // TODO: เก็บช่องทางรับงานจาก JobGenerator, ReadyQueue ปลายทาง และ logger
    //
    // หมายเหตุ: constructor ด้านล่างยังไม่มี parameter สำหรับ "ช่องทางรับงาน"
    // ให้เพิ่มเข้าไปให้ตรงกับที่ออกแบบไว้ใน JobGenerator
    // เพิ่ม parameter ได้ แต่อย่าเปลี่ยนชื่อคลาส
    private BlockingQueue<Job> arrivalQueue;
    private ReadyQueue readyQueue;
    private ProjectLogger logger;

    public Scheduler(ReadyQueue readyQueue, ProjectLogger logger, BlockingQueue<Job> arrivalQueue) {
        super("scheduler");
        // TODO
        // throw new UnsupportedOperationException("TODO: Scheduler constructor");
        this.readyQueue = readyQueue;
        this.logger = logger;
        this.arrivalQueue = arrivalQueue;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Job job = arrivalQueue.take(); //รอจนกว่าจะมีงานนะ (รับงานมา)
                readyQueue.add(job);// ส่งงานไป
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}