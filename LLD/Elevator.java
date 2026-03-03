public class ElevatorSystem {
    static final class Config {
        private Config() {}

        static final int MIN_FLOOR = 1;
        static final int MAX_FLOOR = 5;

        static final int MAX_PERSONS = 10; // ONLY constraint requested
    }

    enum Direction { UP, DOWN, IDLE, STOP }
    enum SwitchDirection { UP, DOWN }
    enum Location { INSIDE_ELEVATOR, OUTSIDE_ELEVATOR }

    // ========================= REQUEST =========================
    static final class Request {
        private final int currentFloor;     // pickup floor for outside; origin for inside
        private final int desiredFloor;     // destination / stop
        private final Direction direction;
        private final Location location;

        // Group size trying to board at OUTSIDE pickup
        private final int groupPersons;

        public Request(int currentFloor, int desiredFloor, Direction direction, Location location) {
            this(currentFloor, desiredFloor, direction, location, 0);
        }

        public Request(int currentFloor, int desiredFloor, Direction direction, Location location, int groupPersons) {
            this.currentFloor = currentFloor;
            this.desiredFloor = desiredFloor;
            this.direction = direction;
            this.location = location;
            this.groupPersons = groupPersons;
        }

        public int getCurrentFloor() { return currentFloor; }
        public int getDesiredFloor() { return desiredFloor; }
        public Direction getDirection() { return direction; }
        public Location getLocation() { return location; }
        public int getGroupPersons() { return groupPersons; }
    }

    // ========================= ELEVATOR STATE =========================
    static final class ElevatorState {
        private Direction direction = Direction.IDLE;
        private int currentFloor = Config.MIN_FLOOR;

        private final Set<Integer> pressedFloors = new HashSet<>();

        // Occupancy (ONLY persons)
        private int currentPersons = 0;

        public Direction getDirection() { return direction; }
        public void setDirection(Direction direction) { this.direction = direction; }

        public int getCurrentFloor() { return currentFloor; }
        public void setCurrentFloor(int currentFloor) { this.currentFloor = currentFloor; }

        public Set<Integer> getPressedFloors() { return pressedFloors; }

        /** returns true if newly added (not already present) */
        public boolean pressFloor(int floor) { return pressedFloors.add(floor); }

        public void clearPressedAtCurrentFloor() { pressedFloors.remove(currentFloor); }

        public int getCurrentPersons() { return currentPersons; }

        public boolean canBoard(int persons) {
            return currentPersons + persons <= Config.MAX_PERSONS;
        }

        public void board(int persons) {
            currentPersons += persons;
        }
    }

    // ========================= ARRIVAL LISTENER =========================
    interface ArrivalListener {
        void onArrive(Elevator elevator, Request request);
    }

    // ========================= ELEVATOR =========================
    static final class Elevator {
        private static final AtomicInteger ID_GEN = new AtomicInteger(1);

        private final int id;
        private final ElevatorState state;

        private boolean fanOn;
        private boolean lightsOn;
        private boolean alarmOn;

        private final PriorityQueue<Request> upQ;
        private final PriorityQueue<Request> downQ;

        private ArrivalListener arrivalListener;

        public Elevator(int initialFloor) {
            validateFloor(initialFloor);
            this.id = ID_GEN.getAndIncrement();
            this.state = new ElevatorState();
            this.state.setCurrentFloor(initialFloor);
            this.state.setDirection(Direction.IDLE);

            this.upQ = new PriorityQueue<>(Comparator.comparingInt(Request::getDesiredFloor));
            this.downQ = new PriorityQueue<>((a, b) -> Integer.compare(b.getDesiredFloor(), a.getDesiredFloor()));
        }

        public int getId() { return id; }
        public ElevatorState getState() { return state; }

        public boolean isFanOn() { return fanOn; }
        public void setFanOn(boolean fanOn) { this.fanOn = fanOn; }

        public boolean isLightsOn() { return lightsOn; }
        public void setLightsOn(boolean lightsOn) { this.lightsOn = lightsOn; }

        public boolean isAlarmOn() { return alarmOn; }
        public void setAlarmOn(boolean alarmOn) { this.alarmOn = alarmOn; }

        public void setArrivalListener(ArrivalListener listener) {
            this.arrivalListener = listener;
        }

        public void addRequest(Request req) {
            validateRequest(req);
            enqueue(req);
            log("Queued " + req);
        }

        private void enqueue(Request req) {
            Direction dir = req.getDirection();
            if (dir == Direction.DOWN) downQ.offer(req);
            else upQ.offer(req); // UP + IDLE => upQ for simplicity
        }

        public void runUntilIdle() {
            while (!upQ.isEmpty() || !downQ.isEmpty()) {

                if (state.getDirection() == Direction.STOP) {
                    log("Emergency STOP -> clearing all pending requests.");
                    upQ.clear();
                    downQ.clear();
                    state.setDirection(Direction.IDLE);
                    break;
                }

                processRequests();
            }

            state.setDirection(Direction.IDLE);
            log("Finished requests. IDLE at floor " + state.getCurrentFloor()
                    + " | persons=" + state.getCurrentPersons());
        }

        private void processRequests() {
            Direction d = state.getDirection();
            if (d == Direction.UP || d == Direction.IDLE) {
                processUp();
                processDown();
            } else if (d == Direction.DOWN) {
                processDown();
                processUp();
            }
        }

        private void processUp() {
            if (upQ.isEmpty()) {
                state.setDirection(downQ.isEmpty() ? Direction.IDLE : Direction.DOWN);
                return;
            }

            state.setDirection(Direction.UP);

            while (!upQ.isEmpty()) {
                Request r = upQ.poll();
                moveTo(r.getDesiredFloor());
                log("Up -> stopped at floor " + state.getCurrentFloor());

                // clear inside pressed floor when reached
                state.clearPressedAtCurrentFloor();

                if (arrivalListener != null) arrivalListener.onArrive(this, r);
            }

            state.setDirection(downQ.isEmpty() ? Direction.IDLE : Direction.DOWN);
        }

        private void processDown() {
            if (downQ.isEmpty()) {
                state.setDirection(upQ.isEmpty() ? Direction.IDLE : Direction.UP);
                return;
            }

            state.setDirection(Direction.DOWN);

            while (!downQ.isEmpty()) {
                Request r = downQ.poll();
                moveTo(r.getDesiredFloor());
                log("Down -> stopped at floor " + state.getCurrentFloor());

                // clear inside pressed floor when reached
                state.clearPressedAtCurrentFloor();

                if (arrivalListener != null) arrivalListener.onArrive(this, r);
            }

            state.setDirection(upQ.isEmpty() ? Direction.IDLE : Direction.UP);
        }

        private void moveTo(int floor) {
            validateFloor(floor);
            state.setCurrentFloor(floor);
        }

        private void log(String msg) {
            System.out.println("[Elevator-" + id + "] " + msg);
        }
    }

    // ========================= FLOOR SWITCH =========================
    static final class FloorSwitch {
        private final int floor;
        private boolean pressedUp;
        private boolean pressedDown;

        public FloorSwitch(int floor) {
            validateFloor(floor);
            this.floor = floor;
        }

        public int getFloor() { return floor; }

        public boolean isPressedUp() { return pressedUp; }
        public void setPressedUp(boolean pressedUp) { this.pressedUp = pressedUp; }

        public boolean isPressedDown() { return pressedDown; }
        public void setPressedDown(boolean pressedDown) { this.pressedDown = pressedDown; }

        @Override
        public String toString() {
            return "FloorSwitch{floor=" + floor + ", up=" + pressedUp + ", down=" + pressedDown + "}";
        }
    }

    // ========================= STRATEGY =========================
    interface ElevatorSelectionStrategy {
        Elevator selectElevator(List<Elevator> elevators, int requestFloor, Direction requestDirection, int groupPersons);
    }

    // Nearest elevator; prefer moving-in-same-direction "on the way", else nearest idle.
    static final class NearestElevatorStrategy implements ElevatorSelectionStrategy {
        @Override
        public Elevator selectElevator(List<Elevator> elevators, int requestFloor, Direction requestDirection, int groupPersons) {
            Elevator bestMoving = null;
            int bestMovingDist = Integer.MAX_VALUE;

            Elevator bestIdle = null;
            int bestIdleDist = Integer.MAX_VALUE;

            for (Elevator e : elevators) {
                ElevatorState st = e.getState();

                // skip elevators that can't take this group (MAX 10 persons)
                if (!st.canBoard(groupPersons)) continue;

                int eFloor = st.getCurrentFloor();
                Direction eDir = st.getDirection();
                int dist = Math.abs(eFloor - requestFloor);

                if (eDir == Direction.IDLE) {
                    if (dist < bestIdleDist) {
                        bestIdleDist = dist;
                        bestIdle = e;
                    }
                    continue;
                }

                if (requestDirection == Direction.UP) {
                    if (eDir == Direction.UP && eFloor <= requestFloor && dist < bestMovingDist) {
                        bestMovingDist = dist;
                        bestMoving = e;
                    }
                } else if (requestDirection == Direction.DOWN) {
                    if (eDir == Direction.DOWN && eFloor >= requestFloor && dist < bestMovingDist) {
                        bestMovingDist = dist;
                        bestMoving = e;
                    }
                }
            }

            return (bestMoving != null) ? bestMoving : bestIdle;
        }
    }

    // ========================= MANAGER =========================
    static final class ElevatorManager implements ArrivalListener {
        private final List<Elevator> elevators;
        private ElevatorSelectionStrategy strategy = new NearestElevatorStrategy();

        private final Map<Integer, FloorSwitch> floorSwitches = new HashMap<>();

        public ElevatorManager(List<Elevator> elevators) {
            this.elevators = elevators;
        }

        public void setStrategy(ElevatorSelectionStrategy strategy) {
            this.strategy = strategy;
        }

        public void registerFloorSwitch(FloorSwitch fs) {
            floorSwitches.put(fs.getFloor(), fs);
        }

        public Elevator callElevator(int floor, Direction direction) {
            return callElevator(floor, direction, 0);
        }

        public Elevator callElevator(int floor, Direction direction, int groupPersons) {
            validateFloor(floor);

            Elevator selected = strategy.selectElevator(elevators, floor, direction, groupPersons);
            if (selected == null) throw new IllegalStateException("No elevator available for floor=" + floor);

            // Outside pickup stop: desired == pickup floor, carries groupPersons to board
            selected.addRequest(new Request(floor, floor, direction, Location.OUTSIDE_ELEVATOR, groupPersons));

            System.out.println("[Manager] Elevator-" + selected.getId() + " assigned to floor " + floor
                    + " for " + direction + " | groupPersons=" + groupPersons);

            return selected;
        }

        @Override
        public void onArrive(Elevator elevator, Request request) {
            // Reset floor switch only for OUTSIDE pickup stops (desired == current)
            if (request.getLocation() == Location.OUTSIDE_ELEVATOR
                    && request.getDesiredFloor() == request.getCurrentFloor()) {

                FloorSwitch fs = floorSwitches.get(request.getDesiredFloor());
                if (fs != null) {
                    if (request.getDirection() == Direction.UP) fs.setPressedUp(false);
                    if (request.getDirection() == Direction.DOWN) fs.setPressedDown(false);
                    System.out.println("[Manager] Reset FloorSwitch at floor " + fs.getFloor() + " for " + request.getDirection());
                }

                // Board group (ONLY persons constraint)
                int gp = request.getGroupPersons();
                if (gp > 0) {
                    if (elevator.getState().canBoard(gp)) {
                        elevator.getState().board(gp);
                        System.out.println("[Manager] Group boarded Elevator-" + elevator.getId()
                                + " | persons=" + gp + " | totalPersons=" + elevator.getState().getCurrentPersons());
                    } else {
                        System.out.println("[Manager] Boarding denied (max persons) for Elevator-" + elevator.getId()
                                + " | requestedPersons=" + gp + " | currentPersons=" + elevator.getState().getCurrentPersons());
                    }
                }
            }
        }
    }

    // ========================= SERVICES =========================
    interface IElevatorService {
        void toggleFan();
        void toggleLights();
        void toggleAlarm();
        void goToFloor(int floor);
        void stopImmediately();

        boolean attemptEnter(int persons); // optional inside boarding
    }

    static final class ElevatorService implements IElevatorService {
        private final Elevator elevator;

        public ElevatorService(Elevator elevator) {
            this.elevator = elevator;
        }

        @Override
        public void toggleFan() {
            elevator.setFanOn(!elevator.isFanOn());
            System.out.println("[ElevatorService] Fan " + (elevator.isFanOn() ? "ON" : "OFF")
                    + " | Elevator-" + elevator.getId());
        }

        @Override
        public void toggleLights() {
            elevator.setLightsOn(!elevator.isLightsOn());
            System.out.println("[ElevatorService] Lights " + (elevator.isLightsOn() ? "ON" : "OFF")
                    + " | Elevator-" + elevator.getId());
        }

        @Override
        public void toggleAlarm() {
            elevator.setAlarmOn(!elevator.isAlarmOn());
            System.out.println("[ElevatorService] Alarm " + (elevator.isAlarmOn() ? "ON" : "OFF")
                    + " | Elevator-" + elevator.getId());
        }

        @Override
        public void goToFloor(int floor) {
            validateFloor(floor);

            int cur = elevator.getState().getCurrentFloor();
            Direction dir = (floor > cur) ? Direction.UP : (floor < cur) ? Direction.DOWN : Direction.IDLE;

            boolean newlyPressed = elevator.getState().pressFloor(floor);
            if (!newlyPressed) {
                System.out.println("[ElevatorService] Floor " + floor + " already selected | Elevator-" + elevator.getId());
                return;
            }

            elevator.addRequest(new Request(cur, floor, dir, Location.INSIDE_ELEVATOR));
            System.out.println("[ElevatorService] Added inside request to floor " + floor + " | Elevator-" + elevator.getId());
        }

        @Override
        public void stopImmediately() {
            elevator.getState().setDirection(Direction.STOP);
            System.out.println("[ElevatorService] STOP requested | Elevator-" + elevator.getId());
        }

        @Override
        public boolean attemptEnter(int persons) {
            if (elevator.getState().canBoard(persons)) {
                elevator.getState().board(persons);
                System.out.println("[ElevatorService] Entered Elevator-" + elevator.getId()
                        + " | persons=" + persons + " | totalPersons=" + elevator.getState().getCurrentPersons());
                return true;
            }
            System.out.println("[ElevatorService] Entry denied (max persons) | Elevator-" + elevator.getId()
                    + " | persons=" + persons + " | currentPersons=" + elevator.getState().getCurrentPersons());
            return false;
        }
    }

    interface IFloorSwitchService {
        void pressSwitch(SwitchDirection direction);
    }

    static final class FloorSwitchService implements IFloorSwitchService {
        private final FloorSwitch floorSwitch;
        private final ElevatorManager manager;

        public FloorSwitchService(FloorSwitch floorSwitch, ElevatorManager manager) {
            this.floorSwitch = floorSwitch;
            this.manager = manager;
        }

        @Override
        public void pressSwitch(SwitchDirection dir) {
            if (dir == SwitchDirection.UP) {
                if (floorSwitch.isPressedUp()) return;
                floorSwitch.setPressedUp(true);
                manager.callElevator(floorSwitch.getFloor(), Direction.UP);
            } else {
                if (floorSwitch.isPressedDown()) return;
                floorSwitch.setPressedDown(true);
                manager.callElevator(floorSwitch.getFloor(), Direction.DOWN);
            }
        }
    }

    // ========================= VALIDATION =========================
    static void validateFloor(int floor) {
        if (floor < Config.MIN_FLOOR || floor > Config.MAX_FLOOR) {
            throw new IllegalArgumentException("Invalid floor " + floor + " | Range: " +
                    Config.MIN_FLOOR + " to " + Config.MAX_FLOOR);
        }
    }

    static void validateRequest(Request r) {
        Objects.requireNonNull(r, "request");
        Objects.requireNonNull(r.getDirection(), "direction");
        Objects.requireNonNull(r.getLocation(), "location");
        validateFloor(r.getCurrentFloor());
        validateFloor(r.getDesiredFloor());
    }

    // ========================= DEMO MAIN =========================
    public static void main(String[] args) {
        List<Elevator> elevators = new ArrayList<>();
        elevators.add(new Elevator(1));
        elevators.add(new Elevator(3));
        elevators.add(new Elevator(5));

        ElevatorManager manager = new ElevatorManager(elevators);

        // Register arrival listener so we can reset switches + board groups
        for (Elevator e : elevators) {
            e.setArrivalListener(manager);
        }

        // Floor switches
        FloorSwitch fs2 = new FloorSwitch(2);
        manager.registerFloorSwitch(fs2);

        // Person at floor 2 presses UP (outside)
        FloorSwitchService fs2Service = new FloorSwitchService(fs2, manager);
        fs2Service.pressSwitch(SwitchDirection.UP);

        // Outside call with group (example: 8 people). Manager will avoid elevators that are already full.
        manager.callElevator(2, Direction.UP, 8);

        // Inside selection: choose elevator-1 and go to floor 5 (duplicate presses ignored via Set)
        Elevator e1 = elevators.get(0);
        ElevatorService e1Service = new ElevatorService(e1);
        e1Service.goToFloor(5);
        e1Service.goToFloor(5); // duplicate, ignored

        // Run simulation
        for (Elevator e : elevators) {
            e.runUntilIdle();
        }

        // Optional: attempt to enter more people (denied if exceeds 10)
        e1Service.attemptEnter(5);

        for (Elevator e : elevators) {
            e.runUntilIdle();
        }
    }
}
