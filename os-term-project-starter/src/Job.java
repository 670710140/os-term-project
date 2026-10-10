/**
 * ข้อมูลของงานหนึ่งชิ้น
 *
 * ฟิลด์ทั้งหมดในไฟล์นี้มาจากไฟล์ workload CSV โดยตรง และถูกกำหนดครั้งเดียว
 * ตอนโหลด จึงประกาศเป็น final และปลอดภัยเมื่อหลาย Thread อ่านพร้อมกัน
 *
 * ไฟล์นี้เป็นโค้ดตั้งต้นที่อาจารย์แจก แต่ต่างจากไฟล์อื่นตรงที่
 * นักศึกษา "ต้องแก้" โดยเพิ่มฟิลด์ของตัวเองในส่วน TODO ด้านล่าง
 */
public class Job {

    /** รหัสงาน เช่น J01 — ไม่ซ้ำกันภายในหนึ่งไฟล์ workload */
    public final String id;

    /** เวลาที่งานควรเข้าสู่ระบบ นับจากวินาทีที่โปรแกรมเริ่ม (มิลลิวินาที) */
    public final long arrivalMs;

    /** ระดับความสำคัญ โดย 1 คือสูงสุด ตัวเลขยิ่งมากยิ่งสำคัญน้อย */
    public final int priority;

    /** ระยะเวลาของงานหลัก ก่อนขอใช้ทรัพยากรร่วม (มิลลิวินาที) */
    public final long workMs;

    /** ทรัพยากรร่วมที่ต้องใช้ หรือ NONE ถ้าไม่ต้องใช้ */
    public final ResourceType resource;

    /**
     * ระยะเวลาที่ถือครองทรัพยากร (มิลลิวินาที) เป็น 0 เสมอเมื่อ resource เป็น NONE
     */
    public final long resourceMs;

    /**
     * ลำดับที่งานนี้ปรากฏในไฟล์ workload เริ่มจาก 0
     * เตรียมไว้ให้เผื่อกลุ่มต้องการใช้ประกอบการตัดสินลำดับเมื่อ priority เท่ากัน
     * จะใช้หรือไม่ใช้ก็ได้ กติกาตัดสินลำดับเป็นสิ่งที่กลุ่มต้องออกแบบเอง
     */
    public final int sequence;

    // =====================================================================
    private volatile long actualArrivalMs = -1;
    private volatile long startMs = -1;
    private volatile long finishMs = -1;
    private volatile long resourceWaitMs = 0;
    // volatile == prevents the JVM and the processor from reordering reads and writes instruction

    public Job(String id, long arrivalMs, int priority, long workMs, ResourceType resource, long resourceMs,
            int sequence) {
        this.id = id;
        this.arrivalMs = arrivalMs;
        this.priority = priority;
        this.workMs = workMs;
        this.resource = resource;
        this.resourceMs = resourceMs;
        this.sequence = sequence;
    }

    // =====================================================================
    // TODO (นักศึกษา): เพิ่มฟิลด์สำหรับเก็บค่าที่ใช้วัดผลของงานชิ้นนี้เอง
    //
    // ค่าที่โครงงานต้องการ (ดูหัวข้อ 8 ของเอกสารโจทย์):
    // - เวลาที่เข้าสู่ระบบจริง
    // - เวลาที่เริ่มถูกทำโดย Worker
    // - เวลาที่ทำเสร็จ
    // - เวลาที่เริ่มรอ resource และเวลารอ resource รวม
    //
    // สามคำถามที่ต้องตอบให้ได้ก่อนเขียน และจะถูกถามใน Demo:
    // 1. ใช้เวลาจากนาฬิกาตัวไหน (ดู ProjectLogger.now() ซึ่งให้เวลาฐานเดียว
    // กับที่ปรากฏใน log ทำให้ค่าที่วัดกับ log ตรวจสอบกันได้)
    // 2. ฟิลด์ใดถูกเขียนโดย Thread หนึ่งแล้วอ่านโดยอีก Thread หนึ่ง
    // และต้องป้องกันอย่างไร 
    // answer = volatile
    // 3. ผลที่ได้ต้องสอดคล้องกับสมการตรวจสอบในหัวข้อ 8:
    // Turnaround = Waiting + workMs + Resource Wait + resourceMs
    // =====================================================================

    public void setActualArrivalMs(long actualArrivalMs) {
        this.actualArrivalMs = actualArrivalMs;
    }

    public long getActualArrivalMs() {
        return actualArrivalMs;
    }

    public void setStartMs(long value) {
        this.startMs = value;
    }

    public long getStartMs() {
        return startMs;
    }

    public void setFinishMs(long value) {
        this.finishMs = value;
    }

    public long getFinishMs() {
        return finishMs;
    }

    public void setResourceWaitMs(long value) {
        this.resourceWaitMs = value;
    }

    public long getResourceWaitMs() {
        return resourceWaitMs;
    }

    public long getWaitingTimeMs() {
        return startMs - actualArrivalMs;
        // if (actualArrivalMs < 0 || startMs < 0) {
        //     return 0;
        // } else {
        //     return Math.max(0, startMs - actualArrivalMs);
        // }
        // return actualArrivalMs < 0 || startMs < 0 ? 0 : Math.max(0, startMs - actualArrivalMs);
    }

    public long getTurnaroundTimeMs() {
        return finishMs - actualArrivalMs;
        // if (actualArrivalMs < 0 || finishMs < 0) {
        //     return 0;
        // } else {
        //     return Math.max(0, finishMs - actualArrivalMs);
        // }
        //return actualArrivalMs < 0 || finishMs < 0 ? 0 : Math.max(0, finishMs - actualArrivalMs);
    }

    public boolean isPoisonPill() {
        return sequence == Integer.MIN_VALUE && "__SCHEDULER_POISON__".equals(id);
    }

    public static Job poisonPill() {
        return new Job("__SCHEDULER_POISON__", Long.MAX_VALUE, Integer.MAX_VALUE, 0, ResourceType.NONE, 0, Integer.MIN_VALUE);
    }

    @Override
    public String toString() {
        return String.format("%s(priority=%d, work=%dms, %s)",
                id, priority, workMs,
                resource == ResourceType.NONE ? "no resource"
                        : resource + " " + resourceMs + "ms");
    }
}
