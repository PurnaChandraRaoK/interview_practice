import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

// ===================== Enums =====================
enum FlightType { DOMESTIC, INTERNATIONAL }
enum PaymentMode { CARD, UPI, NETBANKING }
enum SeatStatus { AVAILABLE, BOOKED }
enum Role { ADMIN, USER }
enum BookingStatus { HOLD, CONFIRMED, CANCELLED, EXPIRED }

// ===================== DTO =====================
final class SearchCriteria {
    final String origin;
    final String destination;
    final LocalDate date;
    final FlightType flightType;

    SearchCriteria(String origin, String destination, LocalDate date, FlightType flightType) {
        this.origin = Objects.requireNonNull(origin);
        this.destination = Objects.requireNonNull(destination);
        this.date = Objects.requireNonNull(date);
        this.flightType = Objects.requireNonNull(flightType);
    }
}

// ===================== Users =====================
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

// ===================== Seat / Flight =====================
final class Seat {
    final int seatNumber;
    volatile SeatStatus status;

    Seat(int seatNumber) {
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }
}

final class Flight {
    final String flightId;
    final String airlineId;
    final String origin;
    final String destination;
    final LocalDate date;
    final FlightType type;

    private final Map<Integer, Seat> seatMap = new ConcurrentHashMap<>();
    private final Lock seatLock = new ReentrantLock();

    Flight(String flightId, String airlineId, String origin, String destination,
           LocalDate date, FlightType type, int totalSeats) {

        this.flightId = Objects.requireNonNull(flightId);
        this.airlineId = Objects.requireNonNull(airlineId);
        this.origin = Objects.requireNonNull(origin);
        this.destination = Objects.requireNonNull(destination);
        this.date = Objects.requireNonNull(date);
        this.type = Objects.requireNonNull(type);

        if (totalSeats <= 0) throw new IllegalArgumentException("totalSeats must be > 0");
        for (int i = 1; i <= totalSeats; i++) seatMap.put(i, new Seat(i));
    }

    boolean isSeatPermanentlyBooked(int seatNumber) {
        Seat seat = seatMap.get(seatNumber);
        return seat != null && seat.status == SeatStatus.BOOKED;
    }

    boolean isSeatValid(int seatNumber) {
        return seatMap.containsKey(seatNumber);
    }

    boolean bookSeatPermanently(int seatNumber) {
        seatLock.lock();
        try {
            Seat seat = seatMap.get(seatNumber);
            if (seat == null || seat.status == SeatStatus.BOOKED) return false;
            seat.status = SeatStatus.BOOKED;
            return true;
        } finally {
            seatLock.unlock();
        }
    }

    void cancelSeat(int seatNumber) {
        seatLock.lock();
        try {
            Seat seat = seatMap.get(seatNumber);
            if (seat != null) seat.status = SeatStatus.AVAILABLE;
        } finally {
            seatLock.unlock();
        }
    }

    int totalAvailableSeatsIgnoringLocks() {
        int count = 0;
        for (Seat s : seatMap.values()) if (s.status == SeatStatus.AVAILABLE) count++;
        return count;
    }
}

// ===================== Airline / Booking =====================
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
    final String flightId;
    final int seatNumber;

    volatile boolean isPaid;
    volatile BookingStatus status;

    Booking(String bookingId, User user, String flightId, int seatNumber) {
        this.bookingId = Objects.requireNonNull(bookingId);
        this.user = Objects.requireNonNull(user);
        this.flightId = Objects.requireNonNull(flightId);
        this.seatNumber = seatNumber;

        this.isPaid = false;
        this.status = BookingStatus.HOLD;
    }
}

// ===================== Repositories =====================
interface AirlineRepository {
    void addAirline(Airline airline);
    Optional<Airline> getAirlineById(String id);
}

