/**
 * Social Media / News Feed LLD (in-memory)
 * - Users + follow/unfollow
 * - Posts (TEXT/IMAGE/VIDEO) via Factory
 * - Comments (nested replies) + edit/delete
 * - Notifications (simple in-memory)
 * - NewsFeed: merge K sorted streams of posts by timestamp + pagination
 */
public class SocialMediaLLD {

    // ===================== ENUMS =====================
    public enum PostType { TEXT, IMAGE, VIDEO }

    // ===================== DOMAIN =====================
    public static final class User {
        private final String userId;
        private final String username;

        public User(String userId, String username) {
            this.userId = Objects.requireNonNull(userId);
            this.username = Objects.requireNonNull(username);
        }

        public String getUserId() { return userId; }
        public String getUsername() { return username; }
    }

    /**
     * Keeping Post simple: id, authorId, type, content, timestamps.
     * If you want richer model later: attachments, visibility, tags, etc.
     */
    public static final class Post {
        private final String postId;
        private final String authorId;
        private final PostType type;
        private final String content;
        private final Instant createdAt;
        private Instant updatedAt;

        public Post(String postId, String authorId, PostType type, String content, Instant createdAt) {
            this.postId = Objects.requireNonNull(postId);
            this.authorId = Objects.requireNonNull(authorId);
            this.type = Objects.requireNonNull(type);
            this.content = Objects.requireNonNull(content);
            this.createdAt = Objects.requireNonNull(createdAt);
        }

        public String getPostId() { return postId; }
        public String getAuthorId() { return authorId; }
        public PostType getType() { return type; }
        public String getContent() { return content; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public void touch() { this.updatedAt = Instant.now(); }
    }

    public static final class Comment {
        private final String commentId;
        private final String postId;
        private final String commenterId;
        private String text;
        private final Instant createdAt;
        private Instant updatedAt;
        private final List<Comment> replies = new ArrayList<>();

        public Comment(String commentId, String postId, String commenterId, String text) {
            this.commentId = Objects.requireNonNull(commentId);
            this.postId = Objects.requireNonNull(postId);
            this.commenterId = Objects.requireNonNull(commenterId);
            this.text = Objects.requireNonNull(text);
            this.createdAt = Instant.now();
        }

        public String getCommentId() { return commentId; }
        public String getPostId() { return postId; }
        public String getCommenterId() { return commenterId; }
        public String getText() { return text; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public List<Comment> getReplies() { return Collections.unmodifiableList(replies); }

        public void edit(String newText) {
            this.text = Objects.requireNonNull(newText);
            this.updatedAt = Instant.now();
        }

        public void addReply(Comment reply) {
            replies.add(Objects.requireNonNull(reply));
        }

        public Comment findById(String id) {
            if (this.commentId.equals(id)) return this;
            for (Comment c : replies) {
                Comment found = c.findById(id);
                if (found != null) return found;
            }
            return null;
        }

        public boolean deleteReplyById(String id) {
            for (Iterator<Comment> it = replies.iterator(); it.hasNext();) {
                Comment child = it.next();
                if (child.commentId.equals(id)) {
                    it.remove();
                    return true;
                }
                if (child.deleteReplyById(id)) return true;
            }
            return false;
        }
    }

    // ===================== EXCEPTIONS =====================
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String msg) { super(msg); }
    }

    // ===================== REPOSITORY CONTRACTS =====================
    public interface UserRepository {
        void save(User user);
        User get(String userId);
        boolean exists(String userId);
    }

    public interface UserRelationsRepository {
        void follow(String followerId, String followeeId);
        void unfollow(String followerId, String followeeId);
        Set<String> getFollowing(String userId);
        Set<String> getFollowers(String userId);
    }

    public interface PostRepository {
        Post save(Post post);
        Post get(String postId);
        boolean delete(String postId);

        /** Returns iterator of user's posts in DESC timestamp order (newest first). */
        Iterator<PostRef> iterateUserPostsDesc(String userId);
    }

