import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-file, interview-ready Parking Lot system
 * Improvements:
 *  - O(1) spot lookup per floor by maintaining per-vehicle-type free-spot queues
 *  - Display empty/filled spots per vehicle type per floor
 *  - Extensible with 3 common follow-ups: strategy, ticket repo, display board
 */
public class ParkingLotSystem {

    // -------------------- Domain --------------------

    public enum VehicleType { CAR, BIKE, TRUCK }

    public static abstract class Vehicle {
        private final String licensePlate;
        private final VehicleType type;

        protected Vehicle(String licensePlate, VehicleType type) {
            this.licensePlate = Objects.requireNonNull(licensePlate, "licensePlate");
            this.type = Objects.requireNonNull(type, "type");
        }
        public String getLicensePlate() { return licensePlate; }
        public VehicleType getType() { return type; }
    }

    public static final class Car extends Vehicle { public Car(String license) { super(license, VehicleType.CAR); } }
    public static final class Bike extends Vehicle { public Bike(String license) { super(license, VehicleType.BIKE); } }
    public static final class Truck extends Vehicle { public Truck(String license) { super(license, VehicleType.TRUCK); } }

    // -------------------- Fee Strategy --------------------

    public interface FeeStrategy { double calculateFee(Ticket ticket); }

    public static final class FlatRateFeeStrategy implements FeeStrategy {
        private static final double RATE_PER_HOUR = 10.0;
        private static final long MILLIS_PER_HOUR = 3_600_000L;

        @Override
        public double calculateFee(Ticket ticket) {
            long durationMillis = ticket.getExitTimestamp() - ticket.getEntryTimestamp();
            long hours = Math.max(1, (durationMillis + MILLIS_PER_HOUR - 1) / MILLIS_PER_HOUR);
            return hours * RATE_PER_HOUR;
        }
    }

    public static final class VehicleBasedFeeStrategy implements FeeStrategy {
        private static final long MILLIS_PER_HOUR = 3_600_000L;

        private final Map<VehicleType, Double> hourlyRates = Map.of(
                VehicleType.CAR, 20.0,
                VehicleType.BIKE, 10.0,
                VehicleType.TRUCK, 30.0
        );

        @Override
        public double calculateFee(Ticket ticket) {
            long durationMillis = ticket.getExitTimestamp() - ticket.getEntryTimestamp();
            long hours = Math.max(1, (durationMillis + MILLIS_PER_HOUR - 1) / MILLIS_PER_HOUR);
            double rate = hourlyRates.get(ticket.getVehicle().getType());
            return hours * rate;
        }
    }

    // -------------------- Parking Spot --------------------

    public static final class ParkingSpot {
        private final int spotNumber;
        private final VehicleType vehicleType;
        private Vehicle vehicle;      // null when empty
        private boolean occupied;

        public ParkingSpot(int spotNumber, VehicleType vehicleType) {
            this.spotNumber = spotNumber;
            this.vehicleType = vehicleType;
            this.occupied = false;
        }

        public synchronized boolean isAvailable() { return !occupied; }

        public synchronized boolean park(Vehicle v) {
            if (occupied || v.getType() != vehicleType) return false;
            this.vehicle = v;
            this.occupied = true;
            return true;
        }

        public synchronized void unpark() {
            this.vehicle = null;
            this.occupied = false;
        }

        public int getSpotNumber() { return spotNumber; }
        public VehicleType getVehicleType() { return vehicleType; }
        public synchronized Vehicle getVehicle() { return vehicle; }
    }

    // -------------------- Floor Status (for display) --------------------

    public static final class SpotTypeStatus {
        public final int total;
        public final int occupied;
        public final int available;
        public final List<Integer> availableSpots; // snapshot
        public final List<Integer> occupiedSpots;  // snapshot

        public SpotTypeStatus(int total, int occupied, List<Integer> availableSpots, List<Integer> occupiedSpots) {
            this.total = total;
            this.occupied = occupied;
            this.available = total - occupied;
            this.availableSpots = availableSpots;
            this.occupiedSpots = occupiedSpots;
        }

        @Override
        public String toString() {
            return "total=" + total +
                   ", occupied=" + occupied +
                   ", available=" + available +
                   ", free=" + availableSpots +
                   ", filled=" + occupiedSpots;
        }
    }

