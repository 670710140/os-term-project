import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/** Releases jobs at their configured arrival time and then sends a poison pill. */
public class JobGenerator extends Thread {
    private final List<Job> jobs;
    private final ProjectLogger logger;
    private final BlockingQueue<Job> arrivalQueue;

    public JobGenerator(List<Job> jobs, ProjectLogger logger, BlockingQueue<Job> arrivalQueue) {
        super("generator");
        this.jobs = new ArrayList<>(jobs);
        this.logger = logger;
        this.arrivalQueue = arrivalQueue;
        System.out.println("Job Gen Created");
    }

    @Override
    public void run() {
        System.out.println("Job Gen Thread Started");
        try {
            // sort workload.cvs input only
            jobs.sort(Comparator.comparingLong((Job j) -> j.arrivalMs).thenComparingInt(j -> j.sequence));
            for (Job job : jobs) {
                long waitMs = job.arrivalMs - logger.now();
                if (waitMs > 0) {
                    Thread.sleep(waitMs);
                }
                job.setActualArrivalMs(logger.now());
                logger.jobArrived(job);
                arrivalQueue.put(job);
            }
            // poiSON T_T Aka: the job contain info to stop
            arrivalQueue.put(Job.poisonPill());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.systemEvent("Job Gen interrupted");
        }
        System.out.println("Job Gen Thread END");
    }
}
