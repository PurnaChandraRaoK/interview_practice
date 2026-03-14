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
enum SeatType { ECONOMY, PREMIUM }

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
    final String flightId;
    final String tripId;
    final LocalDateTime startTime;
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

    @Override public String toString() {
        return "TripSearchResult{flightId=" + flightId +
                ", tripId=" + tripId +
                ", startTime=" + startTime +
                ", src=" + src +
                ", dest=" + dest +
                ", availableSeats=" + availableSeatsCount + "}";
    }
}

final class SeatInfo {
    final int seatNo;
    final SeatType type;
    final long price; // in smallest unit you choose (e.g., INR)

    SeatInfo(int seatNo, SeatType type, long price) {
        this.seatNo = seatNo;
        this.type = Objects.requireNonNull(type);
        this.price = price;
    }

    @Override public String toString() {
        return "SeatInfo{seat=" + seatNo + ", type=" + type + ", price=" + price + "}";
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
    final String flightId;            // flight number / id
    final String airlineId;
    final String origin, destination;
    final FlightType type;
    final int totalSeats;
    final int premiumSeatCount;       // e.g., first N seats are PREMIUM
    final LocalTime departureTime;
    final Map<SeatType, Long> priceByType;

    FlightTemplate(String flightId, String airlineId, String origin, String destination,
                   FlightType type, int totalSeats, int premiumSeatCount,
                   LocalTime departureTime, Map<SeatType, Long> priceByType) {
        this.flightId = Objects.requireNonNull(flightId);
        this.airlineId = Objects.requireNonNull(airlineId);
        this.origin = Objects.requireNonNull(origin);
        this.destination = Objects.requireNonNull(destination);
        this.type = Objects.requireNonNull(type);
        if (totalSeats <= 0) throw new IllegalArgumentException("totalSeats must be > 0");
        if (premiumSeatCount < 0 || premiumSeatCount > totalSeats)
            throw new IllegalArgumentException("premiumSeatCount must be between 0 and totalSeats");

        this.totalSeats = totalSeats;
        this.premiumSeatCount = premiumSeatCount;
        this.departureTime = Objects.requireNonNull(departureTime);

        Objects.requireNonNull(priceByType);
        if (!priceByType.containsKey(SeatType.ECONOMY))
            throw new IllegalArgumentException("priceByType must contain ECONOMY price");
        if (!priceByType.containsKey(SeatType.PREMIUM))
            throw new IllegalArgumentException("priceByType must contain PREMIUM price");
        this.priceByType = Map.copyOf(priceByType);
    }
}

final class FlightTrip {
    enum SeatState { AVAILABLE, HELD, BOOKED }

    static final class SeatRecord {
        final int seatNo;
        final SeatType type;
        final long price;
        final Lock lock = new ReentrantLock();

        SeatState state = SeatState.AVAILABLE;
        String heldBy;
        Instant holdUntil;

        SeatRecord(int seatNo, SeatType type, long price) {
            this.seatNo = seatNo;
            this.type = type;
            this.price = price;
        }

        SeatInfo toInfo() { return new SeatInfo(seatNo, type, price); }
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

    FlightTrip(FlightTemplate t, LocalDate date) {
        this.flightId = t.flightId;
        this.airlineId = t.airlineId;
        this.origin = t.origin;
        this.destination = t.destination;
        this.type = t.type;
        this.date = Objects.requireNonNull(date);
        this.startTime = LocalDateTime.of(date, t.departureTime);
        this.tripId = buildTripId(t.flightId, this.startTime);

        for (int i = 1; i <= t.totalSeats; i++) {
            SeatType seatType = (i <= t.premiumSeatCount) ? SeatType.PREMIUM : SeatType.ECONOMY;
            long price = t.priceByType.get(seatType);
            seats.put(i, new SeatRecord(i, seatType, price));
        }
        this.availableCount = new AtomicInteger(t.totalSeats);
    }

