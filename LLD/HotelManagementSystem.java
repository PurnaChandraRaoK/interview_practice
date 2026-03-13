import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

// ------------------ Enums ------------------
enum RoomType { SINGLE, DOUBLE, SUITE, DELUXE, PRESIDENTIAL }
enum RoomStatus { AVAILABLE, OCCUPIED, MAINTENANCE }
enum BookingStatus { PENDING, CONFIRMED, CANCELLED, CHECKED_IN, CHECKED_OUT }
enum PaymentStatus { PENDING, COMPLETED, FAILED, REFUNDED }

// ------------------ Room Model (Factory + Decorator kept simple) ------------------
abstract class Room {
    protected final String roomNumber;
    protected final RoomType roomType;
    protected volatile RoomStatus status;
    protected final double basePrice;
    protected final int capacity;

    protected Room(String roomNumber, RoomType roomType, double basePrice, int capacity) {
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.basePrice = basePrice;
        this.capacity = capacity;
        this.status = RoomStatus.AVAILABLE;
    }

    public String getRoomNumber() { return roomNumber; }
    public RoomType getRoomType() { return roomType; }
    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }
    public double getBasePrice() { return basePrice; }
    public int getCapacity() { return capacity; }

    public double calculatePricePerNight() { return basePrice; }
    public String getDescription() { return roomType + " Room - " + roomNumber; }
}

class SingleRoom extends Room { public SingleRoom(String no) { super(no, RoomType.SINGLE, 100.0, 1); } }
class DoubleRoom extends Room { public DoubleRoom(String no) { super(no, RoomType.DOUBLE, 150.0, 2); } }
class SuiteRoom  extends Room { public SuiteRoom(String no)  { super(no, RoomType.SUITE,  300.0, 4); } }

class RoomFactory {
    public static Room createRoom(RoomType type, String roomNumber) {
        return switch (type) {
            case SINGLE -> new SingleRoom(roomNumber);
            case DOUBLE -> new DoubleRoom(roomNumber);
            case SUITE  -> new SuiteRoom(roomNumber);
            default -> throw new IllegalArgumentException("Room type not supported in demo: " + type);
        };
    }
}

// Decorator for add-on services (only affects price/description)
abstract class RoomServiceDecorator extends Room {
    protected final Room room;
    protected RoomServiceDecorator(Room room) {
        super(room.getRoomNumber(), room.getRoomType(), room.getBasePrice(), room.getCapacity());
        this.room = room;
    }
    @Override public RoomStatus getStatus() { return room.getStatus(); }
    @Override public void setStatus(RoomStatus status) { room.setStatus(status); }
    @Override public double calculatePricePerNight() { return room.calculatePricePerNight(); }
    @Override public String getDescription() { return room.getDescription(); }
}

class BreakfastDecorator extends RoomServiceDecorator {
    public BreakfastDecorator(Room room) { super(room); }
    @Override public double calculatePricePerNight() { return room.calculatePricePerNight() + 25.0; }
    @Override public String getDescription() { return room.getDescription() + " + Breakfast"; }
}
class WiFiDecorator extends RoomServiceDecorator {
    public WiFiDecorator(Room room) { super(room); }
    @Override public double calculatePricePerNight() { return room.calculatePricePerNight() + 10.0; }
    @Override public String getDescription() { return room.getDescription() + " + WiFi"; }
}
class ParkingDecorator extends RoomServiceDecorator {
    public ParkingDecorator(Room room) { super(room); }
    @Override public double calculatePricePerNight() { return room.calculatePricePerNight() + 15.0; }
    @Override public String getDescription() { return room.getDescription() + " + Parking"; }
}

// ------------------ Observer (Notifications) ------------------
interface Observer { void update(String message); }

class Customer implements Observer {
    private final String customerId;
    private String name;
    private String email;
    private String phone;
    private String address;

    public Customer(String customerId, String name, String email, String phone, String address) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
    }

    public String getCustomerId() { return customerId; }
    public String getName() { return name; }

    @Override
    public void update(String message) {
        System.out.println("Notify " + name + ": " + message);
    }
}

