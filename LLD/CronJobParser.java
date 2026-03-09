import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;

// -------------------- Strategy: CRON Field Parsing --------------------
interface CronFieldParser {
    List<Integer> parse(String field, int min, int max);
}

class SimpleCronFieldParser implements CronFieldParser {

    @Override
    public List<Integer> parse(String field, int min, int max) {
        if (field == null || field.trim().isEmpty()) {
            throw new IllegalArgumentException("Cron field cannot be empty");
        }

        field = field.trim();
        Set<Integer> values = new TreeSet<>(); // sorted + dedup

        if ("*".equals(field)) {
            addRange(values, min, max);
        } else if (field.contains("/")) {
            // step: "*/5" or "0/5"
            String[] parts = field.split("/");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid step format: " + field);

            String startToken = parts[0].trim();
            int start = "*".equals(startToken) ? min : parseInt(startToken, field);
            int step = parseInt(parts[1].trim(), field);

            if (step <= 0) throw new IllegalArgumentException("Step must be > 0: " + field);

            validateRange(start, min, max, field);
            for (int i = start; i <= max; i += step) {
                values.add(i);
            }
        } else if (field.contains(",")) {
            // list: "8,12"
            for (String part : field.split(",")) {
                int v = parseInt(part.trim(), field);
                validateRange(v, min, max, field);
                values.add(v);
            }
        } else if (field.contains("-")) {
            // range: "1-5"
            String[] parts = field.split("-");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid range format: " + field);

            int start = parseInt(parts[0].trim(), field);
            int end = parseInt(parts[1].trim(), field);

            validateRange(start, min, max, field);
            validateRange(end, min, max, field);
            if (start > end) throw new IllegalArgumentException("Range start > end: " + field);

            addRange(values, start, end);
        } else {
            // single value
            int v = parseInt(field, field);
            validateRange(v, min, max, field);
            values.add(v);
        }

        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private void addRange(Set<Integer> values, int start, int end) {
        for (int i = start; i <= end; i++) values.add(i);
    }

    private int parseInt(String token, String fullField) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in field: " + fullField);
        }
    }

    private void validateRange(int v, int min, int max, String field) {
        if (v < min || v > max) {
            throw new IllegalArgumentException("Value out of range [" + min + "," + max + "] in field: " + field);
        }
    }
}

// -------------------- Builder: CRON Expression --------------------
final class CronExpression {
    private final List<Integer> minutes;
    private final List<Integer> hours;
    private final List<Integer> days;
    private final List<Integer> months;
    private final List<Integer> daysOfWeek;

    private CronExpression(Builder builder) {
        this.minutes = safeList(builder.minutes, "minutes");
        this.hours = safeList(builder.hours, "hours");
        this.days = safeList(builder.days, "days");
        this.months = safeList(builder.months, "months");
        this.daysOfWeek = safeList(builder.daysOfWeek, "daysOfWeek");
    }

    private List<Integer> safeList(List<Integer> list, String name) {
        if (list == null) throw new IllegalStateException("Missing field: " + name);
        return list;
    }

    public static class Builder {
        private List<Integer> minutes;
        private List<Integer> hours;
        private List<Integer> days;
        private List<Integer> months;
        private List<Integer> daysOfWeek;

        public Builder setMinutes(List<Integer> minutes) { this.minutes = minutes; return this; }
        public Builder setHours(List<Integer> hours) { this.hours = hours; return this; }
        public Builder setDays(List<Integer> days) { this.days = days; return this; }
        public Builder setMonths(List<Integer> months) { this.months = months; return this; }
        public Builder setDaysOfWeek(List<Integer> daysOfWeek) { this.daysOfWeek = daysOfWeek; return this; }

        public CronExpression build() { return new CronExpression(this); }
    }

    public List<Integer> getMinutes() { return minutes; }
    public List<Integer> getHours() { return hours; }
    public List<Integer> getDays() { return days; }
    public List<Integer> getMonths() { return months; }
    public List<Integer> getDaysOfWeek() { return daysOfWeek; }
}

// -------------------- Parser (DI-friendly) --------------------
class CronJobParser {
    private static final int MIN_MINUTE = 0, MAX_MINUTE = 59;
    private static final int MIN_HOUR = 0, MAX_HOUR = 23;
    private static final int MIN_DAY = 1, MAX_DAY = 31;
    private static final int MIN_MONTH = 1, MAX_MONTH = 12;

    // NOTE: Kept 1..7 to match your code; change to 0..6 if desired
    private static final int MIN_DOW = 1, MAX_DOW = 7;

    private final CronFieldParser fieldParser;

    public CronJobParser() {
        this(new SimpleCronFieldParser());
    }

    public CronJobParser(CronFieldParser fieldParser) {
        this.fieldParser = Objects.requireNonNull(fieldParser);
    }

    public CronExpression parse(String cronExpression) {
        String[] fields = cronExpression == null ? new String[0] : cronExpression.trim().split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException("Invalid cron expression format. Expected 5 fields.");
        }

