import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * LLD: Design GitHub (subset) - Repo + Branch + Commit + Pull Request + Review + Merge.
 *
 * Focus:
 * - Clean object model
 * - PR lifecycle rules (draft cannot be merged)
 * - Merge strategies (Strategy pattern)
 * - Notifications (Observer/EventBus)
 * - In-memory repos for simplicity
 */
public class GitHubLLD {

    // --------------------------- DEMO ---------------------------

    public static void main(String[] args) {
        // Infrastructure
        IdGenerator ids = new UuidGenerator();
        DomainEventBus eventBus = new SimpleEventBus();
        NotificationService notifications = new NotificationService(eventBus);

        // Persistence
        UserRepository userRepo = new InMemoryUserRepository();
        RepoRepository repoRepo = new InMemoryRepoRepository();
        PullRequestRepository prRepo = new InMemoryPullRequestRepository();

        // Services
        RepoService repoService = new RepoService(ids, userRepo, repoRepo, eventBus);
        PullRequestService prService = new PullRequestService(ids, userRepo, repoRepo, prRepo, eventBus);

        // Users
        User alice = repoService.registerUser("alice");
        User bob = repoService.registerUser("bob");

        // Repo creation
        Repository store = repoService.createRepository(alice.id(), "store-service", RepoVisibility.PRIVATE);

        // Watchers (notifications)
        repoService.watchRepo(alice.id(), store.id());
        repoService.watchRepo(bob.id(), store.id());

        // Branches + commits
        repoService.createBranch(alice.id(), store.id(), "main", "feature/login");
        repoService.commit(alice.id(), store.id(), "feature/login", "Add login endpoint");
        repoService.commit(alice.id(), store.id(), "feature/login", "Add unit tests");

        // PR
        PullRequest pr = prService.openPullRequest(
                alice.id(),
                store.id(),
                "feature/login",
                "main",
                "Login feature",
                "Implements login with tests",
                false // isDraft
        );

        // Reviews
        prService.addReviewer(alice.id(), pr.id(), bob.id());
        prService.submitReview(bob.id(), pr.id(), ReviewDecision.APPROVE, "Looks good!");

        // Merge (Strategy)
        prService.mergePullRequest(alice.id(), pr.id(), MergeMethod.SQUASH);

        // Print notifications (for demo)
        System.out.println("\n--- Notifications ---");
        notifications.dumpAll().forEach(System.out::println);
    }

    // --------------------------- DOMAIN ---------------------------

    enum RepoVisibility { PUBLIC, PRIVATE }

    record User(String id, String handle) {}

    record Commit(String id, String message, String authorId, Instant createdAt) {}

    static final class Branch {
        private final String name;
        private final List<Commit> commits = new ArrayList<>();

        Branch(String name) { this.name = Objects.requireNonNull(name); }

        String name() { return name; }
        List<Commit> commits() { return Collections.unmodifiableList(commits); }

        void addCommit(Commit c) { commits.add(Objects.requireNonNull(c)); }

        Commit head() {
            if (commits.isEmpty()) return null;
            return commits.get(commits.size() - 1);
        }
    }

    static final class Repository {
        private final String id;
        private final String name;
        private final String ownerId;
        private final RepoVisibility visibility;

        private final Map<String, Branch> branches = new HashMap<>();
        private final Set<String> watchers = new HashSet<>();

        // Simple repo settings
        private int requiredApprovals = 1;

        Repository(String id, String ownerId, String name, RepoVisibility visibility) {
            this.id = Objects.requireNonNull(id);
            this.ownerId = Objects.requireNonNull(ownerId);
            this.name = Objects.requireNonNull(name);
            this.visibility = Objects.requireNonNull(visibility);

            // default branch
            branches.put("main", new Branch("main"));
        }

        String id() { return id; }
        String name() { return name; }
        String ownerId() { return ownerId; }
        RepoVisibility visibility() { return visibility; }

        int requiredApprovals() { return requiredApprovals; }
        void setRequiredApprovals(int requiredApprovals) {
            if (requiredApprovals < 0) throw new IllegalArgumentException("requiredApprovals >= 0");
            this.requiredApprovals = requiredApprovals;
        }