    public static final class FloorStatus {
        public final int floorNumber;
        public final Map<VehicleType, SpotTypeStatus> byType;

        public FloorStatus(int floorNumber, Map<VehicleType, SpotTypeStatus> byType) {
            this.floorNumber = floorNumber;
            this.byType = byType;
        }
    }

    // -------------------- Parking Floor (OPTIMIZED) --------------------
    /**
     * Old: getAvailableSpot() was O(N) scanning all spots each time.
     * New: maintain per-type free-spot queue => peek/poll is O(1)
     * Updates on park/unpark are O(1)
     */
    public static final class ParkingFloor {
        private final int floorNumber;
        private final List<ParkingSpot> allSpots;

        // O(1) lookup structure
        private final EnumMap<VehicleType, ArrayDeque<ParkingSpot>> freeByType = new EnumMap<>(VehicleType.class);

        // For "filled spots list" quickly (store only spot numbers)
        private final EnumMap<VehicleType, HashSet<Integer>> occupiedNumsByType = new EnumMap<>(VehicleType.class);

        // Totals per type
        private final EnumMap<VehicleType, Integer> totalByType = new EnumMap<>(VehicleType.class);

        public ParkingFloor(int floorNumber, List<ParkingSpot> spots) {
            this.floorNumber = floorNumber;
            this.allSpots = new ArrayList<>(Objects.requireNonNull(spots, "spots"));

            for (VehicleType t : VehicleType.values()) {
                freeByType.put(t, new ArrayDeque<>());
                occupiedNumsByType.put(t, new HashSet<>());
                totalByType.put(t, 0);
            }

            // Build indexes once
            for (ParkingSpot s : allSpots) {
                VehicleType t = s.getVehicleType();
                totalByType.put(t, totalByType.get(t) + 1);
                freeByType.get(t).addLast(s); // initially all empty
            }
        }

        public int getFloorNumber() { return floorNumber; }

        /**
         * O(1) expected: returns a free spot if present (does not park)
         */
        public synchronized Optional<ParkingSpot> peekAvailableSpot(VehicleType type) {
            ParkingSpot s = freeByType.get(type).peekFirst();
            return Optional.ofNullable(s);
        }

        /**
         * O(1) expected: allocate + park within floor under same lock
         */
        public synchronized Optional<ParkingSpot> parkOnThisFloor(Vehicle vehicle) {
            VehicleType t = vehicle.getType();
            ArrayDeque<ParkingSpot> q = freeByType.get(t);

            while (!q.isEmpty()) {
                ParkingSpot spot = q.pollFirst(); // remove from free list
                if (spot.park(vehicle)) {
                    occupiedNumsByType.get(t).add(spot.getSpotNumber());
                    return Optional.of(spot);
                }
                // If park() failed due to race (shouldn't happen under this lock),
                // continue and try next.
            }
            return Optional.empty();
        }

        /**
         * O(1) update to indexes
         */
        public synchronized void unparkFromThisFloor(ParkingSpot spot) {
            VehicleType t = spot.getVehicleType();
            spot.unpark();
            occupiedNumsByType.get(t).remove(spot.getSpotNumber());
            freeByType.get(t).addLast(spot); // add back to free list
        }

        /**
         * Requirement: show empty + filled spots per vehicle type on this floor.
         * Building the lists is O(k) in number of spots of that type (snapshot),
         * but lookup & updates remain O(1).
         */
        public synchronized FloorStatus getStatusSnapshot() {
            EnumMap<VehicleType, SpotTypeStatus> map = new EnumMap<>(VehicleType.class);

            for (VehicleType t : VehicleType.values()) {
                int total = totalByType.get(t);
                int occupied = occupiedNumsByType.get(t).size();

                List<Integer> freeNums = freeByType.get(t).stream()
                        .map(ParkingSpot::getSpotNumber)
                        .sorted()
                        .toList();

                List<Integer> occNums = occupiedNumsByType.get(t).stream()
                        .sorted()
                        .toList();

                map.put(t, new SpotTypeStatus(total, occupied, freeNums, occNums));
            }
            return new FloorStatus(floorNumber, map);
        }
    }

    // -------------------- Ticket --------------------

    public static final class Ticket {
        private final String ticketId;
        private final Vehicle vehicle;
        private final ParkingSpot spot;
        private final int floorNumber;
        private final long entryTimestamp;
        private long exitTimestamp;

