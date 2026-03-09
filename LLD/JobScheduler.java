// Keep enum if you want it for readability / logging
enum JobSchedulerType {
    ONE_TIME,
    PERIOD_WITH_FIXED_RATE,
    PERIOD_WITH_FIXED_DELAY
}

// ---- Job Model (Template Method style) ----
abstract class ScheduledJob {
    private final String id;
    private final Runnable runnable;
    private final TimeUnit unit;
    private final JobSchedulerType type;

    // mutable but only touched under scheduler lock
    private long scheduledTimeMillis;

    protected ScheduledJob(String id, Runnable runnable, long scheduledTimeMillis, TimeUnit unit, JobSchedulerType type) {
        this.id = id;
        this.runnable = runnable;
        this.scheduledTimeMillis = scheduledTimeMillis;
        this.unit = unit;
        this.type = type;
    }

    public String getId() { return id; }
    public Runnable getRunnable() { return runnable; }
    public TimeUnit getUnit() { return unit; }
    public JobSchedulerType getType() { return type; }

    public long getScheduledTimeMillis() { return scheduledTimeMillis; }
    public void setScheduledTimeMillis(long scheduledTimeMillis) { this.scheduledTimeMillis = scheduledTimeMillis; }

    /**
     * Return next scheduled time after this execution, or empty if it should not repeat.
     * nowMillis is current time in millis at reschedule decision.
     */
    public abstract OptionalLong nextScheduleTimeAfterRun(long nowMillis);
}

// One-time Job
final class OneTimeJob extends ScheduledJob {
    public OneTimeJob(String id, Runnable runnable, long scheduledTimeMillis, TimeUnit unit) {
        super(id, runnable, scheduledTimeMillis, unit, JobSchedulerType.ONE_TIME);
    }

    @Override
    public OptionalLong nextScheduleTimeAfterRun(long nowMillis) {
        return OptionalLong.empty();
    }
}

// Fixed rate periodic
final class FixedRateJob extends ScheduledJob {
    private final long period;
    public FixedRateJob(String id, Runnable runnable, long scheduledTimeMillis, TimeUnit unit, long period) {
        super(id, runnable, scheduledTimeMillis, unit, JobSchedulerType.PERIOD_WITH_FIXED_RATE);
        this.period = period;
    }
    @Override
    public OptionalLong nextScheduleTimeAfterRun(long nowMillis) {
        // fixed rate: schedule based on "period" from now (simple approach consistent with your code)
        return OptionalLong.of(nowMillis + getUnit().toMillis(period));
    }
}

// Fixed delay periodic
final class FixedDelayJob extends ScheduledJob {
    private final long delay;
    public FixedDelayJob(String id, Runnable runnable, long scheduledTimeMillis, TimeUnit unit, long delay) {
        super(id, runnable, scheduledTimeMillis, unit, JobSchedulerType.PERIOD_WITH_FIXED_DELAY);
        this.delay = delay;
    }
    @Override
    public OptionalLong nextScheduleTimeAfterRun(long nowMillis) {
        // fixed delay: schedule after completion + delay
        return OptionalLong.of(nowMillis + getUnit().toMillis(delay));
    }
}

// ---- Service API ----
interface JobSchedulerService {
    void schedule(Runnable command, long delay, TimeUnit unit);
    void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);
    void scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

    void start();
    void shutdown();
}

// ---- Implementation ----
final class JobSchedulerServiceImpl implements JobSchedulerService, AutoCloseable {

    private static final int MAX_THREAD_POOL = 10;

    private final PriorityQueue<ScheduledJob> JobQueue =
            new PriorityQueue<>(Comparator.comparingLong(ScheduledJob::getScheduledTimeMillis));

    private final ThreadPoolExecutor executor =
            (ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_THREAD_POOL);

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition notEmptyOrUpdated = lock.newCondition();

    private volatile boolean running;
    private Thread schedulerThread;

