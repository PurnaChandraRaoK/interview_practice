public enum VehicleType {
    CAR,
    BIKE,
    TRUCK
}

public abstract class Vehicle {
    private final String licensePlate;
    private final VehicleType type;

    protected Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = Objects.requireNonNull(licensePlate, "licensePlate");
        this.type = Objects.requireNonNull(type, "type");
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getType() {
        return type;
    }
}

public final class Car extends Vehicle {
    public Car(String license) {
        super(license, VehicleType.CAR);
    }
}

public final class Bike extends Vehicle {
    public Bike(String license) {
        super(license, VehicleType.BIKE);
    }
}

public final class Truck extends Vehicle {
    public Truck(String license) {
        super(license, VehicleType.TRUCK);
    }
}

public interface FeeStrategy {
    double calculateFee(Ticket ticket);
}

public final class FlatRateFeeStrategy implements FeeStrategy {

    private static final double RATE_PER_HOUR = 10.0;
    private static final long MILLIS_PER_HOUR = 3_600_000L;

    @Override
    public double calculateFee(Ticket ticket) {
        long durationMillis = ticket.getExitTimestamp() - ticket.getEntryTimestamp();
        long hours = Math.max(1, (durationMillis + MILLIS_PER_HOUR - 1) / MILLIS_PER_HOUR); // ceil, min 1
        return hours * RATE_PER_HOUR;
    }
}

import java.util.Map;

public final class VehicleBasedFeeStrategy implements FeeStrategy {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

    private final Map<VehicleType, Double> hourlyRates = Map.of(
            VehicleType.CAR, 20.0,
            VehicleType.BIKE, 10.0,
            VehicleType.TRUCK, 30.0
    );

    @Override
    public double calculateFee(Ticket ticket) {
        long durationMillis = ticket.getExitTimestamp() - ticket.getEntryTimestamp();
        long hours = Math.max(1, (durationMillis + MILLIS_PER_HOUR - 1) / MILLIS_PER_HOUR); // ceil, min 1
        double rate = hourlyRates.get(ticket.getVehicle().getType());
        return hours * rate;
    }
}

public final class ParkingSpot {
    private final int spotNumber;
    private final VehicleType vehicleType;
    private Vehicle vehicle;
    private boolean isOccupied;

    public ParkingSpot(int spotNumber, VehicleType vehicleType) {
        this.spotNumber = spotNumber;
        this.vehicleType = vehicleType;
        this.isOccupied = false;
    }

    public synchronized boolean isAvailable() {
        return !isOccupied;
    }

    public synchronized boolean park(Vehicle vehicle) {
        if (isOccupied || vehicle.getType() != vehicleType) {
            return false;
        }
        this.vehicle = vehicle;
        this.isOccupied = true;
        return true;
    }

    public synchronized void unpark() {
        this.vehicle = null;
        this.isOccupied = false;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public int getSpotNumber() {
        return spotNumber;
    }
}

public final class ParkingFloor {
    private final int floorNumber;
    private final List<ParkingSpot> parkingSpots;

    public ParkingFloor(int floorNumber, List<ParkingSpot> spots) {
        this.floorNumber = floorNumber;
        this.parkingSpots = spots;
    }

    public synchronized Optional<ParkingSpot> getAvailableSpot(VehicleType type) {
        return parkingSpots.stream()
                .filter(spot -> spot.isAvailable() && spot.getVehicleType() == type)
                .findFirst();
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public List<ParkingSpot> getParkingSpots() {
        return List.copyOf(parkingSpots);
    }

    public List<Integer> getAvailableSpots(VehicleType type) {
        return parkingSpots.stream()
                .filter(spot -> spot.isAvailable() && spot.getVehicleType() == type)
                .map(ParkingSpot::getSpotNumber)
                .toList();
    }
}

public final class Ticket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final long entryTimestamp;
    private long exitTimestamp;

    public Ticket(String ticketId, Vehicle vehicle, ParkingSpot spot) {
        this.ticketId = Objects.requireNonNull(ticketId, "ticketId");
        this.vehicle = Objects.requireNonNull(vehicle, "vehicle");
        this.spot = Objects.requireNonNull(spot, "spot");
        this.entryTimestamp = Instant.now().toEpochMilli();
    }

    public String getTicketId() { return ticketId; }
    public Vehicle getVehicle() { return vehicle; }
    public ParkingSpot getSpot() { return spot; }
    public long getEntryTimestamp() { return entryTimestamp; }
    public long getExitTimestamp() { return exitTimestamp; }