final class InMemoryAirlineRepository implements AirlineRepository {
    private final Map<String, Airline> airlines = new ConcurrentHashMap<>();
    public void addAirline(Airline airline) { airlines.put(airline.airlineId, airline); }
    public Optional<Airline> getAirlineById(String id) { return Optional.ofNullable(airlines.get(id)); }
}

interface FlightRepository {
    void addFlight(Flight flight);
    Optional<Flight> getFlightById(String id);
    List<Flight> search(SearchCriteria criteria);
}

final class InMemoryFlightRepository implements FlightRepository {
    private final Map<String, Flight> flights = new ConcurrentHashMap<>();
    public void addFlight(Flight flight) { flights.put(flight.flightId, flight); }
    public Optional<Flight> getFlightById(String id) { return Optional.ofNullable(flights.get(id)); }

    public List<Flight> search(SearchCriteria c) {
        List<Flight> result = new ArrayList<>();
        for (Flight f : flights.values()) {
            if (f.origin.equalsIgnoreCase(c.origin)
                    && f.destination.equalsIgnoreCase(c.destination)
                    && f.date.equals(c.date)
                    && f.type == c.flightType) {
                result.add(f);
            }
        }
        return result;
    }
}

interface BookingRepository {
    void save(Booking booking);
    Optional<Booking> get(String id);
    void delete(String id);
}

final class InMemoryBookingRepository implements BookingRepository {
    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();
    public void save(Booking booking) { bookings.put(booking.bookingId, booking); }
    public Optional<Booking> get(String id) { return Optional.ofNullable(bookings.get(id)); }
    public void delete(String id) { bookings.remove(id); }
}

// ===================== Payment Strategy + Factory =====================
interface PaymentStrategy {
    void pay(Booking booking);
}

final class CardPayment implements PaymentStrategy {
    public void pay(Booking b) { b.isPaid = true; System.out.println("Card payment successful for " + b.bookingId); }
}

final class UpiPayment implements PaymentStrategy {
    public void pay(Booking b) { b.isPaid = true; System.out.println("UPI payment successful for " + b.bookingId); }
}

final class NetBankingPayment implements PaymentStrategy {
    public void pay(Booking b) { b.isPaid = true; System.out.println("NetBanking payment successful for " + b.bookingId); }
}

final class PaymentFactory {
    private final Map<PaymentMode, PaymentStrategy> strategies = new EnumMap<>(PaymentMode.class);

    PaymentFactory() {
        strategies.put(PaymentMode.CARD, new CardPayment());
        strategies.put(PaymentMode.UPI, new UpiPayment());
        strategies.put(PaymentMode.NETBANKING, new NetBankingPayment());
    }

    PaymentStrategy getStrategy(PaymentMode mode) {
        PaymentStrategy s = strategies.get(mode);
        if (s == null) throw new IllegalArgumentException("Unsupported mode: " + mode);
        return s;
    }
}

// ===================== Notification (Observer) =====================
interface NotificationChannel { void send(String message); }

final class EmailNotifier implements NotificationChannel {
    public void send(String message) { System.out.println("[EMAIL] " + message); }
}

final class SMSNotifier implements NotificationChannel {
    public void send(String message) { System.out.println("[SMS] " + message); }
}

final class NotificationService {
    private final List<NotificationChannel> channels = new CopyOnWriteArrayList<>();
    public void subscribe(NotificationChannel c) { channels.add(c); }
    public void publish(String message) { for (NotificationChannel c : channels) c.send(message); }
}

// ===================== BookMyShow-style SeatLock =====================
// Mirrors your earlier pattern: lockSeats/unlockSeats/validateLock/getLockedSeats with expiry checks. [1](https://microsoftapc-my.sharepoint.com/personal/pkota_microsoft_com/Documents/Desktop/NewLLD%20-%20Copy%20(2).pdf?web=1)[2](https://microsoftapc-my.sharepoint.com/personal/pkota_microsoft_com/_layouts/15/Doc.aspx?sourcedoc=%7B9668DA9C-FC91-4A0D-A462-543647196FC0%7D&file=NewLLD.docx&action=default&mobileredirect=true&DefaultItemOpen=1)
final class SeatLock {
    final String flightId;
    final int seatNumber;
    final String lockedBy;
    final Instant lockTime;
    final Duration timeout;

