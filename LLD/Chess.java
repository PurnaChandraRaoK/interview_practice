public enum Color {
    WHITE, BLACK
}

public class InvalidMoveException extends RuntimeException {
    public InvalidMoveException(final String message) {
        super(message);
    }
}

public class Move {
    private final Cell start;
    private final Cell end;

    public Move(Cell start, Cell end) {
        this.start = start;
        this.end = end;
    }

    public Cell getStart() { return start; }
    public Cell getEnd() { return end; }
}

public class Cell {
    private final int row, col;
    private Piece piece;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public boolean isOccupied() { return piece != null; }

    public Piece getPiece() { return piece; }
    public void setPiece(Piece piece) { this.piece = piece; }

    public int getRow() { return row; }
    public int getCol() { return col; }
}

public abstract class Piece {
    protected final Color color;

    public Piece(Color color) {
        this.color = color;
    }

    public abstract boolean isValidMove(Board board, Cell from, Cell to);

    public Color getColor() {
        return color;
    }
}

public class Bishop extends Piece {
    public Bishop(Color color) { super(color); }

    @Override
    public boolean isValidMove(Board board, Cell from, Cell to) {
        int rowDiff = Math.abs(to.getRow() - from.getRow());
        int colDiff = Math.abs(to.getCol() - from.getCol());
        return rowDiff == colDiff;
    }
}

public class Rook extends Piece {
    public Rook(Color color) { super(color); }

    @Override
    public boolean isValidMove(Board board, Cell from, Cell to) {
        return (from.getRow() == to.getRow() || from.getCol() == to.getCol());
    }
}

public class Queen extends Piece {
    public Queen(Color color) { super(color); }

    @Override
    public boolean isValidMove(Board board, Cell from, Cell to) {
        int rowDiff = Math.abs(to.getRow() - from.getRow());
        int colDiff = Math.abs(to.getCol() - from.getCol());
        return (rowDiff == colDiff) || (from.getRow() == to.getRow()) || (from.getCol() == to.getCol());
    }
}

public class Knight extends Piece {
    public Knight(Color color) { super(color); }

    @Override
    public boolean isValidMove(Board board, Cell from, Cell to) {
        int rowDiff = Math.abs(to.getRow() - from.getRow());
        int colDiff = Math.abs(to.getCol() - from.getCol());
        return (rowDiff == 2 && colDiff == 1) || (rowDiff == 1 && colDiff == 2);
    }
}

public class King extends Piece {
    public King(Color color) { super(color); }

    @Override
    public boolean isValidMove(Board board, Cell from, Cell to) {
        int rowDiff = Math.abs(to.getRow() - from.getRow());
        int colDiff = Math.abs(to.getCol() - from.getCol());
        return (rowDiff <= 1 && colDiff <= 1);
    }
}

public class Pawn extends Piece {
    public Pawn(Color color) { super(color); }

    @Override
    public boolean isValidMove(Board board, Cell from, Cell to) {
        int rowDiff = to.getRow() - from.getRow();
        int colDiff = Math.abs(to.getCol() - from.getCol());

        if (color == Color.WHITE) {
            return (rowDiff == 1 && colDiff == 0) ||
                   (from.getRow() == 1 && rowDiff == 2 && colDiff == 0) ||
                   (rowDiff == 1 && colDiff == 1);
        } else {
            return (rowDiff == -1 && colDiff == 0) ||
                   (from.getRow() == 6 && rowDiff == -2 && colDiff == 0) ||
                   (rowDiff == -1 && colDiff == 1);
        }
    }
}

public class Board {
    private final Cell[][] board;

    public Board() {
        board = new Cell[8][8];
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                board[r][c] = new Cell(r, c);

        setupPieces();
    }

    public Cell getCell(int row, int col) {
        if (!inBounds(row, col)) throw new IllegalArgumentException("Out of bounds");
        return board[row][col];
    }

    public Piece getPiece(int row, int col) {
        if (!inBounds(row, col)) return null;
        return board[row][col].getPiece();
    }

    public void setPiece(int row, int col, Piece piece) {
        board[row][col].setPiece(piece);
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < 8 && c >= 0 && c < 8;
    }

    private void setupPieces() {
        // pawns
        for (int j = 0; j < 8; j++) {
            board[1][j].setPiece(new Pawn(Color.WHITE));
            board[6][j].setPiece(new Pawn(Color.BLACK));
        }

        // white
        board[0][0].setPiece(new Rook(Color.WHITE));
        board[0][1].setPiece(new Knight(Color.WHITE));
        board[0][2].setPiece(new Bishop(Color.WHITE));
        board[0][3].setPiece(new Queen(Color.WHITE));
        board[0][4].setPiece(new King(Color.WHITE));
        board[0][5].setPiece(new Bishop(Color.WHITE));
        board[0][6].setPiece(new Knight(Color.WHITE));
        board[0][7].setPiece(new Rook(Color.WHITE));

        // black
        board[7][0].setPiece(new Rook(Color.BLACK));
        board[7][1].setPiece(new Knight(Color.BLACK));
        board[7][2].setPiece(new Bishop(Color.BLACK));
        board[7][3].setPiece(new Queen(Color.BLACK));
        board[7][4].setPiece(new King(Color.BLACK));
        board[7][5].setPiece(new Bishop(Color.BLACK));
        board[7][6].setPiece(new Knight(Color.BLACK));
        board[7][7].setPiece(new Rook(Color.BLACK));
    }

