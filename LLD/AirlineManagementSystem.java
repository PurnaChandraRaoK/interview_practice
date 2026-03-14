import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;

// ---------------- Enums ----------------
enum FlightType { DOMESTIC, INTERNATIONAL }
enum PaymentMode { CARD, UPI, NETBANKING }
enum Role { ADMIN, USER }
enum BookingStatus { HOLD, CONFIRMED, CANCELLED, EXPIRED }

// ---------------- DTOs ----------------
final class SearchCriteria {
    final String origin, destination;
    final LocalDate date;
    final FlightType flightType;

    SearchCriteria(String origin, String destination, LocalDate date, FlightType flightType) {
        this.origin = Objects.requireNonNull(origin);
        this.destination = Objects.requireNonNull(destination);
        this.date = Objects.requireNonNull(date);
        this.flightType = Objects.requireNonNull(flightType);
    }
}

final class TripSearchResult {
    final String flightId;          // flight number
    final String tripId;            // unique per (flightId, date, departureTime)
    final LocalDateTime startTime;  // when trip starts
    final String src, dest;
    final int availableSeatsCount;

    TripSearchResult(String flightId, String tripId, LocalDateTime startTime,
                     String src, String dest, int availableSeatsCount) {
        this.flightId = flightId;
        this.tripId = tripId;
        this.startTime = startTime;
        this.src = src;
        this.dest = dest;
        this.availableSeatsCount = availableSeatsCount;
    }
}

// ---------------- Users ----------------
final class User {
    final String userId;
    final String name;
    final Role role;

    User(String userId, String name, Role role) {
        this.userId = Objects.requireNonNull(userId);
        this.name = Objects.requireNonNull(name);
        this.role = Objects.requireNonNull(role);
    }
}

// ---------------- Flight Template vs Trip ----------------
final class FlightTemplate {
    final String flightId;      // flight number / id
    final String airlineId;
    final String origin, destination;
    final FlightType type;
    final int totalSeats;

    FlightTemplate(String flightId, String airlineId, String origin, String destination,
                   FlightType type, int totalSeats) {
        this.flightId = Objects.requireNonNull(flightId);
        this.airlineId = Objects.requireNonNull(airlineId);
        this.origin = Objects.requireNonNull(origin);
        this.destination = Objects.requireNonNull(destination);
        this.type = Objects.requireNonNull(type);
        if (totalSeats <= 0) throw new IllegalArgumentException("totalSeats must be > 0");
        this.totalSeats = totalSeats;
    }
}

/**
 * Trip instance: Seat state + locking is per trip.
 * Per-seat locking (not one big lock) to avoid blocking other seat operations. [1](https://microsoftapc-my.sharepoint.com/personal/pkota_microsoft_com/_layouts/15/Doc.aspx?sourcedoc=%7BF1DDE633-32F2-46F3-8FC3-70792EF422A0%7D&file=NewLLD%20(1).docx&action=default&mobileredirect=true&DefaultItemOpen=1)
 */
final class FlightTrip {
    enum SeatState { AVAILABLE, HELD, BOOKED }

    static final class SeatRecord {
        final Lock lock = new ReentrantLock();
        SeatState state = SeatState.AVAILABLE;
        String heldBy;
        Instant holdUntil;
    }

    final String tripId;
    final String flightId;
    final String airlineId;
    final String origin, destination;
    final FlightType type;
    final LocalDate date;
    final LocalDateTime startTime;

    private final Map<Integer, SeatRecord> seats = new HashMap<>();
    private final AtomicInteger availableCount;

    FlightTrip(FlightTemplate t, LocalDate date, LocalDateTime departureTime) {
        this.flightId = t.flightId;
        this.airlineId = t.airlineId;
        this.origin = t.origin;
        this.destination = t.destination;
        this.type = t.type;
        this.date = Objects.requireNonNull(date);
        this.startTime = LocalDateTime.of(date, t.departureTime);
        this.tripId = buildTripId(t.flightId, this.startTime);

        for (int i = 1; i <= t.totalSeats; i++) seats.put(i, new SeatRecord());
        this.availableCount = new AtomicInteger(t.totalSeats);
    }

