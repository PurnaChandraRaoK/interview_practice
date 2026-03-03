import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * snakeandladder.com - Single file LLD (multi-player)
 * - Account register + login
 * - Profile stats: gamesPlayed, wins, losses, draws, win%
 * - Public matchmaking (random queue) with minPlayers/maxPlayers
 * - Private rooms (create + share code) with minPlayers/maxPlayers
 * - Core Snake & Ladder gameplay (1 dice 1..6, snakes/ladders via transition map)
 */
public class SnakeAndLadderPlatform {

    // ========================== DEMO ==========================
    public static void main(String[] args) {
        AuthService authService = new AuthService();
        PrivateRoomService privateRoomService = new PrivateRoomService();

        // Create users
        authService.register("alice", "pass");
        authService.register("bob", "pass");
        authService.register("charlie", "pass");
        authService.register("dave", "pass");

        User alice = authService.login("alice", "pass");
        User bob = authService.login("bob", "pass");
        User charlie = authService.login("charlie", "pass");
        User dave = authService.login("dave", "pass");

        System.out.println("Login successful. Profile Alice: " + alice.getStats());
        System.out.println("Login successful. Profile Bob:   " + bob.getStats());

        // ======= PUBLIC MATCHMAKING (min=2, max=4) =======
        System.out.println("\n=== PUBLIC MATCHMAKING (min=2, max=4) ===");
        MatchmakingStrategy publicStrategy = new RandomQueueMatchmakingStrategy(2, 4);

        GameSession s1 = publicStrategy.findMatch(alice);
        if (s1 == null) System.out.println("Alice queued. Waiting for more players...");

        GameSession s2 = publicStrategy.findMatch(bob);
        if (s2 != null) {
            System.out.println("Game started: " + s2.getSessionId() + " Players=" + usernames(s2.getPlayers()));
            User winner = s2.getGame().start();
            updateStats(s2, winner);
            System.out.println("Winner: " + winner.getUsername());
            System.out.println("Alice profile: " + alice.getStats());
            System.out.println("Bob profile:   " + bob.getStats());
        }

        // Queue 2 more
        GameSession s3 = publicStrategy.findMatch(charlie);
        if (s3 == null) System.out.println("Charlie queued. Waiting...");

        GameSession s4 = publicStrategy.findMatch(dave);
        if (s4 != null) {
            System.out.println("Game started: " + s4.getSessionId() + " Players=" + usernames(s4.getPlayers()));
            User winner = s4.getGame().start();
            updateStats(s4, winner);
            System.out.println("Winner: " + winner.getUsername());
            System.out.println("Charlie profile: " + charlie.getStats());
            System.out.println("Dave profile:    " + dave.getStats());
        }

        // ======= PRIVATE ROOM (min=2, max=3) =======
        System.out.println("\n=== PRIVATE ROOM (min=2, max=3) ===");
        MatchmakingStrategy createRoom = new PrivateRoomMatchmakingStrategy(privateRoomService, true, null, 2, 3);
        MatchmakingStrategy joinRoom;

        // Host creates room
        GameSession hostSession = createRoom.findMatch(alice);
        // Always null on create (waiting for friend)
        String roomCode = ((PrivateRoomMatchmakingStrategy) createRoom).getCreatedRoomCode();
        System.out.println("Alice created room. Share code: " + roomCode);

        // Friend joins
        joinRoom = new PrivateRoomMatchmakingStrategy(privateRoomService, false, roomCode, 2, 3);
        GameSession privateGame = joinRoom.findMatch(bob);

        if (privateGame != null) {
            System.out.println("Private game started: " + privateGame.getSessionId() +
                    " Players=" + usernames(privateGame.getPlayers()));
            User winner = privateGame.getGame().start();
            updateStats(privateGame, winner);
            System.out.println("Winner: " + winner.getUsername());
            System.out.println("Alice profile: " + alice.getStats());
            System.out.println("Bob profile:   " + bob.getStats());
        } else {
            System.out.println("Waiting for more players in private room...");
        }

        System.out.println("\n=== FINAL PROFILES ===");
        System.out.println("Alice   -> " + alice.getStats());
        System.out.println("Bob     -> " + bob.getStats());
        System.out.println("Charlie -> " + charlie.getStats());
        System.out.println("Dave    -> " + dave.getStats());
    }

    private static List<String> usernames(List<User> players) {
        List<String> names = new ArrayList<>();
        for (User u : players) names.add(u.getUsername());
        return names;
    }

    // ========================== PROFILE STATS ==========================
    static class ProfileStats {
        private int gamesPlayed;
        private int wins;
        private int losses;
        private int draws;

        public void recordWin() {
            gamesPlayed++;
            wins++;
        }

        public void recordLoss() {
            gamesPlayed++;
            losses++;
        }

        public void recordDraw() {
            gamesPlayed++;
            draws++;
        }

        public int getGamesPlayed() {
            return gamesPlayed;
        }