    // keep your original signature (backward compatible)
    public synchronized boolean movePiece(Move move) {
        Cell from = move.getStart();
        Cell to = move.getEnd();
        Piece piece = from.getPiece();
        if (piece == null) return false;
        applyMove(move);
        return true;
    }

    private static class Snapshot {
        final Cell from;
        final Cell to;
        final Piece moved;
        final Piece captured;
        Snapshot(Cell from, Cell to, Piece moved, Piece captured) {
            this.from = from; this.to = to; this.moved = moved; this.captured = captured;
        }
    }

    private Snapshot applyMove(Move move) {
        Cell from = move.getStart();
        Cell to = move.getEnd();
        Piece moved = from.getPiece();
        Piece captured = to.getPiece();

        to.setPiece(moved);
        from.setPiece(null);
        return new Snapshot(from, to, moved, captured);
    }

    private void undoMove(Snapshot snap) {
        snap.from.setPiece(snap.moved);
        snap.to.setPiece(snap.captured);
    } 

    public boolean isCheckmate(Color color) {
        return isInCheck(color) && !hasAnyLegalMove(color);
    }

    public boolean isStalemate(Color color) {
        return !isInCheck(color) && !hasAnyLegalMove(color);
    }
}

public class Player {
    private final Color color;

    public Player(Color color) {
        this.color = color;
    }

    public Color getColor() { return color; }
}

public enum GameResult {
    WHITE_WIN, BLACK_WIN, DRAW, ONGOING
}

import java.util.Scanner;

public class ChessGame {
    private final Board board;
    private final Player whitePlayer, blackPlayer;
    private Player currentPlayer;

    public ChessGame() {
        board = new Board();
        whitePlayer = new Player(Color.WHITE);
        blackPlayer = new Player(Color.BLACK);
        currentPlayer = whitePlayer;
    }

    public GameResult start() {
        while (!isGameOver()) {
            System.out.println(currentPlayer.getColor() + "'s turn.");

            Move move = getPlayerMove(currentPlayer);

            boolean ok = board.movePiece(move, currentPlayer.getColor());
            if (!ok) {
                System.out.println("Invalid move. Try again.");
                continue;
            }

            switchTurn();
        }

        return getResult();
    }

    private void switchTurn() {
        currentPlayer = (currentPlayer == whitePlayer) ? blackPlayer : whitePlayer;
    }

    private boolean isGameOver() {
        return board.isCheckmate(Color.WHITE) || board.isCheckmate(Color.BLACK) ||
               board.isStalemate(Color.WHITE) || board.isStalemate(Color.BLACK);
    }

    private GameResult getResult() {
        if (board.isCheckmate(Color.WHITE)) return GameResult.BLACK_WIN;
        if (board.isCheckmate(Color.BLACK)) return GameResult.WHITE_WIN;
        if (board.isStalemate(Color.WHITE) || board.isStalemate(Color.BLACK)) return GameResult.DRAW;
        return GameResult.ONGOING;
    }

    private Move getPlayerMove(Player player) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter source row: ");
        int sourceRow = scanner.nextInt();
        System.out.print("Enter source column: ");
        int sourceCol = scanner.nextInt();
        System.out.print("Enter destination row: ");
        int destRow = scanner.nextInt();
        System.out.print("Enter destination column: ");
        int destCol = scanner.nextInt();

        Piece piece = board.getPiece(sourceRow, sourceCol);
        if (piece == null || piece.getColor() != player.getColor()) {
            throw new IllegalArgumentException("Invalid piece selection!");
        }

        return new Move(board.getCell(sourceRow, sourceCol), board.getCell(destRow, destCol));
    }
}

public class ProfileStats {
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
        return "Games=" + gamesPlayed + ", W=" + wins + ", L=" + losses + ", D=" + draws +
               ", Win%=" + String.format("%.2f", getWinPercentage());
    }
}

public class User {
    private final String id = UUID.randomUUID().toString();
    private final String username;
    private final String password;
    private final ProfileStats stats = new ProfileStats();

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public ProfileStats getStats() { return stats; }
}

