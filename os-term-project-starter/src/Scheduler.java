import java.util.concurrent.BlockingQueue;

/** Transfers arriving jobs into the policy-specific ready queue. */
public class Scheduler extends Thread {
    private final BlockingQueue<Job> arrivalQueue;
    private final ReadyQueue readyQueue;
    private final int workerCount;

    public Scheduler(ReadyQueue readyQueue, ProjectLogger logger, BlockingQueue<Job> arrivalQueue) {
        this(readyQueue, logger, arrivalQueue, 1);
        System.out.println("No worker input : defualt to 1");
    }

    public Scheduler(ReadyQueue readyQueue, ProjectLogger logger,
                     BlockingQueue<Job> arrivalQueue, int workerCount) {
        super("scheduler");
        this.readyQueue = readyQueue;
        this.arrivalQueue = arrivalQueue;
        if (workerCount < 1) {
            throw new IllegalArgumentException("message from scheduler's construct: worker <= 0!");
        }
        this.workerCount = workerCount;
    }

    @Override
    public void run() {
        System.out.println("scheduler Thead Started");
        try {
            while (true) {
                Job job = arrivalQueue.take();
                if (job.isPoisonPill()) {
                    for (int i = 0; i < workerCount; i++) readyQueue.add(Job.poisonPill());
                    System.out.println("scheduler found poison pill \nscheduler Thread END");
                    return;
                }
                readyQueue.add(job);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("message from scheduler.run() {you should not see this}");
    }
}