        public double getWinPercentage() {
            return gamesPlayed == 0 ? 0.0 : (wins * 100.0) / gamesPlayed;
        }
    }

    // ========================== USER ==========================
    static class User {
        private final String id = UUID.randomUUID().toString();
        private final String username;
        private final String password;
        private final ProfileStats stats = new ProfileStats();

        public User(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public String getId() { return id; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public ProfileStats getStats() { return stats; }
    }

    // ========================== AUTH SERVICE ==========================
    static class AuthService {
        private final Map<String, User> users = new ConcurrentHashMap<>();

        public User register(String username, String password) {
            if (users.containsKey(username)) {
                throw new IllegalArgumentException("User already exists: " + username);
            }
            User u = new User(username, password);
            users.put(username, u);
            return u;
        }

        public User login(String username, String password) {
            User u = users.get(username);
            if (u == null || !u.getPassword().equals(password)) {
                throw new IllegalArgumentException("Invalid credentials");
            }
            return u;
        }
    }

    // ========================== GAME SESSION ==========================
    static class GameSession {
        private final String sessionId = UUID.randomUUID().toString();
        private final List<User> players;
        private final SnakeAndLadderGame game;

        public GameSession(List<User> players) {
            if (players == null || players.size() < 2) {
                throw new IllegalArgumentException("At least 2 players required");
            }
            this.players = Collections.unmodifiableList(new ArrayList<>(players));
            this.game = new SnakeAndLadderGame(players);
        }

        public String getSessionId() { return sessionId; }
        public List<User> getPlayers() { return players; }
        public SnakeAndLadderGame getGame() { return game; }
    }

    // ========================== MATCHMAKING STRATEGY ==========================
    interface MatchmakingStrategy {
        GameSession findMatch(User user);
    }

    /**
     * Public matchmaking queue using minPlayers/maxPlayers.
     * - Starts a match as soon as minPlayers are available
     * - Fills up to maxPlayers if more are already waiting
     */
    static class RandomQueueMatchmakingStrategy implements MatchmakingStrategy {
        private final int minPlayers;
        private final int maxPlayers;
        private final Queue<User> waiting = new ArrayDeque<>();

        public RandomQueueMatchmakingStrategy(int minPlayers, int maxPlayers) {
            if (minPlayers < 2) throw new IllegalArgumentException("minPlayers must be >= 2");
            if (maxPlayers < minPlayers) throw new IllegalArgumentException("maxPlayers must be >= minPlayers");
            this.minPlayers = minPlayers;
            this.maxPlayers = maxPlayers;
        }

        @Override
        public synchronized GameSession findMatch(User user) {
            // If adding this user reaches minPlayers, start a game immediately.
            int available = waiting.size() + 1;
            if (available >= minPlayers) {
                int matchSize = Math.min(maxPlayers, available);
                List<User> players = new ArrayList<>(matchSize);

                // include current user
                players.add(user);

                // pull remaining from waiting queue
                while (players.size() < matchSize && !waiting.isEmpty()) {
                    players.add(waiting.poll());
                }

                // Shuffle for randomness of order
                Collections.shuffle(players);
                return new GameSession(players);
            }

            // Not enough players yet; queue user
            waiting.offer(user);
            return null;
        }
    }

    // ========================== PRIVATE ROOMS ==========================
    static class PrivateRoomService {
        private final Map<String, Room> rooms = new ConcurrentHashMap<>();

        public String createRoom(User host, int minPlayers, int maxPlayers) {
            validateMinMax(minPlayers, maxPlayers);
            String code = UUID.randomUUID().toString().substring(0, 8);
            Room room = new Room(code, minPlayers, maxPlayers);
            room.players.add(host);
            rooms.put(code, room);
            return code;
        }

        public GameSession joinRoom(String code, User guest) {
            Room room = rooms.get(code);
            if (room == null) throw new IllegalArgumentException("Room not found: " + code);

            synchronized (room) {
                if (room.players.size() >= room.maxPlayers) {
                    throw new IllegalStateException("Room is full");
                }
                room.players.add(guest);

                // Start as soon as minPlayers reached (and <= maxPlayers by construction)
                if (room.players.size() >= room.minPlayers) {
                    rooms.remove(code);
                    List<User> players = new ArrayList<>(room.players);
                    Collections.shuffle(players);
                    return new GameSession(players);
                }
                return null;
            }
        }

        private void validateMinMax(int minPlayers, int maxPlayers) {
            if (minPlayers < 2) throw new IllegalArgumentException("minPlayers must be >= 2");
            if (maxPlayers < minPlayers) throw new IllegalArgumentException("maxPlayers must be >= minPlayers");
        }

        static class Room {
            final String code;
            final int minPlayers;
            final int maxPlayers;
            final List<User> players = new ArrayList<>();

            Room(String code, int minPlayers, int maxPlayers) {
                this.code = code;
                this.minPlayers = minPlayers;
                this.maxPlayers = maxPlayers;
            }
        }
    }

    static class PrivateRoomMatchmakingStrategy implements MatchmakingStrategy {
        private final PrivateRoomService rooms;
        private final boolean create;
        private final String roomCodeToJoin;
        private final int minPlayers;
        private final int maxPlayers;

        private String createdRoomCode; // for demo

        public PrivateRoomMatchmakingStrategy(PrivateRoomService rooms,
                                             boolean create,
                                             String roomCodeToJoin,
                                             int minPlayers,
                                             int maxPlayers) {
            this.rooms = rooms;
            this.create = create;
            this.roomCodeToJoin = roomCodeToJoin;
            this.minPlayers = minPlayers;
            this.maxPlayers = maxPlayers;
        }

        public String getCreatedRoomCode() {
            return createdRoomCode;
        }

        @Override
        public GameSession findMatch(User user) {
            if (create) {
                createdRoomCode = rooms.createRoom(user, minPlayers, maxPlayers);
                return null; // waiting for others
            }
            if (roomCodeToJoin == null || roomCodeToJoin.isBlank()) {
                throw new IllegalArgumentException("roomCode required to join");
            }
            return rooms.joinRoom(roomCodeToJoin, user);
        }
    }

    // ========================== SNAKE & LADDER GAME (CORE) ==========================
    static class SnakeAndLadderGame {
        private static final int BOARD_SIZE = 100;
        private static final int SNAKES = 8;
        private static final int LADDERS = 8;

        private final Board board;
        private final Dice dice = new Dice();
        private final Deque<PlayerState> turnOrder;

        public SnakeAndLadderGame(List<User> players) {
            this.board = new BoardFactory().createRandomBoard(BOARD_SIZE, SNAKES, LADDERS);
            this.turnOrder = new ArrayDeque<>();
            for (User u : players) {
                turnOrder.addLast(new PlayerState(u, 1));
            }
        }

        // Returns winner User (first to reach end)
        public User start() {
            while (true) {
                PlayerState current = turnOrder.pollFirst();

                int roll = dice.roll();
                int from = current.position;
                int tentative = from + roll;

                // Overshoot => stay (keeps original typical snake&ladder rule)
                int next = (tentative > board.end) ? from : tentative;

                // Apply snake/ladder transition (O(1))
                next = board.applyTransition(next);

                current.position = next;

                // Win
                if (next == board.end) {
                    return current.user;
                }

                // Rotate turn
                turnOrder.addLast(current);
            }
        }
    }

    static class PlayerState {
        final User user;
        int position;

        PlayerState(User user, int position) {
            this.user = user;
            this.position = position;
        }
    }

    static class Dice {
        private final Random random = new Random();
        public int roll() { return 1 + random.nextInt(6); }
    }

    static class Board {
        final int start = 1;
        final int end;
        final Map<Integer, Integer> transitions; // snake/ladder lookup

        Board(int size, Map<Integer, Integer> transitions) {
            if (size < 2) throw new IllegalArgumentException("Board size must be >= 2");
            this.end = size;
            this.transitions = new HashMap<>(transitions);
        }

        int applyTransition(int position) {
            return transitions.getOrDefault(position, position);
        }
    }

    /**
     * Random board generator:
     * - Snakes: head -> tail (down)
     * - Ladders: bottom -> top (up)  (FIXED direction)
     * - Avoid start/end cells
     * - O(1) transitions map
     */
    static class BoardFactory {
        private final Random random = new Random();

        Board createRandomBoard(int size, int snakes, int ladders) {
            Map<Integer, Integer> transitions = new HashMap<>();
            Set<Integer> usedStarts = new HashSet<>();

            // Snakes (head > tail)
            for (int i = 0; i < snakes; i++) {
                while (true) {
                    int head = between(2, size - 1);
                    int tail = between(2, size - 1);
                    if (head <= tail) continue;
                    if (usedStarts.contains(head)) continue;

                    usedStarts.add(head);
                    transitions.put(head, tail);
                    break;
                }
            }

            // Ladders (bottom < top)  (bottom -> top)
            for (int i = 0; i < ladders; i++) {
                while (true) {
                    int bottom = between(2, size - 1);
                    int top = between(2, size - 1);
                    if (bottom >= top) continue;
                    if (usedStarts.contains(bottom)) continue;

                    usedStarts.add(bottom);
                    transitions.put(bottom, top);
                    break;
                }
            }

            return new Board(size, transitions);
        }

        private int between(int loInclusive, int hiInclusive) {
            return loInclusive + random.nextInt(hiInclusive - loInclusive + 1);
        }
    }

    // ========================== STATS UPDATE ==========================
    private static void updateStats(GameSession session, User winner) {
        // Snake & Ladder: no draw (kept recordDraw for uniformity)
        for (User u : session.getPlayers()) {
            if (u.getId().equals(winner.getId())) {
                u.getStats().recordWin();
            } else {
                u.getStats().recordLoss();
            }
        }
    }
}