    SeatLock(String flightId, int seatNumber, String lockedBy, Duration timeout) {
        this.flightId = flightId;
        this.seatNumber = seatNumber;
        this.lockedBy = lockedBy;
        this.timeout = timeout;
        this.lockTime = Instant.now();
    }

    boolean isLockExpired() {
        return Instant.now().isAfter(lockTime.plus(timeout));
    }
}

interface SeatLockProvider {
    void lockSeats(String flightId, List<Integer> seats, String userId);
    void unlockSeats(String flightId, List<Integer> seats, String userId);
    boolean validateLock(String flightId, int seatNumber, String userId);
    List<Integer> getLockedSeats(String flightId);
}

final class InMemorySeatLockProvider implements SeatLockProvider {
    private final Map<String, Map<Integer, SeatLock>> locks = new ConcurrentHashMap<>();
    private final Duration lockTimeout;

    InMemorySeatLockProvider(Duration lockTimeout) {
        this.lockTimeout = lockTimeout;
    }

    @Override
    public synchronized void lockSeats(String flightId, List<Integer> seats, String userId) {
        Objects.requireNonNull(flightId);
        Objects.requireNonNull(seats);
        Objects.requireNonNull(userId);

        for (int seat : seats) {
            if (isSeatLocked(flightId, seat)) {
                throw new RuntimeException("Seat temporarily unavailable (locked): " + seat);
            }
        }
        for (int seat : seats) {
            lockSeat(flightId, seat, userId);
        }
    }

    @Override
    public void unlockSeats(String flightId, List<Integer> seats, String userId) {
        Objects.requireNonNull(flightId);
        Objects.requireNonNull(seats);
        Objects.requireNonNull(userId);

        for (int seat : seats) {
            if (validateLock(flightId, seat, userId)) {
                unlockSeat(flightId, seat);
            }
        }
    }

    @Override
    public boolean validateLock(String flightId, int seatNumber, String userId) {
        if (!isSeatLocked(flightId, seatNumber)) return false;
        SeatLock lock = locks.get(flightId).get(seatNumber);
        return lock != null && !lock.isLockExpired() && lock.lockedBy.equals(userId);
    }

    @Override
    public List<Integer> getLockedSeats(String flightId) {
        Map<Integer, SeatLock> flightLocks = locks.get(flightId);
        if (flightLocks == null) return List.of();

        List<Integer> locked = new ArrayList<>();
        for (Map.Entry<Integer, SeatLock> e : flightLocks.entrySet()) {
            if (!e.getValue().isLockExpired()) locked.add(e.getKey());
        }
        return locked;
    }

    private void lockSeat(String flightId, int seatNumber, String userId) {
        locks.computeIfAbsent(flightId, k -> new ConcurrentHashMap<>())
             .put(seatNumber, new SeatLock(flightId, seatNumber, userId, lockTimeout));
    }

    private void unlockSeat(String flightId, int seatNumber) {
        Map<Integer, SeatLock> flightLocks = locks.get(flightId);
        if (flightLocks == null) return;
        flightLocks.remove(seatNumber);
    }

    private boolean isSeatLocked(String flightId, int seatNumber) {
        Map<Integer, SeatLock> flightLocks = locks.get(flightId);
        if (flightLocks == null) return false;

        SeatLock lock = flightLocks.get(seatNumber);
        if (lock == null) return false;

        // lazy cleanup
        if (lock.isLockExpired()) {
            flightLocks.remove(seatNumber);
            return false;
        }
        return true;
    }
}

// ===================== Service Layer =====================
final class AirlineService {
    private final AirlineRepository airlineRepo;
    private final FlightRepository flightRepo;
    private final BookingRepository bookingRepo;
    private final SeatLockProvider seatLockProvider;
    private final NotificationService notifier;
    private final PaymentFactory paymentFactory;

