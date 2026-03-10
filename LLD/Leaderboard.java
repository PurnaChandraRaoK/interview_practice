import java.util.*;

/**
 * Simple Leaderboard:
 * - Stores all users
 * - Supports add/remove, set score, increment score
 * - Top N, user rank (with ties), users around a user
 *
 * Ordering:
 *  1) Higher score first
 *  2) If same score, earlier updatedAt first
 *  3) If still tie, userId lexicographically
 *
 * Note: Rank uses "competition ranking" style:
 * scores: 100, 100, 90 => ranks: 1, 1, 3
 */
public class LeaderboardSystem {

    // ---- Domain Models ----

    private static final class User {
        private final String userId;
        private final String username;

        private User(String userId, String username) {
            this.userId = userId;
            this.username = username;
        }
    }

    private static final class ScoreNode {
        private final String userId;
        private final long score;
        private final long updatedAt; // millis

        private ScoreNode(String userId, long score, long updatedAt) {
            this.userId = userId;
            this.score = score;
            this.updatedAt = updatedAt;
        }
    }

    public static final class LeaderboardEntry {
        private final int rank;
        private final String userId;
        private final String username;
        private final long score;
        private final long updatedAt;

        public LeaderboardEntry(int rank, String userId, String username, long score, long updatedAt) {
            this.rank = rank;
            this.userId = userId;
            this.username = username;
            this.score = score;
            this.updatedAt = updatedAt;
        }

        public int getRank() { return rank; }
        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public long getScore() { return score; }
        public long getUpdatedAt() { return updatedAt; }

        @Override
        public String toString() {
            return "Rank=" + rank + ", " + username + "(" + userId + "), score=" + score;
        }
    }

    // ---- Storage ----

    private final Map<String, User> users = new HashMap<>();
    private final Map<String, ScoreNode> currentNodeByUser = new HashMap<>();

    // TreeSet for ordered leaderboard view
    private final NavigableSet<ScoreNode> ordered = new TreeSet<>(new Comparator<ScoreNode>() {
        @Override
        public int compare(ScoreNode a, ScoreNode b) {
            if (a.score != b.score) return Long.compare(b.score, a.score);          // higher first
            if (a.updatedAt != b.updatedAt) return Long.compare(a.updatedAt, b.updatedAt); // earlier first
            return a.userId.compareTo(b.userId);                                   // stable
        }
    });

    // Single lock for simplicity (LLD-friendly)
    private final Object mutex = new Object();

    // ---- APIs ----

    public boolean addUser(String userId, String username) {
        validateUser(userId, username);

        synchronized (mutex) {
            if (users.containsKey(userId)) return false;

            User u = new User(userId, username);
            users.put(userId, u);

            ScoreNode node = new ScoreNode(userId, 0, now());
            ordered.add(node);
            currentNodeByUser.put(userId, node);
            return true;
        }
    }

    public boolean removeUser(String userId) {
        if (isBlank(userId)) throw new IllegalArgumentException("Invalid userId");

        synchronized (mutex) {
            User removed = users.remove(userId);
            if (removed == null) return false;

            ScoreNode node = currentNodeByUser.remove(userId);
            if (node != null) ordered.remove(node);
            return true;
        }
    }

    public boolean setScore(String userId, long newScore) {
        if (isBlank(userId) || newScore < 0) throw new IllegalArgumentException("Invalid score update");

        synchronized (mutex) {
            if (!users.containsKey(userId)) return false;

            ScoreNode old = currentNodeByUser.get(userId);
            if (old != null) ordered.remove(old);

            ScoreNode updated = new ScoreNode(userId, newScore, now());
            ordered.add(updated);
            currentNodeByUser.put(userId, updated);
            return true;
        }
    }

    public boolean incrementScore(String userId, long delta) {
        if (isBlank(userId) || delta < 0) throw new IllegalArgumentException("Invalid increment");

        synchronized (mutex) {
            ScoreNode old = currentNodeByUser.get(userId);
            if (old == null || !users.containsKey(userId)) return false;

            long newScore = old.score + delta;
            // call internal update path without re-locking complexity:
            ordered.remove(old);

            ScoreNode updated = new ScoreNode(userId, newScore, now());
            ordered.add(updated);
            currentNodeByUser.put(userId, updated);
            return true;
        }
    }

