import java.util.*;

// Single-file LLD (interview-friendly). Focus: flow, SOLID-ish separation, readability, correctness.
public class StackOverflowLLD {

    // --- ENUMS ---
    public enum AccountStatus { ACTIVE, CLOSED, BLOCKED }
    public enum BadgeType { BRONZE, SILVER, GOLD, PLATINUM }
    public enum VoteType { UPVOTE, DOWNVOTE }
    public enum PostType { QUESTION, ANSWER }
    public enum EntityType { QUESTION, ANSWER, COMMENT }

    // --- DOMAIN INTERFACES (kept minimal) ---
    public interface Subject {
        void addObserver(Observer o);
        void removeObserver(Observer o);
        void notifyObservers(String msg, User u);
    }

    public interface Observer {
        void update(String message, User u);
    }

    // --- COMMAND PATTERN ---
    public interface Command {
        void execute();
        void undo();
    }

    // --- STRATEGY: REPUTATION ---
    public interface ReputationStrategy {
        int calculateReputationChange(VoteType v, PostType p);
    }

    public static class StandardReputationStrategy implements ReputationStrategy {
        @Override
        public int calculateReputationChange(VoteType v, PostType p) {
            if (v == VoteType.UPVOTE) return (p == PostType.QUESTION ? 5 : 10);
            if (v == VoteType.DOWNVOTE) return -2;
            return 0;
        }
    }

    public static class AcceptedAnswerReputationStrategy implements ReputationStrategy {
        @Override
        public int calculateReputationChange(VoteType voteType, PostType postType) {
            return 15;
        }
    }

    // --- OBSERVER: NOTIFICATIONS ---
    public static class NotificationObserver implements Observer {
        @Override
        public void update(String msg, User u) {
            System.out.println("Notification to " + u.getUsername() + ": " + msg);
        }
    }

    // --- ABSTRACT ENTITY (votable, flaggable, commentable) ---
    public static abstract class Entity {
        private Long id;
        private User createdBy;
        private final Date createdAt = new Date();

        // Vote counts maintained via repository counts (UP/DOWN).
        private final EnumMap<VoteType, Integer> voteCounts = new EnumMap<>(VoteType.class);

        private final List<User> flaggedBy = new ArrayList<>();
        private final List<Comment> comments = new ArrayList<>();

        protected Entity() {
            voteCounts.put(VoteType.UPVOTE, 0);
            voteCounts.put(VoteType.DOWNVOTE, 0);
        }

        public void addComment(Comment c) {
            if (c != null) comments.add(c);
        }

        public void flag(User u) {
            if (u == null) return;
            if (!flaggedBy.contains(u)) flaggedBy.add(u);
        }

        public void setVoteCounts(int up, int down) {
            voteCounts.put(VoteType.UPVOTE, Math.max(0, up));
            voteCounts.put(VoteType.DOWNVOTE, Math.max(0, down));
        }

        public int getUpvotes() { return voteCounts.getOrDefault(VoteType.UPVOTE, 0); }
        public int getDownvotes() { return voteCounts.getOrDefault(VoteType.DOWNVOTE, 0); }

        // Getters/setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public User getCreatedBy() { return createdBy; }
        public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

        public Date getCreatedAt() { return createdAt; }

        public List<Comment> getComments() { return Collections.unmodifiableList(comments); }
        public List<User> getFlaggedBy() { return Collections.unmodifiableList(flaggedBy); }
    }

    // --- USER HIERARCHY ---
    public static class User {
        private Long id;
        private final String username;
        private final String email;
        private int reputation = 0;
        private final Date createdAt = new Date();
        private AccountStatus status = AccountStatus.ACTIVE;

        public User(String uname, String mail) {
            this.username = Objects.requireNonNull(uname, "username");
            this.email = Objects.requireNonNull(mail, "email");
        }

        // Getters/setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getUsername() { return username; }
        public String getEmail() { return email; }

        public int getReputation() { return reputation; }
        public void setReputation(int reputation) { this.reputation = reputation; }

        public Date getCreatedAt() { return createdAt; }

        public AccountStatus getStatus() { return status; }
        public void setStatus(AccountStatus status) { this.status = status; }
    }

