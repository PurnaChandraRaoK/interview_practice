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
// Chain of Responsibility: Handlers
// =======================
abstract class LogHandler {
    // keep old int API
    public static final int DEBUG = 1;
    public static final int INFO  = 2;
    public static final int ERROR = 3;

    protected final int level;        // this node's level
    protected LogHandler next;
    protected final LogAppender appender;

    public LogHandler(int level, LogAppender appender) {
        this.level = level;
        this.appender = appender;
    }

    public void setNext(LogHandler next) { this.next = next; }

    public final void logMessage(LogLevel level, String message) {
        logMessage(level.getValue(), message);
    }

    // Simple & interview-friendly: exact match node handles it, else forward.
    public void logMessage(int level, String message) {
        if (this.level == level) {
            LogMessage msg = new LogMessage(LogLevel.fromInt(level), message);
            if (appender != null) appender.append(msg);
            else writeFallback(msg); // safety
        } else if (next != null) {
            next.logMessage(level, message);
        }
    }

    protected abstract void writeFallback(LogMessage msg);
}

class DebugLogger extends LogHandler {
    public DebugLogger(LogAppender appender) { super(LogHandler.DEBUG, appender); }
    @Override protected void writeFallback(LogMessage msg) {
        System.out.println("DEBUG: " + msg.getMessage());
    }
}

class InfoLogger extends LogHandler {
    public InfoLogger(LogAppender appender) { super(LogHandler.INFO, appender); }
    @Override protected void writeFallback(LogMessage msg) {
        System.out.println("INFO: " + msg.getMessage());
    }
}

class ErrorLogger extends LogHandler {
    public ErrorLogger(LogAppender appender) { super(LogHandler.ERROR, appender); }
    @Override protected void writeFallback(LogMessage msg) {
        System.out.println("ERROR: " + msg.getMessage());
    }
}

// =======================
// Chain Builder
// =======================
final class LogChainFactory {
    private LogChainFactory() {}

    public static LogHandler build(LogAppender appender) {
        LogHandler debug = new DebugLogger(appender);
        LogHandler info  = new InfoLogger(appender);
        LogHandler error = new ErrorLogger(appender);

        debug.setNext(info);
        info.setNext(error);

        return debug; // head
    }
}

// =======================
// Facade Singleton Logger (uses LogHandler chain)
// =======================
final class LoggerConfig {
    private LogLevel threshold;
    private LogAppender appender;
    private LogHandler chainHead;

    public LoggerConfig(LogLevel threshold, LogAppender appender) {
        this.threshold = threshold;
        setAppender(appender); // also builds chain
    }

    public LogLevel getThreshold() { return threshold; }
    public void setThreshold(LogLevel threshold) { this.threshold = threshold; }

    public LogAppender getAppender() { return appender; }
    public LogHandler getChainHead() { return chainHead; }

    public void setAppender(LogAppender appender) {
        this.appender = appender;
        this.chainHead = LogChainFactory.build(appender); // rebuild chain if destination changes
    }
}

final class Logger {
    private static final ConcurrentHashMap<String, Logger> INSTANCES = new ConcurrentHashMap<>();
    private volatile LoggerConfig config;

    private Logger(LoggerConfig config) { this.config = config; }

    public static Logger getInstance(LogLevel threshold, LogAppender appender) {
        String key = threshold.name() + "::" + (appender == null ? "null" : appender.id());
        return INSTANCES.computeIfAbsent(key, k -> new Logger(new LoggerConfig(threshold, appender)));
    }

    public void setConfig(LoggerConfig config) { this.config = config; }

    public void log(LogLevel level, String message) {
        LoggerConfig cfg = this.config;
        if (cfg == null) return;

        // threshold filter at facade level
        if (!level.isGreaterOrEqual(cfg.getThreshold())) return;

        // ✅ important: go through handler chain (CoR)
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
        LogAppender file = new FileAppender("logs.txt");

        // Facade + CoR + Strategy
        Logger logger = Logger.getInstance(LogLevel.INFO, console);

        logger.info("Hello from console (INFO)");
        logger.debug("This will NOT print because threshold is INFO");
        logger.error("Console error");

        // switch destination (rebuild chain internally)
        LoggerConfig cfg = new LoggerConfig(LogLevel.DEBUG, file);
        logger.setConfig(cfg);

        logger.debug("Now debug goes to file");
        logger.info("Info goes to file");
        logger.error("Error goes to file");
    }
}