    /** Top N entries with ranks (competition ranking). */
    public List<LeaderboardEntry> getTopUsers(int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be positive");

        synchronized (mutex) {
            List<LeaderboardEntry> res = new ArrayList<>();
            int produced = 0;

            int rank = 1;
            long prevScore = Long.MIN_VALUE;
            int tiesCount = 0;

            for (ScoreNode node : ordered) {
                if (produced >= n) break;

                User u = users.get(node.userId);
                if (u == null) continue; // safety

                if (node.score == prevScore) {
                    tiesCount++;
                } else {
                    rank += tiesCount;
                    tiesCount = 1;
                    prevScore = node.score;
                }

                res.add(new LeaderboardEntry(rank, u.userId, u.username, node.score, node.updatedAt));
                produced++;
            }
            return res;
        }
    }

    /** Returns rank of user (competition ranking). -1 if not found. */
    public int getUserRank(String userId) {
        if (isBlank(userId)) throw new IllegalArgumentException("Invalid userId");

        synchronized (mutex) {
            if (!users.containsKey(userId)) return -1;

            int rank = 1;
            long prevScore = Long.MIN_VALUE;
            int tiesCount = 0;

            for (ScoreNode node : ordered) {
                if (node.score == prevScore) {
                    tiesCount++;
                } else {
                    rank += tiesCount;
                    tiesCount = 1;
                    prevScore = node.score;
                }

                if (node.userId.equals(userId)) return rank;
            }
            return -1;
        }
    }

    /**
     * Returns a window of size "count" around the user in the ordered list.
     * Example: count=5 -> 2 above, user, 2 below (as available).
     */
    public List<LeaderboardEntry> getUsersAroundUser(String userId, int count) {
        if (isBlank(userId) || count <= 0) throw new IllegalArgumentException("Invalid parameters");

        synchronized (mutex) {
            if (!users.containsKey(userId)) return Collections.emptyList();

            // Convert to list once (simple + interview friendly)
            List<ScoreNode> list = new ArrayList<>(ordered);

            int idx = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).userId.equals(userId)) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) return Collections.emptyList();

            int half = count / 2;
            int start = Math.max(0, idx - half);
            int end = Math.min(list.size(), start + count);

            // Ensure we still include user if near end
            start = Math.max(0, end - count);

            // Build entries with correct ranks for that slice:
            // easiest: compute full ranks once by scanning ordered, store rank per userId
            Map<String, Integer> rankByUser = computeRanks();

            List<LeaderboardEntry> res = new ArrayList<>();
            for (int i = start; i < end; i++) {
                ScoreNode node = list.get(i);
                User u = users.get(node.userId);
                if (u == null) continue;

                int rank = rankByUser.getOrDefault(node.userId, -1);
                res.add(new LeaderboardEntry(rank, u.userId, u.username, node.score, node.updatedAt));
            }
            return res;
        }
    }

    public void resetAllScores() {
        synchronized (mutex) {
            ordered.clear();
            currentNodeByUser.clear();

            long t = now();
            for (String userId : users.keySet()) {
                ScoreNode node = new ScoreNode(userId, 0, t);
                ordered.add(node);
                currentNodeByUser.put(userId, node);
            }
        }
    }

    public int getTotalUsers() {
        synchronized (mutex) {
            return users.size();
        }
    }

    // ---- Helpers ----

    private Map<String, Integer> computeRanks() {
        Map<String, Integer> rankByUser = new HashMap<>();

        int rank = 1;
        long prevScore = Long.MIN_VALUE;
        int tiesCount = 0;

        for (ScoreNode node : ordered) {
            if (node.score == prevScore) {
                tiesCount++;
            } else {
                rank += tiesCount;
                tiesCount = 1;
                prevScore = node.score;
            }
            rankByUser.put(node.userId, rank);
        }
        return rankByUser;
    }

    private static void validateUser(String userId, String username) {
        if (isBlank(userId) || isBlank(username)) {
            throw new IllegalArgumentException("Invalid user data");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    // ---- Quick demo (optional) ----
    public static void main(String[] args) {
        LeaderboardSystem lb = new LeaderboardSystem();
        lb.addUser("u1", "Alice");
        lb.addUser("u2", "Bob");
        lb.addUser("u3", "Charlie");

        lb.setScore("u1", 100);
        lb.setScore("u2", 100);
        lb.setScore("u3", 90);

        System.out.println(lb.getTopUsers(10));
        System.out.println("Rank u1=" + lb.getUserRank("u1")); // 1
        System.out.println("Rank u2=" + lb.getUserRank("u2")); // 1
        System.out.println("Rank u3=" + lb.getUserRank("u3")); // 3

        System.out.println("Around u3=" + lb.getUsersAroundUser("u3", 3));
    }
}
