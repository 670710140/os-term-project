import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe completion metrics for a single scheduler run. */
public class Statistics {
    private final CountDownLatch allCompleted;
    private final Set<String> recordedIds = ConcurrentHashMap.newKeySet();
    private int completed;
    private long totalWaitingMs;
    private long totalTurnaroundMs;
    private long totalResourceWaitMs;
    private int resourceJobCount;

    public Statistics(int expectedJobs) {
        if (expectedJobs < 0) {
            throw new UnsupportedOperationException("expectedJobs must be >= 0");
        }
        allCompleted = new CountDownLatch(expectedJobs);
    }

    // Called exactly once for each completed job;
    // Called when job finished
    public synchronized void recordCompletion(Job job) {
        if (job == null || !recordedIds.add(job.id)) return;
        totalWaitingMs += job.getWaitingTimeMs();
        totalTurnaroundMs += job.getTurnaroundTimeMs();
        if (job.resource != ResourceType.NONE) {
            totalResourceWaitMs += job.getResourceWaitMs();
            resourceJobCount++;
        }
        completed++;
        allCompleted.countDown();
    }

    public synchronized int completedCount() {
        return completed;
    }

    public void awaitCompletion() throws InterruptedException {
        allCompleted.await();
    }

    public void printSummary(List<Job> allJobs, long makespanMs) {
        int done;
        long waiting;
        long turnaround;
        long resourceWait;
        int resourceJobs;
        synchronized (this) {
            done = completed;
            waiting = totalWaitingMs;
            turnaround = totalTurnaroundMs;
            resourceWait = totalResourceWaitMs;
            resourceJobs = resourceJobCount;
        }
        
        double avgWaiting;
        if (done == 0) {
            avgWaiting = 0.0;
        } else {
            avgWaiting = (double) waiting / done;
        }

        double avgTurnaround;
        if (done == 0) {
            avgTurnaround = 0.0;
        } else {
            avgTurnaround = (double) turnaround / done;
        }

        double avgResourceWait;
        if (resourceJobs == 0) {
            avgResourceWait = 0.0;
        } else {
            avgResourceWait = (double) resourceWait / resourceJobs;
        }

        double throughput;
        if (makespanMs <= 0) {
            throughput = 0.0;
        } else {
            throughput = done * 1000.0 / makespanMs;
        }


        System.out.println();
        System.out.println("========== STATISTICS ==========");
        System.out.printf("Completed jobs:          %d/%d%n", done, allJobs.size());
        System.out.printf("Makespan:                %d ms%n", makespanMs);
        System.out.printf("Average waiting time:    %d ms%n", Math.round(avgWaiting));
        System.out.printf("Average turnaround time: %d ms%n", Math.round(avgTurnaround));
        System.out.printf("Throughput:               %.2f jobs/s%n", throughput);
        System.out.printf("Average resource wait:   %d ms (resource jobs only)%n", Math.round(avgResourceWait));
        System.out.println("================================");
    }
}