    public interface CommentRepository {
        void addRootComment(String postId, Comment comment);
        List<Comment> getRootComments(String postId);

        Comment findComment(String postId, String commentId);
        boolean deleteComment(String postId, String commentId);
    }

    public interface NotificationService {
        void notify(String userId, String message);
        List<String> getNotifications(String userId);
    }

    // ===================== IN-MEMORY IMPLEMENTATIONS =====================
    public static final class InMemoryUserRepository implements UserRepository {
        private final Map<String, User> users = new ConcurrentHashMap<>();

        @Override public void save(User user) { users.put(user.getUserId(), user); }

        @Override public User get(String userId) {
            User u = users.get(userId);
            if (u == null) throw new NotFoundException("User not found: " + userId);
            return u;
        }

        @Override public boolean exists(String userId) { return users.containsKey(userId); }
    }

    public static final class InMemoryUserRelationsRepository implements UserRelationsRepository {
        private final Map<String, Set<String>> following = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> followers = new ConcurrentHashMap<>();

        @Override
        public void follow(String followerId, String followeeId) {
            if (followerId.equals(followeeId)) return;

            following.computeIfAbsent(followerId, k -> ConcurrentHashMap.newKeySet()).add(followeeId);
            followers.computeIfAbsent(followeeId, k -> ConcurrentHashMap.newKeySet()).add(followerId);
        }

        @Override
        public void unfollow(String followerId, String followeeId) {
            Set<String> f1 = following.getOrDefault(followerId, Collections.emptySet());
            f1.remove(followeeId);

            Set<String> f2 = followers.getOrDefault(followeeId, Collections.emptySet());
            f2.remove(followerId);
        }

        @Override public Set<String> getFollowing(String userId) {
            return Collections.unmodifiableSet(following.getOrDefault(userId, Collections.emptySet()));
        }

        @Override public Set<String> getFollowers(String userId) {
            return Collections.unmodifiableSet(followers.getOrDefault(userId, Collections.emptySet()));
        }
    }

    /**
     * PostRef avoids storing Post object inside a TreeSet and comparator issues with ties.
     * We keep a (timestamp, seq) to guarantee strict ordering.
     */
    public static final class PostRef {
        private final String postId;
        private final long epochMilli;
        private final long seq;

        public PostRef(String postId, long epochMilli, long seq) {
            this.postId = postId;
            this.epochMilli = epochMilli;
            this.seq = seq;
        }

        public String getPostId() { return postId; }
        public long getEpochMilli() { return epochMilli; }
        public long getSeq() { return seq; }
    }

    public static final class InMemoryPostRepository implements PostRepository {
        private static final int PER_USER_CAP = 100;

        private final Map<String, Post> postsById = new ConcurrentHashMap<>();
        private final Map<String, Deque<PostRef>> userPosts = new ConcurrentHashMap<>();
        private final AtomicLong seqGen = new AtomicLong(0);

        @Override
        public Post save(Post post) {
            postsById.put(post.getPostId(), post);

            Deque<PostRef> dq = userPosts.computeIfAbsent(post.getAuthorId(), k -> new ArrayDeque<>());
            if (dq.size() >= PER_USER_CAP) dq.removeLast();

            long ts = post.getCreatedAt().toEpochMilli();
            dq.addFirst(new PostRef(post.getPostId(), ts, seqGen.incrementAndGet()));
            return post;
        }

        @Override
        public Post get(String postId) {
            Post p = postsById.get(postId);
            if (p == null) throw new NotFoundException("Post not found: " + postId);
            return p;
        }

        @Override
        public boolean delete(String postId) {
            Post p = postsById.remove(postId);
            if (p == null) return false;

            Deque<PostRef> dq = userPosts.getOrDefault(p.getAuthorId(), new ArrayDeque<>());
            dq.removeIf(ref -> ref.getPostId().equals(postId));
            return true;
        }

