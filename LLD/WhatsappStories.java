import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

// -------------------- Domain --------------------

public final class Story {
    private final String id;
    private final String userId;
    private final String mediaUrl;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private final String caption;

    private Story(Builder b) {
        this.id = b.id;
        this.userId = b.userId;
        this.mediaUrl = b.mediaUrl;
        this.caption = b.caption;
        this.createdAt = b.createdAt;
        this.expiresAt = b.expiresAt;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getMediaUrl() { return mediaUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public String getCaption() { return caption; }

    public boolean isActive(Clock clock) {
        return expiresAt.isAfter(LocalDateTime.now(clock));
    }

    public static Builder builder(Clock clock) {
        return new Builder(clock);
    }

    public static final class Builder {
        private final Clock clock;
        private final Duration ttl;

        private String id;
        private String userId;
        private String mediaUrl;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private String caption;

        // default TTL = 24h (same as your logic)
        private Builder(Clock clock) {
            this(clock, Duration.ofHours(24));
        }

        private Builder(Clock clock, Duration ttl) {
            this.clock = Objects.requireNonNull(clock, "clock");
            this.ttl = Objects.requireNonNull(ttl, "ttl");
        }

        public Builder id(String id) { this.id = id; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder mediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; return this; }
        public Builder caption(String caption) { this.caption = caption; return this; }

        public Story build() {
            requireNonBlank(id, "id");
            requireNonBlank(userId, "userId");
            requireNonBlank(mediaUrl, "mediaUrl");

            // Preserve existing values if explicitly set (extensible),
            // but default to "now" + 24h like your original behavior.
            LocalDateTime now = LocalDateTime.now(clock);
            this.createdAt = (this.createdAt != null) ? this.createdAt : now;
            this.expiresAt = (this.expiresAt != null) ? this.expiresAt : this.createdAt.plus(ttl);

            return new Story(this);
        }

        private static void requireNonBlank(String v, String name) {
            if (v == null || v.trim().isEmpty()) {
                throw new IllegalArgumentException(name + " cannot be null/blank");
            }
        }
    }
}

// -------------------- DAO --------------------

interface StoryDAO {
    void save(Story story);
    List<Story> getActiveStories(String userId);
}

// Minimal placeholder impl (kept simple for LLD interview)
final class InMemoryStoryDAO implements StoryDAO {
    private final Map<String, List<Story>> store = new ConcurrentHashMap<>();

    @Override
    public void save(Story story) {
        store.computeIfAbsent(story.getUserId(), k -> new CopyOnWriteArrayList<>()).add(story);
    }

    @Override
    public List<Story> getActiveStories(String userId) {
        return store.getOrDefault(userId, List.of());
    }
}

// -------------------- Cache --------------------

interface CacheService {
    void addToUserStories(String userId, Story story);
    List<Story> getUserStories(String userId);
}

final class RedisCache implements CacheService {
    private static final RedisCache INSTANCE = new RedisCache(Clock.systemDefaultZone());

    private final Clock clock;
    private final Map<String, List<Story>> userStoryMap = new ConcurrentHashMap<>();

    private RedisCache(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static RedisCache getInstance() {
        return INSTANCE;
    }

    @Override
    public void addToUserStories(String userId, Story story) {
        // thread-safe list for concurrent access
        userStoryMap.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(story);
    }

    @Override
    public List<Story> getUserStories(String userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return userStoryMap.getOrDefault(userId, List.of())
                .stream()
                .filter(s -> s.getExpiresAt().isAfter(now))
                .collect(Collectors.toList());
    }
}

// -------------------- Services --------------------

final class StoryService {
    private static final StoryService INSTANCE =
            new StoryService(new InMemoryStoryDAO(), RedisCache.getInstance());

    private final StoryDAO storyDAO;
    private final CacheService cache;

    private StoryService(StoryDAO storyDAO, CacheService cache) {
        this.storyDAO = Objects.requireNonNull(storyDAO, "storyDAO");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    public static StoryService getInstance() {
        return INSTANCE;
    }

    public void uploadStory(Story story) {
        Objects.requireNonNull(story, "story");
        storyDAO.save(story);
        cache.addToUserStories(story.getUserId(), story);
    }

    // Note: userId parameter is currently unused in your original code.
    // Keeping signature same to avoid "major" changes.
    public List<Story> getStoriesForUser(String userId, List<String> following) {
        if (following == null || following.isEmpty()) return List.of();

        List<Story> stories = new ArrayList<>();
        for (String uid : following) {
            if (uid == null || uid.isBlank()) continue;
            stories.addAll(cache.getUserStories(uid));
        }
        return stories;
    }
}

interface ViewLogger {
    void logView(String storyId, String viewerId);
}

final class ConsoleViewLogger implements ViewLogger {
    @Override
    public void logView(String storyId, String viewerId) {
        System.out.println("Logged view for story " + storyId + " by " + viewerId);
    }
}

final class ViewTrackerService {
    private static final ViewTrackerService INSTANCE =
            new ViewTrackerService(Executors.newFixedThreadPool(5), new ConsoleViewLogger());

    private final ExecutorService executor;
    private final ViewLogger viewLogger;

    private ViewTrackerService(ExecutorService executor, ViewLogger viewLogger) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.viewLogger = Objects.requireNonNull(viewLogger, "viewLogger");
    }

    public static ViewTrackerService getInstance() {
        return INSTANCE;
    }

    public void trackView(String storyId, String viewerId) {
        if (storyId == null || storyId.isBlank() || viewerId == null || viewerId.isBlank()) return;

        executor.submit(() -> viewLogger.logView(storyId, viewerId));
    }

    // Optional lifecycle method (nice to have)
    public void shutdown() {
        executor.shutdown();
    }
}