public class AuthService {
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public User register(String username, String password) {
        if (users.containsKey(username)) throw new IllegalArgumentException("User already exists");
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

public class GameSession {
    private final String sessionId;
    private final User whiteUser;
    private final User blackUser;
    private final ChessGame game;

    public GameSession(String sessionId, User whiteUser, User blackUser) {
        this.sessionId = sessionId;
        this.whiteUser = whiteUser;
        this.blackUser = blackUser;
        this.game = new ChessGame();
    }

    public String getSessionId() { return sessionId; }
    public User getWhiteUser() { return whiteUser; }
    public User getBlackUser() { return blackUser; }
    public ChessGame getGame() { return game; }
}

public interface MatchmakingStrategy {
    GameSession findMatch(User user); // returns session when matched, else null
}

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

public class RandomQueueMatchmakingStrategy implements MatchmakingStrategy {
    private final Queue<User> waiting = new ArrayDeque<>();

    @Override
    public synchronized GameSession findMatch(User user) {
        if (!waiting.isEmpty()) {
            User opponent = waiting.poll();
            // Decide colors simply: requester is WHITE
            return new GameSession(UUID.randomUUID().toString(), user, opponent);
        }
        waiting.offer(user);
        return null;
    }
}

public class PrivateRoomMatchmakingStrategy implements MatchmakingStrategy {
    private final PrivateRoomService rooms;
    private final String roomCode;  // null means create, non-null means join
    private final boolean create;

    public PrivateRoomMatchmakingStrategy(PrivateRoomService rooms, boolean create, String roomCode) {
        this.rooms = rooms;
        this.create = create;
        this.roomCode = roomCode;
    }

    @Override
    public GameSession findMatch(User user) {
        if (create) {
            String code = rooms.createRoom(user);
            System.out.println("Share this room code with friend: " + code);
            return null; // not matched until someone joins
        }
        return rooms.joinRoom(roomCode, user);
    }
}

public class PrivateRoomService {
    private static class Room {
        final String code;
        final User host;
        User guest;
        Room(String code, User host) { this.code = code; this.host = host; }
    }

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public String createRoom(User host) {
        String code = UUID.randomUUID().toString().substring(0, 8);
        rooms.put(code, new Room(code, host));
        return code;
    }

    public GameSession joinRoom(String code, User guest) {
        Room room = rooms.get(code);
        if (room == null) throw new IllegalArgumentException("Room not found");
        if (room.guest != null) throw new IllegalStateException("Room already full");

        room.guest = guest;
        rooms.remove(code);

        return new GameSession(UUID.randomUUID().toString(), room.host, guest);
    }
}

public class ChessPlatform {
    private final AuthService authService = new AuthService();
    private final RandomQueueMatchmaking queue = new RandomQueueMatchmaking();
    private final PrivateRoomService privateRoomService = new PrivateRoomService();

    MatchmakingStrategy strategy;

    public void run() {
        Scanner sc = new Scanner(System.in);

        System.out.println("1) Register  2) Login");
        int choice = sc.nextInt();
        sc.nextLine();

        System.out.print("Username: ");
        String u = sc.nextLine();
        System.out.print("Password: ");
        String p = sc.nextLine();

        User user;
        if (choice == 1) user = authService.register(u, p);
        else user = authService.login(u, p);

        System.out.println("Login successful!");
        System.out.println("Profile: " + user.getStats());

        System.out.println("\n1) Play Random (Queue)  2) Create Private Room  3) Join Private Room");
        int mode = sc.nextInt();
        sc.nextLine();

        GameSession session = null;
        
        if (mode == 1) {
            strategy = new RandomQueueMatchmakingStrategy();
        } else if (mode == 2) {
            strategy = new PrivateRoomMatchmakingStrategy(privateRoomService, true, null);
        } else {
            System.out.print("Enter room code: ");
            String code = sc.nextLine();
            strategy = new PrivateRoomMatchmakingStrategy(privateRoomService, false, code);
        }
        
        while (session == null) {
            session = strategy.findMatch(user);

            if (session == null) {
                System.out.println("Waiting for opponent...");
                try {
                    Thread.sleep(1500); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // Start game and update stats
        System.out.println("\nGame started: " + session.getSessionId());
        System.out.println("White=" + session.getWhiteUser().getUsername() +
                           ", Black=" + session.getBlackUser().getUsername());

        GameResult result = session.getGame().start();
        updateStats(session, result);

        System.out.println("\nGame finished: " + result);
        System.out.println("White profile: " + session.getWhiteUser().getStats());
        System.out.println("Black profile: " + session.getBlackUser().getStats());
    }

    private void updateStats(GameSession session, GameResult result) {
        User white = session.getWhiteUser();
        User black = session.getBlackUser();

        if (result == GameResult.WHITE_WIN) {
            white.getStats().recordWin();
            black.getStats().recordLoss();
        } else if (result == GameResult.BLACK_WIN) {
            black.getStats().recordWin();
            white.getStats().recordLoss();
        } else if (result == GameResult.DRAW) {
            white.getStats().recordDraw();
            black.getStats().recordDraw();
        }
    }
}

public class ChessGameDemo {
    public static void main(String[] args) {
        new ChessPlatform().run();
    }
}