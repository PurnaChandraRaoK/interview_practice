// Room Types Enum
public enum RoomType {
    SINGLE, DOUBLE, SUITE, DELUXE, PRESIDENTIAL
}

// Room Status Enum
public enum RoomStatus {
    AVAILABLE, OCCUPIED, MAINTENANCE, RESERVED
}

// Booking Status Enum
public enum BookingStatus {
    CONFIRMED, CANCELLED, CHECKED_IN, CHECKED_OUT, PENDING
}

// Payment Status Enum
public enum PaymentStatus {
    PENDING, COMPLETED, FAILED, REFUNDED
}

// Abstract Room class
public abstract class Room {
    protected String roomNumber;
    protected RoomType roomType;
    protected RoomStatus status;
    protected double basePrice;
    protected int capacity;
    
    public Room(String roomNumber, RoomType roomType, double basePrice, int capacity) {
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.basePrice = basePrice;
        this.capacity = capacity;
        this.status = RoomStatus.AVAILABLE;
    }
    
    // Getters and setters
    public String getRoomNumber() { return roomNumber; }
    public RoomType getRoomType() { return roomType; }
    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }
    public double getBasePrice() { return basePrice; }
    public int getCapacity() { return capacity; }
    
    public abstract double calculatePrice();
    public abstract String getDescription();
}

// Concrete Room implementations
public class SingleRoom extends Room {
    public SingleRoom(String roomNumber) {
        super(roomNumber, RoomType.SINGLE, 100.0, 1);
    }
    
    @Override
    public double calculatePrice() {
        return basePrice;
    }
    
    @Override
    public String getDescription() {
        return "Single Room - " + roomNumber;
    }
}

public class DoubleRoom extends Room {
    public DoubleRoom(String roomNumber) {
        super(roomNumber, RoomType.DOUBLE, 150.0, 2);
    }
    
    @Override
    public double calculatePrice() {
        return basePrice;
    }
    
    @Override
    public String getDescription() {
        return "Double Room - " + roomNumber;
    }
}

public class SuiteRoom extends Room {
    public SuiteRoom(String roomNumber) {
        super(roomNumber, RoomType.SUITE, 300.0, 4);
    }
    
    @Override
    public double calculatePrice() {
        return basePrice;
    }
    
    @Override
    public String getDescription() {
        return "Suite Room - " + roomNumber;
    }
}

// Factory Pattern for Room Creation
public class RoomFactory {
    public static Room createRoom(RoomType type, String roomNumber) {
        switch (type) {
            case SINGLE:
                return new SingleRoom(roomNumber);
            case DOUBLE:
                return new DoubleRoom(roomNumber);
            case SUITE:
                return new SuiteRoom(roomNumber);
            default:
                throw new IllegalArgumentException("Invalid room type: " + type);
        }
    }
}

// Decorator Pattern for Room Services
public abstract class RoomServiceDecorator extends Room {
    protected Room room;
    
    public RoomServiceDecorator(Room room) {
        super(room.getRoomNumber(), room.getRoomType(), room.getBasePrice(), room.getCapacity());
        this.room = room;
    }
    
    @Override
    public String getRoomNumber() {
        return room.getRoomNumber();
    }
    
    @Override
    public RoomType getRoomType() {
        return room.getRoomType();
    }
    
    @Override
    public RoomStatus getStatus() {
        return room.getStatus();
    }
    
    @Override
    public void setStatus(RoomStatus status) {
        room.setStatus(status);
    }
}

// Concrete Decorators
public class BreakfastDecorator extends RoomServiceDecorator {
    public BreakfastDecorator(Room room) {
        super(room);
    }
    
    @Override
    public double calculatePrice() {
        return room.calculatePrice() + 25.0;
    }
    
    @Override
    public String getDescription() {
        return room.getDescription() + " + Breakfast";
    }
}

public class WiFiDecorator extends RoomServiceDecorator {
    public WiFiDecorator(Room room) {
        super(room);
    }
    
    @Override
    public double calculatePrice() {
        return room.calculatePrice() + 10.0;
    }
    
    @Override
    public String getDescription() {
        return room.getDescription() + " + WiFi";
    }
}