        void addWatcher(String userId) { watchers.add(userId); }
        Set<String> watchers() { return Collections.unmodifiableSet(watchers); }

        Branch getBranch(String branchName) { return branches.get(branchName); }

        void createBranch(String fromBranch, String newBranch) {
            if (branches.containsKey(newBranch)) throw new IllegalStateException("Branch exists: " + newBranch);
            Branch base = branches.get(fromBranch);
            if (base == null) throw new IllegalArgumentException("Unknown base branch: " + fromBranch);

            Branch b = new Branch(newBranch);
            // shallow copy commit history (metadata only)
            base.commits().forEach(b::addCommit);
            branches.put(newBranch, b);
        }

        Commit commit(String commitId, String branchName, String authorId, String message) {
            Branch b = branches.get(branchName);
            if (b == null) throw new IllegalArgumentException("Unknown branch: " + branchName);

            Commit c = new Commit(commitId, message, authorId, Instant.now());
            b.addCommit(c);
            return c;
        }

        void merge(String headBranch, String baseBranch, MergeStrategy strategy) {
            Branch head = branches.get(headBranch);
            Branch base = branches.get(baseBranch);
            if (head == null || base == null) throw new IllegalArgumentException("Invalid branch");
            strategy.merge(head, base);
        }
    }

    enum PullRequestState {
        DRAFT, OPEN, MERGED, CLOSED;

        boolean canMerge() { return this == OPEN; } // draft cannot merge
        boolean canClose() { return this == OPEN || this == DRAFT; }
    }

    static final class PullRequest {
        private final String id;
        private final String repoId;
        private final String authorId;
        private final String title;
        private final String description;
        private final String headBranch;
        private final String baseBranch;

        private PullRequestState state;
        private final Set<String> reviewers = new HashSet<>();
        private final List<Review> reviews = new ArrayList<>();
        private final List<String> history = new ArrayList<>();

        PullRequest(String id, String repoId, String authorId, String headBranch, String baseBranch,
                   String title, String description, boolean isDraft) {
            this.id = Objects.requireNonNull(id);
            this.repoId = Objects.requireNonNull(repoId);
            this.authorId = Objects.requireNonNull(authorId);
            this.headBranch = Objects.requireNonNull(headBranch);
            this.baseBranch = Objects.requireNonNull(baseBranch);
            this.title = Objects.requireNonNull(title);
            this.description = Objects.requireNonNull(description);
            this.state = isDraft ? PullRequestState.DRAFT : PullRequestState.OPEN;

            addHistory("PR created (state=" + state + ")");
        }

        String id() { return id; }
        String repoId() { return repoId; }
        String authorId() { return authorId; }
        String headBranch() { return headBranch; }
        String baseBranch() { return baseBranch; }
        String title() { return title; }
        String description() { return description; }
        PullRequestState state() { return state; }

        void addReviewer(String userId) {
            reviewers.add(userId);
            addHistory("Reviewer added: " + userId);
        }

        Set<String> reviewers() { return Collections.unmodifiableSet(reviewers); }
        List<Review> reviews() { return Collections.unmodifiableList(reviews); }
        List<String> history() { return Collections.unmodifiableList(history); }

        void submitReview(Review review) {
            if (!reviewers.contains(review.reviewerId())) {
                throw new IllegalStateException("Reviewer not assigned to PR");
            }
            if (state == PullRequestState.MERGED || state == PullRequestState.CLOSED) {
                throw new IllegalStateException("Cannot review closed/merged PR");
            }
            reviews.add(review);
            addHistory("Review submitted by " + review.reviewerId() + ": " + review.decision());
        }

        int approvalCount() {
            int count = 0;
            for (Review r : reviews) {
                if (r.decision() == ReviewDecision.APPROVE) count++;
            }
            return count;
        }

        void markReadyForReview() {
            if (state != PullRequestState.DRAFT) return;
            state = PullRequestState.OPEN;
            addHistory("Moved from DRAFT -> OPEN");
        }