    static String buildTripId(String flightId, LocalDateTime startTime) {
        return flightId + "-" + startTime.toLocalDate() + "-" + startTime.toLocalTime();
    }

    boolean isSeatValid(int seatNo) { return seats.containsKey(seatNo); }
    int availableSeatCount() { return availableCount.get(); }

    // ✅ "once customer selects flightId, show seats available for that flight in that trip"
    // Returns ONLY AVAILABLE seats (HELD/BOOKED hidden), includes type & price.
    List<SeatInfo> getAvailableSeats() {
        List<SeatInfo> res = new ArrayList<>();
        for (SeatRecord r : seats.values()) {
            r.lock.lock();
            try {
                cleanupExpiredHoldIfNeeded(r);
                if (r.state == SeatState.AVAILABLE) res.add(r.toInfo());
            } finally {
                r.lock.unlock();
            }
        }
        res.sort(Comparator.comparingInt(s -> s.seatNo));
        return res;
    }

    // Holds multiple seats atomically (either all held or none) and returns total price + holdUntil.
    HoldResult holdSeats(List<Integer> seatNos, String userId, Duration ttl) {
        Objects.requireNonNull(seatNos);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(ttl);
        if (seatNos.isEmpty()) throw new IllegalArgumentException("seatNos cannot be empty");

        List<Integer> uniqueSorted = seatNos.stream().distinct().sorted().toList();

        // Gather records & validate seat numbers
        List<SeatRecord> records = new ArrayList<>(uniqueSorted.size());
        for (int seatNo : uniqueSorted) {
            SeatRecord r = seats.get(seatNo);
            if (r == null) throw new IllegalArgumentException("Invalid seat: " + seatNo);
            records.add(r);
        }

        // Acquire locks in seatNo order => prevents deadlock
        for (SeatRecord r : records) r.lock.lock();
        try {
            // First pass: cleanup expiry & validate all seats can be held
            for (SeatRecord r : records) {
                cleanupExpiredHoldIfNeeded(r);
                if (r.state == SeatState.BOOKED)
                    throw new IllegalStateException("Seat already BOOKED: " + r.seatNo);
                if (r.state == SeatState.HELD && !userId.equals(r.heldBy))
                    throw new IllegalStateException("Seat temporarily HELD by another user: " + r.seatNo);
            }

            Instant holdUntil = Instant.now().plus(ttl);
            long total = 0;

            // Second pass: apply holds
            for (SeatRecord r : records) {
                if (r.state == SeatState.AVAILABLE) availableCount.decrementAndGet();
                r.state = SeatState.HELD;
                r.heldBy = userId;
                r.holdUntil = holdUntil;
                total += r.price;
            }

            return new HoldResult(holdUntil, total, records.stream().map(SeatRecord::toInfo).toList());
        } finally {
            for (int i = records.size() - 1; i >= 0; i--) records.get(i).lock.unlock();
        }
    }

    void confirmSeats(List<Integer> seatNos, String userId) {
        Objects.requireNonNull(seatNos);
        Objects.requireNonNull(userId);
        if (seatNos.isEmpty()) throw new IllegalArgumentException("seatNos cannot be empty");

        List<Integer> uniqueSorted = seatNos.stream().distinct().sorted().toList();
        List<SeatRecord> records = new ArrayList<>(uniqueSorted.size());
        for (int seatNo : uniqueSorted) {
            SeatRecord r = seats.get(seatNo);
            if (r == null) throw new IllegalArgumentException("Invalid seat: " + seatNo);
            records.add(r);
        }

        for (SeatRecord r : records) r.lock.lock();
        try {
            for (SeatRecord r : records) {
                cleanupExpiredHoldIfNeeded(r);
                if (r.state != SeatState.HELD) throw new IllegalStateException("Seat not on HOLD: " + r.seatNo);
                if (!userId.equals(r.heldBy)) throw new IllegalStateException("HOLD not owned by user: " + r.seatNo);
            }
            for (SeatRecord r : records) {
                r.state = SeatState.BOOKED;
                r.heldBy = null;
                r.holdUntil = null;
            }
        } finally {
            for (int i = records.size() - 1; i >= 0; i--) records.get(i).lock.unlock();
        }
    }