class NotificationManager {
    private final CopyOnWriteArrayList<Observer> observers = new CopyOnWriteArrayList<>();
    public void addObserver(Observer observer) { observers.addIfAbsent(observer); }
    public void removeObserver(Observer observer) { observers.remove(observer); }
    public void notifyObservers(String msg) { observers.forEach(o -> o.update(msg)); }
}

// ------------------ Booking ------------------
class Booking {
    private final String bookingId;
    private final String lockId;          // important: BookMyShow-like lock ownership
    private final Customer customer;
    private final Room room;
    private final LocalDate checkIn;
    private final LocalDate checkOut;     // exclusive
    private final LocalDateTime createdAt;
    private volatile BookingStatus status;
    private volatile PaymentStatus paymentStatus;
    private final double totalAmount;

    public Booking(String bookingId, String lockId, Customer customer, Room room,
                   LocalDate checkIn, LocalDate checkOut) {
        this.bookingId = bookingId;
        this.lockId = lockId;
        this.customer = customer;
        this.room = room;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.createdAt = LocalDateTime.now();
        this.status = BookingStatus.PENDING;
        this.paymentStatus = PaymentStatus.PENDING;
        this.totalAmount = computeTotal(room, checkIn, checkOut);
    }

    private static double computeTotal(Room room, LocalDate in, LocalDate out) {
        long nights = ChronoUnit.DAYS.between(in, out);
        return nights * room.calculatePricePerNight();
    }