    public static class Member extends User {
        private final List<Badge> badges = new ArrayList<>();
        public Member(String uname, String mail) { super(uname, mail); }
        public List<Badge> getBadges() { return Collections.unmodifiableList(badges); }
        public void awardBadge(Badge b) { if (b != null) badges.add(b); }
    }

    public static class Moderator extends Member {
        public Moderator(String uname, String mail) { super(uname, mail); }
    }

    // --- BADGE, TAG ---
    public static class Badge {
        private final BadgeType type;
        private final String description;

        public Badge(BadgeType t, String d) {
            this.type = Objects.requireNonNull(t);
            this.description = d == null ? "" : d;
        }

        public BadgeType getType() { return type; }
        public String getDescription() { return description; }
    }

    public static class Tag {
        private Long id;
        private final String name;
        private final String description;
        private int questionCount = 0;

        public Tag(String n, String d) {
            this.name = Objects.requireNonNull(n, "tagName");
            this.description = (d == null ? "" : d);
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public String getDescription() { return description; }

        public int getQuestionCount() { return questionCount; }
        public void incrementQuestionCount() { questionCount++; }
    }

    // --- POSTS, ANSWERS, COMMENTS ---
    public static abstract class Post extends Entity {
        private String content;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public abstract PostType getPostType();
    }

    public static class Question extends Post {
        private String title;
        private final List<Tag> tags = new ArrayList<>();
        private final List<Answer> answers = new ArrayList<>();

        private Answer acceptedAnswer;
        private boolean answered = false;

        public Question(String t, String c, User author, List<Tag> tags) {
            this.title = Objects.requireNonNull(t, "title");
            setContent(Objects.requireNonNull(c, "content"));
            setCreatedBy(Objects.requireNonNull(author, "author"));
            if (tags != null) this.tags.addAll(tags);
        }

        public void addAnswer(Answer a) {
            if (a != null) answers.add(a);
        }

        // Ensures integrity is handled in one place.
        public void acceptAnswer(Answer a) {
            if (a == null) return;
            if (acceptedAnswer != null) acceptedAnswer.setAccepted(false);
            a.setAccepted(true);
            acceptedAnswer = a;
            answered = true;
        }

        @Override public PostType getPostType() { return PostType.QUESTION; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public List<Tag> getTags() { return Collections.unmodifiableList(tags); }
        public List<Answer> getAnswers() { return Collections.unmodifiableList(answers); }

        public Answer getAcceptedAnswer() { return acceptedAnswer; }
        public boolean isAnswered() { return answered; }
    }

    public static class Answer extends Post {
        private final Question question;
        private boolean accepted = false;

        public Answer(String c, User author, Question q) {
            setContent(Objects.requireNonNull(c, "content"));
            setCreatedBy(Objects.requireNonNull(author, "author"));
            this.question = Objects.requireNonNull(q, "question");
        }

        @Override public PostType getPostType() { return PostType.ANSWER; }

        public Question getQuestion() { return question; }

        public boolean isAccepted() { return accepted; }
        public void setAccepted(boolean accepted) { this.accepted = accepted; }
    }

    public static class Comment extends Entity {
        private final String message;
        private final Post post;

        public Comment(String m, User u, Post p) {
            this.message = Objects.requireNonNull(m, "message");
            this.post = Objects.requireNonNull(p, "post");
            setCreatedBy(Objects.requireNonNull(u, "author"));
        }

        public String getMessage() { return message; }
        public Post getPost() { return post; }
    }

    // --- VOTES ---
    public static class Vote {
        private Long id;
        private final User voter;
        private final Entity entity;
        private VoteType type;
        private final Date createdAt = new Date();

        public Vote(User voter, Entity target, VoteType type) {
            this.voter = Objects.requireNonNull(voter);
            this.entity = Objects.requireNonNull(target);
            this.type = Objects.requireNonNull(type);
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public User getVoter() { return voter; }
        public Entity getEntity() { return entity; }

        public VoteType getType() { return type; }
        public void setType(VoteType type) { this.type = type; }

        public Date getCreatedAt() { return createdAt; }
    }

    // --- FACTORY ---
    public interface EntityFactory {
        Entity createEntity(EntityType type, Object... params);
    }