        public Ticket(String ticketId, Vehicle vehicle, ParkingSpot spot, int floorNumber) {
            this.ticketId = Objects.requireNonNull(ticketId, "ticketId");
            this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
            this.spot = Objects.requireNonNull(spot, "spot");
            this.floorNumber = floorNumber;
            this.entryTimestamp = Instant.now().toEpochMilli();
        }

        public String getTicketId() { return ticketId; }
        public Vehicle getVehicle() { return vehicle; }
        public ParkingSpot getSpot() { return spot; }
        public int getFloorNumber() { return floorNumber; }
        public long getEntryTimestamp() { return entryTimestamp; }
        public long getExitTimestamp() { return exitTimestamp; }

        public void setExitTimestamp() { this.exitTimestamp = Instant.now().toEpochMilli(); }
    }

    // -------------------- Follow-up #1: Ticket Repository --------------------

    public interface TicketRepository {
        void save(Ticket t);
        Ticket remove(String ticketId);
        Ticket find(String ticketId);
    }

    public static final class InMemoryTicketRepository implements TicketRepository {
        private final Map<String, Ticket> map = new ConcurrentHashMap<>();
        @Override public void save(Ticket t) { map.put(t.getTicketId(), t); }
        @Override public Ticket remove(String id) { return map.remove(id); }
        @Override public Ticket find(String id) { return map.get(id); }
    }

    // -------------------- Follow-up #2: Allocation Strategy --------------------

    public interface SpotAllocationStrategy {
        Optional<SpotWithFloor> allocate(List<ParkingFloor> floors, Vehicle vehicle);
    }

    public static final class SpotWithFloor {
        public final ParkingFloor floor;
        public final ParkingSpot spot;
        public SpotWithFloor(ParkingFloor floor, ParkingSpot spot) {
            this.floor = floor;
            this.spot = spot;
        }
    }

    /**
     * Simple & common: pick the first floor that has a free spot for this type.
     * Complexity: O(#floors) checks, each check O(1)
     */
    public static final class FirstAvailableStrategy implements SpotAllocationStrategy {
        @Override
        public Optional<SpotWithFloor> allocate(List<ParkingFloor> floors, Vehicle vehicle) {
            for (ParkingFloor f : floors) {
                Optional<ParkingSpot> parked = f.parkOnThisFloor(vehicle); // parks atomically per floor
                if (parked.isPresent()) return Optional.of(new SpotWithFloor(f, parked.get()));
            }
            return Optional.empty();
        }
    }

    // -------------------- Parking Lot --------------------

    public static final class ParkingLot {

        // Singleton
        private static class Holder { private static final ParkingLot INSTANCE = new ParkingLot(); }
        public static ParkingLot getInstance() { return Holder.INSTANCE; }

        private final List<ParkingFloor> floors = new ArrayList<>();
        private volatile FeeStrategy feeStrategy = new FlatRateFeeStrategy();

        // extensibility hooks
        private volatile SpotAllocationStrategy allocationStrategy = new FirstAvailableStrategy();
        private final TicketRepository ticketRepo = new InMemoryTicketRepository();

        private ParkingLot() {}

        public void addFloor(ParkingFloor floor) { floors.add(Objects.requireNonNull(floor)); }

        public void setFeeStrategy(FeeStrategy feeStrategy) {
            this.feeStrategy = Objects.requireNonNull(feeStrategy);
        }

        public void setAllocationStrategy(SpotAllocationStrategy strategy) {
            this.allocationStrategy = Objects.requireNonNull(strategy);
        }

        public List<ParkingFloor> getParkingFloors() { return List.copyOf(floors); }

        /**
         * Main operation: park and return ticket
         * Complexity: O(#floors) * O(1) per floor for lookup/park
         */
        public synchronized Ticket parkVehicle(Vehicle vehicle) {
            Objects.requireNonNull(vehicle, "vehicle");

            Optional<SpotWithFloor> res = allocationStrategy.allocate(floors, vehicle);
            if (res.isEmpty()) throw new IllegalStateException("No available spot for " + vehicle.getType());

            SpotWithFloor swf = res.get();
            String ticketId = UUID.randomUUID().toString();
            Ticket ticket = new Ticket(ticketId, vehicle, swf.spot, swf.floor.getFloorNumber());
            ticketRepo.save(ticket);
            return ticket;
        }