    public String getBookingId() { return bookingId; }
    public String getLockId() { return lockId; }
    public Customer getCustomer() { return customer; }
    public Room getRoom() { return room; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public BookingStatus getStatus() { return status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public double getTotalAmount() { return totalAmount; }

    public void markPaymentCompleted() { this.paymentStatus = PaymentStatus.COMPLETED; }
    public void markPaymentFailed() { this.paymentStatus = PaymentStatus.FAILED; }

    public void confirm() { this.status = BookingStatus.CONFIRMED; }
    public void cancel()  { this.status = BookingStatus.CANCELLED; }
    public boolean isConfirmed() { return status == BookingStatus.CONFIRMED; }

    @Override
    public String toString() {
        return "Booking{" + bookingId + ", room=" + room.getRoomNumber() +
                ", " + checkIn + "->" + checkOut +
                ", status=" + status + ", pay=" + paymentStatus + "}";
    }
}

// ------------------ Search (fixed, single signature) ------------------
class SearchCriteria {
    RoomType type;
    Double minPrice, maxPrice;
    Integer minCapacity;
    LocalDate checkIn, checkOut;

    public SearchCriteria type(RoomType t) { this.type = t; return this; }
    public SearchCriteria price(double min, double max) { this.minPrice = min; this.maxPrice = max; return this; }
    public SearchCriteria capacity(int cap) { this.minCapacity = cap; return this; }
    public SearchCriteria dateRange(LocalDate in, LocalDate out) { this.checkIn = in; this.checkOut = out; return this; }
}

interface SearchStrategy {
    List<Room> search(List<Room> rooms, SearchCriteria criteria, Collection<Booking> bookings, RoomLockProvider lockProvider);
}

class DefaultSearchStrategy implements SearchStrategy {
    @Override
    public List<Room> search(List<Room> rooms, SearchCriteria c, Collection<Booking> bookings, RoomLockProvider lockProvider) {
        return rooms.stream()
                .filter(r -> r.getStatus() == RoomStatus.AVAILABLE)
                .filter(r -> c.type == null || r.getRoomType() == c.type)
                .filter(r -> c.minCapacity == null || r.getCapacity() >= c.minCapacity)
                .filter(r -> c.minPrice == null || r.calculatePricePerNight() >= c.minPrice)
                .filter(r -> c.maxPrice == null || r.calculatePricePerNight() <= c.maxPrice)
                .filter(r -> {
                    if (c.checkIn == null || c.checkOut == null) return true;
                    return AvailabilityUtil.isAvailable(r.getRoomNumber(), c.checkIn, c.checkOut, bookings, lockProvider, null);
                })
                .collect(Collectors.toList());
    }
}

// ------------------ BookMyShow style: Lock Provider (Concurrency + TTL) ------------------
class RoomLock {
    private final String lockId;
    private final String roomNumber;
    private final LocalDate checkIn;
    private final LocalDate checkOut; // exclusive
    private final String lockedByCustomerId;
    private final Instant lockTime;
    private final int timeoutSeconds;

    RoomLock(String lockId, String roomNumber, LocalDate checkIn, LocalDate checkOut,
             String lockedByCustomerId, Instant lockTime, int timeoutSeconds) {
        this.lockId = lockId;
        this.roomNumber = roomNumber;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.lockedByCustomerId = lockedByCustomerId;
        this.lockTime = lockTime;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getLockId() { return lockId; }
    public String getRoomNumber() { return roomNumber; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public String getLockedByCustomerId() { return lockedByCustomerId; }

    public boolean isExpired() {
        return lockTime.plusSeconds(timeoutSeconds).isBefore(Instant.now());
    }

    public boolean overlaps(LocalDate in, LocalDate out) {
        // half-open overlap: [a,b) and [c,d) overlap if a<d && c<b
        return checkIn.isBefore(out) && in.isBefore(checkOut);
    }
}

interface RoomLockProvider {
    RoomLock lockRoom(String roomNumber, LocalDate checkIn, LocalDate checkOut, String customerId);
    void unlockRoom(String lockId, String customerId);
    boolean validateLock(String lockId, String customerId);
    Optional<RoomLock> getLock(String lockId);
    boolean hasActiveOverlappingLock(String roomNumber, LocalDate checkIn, LocalDate checkOut, String ignoreCustomerId);
}

// In-memory lock provider with per-room mutex (simple + safe)
class InMemoryRoomLockProvider implements RoomLockProvider {
    private final int lockTimeoutSeconds;
    private final ConcurrentHashMap<String, List<RoomLock>> locksByRoom = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RoomLock> locksById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> roomMutex = new ConcurrentHashMap<>();

    public InMemoryRoomLockProvider(int lockTimeoutSeconds) {
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    private ReentrantLock mutex(String roomNumber) {
        return roomMutex.computeIfAbsent(roomNumber, k -> new ReentrantLock());
    }

    @Override
    public RoomLock lockRoom(String roomNumber, LocalDate checkIn, LocalDate checkOut, String customerId) {
        ReentrantLock m = mutex(roomNumber);
        m.lock();
        try {
            cleanupExpired(roomNumber);

            List<RoomLock> existing = locksByRoom.computeIfAbsent(roomNumber, k -> new ArrayList<>());
            boolean conflict = existing.stream()
                    .filter(l -> !l.isExpired())
                    .anyMatch(l -> l.overlaps(checkIn, checkOut));

            if (conflict) {
                throw new IllegalStateException("Room temporarily unavailable (locked by someone else).");
            }

            String lockId = UUID.randomUUID().toString();
            RoomLock lock = new RoomLock(lockId, roomNumber, checkIn, checkOut, customerId, Instant.now(), lockTimeoutSeconds);
            existing.add(lock);
            locksById.put(lockId, lock);
            return lock;
        } finally {
            m.unlock();
        }
    }

    @Override
    public void unlockRoom(String lockId, String customerId) {
        RoomLock lock = locksById.get(lockId);
        if (lock == null) return;

        ReentrantLock m = mutex(lock.getRoomNumber());
        m.lock();
        try {
            RoomLock current = locksById.get(lockId);
            if (current == null) return;

            // Only owner can unlock (same as BookMyShow validateLock usage)
            if (!current.getLockedByCustomerId().equals(customerId)) return;

            List<RoomLock> list = locksByRoom.getOrDefault(current.getRoomNumber(), Collections.emptyList());
            list.removeIf(l -> l.getLockId().equals(lockId));
            locksById.remove(lockId);
        } finally {
            m.unlock();
        }
    }

    @Override
    public boolean validateLock(String lockId, String customerId) {
        RoomLock lock = locksById.get(lockId);
        return lock != null && !lock.isExpired() && lock.getLockedByCustomerId().equals(customerId);
    }

    @Override
    public Optional<RoomLock> getLock(String lockId) {
        RoomLock lock = locksById.get(lockId);
        if (lock == null || lock.isExpired()) return Optional.empty();
        return Optional.of(lock);
    }

    @Override
    public boolean hasActiveOverlappingLock(String roomNumber, LocalDate checkIn, LocalDate checkOut, String ignoreCustomerId) {
        cleanupExpired(roomNumber);
        List<RoomLock> existing = locksByRoom.getOrDefault(roomNumber, Collections.emptyList());
        return existing.stream()
                .filter(l -> !l.isExpired())
                .filter(l -> ignoreCustomerId == null || !l.getLockedByCustomerId().equals(ignoreCustomerId))
                .anyMatch(l -> l.overlaps(checkIn, checkOut));
    }

    private void cleanupExpired(String roomNumber) {
        List<RoomLock> list = locksByRoom.get(roomNumber);
        if (list == null) return;

        list.removeIf(l -> {
            boolean expired = l.isExpired();
            if (expired) locksById.remove(l.getLockId());
            return expired;
        });
    }
}

// ------------------ Availability Util ------------------
class AvailabilityUtil {
    static boolean overlaps(LocalDate aIn, LocalDate aOut, LocalDate bIn, LocalDate bOut) {
        return aIn.isBefore(bOut) && bIn.isBefore(aOut);
    }

    static boolean isAvailable(String roomNumber, LocalDate in, LocalDate out,
                               Collection<Booking> bookings,
                               RoomLockProvider lockProvider,
                               String ignoreCustomerIdForLock) {

        // confirmed bookings are permanent blocks
        boolean booked = bookings.stream()
                .filter(Booking::isConfirmed)
                .filter(b -> b.getRoom().getRoomNumber().equals(roomNumber))
                .anyMatch(b -> overlaps(in, out, b.getCheckIn(), b.getCheckOut()));

        if (booked) return false;

        // active lock by someone else => temporarily unavailable
        return !lockProvider.hasActiveOverlappingLock(roomNumber, in, out, ignoreCustomerIdForLock);
    }
}

// ------------------ Booking Service (BookMyShow style flow) ------------------
class BookingService {
    private final Map<String, Booking> bookingsById = new ConcurrentHashMap<>();
    private final RoomLockProvider lockProvider;

    public BookingService(RoomLockProvider lockProvider) {
        this.lockProvider = lockProvider;
    }

    public Booking getBooking(String bookingId) {
        Booking b = bookingsById.get(bookingId);
        if (b == null) throw new NoSuchElementException("Booking not found");
        return b;
    }

    public Collection<Booking> allBookings() { return bookingsById.values(); }

    /**
     * Step-1: Acquire lock (hold) then create a PENDING booking.
     * Similar to BookMyShow: lockSeats() then createBooking().
     */
    public Booking createPendingBooking(Customer customer, Room room, LocalDate in, LocalDate out) {
        validateDates(in, out);

        if (room.getStatus() != RoomStatus.AVAILABLE) {
            throw new IllegalStateException("Room not available due to status=" + room.getStatus());
        }

        // Before locking, quick permanent availability check (confirmed bookings)
        if (!AvailabilityUtil.isAvailable(room.getRoomNumber(), in, out, bookingsById.values(), lockProvider, customer.getCustomerId())) {
            throw new IllegalStateException("Room not available for given date range.");
        }

        // Lock (atomic within lockProvider per-room mutex)
        RoomLock lock = lockProvider.lockRoom(room.getRoomNumber(), in, out, customer.getCustomerId());

        String bookingId = UUID.randomUUID().toString();
        Booking booking = new Booking(bookingId, lock.getLockId(), customer, room, in, out);
        bookingsById.put(bookingId, booking);

        return booking;
    }

    /**
     * Step-2: Confirm booking AFTER payment by validating lock still held.
     * Similar to BookMyShow: validateLock() then booking.confirmBooking().
     */
    public void confirmBooking(String bookingId, String customerId) {
        Booking booking = getBooking(bookingId);

        // idempotent behavior: if already confirmed, do nothing
        if (booking.getStatus() == BookingStatus.CONFIRMED) return;

        if (!booking.getCustomer().getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("Not booking owner.");
        }

        // validate lock
        if (!lockProvider.validateLock(booking.getLockId(), customerId)) {
            throw new IllegalStateException("Lock expired/invalid. Cannot confirm.");
        }

        // final permanent availability check (race safety)
        boolean stillFree = AvailabilityUtil.isAvailable(
                booking.getRoom().getRoomNumber(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                bookingsById.values(),
                lockProvider,
                customerId
        );

        if (!stillFree) {
            // release lock and fail
            lockProvider.unlockRoom(booking.getLockId(), customerId);
            booking.cancel();
            throw new IllegalStateException("Room got booked by someone else.");
        }

        booking.markPaymentCompleted();
        booking.confirm();
        lockProvider.unlockRoom(booking.getLockId(), customerId);
    }

    /**
     * Cancel and release lock.
     */
    public void cancelBooking(String bookingId, String customerId) {
        Booking booking = getBooking(bookingId);
        if (!booking.getCustomer().getCustomerId().equals(customerId)) {
            throw new IllegalArgumentException("Not booking owner.");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) return;

        booking.cancel();
        lockProvider.unlockRoom(booking.getLockId(), customerId);
    }

    private void validateDates(LocalDate in, LocalDate out) {
        if (in == null || out == null) throw new IllegalArgumentException("Dates required");
        if (!in.isBefore(out)) throw new IllegalArgumentException("checkIn must be before checkOut");
    }
}

// ------------------ Payment Service (simple retry like BookMyShow PDF snippet) ------------------
class PaymentService {
    private final int allowedRetries;
    private final Map<String, AtomicInteger> failuresByBooking = new ConcurrentHashMap<>();
    private final BookingService bookingService;

    public PaymentService(int allowedRetries, BookingService bookingService) {
        this.allowedRetries = allowedRetries;
        this.bookingService = bookingService;
    }

    public void paymentSuccess(String bookingId, String customerId) {
        bookingService.confirmBooking(bookingId, customerId);
    }

    public void paymentFailed(String bookingId, String customerId) {
        Booking b = bookingService.getBooking(bookingId);
        b.markPaymentFailed();

        int failures = failuresByBooking.computeIfAbsent(bookingId, k -> new AtomicInteger(0)).incrementAndGet();
        if (failures > allowedRetries) {
            bookingService.cancelBooking(bookingId, customerId);
        }
    }
}

// ------------------ Hotel Management System (Singleton-ish) ------------------
class HotelManagementSystem {
    private static final class Holder {
        private static final HotelManagementSystem INSTANCE = new HotelManagementSystem();
    }
    public static HotelManagementSystem getInstance() { return Holder.INSTANCE; }

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, Customer> customers = new ConcurrentHashMap<>();
    private final NotificationManager notifier = new NotificationManager();

    private final RoomLockProvider lockProvider = new InMemoryRoomLockProvider(120); // 2 mins hold
    private final BookingService bookingService = new BookingService(lockProvider);
    private final PaymentService paymentService = new PaymentService(2, bookingService);

    private final SearchStrategy searchStrategy = new DefaultSearchStrategy();

    private HotelManagementSystem() {
        initRooms();
    }

    private void initRooms() {
        addRoom(RoomFactory.createRoom(RoomType.SINGLE, "101"));
        addRoom(RoomFactory.createRoom(RoomType.SINGLE, "102"));
        addRoom(RoomFactory.createRoom(RoomType.DOUBLE, "201"));
        addRoom(RoomFactory.createRoom(RoomType.DOUBLE, "202"));
        addRoom(RoomFactory.createRoom(RoomType.SUITE, "301"));
    }

    public void addRoom(Room room) { rooms.put(room.getRoomNumber(), room); }
    public Room getRoom(String roomNumber) { return rooms.get(roomNumber); }

    public void addCustomer(Customer c) {
        customers.put(c.getCustomerId(), c);
        notifier.addObserver(c);
    }
    public Customer getCustomer(String id) { return customers.get(id); }

    public List<Room> searchRooms(SearchCriteria criteria) {
        return searchStrategy.search(new ArrayList<>(rooms.values()), criteria, bookingService.allBookings(), lockProvider);
    }

    // ------------ BookMyShow-like booking flow ------------
    public Booking holdAndCreatePendingBooking(String customerId, String roomNumber, LocalDate in, LocalDate out) {
        Customer c = requireCustomer(customerId);
        Room room = requireRoom(roomNumber);

        Booking b = bookingService.createPendingBooking(c, room, in, out);
        notifier.notifyObservers("Room held. Booking created (PENDING): " + b.getBookingId());
        return b;
    }

    public void completePaymentAndConfirm(String bookingId, String customerId) {
        paymentService.paymentSuccess(bookingId, customerId);
        notifier.notifyObservers("Booking CONFIRMED: " + bookingId);
    }

    public void failPayment(String bookingId, String customerId) {
        paymentService.paymentFailed(bookingId, customerId);
        notifier.notifyObservers("Payment failed for booking: " + bookingId);
    }

    public void checkIn(String bookingId) {
        Booking b = bookingService.getBooking(bookingId);
        if (b.getStatus() != BookingStatus.CONFIRMED) throw new IllegalStateException("Not confirmed");
        b.getRoom().setStatus(RoomStatus.OCCUPIED);
        notifier.notifyObservers("Check-in done for booking: " + bookingId);
    }

    public void checkOut(String bookingId) {
        Booking b = bookingService.getBooking(bookingId);
        if (b.getRoom().getStatus() != RoomStatus.OCCUPIED) throw new IllegalStateException("Not checked-in");
        b.getRoom().setStatus(RoomStatus.AVAILABLE);
        notifier.notifyObservers("Check-out done for booking: " + bookingId);
    }

    private Customer requireCustomer(String id) {
        Customer c = customers.get(id);
        if (c == null) throw new IllegalArgumentException("Customer not found: " + id);
        return c;
    }
    private Room requireRoom(String roomNumber) {
        Room r = rooms.get(roomNumber);
        if (r == null) throw new IllegalArgumentException("Room not found: " + roomNumber);
        return r;
    }
}

// ------------------ Demo ------------------
class Demo {
    public static void main(String[] args) throws Exception {
        HotelManagementSystem h = HotelManagementSystem.getInstance();
        h.addCustomer(new Customer("C1", "Alice", "a@x.com", "111", "addr"));
        h.addCustomer(new Customer("C2", "Bob", "b@x.com", "222", "addr"));

        LocalDate in = LocalDate.now().plusDays(1);
        LocalDate out = in.plusDays(2);

        // concurrency demo: both try same room same dates
        ExecutorService es = Executors.newFixedThreadPool(2);
        Future<?> f1 = es.submit(() -> {
            Booking b = h.holdAndCreatePendingBooking("C1", "101", in, out);
            h.completePaymentAndConfirm(b.getBookingId(), "C1");
            System.out.println("C1 confirmed " + b);
        });

        Future<?> f2 = es.submit(() -> {
            try {
                Booking b = h.holdAndCreatePendingBooking("C2", "101", in, out);
                h.completePaymentAndConfirm(b.getBookingId(), "C2");
                System.out.println("C2 confirmed " + b);
            } catch (Exception e) {
                System.out.println("C2 failed: " + e.getMessage());
            }
        });

        f1.get(); f2.get();
        es.shutdownNow();
    }
}