    public static class EntityFactoryImpl implements EntityFactory {
        @Override
        @SuppressWarnings("unchecked")
        public Entity createEntity(EntityType type, Object... params) {
            switch (type) {
                case QUESTION:
                    return new Question((String) params[0], (String) params[1], (User) params[2], (List<Tag>) params[3]);
                case ANSWER:
                    return new Answer((String) params[0], (User) params[1], (Question) params[2]);
                case COMMENT:
                    return new Comment((String) params[0], (User) params[1], (Post) params[2]);
                default:
                    throw new IllegalArgumentException("Unknown entity type: " + type);
            }
        }
    }

    // --- COMMAND: VOTE ---
    public static class VoteCommand implements Command {
        private final VoteService voteService;
        private final User user;
        private final Entity entity;
        private final VoteType voteType;
        private Vote previousVote;

        public VoteCommand(VoteService vs, User u, Entity e, VoteType t) {
            this.voteService = Objects.requireNonNull(vs);
            this.user = Objects.requireNonNull(u);
            this.entity = Objects.requireNonNull(e);
            this.voteType = Objects.requireNonNull(t);
        }

        @Override public void execute() { previousVote = voteService.vote(user, entity, voteType); }

        @Override public void undo() {
            if (previousVote != null) voteService.removeVote(user, entity);
        }
    }

    // --- REPOSITORIES (interfaces) ---
    public interface UserRepository {
        User save(User u);
        User findById(Long id);
        User findByUsername(String u);
        User findByEmail(String e);
        void updateReputation(Long id, int change);
    }

    public interface QuestionRepository {
        Question save(Question q);
        Question findById(Long id);

        List<Question> findByTagsIn(List<Tag> tags);
        List<Question> searchByKeyword(String k);

        List<Question> findAll(int page, int size);
        List<Question> findByAuthor(User author);

        // For CombinedSearchStrategy (optional)
        default List<Question> advancedSearch(String keyword, List<String> tagNames, String authorUsername,
                                            Date createdAfter, Date createdBefore, boolean onlyUnanswered) {
            // Stub default: implementation can live in concrete repo.
            return Collections.emptyList();
        }
    }

    public interface AnswerRepository {
        Answer save(Answer a);
        Answer findById(Long id);

        List<Answer> findByQuestion(Question q);
        List<Answer> findByAuthor(User a);
    }

    public interface CommentRepository {
        Comment save(Comment c);
        List<Comment> findByPost(Post p);
        Comment findById(Long id);
    }

    public interface TagRepository {
        Tag save(Tag t);
        Tag findByName(String n);
        List<Tag> findAll();
        void incrementQuestionCount(Long id);
    }

    public interface VoteRepository {
        Vote save(Vote v);
        Vote findByUserAndEntity(User u, Entity e);
        void delete(Vote v);

        int countUpvotesByEntity(Entity e);
        int countDownvotesByEntity(Entity e);
    }

    // --- SERVICE LAYER ---
    public static class UserService {
        private final UserRepository repo;
        private ReputationStrategy repStrategy = new StandardReputationStrategy();

        public UserService(UserRepository repo) { this.repo = Objects.requireNonNull(repo); }

        public User createUser(String uname, String email) {
            User u = new User(uname, email);
            return repo.save(u);
        }

        public User getUserById(Long id) { return repo.findById(id); }

        public void updateReputation(User u, int delta) {
            if (u == null || u.getId() == null) return;
            repo.updateReputation(u.getId(), delta);
        }

        public void setReputationStrategy(ReputationStrategy s) {
            this.repStrategy = Objects.requireNonNull(s);
        }

        public int calcReputationChange(VoteType t, PostType pt) {
            return repStrategy.calculateReputationChange(t, pt);
        }
    }

    public static class QuestionService implements Subject {
        private final QuestionRepository qRepo;
        private final TagRepository tRepo;
        private final EntityFactory factory;

        private final List<Observer> observers = new ArrayList<>();

        public QuestionService(QuestionRepository q, TagRepository t, EntityFactory f) {
            this.qRepo = Objects.requireNonNull(q);
            this.tRepo = Objects.requireNonNull(t);
            this.factory = Objects.requireNonNull(f);
        }

