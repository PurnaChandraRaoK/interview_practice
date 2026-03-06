// =======================
// Core Domain
// =======================

enum LogLevel {
    DEBUG(1),
    INFO(2),
    ERROR(3);

    private final int value;

    LogLevel(int value) { this.value = value; }

    public int getValue() { return value; }

    public boolean isGreaterOrEqual(LogLevel other) {
        return this.value >= other.value;
    }

    // Utility for bridging old int-based APIs
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

    // Helps singleton keying when appenders have state (file path etc.)
    default String id() { return getClass().getName(); }
}

class ConsoleAppender implements LogAppender {
    @Override
    public void append(LogMessage logMessage) {
        System.out.println(logMessage);
    }
}

class FileAppender implements LogAppender {
    private final String filePath;

    public FileAppender(String filePath) { this.filePath = filePath; }

    @Override
    public void append(LogMessage logMessage) {
        try (java.io.FileWriter writer = new java.io.FileWriter(filePath, true)) {
            writer.write(logMessage.toString());
            writer.write(System.lineSeparator()); // correct newline
        } catch (java.io.IOException e) {
            // LLD: handle via fallback appender / stderr / error policy
            e.printStackTrace();
        }
    }

    @Override
    public String id() {
        return "FileAppender:" + filePath; // include state to avoid collisions
    }
}


// =======================
// Chain of Responsibility: Handlers
// =======================

abstract class LogHandler {

    // Keep int constants for backward compatibility, but align with LogLevel values.
    public static final int DEBUG = 1;
    public static final int INFO  = 2;
    public static final int ERROR = 3;

    protected final int level;            // handler’s level
    protected LogHandler nextLogger;
    protected final LogAppender appender; // where to send formatted log messages

    public LogHandler(int level, LogAppender appender) {
        this.level = level;
        this.appender = appender;
    }

    public void setNextLogger(LogHandler nextLogger) {
        this.nextLogger = nextLogger;
    }

    // Preferred overload (LLD): use enum for correctness and clarity.
    public final void logMessage(LogLevel level, String message) {
        logMessage(level.getValue(), message);
    }

    // Existing int-based API (kept), but internally consistent.
    public void logMessage(int level, String message) {

        // Minimal behavioral fix: handle EXACT level in this handler, otherwise delegate.
        // (Keeps “one handler per level” chain clean and predictable.)
        if (this.level == level) {
            LogLevel enumLevel = LogLevel.fromInt(level);
            LogMessage logMsg = new LogMessage(enumLevel, message);

            // Avoid double logging: appender is the output mechanism.
            if (appender != null) {
                appender.append(logMsg);
            } else {
                // fallback if no appender is wired
                write(logMsg);
            }

        } else if (nextLogger != null) {
            nextLogger.logMessage(level, message);
        } else {
            // LLD: could drop silently or route to a default handler/appender
            // (kept intentionally simple)
        }
    }

    // Concrete handler-specific formatting / fallback output
    protected abstract void write(LogMessage msg);
}


// Concrete handlers: keep simple, mainly serve as “chain nodes”
// (In this design, most output goes through appender; write() is fallback)
class DebugLogger extends LogHandler {
    public DebugLogger(LogAppender appender) { super(LogHandler.DEBUG, appender); }

    @Override
    protected void write(LogMessage msg) {
        System.out.println("DEBUG: " + msg.getMessage());
    }
}

class InfoLogger extends LogHandler {
    public InfoLogger(LogAppender appender) { super(LogHandler.INFO, appender); }

    @Override
    protected void write(LogMessage msg) {
        System.out.println("INFO: " + msg.getMessage());
    }
}

class ErrorLogger extends LogHandler {
    public ErrorLogger(LogAppender appender) { super(LogHandler.ERROR, appender); }

    @Override
    protected void write(LogMessage msg) {
        System.out.println("ERROR: " + msg.getMessage());
    }
}


// =======================
// Optional Facade Singleton (Alternative API)
// =======================

final class LoggerConfig {
    private LogLevel logLevel;
    private LogAppender logAppender;

    public LoggerConfig(LogLevel logLevel, LogAppender logAppender) {
        this.logLevel = logLevel;
        this.logAppender = logAppender;
    }

    public LogLevel getLogLevel() { return logLevel; }
    public LogAppender getLogAppender() { return logAppender; }

    public void setLogLevel(LogLevel logLevel) { this.logLevel = logLevel; }
    public void setLogAppender(LogAppender logAppender) { this.logAppender = logAppender; }
}

final class Logger {
    // Multiton keyed by config identity (level + appender identity)
    private static final java.util.concurrent.ConcurrentHashMap<String, Logger> INSTANCES =
            new java.util.concurrent.ConcurrentHashMap<>();

    // Ensure visibility across threads for config changes
    private volatile LoggerConfig config;

    private Logger(LoggerConfig config) {
        this.config = config;
    }

    public static Logger getInstance(LogLevel level, LogAppender appender) {
        String key = level.name() + "::" + (appender == null ? "null" : appender.id());
        return INSTANCES.computeIfAbsent(key, k -> new Logger(new LoggerConfig(level, appender)));
    }

    public void setConfig(LoggerConfig config) {
        this.config = config; // volatile write => visible to log() readers
    }

    public void log(LogLevel level, String message) {
        LoggerConfig cfg = this.config; // local snapshot (volatile read once)

        if (cfg == null || cfg.getLogAppender() == null) return;

        // Threshold style: only log if message severity >= configured threshold
        if (level.isGreaterOrEqual(cfg.getLogLevel())) {
            cfg.getLogAppender().append(new LogMessage(level, message));
        }
    }

    public void debug(String message) { log(LogLevel.DEBUG, message); }
    public void info(String message)  { log(LogLevel.INFO, message); }
    public void error(String message) { log(LogLevel.ERROR, message); }
}


// =======================
// Composition / Builder (LLD-style wiring)
// =======================

final class LogChainFactory {

    // Build chain: DEBUG -> INFO -> ERROR (or any order you prefer)
    // Here: exact match chain order doesn’t matter much as long as it reaches the correct node.
    public static LogHandler build(LogAppender appender) {
        LogHandler debug = new DebugLogger(appender);
        LogHandler info  = new InfoLogger(appender);
        LogHandler error = new ErrorLogger(appender);

        debug.setNextLogger(info);
        info.setNextLogger(error);

        return debug; // chain head
    }
}


// =======================
// Demo (LLD)
// =======================

class Main {
    public static void main(String[] args) {
        LogAppender console = new ConsoleAppender();
        LogAppender file = new FileAppender("logs.txt");

        // Chain of Responsibility usage
        LogHandler chain = LogChainFactory.build(console);

        chain.logMessage(LogLevel.INFO,  "This is an information.");
        chain.logMessage(LogLevel.DEBUG, "This is a debug level information.");
        chain.logMessage(LogLevel.ERROR, "This is an error information.");

        // Singleton/Facade usage (optional alternate API)
        Logger logger = Logger.getInstance(LogLevel.INFO, console);
        logger.setConfig(new LoggerConfig(LogLevel.INFO, file)); // switch destination
        logger.error("Using singleton Logger - Error message");
    }
}