    AirlineService(AirlineRepository airlineRepo,
                   FlightRepository flightRepo,
                   BookingRepository bookingRepo,
                   SeatLockProvider seatLockProvider,
                   NotificationService notifier,
                   PaymentFactory paymentFactory) {
        this.airlineRepo = airlineRepo;
        this.flightRepo = flightRepo;
        this.bookingRepo = bookingRepo;
        this.seatLockProvider = seatLockProvider;
        this.notifier = notifier;
        this.paymentFactory = paymentFactory;
    }

    // Admin-only operations
    public void registerAirline(User actor, String id, String name) {
        requireAdmin(actor);
        airlineRepo.addAirline(new Airline(id, name));
    }

    public void registerFlight(User actor, String flightId, String airlineId, String origin, String dest,
                               LocalDate date, FlightType type, int seats) {
        requireAdmin(actor);
        if (airlineRepo.getAirlineById(airlineId).isEmpty())
            throw new RuntimeException("Airline not found: " + airlineId);
        flightRepo.addFlight(new Flight(flightId, airlineId, origin, dest, date, type, seats));
    }

    public List<Flight> searchFlights(SearchCriteria criteria) {
        return flightRepo.search(criteria);
    }

    // HOLD booking (temporary lock like BMS)
    public Booking bookSeat(User user, String flightId, int seatNo) {
        Flight flight = flightRepo.getFlightById(flightId)
                .orElseThrow(() -> new RuntimeException("Flight not found: " + flightId));

        if (!flight.isSeatValid(seatNo)) throw new RuntimeException("Invalid seat: " + seatNo);
        if (flight.isSeatPermanentlyBooked(seatNo)) throw new RuntimeException("Seat already booked: " + seatNo);

        // lock seat (temporary)
        seatLockProvider.lockSeats(flightId, List.of(seatNo), user.userId);

        Booking booking = new Booking(UUID.randomUUID().toString(), user, flightId, seatNo);
        bookingRepo.save(booking);

        notifier.publish("Seat " + seatNo + " HELD by " + user.name + " on flight " + flightId);
        return booking;
    }