        @Override
        public Iterator<PostRef> iterateUserPostsDesc(String userId) {
            Deque<PostRef> dq = userPosts.getOrDefault(userId, new ArrayDeque<>());
            return dq.iterator(); // already newest-first
        }
    }

    public static final class InMemoryCommentRepository implements CommentRepository {
        private final Map<String, List<Comment>> postToRootComments = new ConcurrentHashMap<>();

        @Override
        public void addRootComment(String postId, Comment comment) {
            postToRootComments.computeIfAbsent(postId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(comment);
        }

        @Override
        public List<Comment> getRootComments(String postId) {
            return Collections.unmodifiableList(postToRootComments.getOrDefault(postId, Collections.emptyList()));
        }

        @Override
        public Comment findComment(String postId, String commentId) {
            for (Comment root : postToRootComments.getOrDefault(postId, Collections.emptyList())) {
                Comment found = root.findById(commentId);
                if (found != null) return found;
            }
            return null;
        }

        @Override
        public boolean deleteComment(String postId, String commentId) {
            List<Comment> roots = postToRootComments.getOrDefault(postId, Collections.emptyList());

            // remove root
            boolean removedRoot = roots.removeIf(c -> c.getCommentId().equals(commentId));
            if (removedRoot) return true;

            // remove nested
            for (Comment root : roots) {
                if (root.deleteReplyById(commentId)) return true;
            }
            return false;
        }
    }

    public static final class InMemoryNotificationService implements NotificationService {
        private final Map<String, List<String>> notifications = new ConcurrentHashMap<>();

        @Override
        public void notify(String userId, String message) {
            notifications.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(message);
        }

        @Override
        public List<String> getNotifications(String userId) {
            return Collections.unmodifiableList(notifications.getOrDefault(userId, Collections.emptyList()));
        }
    }

    // ===================== FACTORY (EXTENSIBLE) =====================
    public interface PostCreator {
        Post create(String postId, String authorId, String content);
    }

    public static final class PostFactory {
        private final Map<PostType, PostCreator> creators = new EnumMap<>(PostType.class);

        public PostFactory() {
            creators.put(PostType.TEXT,  (id, author, content) -> new Post(id, author, PostType.TEXT,  content, Instant.now()));
            creators.put(PostType.IMAGE, (id, author, content) -> new Post(id, author, PostType.IMAGE, content, Instant.now()));
            creators.put(PostType.VIDEO, (id, author, content) -> new Post(id, author, PostType.VIDEO, content, Instant.now()));
        }

        public void register(PostType type, PostCreator creator) { creators.put(type, creator); }

        public Post create(PostType type, String authorId, String content) {
            PostCreator creator = creators.get(type);
            if (creator == null) throw new IllegalArgumentException("Unsupported post type: " + type);
            return creator.create(Id.next(), authorId, content);
        }
    }

    // ===================== SERVICES =====================
    public static final class UserService {
        private final UserRepository userRepo;

        public UserService(UserRepository userRepo) { this.userRepo = userRepo; }

        public User createUser(String userId, String username) {
            User u = new User(userId, username);
            userRepo.save(u);
            return u;
        }

        public User getUser(String userId) { return userRepo.get(userId); }
        public boolean exists(String userId) { return userRepo.exists(userId); }
    }

    public static final class UserRelationsService {
        private final UserRepository userRepo;
        private final UserRelationsRepository relationsRepo;

        public UserRelationsService(UserRepository userRepo, UserRelationsRepository relationsRepo) {
            this.userRepo = userRepo;
            this.relationsRepo = relationsRepo;
        }

        public void follow(String followerId, String followeeId) {
            userRepo.get(followerId);
            userRepo.get(followeeId);
            relationsRepo.follow(followerId, followeeId);
        }

        public void unfollow(String followerId, String followeeId) {
            userRepo.get(followerId);
            userRepo.get(followeeId);
            relationsRepo.unfollow(followerId, followeeId);
        }

        public Set<String> getFollowing(String userId) {
            userRepo.get(userId);
            return relationsRepo.getFollowing(userId);
        }
    }

