# PROJECT_PROGRESS.md

## OS Term Project — Progress Tracker

**Owner:** ฟลุ๊ค
**Current checkpoint:** Checkpoint 1 — Basic End-to-End Flow

### 1. Project order from README

1. Basic end-to-end flow
2. Multiple workers
3. ResourceManager + semaphores
4. Statistics + Monitor
5. Correct shutdown

Group A (generally do not modify):
- WorkloadLoader.java
- ProjectLogger.java
- Config.java
- ResourceType.java
- WorkloadFormatException.java

Group B:
- Job.java

Group C:
- Main.java
- JobGenerator.java
- Scheduler.java
- ReadyQueue.java
- Worker.java
- ResourceManager.java
- Statistics.java
- Monitor.java

### 2. System architecture

```text
Workload CSV
    ↓
WorkloadLoader
    ↓
JobGenerator
    ↓ put(job)
ArrivalQueue
    ↓ take()
Scheduler
    ↓ add(job)
ReadyQueue
    ↓
Worker(s)
    ↓
ResourceManager (when needed)
    ↓
Statistics / Monitor
```

Main is the assembler: it creates the shared objects, creates threads, starts them, and later controls shutdown.

### 3. Job.java

**Status: partially done by ฟลุ๊ค**

Added:

```java
private long actualArrivalMs = -1;

public void setActualArrivalMs(long actualArrivalMs) {
    this.actualArrivalMs = actualArrivalMs;
}

public long getActualArrivalMs() {
    return actualArrivalMs;
}
```

Meaning:
- `arrivalMs` = target arrival time from workload
- `actualArrivalMs` = actual time the job enters the system
- timing should use the same `ProjectLogger.now()` clock

Still required later by the README:
- Worker start time
- completion time
- resource wait start / total resource wait
- appropriate cross-thread visibility

### 4. JobGenerator.java

**Status: done for Checkpoint 1 by ฟลุ๊ค**

Current logic:
- sort jobs by `arrivalMs`
- wait until each target arrival time
- set `actualArrivalMs`
- `arrivalQueue.put(job)`
- log arrival
- restore interrupt status if interrupted

Important:
`BlockingQueue.put()` is the connection from Generator to Scheduler.

### 5. ArrivalQueue

**Status: design understood; must be created in Main**

Use one shared object:

```java
BlockingQueue<Job> arrivalQueue = new LinkedBlockingQueue<>();
```