    // Pay -> confirm booking (validate lock owner like BMS confirmBooking)
    public void pay(String bookingId, PaymentMode mode) {
        Booking booking = bookingRepo.get(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (booking.status == BookingStatus.CONFIRMED) return;
        if (booking.status != BookingStatus.HOLD) throw new RuntimeException("Cannot pay. Booking status: " + booking.status);

        // validate lock ownership & expiry
        if (!seatLockProvider.validateLock(booking.flightId, booking.seatNumber, booking.user.userId)) {
            expireBooking(booking);
            throw new RuntimeException("Seat lock expired. Please book again.");
        }

        // payment
        if (booking.isPaid) return; // idempotent-ish
        paymentFactory.getStrategy(mode).pay(booking);

        // permanent booking
        Flight flight = flightRepo.getFlightById(booking.flightId)
                .orElseThrow(() -> new RuntimeException("Flight not found: " + booking.flightId));

        if (!flight.bookSeatPermanently(booking.seatNumber)) {
            expireBooking(booking);
            throw new RuntimeException("Seat already booked (race). Please book again.");
        }

        booking.status = BookingStatus.CONFIRMED;
        bookingRepo.save(booking);

        // unlock temp lock after confirm
        seatLockProvider.unlockSeats(booking.flightId, List.of(booking.seatNumber), booking.user.userId);

        notifier.publish("Payment successful. Booking CONFIRMED: " + bookingId);
    }

    // Cancel booking (release lock if HOLD; free seat if CONFIRMED)
    public void cancelBooking(String bookingId) {
        Booking booking = bookingRepo.get(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (booking.status == BookingStatus.CANCELLED) return;
        if (booking.status == BookingStatus.EXPIRED) return;

        Flight flight = flightRepo.getFlightById(booking.flightId)
                .orElseThrow(() -> new RuntimeException("Flight not found: " + booking.flightId));

        if (booking.status == BookingStatus.HOLD) {
            // release temp lock if owner
            seatLockProvider.unlockSeats(booking.flightId, List.of(booking.seatNumber), booking.user.userId);
            booking.status = BookingStatus.CANCELLED;
            bookingRepo.delete(bookingId);
            notifier.publish("Booking cancelled (HOLD released): " + bookingId);
            return;
        }

        // CONFIRMED cancellation -> free seat (refund not modeled)
        flight.cancelSeat(booking.seatNumber);
        booking.status = BookingStatus.CANCELLED;
        bookingRepo.delete(bookingId);
        notifier.publish("Booking cancelled (CONFIRMED seat freed): " + bookingId + " (refund not modeled)");
    }

    public Booking getBooking(String bookingId) {
        Booking booking = bookingRepo.get(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        // Lazy expiry if still HOLD but lock expired
        if (booking.status == BookingStatus.HOLD
                && !seatLockProvider.validateLock(booking.flightId, booking.seatNumber, booking.user.userId)) {
            expireBooking(booking);
            throw new RuntimeException("Booking expired: " + bookingId);
        }
        return booking;
    }

    // Available seats = available (not BOOKED) minus locked seats (not expired)
    public int getAvailableSeatCount(String flightId) {
        Flight flight = flightRepo.getFlightById(flightId)
                .orElseThrow(() -> new RuntimeException("Flight not found: " + flightId));

        int availableIgnoringLocks = flight.totalAvailableSeatsIgnoringLocks();
        int locked = seatLockProvider.getLockedSeats(flightId).size();
        return Math.max(0, availableIgnoringLocks - locked);
    }

    private void expireBooking(Booking booking) {
        booking.status = BookingStatus.EXPIRED;
        bookingRepo.delete(booking.bookingId);
        // no seat changes needed since HOLD never permanently booked
        notifier.publish("Booking expired: " + booking.bookingId);
    }

    private void requireAdmin(User actor) {
        if (actor == null || actor.role != Role.ADMIN) throw new RuntimeException("Admin access required");
    }
}

// ===================== Demo =====================
public class AirlineSystemMain {
    public static void main(String[] args) throws Exception {
        AirlineRepository airlineRepo = new InMemoryAirlineRepository();
        FlightRepository flightRepo = new InMemoryFlightRepository();
        BookingRepository bookingRepo = new InMemoryBookingRepository();

        // BMS-style lock provider with TTL
        SeatLockProvider seatLockProvider = new InMemorySeatLockProvider(Duration.ofMinutes(2)); // hold TTL

        NotificationService notifier = new NotificationService();
        notifier.subscribe(new EmailNotifier());
        notifier.subscribe(new SMSNotifier());

        PaymentFactory paymentFactory = new PaymentFactory();

        AirlineService service = new AirlineService(
                airlineRepo, flightRepo, bookingRepo, seatLockProvider, notifier, paymentFactory
        );

        User admin = new User("A0", "Admin", Role.ADMIN);
        service.registerAirline(admin, "A1", "Indigo");
        service.registerFlight(admin, "F101", "A1", "Delhi", "Mumbai",
                LocalDate.of(2025, 8, 15), FlightType.DOMESTIC, 100);

        User user1 = new User("U1", "Alice", Role.USER);
        User user2 = new User("U2", "Bob", Role.USER);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (User u : List.of(user1, user2)) {
            pool.submit(() -> {
                try {
                    Booking b = service.bookSeat(u, "F101", 1);
                    service.pay(b.bookingId, PaymentMode.UPI);
                } catch (Exception e) {
                    System.out.println("[" + u.name + "] " + e.getMessage());
                }
            });
        }

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("Available seats (locks considered): " + service.getAvailableSeatCount("F101"));
    }
}