    public static final class PostService {
        private final UserRepository userRepo;
        private final PostRepository postRepo;
        private final PostFactory postFactory;

        public PostService(UserRepository userRepo, PostRepository postRepo, PostFactory postFactory) {
            this.userRepo = userRepo;
            this.postRepo = postRepo;
            this.postFactory = postFactory;
        }

        public Post createPost(String authorId, PostType type, String content) {
            userRepo.get(authorId);
            Post p = postFactory.create(type, authorId, content);
            return postRepo.save(p);
        }

        public Post getPost(String postId) { return postRepo.get(postId); }

        public boolean deletePost(String requesterId, String postId) {
            Post p = postRepo.get(postId);
            if (!p.getAuthorId().equals(requesterId)) {
                throw new IllegalArgumentException("Only author can delete the post.");
            }
            return postRepo.delete(postId);
        }
    }

    public static final class CommentService {
        private final UserRepository userRepo;
        private final PostRepository postRepo;
        private final CommentRepository commentRepo;
        private final NotificationService notificationService;

        public CommentService(UserRepository userRepo,
                              PostRepository postRepo,
                              CommentRepository commentRepo,
                              NotificationService notificationService) {
            this.userRepo = userRepo;
            this.postRepo = postRepo;
            this.commentRepo = commentRepo;
            this.notificationService = notificationService;
        }

        public Comment addComment(String postId, String commenterId, String text) {
            userRepo.get(commenterId);
            Post post = postRepo.get(postId);

            Comment c = new Comment(Id.next(), postId, commenterId, text);
            commentRepo.addRootComment(postId, c);

            // notify post author (SRP: notification handled here, not in Post entity)
            notificationService.notify(post.getAuthorId(),
                    "New comment on your post by " + commenterId + ": " + text);

            return c;
        }

        public Comment reply(String postId, String parentCommentId, String commenterId, String text) {
            userRepo.get(commenterId);
            postRepo.get(postId);

            Comment parent = commentRepo.findComment(postId, parentCommentId);
            if (parent == null) throw new NotFoundException("Comment not found: " + parentCommentId);

            Comment reply = new Comment(Id.next(), postId, commenterId, text);
            parent.addReply(reply);

            // notify parent commenter (simple)
            notificationService.notify(parent.getCommenterId(),
                    "New reply from " + commenterId + ": " + text);

            return reply;
        }

        public void edit(String postId, String commentId, String requesterId, String newText) {
            userRepo.get(requesterId);
            postRepo.get(postId);

            Comment target = commentRepo.findComment(postId, commentId);
            if (target == null) throw new NotFoundException("Comment not found: " + commentId);

            if (!target.getCommenterId().equals(requesterId)) {
                throw new IllegalArgumentException("Only comment author can edit.");
            }
            target.edit(newText);
        }

        public boolean delete(String postId, String commentId, String requesterId) {
            userRepo.get(requesterId);
            postRepo.get(postId);

            Comment target = commentRepo.findComment(postId, commentId);
            if (target == null) return false;

            if (!target.getCommenterId().equals(requesterId)) {
                throw new IllegalArgumentException("Only comment author can delete.");
            }
            return commentRepo.deleteComment(postId, commentId);
        }

        public List<Comment> getComments(String postId) {
            postRepo.get(postId);
            return commentRepo.getRootComments(postId);
        }
    }

    public static final class NewsFeedService {
        private final UserRepository userRepo;
        private final UserRelationsRepository relationsRepo;
        private final PostRepository postRepo;

        public NewsFeedService(UserRepository userRepo, UserRelationsRepository relationsRepo, PostRepository postRepo) {
            this.userRepo = userRepo;
            this.relationsRepo = relationsRepo;
            this.postRepo = postRepo;
        }