    @Override
    public void start() {
        if (running) return;
        running = true;

        schedulerThread = new Thread(this::runLoop, "Job-scheduler-thread");
        schedulerThread.setDaemon(true);
        schedulerThread.start();
    }

    private void runLoop() {
        while (running) {
            ScheduledJob JobToRun = null;

            lock.lock();
            try {
                while (running && JobQueue.isEmpty()) {
                    notEmptyOrUpdated.await();
                }
                if (!running) break;

                while (running && !JobQueue.isEmpty()) {
                    long now = System.currentTimeMillis();
                    long sleepMs = JobQueue.peek().getScheduledTimeMillis() - now;
                    if (sleepMs <= 0) {
                        JobToRun = JobQueue.poll();
                        break;
                    }
                    notEmptyOrUpdated.await(sleepMs, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                lock.unlock();
            }

            if (!running || JobToRun == null) {
                continue;
            }

            // Execute OUTSIDE the lock (critical fix)
            try {
                if (JobToRun.getType() == JobSchedulerType.PERIOD_WITH_FIXED_DELAY) {
                    Future<?> f = executor.submit(JobToRun.getRunnable());
                    f.get(); // wait for completion for fixed-delay
                } else {
                    executor.submit(JobToRun.getRunnable());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException ee) {
                // swallow or log; do not kill scheduler
                System.out.println("Error executing Job " + JobToRun.getId() + " : " + ee.getMessage());
            } catch (Exception ex) {
                System.out.println("Error executing Job " + JobToRun.getId() + " : " + ex.getMessage());
            }

            // Reschedule if periodic
            long nowAfterRun = System.currentTimeMillis();
            OptionalLong next = JobToRun.nextScheduleTimeAfterRun(nowAfterRun);
            if (next.isPresent() && running) {
                JobToRun.setScheduledTimeMillis(next.getAsLong());
                lock.lock();
                try {
                    JobQueue.add(JobToRun);
                    notEmptyOrUpdated.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    public void schedule(Runnable command, long delay, TimeUnit unit) {
        long scheduledTime = System.currentTimeMillis() + unit.toMillis(delay);
        ScheduledJob Job = new OneTimeJob(UUID.randomUUID().toString(), command, scheduledTime, unit);

        lock.lock();
        try {
            JobQueue.add(Job);
            notEmptyOrUpdated.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        long scheduledTime = System.currentTimeMillis() + unit.toMillis(initialDelay);
        ScheduledJob Job = new FixedRateJob(UUID.randomUUID().toString(), command, scheduledTime, unit, period);

        lock.lock();
        try {
            JobQueue.add(Job);
            notEmptyOrUpdated.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        long scheduledTime = System.currentTimeMillis() + unit.toMillis(initialDelay);
        ScheduledJob Job = new FixedDelayJob(UUID.randomUUID().toString(), command, scheduledTime, unit, delay);

        lock.lock();
        try {
            JobQueue.add(Job);
            notEmptyOrUpdated.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void shutdown() {
        running = false;

        lock.lock();
        try {
            notEmptyOrUpdated.signalAll();
        } finally {
            lock.unlock();
        }

        if (schedulerThread != null) {
            schedulerThread.interrupt();
        }
        executor.shutdownNow();
    }

    @Override
    public void close() {
        shutdown();
    }
}

// ---- Demo ----
public class Main {
    public static void main(String[] args) {
        JobSchedulerService scheduler = new JobSchedulerServiceImpl();
        scheduler.start();

        scheduler.schedule(Job("ONE_TIME"), 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(Job("FIXED_RATE"), 1, 2, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(Job("FIXED_DELAY"), 1, 2, TimeUnit.SECONDS);
    }

    private static Runnable Job(String name) {
        return () -> {
            System.out.println(name + " started at " + System.currentTimeMillis() / 1000);
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.println(name + " ended at " + System.currentTimeMillis() / 1000);
        };
    }
}
