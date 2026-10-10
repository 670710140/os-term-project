import java.util.concurrent.Semaphore;

/**
 * ควบคุมสิทธิ์การใช้ทรัพยากรร่วมของทั้งระบบ
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ข้อกำหนดจากโจทย์ที่เกี่ยวกับคลาสนี้:
 *   - หัวข้อ 5: ใช้ Semaphore ควบคุม PRINTER และ DATABASE
 *     จำนวน permit มาจาก command line (Config)
 *     ในส่วนบังคับให้สร้าง Semaphore แบบ fair = true
 *   - Worker ทุกตัวต้องใช้ ResourceManager object เดียวกัน
 *   - หัวข้อ 7: permit ต้องไม่สูญหายหรือค้าง แม้เกิด exception
 *     หรือถูก interrupt ระหว่างถือ resource
 *
 * คำถามที่จะถูกถามใน Demo:
 *   - ทำไมต้อง fair = true และถ้าเปลี่ยนเป็น false จะเกิดอะไรขึ้น
 *   - ถ้า Thread ถูก interrupt หลัง acquire สำเร็จแต่ก่อน release
 *     โค้ดของกลุ่มยังคืน permit ได้หรือไม่
 */

/** Fair semaphore-based access control for the shared resources. */
public class ResourceManager {
    private final int printerCapacity;
    private final int databaseCapacity;
    private final Semaphore printer;
    private final Semaphore database;

    public ResourceManager(int printerPermits, int databasePermits) {
        this.printerCapacity = printerPermits;
        this.databaseCapacity = databasePermits;
        this.printer = new Semaphore(printerPermits, true);
        this.database = new Semaphore(databasePermits, true);
    }

    public boolean acquire(ResourceType type) throws InterruptedException {
        switch (type) {
            case NONE:
                return false;
            case PRINTER:
                printer.acquire();
                return true;
            case DATABASE:
                database.acquire();
                return true;
            default:  throw new UnsupportedOperationException("Acq Wrong type");
        }
    }

    public void release(ResourceType type) {
        
        switch (type) {
            case PRINTER:
                printer.release();
                break;
            case DATABASE:
                database.release();
                break;
            default:
                throw new UnsupportedOperationException("Res wrong type");
        }
    }

    public String status() {
        return String.format("printer=%d/%d database=%d/%d",
                printerCapacity - printer.availablePermits(), printerCapacity,
                databaseCapacity - database.availablePermits(), databaseCapacity);
    }
}