    void cancelHold(List<Integer> seatNos, String userId) {
        Objects.requireNonNull(seatNos);
        Objects.requireNonNull(userId);
        if (seatNos.isEmpty()) return;

        List<Integer> uniqueSorted = seatNos.stream().distinct().sorted().toList();
        List<SeatRecord> records = new ArrayList<>(uniqueSorted.size());
        for (int seatNo : uniqueSorted) {
            SeatRecord r = seats.get(seatNo);
            if (r != null) records.add(r);
        }

        for (SeatRecord r : records) r.lock.lock();
        try {
            for (SeatRecord r : records) {
                cleanupExpiredHoldIfNeeded(r);
                if (r.state == SeatState.HELD && userId.equals(r.heldBy)) {
                    r.state = SeatState.AVAILABLE;
                    r.heldBy = null;
                    r.holdUntil = null;
                    availableCount.incrementAndGet();
                }
            }
        } finally {
            for (int i = records.size() - 1; i >= 0; i--) records.get(i).lock.unlock();
        }
    }

    private void cleanupExpiredHoldIfNeeded(SeatRecord r) {
        if (r.state == SeatState.HELD && r.holdUntil != null && Instant.now().isAfter(r.holdUntil)) {
            r.state = SeatState.AVAILABLE;
            r.heldBy = null;
            r.holdUntil = null;
            availableCount.incrementAndGet();
        }
    }

    static final class HoldResult {
        final Instant holdUntil;
        final long totalPrice;
        final List<SeatInfo> seats;

        HoldResult(Instant holdUntil, long totalPrice, List<SeatInfo> seats) {
            this.holdUntil = holdUntil;
            this.totalPrice = totalPrice;
            this.seats = seats;
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
    final List<SeatInfo> seats;   // ✅ includes type + price
    final Instant holdUntil;
    final long totalPrice;        // ✅ final booking price (sum of seat prices)

    volatile BookingStatus status = BookingStatus.HOLD;
    volatile boolean paid = false;

    Booking(String bookingId, User user, String tripId,
            List<SeatInfo> seats, Instant holdUntil, long totalPrice) {
        this.bookingId = Objects.requireNonNull(bookingId);
        this.user = Objects.requireNonNull(user);
        this.tripId = Objects.requireNonNull(tripId);
        this.seats = List.copyOf(seats);
        this.holdUntil = Objects.requireNonNull(holdUntil);
        this.totalPrice = totalPrice;
    }

    boolean isHoldExpired() { return Instant.now().isAfter(holdUntil); }
    List<Integer> seatNos() { return seats.stream().map(s -> s.seatNo).toList(); }
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
interface PaymentStrategy { void pay(String bookingId, long amount); }

final class CardPayment implements PaymentStrategy {
    public void pay(String bookingId, long amount) { System.out.println("Card payment OK for " + bookingId + " amount=" + amount); }
}
final class UpiPayment implements PaymentStrategy {
    public void pay(String bookingId, long amount) { System.out.println("UPI payment OK for " + bookingId + " amount=" + amount); }
}
final class NetBankingPayment implements PaymentStrategy {
    public void pay(String bookingId, long amount) { System.out.println("NetBanking payment OK for " + bookingId + " amount=" + amount); }
}

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
                                       int totalSeats, int premiumSeatCount,
                                       LocalTime departureTime,
                                       long economyPrice, long premiumPrice) {
        requireAdmin(actor);
        if (airlineRepo.getAirlineById(airlineId).isEmpty())
            throw new RuntimeException("Airline not found: " + airlineId);

        Map<SeatType, Long> priceByType = Map.of(
                SeatType.ECONOMY, economyPrice,
                SeatType.PREMIUM, premiumPrice
        );

        templateRepo.addTemplate(new FlightTemplate(
                flightId, airlineId, origin, dest, type,
                totalSeats, premiumSeatCount, departureTime, priceByType
        ));
    }

