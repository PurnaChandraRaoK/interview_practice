import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    // ========= Strategy =========
    interface RateLimiter {
        boolean allowRequest(String userId);
    }

    enum StrategyType {
        FIXED,
        TOKEN,
        SLIDING_LOG,
        SLIDING_COUNTER,
        LEAKY;

        static StrategyType from(String s) {
            return StrategyType.valueOf(s.trim().toUpperCase(Locale.ROOT));
        }
    }

    // ========= Fixed Window =========
    static class FixedWindowRateLimiter implements RateLimiter {
        private final int maxRequests;
        private final long windowSizeInMillis;
        private final ConcurrentHashMap<String, RequestCounter> userRequestMap = new ConcurrentHashMap<>();

        FixedWindowRateLimiter(int maxRequests, long windowSizeInMillis) {
            this.maxRequests = maxRequests;
            this.windowSizeInMillis = windowSizeInMillis;
        }

        private static class RequestCounter {
            final long windowStart;
            final AtomicInteger count;

            RequestCounter(long windowStart) {
                this.windowStart = windowStart;
                this.count = new AtomicInteger(1);
            }
        }

        @Override
        public boolean allowRequest(String userId) {
            long now = System.currentTimeMillis();

            RequestCounter counter = userRequestMap.compute(userId, (k, v) -> {
                if (v == null || now - v.windowStart >= windowSizeInMillis) {
                    return new RequestCounter(now);
                }
                v.count.incrementAndGet();
                return v;
            });

            return counter.count.get() <= maxRequests;
        }
    }

    // ========= Token Bucket =========
    static class TokenBucketRateLimiter implements RateLimiter {
        private final int capacity;
        private final int refillRatePerSecond; // tokens per second
        private final ConcurrentHashMap<String, Bucket> userBuckets = new ConcurrentHashMap<>();

        TokenBucketRateLimiter(int capacity, int refillRatePerSecond) {
            if (capacity <= 0 || refillRatePerSecond <= 0) throw new IllegalArgumentException("Invalid capacity/refillRate");
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
        }

        private static class Bucket {
            int tokens;
            long lastRefillTimestamp;

            Bucket(int capacity) {
                this.tokens = capacity;
                this.lastRefillTimestamp = System.currentTimeMillis();
            }

            synchronized boolean allowRequest(int refillRatePerSecond, int capacity) {
                long now = System.currentTimeMillis();
                long elapsedMillis = now - lastRefillTimestamp;

                long tokensToAdd = (elapsedMillis * refillRatePerSecond) / 1000L;
                if (tokensToAdd > 0) {
                    long newTokens = Math.min(capacity, (long) tokens + tokensToAdd);
                    tokens = (int) newTokens;

                    // advance timestamp by accounted time to preserve remainder
                    long accountedMillis = (tokensToAdd * 1000L) / refillRatePerSecond;
                    lastRefillTimestamp += accountedMillis;
                }

                if (tokens > 0) {
                    tokens--;
                    return true;
                }
                return false;
            }
        }

        @Override
        public boolean allowRequest(String userId) {
            Bucket b = userBuckets.computeIfAbsent(userId, id -> new Bucket(capacity));
            return b.allowRequest(refillRatePerSecond, capacity);
        }
    }

    // ========= Sliding Window (Log) =========
    static class SlidingWindowLogRateLimiter implements RateLimiter {
        private final int maxRequests;
        private final long windowSizeInMillis;
        private final ConcurrentHashMap<String, Deque<Long>> userRequestLog = new ConcurrentHashMap<>();

        SlidingWindowLogRateLimiter(int maxRequests, long windowSizeInMillis) {
            this.maxRequests = maxRequests;
            this.windowSizeInMillis = windowSizeInMillis;
        }

        @Override
        public boolean allowRequest(String userId) {
            long now = System.currentTimeMillis();
            Deque<Long> logs = userRequestLog.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());

            synchronized (logs) {
                while (!logs.isEmpty() && now - logs.peekFirst() >= windowSizeInMillis) {
                    logs.pollFirst();
                }
                if (logs.size() < maxRequests) {
                    logs.addLast(now);
                    return true;
                }
                // optional cleanup
                if (logs.isEmpty()) userRequestLog.remove(userId, logs);
                return false;
            }
        }
    }

    // ========= Leaky Bucket =========
    static class LeakyBucketRateLimiter implements RateLimiter {
        private final int capacity;
        private final int leakRatePerSecond;
        private final ConcurrentHashMap<String, Bucket> userBuckets = new ConcurrentHashMap<>();

        LeakyBucketRateLimiter(int capacity, int leakRatePerSecond) {
            if (capacity <= 0 || leakRatePerSecond <= 0) throw new IllegalArgumentException("Invalid capacity/leakRate");
            this.capacity = capacity;
            this.leakRatePerSecond = leakRatePerSecond;
        }

        private static class Bucket {
            int water;
            long lastLeakTimestamp;

            Bucket() {
                this.water = 0;
                this.lastLeakTimestamp = System.currentTimeMillis();
            }

            synchronized boolean allowRequest(int capacity, int leakRatePerSecond) {
                long now = System.currentTimeMillis();
                long elapsedMillis = now - lastLeakTimestamp;

                long leaked = (elapsedMillis * leakRatePerSecond) / 1000L;
                if (leaked > 0) {
                    water = Math.max(0, water - (int) leaked);

                    long accountedMillis = (leaked * 1000L) / leakRatePerSecond;
                    lastLeakTimestamp += accountedMillis;
                }

                if (water < capacity) {
                    water++;
                    return true;
                }
                return false;
            }
        }

        @Override
        public boolean allowRequest(String userId) {
            Bucket b = userBuckets.computeIfAbsent(userId, id -> new Bucket());
            return b.allowRequest(capacity, leakRatePerSecond);
        }
    }

    // ========= Sliding Window (Counter) =========
    static class SlidingWindowCounterRateLimiter implements RateLimiter {
        private final int maxRequests;
        private final int windowSizeInSeconds;

        // userId -> (epochSecond -> count)
        private final ConcurrentHashMap<String, ConcurrentHashMap<Long, AtomicInteger>> userWindows = new ConcurrentHashMap<>();

        SlidingWindowCounterRateLimiter(int maxRequests, int windowSizeInSeconds) {
            if (maxRequests <= 0 || windowSizeInSeconds <= 0) throw new IllegalArgumentException("Invalid max/window");
            this.maxRequests = maxRequests;
            this.windowSizeInSeconds = windowSizeInSeconds;
        }

        @Override
        public boolean allowRequest(String userId) {
            long currentSecond = System.currentTimeMillis() / 1000L;
            long earliest = currentSecond - windowSizeInSeconds + 1;

            ConcurrentHashMap<Long, AtomicInteger> window =
                    userWindows.computeIfAbsent(userId, id -> new ConcurrentHashMap<>());

            // lock per-user window to make "check + increment" atomic
            synchronized (window) {
                // cleanup old buckets
                for (Long sec : window.keySet()) {
                    if (sec < earliest) window.remove(sec);
                }

                int total = 0;
                for (long sec = earliest; sec <= currentSecond; sec++) {
                    AtomicInteger c = window.get(sec);
                    if (c != null) total += c.get();
                    if (total >= maxRequests) return false;
                }

                window.computeIfAbsent(currentSecond, k -> new AtomicInteger(0)).incrementAndGet();
                return true;
            }
        }
    }

    // ========= Factory =========
    static class RateLimiterFactory {
        static RateLimiter getRateLimiter(StrategyType type) {
            switch (type) {
                case FIXED:
                    return new FixedWindowRateLimiter(5, 10_000);        // 5 req / 10 sec
                case TOKEN:
                    return new TokenBucketRateLimiter(10, 5);            // capacity 10, refill 5/sec
                case SLIDING_LOG:
                    return new SlidingWindowLogRateLimiter(5, 10_000);   // 5 req / 10 sec
                case SLIDING_COUNTER:
                    return new SlidingWindowCounterRateLimiter(5, 10);   // 5 req / 10 sec
                case LEAKY:
                    return new LeakyBucketRateLimiter(10, 2);            // capacity 10, leak 2/sec
                default:
                    throw new IllegalArgumentException("Unsupported strategy: " + type);
            }
        }
    }

    // ========= Services (thin wrappers) =========
    static class RateLimiterService {
        private final RateLimiter rateLimiter;

        RateLimiterService(String strategyType) {
            this.rateLimiter = RateLimiterFactory.getRateLimiter(StrategyType.from(strategyType));
        }

        boolean processRequest(String userId) {
            boolean allowed = rateLimiter.allowRequest(userId);
            if (allowed) {
                System.out.println("✅ Allowed: " + userId);
            } else {
                System.out.println("❌ Blocked (Rate Limit Exceeded): " + userId);
            }
            return allowed;
        }
    }

    static class ApplicationService {
        private final RateLimiterService rateLimiterService;

        ApplicationService(String strategyType) {
            this.rateLimiterService = new RateLimiterService(strategyType);
        }

        void handleRequest(String userId) {
            if (rateLimiterService.processRequest(userId)) {
                System.out.println("✅ Processing request for user: " + userId);
            } else {
                System.out.println("⚠️ Too many requests for user: " + userId);
            }
        }
    }

    // ========= Demo =========
    public static void main(String[] args) throws InterruptedException {
        ApplicationService service = new ApplicationService("SLIDING_COUNTER"); // try: TOKEN / FIXED / SLIDING_LOG / LEAKY
        String userId = "user123";

        for (int i = 0; i < 15; i++) {
            service.handleRequest(userId);
            Thread.sleep(700); // simulate incoming requests
        }
    }
}