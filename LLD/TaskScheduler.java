// Keep enum if you want it for readability / logging
enum TaskSchedulerType {
    ONE_TIME,
    PERIOD_WITH_FIXED_RATE,
    PERIOD_WITH_FIXED_DELAY
}

// ---- Task Model (Template Method style) ----
abstract class ScheduledTask {
    private final String id;
    private final Runnable runnable;
    private final TimeUnit unit;
    private final TaskSchedulerType type;

    // mutable but only touched under scheduler lock
    private long scheduledTimeMillis;

    protected ScheduledTask(String id, Runnable runnable, long scheduledTimeMillis, TimeUnit unit, TaskSchedulerType type) {
        this.id = id;
        this.runnable = runnable;
        this.scheduledTimeMillis = scheduledTimeMillis;
        this.unit = unit;
        this.type = type;
    }

    public String getId() { return id; }
    public Runnable getRunnable() { return runnable; }
    public TimeUnit getUnit() { return unit; }
    public TaskSchedulerType getType() { return type; }

    public long getScheduledTimeMillis() { return scheduledTimeMillis; }
    public void setScheduledTimeMillis(long scheduledTimeMillis) { this.scheduledTimeMillis = scheduledTimeMillis; }

    /**
     * Return next scheduled time after this execution, or empty if it should not repeat.
     * nowMillis is current time in millis at reschedule decision.
     */
    public abstract OptionalLong nextScheduleTimeAfterRun(long nowMillis);
}

// One-time task
final class OneTimeTask extends ScheduledTask {
    public OneTimeTask(String id, Runnable runnable, long scheduledTimeMillis, TimeUnit unit) {
        super(id, runnable, scheduledTimeMillis, unit, TaskSchedulerType.ONE_TIME);
    }

    @Override
    public OptionalLong nextScheduleTimeAfterRun(long nowMillis) {
        return OptionalLong.empty();
    }
}

// Fixed rate periodic
final class FixedRateTask extends ScheduledTask {
    private final long period;
    public FixedRateTask(String id, Runnable runnable, long scheduledTimeMillis, TimeUnit unit, long period) {
        super(id, runnable, scheduledTimeMillis, unit, TaskSchedulerType.PERIOD_WITH_FIXED_RATE);
        this.period = period;
    }
    @Override
    public OptionalLong nextScheduleTimeAfterRun(long nowMillis) {
        // fixed rate: schedule based on "period" from now (simple approach consistent with your code)
        return OptionalLong.of(nowMillis + getUnit().toMillis(period));
    }
}

// Fixed delay periodic
final class FixedDelayTask extends ScheduledTask {
    private final long delay;
    public FixedDelayTask(String id, Runnable runnable, long scheduledTimeMillis, TimeUnit unit, long delay) {
        super(id, runnable, scheduledTimeMillis, unit, TaskSchedulerType.PERIOD_WITH_FIXED_DELAY);
        this.delay = delay;
    }
    @Override
    public OptionalLong nextScheduleTimeAfterRun(long nowMillis) {
        // fixed delay: schedule after completion + delay
        return OptionalLong.of(nowMillis + getUnit().toMillis(delay));
    }
}

// ---- Service API ----
interface TaskSchedulerService {
    void schedule(Runnable command, long delay, TimeUnit unit);
    void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit);
    void scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit);

    void start();
    void shutdown();
}

// ---- Implementation ----
final class TaskSchedulerServiceImpl implements TaskSchedulerService, AutoCloseable {

    private static final int MAX_THREAD_POOL = 10;

    private final PriorityQueue<ScheduledTask> taskQueue =
            new PriorityQueue<>(Comparator.comparingLong(ScheduledTask::getScheduledTimeMillis));

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

        schedulerThread = new Thread(this::runLoop, "task-scheduler-thread");
        schedulerThread.setDaemon(true);
        schedulerThread.start();
    }

    private void runLoop() {
        while (running) {
            ScheduledTask taskToRun = null;

            lock.lock();
            try {
                while (running && taskQueue.isEmpty()) {
                    notEmptyOrUpdated.await();
                }
                if (!running) break;

                while (running && !taskQueue.isEmpty()) {
                    long now = System.currentTimeMillis();
                    long sleepMs = taskQueue.peek().getScheduledTimeMillis() - now;
                    if (sleepMs <= 0) {
                        taskToRun = taskQueue.poll();
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

            if (!running || taskToRun == null) {
                continue;
            }

            // Execute OUTSIDE the lock (critical fix)
            try {
                if (taskToRun.getType() == TaskSchedulerType.PERIOD_WITH_FIXED_DELAY) {
                    Future<?> f = executor.submit(taskToRun.getRunnable());
                    f.get(); // wait for completion for fixed-delay
                } else {
                    executor.submit(taskToRun.getRunnable());
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException ee) {
                // swallow or log; do not kill scheduler
                System.out.println("Error executing task " + taskToRun.getId() + " : " + ee.getMessage());
            } catch (Exception ex) {
                System.out.println("Error executing task " + taskToRun.getId() + " : " + ex.getMessage());
            }

            // Reschedule if periodic
            long nowAfterRun = System.currentTimeMillis();
            OptionalLong next = taskToRun.nextScheduleTimeAfterRun(nowAfterRun);
            if (next.isPresent() && running) {
                taskToRun.setScheduledTimeMillis(next.getAsLong());
                lock.lock();
                try {
                    taskQueue.add(taskToRun);
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
        ScheduledTask task = new OneTimeTask(UUID.randomUUID().toString(), command, scheduledTime, unit);

        lock.lock();
        try {
            taskQueue.add(task);
            notEmptyOrUpdated.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        long scheduledTime = System.currentTimeMillis() + unit.toMillis(initialDelay);
        ScheduledTask task = new FixedRateTask(UUID.randomUUID().toString(), command, scheduledTime, unit, period);

        lock.lock();
        try {
            taskQueue.add(task);
            notEmptyOrUpdated.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        long scheduledTime = System.currentTimeMillis() + unit.toMillis(initialDelay);
        ScheduledTask task = new FixedDelayTask(UUID.randomUUID().toString(), command, scheduledTime, unit, delay);

        lock.lock();
        try {
            taskQueue.add(task);
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
        TaskSchedulerService scheduler = new TaskSchedulerServiceImpl();
        scheduler.start();

        scheduler.schedule(task("ONE_TIME"), 1, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(task("FIXED_RATE"), 1, 2, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(task("FIXED_DELAY"), 1, 2, TimeUnit.SECONDS);
    }

    private static Runnable task(String name) {
        return () -> {
            System.out.println(name + " started at " + System.currentTimeMillis() / 1000);
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.println(name + " ended at " + System.currentTimeMillis() / 1000);
        };
    }
}