public class ParkingDecorator extends RoomServiceDecorator {
    public ParkingDecorator(Room room) {
        super(room);
    }
    
    @Override
    public double calculatePrice() {
        return room.calculatePrice() + 15.0;
    }
    
    @Override
    public String getDescription() {
        return room.getDescription() + " + Parking";
    }
}

// Customer class
public class Customer {
    private String customerId;
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
    
    // Getters and setters
    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setAddress(String address) { this.address = address; }
}

// Booking class
public class Booking {
    private String bookingId;
    private Customer customer;
    private Room room;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private BookingStatus status;
    private double totalAmount;
    private LocalDateTime bookingTime;
    
    public Booking(String bookingId, Customer customer, Room room, 
                   LocalDate checkInDate, LocalDate checkOutDate) {
        this.bookingId = bookingId;
        this.customer = customer;
        this.room = room;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.status = BookingStatus.PENDING;
        this.bookingTime = LocalDateTime.now();
        this.totalAmount = calculateTotalAmount();
    }
    
    private double calculateTotalAmount() {
        long nights = ChronoUnit.DAYS.between(checkInDate, checkOutDate);
        return room.calculatePrice() * nights;
    }
    
    // Getters and setters
    public String getBookingId() { return bookingId; }
    public Customer getCustomer() { return customer; }
    public Room getRoom() { return room; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public double getTotalAmount() { return totalAmount; }
    public LocalDateTime getBookingTime() { return bookingTime; }
}

// Strategy Pattern for Search Criteria
public interface SearchStrategy {
    List<Room> search(List<Room> rooms, Object criteria);
}

public class SearchByRoomType implements SearchStrategy {
    @Override
    public List<Room> search(List<Room> rooms, Object criteria) {
        RoomType roomType = (RoomType) criteria;
        return rooms.stream()
                .filter(room -> room.getRoomType() == roomType && room.getStatus() == RoomStatus.AVAILABLE)
                .collect(Collectors.toList());
    }
}

public class SearchByPriceRange implements SearchStrategy {
    @Override
    public List<Room> search(List<Room> rooms, Object criteria) {
        double[] priceRange = (double[]) criteria; // [minPrice, maxPrice]
        return rooms.stream()
                .filter(room -> room.getStatus() == RoomStatus.AVAILABLE)
                .filter(room -> room.calculatePrice() >= priceRange[0] && 
                               room.calculatePrice() <= priceRange[1])
                .collect(Collectors.toList());
    }
}

public class SearchByCapacity implements SearchStrategy {
    @Override
    public List<Room> search(List<Room> rooms, Object criteria) {
        Integer capacity = (Integer) criteria;
        return rooms.stream()
                .filter(room -> room.getCapacity() >= capacity && room.getStatus() == RoomStatus.AVAILABLE)
                .collect(Collectors.toList());
    }
}
// Keyword Search
class SearchByKeyword implements SearchStrategy {
    @Override
    public List<Room> search(List<Room> rooms, SearchCriteria criteria, List<Booking> bookings) {
        String kw = criteria.keyword.toLowerCase();
        return rooms.stream()
            .filter(r -> r.getDescriptionText().toLowerCase().contains(kw) ||
                         r.getTags().stream().anyMatch(tag -> tag.toLowerCase().contains(kw)))
            .collect(Collectors.toList());
    }
}

// Location Search
class SearchByLocation implements SearchStrategy {
    @Override
    public List<Room> search(List<Room> rooms, SearchCriteria criteria, List<Booking> bookings) {
        return rooms.stream()
            .filter(r -> r.getLocation().equalsIgnoreCase(criteria.location))
            .collect(Collectors.toList());
    }
}

// Composite Search (combines multiple filters)
class CompositeSearchStrategy implements SearchStrategy {
    private final List<SearchStrategy> strategies = new ArrayList<>();

    public CompositeSearchStrategy addStrategy(SearchStrategy strategy) {
        strategies.add(strategy);
        return this;
    }