        void merge() {
            if (!state.canMerge()) throw new IllegalStateException("PR not mergeable: " + state);
            state = PullRequestState.MERGED;
            addHistory("PR merged");
        }

        void close() {
            if (!state.canClose()) throw new IllegalStateException("PR not closable: " + state);
            state = PullRequestState.CLOSED;
            addHistory("PR closed");
        }

        private void addHistory(String entry) {
            history.add(Instant.now() + " :: " + entry);
        }
    }

    enum ReviewDecision { APPROVE, REQUEST_CHANGES, COMMENT }

    record Review(String id, String prId, String reviewerId, ReviewDecision decision, String comment, Instant createdAt) {
        static Review of(String id, String prId, String reviewerId, ReviewDecision decision, String comment) {
            return new Review(id, prId, reviewerId, decision, comment, Instant.now());
        }
    }

    // --------------------------- MERGE STRATEGY (Strategy Pattern) ---------------------------

    enum MergeMethod { MERGE_COMMIT, SQUASH, REBASE }

    interface MergeStrategy {
        void merge(Branch head, Branch base);
    }

    static final class MergeCommitStrategy implements MergeStrategy {
        @Override public void merge(Branch head, Branch base) {
            // Simplified: append all commits from head not already in base by id
            Set<String> baseIds = new HashSet<>();
            for (Commit c : base.commits()) baseIds.add(c.id());
            for (Commit c : head.commits()) {
                if (!baseIds.contains(c.id())) base.addCommit(c);
            }
        }
    }

    static final class SquashMergeStrategy implements MergeStrategy {
        @Override public void merge(Branch head, Branch base) {
            Commit last = head.head();
            if (last == null) return;
            // Simplified: one squashed commit representing head state
            Commit squashed = new Commit(
                    UUID.randomUUID().toString(),
                    "squash: " + last.message(),
                    last.authorId(),
                    Instant.now()
            );
            base.addCommit(squashed);
        }
    }

    static final class RebaseMergeStrategy implements MergeStrategy {
        @Override public void merge(Branch head, Branch base) {
            // Simplified: copy head commits as "rebased" commits onto base
            for (Commit c : head.commits()) {
                Commit rebased = new Commit(
                        UUID.randomUUID().toString(),
                        "rebase: " + c.message(),
                        c.authorId(),
                        Instant.now()
                );
                base.addCommit(rebased);
            }
        }
    }

    static final class MergeStrategyFactory {
        static MergeStrategy forMethod(MergeMethod method) {
            return switch (method) {
                case MERGE_COMMIT -> new MergeCommitStrategy();
                case SQUASH -> new SquashMergeStrategy();
                case REBASE -> new RebaseMergeStrategy();
            };
        }
    }

    // --------------------------- EVENTS + NOTIFICATIONS (Observer Pattern) ---------------------------

    interface DomainEvent {}

    record PullRequestOpened(String prId, String repoId, String authorId) implements DomainEvent {}
    record ReviewSubmitted(String prId, String reviewerId, ReviewDecision decision) implements DomainEvent {}
    record PullRequestMerged(String prId, String repoId) implements DomainEvent {}

    interface DomainEventListener<T extends DomainEvent> {
        void onEvent(T event);
        Class<T> eventType();
    }

    interface DomainEventBus {
        void publish(DomainEvent event);
        void subscribe(DomainEventListener<? extends DomainEvent> listener);
    }

    static final class SimpleEventBus implements DomainEventBus {
        private final List<DomainEventListener<? extends DomainEvent>> listeners = new CopyOnWriteArrayList<>();

        @Override public void publish(DomainEvent event) {
            for (DomainEventListener<? extends DomainEvent> l : listeners) {
                if (l.eventType().isAssignableFrom(event.getClass())) {
                    @SuppressWarnings("unchecked")
                    DomainEventListener<DomainEvent> casted = (DomainEventListener<DomainEvent>) l;
                    casted.onEvent(event);
                }
            }
        }

        @Override public void subscribe(DomainEventListener<? extends DomainEvent> listener) {
            listeners.add(listener);
        }
    }

