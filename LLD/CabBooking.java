@SpringBootApplication
public class CabBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CabBookingApplication.class, args);
    }

    // ---------- Beans (simple wiring) ----------
    @Bean
    public CabsManager cabsManager() { return new CabsManager(); }

    @Bean
    public RidersManager ridersManager() { return new RidersManager(); }

    @Bean
    public DriversManager driversManager() { return new DriversManager(); }

    @Bean
    public CabMatchingStrategy cabMatchingStrategy() { return new NearestCabMatchingStrategy(); }

    @Bean
    public PricingStrategy pricingStrategy() { return new PerKmPricingStrategy(new BigDecimal("10.00")); }

    @Bean
    public TripsManager tripsManager(CabsManager cabsManager,
                                    RidersManager ridersManager,
                                    DriversManager driversManager,
                                    CabMatchingStrategy cabMatchingStrategy,
                                    PricingStrategy pricingStrategy) {
        return new TripsManager(cabsManager, ridersManager, driversManager, cabMatchingStrategy, pricingStrategy);
    }

    // ---------- Controllers ----------
    @RestController
    public static class DriversController {
        private final DriversManager driversManager;

        public DriversController(DriversManager driversManager) {
            this.driversManager = driversManager;
        }

        @PostMapping("/register/driver")
        public ResponseEntity<PostResponse> registerDriver(@RequestParam String driverId,
                                                          @RequestParam String driverName,
                                                          @RequestParam(required = false) String contact,
                                                          @RequestParam(required = false) String licenseNumber) {
            driversManager.createDriver(new Driver(driverId, driverName, contact, licenseNumber));
            return ResponseEntity.ok(PostResponse.ok());
        }

        @GetMapping("/driver/rating")
        public ResponseEntity<DriverRatingResponse> driverRating(@RequestParam String driverId) {
            Driver d = driversManager.getDriver(driverId);
            return ResponseEntity.ok(new DriverRatingResponse(d.getId(), d.getName(), d.getAverageRating(), d.getRatingCount()));
        }
    }

    @RestController
    public static class CabsController {
        private final CabsManager cabsManager;
        private final DriversManager driversManager;
        private final TripsManager tripsManager;

        public CabsController(CabsManager cabsManager, DriversManager driversManager, TripsManager tripsManager) {
            this.cabsManager = cabsManager;
            this.driversManager = driversManager;
            this.tripsManager = tripsManager;
        }

        /**
         * Now cab is registered with a driverId (Driver is separate).
         */
        @PostMapping("/register/cab")
        public ResponseEntity<PostResponse> registerCab(@RequestParam String cabId,
                                                       @RequestParam String driverId) {
            // validate driver exists
            driversManager.getDriver(driverId);
            cabsManager.createCab(new Cab(cabId, driverId));
            return ResponseEntity.ok(PostResponse.ok());
        }

        @PostMapping("/update/cab/location")
        public ResponseEntity<PostResponse> updateCabLocation(@RequestParam String cabId,
                                                             @RequestParam double x,
                                                             @RequestParam double y) {
            cabsManager.updateCabLocation(cabId, new Location(x, y));
            return ResponseEntity.ok(PostResponse.ok());
        }

        @PostMapping("/endTrip")
        public ResponseEntity<PostResponse> endTrip(@RequestParam String cabId) {
            tripsManager.endTrip(cabsManager.getCab(cabId));
            return ResponseEntity.ok(PostResponse.ok());
        }

        @PostMapping("/cancelTrip")
        public ResponseEntity<PostResponse> cancelTrip(@RequestParam String cabId) {
            tripsManager.cancelTrip(cabsManager.getCab(cabId));
            return ResponseEntity.ok(PostResponse.ok());
        }
    }

    @RestController
    public static class RidersController {
        private final RidersManager ridersManager;
        private final TripsManager tripsManager;

        public RidersController(RidersManager ridersManager, TripsManager tripsManager) {
            this.ridersManager = ridersManager;
            this.tripsManager = tripsManager;
        }

        @PostMapping("/register/rider")
        public ResponseEntity<PostResponse> registerRider(@RequestParam String riderId,
                                                         @RequestParam String riderName,
                                                         @RequestParam(required = false) String contact) {
            ridersManager.createRider(new Rider(riderId, riderName, contact));
            return ResponseEntity.ok(PostResponse.ok());
        }

        @PostMapping("/book")
        public ResponseEntity<Trip> book(@RequestParam String riderId,
                                         @RequestParam double sourceX,
                                         @RequestParam double sourceY,
                                         @RequestParam double destX,
                                         @RequestParam double destY) {
            Rider rider = ridersManager.getRider(riderId);
            return ResponseEntity.ok(
                    tripsManager.createTrip(rider,
                            new Location(sourceX, sourceY),
                            new Location(destX, destY))
            );
        }

        @GetMapping("/history")
        public ResponseEntity<List<Trip>> history(@RequestParam String riderId) {
            Rider rider = ridersManager.getRider(riderId);
            return ResponseEntity.ok(tripsManager.tripHistory(rider));
        }

        /**
         * Rider rates a completed trip. This updates:
         *  - Trip.rating
         *  - Driver aggregated rating
         */
        @PostMapping("/rateTrip")
        public ResponseEntity<PostResponse> rateTrip(@RequestParam String riderId,
                                                     @RequestParam String tripId,
                                                     @RequestParam int rating) {
            // validate rider exists
            ridersManager.getRider(riderId);
            tripsManager.rateTrip(tripId, rating);
            return ResponseEntity.ok(PostResponse.ok());
        }
    }

    // ---------- Exception -> HTTP mapping ----------
    @RestControllerAdvice
    public static class ApiExceptionHandler {

        @ExceptionHandler(DomainException.class)
        public ResponseEntity<PostResponse> handleDomain(DomainException ex) {
            return ResponseEntity.status(ex.status()).body(PostResponse.error(ex.getMessage()));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<PostResponse> handleGeneric(Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(PostResponse.error("internal_error"));
        }
    }

    // ---------- Domain ----------
    public static abstract class User {
        private final String id;
        private final String name;
        private final String contact;

        protected User(String id, String name, String contact) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
            this.id = id;
            this.name = name;
            this.contact = contact;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getContact() { return contact; }
    }

    public static final class Rider extends User {
        public Rider(String id, String name, String contact) {
            super(id, name, contact);
        }
    }

    /**
     * Driver is a separate class now, not merged into Cab.
     * Maintains rating aggregation: sum + count -> avg.
     */
    public static final class Driver extends User {
        private final String licenseNumber;

        // aggregated rating fields (thread-safe updates via synchronized method)
        private long ratingCount = 0;
        private long ratingSum = 0;

        public Driver(String id, String name, String contact, String licenseNumber) {
            super(id, name, contact);
            this.licenseNumber = licenseNumber;
        }

        public String getLicenseNumber() { return licenseNumber; }

        public synchronized void addRating(int rating) {
            ratingSum += rating;
            ratingCount += 1;
        }

        public synchronized double getAverageRating() {
            if (ratingCount == 0) return 0.0;
            return (double) ratingSum / ratingCount;
        }

        public synchronized long getRatingCount() {
            return ratingCount;
        }
    }

    /**
     * Cab now references driverId (composition).
     * Booking is still based on Cab availability.
     */
    public static final class Cab {
        private final String id;
        private final String driverId;
        private volatile Location currentLocation;
        private volatile Trip currentTrip; // availability = currentTrip == null

        public Cab(String id, String driverId) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("cab id required");
            if (driverId == null || driverId.isBlank()) throw new IllegalArgumentException("driverId required");
            this.id = id;
            this.driverId = driverId;
        }

        public String getId() { return id; }
        public String getDriverId() { return driverId; }

        public Location getCurrentLocation() { return currentLocation; }
        public void setCurrentLocation(Location currentLocation) { this.currentLocation = currentLocation; }

        public Trip getCurrentTrip() { return currentTrip; }
        public void setCurrentTrip(Trip currentTrip) { this.currentTrip = currentTrip; }

        public boolean isAvailable() { return currentTrip == null; }
    }

    public static final class Location {
        private final double x;
        private final double y;

        public Location(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double distance(Location other) {
            return Math.hypot(this.x - other.x, this.y - other.y);
        }

        @Override
        public String toString() {
            return "Location{" + "x=" + x + ", y=" + y + '}';
        }
    }

    public enum TripStatus {
        IN_PROGRESS,
        FINISHED,
        CANCELLED
    }

    public static final class Trip {
        private final String id;
        private final String riderId;
        private final String cabId;
        private final String driverId;

        private final TripStatus status;
        private final BigDecimal price;
        private final Location fromPoint;
        private final Location toPoint;
        private final Instant createdAt;
        private final Instant endedAt;

        // rating belongs to the trip (contextual)
        private final Integer rating; // null until rated

        private Trip(String id,
                     String riderId,
                     String cabId,
                     String driverId,
                     TripStatus status,
                     BigDecimal price,
                     Location fromPoint,
                     Location toPoint,
                     Instant createdAt,
                     Instant endedAt,
                     Integer rating) {
            this.id = id;
            this.riderId = riderId;
            this.cabId = cabId;
            this.driverId = driverId;
            this.status = status;
            this.price = price;
            this.fromPoint = fromPoint;
            this.toPoint = toPoint;
            this.createdAt = createdAt;
            this.endedAt = endedAt;
            this.rating = rating;
        }

        public static Trip inProgress(String riderId, String cabId, String driverId,
                                      BigDecimal price, Location from, Location to) {
            return new Trip(UUID.randomUUID().toString(), riderId, cabId, driverId,
                    TripStatus.IN_PROGRESS, price, from, to, Instant.now(), null, null);
        }

        public Trip finish() {
            if (status != TripStatus.IN_PROGRESS) return this;
            return new Trip(id, riderId, cabId, driverId,
                    TripStatus.FINISHED, price, fromPoint, toPoint, createdAt, Instant.now(), rating);
        }

        public Trip cancel() {
            if (status != TripStatus.IN_PROGRESS) return this;
            return new Trip(id, riderId, cabId, driverId,
                    TripStatus.CANCELLED, price, fromPoint, toPoint, createdAt, Instant.now(), rating);
        }

        public Trip rate(int rating) {
            return new Trip(id, riderId, cabId, driverId,
                    status, price, fromPoint, toPoint, createdAt, endedAt, rating);
        }

        public String getId() { return id; }
        public String getRiderId() { return riderId; }
        public String getCabId() { return cabId; }
        public String getDriverId() { return driverId; }
        public TripStatus getStatus() { return status; }
        public BigDecimal getPrice() { return price; }
        public Location getFromPoint() { return fromPoint; }
        public Location getToPoint() { return toPoint; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getEndedAt() { return endedAt; }
        public Integer getRating() { return rating; }
    }

    // ---------- Managers (in-memory) ----------
    public static final class DriversManager {
        private final Map<String, Driver> drivers = new ConcurrentHashMap<>();

        public void createDriver(Driver newDriver) {
            Objects.requireNonNull(newDriver, "newDriver");
            Driver prev = drivers.putIfAbsent(newDriver.getId(), newDriver);
            if (prev != null) throw new DriverAlreadyExistsException();
        }

        public Driver getDriver(String driverId) {
            Driver d = drivers.get(driverId);
            if (d == null) throw new DriverNotFoundException();
            return d;
        }

        public void addRating(String driverId, int rating) {
            Driver d = getDriver(driverId);
            d.addRating(rating);
        }
    }

    public static final class CabsManager {
        private final Map<String, Cab> cabs = new ConcurrentHashMap<>();

        public void createCab(Cab newCab) {
            Objects.requireNonNull(newCab, "newCab");
            Cab prev = cabs.putIfAbsent(newCab.getId(), newCab);
            if (prev != null) throw new CabAlreadyExistsException();
        }

        public Cab getCab(String cabId) {
            Cab cab = cabs.get(cabId);
            if (cab == null) throw new CabNotFoundException();
            return cab;
        }

        public void updateCabLocation(String cabId, Location newLocation) {
            Objects.requireNonNull(newLocation, "newLocation");
            Cab cab = getCab(cabId);
            cab.setCurrentLocation(newLocation);
        }

        public List<Cab> getCabs(Location fromPoint, double maxDistance) {
            Objects.requireNonNull(fromPoint, "fromPoint");
            List<Cab> result = new ArrayList<>();
            for (Cab cab : cabs.values()) {
                Location loc = cab.getCurrentLocation();
                if (loc == null) continue;
                if (loc.distance(fromPoint) <= maxDistance) result.add(cab);
            }
            return result;
        }
    }

    public static final class RidersManager {
        private final Map<String, Rider> riders = new ConcurrentHashMap<>();

        public void createRider(Rider newRider) {
            Objects.requireNonNull(newRider, "newRider");
            Rider prev = riders.putIfAbsent(newRider.getId(), newRider);
            if (prev != null) throw new RiderAlreadyExistsException();
        }

        public Rider getRider(String riderId) {
            Rider rider = riders.get(riderId);
            if (rider == null) throw new RiderNotFoundException();
            return rider;
        }
    }

    public static final class TripsManager {
        public static final double MAX_ALLOWED_TRIP_MATCHING_DISTANCE = 10.0;

        private final Map<String, List<Trip>> tripsByRider = new ConcurrentHashMap<>();
        private final Map<String, Trip> tripsById = new ConcurrentHashMap<>();

        private final CabsManager cabsManager;
        private final RidersManager ridersManager;
        private final DriversManager driversManager;
        private final CabMatchingStrategy cabMatchingStrategy;
        private final PricingStrategy pricingStrategy;

        public TripsManager(CabsManager cabsManager,
                            RidersManager ridersManager,
                            DriversManager driversManager,
                            CabMatchingStrategy cabMatchingStrategy,
                            PricingStrategy pricingStrategy) {
            this.cabsManager = cabsManager;
            this.ridersManager = ridersManager;
            this.driversManager = driversManager;
            this.cabMatchingStrategy = cabMatchingStrategy;
            this.pricingStrategy = pricingStrategy;
        }

        public Trip createTrip(Rider rider, Location fromPoint, Location toPoint) {
            Objects.requireNonNull(rider, "rider");
            Objects.requireNonNull(fromPoint, "fromPoint");
            Objects.requireNonNull(toPoint, "toPoint");

            List<Cab> closeBy = cabsManager.getCabs(fromPoint, MAX_ALLOWED_TRIP_MATCHING_DISTANCE);

            List<Cab> closeByAvailable = new ArrayList<>();
            for (Cab cab : closeBy) {
                if (cab.isAvailable()) closeByAvailable.add(cab);
            }

            Cab selected = cabMatchingStrategy.matchCabToRider(rider, closeByAvailable, fromPoint, toPoint);
            if (selected == null) throw new NoCabsAvailableException();

            synchronized (selected) {
                if (!selected.isAvailable()) throw new NoCabsAvailableException();

                // validate driver exists (composition)
                driversManager.getDriver(selected.getDriverId());

                BigDecimal price = pricingStrategy.findPrice(fromPoint, toPoint);
                Trip trip = Trip.inProgress(rider.getId(), selected.getId(), selected.getDriverId(),
                        price, fromPoint, toPoint);

                tripsById.put(trip.getId(), trip);
                tripsByRider.computeIfAbsent(rider.getId(), k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(trip);

                selected.setCurrentTrip(trip);
                return trip;
            }
        }

        public List<Trip> tripHistory(Rider rider) {
            Objects.requireNonNull(rider, "rider");
            return tripsByRider.getOrDefault(rider.getId(), Collections.emptyList());
        }

        public void endTrip(Cab cab) {
            Objects.requireNonNull(cab, "cab");
            synchronized (cab) {
                Trip current = cab.getCurrentTrip();
                if (current == null) throw new TripNotFoundException();

                Trip finished = current.finish();
                tripsById.put(finished.getId(), finished);

                cab.setCurrentTrip(null);
            }
        }

        public void cancelTrip(Cab cab) {
            Objects.requireNonNull(cab, "cab");
            synchronized (cab) {
                Trip current = cab.getCurrentTrip();
                if (current == null) throw new TripNotFoundException();

                Trip cancelled = current.cancel();
                tripsById.put(cancelled.getId(), cancelled);

                cab.setCurrentTrip(null);
            }
        }

        /**
         * Rating rules:
         *  - rating allowed only when trip is FINISHED
         *  - only once (idempotent-ish)
         *  - updates driver aggregation
         */
        public void rateTrip(String tripId, int rating) {
            if (rating < 1 || rating > 5) throw new InvalidRatingException();

            Trip trip = tripsById.get(tripId);
            if (trip == null) throw new TripNotFoundException();

            if (trip.getStatus() != TripStatus.FINISHED) throw new TripNotRateableException();
            if (trip.getRating() != null) return; // already rated, treat as idempotent

            Trip rated = trip.rate(rating);
            tripsById.put(rated.getId(), rated);

            // update driver aggregate rating
            driversManager.addRating(rated.getDriverId(), rating);

            // also update the trip object inside rider history list (minimal in-memory consistency)
            List<Trip> list = tripsByRider.getOrDefault(rated.getRiderId(), Collections.emptyList());
            synchronized (list) {
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).getId().equals(rated.getId())) {
                        list.set(i, rated);
                        break;
                    }
                }
            }
        }
    }

    // ---------- Strategy Pattern ----------
    public interface CabMatchingStrategy {
        Cab matchCabToRider(Rider rider, List<Cab> candidateCabs, Location fromPoint, Location toPoint);
    }

    public static final class NearestCabMatchingStrategy implements CabMatchingStrategy {
        @Override
        public Cab matchCabToRider(Rider rider, List<Cab> candidateCabs, Location fromPoint, Location toPoint) {
            if (candidateCabs == null || candidateCabs.isEmpty()) return null;

            Cab best = null;
            double bestDist = Double.MAX_VALUE;
            for (Cab cab : candidateCabs) {
                Location loc = cab.getCurrentLocation();
                if (loc == null) continue;
                double d = loc.distance(fromPoint);
                if (d < bestDist) {
                    bestDist = d;
                    best = cab;
                }
            }
            return best;
        }
    }

    public interface PricingStrategy {
        BigDecimal findPrice(Location fromPoint, Location toPoint);
    }

    public static final class PerKmPricingStrategy implements PricingStrategy {
        private final BigDecimal perKmRate;

        public PerKmPricingStrategy(BigDecimal perKmRate) {
            this.perKmRate = perKmRate;
        }

        @Override
        public BigDecimal findPrice(Location fromPoint, Location toPoint) {
            double dist = fromPoint.distance(toPoint);
            return perKmRate.multiply(BigDecimal.valueOf(dist)).setScale(2, RoundingMode.HALF_UP);
        }
    }

    // ---------- API response ----------
    public static final class PostResponse {
        private final String status;
        private final String message;

        private PostResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public static PostResponse ok() { return new PostResponse("ok", null); }
        public static PostResponse error(String msg) { return new PostResponse("error", msg); }

        public String getStatus() { return status; }
        public String getMessage() { return message; }
    }

    public static final class DriverRatingResponse {
        private final String driverId;
        private final String driverName;
        private final double averageRating;
        private final long ratingCount;

        public DriverRatingResponse(String driverId, String driverName, double averageRating, long ratingCount) {
            this.driverId = driverId;
            this.driverName = driverName;
            this.averageRating = averageRating;
            this.ratingCount = ratingCount;
        }

        public String getDriverId() { return driverId; }
        public String getDriverName() { return driverName; }
        public double getAverageRating() { return averageRating; }
        public long getRatingCount() { return ratingCount; }
    }

    // ---------- Exceptions ----------
    public static abstract class DomainException extends RuntimeException {
        private final HttpStatus status;

        protected DomainException(String message, HttpStatus status) {
            super(message);
            this.status = status;
        }

        public HttpStatus status() { return status; }
    }

    public static final class CabAlreadyExistsException extends DomainException {
        public CabAlreadyExistsException() { super("cab_already_exists", HttpStatus.CONFLICT); }
    }

    public static final class CabNotFoundException extends DomainException {
        public CabNotFoundException() { super("cab_not_found", HttpStatus.NOT_FOUND); }
    }

    public static final class RiderAlreadyExistsException extends DomainException {
        public RiderAlreadyExistsException() { super("rider_already_exists", HttpStatus.CONFLICT); }
    }

    public static final class RiderNotFoundException extends DomainException {
        public RiderNotFoundException() { super("rider_not_found", HttpStatus.NOT_FOUND); }
    }

    public static final class DriverAlreadyExistsException extends DomainException {
        public DriverAlreadyExistsException() { super("driver_already_exists", HttpStatus.CONFLICT); }
    }

    public static final class DriverNotFoundException extends DomainException {
        public DriverNotFoundException() { super("driver_not_found", HttpStatus.NOT_FOUND); }
    }

    public static final class NoCabsAvailableException extends DomainException {
        public NoCabsAvailableException() { super("no_cabs_available", HttpStatus.CONFLICT); }
    }

    public static final class TripNotFoundException extends DomainException {
        public TripNotFoundException() { super("trip_not_found", HttpStatus.NOT_FOUND); }
    }

    public static final class InvalidRatingException extends DomainException {
        public InvalidRatingException() { super("invalid_rating", HttpStatus.BAD_REQUEST); }
    }

    public static final class TripNotRateableException extends DomainException {
        public TripNotRateableException() { super("trip_not_rateable", HttpStatus.CONFLICT); }
    }
}