    static String buildTripId(String flightId, LocalDateTime startTime) {
        return flightId + "-" + startTime.toLocalDate() + "-" + startTime.toLocalTime();
    }

    boolean isSeatValid(int seatNo) { return seats.containsKey(seatNo); }

    int availableSeatCount() { return availableCount.get(); }

    /**
     * Hide locked seats: return only AVAILABLE seats for this trip.
     */
    List<Integer> getAvailableSeats() {
        List<Integer> res = new ArrayList<>();
        for (Map.Entry<Integer, SeatRecord> e : seats.entrySet()) {
            int seatNo = e.getKey();
            SeatRecord r = e.getValue();
            r.lock.lock();
            try {
                cleanupExpiredHoldIfNeeded(r);
                if (r.state == SeatState.AVAILABLE) res.add(seatNo);
            } finally {
                r.lock.unlock();
            }
        }
        Collections.sort(res);
        return res;
    }

    Instant holdSeat(int seatNo, String userId, Duration ttl) {
        SeatRecord r = seats.get(seatNo);
        if (r == null) throw new IllegalArgumentException("Invalid seat: " + seatNo);

        r.lock.lock();
        try {
            cleanupExpiredHoldIfNeeded(r);

            if (r.state == SeatState.BOOKED) throw new IllegalStateException("Seat already BOOKED: " + seatNo);
            if (r.state == SeatState.HELD && !userId.equals(r.heldBy))
                throw new IllegalStateException("Seat temporarily HELD by another user: " + seatNo);

            // AVAILABLE -> HELD impacts availableCount
            if (r.state == SeatState.AVAILABLE) availableCount.decrementAndGet();

            r.state = SeatState.HELD;
            r.heldBy = userId;
            r.holdUntil = Instant.now().plus(ttl);
            return r.holdUntil;
        } finally {
            r.lock.unlock();
        }
    }

    void confirmSeat(int seatNo, String userId) {
        SeatRecord r = seats.get(seatNo);
        if (r == null) throw new IllegalArgumentException("Invalid seat: " + seatNo);

        r.lock.lock();
        try {
            cleanupExpiredHoldIfNeeded(r);

            if (r.state != SeatState.HELD) throw new IllegalStateException("Seat not on HOLD: " + seatNo);
            if (!userId.equals(r.heldBy)) throw new IllegalStateException("HOLD not owned by user: " + seatNo);

            // HELD -> BOOKED (availableCount already decremented at hold time)
            r.state = SeatState.BOOKED;
            r.heldBy = null;
            r.holdUntil = null;
        } finally {
            r.lock.unlock();
        }
    }

    void cancelHold(int seatNo, String userId) {
        SeatRecord r = seats.get(seatNo);
        if (r == null) return;

        r.lock.lock();
        try {
            cleanupExpiredHoldIfNeeded(r);

            if (r.state == SeatState.HELD && userId.equals(r.heldBy)) {
                // HELD -> AVAILABLE increments availableCount
                r.state = SeatState.AVAILABLE;
                r.heldBy = null;
                r.holdUntil = null;
                availableCount.incrementAndGet();
            }
        } finally {
            r.lock.unlock();
        }
    }

    void cancelConfirmed(int seatNo) {
        SeatRecord r = seats.get(seatNo);
        if (r == null) return;

        r.lock.lock();
        try {
            if (r.state == SeatState.BOOKED) {
                // BOOKED -> AVAILABLE increments availableCount
                r.state = SeatState.AVAILABLE;
                r.heldBy = null;
                r.holdUntil = null;
                availableCount.incrementAndGet();
            }
        } finally {
            r.lock.unlock();
        }
    }

    private void cleanupExpiredHoldIfNeeded(SeatRecord r) {
        if (r.state == SeatState.HELD && r.holdUntil != null && Instant.now().isAfter(r.holdUntil)) {
            // HELD expired -> AVAILABLE increments availableCount
            r.state = SeatState.AVAILABLE;
            r.heldBy = null;
            r.holdUntil = null;
            availableCount.incrementAndGet();
        }
    }
}