Required imports:

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
```

The same queue object must be passed to both Generator and Scheduler.

### 6. Scheduler.java

**Status: constructor done; run() being learned/checked**

ฟลุ๊ค changed the constructor to:

```java
public Scheduler(
        ReadyQueue readyQueue,
        ProjectLogger logger,
        BlockingQueue<Job> arrivalQueue) {
    super("scheduler");
    this.readyQueue = readyQueue;
    this.logger = logger;
    this.arrivalQueue = arrivalQueue;
}
```

Fields:

```java
private ReadyQueue readyQueue;
private ProjectLogger logger;
private BlockingQueue<Job> arrivalQueue;
```

Checkpoint 1 logic:

```java
@Override
public void run() {
    try {
        while (true) {
            Job job = arrivalQueue.take();
            readyQueue.add(job);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
```

Key concept:
`arrivalQueue.take()` blocks when the queue is empty, so there is no busy-wait loop.

Shutdown is intentionally deferred to Checkpoint 5.

### 7. ReadyQueue.java

**Status: done for basic FCFS by ฟลุ๊ค**

Current implementation uses:

```java
BlockingQueue<Job> queue = new LinkedBlockingQueue<>();
```

Methods:
- `add(job)` inserts a job
- `take()` waits for a job and removes it
- `size()` reports queue size

The constructor accepts `Config.Policy`, but Priority is not implemented yet. Current behavior is basic FCFS.

### 8. Main.java

**Status: not assembled yet**

Need to create:

```java
BlockingQueue<Job> arrivalQueue = new LinkedBlockingQueue<>();

ReadyQueue readyQueue = new ReadyQueue(config.policy);

Scheduler scheduler =
        new Scheduler(readyQueue, logger, arrivalQueue);

JobGenerator generator =
        new JobGenerator(jobs, logger, arrivalQueue);
```

Then Worker(s), later ResourceManager / Statistics / Monitor as checkpoints advance.

### 9. Next task

`Worker.java` has now been implemented for the Checkpoint 1 basic flow.

Next order:

```text
[1] Assemble Main with ArrivalQueue + Scheduler + Generator + Worker
 ↓
[2] Compile (`javac *.java`)
 ↓
[3] Run Checkpoint 1 end-to-end
 ↓
[4] Verify jobs reach Worker and `JOB_COMPLETED`
 ↓
[5] Fix any basic-flow issues found by the run
 ↓
[6] Multiple workers
 ↓
[7] ResourceManager + Semaphore
 ↓
[8] Statistics + Monitor
 ↓
[9] Correct shutdown
 ↓
[10] Final workload tests
```

Note:
- `Worker.java` already contains the ResourceManager calls needed by its process flow.
- `ResourceManager.java` itself is still not implemented; the next dedicated checkpoint will implement the shared semaphores for PRINTER/DATABASE.

### 10. Worker

**Status: implemented for Checkpoint 1 basic flow by ฟลุ๊ค**

Current implementation:
- stores `ReadyQueue`, `ResourceManager`, `Statistics`, and `ProjectLogger`
- constructor initializes all shared dependencies
- `run()` repeatedly calls `readyQueue.take()` and sends each Job to `processJob()`
- `processJob()` follows the required order: start log → `workMs` → work-finished log → resource wait/acquire when needed → resource wait measurement → `resourceMs` while holding the resource → release → completion log
- resource wait time uses the same `ProjectLogger.now()` clock
- uses an `acquired` flag with `try/finally` so a successfully acquired permit is returned even if an interrupt/exception occurs during resource use

Current limitations intentionally deferred:
- `Statistics` is injected but not yet used because `Statistics.java` is a later checkpoint
- Worker shutdown is not finalized yet; the current blocking loop is for the basic flow and will be completed during the shutdown checkpoint

Checkpoint 1 target:

```text
ReadyQueue
    ↓ take()
Worker 1
    ↓
execute job
    ↓
JOB_COMPLETED
```

### 11. Priority

**Status: not started**

ReadyQueue currently behaves as FCFS.

Priority scheduling must be added later, with tie-breaking based on the project requirements. Do not invent a tie-break rule without checking the source requirements.

### 12. ResourceManager

**Status: not started**

Later checkpoint:
- use semaphores
- manage PRINTER and DATABASE
- acquire before resource use
- release afterward
- avoid permit leaks

Required final checks include:
- `RESOURCE_ACQUIRED` count equals `RESOURCE_RELEASED`
- no resource is held beyond the configured permit count

### 13. Statistics + Monitor

**Status: not started**

README requires metrics such as:
- waiting time
- turnaround
- resource wait
- throughput

Important equation:

```text
Turnaround
= Waiting
+ workMs
+ Resource Wait
+ resourceMs
```

Use the same `ProjectLogger.now()` clock.

Monitor will report system state periodically.

### 14. Shutdown

**Status: not started**

README says not to guess completion with something like:

```java
Thread.sleep(10000);
```

Possible approaches mentioned by the README include:
- poison pill
- CountDownLatch
- protected pending-job counter

Final shutdown must ensure:
- workers do not stop before the last job
- Scheduler / Monitor / Generator stop correctly
- resource permits are released
- Main joins threads
- JVM exits without Ctrl+C

### 15. Workloads

Known workload files:
- jobs_single.csv
- jobs_same_priority.csv
- jobs_printer.csv
- jobs_db.csv
- jobs_standard.csv

README notes that `jobs_db.csv` with database=2 should be structurally comparable to `jobs_printer.csv` with printer=2. A discrepancy can indicate a bug.

### 16. Final verification checklist

- [ ] `javac *.java` passes
- [ ] no inappropriate concurrency warnings
- [ ] all 7 result-table rows are tested
- [ ] JVM closes by itself
- [ ] `JOB_COMPLETED` count equals workload size
- [ ] `RESOURCE_ACQUIRED` count equals `RESOURCE_RELEASED`
- [ ] no resource held beyond permit count
- [ ] metrics equation checks
- [ ] no `Thread.stop()`
- [ ] no busy-wait loops
- [ ] every group member can explain the code

### 17. Work history — ฟลุ๊ค

Completed / worked on:
- README and project architecture understanding
- `Job.actualArrivalMs`
- `JobGenerator` for arrival timing
- basic FCFS `ReadyQueue`
- Scheduler constructor
- understanding `Generator → ArrivalQueue → Scheduler → ReadyQueue`
- `Scheduler.run()` basic blocking flow
- `Worker.java` constructor and shared dependencies
- `Worker.run()` using `ReadyQueue.take()`
- `Worker.processJob()` basic work flow
- resource wait timing with `ProjectLogger.now()`
- ResourceManager acquire/release integration points in Worker
- `try/finally` + `acquired` flag to prevent resource permit leaks
- `JOB_COMPLETED` logging after the complete job flow

Current:
- Worker is complete for the Checkpoint 1 basic implementation
- ready to assemble `Main.java` and run the first end-to-end test

### 18. Known remaining work

- [ ] Assemble `Main.java` with the shared ArrivalQueue, ReadyQueue, Generator, Scheduler, and Worker
- [ ] Checkpoint 1 end-to-end run
- [ ] Verify `JOB_COMPLETED` count against the workload
- [ ] Multiple workers
- [ ] Priority
- [ ] Implement `ResourceManager` with fair Semaphores for PRINTER/DATABASE
- [ ] Complete Statistics
- [ ] Complete Monitor
- [ ] Correct shutdown
- [ ] Seven final test rows
- [ ] Logs / experiment analysis / AI disclosure as required by README

### 19. Guidance for anyone continuing this project

1. Read `README.md` first.
2. Read this progress file.
3. Check the actual Java source before changing code.
4. Follow the checkpoint order.
5. Do not silently invent requirements.
6. Explain code to ฟลุ๊ค rather than only giving a finished code dump.
7. Record meaningful changes in this file.
8. Do not implement final shutdown before the main flow is working.

### 20. Definition of Done

The project should reach:

```text
CSV
 ↓
Generator
 ↓
ArrivalQueue
 ↓
Scheduler
 ↓
ReadyQueue
 ↓
Workers
 ↓
Resources
 ↓
Completion
 ↓
Statistics
 ↓
Monitor
 ↓
Correct Shutdown
 ↓
Final Results
```

and pass the README's final checks.

---

### 21. Latest checkpoint note — Worker completed

`Worker.java` is now considered **done for Checkpoint 1 basic flow**.

Important learning points recorded:
- `readyQueue.take()` blocks instead of busy-waiting.
- `jobStarted()` happens before `workMs`.
- Main work (`workMs`) must happen before resource acquisition.
- `resourceWaitStarted()` marks the beginning of resource waiting.
- `resources.acquire(job.resource)` may block until a permit is available.
- Resource wait time is measured with the same `ProjectLogger.now()` clock.
- A successful acquire sets `acquired = true`.
- `try/finally` guarantees that an acquired permit is returned.
- `jobCompleted()` is recorded after the whole Job is finished, including resource use when applicable.

Next practical step: **assemble `Main.java` and run the Checkpoint 1 end-to-end flow before moving deeper into ResourceManager/Statistics/Monitor.**

**Last updated:** 2026-10-06
