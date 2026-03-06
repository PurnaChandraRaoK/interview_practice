/**
 * Vehicle types
 */
enum VehicleType {
    SUV, SEDAN, BIKE
}

/**
 * Domain model: Vehicle
 */
final class Vehicle {
    private final String id;
    private final String numberPlate;
    private final VehicleType type;
    private final double pricePerHour;
    private double latitude;
    private double longitude;

    public Vehicle(String numberPlate, VehicleType type, double pricePerHour,
                   double latitude, double longitude) {
        if (pricePerHour <= 0) throw new IllegalArgumentException("pricePerHour must be > 0");
        this.id = UUID.randomUUID().toString();
        this.numberPlate = Objects.requireNonNull(numberPlate, "numberPlate");
        this.type = Objects.requireNonNull(type, "type");
        this.pricePerHour = pricePerHour;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() { return id; }
    public String getNumberPlate() { return numberPlate; }
    public VehicleType getType() { return type; }
    public double getPricePerHour() { return pricePerHour; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    public void setLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public String toString() {
        return String.format("Vehicle[id=%s, plate=%s, type=%s, loc=(%.4f,%.4f)]",
                id, numberPlate, type, latitude, longitude);
    }
}

/**
 * Domain model: Station
 */
final class Station {
    private final String id;
    private final String name;
    private boolean active;
    private final double latitude;
    private final double longitude;

    // Keep station inventory encapsulated.
    private final List<Vehicle> vehicles = new ArrayList<>();

    public Station(String name, double latitude, double longitude) {
        this.id = UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(name, "name");
        this.active = true;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    public List<Vehicle> getVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    public void addVehicle(Vehicle v) {
        Objects.requireNonNull(v, "vehicle");
        // Avoid duplicates (simple invariant).
        boolean exists = vehicles.stream().anyMatch(x -> x.getId().equals(v.getId()));
        if (!exists) vehicles.add(v);
    }

    public void removeVehicle(String vehicleId) {
        Objects.requireNonNull(vehicleId, "vehicleId");
        vehicles.removeIf(v -> v.getId().equals(vehicleId));
    }

    public boolean hasVehicle(String vehicleId) {
        return vehicles.stream().anyMatch(v -> v.getId().equals(vehicleId));
    }

    public boolean hasVehicleType(VehicleType type) {
        return vehicles.stream().anyMatch(v -> v.getType() == type);
    }

    public void deactivate() { this.active = false; }

    @Override
    public String toString() {
        return String.format(
                "Station[id=%s, name=%s, active=%s, loc=(%.4f,%.4f), vehicles=%d]",
                id, name, active, latitude, longitude, vehicles.size());
    }
}

/**
 * Domain model: Booking
 */
final class Booking {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final int id;
    private final String vehicleId;
    private final String stationId;
    private final String userId;
    private final LocalDateTime start;
    private final LocalDateTime end;
    private final double amount;
    private boolean active;

    public Booking(String vehicleId, String stationId, String userId,
                   LocalDateTime start, LocalDateTime end, double amount) {
        this.id = COUNTER.incrementAndGet();
        this.vehicleId = Objects.requireNonNull(vehicleId, "vehicleId");
        this.stationId = Objects.requireNonNull(stationId, "stationId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        this.amount = amount;
        this.active = true;
    }

    public int getId() { return id; }
    public String getVehicleId() { return vehicleId; }
    public String getStationId() { return stationId; }
    public String getUserId() { return userId; }
    public LocalDateTime getStart() { return start; }
    public LocalDateTime getEnd() { return end; }
    public double getAmount() { return amount; }
    public boolean isActive() { return active; }

    public void complete() { this.active = false; }

    @Override
    public String toString() {
        return String.format(
                "Booking[id=%d, vehicle=%s, station=%s, user=%s, %s->%s, amount=%.2f, active=%s]",
                id, vehicleId, stationId, userId, start, end, amount, active);
    }
}

/**
 * Utility for distance calculation
 */
final class LocationUtil {
    private LocationUtil() { }

    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}

/**
 * In-memory repositories
 */
interface StationRepository {
    Station save(Station s);
    Optional<Station> findById(String id);
    List<Station> findAll();
}

final class InMemoryStationRepo implements StationRepository {
    private final Map<String, Station> map = new LinkedHashMap<>();

    public Station save(Station s) { map.put(s.getId(), s); return s; }
    public Optional<Station> findById(String id) { return Optional.ofNullable(map.get(id)); }
    public List<Station> findAll() { return new ArrayList<>(map.values()); }
}

interface VehicleRepository {
    Vehicle save(Vehicle v);
    Optional<Vehicle> findById(String id);
    List<Vehicle> findAll();
    boolean deleteById(String id);
}

final class InMemoryVehicleRepo implements VehicleRepository {
    private final Map<String, Vehicle> map = new LinkedHashMap<>();

    public Vehicle save(Vehicle v) { map.put(v.getId(), v); return v; }
    public Optional<Vehicle> findById(String id) { return Optional.ofNullable(map.get(id)); }
    public List<Vehicle> findAll() { return new ArrayList<>(map.values()); }
    public boolean deleteById(String id) { return map.remove(id) != null; }
}

interface BookingRepository {
    Booking save(Booking b);
    Optional<Booking> findById(int id);
    List<Booking> findAll();
}

final class InMemoryBookingRepo implements BookingRepository {
    private final Map<Integer, Booking> map = new LinkedHashMap<>();

    public Booking save(Booking b) { map.put(b.getId(), b); return b; }
    public Optional<Booking> findById(int id) { return Optional.ofNullable(map.get(id)); }
    public List<Booking> findAll() { return new ArrayList<>(map.values()); }
}

/**
 * Services
 */
final class StationService {
    private final StationRepository repo;

    public StationService(StationRepository repo) {
        this.repo = Objects.requireNonNull(repo, "repo");
    }

    public Station addStation(Station s) { return repo.save(s); }

    public void removeStation(String id) {
        repo.findById(id).ifPresent(Station::deactivate);
    }

    public List<Station> filterByLocation(double lat, double lon, double maxKm) {
        return repo.findAll().stream()
                .filter(Station::isActive)
                .filter(s -> LocationUtil.distanceKm(lat, lon, s.getLatitude(), s.getLongitude()) <= maxKm)
                .collect(Collectors.toList());
    }

    public Station getStation(String id) {
        return repo.findById(id)
                .filter(Station::isActive)
                .orElseThrow(() -> new NoSuchElementException("Station not found"));
    }
}

final class VehicleService {
    private final VehicleRepository repo;

    public VehicleService(VehicleRepository repo) {
        this.repo = Objects.requireNonNull(repo, "repo");
    }

    public Vehicle addVehicle(Vehicle v) { return repo.save(v); }

    public void removeVehicle(String id) {
        boolean removed = repo.deleteById(id);
        if (!removed) {
            // Optional: keep silent if you want; throwing makes correctness clearer.
            // throw new NoSuchElementException("Vehicle not found");
        }
    }

    public Vehicle getVehicle(String id) {
        return repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Vehicle not found"));
    }

    public List<Vehicle> filterByType(VehicleType type) {
        return repo.findAll().stream()
                .filter(v -> v.getType() == type)
                .collect(Collectors.toList());
    }
}

final class BookingService {
    private final BookingRepository repo;

    public BookingService(BookingRepository repo) {
        this.repo = Objects.requireNonNull(repo, "repo");
    }

    public Booking createBooking(String vehicleId, String stationId, String userId,
                                 LocalDateTime start, LocalDateTime end, double pricePerHour) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) throw new IllegalArgumentException("end must be after start");
        if (pricePerHour <= 0) throw new IllegalArgumentException("pricePerHour must be > 0");

        // Correct hour rounding: ceil(minutes / 60.0). Minimum 1 hour.
        long minutes = Duration.between(start, end).toMinutes();
        int billableHours = (int) Math.max(1, Math.ceil(minutes / 60.0));

        double amt = billableHours * pricePerHour;
        Booking b = new Booking(vehicleId, stationId, userId, start, end, amt);
        return repo.save(b);
    }

    public Booking endBooking(int bookingId) {
        Booking b = repo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found"));
        b.complete();
        return b;
    }

    public Booking getBooking(int bookingId) {
        return repo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found"));
    }
}

/**
 * Orchestration (Facade)
 */
final class CarRentalService {
    private final StationService stationService;
    private final VehicleService vehicleService;
    private final BookingService bookingService;

    public CarRentalService(StationService stSvc, VehicleService vSvc, BookingService bSvc) {
        this.stationService = Objects.requireNonNull(stSvc, "stationService");
        this.vehicleService = Objects.requireNonNull(vSvc, "vehicleService");
        this.bookingService = Objects.requireNonNull(bSvc, "bookingService");
    }

    public Station addStation(Station s) {
        // register station and its vehicles
        for (Vehicle v : s.getVehicles()) {
            vehicleService.addVehicle(v);
        }
        return stationService.addStation(s);
    }

    public void removeStation(String stationId) {
        Station s = stationService.getStation(stationId);
        for (Vehicle v : s.getVehicles()) {
            vehicleService.removeVehicle(v.getId());
        }
        stationService.removeStation(stationId);
    }

    public Booking bookVehicle(String stationId, String vehicleId,
                               String userId, LocalDateTime start,
                               LocalDateTime end) {
        Station s = stationService.getStation(stationId);
        Vehicle v = vehicleService.getVehicle(vehicleId);

        // ensure vehicle at station
        if (!s.hasVehicle(vehicleId)) {
            throw new IllegalArgumentException("Vehicle not at given station");
        }

        Booking b = bookingService.createBooking(vehicleId, stationId, userId, start, end, v.getPricePerHour());
        s.removeVehicle(vehicleId); // mark as unavailable at station
        return b;
    }

    public Booking endBooking(int bookingId) {
        Booking b = bookingService.endBooking(bookingId);

        // return vehicle to station
        Station s = stationService.getStation(b.getStationId());
        Vehicle v = vehicleService.getVehicle(b.getVehicleId());
        s.addVehicle(v);

        return b;
    }

    /**
     * Throws if no vehicles of type exist at all; else returns nearby stations that actually have that type.
     */
    public List<Station> searchVehicle(VehicleType type,
                                       double lat, double lon,
                                       double maxKm) {
        List<Vehicle> allOfType = vehicleService.filterByType(type);
        if (allOfType.isEmpty()) {
            throw new NoSuchElementException("No vehicles found of this type");
        }

        // Nearby stations, then filter to those that have the requested type available.
        return stationService.filterByLocation(lat, lon, maxKm).stream()
                .filter(st -> st.hasVehicleType(type))
                .collect(Collectors.toList());
    }
}

/**
 * Application Entry
 */
public class CarRentalApp {
    public static void main(String[] args) {
        var stationRepo = new InMemoryStationRepo();
        var vehicleRepo = new InMemoryVehicleRepo();
        var bookingRepo = new InMemoryBookingRepo();

        var stationSvc = new StationService(stationRepo);
        var vehicleSvc = new VehicleService(vehicleRepo);
        var bookingSvc = new BookingService(bookingRepo);

        var app = new CarRentalService(stationSvc, vehicleSvc, bookingSvc);

        // Onboard a station
        Station s = new Station("Central", 12.9716, 77.5946);
        Vehicle v1 = new Vehicle("KA-01-XYZ", VehicleType.SUV, 50.0, 12.9716, 77.5946);
        s.addVehicle(v1);
        app.addStation(s);

        // Search vehicles
        System.out.println("Stations nearby with SUV:");
        app.searchVehicle(VehicleType.SUV, 12.97, 77.59, 5)
                .forEach(System.out::println);

        // Book vehicle
        LocalDateTime now = LocalDateTime.now();
        Booking b = app.bookVehicle(s.getId(), v1.getId(), "User1", now.plusHours(1), now.plusHours(5));
        System.out.println("Booked: " + b);

        // End booking
        Booking ended = app.endBooking(b.getId());
        System.out.println("Ended: " + ended);
    }
}