        public Question createQuestion(String title, String content, User author, List<String> tagNames) {
            List<Tag> tags = new ArrayList<>();
            if (tagNames != null) {
                for (String tagName : tagNames) {
                    Tag tag = tRepo.findByName(tagName);
                    if (tag == null) tag = tRepo.save(new Tag(tagName, ""));
                    tags.add(tag);
                    if (tag.getId() != null) tRepo.incrementQuestionCount(tag.getId());
                }
            }

            Question q = (Question) factory.createEntity(EntityType.QUESTION, title, content, author, tags);
            q = qRepo.save(q);
            notifyObservers("New question posted: " + title, author);
            return q;
        }

        public Question getQuestionById(Long id) { return qRepo.findById(id); }

        public void acceptAnswer(Question q, Answer a, User actor) {
            if (q == null || a == null || actor == null) throw new IllegalArgumentException("Invalid inputs");
            if (!Objects.equals(q.getCreatedBy(), actor)) throw new RuntimeException("Unauthorized");
            if (!Objects.equals(a.getQuestion(), q)) throw new RuntimeException("Answer not for this question");

            q.acceptAnswer(a);
            qRepo.save(q);

            notifyObservers("Your answer was accepted!", a.getCreatedBy());
        }

        @Override public void addObserver(Observer o) { if (o != null) observers.add(o); }

        @Override public void removeObserver(Observer o) { observers.remove(o); }

        @Override public void notifyObservers(String msg, User u) {
            for (Observer ob : observers) ob.update(msg, u);
        }
    }

    public static class AnswerService implements Subject {
        private final AnswerRepository repo;
        private final EntityFactory factory;
        private final List<Observer> observers = new ArrayList<>();

        public AnswerService(AnswerRepository repo, EntityFactory f) {
            this.repo = Objects.requireNonNull(repo);
            this.factory = Objects.requireNonNull(f);
        }

        public Answer createAnswer(String content, User author, Question question) {
            Answer a = (Answer) factory.createEntity(EntityType.ANSWER, content, author, question);
            a = repo.save(a);
            question.addAnswer(a);

            notifyObservers("New answer on: " + question.getTitle(), question.getCreatedBy());
            return a;
        }

        public Answer getAnswerById(Long id) { return repo.findById(id); }

        @Override public void addObserver(Observer o) { if (o != null) observers.add(o); }

        @Override public void removeObserver(Observer o) { observers.remove(o); }

        @Override public void notifyObservers(String msg, User u) {
            for (Observer ob : observers) ob.update(msg, u);
        }
    }

    public static class VoteService {
        private final VoteRepository repo;
        private final UserService uService;

        public VoteService(VoteRepository repo, UserService us) {
            this.repo = Objects.requireNonNull(repo);
            this.uService = Objects.requireNonNull(us);
        }

        public Vote vote(User user, Entity entity, VoteType type) {
            if (user == null || entity == null || type == null) throw new IllegalArgumentException("Invalid vote");
            if (Objects.equals(entity.getCreatedBy(), user)) throw new RuntimeException("Cannot vote own post");

            Vote existing = repo.findByUserAndEntity(user, entity);

            PostType postType = getPostTypeForEntity(entity);
            int newDelta = uService.calcReputationChange(type, postType);

            if (existing != null) {
                VoteType oldType = existing.getType();
                int oldDelta = uService.calcReputationChange(oldType, postType);

                if (oldType == type) {
                    // Same vote clicked again => remove vote (undo)
                    repo.delete(existing);
                    updateVoteCount(entity);
                    uService.updateReputation(entity.getCreatedBy(), -oldDelta);
                    return null;
                } else {
                    // Switch vote => apply (newDelta - oldDelta)
                    existing.setType(type);
                    Vote updated = repo.save(existing);
                    updateVoteCount(entity);

                    int netDelta = newDelta - oldDelta;
                    uService.updateReputation(entity.getCreatedBy(), netDelta);
                    return updated;
                }
            } else {
                // First time vote
                Vote v = new Vote(user, entity, type);
                v = repo.save(v);
                updateVoteCount(entity);
                uService.updateReputation(entity.getCreatedBy(), newDelta);
                return v;
            }
        }

        public void removeVote(User user, Entity entity) {
            Vote ex = repo.findByUserAndEntity(user, entity);
            if (ex != null) {
                PostType postType = getPostTypeForEntity(entity);
                int delta = uService.calcReputationChange(ex.getType(), postType);

                repo.delete(ex);
                updateVoteCount(entity);
                uService.updateReputation(entity.getCreatedBy(), -delta);
            }
        }