    public void setExitTimestamp() {
        this.exitTimestamp = Instant.now().toEpochMilli();
    }
}

public final class ParkingLot {

    // Initialization-on-demand holder (thread-safe singleton)
    private static class Holder {
        private static final ParkingLot INSTANCE = new ParkingLot();
    }

    public static ParkingLot getInstance() {
        return Holder.INSTANCE;
    }

    private final List<ParkingFloor> floors = new ArrayList<>();
    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();
    private volatile FeeStrategy feeStrategy;

    private ParkingLot() {
        this.feeStrategy = new FlatRateFeeStrategy();
    }

    public void addFloor(ParkingFloor floor) {
        floors.add(Objects.requireNonNull(floor, "floor"));
    }

    public void setFeeStrategy(FeeStrategy feeStrategy) {
        this.feeStrategy = Objects.requireNonNull(feeStrategy, "feeStrategy");
    }

    public synchronized Ticket parkVehicle(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle");

        for (ParkingFloor floor : floors) {
            Optional<ParkingSpot> spotOpt = floor.getAvailableSpot(vehicle.getType());
            if (spotOpt.isPresent()) {
                ParkingSpot spot = spotOpt.get();
                if (spot.park(vehicle)) {
                    String ticketId = UUID.randomUUID().toString();
                    Ticket ticket = new Ticket(ticketId, vehicle, spot);
                    activeTickets.put(ticketId, ticket);
                    return ticket;
                }
            }
        }
        throw new IllegalStateException("No available spot for " + vehicle.getType());
    }

    public synchronized double unparkVehicle(String ticketId) {
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Invalid ticket");
        }

        ParkingSpot spot = ticket.getSpot();
        spot.unpark();

        ticket.setExitTimestamp();
        return feeStrategy.calculateFee(ticket);
    }

    public List<ParkingFloor> getParkingFloors() {
        return List.copyOf(floors);
    }
}

public final class ParkingLotDemo {

    public static void run() {
        ParkingLot parkingLot = ParkingLot.getInstance();

        List<ParkingSpot> parkingSpotsFloor1 = List.of(
                new ParkingSpot(101, VehicleType.CAR),
                new ParkingSpot(102, VehicleType.CAR),
                new ParkingSpot(103, VehicleType.BIKE)
        );

        List<ParkingSpot> parkingSpotsFloor2 = List.of(
                new ParkingSpot(201, VehicleType.BIKE),
                new ParkingSpot(202, VehicleType.TRUCK)
        );

        ParkingFloor floor1 = new ParkingFloor(1, parkingSpotsFloor1);
        ParkingFloor floor2 = new ParkingFloor(2, parkingSpotsFloor2);

        parkingLot.addFloor(floor1);
        parkingLot.addFloor(floor2);

        parkingLot.setFeeStrategy(new VehicleBasedFeeStrategy());

        Vehicle car1 = new Car("ABC123");
        Vehicle car2 = new Car("XYZ789");
        Vehicle bike1 = new Bike("M1234");

        System.out.println("Available spots for Car:");
        for (ParkingFloor floor : parkingLot.getParkingFloors()) {
            System.out.println("Floor: " + floor.getFloorNumber() + " -> " + floor.getAvailableSpots(VehicleType.CAR));
        }

        List<String> parkingTickets = new ArrayList<>();

        // park vehicles
        try {
            Ticket ticket1 = parkingLot.parkVehicle(car1);
            System.out.println("Car 1 parked: " + ticket1.getTicketId());
            parkingTickets.add(ticket1.getTicketId());

            Ticket ticket2 = parkingLot.parkVehicle(car2);
            System.out.println("Car 2 parked: " + ticket2.getTicketId());
            parkingTickets.add(ticket2.getTicketId());

            Ticket ticket3 = parkingLot.parkVehicle(bike1);
            System.out.println("Bike 1 parked: " + ticket3.getTicketId());
            parkingTickets.add(ticket3.getTicketId());

            // Try parking another car when spots are full
            Vehicle car3 = new Car("DL0432");
            parkingLot.parkVehicle(car3);

        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
        }

        // unpark vehicles
        try {
            double fee = parkingLot.unparkVehicle(parkingTickets.getFirst()); // valid ticket
            System.out.println("Ticket: " + parkingTickets.getFirst() + ", Fee: " + fee);

            fee = parkingLot.unparkVehicle("1"); // invalid ticket
        } catch (Exception e) {
            System.out.println("Exception while unparking: " + e.getMessage());
        }
    }
}