    @Override
    public List<Room> search(List<Room> rooms, SearchCriteria criteria, List<Booking> bookings) {
        List<Room> result = new ArrayList<>(rooms);
        for (SearchStrategy strategy : strategies) {
            result = strategy.search(result, criteria, bookings);
        }
        return result;
    }
}
class SearchByTypePriceDate implements SearchStrategy {
    @Override
    public List<Room> search(List<Room> rooms, Object criteriaObj, List<Booking> bookings) {
        SearchCriteria criteria = (SearchCriteria) criteriaObj;
        return rooms.stream()
                .filter(r -> r.getRoomType() == criteria.type)
                .filter(r -> r.calculatePrice() >= criteria.minPrice &&
                             r.calculatePrice() <= criteria.maxPrice)
                .filter(r -> isAvailable(r, criteria.checkIn, criteria.checkOut, bookings))
                .collect(Collectors.toList());
    }

    private boolean isAvailable(Room room, LocalDate in, LocalDate out, List<Booking> bookings) {
        for (Booking b : bookings) {
            if (b.getRoom().getRoomNumber().equals(room.getRoomNumber()) &&
                !(out.isBefore(b.getCheckInDate()) || in.isAfter(b.getCheckOutDate().minusDays(1)))) {
                return false;
            }
        }
        return true;
    }
}

class SearchCriteria {
    RoomType type;
    Double minPrice, maxPrice;
    LocalDate checkIn, checkOut;
    String keyword;
    String location;

    public SearchCriteria type(RoomType t) { this.type = t; return this; }
    public SearchCriteria price(Double min, Double max) { this.minPrice = min; this.maxPrice = max; return this; }
    public SearchCriteria dateRange(LocalDate in, LocalDate out) { this.checkIn = in; this.checkOut = out; return this; }
    public SearchCriteria keyword(String k) { this.keyword = k; return this; }
    public SearchCriteria location(String loc) { this.location = loc; return this; }
}

// Search Context
public class RoomSearchContext {
    private SearchStrategy strategy;
    
    public void setSearchStrategy(SearchStrategy strategy) {
        this.strategy = strategy;
    }
    
    public List<Room> executeSearch(List<Room> rooms, Object criteria) {
        if (strategy == null) {
            throw new IllegalStateException("Search strategy not set");
        }
        return strategy.search(rooms, criteria);
    }
}

// Observer Pattern for Notifications
public interface Observer {
    void update(String message);
}

public class Customer implements Observer {
    // ... existing Customer code ...
    
    @Override
    public void update(String message) {
        System.out.println("Notification to " + name + ": " + message);
        // Could send email, SMS, etc.
    }
}

public class NotificationManager {
    private List<Observer> observers = new ArrayList<>();
    
    public void addObserver(Observer observer) {
        observers.add(observer);
    }
    
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }
    
    public void notifyObservers(String message) {
        for (Observer observer : observers) {
            observer.update(message);
        }
    }
}

// Singleton Pattern for Hotel Management System
public class HotelManagementSystem {
    private static HotelManagementSystem instance;
    private List<Room> rooms;
    private List<Customer> customers;
    private List<Booking> bookings;
    private RoomSearchContext searchContext;
    private NotificationManager notificationManager;
    
    private HotelManagementSystem() {
        rooms = new ArrayList<>();
        customers = new ArrayList<>();
        bookings = new ArrayList<>();
        searchContext = new RoomSearchContext();
        notificationManager = new NotificationManager();
        initializeRooms();
    }
    
    public static synchronized HotelManagementSystem getInstance() {
        if (instance == null) {
            instance = new HotelManagementSystem();
        }
        return instance;
    }
    
    private void initializeRooms() {
        // Initialize some rooms
        rooms.add(RoomFactory.createRoom(RoomType.SINGLE, "101"));
        rooms.add(RoomFactory.createRoom(RoomType.SINGLE, "102"));
        rooms.add(RoomFactory.createRoom(RoomType.DOUBLE, "201"));
        rooms.add(RoomFactory.createRoom(RoomType.DOUBLE, "202"));
        rooms.add(RoomFactory.createRoom(RoomType.SUITE, "301"));
    }
    
    // Customer Management
    public void addCustomer(Customer customer) {
        customers.add(customer);
    }
    
    public Customer findCustomer(String customerId) {
        return customers.stream()
                .filter(c -> c.getCustomerId().equals(customerId))
                .findFirst()
                .orElse(null);
    }
    