        /**
         * Merge K sorted iterators (each followee's post stream is newest-first).
         * Returns newest posts for user from (self + followees).
         */
        public List<Post> getNewsFeed(String userId, int limit) {
            userRepo.get(userId);
            if (limit <= 0) return Collections.emptyList();

            Set<String> sources = new HashSet<>(relationsRepo.getFollowing(userId));
            sources.add(userId); // include self posts (typical feed)

            PriorityQueue<StreamCursor> pq = new PriorityQueue<>(
                    (a, b) -> {
                        int cmp = Long.compare(b.current.epochMilli, a.current.epochMilli);
                        if (cmp != 0) return cmp;
                        return Long.compare(b.current.seq, a.current.seq);
                    });

            for (String srcUserId : sources) {
                Iterator<PostRef> it = postRepo.iterateUserPostsDesc(srcUserId);
                if (it.hasNext()) pq.offer(new StreamCursor(it.next(), it));
            }

            List<Post> result = new ArrayList<>();
            while (!pq.isEmpty() && result.size() < limit) {
                StreamCursor cursor = pq.poll();
                result.add(postRepo.get(cursor.current.postId));
                if (cursor.it.hasNext()) {
                    cursor.current = cursor.it.next();
                    pq.offer(cursor);
                }
            }
            return result;
        }

        public List<Post> getNewsFeedPage(String userId, int pageNumber, int pageSize) {
            if (pageNumber <= 0 || pageSize <= 0) return Collections.emptyList();

            int needed = pageNumber * pageSize;
            List<Post> top = getNewsFeed(userId, needed);

            int start = (pageNumber - 1) * pageSize;
            if (start >= top.size()) return Collections.emptyList();

            int end = Math.min(start + pageSize, top.size());
            return top.subList(start, end);
        }

        private static final class StreamCursor {
            private PostRef current;
            private final Iterator<PostRef> it;

            private StreamCursor(PostRef current, Iterator<PostRef> it) {
                this.current = current;
                this.it = it;
            }
        }
    }

    // ===================== ID GENERATOR =====================
    public static final class Id {
        public static String next() { return UUID.randomUUID().toString(); }
    }

    // ===================== (OPTIONAL) DEMO =====================
    public static void main(String[] args) {
        // Wiring
        UserRepository userRepo = new InMemoryUserRepository();
        UserRelationsRepository relationsRepo = new InMemoryUserRelationsRepository();
        PostRepository postRepo = new InMemoryPostRepository();
        CommentRepository commentRepo = new InMemoryCommentRepository();
        NotificationService notificationSvc = new InMemoryNotificationService();
        PostFactory postFactory = new PostFactory();

        UserService userService = new UserService(userRepo);
        UserRelationsService relationsService = new UserRelationsService(userRepo, relationsRepo);
        PostService postService = new PostService(userRepo, postRepo, postFactory);
        CommentService commentService = new CommentService(userRepo, postRepo, commentRepo, notificationSvc);
        NewsFeedService feedService = new NewsFeedService(userRepo, relationsRepo, postRepo);

        // Users
        userService.createUser("u1", "alice");
        userService.createUser("u2", "bob");
        userService.createUser("u3", "charlie");

        // Follow
        relationsService.follow("u1", "u2");
        relationsService.follow("u1", "u3");

        // Posts
        Post p1 = postService.createPost("u2", PostType.TEXT, "hello from bob");
        Post p2 = postService.createPost("u3", PostType.IMAGE, "https://img/1.png");
        postService.createPost("u1", PostType.VIDEO, "https://vid/1.mp4");

        // Comments
        Comment c1 = commentService.addComment(p1.getPostId(), "u1", "nice!");
        commentService.reply(p1.getPostId(), c1.getCommentId(), "u2", "thanks!");

        // Feed
        List<Post> feedPage1 = feedService.getNewsFeedPage("u1", 1, 2);
        System.out.println("Feed page1: " + feedPage1.size());

        // Notifications
        System.out.println("Bob notifications: " + notificationSvc.getNotifications("u2"));
        System.out.println("Alice notifications: " + notificationSvc.getNotifications("u1"));
    }
}
