public class SnakeGameMain {

    enum Direction { U, D, L, R }

    // Configurable food categories (extendable)
    enum FoodType {
        NORMAL(1),
        BONUS(3);

        private final int points;
        FoodType(int points) { this.points = points; }
        public int points() { return points; }
    }

    // Immutable position (Java 16+). If on Java 8, replace with a class + equals/hashCode.
    record Position(int row, int col) {}

    record Food(Position pos, FoodType type) {}

    interface MovementStrategy {
        Position next(Position currentHead, Direction direction);
    }

    static class HumanMovementStrategy implements MovementStrategy {
        @Override
        public Position next(Position head, Direction dir) {
            return switch (dir) {
                case U -> new Position(head.row() - 1, head.col());
                case D -> new Position(head.row() + 1, head.col());
                case L -> new Position(head.row(), head.col() - 1);
                case R -> new Position(head.row(), head.col() + 1);
            };
        }
    }

    // Optional stub to show extensibility (not used in demo)
    static class AIMovementStrategy implements MovementStrategy {
        @Override
        public Position next(Position currentHead, Direction direction) {
            // out of scope for interview demo
            return currentHead;
        }
    }

    interface GameObserver {
        default void onMoveMade(Position newHead) {}
        default void onFoodEaten(Food food, int totalScore) {}
        default void onGameOver(int finalScore) {}
    }

    static class ConsoleGameObserver implements GameObserver {
        @Override public void onMoveMade(Position newHead) {
            System.out.println("Snake moved to: (" + newHead.row() + "," + newHead.col() + ")");
        }
        @Override public void onFoodEaten(Food food, int totalScore) {
            System.out.println("Food eaten: " + food.type() + " +" + food.type().points()
                    + " | Score: " + totalScore);
        }
        @Override public void onGameOver(int finalScore) {
            System.out.println("Game Over! Final score: " + finalScore);
        }
    }

    static class SnakeGame {
        private final int width;
        private final int height;
        private final List<Food> foods;
        private int foodIndex = 0;

        private final Deque<Position> snake = new ArrayDeque<>();
        private final Set<Position> occupied = new HashSet<>();

        private MovementStrategy movementStrategy = new HumanMovementStrategy();
        private final List<GameObserver> observers = new ArrayList<>();

        private int score = 0;

        public SnakeGame(int width, int height, List<Food> foods) {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid board size");
            this.width = width;
            this.height = height;
            this.foods = (foods == null) ? List.of() : foods;

            Position start = new Position(0, 0);
            snake.addFirst(start);
            occupied.add(start);
        }

        public void setMovementStrategy(MovementStrategy strategy) {
            if (strategy == null) throw new IllegalArgumentException("strategy cannot be null");
            this.movementStrategy = strategy;
        }

        public void addObserver(GameObserver observer) {
            if (observer != null) observers.add(observer);
        }

        public int getScore() { return score; }

        public Position getHead() { return snake.peekFirst(); }

        public Food getNextFood() {
            return (foodIndex < foods.size()) ? foods.get(foodIndex) : null;
        }

        /**
         * @return current score, or -1 if game over
         */
        public int move(Direction direction) {
            Position head = snake.peekFirst();
            Position newHead = movementStrategy.next(head, direction);

            // Boundary check
            boolean crossesBoundary =
                    newHead.row() < 0 || newHead.row() >= height ||
                    newHead.col() < 0 || newHead.col() >= width;

            // Tail exception: moving into current tail is allowed ONLY if not eating food
            Position currentTail = snake.peekLast();
            boolean willEatFood = willEatFoodAt(newHead);

            boolean hitsBody = occupied.contains(newHead);
            boolean hitsTailCell = newHead.equals(currentTail);
            boolean bitesItself = hitsBody && !(hitsTailCell && !willEatFood);

            if (crossesBoundary || bitesItself) {
                notifyGameOver(score);
                return -1;
            }

            if (!willEatFood) {
                // normal move: remove tail
                Position tail = snake.removeLast();
                occupied.remove(tail);
            } else {
                // eat: score increases by food points, snake grows (tail not removed)
                Food eaten = foods.get(foodIndex);
                score += eaten.type().points();
                foodIndex++;
                notifyFoodEaten(eaten, score);
            }

            // add new head
            snake.addFirst(newHead);
            occupied.add(newHead);
            notifyMoveMade(newHead);

            return score;
        }

        private boolean willEatFoodAt(Position pos) {
            if (foodIndex >= foods.size()) return false;
            Food next = foods.get(foodIndex);
            return next.pos().equals(pos);
        }

        private void notifyMoveMade(Position newHead) {
            for (GameObserver o : observers) o.onMoveMade(newHead);
        }

        private void notifyFoodEaten(Food food, int totalScore) {
            for (GameObserver o : observers) o.onFoodEaten(food, totalScore);
        }

        private void notifyGameOver(int finalScore) {
            for (GameObserver o : observers) o.onGameOver(finalScore);
        }
    }

    public static void main(String[] args) {
        int width = 20, height = 15;

        // Configurable foods: position + type
        List<Food> foods = List.of(
                new Food(new Position(5, 5), FoodType.NORMAL),
                new Food(new Position(10, 8), FoodType.BONUS),
                new Food(new Position(3, 12), FoodType.NORMAL),
                new Food(new Position(8, 17), FoodType.BONUS),
                new Food(new Position(12, 3), FoodType.NORMAL)
        );

        SnakeGame game = new SnakeGame(width, height, foods);
        game.addObserver(new ConsoleGameObserver());

        System.out.println("==== SNAKE GAME ====");
        System.out.println("Controls: W (Up), S (Down), A (Left), D (Right), Q (Quit)");
        System.out.println("Food: NORMAL=+1, BONUS=+3");
        System.out.println("====================");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                displayGameState(game);
                System.out.print("Enter move (W/A/S/D) or Q to quit: ");
                String input = scanner.nextLine().trim().toUpperCase();

                if ("Q".equals(input)) {
                    System.out.println("Game ended by player. Final score: " + game.getScore());
                    break;
                }

                Direction dir = convertInput(input);
                if (dir == null) {
                    System.out.println("Invalid input! Use W/A/S/D or Q.");
                    continue;
                }

                int score = game.move(dir);
                if (score == -1) break; // game over already printed by observer
            }
        }

        System.out.println("Thanks for playing!");
    }

    private static Direction convertInput(String input) {
        return switch (input) {
            case "W" -> Direction.U;
            case "S" -> Direction.D;
            case "A" -> Direction.L;
            case "D" -> Direction.R;
            default -> null;
        };
    }

    private static void displayGameState(SnakeGame game) {
        Position head = game.getHead();
        Food next = game.getNextFood();
        System.out.println("\nScore: " + game.getScore()
                + " | Head: (" + head.row() + "," + head.col() + ")"
                + (next != null ? " | NextFood: " + next.type() + " at (" + next.pos().row() + "," + next.pos().col() + ")" : " | No more food"));
    }
}
