/**
A city wants to start offering a bike rental service based on subscriptions. There will be electric and mechanical bikes and all of them will be equipped with low speed internet connectivity. Customers will pay an annual fee, which will allow them to go to a station, select a bike, unlock it, ride and then return it to another station.
System Architecture problem
Scope
We need to model a system architecture to support the following use cases of the problem stated above.
Functional requirements
Customers will be able to browse the list of stations nearby
When the customer is at a station, they can unlock a bike and start riding it
While riding, the bike will send its coordinates every fixed interval of time (20 seconds)
When the customer returns the bike, the system will display to the customer the summary of the ride: bike distance, starting date, end date, and the route followed
app should work 24/7
peak traffic before and after work
Scale
200k stations
20M bikes
10M users
 */

/** Bike types */
enum BikeType {
    ELECTRIC, MECHANICAL
}

/** Subscription */
enum SubscriptionStatus {
    ACTIVE, EXPIRED, SUSPENDED
}

final class Subscription {
    private final String id;
    private final String userId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private SubscriptionStatus status;

    public Subscription(String userId, LocalDate startDate, LocalDate endDate) {
        this.id = UUID.randomUUID().toString();
        this.userId = Objects.requireNonNull(userId, "userId");
        this.startDate = Objects.requireNonNull(startDate, "startDate");
        this.endDate = Objects.requireNonNull(endDate, "endDate");
        this.status = SubscriptionStatus.ACTIVE;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public SubscriptionStatus getStatus() { return status; }

    public boolean isActiveOn(LocalDate date) {
        if (status != SubscriptionStatus.ACTIVE) return false;
        return ( !date.isBefore(startDate) ) && ( !date.isAfter(endDate) );
    }

    public void expire() { this.status = SubscriptionStatus.EXPIRED; }
    public void suspend() { this.status = SubscriptionStatus.SUSPENDED; }
}

/** Simple GPS point (telemetry) */
final class GpsPoint {
    private final double latitude;
    private final double longitude;
    private final Instant timestamp;

    public GpsPoint(double latitude, double longitude, Instant timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("(%.5f,%.5f @ %s)", latitude, longitude, timestamp);
    }
}

/** Domain model: Bike */
final class Bike {
    private final String id;
    private final String bikeCode;      // like number plate / QR code
    private final BikeType type;
    private final String deviceId;      // low-speed internet device id
    private double latitude;
    private double longitude;
    private boolean locked = true;
    private boolean inRide = false;