        /**
         * Unpark and compute fee
         */
        public synchronized double unparkVehicle(String ticketId) {
            Ticket ticket = ticketRepo.remove(ticketId);
            if (ticket == null) throw new IllegalArgumentException("Invalid ticket");

            // find the floor by number and unpark
            ParkingFloor floor = floors.stream()
                    .filter(f -> f.getFloorNumber() == ticket.getFloorNumber())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Floor not found for ticket"));

            floor.unparkFromThisFloor(ticket.getSpot());
            ticket.setExitTimestamp();
            return feeStrategy.calculateFee(ticket);
        }

        // Useful for display board
        public List<FloorStatus> getLotStatusSnapshot() {
            List<FloorStatus> list = new ArrayList<>();
            for (ParkingFloor f : floors) list.add(f.getStatusSnapshot());
            return list;
        }
    }

    // -------------------- Follow-up #3: Display Board --------------------

    public static final class ParkingDisplayBoard {
        public void print(ParkingLot lot) {
            System.out.println("=== PARKING LOT STATUS ===");
            for (FloorStatus fs : lot.getLotStatusSnapshot()) {
                System.out.println("Floor " + fs.floorNumber);
                for (VehicleType t : VehicleType.values()) {
                    SpotTypeStatus s = fs.byType.get(t);
                    System.out.println("  " + t + " -> " + s);
                }
            }
            System.out.println("==========================");
        }
    }

    // -------------------- Demo --------------------

    public static final class ParkingLotDemo {
        public static void run() {
            ParkingLot lot = ParkingLot.getInstance();

            ParkingFloor floor1 = new ParkingFloor(1, List.of(
                    new ParkingSpot(101, VehicleType.CAR),
                    new ParkingSpot(102, VehicleType.CAR),
                    new ParkingSpot(103, VehicleType.BIKE)
            ));

            ParkingFloor floor2 = new ParkingFloor(2, List.of(
                    new ParkingSpot(201, VehicleType.BIKE),
                    new ParkingSpot(202, VehicleType.TRUCK),
                    new ParkingSpot(203, VehicleType.CAR)
            ));

            lot.addFloor(floor1);
            lot.addFloor(floor2);

            lot.setFeeStrategy(new VehicleBasedFeeStrategy());

            ParkingDisplayBoard board = new ParkingDisplayBoard();
            board.print(lot);

            Vehicle car1 = new Car("ABC123");
            Vehicle car2 = new Car("XYZ789");
            Vehicle bike1 = new Bike("M1234");

            List<String> tickets = new ArrayList<>();

            try {
                Ticket t1 = lot.parkVehicle(car1); tickets.add(t1.getTicketId());
                System.out.println("Car1 parked: ticket=" + t1.getTicketId() +
                        ", floor=" + t1.getFloorNumber() + ", spot=" + t1.getSpot().getSpotNumber());

                Ticket t2 = lot.parkVehicle(car2); tickets.add(t2.getTicketId());
                System.out.println("Car2 parked: ticket=" + t2.getTicketId() +
                        ", floor=" + t2.getFloorNumber() + ", spot=" + t2.getSpot().getSpotNumber());

                Ticket t3 = lot.parkVehicle(bike1); tickets.add(t3.getTicketId());
                System.out.println("Bike1 parked: ticket=" + t3.getTicketId() +
                        ", floor=" + t3.getFloorNumber() + ", spot=" + t3.getSpot().getSpotNumber());

                board.print(lot);

                // Try parking another car when car spots might be full
                Vehicle car3 = new Car("DL0432");
                Ticket t4 = lot.parkVehicle(car3);
                tickets.add(t4.getTicketId());
                System.out.println("Car3 parked: ticket=" + t4.getTicketId());

            } catch (Exception e) {
                System.out.println("Exception: " + e.getMessage());
            }

            try {
                double fee = lot.unparkVehicle(tickets.getFirst());
                System.out.println("Unparked ticket=" + tickets.getFirst() + ", fee=" + fee);

                board.print(lot);

                lot.unparkVehicle("1"); // invalid
            } catch (Exception e) {
                System.out.println("Exception while unparking: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        ParkingLotDemo.run();
    }
}