    static final class NotificationService {
        private final List<String> outbox = new CopyOnWriteArrayList<>();

        NotificationService(DomainEventBus bus) {
            bus.subscribe(new DomainEventListener<PullRequestOpened>() {
                @Override public void onEvent(PullRequestOpened e) {
                    outbox.add("PR opened: prId=" + e.prId() + " repoId=" + e.repoId());
                }
                @Override public Class<PullRequestOpened> eventType() { return PullRequestOpened.class; }
            });
            bus.subscribe(new DomainEventListener<ReviewSubmitted>() {
                @Override public void onEvent(ReviewSubmitted e) {
                    outbox.add("Review: prId=" + e.prId() + " by=" + e.reviewerId() + " decision=" + e.decision());
                }
                @Override public Class<ReviewSubmitted> eventType() { return ReviewSubmitted.class; }
            });
            bus.subscribe(new DomainEventListener<PullRequestMerged>() {
                @Override public void onEvent(PullRequestMerged e) {
                    outbox.add("PR merged: prId=" + e.prId() + " repoId=" + e.repoId());
                }
                @Override public Class<PullRequestMerged> eventType() { return PullRequestMerged.class; }
            });
        }

        List<String> dumpAll() { return new ArrayList<>(outbox); }
    }

    // --------------------------- PERSISTENCE (Repository pattern) ---------------------------

    interface UserRepository {
        void save(User user);
        User get(String userId);
        Optional<User> findByHandle(String handle);
    }

    static final class InMemoryUserRepository implements UserRepository {
        private final Map<String, User> byId = new ConcurrentHashMap<>();
        private final Map<String, String> idByHandle = new ConcurrentHashMap<>();

        @Override public void save(User user) {
            byId.put(user.id(), user);
            idByHandle.put(user.handle(), user.id());
        }

        @Override public User get(String userId) {
            User u = byId.get(userId);
            if (u == null) throw new IllegalArgumentException("User not found: " + userId);
            return u;
        }

        @Override public Optional<User> findByHandle(String handle) {
            String id = idByHandle.get(handle);
            return id == null ? Optional.empty() : Optional.of(byId.get(id));
        }
    }

    interface RepoRepository {
        void save(Repository repo);
        Repository get(String repoId);
    }

    static final class InMemoryRepoRepository implements RepoRepository {
        private final Map<String, Repository> byId = new ConcurrentHashMap<>();

        @Override public void save(Repository repo) { byId.put(repo.id(), repo); }

        @Override public Repository get(String repoId) {
            Repository r = byId.get(repoId);
            if (r == null) throw new IllegalArgumentException("Repo not found: " + repoId);
            return r;
        }
    }

    interface PullRequestRepository {
        void save(PullRequest pr);
        PullRequest get(String prId);
    }

    static final class InMemoryPullRequestRepository implements PullRequestRepository {
        private final Map<String, PullRequest> byId = new ConcurrentHashMap<>();

        @Override public void save(PullRequest pr) { byId.put(pr.id(), pr); }

        @Override public PullRequest get(String prId) {
            PullRequest pr = byId.get(prId);
            if (pr == null) throw new IllegalArgumentException("PR not found: " + prId);
            return pr;
        }
    }

    // --------------------------- SERVICES (SOLID: depend on interfaces) ---------------------------

    static final class RepoService {
        private final IdGenerator ids;
        private final UserRepository users;
        private final RepoRepository repos;
        private final DomainEventBus eventBus;

        RepoService(IdGenerator ids, UserRepository users, RepoRepository repos, DomainEventBus eventBus) {
            this.ids = ids;
            this.users = users;
            this.repos = repos;
            this.eventBus = eventBus;
        }

        User registerUser(String handle) {
            users.findByHandle(handle).ifPresent(u -> { throw new IllegalStateException("Handle taken"); });
            User u = new User(ids.newId(), handle);
            users.save(u);
            return u;
        }