        private void updateVoteCount(Entity entity) {
            int up = repo.countUpvotesByEntity(entity);
            int down = repo.countDownvotesByEntity(entity);
            entity.setVoteCounts(up, down);
        }

        private PostType getPostTypeForEntity(Entity entity) {
            if (entity instanceof Question) return PostType.QUESTION;
            if (entity instanceof Answer) return PostType.ANSWER;
            return null; // comment vote rules can be added later
        }
    }

    public static class CommentService {
        private final CommentRepository repo;
        private final EntityFactory factory;

        public CommentService(CommentRepository repo, EntityFactory f) {
            this.repo = Objects.requireNonNull(repo);
            this.factory = Objects.requireNonNull(f);
        }

        public Comment createComment(String msg, User author, Post post) {
            Comment c = (Comment) factory.createEntity(EntityType.COMMENT, msg, author, post);
            c = repo.save(c);
            post.addComment(c);
            return c;
        }
    }

    // --- SEARCH STRATEGY (single, consistent interface) ---
    public static class SearchRequest {
        private String keyword;
        private List<String> tagNames;
        private String authorUsername;
        private Date createdAfter;
        private Date createdBefore;
        private boolean onlyUnanswered;

        public String getKeyword() { return keyword; }
        public List<String> getTagNames() { return tagNames; }
        public String getAuthorUsername() { return authorUsername; }
        public Date getCreatedAfter() { return createdAfter; }
        public Date getCreatedBefore() { return createdBefore; }
        public boolean isOnlyUnanswered() { return onlyUnanswered; }

        public SearchRequest keyword(String k) { this.keyword = k; return this; }
        public SearchRequest tagNames(List<String> t) { this.tagNames = t; return this; }
        public SearchRequest authorUsername(String a) { this.authorUsername = a; return this; }
        public SearchRequest createdAfter(Date d) { this.createdAfter = d; return this; }
        public SearchRequest createdBefore(Date d) { this.createdBefore = d; return this; }
        public SearchRequest onlyUnanswered(boolean b) { this.onlyUnanswered = b; return this; }
    }

    public static class SearchResponse {
        private List<Question> questions = new ArrayList<>();
        private int totalResults;
        private int page;
        private int pageSize;

        public List<Question> getQuestions() { return questions; }
        public void setQuestions(List<Question> questions) { this.questions = questions; }

        public int getTotalResults() { return totalResults; }
        public void setTotalResults(int totalResults) { this.totalResults = totalResults; }

        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }

        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    }

    public interface SearchStrategy {
        SearchResponse search(SearchRequest request);
    }

    public static class KeywordSearchStrategy implements SearchStrategy {
        private final QuestionRepository repo;
        public KeywordSearchStrategy(QuestionRepository r) { this.repo = Objects.requireNonNull(r); }

        @Override
        public SearchResponse search(SearchRequest request) {
            List<Question> results = repo.searchByKeyword(request.getKeyword());
            SearchResponse resp = new SearchResponse();
            resp.setQuestions(results);
            resp.setTotalResults(results.size());
            return resp;
        }
    }

    public static class TagSearchStrategy implements SearchStrategy {
        private final QuestionRepository qRepo;
        private final TagRepository tRepo;

        public TagSearchStrategy(QuestionRepository q, TagRepository t) {
            this.qRepo = Objects.requireNonNull(q);
            this.tRepo = Objects.requireNonNull(t);
        }

        @Override
        public SearchResponse search(SearchRequest request) {
            List<Tag> tags = new ArrayList<>();
            if (request.getTagNames() != null) {
                for (String s : request.getTagNames()) {
                    Tag t = tRepo.findByName(s.trim());
                    if (t != null) tags.add(t);
                }
            }

            List<Question> results = qRepo.findByTagsIn(tags);
            SearchResponse resp = new SearchResponse();
            resp.setQuestions(results);
            resp.setTotalResults(results.size());
            return resp;
        }
    }

    public static class CombinedSearchStrategy implements SearchStrategy {
        private final QuestionRepository questionRepo;

        public CombinedSearchStrategy(QuestionRepository qr) {
            this.questionRepo = Objects.requireNonNull(qr);
        }