// ---------------- Airline / Booking ----------------
final class Airline {
    final String airlineId;
    final String name;
    Airline(String airlineId, String name) {
        this.airlineId = Objects.requireNonNull(airlineId);
        this.name = Objects.requireNonNull(name);
    }
}

final class Booking {
    final String bookingId;
    final User user;
    final String tripId;
    final int seatNumber;
    final Instant holdUntil;

    volatile BookingStatus status = BookingStatus.HOLD;
    volatile boolean paid = false;

    Booking(String bookingId, User user, String tripId, int seatNumber, Instant holdUntil) {
        this.bookingId = Objects.requireNonNull(bookingId);
        this.user = Objects.requireNonNull(user);
        this.tripId = Objects.requireNonNull(tripId);
        this.seatNumber = seatNumber;
        this.holdUntil = Objects.requireNonNull(holdUntil);
    }

    boolean isHoldExpired() { return Instant.now().isAfter(holdUntil); }
}

// ---------------- Repositories ----------------
interface AirlineRepository { void addAirline(Airline a); Optional<Airline> getAirlineById(String id); }
final class InMemoryAirlineRepository implements AirlineRepository {
    private final Map<String, Airline> map = new ConcurrentHashMap<>();
    public void addAirline(Airline a) { map.put(a.airlineId, a); }
    public Optional<Airline> getAirlineById(String id) { return Optional.ofNullable(map.get(id)); }
}

interface FlightTemplateRepository { void addTemplate(FlightTemplate t); Optional<FlightTemplate> getTemplate(String flightId); }
final class InMemoryFlightTemplateRepo implements FlightTemplateRepository {
    private final Map<String, FlightTemplate> map = new ConcurrentHashMap<>();
    public void addTemplate(FlightTemplate t) { map.put(t.flightId, t); }
    public Optional<FlightTemplate> getTemplate(String flightId) { return Optional.ofNullable(map.get(flightId)); }
}

interface FlightTripRepository {
    void addTrip(FlightTrip trip);
    Optional<FlightTrip> getTrip(String tripId);
    List<FlightTrip> searchTrips(SearchCriteria c);
}
final class InMemoryFlightTripRepo implements FlightTripRepository {
    private final Map<String, FlightTrip> map = new ConcurrentHashMap<>();
    public void addTrip(FlightTrip trip) { map.put(trip.tripId, trip); }
    public Optional<FlightTrip> getTrip(String tripId) { return Optional.ofNullable(map.get(tripId)); }

    public List<FlightTrip> searchTrips(SearchCriteria c) {
        List<FlightTrip> res = new ArrayList<>();
        for (FlightTrip t : map.values()) {
            if (t.origin.equalsIgnoreCase(c.origin)
                    && t.destination.equalsIgnoreCase(c.destination)
                    && t.date.equals(c.date)
                    && t.type == c.flightType) res.add(t);
        }
        // sort by start time for nicer search UX
        res.sort(Comparator.comparing(x -> x.startTime));
        return res;
    }
}

interface BookingRepository { void save(Booking b); Optional<Booking> get(String id); void delete(String id); }
final class InMemoryBookingRepository implements BookingRepository {
    private final Map<String, Booking> map = new ConcurrentHashMap<>();
    public void save(Booking b) { map.put(b.bookingId, b); }
    public Optional<Booking> get(String id) { return Optional.ofNullable(map.get(id)); }
    public void delete(String id) { map.remove(id); }
}

// ---------------- Payment Strategy + Factory ----------------
interface PaymentStrategy { void pay(String bookingId); }
final class CardPayment implements PaymentStrategy { public void pay(String bookingId){ System.out.println("Card payment OK for " + bookingId);} }
final class UpiPayment implements PaymentStrategy { public void pay(String bookingId){ System.out.println("UPI payment OK for " + bookingId);} }
final class NetBankingPayment implements PaymentStrategy { public void pay(String bookingId){ System.out.println("NetBanking payment OK for " + bookingId);} }

