import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TicTacToePlatform {

    // ============================ DEMO ============================
    public static void main(String[] args) {
        AuthService auth = new AuthService();
        PrivateRoomService rooms = new PrivateRoomService();

        auth.register("alice", "pass");
        auth.register("bob", "pass");

        User alice = auth.login("alice", "pass");
        User bob = auth.login("bob", "pass");

        System.out.println("Alice -> " + alice.getStats());
        System.out.println("Bob   -> " + bob.getStats());

        // Public Queue (2 players only)
        System.out.println("\n=== PUBLIC QUEUE ===");
        MatchmakingStrategy queue = new RandomQueueMatchmakingStrategy();
        GameSession s1 = queue.findMatch(alice);
        if (s1 == null) System.out.println("Alice queued, waiting...");

        GameSession s2 = queue.findMatch(bob);
        if (s2 != null) {
            System.out.println("Game started: " + s2.getSessionId() +
                    " P1=" + s2.getPlayer1().getUsername() +
                    " P2=" + s2.getPlayer2().getUsername());

            GameResult result = s2.getGame().startDemoGame(); // plays until win/draw
            updateStats(s2, result);

            System.out.println("Result: " + result);
            System.out.println("Alice -> " + alice.getStats());
            System.out.println("Bob   -> " + bob.getStats());
        }

        // Private Room (2 players only)
        System.out.println("\n=== PRIVATE ROOM ===");
        PrivateRoomMatchmakingStrategy createRoom = new PrivateRoomMatchmakingStrategy(rooms, true, null);
        createRoom.findMatch(alice);
        String code = createRoom.getCreatedRoomCode();
        System.out.println("Room code: " + code);

        PrivateRoomMatchmakingStrategy joinRoom = new PrivateRoomMatchmakingStrategy(rooms, false, code);
        GameSession privateGame = joinRoom.findMatch(bob);
        if (privateGame != null) {
            System.out.println("Private game started: " + privateGame.getSessionId());

            GameResult result = privateGame.getGame().startDemoGame();
            updateStats(privateGame, result);

            System.out.println("Result: " + result);
            System.out.println("Alice -> " + alice.getStats());
            System.out.println("Bob   -> " + bob.getStats());
        }

        System.out.println("\n=== FINAL PROFILES ===");
        System.out.println("Alice -> " + alice.getStats());
        System.out.println("Bob   -> " + bob.getStats());
    }

    private static void updateStats(GameSession session, GameResult result) {
        User p1 = session.getPlayer1();
        User p2 = session.getPlayer2();

        if (result == GameResult.DRAW) {
            p1.getStats().recordDraw();
            p2.getStats().recordDraw();
            return;
        }

        if (result == GameResult.PLAYER1_WIN) {
            p1.getStats().recordWin();
            p2.getStats().recordLoss();
        } else {
            p2.getStats().recordWin();
            p1.getStats().recordLoss();
        }
    }

    // ============================ STATS ============================
    static class ProfileStats {
        private int gamesPlayed;
        private int wins;
        private int losses;
        private int draws;

        public void recordWin()  { gamesPlayed++; wins++; }
        public void recordLoss() { gamesPlayed++; losses++; }
        public void recordDraw() { gamesPlayed++; draws++; }

        public int getGamesPlayed() { return gamesPlayed; }

        public double getWinPercentage() {
            return gamesPlayed == 0 ? 0.0 : (wins * 100.0) / gamesPlayed;
        }

        @Override
        public String toString() {
            return "Games=" + gamesPlayed +
                    " W=" + wins +
                    " L=" + losses +
                    " D=" + draws +
                    " Win%=" + String.format(Locale.ROOT, "%.2f", getWinPercentage());
        }
    }

    // ============================ USER + AUTH ============================
    static class User {
        private final String id = UUID.randomUUID().toString();
        private final String username;
        private final String password;
        private final ProfileStats stats = new ProfileStats();

        public User(String username, String password) {
            this.username = Objects.requireNonNull(username);
            this.password = Objects.requireNonNull(password);
        }

        public String getId() { return id; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public ProfileStats getStats() { return stats; }
    }

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

    // ============================ MATCHMAKING ============================
    interface MatchmakingStrategy {
        GameSession findMatch(User user);
    }

    /**
     * 2-player only queue (exactly like chess).
     */
    static class RandomQueueMatchmakingStrategy implements MatchmakingStrategy {
        private final Queue<User> waiting = new ArrayDeque<>();

        @Override
        public synchronized GameSession findMatch(User user) {
            if (waiting.isEmpty()) {
                waiting.offer(user);
                return null;
            }
            User opponent = waiting.poll();
            return new GameSession(opponent, user); // opponent is Player1, user is Player2
        }
    }

    static class PrivateRoomService {
        private final Map<String, User> rooms = new ConcurrentHashMap<>();

        public String createRoom(User host) {
            String code = UUID.randomUUID().toString().substring(0, 8);
            rooms.put(code, host);
            return code;
        }

        public GameSession joinRoom(String code, User guest) {
            User host = rooms.remove(code);
            if (host == null) throw new IllegalArgumentException("Room not found: " + code);
            return new GameSession(host, guest);
        }
    }

    static class PrivateRoomMatchmakingStrategy implements MatchmakingStrategy {
        private final PrivateRoomService rooms;
        private final boolean create;
        private final String roomCodeToJoin;
        private String createdRoomCode;

        public PrivateRoomMatchmakingStrategy(PrivateRoomService rooms, boolean create, String roomCodeToJoin) {
            this.rooms = Objects.requireNonNull(rooms);
            this.create = create;
            this.roomCodeToJoin = roomCodeToJoin;
        }

        public String getCreatedRoomCode() { return createdRoomCode; }

        @Override
        public GameSession findMatch(User user) {
            if (create) {
                createdRoomCode = rooms.createRoom(user);
                return null; // waiting
            }
            if (roomCodeToJoin == null || roomCodeToJoin.isBlank()) {
                throw new IllegalArgumentException("Room code required to join");
            }
            return rooms.joinRoom(roomCodeToJoin, user);
        }
    }

    // ============================ SESSION ============================
    static class GameSession {
        private final String sessionId = UUID.randomUUID().toString();
        private final User player1;
        private final User player2;
        private final TicTacToeGame game;

        public GameSession(User player1, User player2) {
            this.player1 = Objects.requireNonNull(player1);
            this.player2 = Objects.requireNonNull(player2);
            this.game = new TicTacToeGame(player1.getUsername(), player2.getUsername(), 3);
        }

        public String getSessionId() { return sessionId; }
        public User getPlayer1() { return player1; }
        public User getPlayer2() { return player2; }
        public TicTacToeGame getGame() { return game; }
    }

    // ============================ TIC TAC TOE CORE ============================
    enum Symbol { X, O, EMPTY }
    enum GameStatus { IN_PROGRESS, WIN, DRAW }
    enum GameResult { PLAYER1_WIN, PLAYER2_WIN, DRAW }

    static class Cell {
        private Symbol symbol = Symbol.EMPTY;
        public Symbol getSymbol() { return symbol; }
        public void setSymbol(Symbol symbol) { this.symbol = Objects.requireNonNull(symbol); }
        public boolean isEmpty() { return symbol == Symbol.EMPTY; }
    }

    static class Board {
        private final int size;
        private final Cell[][] grid;
        private int movesCount;

        public Board(int size) {
            if (size < 3) throw new IllegalArgumentException("Board size must be >= 3");
            this.size = size;
            this.grid = new Cell[size][size];
            reset();
        }

        public int getSize() { return size; }

        public boolean isValidMove(int row, int col) {
            return row >= 0 && col >= 0 && row < size && col < size && grid[row][col].isEmpty();
        }

        public void placeMove(int row, int col, Symbol symbol) {
            grid[row][col].setSymbol(symbol);
            movesCount++; // important for DRAW
        }

        public boolean isFull() {
            return movesCount == size * size;
        }

        public boolean checkWin(Symbol s) {
            // rows
            for (int r = 0; r < size; r++) {
                boolean ok = true;
                for (int c = 0; c < size; c++) {
                    if (grid[r][c].getSymbol() != s) { ok = false; break; }
                }
                if (ok) return true;
            }
            // cols
            for (int c = 0; c < size; c++) {
                boolean ok = true;
                for (int r = 0; r < size; r++) {
                    if (grid[r][c].getSymbol() != s) { ok = false; break; }
                }
                if (ok) return true;
            }
            // diagonals
            boolean d1 = true, d2 = true;
            for (int i = 0; i < size; i++) {
                if (grid[i][i].getSymbol() != s) d1 = false;
                if (grid[i][size - 1 - i].getSymbol() != s) d2 = false;
            }
            return d1 || d2;
        }

        public void reset() {
            movesCount = 0;
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    if (grid[r][c] == null) grid[r][c] = new Cell();
                    grid[r][c].setSymbol(Symbol.EMPTY);
                }
            }
        }

        public void print() {
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    Symbol s = grid[r][c].getSymbol();
                    char ch = (s == Symbol.EMPTY) ? '.' : (s == Symbol.X ? 'X' : 'O');
                    System.out.print(ch + (c == size - 1 ? "" : " | "));
                }
                System.out.println();
                if (r != size - 1) {
                    System.out.println("---+---+---");
                }
            }
            System.out.println();
        }
    }

    static class TicTacToeGame {
        private final Board board;
        private final String player1Name;
        private final String player2Name;

        private int turn; // 0 => X (player1), 1 => O (player2)
        private GameStatus status;
        private Symbol winnerSymbol; // FIX: track winner explicitly

        public TicTacToeGame(String player1Name, String player2Name, int size) {
            this.player1Name = Objects.requireNonNull(player1Name);
            this.player2Name = Objects.requireNonNull(player2Name);
            this.board = new Board(size);
            this.turn = 0;
            this.status = GameStatus.IN_PROGRESS;
            this.winnerSymbol = Symbol.EMPTY;
        }

        public synchronized void playMove(int row, int col) {
            if (status != GameStatus.IN_PROGRESS) {
                throw new IllegalStateException("Game already finished.");
            }
            if (!board.isValidMove(row, col)) {
                throw new IllegalArgumentException("Invalid move.");
            }

            Symbol symbol = (turn == 0) ? Symbol.X : Symbol.O;
            board.placeMove(row, col, symbol);

            if (board.checkWin(symbol)) {
                status = GameStatus.WIN;
                winnerSymbol = symbol; // important
                return;
            }

            if (board.isFull()) {
                status = GameStatus.DRAW;
                return;
            }

            turn = 1 - turn;
        }

        public GameStatus getStatus() { return status; }

        public void printBoard() { board.print(); }

        /**
         * Plays automatically until WIN or DRAW.
         * Picks random empty cells and plays valid moves.
         */
        public GameResult startDemoGame() {
            Random rnd = new Random();
            while (status == GameStatus.IN_PROGRESS) {
                int r, c;
                // find a valid empty cell
                do {
                    r = rnd.nextInt(board.getSize());
                    c = rnd.nextInt(board.getSize());
                } while (!board.isValidMove(r, c));

                playMove(r, c);
                // optional board print each move (comment if you don't want logs)
                // printBoard();
            }

            if (status == GameStatus.DRAW) return GameResult.DRAW;

            // WIN case: decide by winnerSymbol (not by turn)
            return (winnerSymbol == Symbol.X) ? GameResult.PLAYER1_WIN : GameResult.PLAYER2_WIN;
        }

        @Override
        public String toString() {
            String next = (turn == 0) ? player1Name + "(X)" : player2Name + "(O)";
            return "TicTacToeGame{status=" + status + ", next=" + next + "}";
        }
    }
}