        Repository createRepository(String ownerId, String name, RepoVisibility visibility) {
            users.get(ownerId);
            Repository repo = new Repository(ids.newId(), ownerId, name, visibility);
            repos.save(repo);
            return repo;
        }

        void watchRepo(String userId, String repoId) {
            users.get(userId);
            Repository repo = repos.get(repoId);
            repo.addWatcher(userId);
        }

        void createBranch(String userId, String repoId, String fromBranch, String newBranch) {
            users.get(userId);
            Repository repo = repos.get(repoId);
            repo.createBranch(fromBranch, newBranch);
        }

        Commit commit(String userId, String repoId, String branch, String message) {
            users.get(userId);
            Repository repo = repos.get(repoId);
            Commit c = repo.commit(ids.newId(), branch, userId, message);
            repos.save(repo);
            return c;
        }
    }

    static final class PullRequestService {
        private final IdGenerator ids;
        private final UserRepository users;
        private final RepoRepository repos;
        private final PullRequestRepository prs;
        private final DomainEventBus eventBus;

        PullRequestService(IdGenerator ids, UserRepository users, RepoRepository repos, PullRequestRepository prs, DomainEventBus eventBus) {
            this.ids = ids;
            this.users = users;
            this.repos = repos;
            this.prs = prs;
            this.eventBus = eventBus;
        }

        PullRequest openPullRequest(String authorId, String repoId,
                                    String headBranch, String baseBranch,
                                    String title, String description,
                                    boolean isDraft) {

            users.get(authorId);
            Repository repo = repos.get(repoId);

            // Validate branches exist
            if (repo.getBranch(headBranch) == null) throw new IllegalArgumentException("Unknown head branch: " + headBranch);
            if (repo.getBranch(baseBranch) == null) throw new IllegalArgumentException("Unknown base branch: " + baseBranch);

            PullRequest pr = new PullRequest(ids.newId(), repoId, authorId, headBranch, baseBranch, title, description, isDraft);
            prs.save(pr);

            eventBus.publish(new PullRequestOpened(pr.id(), repoId, authorId));
            return pr;
        }

        void addReviewer(String actorId, String prId, String reviewerId) {
            users.get(actorId);
            users.get(reviewerId);

            PullRequest pr = prs.get(prId);
            pr.addReviewer(reviewerId);
            prs.save(pr);
        }

        void submitReview(String reviewerId, String prId, ReviewDecision decision, String comment) {
            users.get(reviewerId);

            PullRequest pr = prs.get(prId);
            Review review = Review.of(ids.newId(), prId, reviewerId, decision, comment);
            pr.submitReview(review);
            prs.save(pr);

            eventBus.publish(new ReviewSubmitted(prId, reviewerId, decision));
        }

        void markReadyForReview(String actorId, String prId) {
            users.get(actorId);
            PullRequest pr = prs.get(prId);
            pr.markReadyForReview();
            prs.save(pr);
        }

        void mergePullRequest(String actorId, String prId, MergeMethod mergeMethod) {
            users.get(actorId);

            PullRequest pr = prs.get(prId);
            Repository repo = repos.get(pr.repoId());

            // Business rule: draft cannot merge; only OPEN merges
            if (!pr.state().canMerge()) {
                throw new IllegalStateException("PR not mergeable. Current state=" + pr.state());
            }

            // Business rule: approvals
            if (pr.approvalCount() < repo.requiredApprovals()) {
                throw new IllegalStateException("Not enough approvals: " + pr.approvalCount() +
                        " required=" + repo.requiredApprovals());
            }

            // Merge branches using strategy
            MergeStrategy strategy = MergeStrategyFactory.forMethod(mergeMethod);
            repo.merge(pr.headBranch(), pr.baseBranch(), strategy);

            // Update PR state
            pr.merge();

            // Persist
            repos.save(repo);
            prs.save(pr);

            eventBus.publish(new PullRequestMerged(pr.id(), repo.id()));
        }
    }

    // --------------------------- UTILS ---------------------------

    interface IdGenerator { String newId(); }

    static final class UuidGenerator implements IdGenerator {
        @Override public String newId() { return UUID.randomUUID().toString(); }
    }
}