final class PaymentFactory {
    private final Map<PaymentMode, PaymentStrategy> map = new EnumMap<>(PaymentMode.class);
    PaymentFactory() {
        map.put(PaymentMode.CARD, new CardPayment());
        map.put(PaymentMode.UPI, new UpiPayment());
        map.put(PaymentMode.NETBANKING, new NetBankingPayment());
    }
    PaymentStrategy get(PaymentMode mode) {
        PaymentStrategy s = map.get(mode);
        if (s == null) throw new IllegalArgumentException("Unsupported payment: " + mode);
        return s;
    }
}

// ---------------- Notification ----------------
interface NotificationChannel { void send(String msg); }
final class EmailNotifier implements NotificationChannel { public void send(String msg){ System.out.println("[EMAIL] " + msg);} }
final class SMSNotifier implements NotificationChannel { public void send(String msg){ System.out.println("[SMS] " + msg);} }

final class NotificationService {
    private final List<NotificationChannel> channels = new CopyOnWriteArrayList<>();
    public void subscribe(NotificationChannel c) { channels.add(c); }
    public void publish(String msg) { for (var c: channels) c.send(msg); }
}

// ---------------- Service Layer ----------------
final class AirlineService {
    private final AirlineRepository airlineRepo;
    private final FlightTemplateRepository templateRepo;
    private final FlightTripRepository tripRepo;
    private final BookingRepository bookingRepo;
    private final NotificationService notifier;
    private final PaymentFactory paymentFactory;
    private final Duration holdTtl;

    AirlineService(AirlineRepository airlineRepo,
                   FlightTemplateRepository templateRepo,
                   FlightTripRepository tripRepo,
                   BookingRepository bookingRepo,
                   NotificationService notifier,
                   PaymentFactory paymentFactory,
                   Duration holdTtl) {
        this.airlineRepo = airlineRepo;
        this.templateRepo = templateRepo;
        this.tripRepo = tripRepo;
        this.bookingRepo = bookingRepo;
        this.notifier = notifier;
        this.paymentFactory = paymentFactory;
        this.holdTtl = holdTtl;
    }

    public void registerAirline(User actor, String id, String name) {
        requireAdmin(actor);
        airlineRepo.addAirline(new Airline(id, name));
    }

    public void registerFlightTemplate(User actor, String flightId, String airlineId,
                                       String origin, String dest, FlightType type,
                                       int totalSeats) {
        requireAdmin(actor);
        if (airlineRepo.getAirlineById(airlineId).isEmpty())
            throw new RuntimeException("Airline not found: " + airlineId);

        templateRepo.addTemplate(new FlightTemplate(flightId, airlineId, origin, dest, type, totalSeats));
    }

    public void createTrip(User actor, String flightId, LocalDate date, LocalTime departureTime) {
        requireAdmin(actor);
        FlightTemplate t = templateRepo.getTemplate(flightId)
                .orElseThrow(() -> new RuntimeException("Flight template not found: " + flightId));
        tripRepo.addTrip(new FlightTrip(t, date, departureTime));
    }

    public List<TripSearchResult> searchTripsWithAvailability(SearchCriteria c) {
        List<FlightTrip> trips = tripRepo.searchTrips(c);
        List<TripSearchResult> out = new ArrayList<>();
        for (FlightTrip t : trips) {
            out.add(new TripSearchResult(
                    t.flightId,
                    t.tripId,
                    t.startTime,
                    t.origin,
                    t.destination,
                    t.availableSeatCount() // excludes locked seats because AVAILABLE->HELD decrements
            ));
        }
        return out;
    }

