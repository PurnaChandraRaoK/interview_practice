public class TrafficSignalSystem {

    public enum Direction { NORTH, SOUTH, EAST, WEST }
    public enum Phase { GREEN, YELLOW, RED }

    public interface SignalState {
        Phase phase();
        void onEnter(TrafficLight light, TrafficSignalController controller);
        default String getName() { return phase().name(); }
    }

    public static final class GreenState implements SignalState {
        private static final GreenState INSTANCE = new GreenState();
        private GreenState() {}
        public static GreenState instance() { return INSTANCE; }
        @Override public Phase phase() { return Phase.GREEN; }
        @Override public void onEnter(TrafficLight light, TrafficSignalController controller) {
            controller.log(light.getDirection(), "State=GREEN");
            Duration d = controller.getDuration(light.getDirection(), this);
            controller.scheduleStateChange(light.getDirection(), YellowState.instance(), d);
        }
    }

    public static final class YellowState implements SignalState {
        private static final YellowState INSTANCE = new YellowState();
        private YellowState() {}
        public static YellowState instance() { return INSTANCE; }
        @Override public Phase phase() { return Phase.YELLOW; }
        @Override public void onEnter(TrafficLight light, TrafficSignalController controller) {
            controller.log(light.getDirection(), "State=YELLOW");
            Duration d = controller.getDuration(light.getDirection(), this);
            controller.scheduleStateChange(light.getDirection(), RedState.instance(), d);
        }
    }

    public static final class RedState implements SignalState {
        private static final RedState INSTANCE = new RedState();
        private RedState() {}
        public static RedState instance() { return INSTANCE; }
        @Override public Phase phase() { return Phase.RED; }
        @Override public void onEnter(TrafficLight light, TrafficSignalController controller) {
            controller.log(light.getDirection(), "State=RED");
            Duration d = controller.getDuration(light.getDirection(), this);
            Direction next = controller.getNextDirection(light.getDirection());
            controller.scheduleActivation(next, d);
        }
    }

    public static final class TrafficLight {
        private final Direction direction;
        private volatile SignalState state;

        public TrafficLight(Direction direction) {
            this.direction = Objects.requireNonNull(direction);
            this.state = RedState.instance();
        }

        public Direction getDirection() { return direction; }
        public SignalState getState() { return state; }

        public void setState(SignalState state) { this.state = Objects.requireNonNull(state); }
        public void enterState(TrafficSignalController controller) { state.onEnter(this, controller); }
    }

    public static final class TrafficSignalController {
        private final EnumMap<Direction, TrafficLight> signals;
        private final EnumMap<Direction, Map<String, Integer>> durationsSeconds;
        private final ScheduledExecutorService scheduler;
        private final EnumMap<Direction, ScheduledFuture<?>> scheduled = new EnumMap<>(Direction.class);

        public TrafficSignalController(EnumMap<Direction, TrafficLight> signals,
                                       EnumMap<Direction, Map<String, Integer>> durationsSeconds) {
            this.signals = new EnumMap<>(Direction.class);
            this.signals.putAll(Objects.requireNonNull(signals));

            this.durationsSeconds = new EnumMap<>(Direction.class);
            this.durationsSeconds.putAll(Objects.requireNonNull(durationsSeconds));

            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TrafficSignalScheduler");
                t.setDaemon(true);
                return t;
            });
        }

        public void start(Direction startDirection) {
            for (TrafficLight l : signals.values()) l.setState(RedState.instance());
            activate(startDirection);
        }

        private void activate(Direction direction) {
            cancel(direction);
            TrafficLight light = signals.get(direction);
            if (light == null) throw new IllegalArgumentException("No light configured for " + direction);
            light.setState(GreenState.instance());
            light.enterState(this);
        }

        public void scheduleStateChange(Direction direction, SignalState nextState, Duration delay) {
            Objects.requireNonNull(nextState);
            Objects.requireNonNull(delay);
            cancel(direction);

            ScheduledFuture<?> f = scheduler.schedule(() -> {
                TrafficLight light = signals.get(direction);
                if (light == null) return;
                light.setState(nextState);
                light.enterState(this);
            }, Math.max(0, delay.toSeconds()), TimeUnit.SECONDS);

            scheduled.put(direction, f);
        }

        public void scheduleActivation(Direction direction, Duration delay) {
            Objects.requireNonNull(direction);
            Objects.requireNonNull(delay);
            cancel(direction);

            ScheduledFuture<?> f = scheduler.schedule(() -> activate(direction),
                    Math.max(0, delay.toSeconds()), TimeUnit.SECONDS);

            scheduled.put(direction, f);
        }

        public Duration getDuration(Direction direction, SignalState state) {
            Map<String, Integer> perState = durationsSeconds.get(direction);
            if (perState == null) throw new IllegalStateException("Missing durations for " + direction);
            Integer sec = perState.get(state.getName());
            if (sec == null) throw new IllegalStateException("Missing duration for " + direction + ":" + state.getName());
            return Duration.ofSeconds(sec);
        }

        public Direction getNextDirection(Direction current) {
            Direction[] dirs = Direction.values();
            return dirs[(current.ordinal() + 1) % dirs.length];
        }

        public void shutdown() {
            for (Direction d : Direction.values()) cancel(d);
            scheduler.shutdownNow();
        }

        private void cancel(Direction d) {
            ScheduledFuture<?> f = scheduled.remove(d);
            if (f != null) f.cancel(false);
        }

        public void log(Direction direction, String msg) {
            System.out.println("Direction=" + direction + " | " + msg);
        }
    }

    public static final class Intersection {
        private final String id;
        private final EnumMap<Direction, TrafficLight> signals;
        private final TrafficSignalController controller;

        public Intersection(String id,
                            EnumMap<Direction, TrafficLight> signals,
                            EnumMap<Direction, Map<String, Integer>> durationsSeconds) {
            this.id = Objects.requireNonNull(id);
            this.signals = new EnumMap<>(Direction.class);
            this.signals.putAll(Objects.requireNonNull(signals));
            this.controller = new TrafficSignalController(this.signals, Objects.requireNonNull(durationsSeconds));
        }

        public void start(Direction startDirection) { controller.start(startDirection); }
        public void shutdown() { controller.shutdown(); }
        public String getId() { return id; }
        public TrafficLight getSignal(Direction d) { return signals.get(d); }
    }

    public static void main(String[] args) throws Exception {
        EnumMap<Direction, Map<String, Integer>> durations = new EnumMap<>(Direction.class);
        durations.put(Direction.NORTH, map("GREEN", 4, "YELLOW", 2, "RED", 3));
        durations.put(Direction.SOUTH, map("GREEN", 3, "YELLOW", 2, "RED", 4));
        durations.put(Direction.EAST,  map("GREEN", 5, "YELLOW", 2, "RED", 3));
        durations.put(Direction.WEST,  map("GREEN", 2, "YELLOW", 2, "RED", 5));

        EnumMap<Direction, TrafficLight> signals = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) signals.put(d, new TrafficLight(d));

        Intersection intersection = new Intersection("I1", signals, durations);
        intersection.start(Direction.NORTH);

        Thread.sleep(60_000);
        intersection.shutdown();
    }

    private static Map<String, Integer> map(String k1, int v1, String k2, int v2, String k3, int v3) {
        Map<String, Integer> m = new HashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        m.put(k3, v3);
        return m;
    }
}