    // Room Management
    public void addRoom(Room room) {
        rooms.add(room);
    }
    
    public Room findRoom(String roomNumber) {
        return rooms.stream()
                .filter(r -> r.getRoomNumber().equals(roomNumber))
                .findFirst()
                .orElse(null);
    }
    
    public List<Room> getAvailableRooms() {
        return rooms.stream()
                .filter(room -> room.getStatus() == RoomStatus.AVAILABLE)
                .collect(Collectors.toList());
    }
    
    // Search functionality
    public List<Room> searchRoomsByType(RoomType roomType) {
        searchContext.setSearchStrategy(new SearchByRoomType());
        return searchContext.executeSearch(rooms, roomType);
    }
    
    public List<Room> searchRoomsByPriceRange(double minPrice, double maxPrice) {
        searchContext.setSearchStrategy(new SearchByPriceRange());
        return searchContext.executeSearch(rooms, new double[]{minPrice, maxPrice});
    }
    
    public List<Room> searchRoomsByCapacity(int capacity) {
        searchContext.setSearchStrategy(new SearchByCapacity());
        return searchContext.executeSearch(rooms, capacity);
    }
    
   // Thread-safe booking
    public Booking createBooking(String custId, String roomNumber, LocalDate in, LocalDate out) {
        Room room = rooms.stream().filter(r -> r.getRoomNumber().equals(roomNumber)).findFirst().orElse(null);
        Customer cust = getCustomer(custId);
        if (room == null || cust == null) return null;

        ReentrantLock lock = roomLocks.get(roomNumber);
        lock.lock();
        try {
            // Check again inside lock for availability in date range
            boolean available = bookings.stream().noneMatch(b ->
                    b.getRoom().getRoomNumber().equals(roomNumber) &&
                    !(out.isBefore(b.getCheckInDate()) || in.isAfter(b.getCheckOutDate().minusDays(1)))
            );

            if (!available) {
                return null; // Already booked
            }

            String bookingId = "BOOK" + System.currentTimeMillis();
            Booking booking = new Booking(bookingId, cust, room, in, out);
            bookings.add(booking);
            return booking;
        } finally {
            lock.unlock();
        }
    }

   public List<Room> searchRooms(SearchCriteria criteria) {
        return new SearchByTypePriceDate().search(rooms, criteria, bookings);
    }
    
    public boolean checkIn(String bookingId) {
        Booking booking = findBooking(bookingId);
        if (booking != null && booking.getStatus() == BookingStatus.CONFIRMED) {
            booking.setStatus(BookingStatus.CHECKED_IN);
            booking.getRoom().setStatus(RoomStatus.OCCUPIED);
            
            notificationManager.notifyObservers("Check-in completed for booking: " + bookingId);
            return true;
        }
        return false;
    }
    
    public boolean checkOut(String bookingId) {
        Booking booking = findBooking(bookingId);
        if (booking != null && booking.getStatus() == BookingStatus.CHECKED_IN) {
            booking.setStatus(BookingStatus.CHECKED_OUT);
            booking.getRoom().setStatus(RoomStatus.AVAILABLE);
            
            notificationManager.notifyObservers("Check-out completed for booking: " + bookingId);
            return true;
        }
        return false;
    }
    
    public Booking findBooking(String bookingId) {
        return bookings.stream()
                .filter(b -> b.getBookingId().equals(bookingId))
                .findFirst()
                .orElse(null);
    }
    
    public List<Booking> getCustomerBookings(String customerId) {
        return bookings.stream()
                .filter(b -> b.getCustomer().getCustomerId().equals(customerId))
                .collect(Collectors.toList());
    }
    
    // Room Service Management (using Decorator Pattern)
    public Room addRoomService(String roomNumber, String service) {
        Room room = findRoom(roomNumber);
        if (room == null) return null;
        
        switch (service.toLowerCase()) {
            case "breakfast":
                return new BreakfastDecorator(room);
            case "wifi":
                return new WiFiDecorator(room);
            case "parking":
                return new ParkingDecorator(room);
            default:
                return room;
        }
    }
}