        return new CronExpression.Builder()
                .setMinutes(fieldParser.parse(fields[0], MIN_MINUTE, MAX_MINUTE))
                .setHours(fieldParser.parse(fields[1], MIN_HOUR, MAX_HOUR))
                .setDays(fieldParser.parse(fields[2], MIN_DAY, MAX_DAY))
                .setMonths(fieldParser.parse(fields[3], MIN_MONTH, MAX_MONTH))
                .setDaysOfWeek(fieldParser.parse(fields[4], MIN_DOW, MAX_DOW))
                .build();
    }

    // Still simplified as in your code (no major logic change)
    public long getNextExecutionDelayMinutes(String cronExpression) {
        return 1;
    }

    public long getPeriodMinutes(String cronExpression) {
        return 5;
    }

    public void printExpandedExpression(String cronExpression) {
        CronExpression parsed = parse(cronExpression);
        System.out.println("CRON Expression: " + cronExpression);
        System.out.println("Minutes: " + parsed.getMinutes());
        System.out.println("Hours: " + parsed.getHours());
        System.out.println("Days: " + parsed.getDays());
        System.out.println("Months: " + parsed.getMonths());
        System.out.println("DaysOfWeek: " + parsed.getDaysOfWeek());
    }
}

// -------------------- Command: Job --------------------
interface Job {
    void execute();
    String getId();
}

class ConcreteJob implements Job {
    private final String id;
    private final Runnable task;

    public ConcreteJob(String id, Runnable task) {
        this.id = Objects.requireNonNull(id);
        this.task = Objects.requireNonNull(task);
    }

    @Override
    public void execute() {
        System.out.println("Executing job: " + id + " at " + LocalDateTime.now());
        task.run();
    }

    @Override
    public String getId() {
        return id;
    }
}

// -------------------- Observer: Job Events --------------------
interface JobEventListener {
    void onJobStarted(String jobId);
    void onJobCompleted(String jobId);
    void onJobFailed(String jobId, Exception e);
}

class JobEventNotifier {
    private final List<JobEventListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(JobEventListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void notifyJobStarted(String jobId) {
        for (JobEventListener l : listeners) l.onJobStarted(jobId);
    }

    public void notifyJobCompleted(String jobId) {
        for (JobEventListener l : listeners) l.onJobCompleted(jobId);
    }

    public void notifyJobFailed(String jobId, Exception e) {
        for (JobEventListener l : listeners) l.onJobFailed(jobId, e);
    }
}

// -------------------- Strategy: Scheduling --------------------
interface SchedulingStrategy {
    void schedule(Job job, String cronExpression, JobEventNotifier notifier);
    void shutdown();
}

class CronSchedulingStrategy implements SchedulingStrategy {
    private final ScheduledExecutorService executor;
    private final CronJobParser cronParser;

    public CronSchedulingStrategy() {
        this(Executors.newScheduledThreadPool(4), new CronJobParser());
    }

    public CronSchedulingStrategy(ScheduledExecutorService executor, CronJobParser cronParser) {
        this.executor = Objects.requireNonNull(executor);
        this.cronParser = Objects.requireNonNull(cronParser);
    }

    @Override
    public void schedule(Job job, String cronExpression, JobEventNotifier notifier) {
        long initialDelay = cronParser.getNextExecutionDelayMinutes(cronExpression);
        long period = cronParser.getPeriodMinutes(cronExpression);

        executor.scheduleAtFixedRate(() -> {
            notifier.notifyJobStarted(job.getId());
            try {
                job.execute();
                notifier.notifyJobCompleted(job.getId());
            } catch (Exception e) {
                notifier.notifyJobFailed(job.getId(), e);
            }
        }, initialDelay, period, TimeUnit.MINUTES);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}

// -------------------- Singleton: Scheduler --------------------
class JobScheduler {
    private final SchedulingStrategy strategy;
    private final JobEventNotifier eventNotifier;
    private final Map<String, Job> scheduledJobs = new ConcurrentHashMap<>();

    private JobScheduler(SchedulingStrategy strategy, JobEventNotifier eventNotifier) {
        this.strategy = Objects.requireNonNull(strategy);
        this.eventNotifier = Objects.requireNonNull(eventNotifier);
    }

    // Initialization-on-demand holder (thread-safe, simpler than synchronized getInstance)
    private static class Holder {
        private static final JobScheduler INSTANCE =
                new JobScheduler(new CronSchedulingStrategy(), new JobEventNotifier());
    }

    public static JobScheduler getInstance() {
        return Holder.INSTANCE;
    }

    public void scheduleJob(Job job, String cronExpression) {
        Objects.requireNonNull(job);
        Objects.requireNonNull(cronExpression);

        scheduledJobs.put(job.getId(), job);
        strategy.schedule(job, cronExpression, eventNotifier);
    }

    public void addEventListener(JobEventListener listener) {
        eventNotifier.addListener(listener);
    }

    public Optional<Job> getJob(String jobId) {
        return Optional.ofNullable(scheduledJobs.get(jobId));
    }

    public void shutdown() {
        strategy.shutdown();
    }
}