    public Bike(String bikeCode, BikeType type, String deviceId, double latitude, double longitude) {
        this.id = UUID.randomUUID().toString();
        this.bikeCode = Objects.requireNonNull(bikeCode, "bikeCode");
        this.type = Objects.requireNonNull(type, "type");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() { return id; }
    public String getBikeCode() { return bikeCode; }
    public BikeType getType() { return type; }
    public String getDeviceId() { return deviceId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public boolean isLocked() { return locked; }
    public boolean isInRide() { return inRide; }

    public void setLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void unlock() { this.locked = false; }
    public void lock() { this.locked = true; }

    public void markInRide(boolean inRide) { this.inRide = inRide; }

    @Override
    public String toString() {
        return String.format("Bike[id=%s, code=%s, type=%s, device=%s, locked=%s, inRide=%s, loc=(%.4f,%.4f)]",
                id, bikeCode, type, deviceId, locked, inRide, latitude, longitude);
    }
}

/** Domain model: Station */
final class Station {
    private final String id;
    private final String name;
    private boolean active;
    private final double latitude;
    private final double longitude;

    // Inventory: Map by type -> Map of bikeId -> bike (easy lookup + checkout by type)
    private final Map<BikeType, LinkedHashMap<String, Bike>> inventory = new EnumMap<>(BikeType.class);

    public Station(String name, double latitude, double longitude) {
        this.id = UUID.randomUUID().toString();
        this.name = Objects.requireNonNull(name, "name");
        this.active = true;
        this.latitude = latitude;
        this.longitude = longitude;

        // init buckets
        for (BikeType t : BikeType.values()) inventory.put(t, new LinkedHashMap<>());
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    /** Unmodifiable snapshot for a given type */
    public Collection<Bike> getBikesByType(BikeType type) {
        return Collections.unmodifiableCollection(inventory.get(type).values());
    }

    public int count(BikeType type) {
        return inventory.get(type).size();
    }

    public void addBike(Bike b) {
        Objects.requireNonNull(b, "bike");
        inventory.get(b.getType()).putIfAbsent(b.getId(), b);
    }

    public Bike removeBike(String bikeId) {
        Objects.requireNonNull(bikeId, "bikeId");
        // We don't know the type; search buckets (only 2 types, so fine in LLD)
        for (BikeType t : BikeType.values()) {
            Bike removed = inventory.get(t).remove(bikeId);
            if (removed != null) return removed;
        }
        return null;
    }

    public boolean hasBike(String bikeId) {
        for (BikeType t : BikeType.values()) {
            if (inventory.get(t).containsKey(bikeId)) return true;
        }
        return false;
    }

    public boolean hasBikeType(BikeType type) {
        return !inventory.get(type).isEmpty();
    }

    /**
     * Checkout any bike of a given type (simple FIFO by insertion order).
     * This is O(1) to find "some" bike of type.
     */
    public Bike checkoutAny(BikeType type) {
        LinkedHashMap<String, Bike> bucket = inventory.get(type);
        if (bucket.isEmpty()) return null;

        // pick first
        Iterator<Map.Entry<String, Bike>> it = bucket.entrySet().iterator();
        Map.Entry<String, Bike> entry = it.next();
        Bike b = entry.getValue();

        it.remove(); // remove from station inventory (now unavailable at station)
        return b;
    }

    public void deactivate() { this.active = false; }

    @Override
    public String toString() {
        return String.format("Station[id=%s, name=%s, active=%s, loc=(%.4f,%.4f), mech=%d, elec=%d]",
                id, name, active, latitude, longitude, count(BikeType.MECHANICAL), count(BikeType.ELECTRIC));
    }
}

/** Ride summary to show on return */
final class RideSummary {
    private final String bikeId;
    private final String userId;
    private final String startStationId;
    private final String endStationId;
    private final Instant startTime;
    private final Instant endTime;
    private final double distanceKm;
    private final List<GpsPoint> route;

    public RideSummary(String bikeId, String userId,
                       String startStationId, String endStationId,
                       Instant startTime, Instant endTime,
                       double distanceKm, List<GpsPoint> route) {
        this.bikeId = bikeId;
        this.userId = userId;
        this.startStationId = startStationId;
        this.endStationId = endStationId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.distanceKm = distanceKm;
        this.route = route;
    }

    @Override
    public String toString() {
        return "RideSummary{" +
                "bikeId='" + bikeId + '\'' +
                ", userId='" + userId + '\'' +
                ", startStationId='" + startStationId + '\'' +
                ", endStationId='" + endStationId + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", distanceKm=" + String.format("%.3f", distanceKm) +
                ", routePoints=" + route.size() +
                '}';
    }

    public double getDistanceKm() { return distanceKm; }
    public List<GpsPoint> getRoute() { return route; }
}

/** Domain model: Ride (active while user rides) */
final class Ride {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final int id;
    private final String bikeId;
    private final String userId;
    private final String startStationId;
    private String endStationId;

    private final Instant startTime;
    private Instant endTime;

    private boolean active = true;

    private final List<GpsPoint> route = new ArrayList<>();
    private double distanceKm = 0.0;

    public Ride(String bikeId, String userId, String startStationId, Instant startTime) {
        this.id = COUNTER.incrementAndGet();
        this.bikeId = Objects.requireNonNull(bikeId, "bikeId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.startStationId = Objects.requireNonNull(startStationId, "startStationId");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
    }

    public int getId() { return id; }
    public String getBikeId() { return bikeId; }
    public String getUserId() { return userId; }
    public String getStartStationId() { return startStationId; }
    public String getEndStationId() { return endStationId; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public boolean isActive() { return active; }
    public double getDistanceKm() { return distanceKm; }

    public List<GpsPoint> getRoute() {
        return Collections.unmodifiableList(route);
    }

    /** called by telemetry ingestion */
    public void addPoint(GpsPoint p) {
        Objects.requireNonNull(p, "point");
        if (!active) return;

        if (!route.isEmpty()) {
            GpsPoint last = route.get(route.size() - 1);
            distanceKm += LocationUtil.distanceKm(
                    last.getLatitude(), last.getLongitude(),
                    p.getLatitude(), p.getLongitude()
            );
        }
        route.add(p);
    }

    public RideSummary complete(String endStationId, Instant endTime) {
        this.endStationId = Objects.requireNonNull(endStationId, "endStationId");
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        this.active = false;

        return new RideSummary(
                bikeId, userId,
                startStationId, this.endStationId,
                startTime, this.endTime,
                distanceKm,
                new ArrayList<>(route)
        );
    }

    @Override
    public String toString() {
        return "Ride{" +
                "id=" + id +
                ", bikeId='" + bikeId + '\'' +
                ", userId='" + userId + '\'' +
                ", startStationId='" + startStationId + '\'' +
                ", endStationId='" + endStationId + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", distanceKm=" + String.format("%.3f", distanceKm) +
                ", points=" + route.size() +
                ", active=" + active +
                '}';
    }
}

/** Utility for distance calculation */
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

/** In-memory repositories */
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

interface BikeRepository {
    Bike save(Bike b);
    Optional<Bike> findById(String id);
    boolean deleteById(String id);
    List<Bike> findAll();
}

final class InMemoryBikeRepo implements BikeRepository {
    private final Map<String, Bike> map = new LinkedHashMap<>();
    public Bike save(Bike b) { map.put(b.getId(), b); return b; }
    public Optional<Bike> findById(String id) { return Optional.ofNullable(map.get(id)); }
    public boolean deleteById(String id) { return map.remove(id) != null; }
    public List<Bike> findAll() { return new ArrayList<>(map.values()); }
}

interface RideRepository {
    Ride save(Ride r);
    Optional<Ride> findById(int id);
    List<Ride> findAll();
}

final class InMemoryRideRepo implements RideRepository {
    private final Map<Integer, Ride> map = new LinkedHashMap<>();
    public Ride save(Ride r) { map.put(r.getId(), r); return r; }
    public Optional<Ride> findById(int id) { return Optional.ofNullable(map.get(id)); }
    public List<Ride> findAll() { return new ArrayList<>(map.values()); }
}

interface SubscriptionRepository {
    Subscription save(Subscription s);
    Optional<Subscription> findByUserId(String userId);
}

final class InMemorySubscriptionRepo implements SubscriptionRepository {
    private final Map<String, Subscription> map = new LinkedHashMap<>(); // userId -> subscription
    public Subscription save(Subscription s) { map.put(s.getUserId(), s); return s; }
    public Optional<Subscription> findByUserId(String userId) { return Optional.ofNullable(map.get(userId)); }
}

/** Services */
final class StationService {
    private final StationRepository repo;

    public StationService(StationRepository repo) {
        this.repo = Objects.requireNonNull(repo, "repo");
    }

    public Station addStation(Station s) { return repo.save(s); }

    public void removeStation(String id) {
        repo.findById(id).ifPresent(Station::deactivate);
    }

    /** browse nearby stations */
    public List<Station> nearbyStations(double lat, double lon, double maxKm) {
        return repo.findAll().stream()
                .filter(Station::isActive)
                .filter(s -> LocationUtil.distanceKm(lat, lon, s.getLatitude(), s.getLongitude()) <= maxKm)
                .sorted(Comparator.comparingDouble(s -> LocationUtil.distanceKm(lat, lon, s.getLatitude(), s.getLongitude())))
                .collect(Collectors.toList());
    }

    public Station getStation(String id) {
        return repo.findById(id)
                .filter(Station::isActive)
                .orElseThrow(() -> new NoSuchElementException("Station not found"));
    }
}

final class BikeService {
    private final BikeRepository repo;

    public BikeService(BikeRepository repo) {
        this.repo = Objects.requireNonNull(repo, "repo");
    }

    public Bike addBike(Bike b) { return repo.save(b); }

    public Bike getBike(String id) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException("Bike not found"));
    }

    public void updateLocation(String bikeId, double lat, double lon) {
        Bike b = getBike(bikeId);
        b.setLocation(lat, lon);
    }
}

final class SubscriptionService {
    private final SubscriptionRepository repo;

    public SubscriptionService(SubscriptionRepository repo) {
        this.repo = Objects.requireNonNull(repo, "repo");
    }

    /** annual subscription */
    public Subscription createAnnual(String userId, LocalDate startDate) {
        LocalDate end = startDate.plusYears(1).minusDays(1);
        Subscription s = new Subscription(userId, startDate, end);
        return repo.save(s);
    }

    public Subscription getRequiredActive(String userId, LocalDate today) {
        Subscription s = repo.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("User has no subscription"));
        if (!s.isActiveOn(today)) throw new IllegalStateException("Subscription not active");
        return s;
    }
}

final class RideService {
    private final RideRepository repo;

    // active ride per bike for quick telemetry routing
    private final Map<String, Integer> bikeIdToActiveRideId = new HashMap<>();

    public RideService(RideRepository repo) {
        this.repo = Objects.requireNonNull(repo, "repo");
    }

    public Ride startRide(String bikeId, String userId, String stationId, Instant startTime) {
        if (bikeIdToActiveRideId.containsKey(bikeId)) {
            throw new IllegalStateException("Bike already in an active ride");
        }
        Ride r = new Ride(bikeId, userId, stationId, startTime);
        repo.save(r);
        bikeIdToActiveRideId.put(bikeId, r.getId());
        return r;
    }

    /** Ingest telemetry point (bike sends coords every 20 seconds) */
    public void ingestTelemetry(String bikeId, GpsPoint point) {
        Integer rideId = bikeIdToActiveRideId.get(bikeId);
        if (rideId == null) return; // bike not in active ride; ignore
        Ride r = repo.findById(rideId).orElse(null);
        if (r == null || !r.isActive()) return;
        r.addPoint(point);
    }

    public RideSummary endRide(int rideId, String endStationId, Instant endTime) {
        Ride r = repo.findById(rideId).orElseThrow(() -> new NoSuchElementException("Ride not found"));
        if (!r.isActive()) throw new IllegalStateException("Ride already ended");

        RideSummary summary = r.complete(endStationId, endTime);
        bikeIdToActiveRideId.remove(r.getBikeId());
        return summary;
    }

    public Ride getRide(int rideId) {
        return repo.findById(rideId).orElseThrow(() -> new NoSuchElementException("Ride not found"));
    }
}

/** Facade / Orchestration */
final class BikeRentalService {
    public static final int TELEMETRY_INTERVAL_SECONDS = 20;

    private final StationService stationService;
    private final BikeService bikeService;
    private final RideService rideService;
    private final SubscriptionService subscriptionService;

    // Store latest ride summaries (in real system: persisted)
    private final Map<Integer, RideSummary> summaries = new HashMap<>();

    public BikeRentalService(StationService stationService,
                             BikeService bikeService,
                             RideService rideService,
                             SubscriptionService subscriptionService) {
        this.stationService = Objects.requireNonNull(stationService);
        this.bikeService = Objects.requireNonNull(bikeService);
        this.rideService = Objects.requireNonNull(rideService);
        this.subscriptionService = Objects.requireNonNull(subscriptionService);
    }

    /** Register station & all its bikes in bike repo as well */
    public Station addStation(Station s) {
        // persist bikes first
        for (BikeType t : BikeType.values()) {
            for (Bike b : s.getBikesByType(t)) bikeService.addBike(b);
        }
        return stationService.addStation(s);
    }

    /** Browse nearby stations */
    public List<Station> browseNearbyStations(double lat, double lon, double maxKm) {
        return stationService.nearbyStations(lat, lon, maxKm);
    }

    /**
     * Unlock a bike at station and start a ride.
     * - validates active annual subscription
     * - checks out any bike of requested type from station inventory
     */
    public Ride unlockAndStartRide(String stationId, BikeType type, String userId, Instant startTime) {
        subscriptionService.getRequiredActive(userId, LocalDate.now());

        Station st = stationService.getStation(stationId);
        Bike bike = st.checkoutAny(type);
        if (bike == null) throw new NoSuchElementException("No bike available of type " + type + " at station");

        bike.unlock();
        bike.markInRide(true);

        Ride ride = rideService.startRide(bike.getId(), userId, stationId, startTime);

        // optionally push first point as station location
        rideService.ingestTelemetry(bike.getId(), new GpsPoint(st.getLatitude(), st.getLongitude(), startTime));

        return ride;
    }

    /**
     * Bike device sends telemetry every 20 seconds while riding.
     * We update:
     *  - bike location
     *  - ride route + distance
     */
    public void receiveTelemetry(String bikeId, double lat, double lon, Instant timestamp) {
        bikeService.updateLocation(bikeId, lat, lon);
        rideService.ingestTelemetry(bikeId, new GpsPoint(lat, lon, timestamp));
    }

    /**
     * Return bike to another station and show summary.
     */
    public RideSummary returnBike(int rideId, String returnStationId, Instant endTime) {
        Ride r = rideService.getRide(rideId);
        Bike b = bikeService.getBike(r.getBikeId());
        Station returnStation = stationService.getStation(returnStationId);

        // final route point can be station location (optional)
        receiveTelemetry(b.getId(), returnStation.getLatitude(), returnStation.getLongitude(), endTime);

        RideSummary summary = rideService.endRide(rideId, returnStationId, endTime);

        // mark bike state and put back to station inventory
        b.lock();
        b.markInRide(false);
        returnStation.addBike(b);

        summaries.put(rideId, summary);
        return summary;
    }

    public RideSummary getRideSummary(int rideId) {
        RideSummary s = summaries.get(rideId);
        if (s == null) throw new NoSuchElementException("Summary not found (ride may be active or not returned)");
        return s;
    }
}

/** Application Entry (demo) */
public class BikeRentalApp {
    public static void main(String[] args) {
        // Repos
        var stationRepo = new InMemoryStationRepo();
        var bikeRepo = new InMemoryBikeRepo();
        var rideRepo = new InMemoryRideRepo();
        var subRepo = new InMemorySubscriptionRepo();

        // Services
        var stationSvc = new StationService(stationRepo);
        var bikeSvc = new BikeService(bikeRepo);
        var rideSvc = new RideService(rideRepo);
        var subSvc = new SubscriptionService(subRepo);

        var app = new BikeRentalService(stationSvc, bikeSvc, rideSvc, subSvc);

        // Create subscription for user
        String userId = "User1";
        subSvc.createAnnual(userId, LocalDate.now());

        // Stations
        Station s1 = new Station("Station-Central", 12.9716, 77.5946);
        Station s2 = new Station("Station-TechPark", 12.9352, 77.6245);

        // Bikes (with deviceId)
        Bike b1 = new Bike("QR-MECH-001", BikeType.MECHANICAL, "dev-001", 12.9716, 77.5946);
        Bike b2 = new Bike("QR-ELEC-001", BikeType.ELECTRIC, "dev-002", 12.9716, 77.5946);
        s1.addBike(b1);
        s1.addBike(b2);

        app.addStation(s1);
        app.addStation(s2);

        // Browse nearby stations
        System.out.println("Nearby stations (within 10km):");
        app.browseNearbyStations(12.97, 77.59, 10).forEach(System.out::println);

        // Unlock and start ride (mechanical)
        Instant start = Instant.now();
        Ride ride = app.unlockAndStartRide(s1.getId(), BikeType.MECHANICAL, userId, start);
        System.out.println("\nStarted ride: " + ride);

        // Simulate telemetry points (normally every 20s from device)
        // Here we just send a few points quickly.
        app.receiveTelemetry(ride.getBikeId(), 12.9700, 77.5950, start.plusSeconds(20));
        app.receiveTelemetry(ride.getBikeId(), 12.9650, 77.6000, start.plusSeconds(40));
        app.receiveTelemetry(ride.getBikeId(), 12.9600, 77.6100, start.plusSeconds(60));

        // Return bike at different station + get summary
        Instant end = start.plusSeconds(120);
        RideSummary summary = app.returnBike(ride.getId(), s2.getId(), end);
        System.out.println("\nRide returned. Summary:");
        System.out.println(summary);
        System.out.println("Distance (km): " + String.format("%.3f", summary.getDistanceKm()));
        System.out.println("Route points:");
        summary.getRoute().forEach(System.out::println);

        System.out.println("\nReturn station inventory now:");
        System.out.println(s2);
    }
}

/*
-------------------------------------------
Scale / architecture notes (very compact):
-------------------------------------------
- Nearby station browse: use geo-index (S2 / geohash), cache hot areas (before/after work peaks).
- Telemetry ingest: device -> API gateway -> queue (Kafka/EventHub) -> stream processor -> ride store.
- Ride route storage: store polyline / compressed points in blob, keep summary in DB (rideId keyed).
- Station inventory: shard by city/geo-cell; maintain cache + eventual consistency.
- Availability concurrency: station checkout needs atomicity (DB row lock / Redis lock / compare-and-swap).
*/