        @Override
        public SearchResponse search(SearchRequest request) {
            List<Question> results = questionRepo.advancedSearch(
                    request.getKeyword(),
                    request.getTagNames(),
                    request.getAuthorUsername(),
                    request.getCreatedAfter(),
                    request.getCreatedBefore(),
                    request.isOnlyUnanswered()
            );
            SearchResponse resp = new SearchResponse();
            resp.setQuestions(results);
            resp.setTotalResults(results.size());
            return resp;
        }
    }

    // Keeps Strategy pattern but avoids controller "new Strategy(null)"
    public static class SearchService {
        private final Map<String, SearchStrategy> strategies = new HashMap<>();

        public void register(String type, SearchStrategy strategy) {
            strategies.put(type, Objects.requireNonNull(strategy));
        }

        public SearchResponse search(String type, SearchRequest request) {
            SearchStrategy s = strategies.get(type);
            if (s == null) throw new IllegalStateException("No strategy registered for: " + type);
            return s.search(request);
        }
    }

    // -----------------------------
    // CONTROLLER + DTOs (pseudo-Spring)
    // Note: Annotations kept as comments for interview readability.
    // -----------------------------

    // @RestController
    // @RequestMapping("/api/questions")
    public static class QuestionController {
        private final QuestionService questionService;
        private final AnswerService answerService;
        private final VoteService voteService;
        private final CommentService commentService;
        private final SearchService searchService;

        public QuestionController(QuestionService questionService,
                                  AnswerService answerService,
                                  VoteService voteService,
                                  CommentService commentService,
                                  SearchService searchService) {
            this.questionService = questionService;
            this.answerService = answerService;
            this.voteService = voteService;
            this.commentService = commentService;
            this.searchService = searchService;
        }

        // @PostMapping
        public Question createQuestion(CreateQuestionRequest request) {
            User author = getCurrentUser();
            return questionService.createQuestion(request.getTitle(), request.getContent(), author, request.getTags());
        }

        // @GetMapping("/{id}")
        public Question getQuestion(Long id) {
            return questionService.getQuestionById(id);
        }

        // @PostMapping("/{id}/answers")
        public Answer createAnswer(Long id, CreateAnswerRequest request) {
            User author = getCurrentUser();
            Question question = questionService.getQuestionById(id);
            return answerService.createAnswer(request.getContent(), author, question);
        }

        // @PostMapping("/{id}/vote")
        public void voteOnQuestion(Long id, VoteRequest request) {
            User user = getCurrentUser();
            Question question = questionService.getQuestionById(id);
            VoteCommand voteCommand = new VoteCommand(voteService, user, question, request.getVoteType());
            voteCommand.execute();
        }

        // @PostMapping("/{questionId}/answers/{answerId}/accept")
        public void acceptAnswer(Long questionId, Long answerId) {
            User user = getCurrentUser();
            Question question = questionService.getQuestionById(questionId);
            Answer answer = answerService.getAnswerById(answerId);
            questionService.acceptAnswer(question, answer, user);
        }

        // @GetMapping("/search?type=keyword|tag|combined")
        public List<Question> searchQuestions(String query, String type) {
            SearchRequest req = new SearchRequest();

            if ("keyword".equals(type)) {
                req.keyword(query);
            } else if ("tag".equals(type)) {
                // allow comma-separated tags
                List<String> tags = Arrays.asList(query.split(","));
                req.tagNames(tags);
            } else if ("combined".equals(type)) {
                // Simple example: keyword only; extend by filling other fields as needed
                req.keyword(query);
            } else {
                throw new IllegalArgumentException("Unknown search type: " + type);
            }

            SearchResponse resp = searchService.search(type, req);
            return resp.getQuestions();
        }

        private User getCurrentUser() {
            // Implement authentication in real system.
            User u = new User("currentUser", "user@example.com");
            u.setId(1L);
            return u;
        }
    }

    // DTOs
    public static class CreateQuestionRequest {
        private String title;
        private String content;
        private List<String> tags;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }

    public static class CreateAnswerRequest {
        private String content;
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class VoteRequest {
        private VoteType voteType;
        public VoteType getVoteType() { return voteType; }
        public void setVoteType(VoteType voteType) { this.voteType = voteType; }
    }
}
