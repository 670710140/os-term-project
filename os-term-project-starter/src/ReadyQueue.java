import java.util.Comparator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Thread-safe blocking ready queue. Priority 1 is highest; ties use workload sequence.
 */
public class ReadyQueue {
    private static final Comparator<Job> PRIORITY_ORDER = (a, b) -> {
        if (a.isPoisonPill()) return b.isPoisonPill() ? 0 : 1;
        if (b.isPoisonPill()) return -1;
        int byPriority = Integer.compare(a.priority, b.priority);
        return byPriority != 0 ? byPriority : Integer.compare(a.sequence, b.sequence);
    };

    private final BlockingQueue<Job> queue;
    private final Config.Policy policy;

    public ReadyQueue(Config.Policy policy) {
        if (policy == null) throw new IllegalArgumentException("policy must not be null");
        this.policy = policy;
        if (policy == Config.Policy.FCFS) {
            this.queue = new LinkedBlockingQueue<>();
        } else {
            this.queue = new PriorityBlockingQueue<>(500, PRIORITY_ORDER);
        }
    }

    /** Add one job. BlockingQueue implementations make this safe across threads. */
    public void add(Job job) {
        if (job == null) throw new IllegalArgumentException("job must not be null");
        queue.add(job);
    }

    /** Blocks when empty; no polling/busy waiting. */
    public Job take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }
}
