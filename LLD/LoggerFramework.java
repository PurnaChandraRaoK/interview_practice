// =======================
// Core Domain
// =======================
enum LogLevel {
    DEBUG(1), INFO(2), ERROR(3);

    private final int value;
    LogLevel(int value) { this.value = value; }
    public int getValue() { return value; }

    public boolean isGreaterOrEqual(LogLevel other) {
        return this.value >= other.value;
    }

    public static LogLevel fromInt(int level) {
        switch (level) {
            case 1: return DEBUG;
            case 2: return INFO;
            case 3: return ERROR;
            default: return INFO;
        }
    }
}

final class LogMessage {
    private final LogLevel level;
    private final String message;
    private final long timestamp;

    public LogMessage(LogLevel level, String message) {
        this.level = level;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    public LogLevel getLevel() { return level; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "[" + level + "] " + timestamp + " - " + message;
    }
}

// =======================
// Strategy: Appenders
// =======================
interface LogAppender {
    void append(LogMessage logMessage);
    default String id() { return getClass().getName(); }
}

class ConsoleAppender implements LogAppender {
    @Override public void append(LogMessage logMessage) {
        System.out.println(logMessage);
    }
}

class FileAppender implements LogAppender {
    private final String filePath;
    public FileAppender(String filePath) { this.filePath = filePath; }

    @Override public void append(LogMessage logMessage) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            writer.write(logMessage.toString());
            writer.write(System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override public String id() { return "FileAppender:" + filePath; }
}

// =======================
// Appender Routing (NEW)
// =======================
interface AppenderRouter {
    List<LogAppender> getAppenders(LogLevel level);
}

// =======================
// Chain of Responsibility: Handlers
// =======================
abstract class LogHandler {
    public static final int DEBUG = 1;
    public static final int INFO  = 2;
    public static final int ERROR = 3;

    protected final int level; // exact level this handler handles
    protected LogHandler next;
    protected final AppenderRouter router; // NEW: decides which appenders to use

    public LogHandler(int level, AppenderRouter router) {
        this.level = level;
        this.router = router;
    }

    public void setNext(LogHandler next) { this.next = next; }

    public final void logMessage(LogLevel level, String message) {
        logMessage(level.getValue(), message);
    }

    // Simple: exact match handles, else forward
    public void logMessage(int level, String message) {
        if (this.level == level) {
            LogLevel enumLevel = LogLevel.fromInt(level);
            LogMessage msg = new LogMessage(enumLevel, message);

            List<LogAppender> appenders = (router == null) ? Collections.emptyList() : router.getAppenders(enumLevel);
            if (appenders != null && !appenders.isEmpty()) {
                for (LogAppender a : appenders) {
                    if (a != null) a.append(msg);
                }
            } else {
                // fallback if nothing configured
                writeFallback(msg);
            }
        } else if (next != null) {
            next.logMessage(level, message);
        }
    }

    protected abstract void writeFallback(LogMessage msg);
}

class DebugLogger extends LogHandler {
    public DebugLogger(AppenderRouter router) { super(LogHandler.DEBUG, router); }
    @Override protected void writeFallback(LogMessage msg) {
        System.out.println("DEBUG: " + msg.getMessage());
    }
}

class InfoLogger extends LogHandler {
    public InfoLogger(AppenderRouter router) { super(LogHandler.INFO, router); }
    @Override protected void writeFallback(LogMessage msg) {
        System.out.println("INFO: " + msg.getMessage());
    }
}

class ErrorLogger extends LogHandler {
    public ErrorLogger(AppenderRouter router) { super(LogHandler.ERROR, router); }
    @Override protected void writeFallback(LogMessage msg) {
        System.out.println("ERROR: " + msg.getMessage());
    }
}

// =======================
// Chain Builder
// =======================
final class LogChainFactory {
    private LogChainFactory() {}

    public static LogHandler build(AppenderRouter router) {
        LogHandler debug = new DebugLogger(router);
        LogHandler info  = new InfoLogger(router);
        LogHandler error = new ErrorLogger(router);

        debug.setNext(info);
        info.setNext(error);

        return debug; // head
    }
}

// =======================
// Facade Singleton Logger (Configurable appenders per level)
// =======================
final class LoggerConfig implements AppenderRouter {
    private LogLevel threshold;

    // NEW: level -> list of appenders
    private final EnumMap<LogLevel, List<LogAppender>> appendersByLevel = new EnumMap<>(LogLevel.class);

    // chain head kept inside config
    private LogHandler chainHead;

    public LoggerConfig(LogLevel threshold) {
        this.threshold = threshold;
        this.chainHead = LogChainFactory.build(this);
    }

    public LogLevel getThreshold() { return threshold; }
    public void setThreshold(LogLevel threshold) { this.threshold = threshold; }

    public LogHandler getChainHead() { return chainHead; }

    // Configure appenders for a level (replace existing)
    public LoggerConfig setAppenders(LogLevel level, LogAppender... appenders) {
        List<LogAppender> list = new ArrayList<>();
        if (appenders != null) {
            for (LogAppender a : appenders) if (a != null) list.add(a);
        }
        appendersByLevel.put(level, list);
        return this;
    }

    // Add one appender (keep existing)
    public LoggerConfig addAppender(LogLevel level, LogAppender appender) {
        appendersByLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(appender);
        return this;
    }

    @Override
    public List<LogAppender> getAppenders(LogLevel level) {
        List<LogAppender> list = appendersByLevel.get(level);
        return (list == null) ? Collections.emptyList() : list;
    }
}

final class Logger {
    private static final ConcurrentHashMap<String, Logger> INSTANCES = new ConcurrentHashMap<>();
    private volatile LoggerConfig config;

    private Logger(LoggerConfig config) { this.config = config; }

    // simple singleton-ish keying (not super important for interview)
    public static Logger getInstance(String name, LoggerConfig config) {
        return INSTANCES.computeIfAbsent(name, k -> new Logger(config));
    }

    public void setConfig(LoggerConfig config) { this.config = config; }

    public void log(LogLevel level, String message) {
        LoggerConfig cfg = this.config;
        if (cfg == null) return;

        // threshold filter at facade
        if (!level.isGreaterOrEqual(cfg.getThreshold())) return;

        // IMPORTANT: delegate to CoR chain (handlers route to configured appenders)
        LogHandler chain = cfg.getChainHead();
        if (chain != null) chain.logMessage(level, message);
    }

    public void debug(String message) { log(LogLevel.DEBUG, message); }
    public void info(String message)  { log(LogLevel.INFO, message); }
    public void error(String message) { log(LogLevel.ERROR, message); }
}

// =======================
// Demo
// =======================
public class Main {
    public static void main(String[] args) {
        LogAppender console = new ConsoleAppender();
        LogAppender infoFile  = new FileAppender("info.log");
        LogAppender debugFile = new FileAppender("debug.log");
        LogAppender errorFile = new FileAppender("error.log");

        // Configure:
        // INFO  -> console + info.log
        // DEBUG -> debug.log
        // ERROR -> console + error.log
        LoggerConfig cfg = new LoggerConfig(LogLevel.DEBUG)
                .setAppenders(LogLevel.INFO, console, infoFile)
                .setAppenders(LogLevel.DEBUG, debugFile)
                .setAppenders(LogLevel.ERROR, console, errorFile);

        Logger logger = Logger.getInstance("app", cfg);

        logger.debug("debug message");   // goes to debug.log
        logger.info("info message");     // goes to console + info.log
        logger.error("error message");   // goes to console + error.log
    }
}