    public List<Integer> getAvailableSeats(String tripId) {
        FlightTrip trip = tripRepo.getTrip(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));
        return trip.getAvailableSeats(); // hides HELD + BOOKED
    }

    public Booking holdSeat(User user, String tripId, int seatNo) {
        FlightTrip trip = tripRepo.getTrip(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));
        if (!trip.isSeatValid(seatNo)) throw new RuntimeException("Invalid seat: " + seatNo);

        Instant holdUntil = trip.holdSeat(seatNo, user.userId, holdTtl);
        Booking b = new Booking(UUID.randomUUID().toString(), user, tripId, seatNo, holdUntil);
        bookingRepo.save(b);
        notifier.publish("Seat " + seatNo + " HELD by " + user.name + " for trip " + tripId);
        return b;
    }

    public void payAndConfirm(String bookingId, PaymentMode mode) {
        Booking b = bookingRepo.get(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));
        if (b.status == BookingStatus.CONFIRMED) return;
        if (b.status != BookingStatus.HOLD) throw new RuntimeException("Cannot pay. Status: " + b.status);

        FlightTrip trip = tripRepo.getTrip(b.tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + b.tripId));

        if (b.isHoldExpired()) {
            trip.cancelHold(b.seatNumber, b.user.userId);
            expire(b);
            throw new RuntimeException("Seat HOLD expired. Please hold again.");
        }

        if (!b.paid) {
            paymentFactory.get(mode).pay(b.bookingId);
            b.paid = true;
        }

        try {
            trip.confirmSeat(b.seatNumber, b.user.userId);
        } catch (RuntimeException ex) {
            trip.cancelHold(b.seatNumber, b.user.userId);
            expire(b);
            throw ex;
        }

        b.status = BookingStatus.CONFIRMED;
        bookingRepo.save(b);
        notifier.publish("Booking CONFIRMED: " + bookingId + " for trip " + b.tripId);
    }

    private void expire(Booking b) {
        b.status = BookingStatus.EXPIRED;
        bookingRepo.delete(b.bookingId);
        notifier.publish("Booking EXPIRED: " + b.bookingId);
    }

    private void requireAdmin(User actor) {
        if (actor == null || actor.role != Role.ADMIN) throw new RuntimeException("Admin access required");
    }
}

// ---------------- Demo ----------------
public class AirlineSystemMain_SearchAndPerSeatLock {
    public static void main(String[] args) throws Exception {
        var airlineRepo = new InMemoryAirlineRepository();
        var templateRepo = new InMemoryFlightTemplateRepo();
        var tripRepo = new InMemoryFlightTripRepo();
        var bookingRepo = new InMemoryBookingRepository();
        var notifier = new NotificationService();
        notifier.subscribe(new EmailNotifier());
        notifier.subscribe(new SMSNotifier());

        var service = new AirlineService(
                airlineRepo, templateRepo, tripRepo, bookingRepo,
                notifier, new PaymentFactory(), Duration.ofMinutes(2)
        );

        User admin = new User("A0", "Admin", Role.ADMIN);
        service.registerAirline(admin, "A1", "Indigo");
        service.registerFlightTemplate(admin, "F101", "A1", "Delhi", "Mumbai",
                FlightType.DOMESTIC, 10, LocalTime.of(9, 30));

        service.createTrip(admin, "F101", LocalDate.of(2026, 8, 15));
        service.createTrip(admin, "F101", LocalDate.of(2026, 8, 16));

        // Search -> show details the way you asked
        var results = service.searchTripsWithAvailability(
                new SearchCriteria("Delhi", "Mumbai", LocalDate.of(2026, 8, 15), FlightType.DOMESTIC)
        );
        System.out.println("Search results:");
        results.forEach(System.out::println);

        // User selects a flight/trip row => we use tripId (not just flightId)
        String selectedTripId = results.get(0).tripId;
        System.out.println("Available seats for selected trip " + selectedTripId + ": " +
                service.getAvailableSeats(selectedTripId));

        // Concurrency demo: lock seat 1 and seat 2 simultaneously in SAME trip (no global lock bottleneck)
        User u1 = new User("U1", "Alice", Role.USER);
        User u2 = new User("U2", "Bob", Role.USER);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> service.holdSeat(u1, selectedTripId, 1));
        pool.submit(() -> service.holdSeat(u2, selectedTripId, 2));
        pool.shutdown();
        pool.awaitTermination(3, TimeUnit.SECONDS);

        // Locked seats won't appear now
        System.out.println("Available seats after holds: " + service.getAvailableSeats(selectedTripId));
    }
}