    public void createTrip(User actor, String flightId, LocalDate date) {
        requireAdmin(actor);
        FlightTemplate t = templateRepo.getTemplate(flightId)
                .orElseThrow(() -> new RuntimeException("Flight template not found: " + flightId));
        tripRepo.addTrip(new FlightTrip(t, date));
    }

    // ✅ Search API as requested: flightId, startTime, src, dest, tripId
    public List<TripSearchResult> searchTripsWithAvailability(SearchCriteria c) {
        List<FlightTrip> trips = tripRepo.searchTrips(c);
        List<TripSearchResult> out = new ArrayList<>();
        for (FlightTrip t : trips) {
            out.add(new TripSearchResult(
                    t.flightId, t.tripId, t.startTime, t.origin, t.destination, t.availableSeatCount()
            ));
        }
        return out;
    }

    // ✅ After selecting a tripId, show seats available (with type + price)
    public List<SeatInfo> getAvailableSeats(String tripId) {
        FlightTrip trip = tripRepo.getTrip(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));
        return trip.getAvailableSeats();
    }

    // ✅ Hold seats and return Booking with final total price
    public Booking holdSeats(User user, String tripId, List<Integer> seatNos) {
        FlightTrip trip = tripRepo.getTrip(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));

        FlightTrip.HoldResult hold = trip.holdSeats(seatNos, user.userId, holdTtl);

        Booking b = new Booking(
                UUID.randomUUID().toString(),
                user,
                tripId,
                hold.seats,
                hold.holdUntil,
                hold.totalPrice
        );
        bookingRepo.save(b);

        notifier.publish("Seats " + seatNos + " HELD by " + user.name +
                " for trip " + tripId + " totalPrice=" + b.totalPrice);
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
            trip.cancelHold(b.seatNos(), b.user.userId);
            expire(b);
            throw new RuntimeException("Seat HOLD expired. Please hold again.");
        }

        if (!b.paid) {
            paymentFactory.get(mode).pay(b.bookingId, b.totalPrice); // ✅ final price used here
            b.paid = true;
        }

        try {
            trip.confirmSeats(b.seatNos(), b.user.userId);
        } catch (RuntimeException ex) {
            trip.cancelHold(b.seatNos(), b.user.userId);
            expire(b);
            throw ex;
        }

        b.status = BookingStatus.CONFIRMED;
        bookingRepo.save(b);
        notifier.publish("Booking CONFIRMED: " + bookingId +
                " seats=" + b.seatNos() + " totalPrice=" + b.totalPrice);
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
public class AirlineSystemMain_SeatTypesAndPrice {
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

        // seat types + different pricing per type
        service.registerFlightTemplate(
                admin,
                "F101",
                "A1",
                "Delhi",
                "Mumbai",
                FlightType.DOMESTIC,
                10,
                3,                      // first 3 seats are PREMIUM
                LocalTime.of(9, 30),
                5000,                   // economy price
                8000                    // premium price
        );

        service.createTrip(admin, "F101", LocalDate.of(2026, 8, 15));

        // Search
        var results = service.searchTripsWithAvailability(
                new SearchCriteria("Delhi", "Mumbai", LocalDate.of(2026, 8, 15), FlightType.DOMESTIC)
        );
        results.forEach(System.out::println);

        String tripId = results.get(0).tripId;

        // Show available seats with type + price
        System.out.println("Available seats for trip=" + tripId);
        System.out.println(service.getAvailableSeats(tripId));

        // Hold seats
        User u1 = new User("U1", "Alice", Role.USER);
        Booking b = service.holdSeats(u1, tripId, List.of(1, 5)); // seat1 PREMIUM, seat5 ECONOMY
        System.out.println("Booking total price = " + b.totalPrice + ", seats=" + b.seats);

        // Pay & confirm
        service.payAndConfirm(b.bookingId, PaymentMode.UPI);
    }
}
