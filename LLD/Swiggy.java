import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Food Ordering System (Interview-ready LLD)
 * - Clean layering: Models + Repos + Services + Strategies + Factories
 * - Patterns used:
 *   1) Strategy: driver assignment, restaurant sorting
 *   2) State: order lifecycle
 *   3) Factory: payment method, notification channel
 * - In-memory repositories (easy to swap later)
 */

// =========================
// Models (Value Objects)
// =========================
final class Location {
    private final double lat;
    private final double lng;

    public Location(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public double distanceTo(Location other) {
        double dx = this.lat - other.lat;
        double dy = this.lng - other.lng;
        return Math.sqrt(dx * dx + dy * dy);
    }
}

final class Address {
    private final String id;
    private final String description;
    private final String pincode;
    private final Location location;

    public Address(String id, String description, String pincode, Location location) {
        this.id = id;
        this.description = description;
        this.pincode = pincode;
        this.location = location;
    }

    public String getPincode() { return pincode; }
    public Location getLocation() { return location; }
    public String getDescription() { return description; }
}

// =========================
// Models (People)
// =========================
abstract class Person {
    private final String id;
    private final String name;
    private final String phoneNumber;
    private Location location;

    protected Person(String id, String name, String phoneNumber, Location location) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.phoneNumber = Objects.requireNonNull(phoneNumber);
        this.location = Objects.requireNonNull(location);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPhoneNumber() { return phoneNumber; }
    public Location getLocation() { return location; }

    public void updateLocation(Location newLocation) {
        this.location = Objects.requireNonNull(newLocation);
    }
}

final class Account {
    private PaymentMode preferredPaymentMode;

    public Account(PaymentMode preferredPaymentMode) {
        this.preferredPaymentMode = preferredPaymentMode;
    }

    public PaymentMode getPreferredPaymentMode() { return preferredPaymentMode; }
    public void setPreferredPaymentMode(PaymentMode mode) { this.preferredPaymentMode = mode; }
}

final class User extends Person {
    private final List<Address> savedAddresses = new ArrayList<>();
    private final Account account;
    private Cart activeCart; // one active cart for simplicity

    public User(String id, String name, String phone, Location location, Account account) {
        super(id, name, phone, location);
        this.account = Objects.requireNonNull(account);
    }

    public void addAddress(Address addr) { savedAddresses.add(Objects.requireNonNull(addr)); }
    public List<Address> getSavedAddresses() { return Collections.unmodifiableList(savedAddresses); }
    public Account getAccount() { return account; }

    public Cart getOrCreateCart() {
        if (activeCart == null) activeCart = new Cart(getId());
        return activeCart;
    }

    public void clearCart() { this.activeCart = null; }
}

final class Driver extends Person {
    private boolean available = true;
    private String currentOrderId; // minimal linkage

    public Driver(String id, String name, String phone, Location location) {
        super(id, name, phone, location);
    }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public void assignOrder(String orderId) {
        this.currentOrderId = orderId;
        this.available = false;
    }

    public void clearOrder() {
        this.currentOrderId = null;
        this.available = true;
    }
}

// =========================
// Models (Restaurant, Menu, Rating)
// =========================
final class MenuItem {
    private final String id;
    private final String name;
    private final BigDecimal price;

    public MenuItem(String id, String name, BigDecimal price) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.price = Objects.requireNonNull(price);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
}

final class Review {
    private final int rating; // 1..5
    private final String text;
    private final String userId;
    private final LocalDateTime time;

    public Review(int rating, String text, String userId, LocalDateTime time) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("rating must be 1..5");
        this.rating = rating;
        this.text = text;
        this.userId = userId;
        this.time = time == null ? LocalDateTime.now() : time;
    }

    public int getRating() { return rating; }
}

final class RatingAggregate {
    private int total;
    private int count;

    public void add(int rating) {
        total += rating;
        count++;
    }

    public double average() {
        return count == 0 ? 0.0 : (total * 1.0) / count;
    }
}

final class Restaurant {
    private final String id;
    private final String name;
    private final Address address;
    private final Set<String> servicePincodes = new HashSet<>();

    // menuId -> MenuItem
    private final Map<String, MenuItem> menu = new LinkedHashMap<>();

    // Capacity model (simple): how many item-quantities can be in-flight at a time
    private final int totalCapacity;
    private int capacityInUse;

    private final RatingAggregate ratingAggregate = new RatingAggregate();

    public Restaurant(String id, String name, Address address, int totalCapacity, Collection<String> servicePincodes) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.address = Objects.requireNonNull(address);
        this.totalCapacity = totalCapacity;
        if (servicePincodes != null) this.servicePincodes.addAll(servicePincodes);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Address getAddress() { return address; }
    public double getAverageRating() { return ratingAggregate.average(); }
    public Map<String, MenuItem> getMenu() { return Collections.unmodifiableMap(menu); }

    public void upsertMenuItem(MenuItem item) {
        menu.put(item.getId(), item);
    }

    public boolean serves(String pincode) {
        return pincode != null && servicePincodes.contains(pincode);
    }

    public boolean canFulfill(List<OrderLine> lines) {
        int neededQty = 0;
        for (OrderLine l : lines) {
            if (!menu.containsKey(l.getMenuItemId())) return false;
            neededQty += l.getQuantity();
        }
        return (capacityInUse + neededQty) <= totalCapacity;
    }

    public void allocateCapacity(List<OrderLine> lines) {
        capacityInUse += totalQuantity(lines);
    }

    public void releaseCapacity(List<OrderLine> lines) {
        capacityInUse -= totalQuantity(lines);
        if (capacityInUse < 0) capacityInUse = 0;
    }

    public BigDecimal calculateCost(List<OrderLine> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderLine l : lines) {
            MenuItem mi = menu.get(l.getMenuItemId());
            sum = sum.add(mi.getPrice().multiply(BigDecimal.valueOf(l.getQuantity())));
        }
        return sum;
    }

    public void addReview(Review review) {
        ratingAggregate.add(review.getRating());
    }

    private int totalQuantity(List<OrderLine> lines) {
        int qty = 0;
        for (OrderLine l : lines) qty += l.getQuantity();
        return qty;
    }
}

// =========================
// Models (Cart, Order)
// =========================
final class OrderLine {
    private final String menuItemId;
    private final int quantity;

    public OrderLine(String menuItemId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        this.menuItemId = Objects.requireNonNull(menuItemId);
        this.quantity = quantity;
    }

    public String getMenuItemId() { return menuItemId; }
    public int getQuantity() { return quantity; }
}

final class Cart {
    private final String userId;
    private String restaurantId; // cart is tied to one restaurant
    private final Map<String, Integer> items = new LinkedHashMap<>(); // menuItemId -> qty

    public Cart(String userId) {
        this.userId = Objects.requireNonNull(userId);
    }

    public String getUserId() { return userId; }
    public String getRestaurantId() { return restaurantId; }

    public void selectRestaurant(String restaurantId) {
        if (this.restaurantId != null && !this.restaurantId.equals(restaurantId)) {
            items.clear(); // simple: switching restaurant clears cart
        }
        this.restaurantId = Objects.requireNonNull(restaurantId);
    }

    public void addItem(String menuItemId, int qty) {
        if (restaurantId == null) throw new IllegalStateException("Select restaurant first");
        if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
        items.put(menuItemId, items.getOrDefault(menuItemId, 0) + qty);
    }

    public void removeItem(String menuItemId) {
        items.remove(menuItemId);
    }

    public List<OrderLine> toOrderLines() {
        List<OrderLine> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            lines.add(new OrderLine(e.getKey(), e.getValue()));
        }
        return lines;
    }

    public boolean isEmpty() { return items.isEmpty(); }
}

enum PaymentMode { CASH, CARD, UPI }
enum PaymentStatus { SUCCESS, FAILED }

interface OrderState {
    OrderState next();
    String name();
}

final class CreatedState implements OrderState {
    public OrderState next() { return new AcceptedState(); }
    public String name() { return "CREATED"; }
}

final class AcceptedState implements OrderState {
    public OrderState next() { return new PickedUpState(); }
    public String name() { return "ACCEPTED"; }
}

final class PickedUpState implements OrderState {
    public OrderState next() { return new DeliveredState(); }
    public String name() { return "PICKED_UP"; }
}

final class DeliveredState implements OrderState {
    public OrderState next() { return this; }
    public String name() { return "DELIVERED"; }
}

final class Order {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final String id;
    private final String userId;
    private final String restaurantId;
    private final Address deliveryAddress;
    private final List<OrderLine> lines;

    private final BigDecimal amount;
    private PaymentStatus paymentStatus;

    private OrderState state = new CreatedState();
    private String driverId;

    private final LocalDateTime createdAt = LocalDateTime.now();

    public Order(String userId, String restaurantId, Address deliveryAddress, List<OrderLine> lines, BigDecimal amount) {
        this.id = String.valueOf(COUNTER.incrementAndGet());
        this.userId = Objects.requireNonNull(userId);
        this.restaurantId = Objects.requireNonNull(restaurantId);
        this.deliveryAddress = Objects.requireNonNull(deliveryAddress);
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.amount = Objects.requireNonNull(amount);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getRestaurantId() { return restaurantId; }
    public Address getDeliveryAddress() { return deliveryAddress; }
    public List<OrderLine> getLines() { return lines; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return state.name(); }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getDriverId() { return driverId; }

    public void markPayment(PaymentStatus status) { this.paymentStatus = status; }

    public void assignDriver(String driverId) { this.driverId = driverId; }

    public void advance() { this.state = state.next(); }

    public boolean isDelivered() { return "DELIVERED".equals(state.name()); }

    @Override
    public String toString() {
        return "Order{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", restaurantId='" + restaurantId + '\'' +
                ", amount=" + amount +
                ", paymentStatus=" + paymentStatus +
                ", status=" + state.name() +
                ", driverId=" + driverId +
                ", createdAt=" + createdAt +
                '}';
    }
}

// =========================
// Repositories (Interfaces)
// =========================
interface UserRepository {
    void save(User user);
    User findById(String userId);
}

interface RestaurantRepository {
    void save(Restaurant restaurant);
    Restaurant findById(String restaurantId);
    List<Restaurant> findAll();
}

interface DriverRepository {
    void save(Driver driver);
    Driver findById(String driverId);
    List<Driver> findAll();
}

interface OrderRepository {
    void save(Order order);
    Order findById(String orderId);
    List<Order> findByUserId(String userId);
}

// =========================
// Repositories (In-Memory)
// =========================
final class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> map = new HashMap<>();
    public void save(User user) { map.put(user.getId(), user); }
    public User findById(String userId) { return map.get(userId); }
}

final class InMemoryRestaurantRepository implements RestaurantRepository {
    private final Map<String, Restaurant> map = new LinkedHashMap<>();
    public void save(Restaurant restaurant) { map.put(restaurant.getId(), restaurant); }
    public Restaurant findById(String restaurantId) { return map.get(restaurantId); }
    public List<Restaurant> findAll() { return new ArrayList<>(map.values()); }
}

final class InMemoryDriverRepository implements DriverRepository {
    private final Map<String, Driver> map = new LinkedHashMap<>();
    public void save(Driver driver) { map.put(driver.getId(), driver); }
    public Driver findById(String driverId) { return map.get(driverId); }
    public List<Driver> findAll() { return new ArrayList<>(map.values()); }
}

final class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> map = new LinkedHashMap<>();
    private final Map<String, List<String>> userOrders = new HashMap<>(); // userId -> orderIds

    public void save(Order order) {
        map.put(order.getId(), order);
        userOrders.computeIfAbsent(order.getUserId(), k -> new ArrayList<>()).add(order.getId());
    }

    public Order findById(String orderId) { return map.get(orderId); }

    public List<Order> findByUserId(String userId) {
        List<String> ids = userOrders.getOrDefault(userId, Collections.emptyList());
        List<Order> res = new ArrayList<>();
        for (String id : ids) res.add(map.get(id));
        return res;
    }
}

// =========================
// Strategies
// =========================
interface DriverAssignmentStrategy {
    Driver assign(List<Driver> availableDrivers, Location dropLocation);
}

final class NearestDriverAssignmentStrategy implements DriverAssignmentStrategy {
    public Driver assign(List<Driver> drivers, Location dropLocation) {
        Driver best = null;
        double bestDist = Double.MAX_VALUE;
        for (Driver d : drivers) {
            if (!d.isAvailable()) continue;
            double dist = d.getLocation().distanceTo(dropLocation);
            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }
        return best;
    }
}

interface RestaurantSortStrategy {
    List<Restaurant> sort(List<Restaurant> restaurants);
}

final class SortByRatingDesc implements RestaurantSortStrategy {
    public List<Restaurant> sort(List<Restaurant> restaurants) {
        List<Restaurant> copy = new ArrayList<>(restaurants);
        copy.sort((a, b) -> Double.compare(b.getAverageRating(), a.getAverageRating()));
        return copy;
    }
}

final class SortByNameAsc implements RestaurantSortStrategy {
    public List<Restaurant> sort(List<Restaurant> restaurants) {
        List<Restaurant> copy = new ArrayList<>(restaurants);
        copy.sort(Comparator.comparing(Restaurant::getName));
        return copy;
    }
}

// =========================
// Factories (Payment, Notification)
// =========================
interface PaymentMethod {
    PaymentStatus pay(User user, BigDecimal amount);
}

final class CardPayment implements PaymentMethod {
    public PaymentStatus pay(User user, BigDecimal amount) {
        // Stub: simulate success
        return PaymentStatus.SUCCESS;
    }
}

final class UpiPayment implements PaymentMethod {
    public PaymentStatus pay(User user, BigDecimal amount) {
        return PaymentStatus.SUCCESS;
    }
}

final class CashPayment implements PaymentMethod {
    public PaymentStatus pay(User user, BigDecimal amount) {
        // Cash on delivery -> treat as success for order placement
        return PaymentStatus.SUCCESS;
    }
}

final class PaymentMethodFactory {
    public PaymentMethod create(PaymentMode mode) {
        if (mode == PaymentMode.CARD) return new CardPayment();
        if (mode == PaymentMode.UPI) return new UpiPayment();
        if (mode == PaymentMode.CASH) return new CashPayment();
        throw new IllegalArgumentException("Unsupported payment mode: " + mode);
    }
}

interface Notification {
    void send(User user, String message);
}

final class SmsNotification implements Notification {
    public void send(User user, String message) {
        System.out.println("SMS to " + user.getPhoneNumber() + " : " + message);
    }
}

final class NotificationFactory {
    public Notification create(String type) {
        if ("SMS".equalsIgnoreCase(type)) return new SmsNotification();
        throw new IllegalArgumentException("Unsupported notification type: " + type);
    }
}

// =========================
// Services
// =========================
final class UserService {
    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    public void register(User user) { userRepo.save(user); }
    public User get(String userId) { return userRepo.findById(userId); }
}

final class RestaurantService {
    private final RestaurantRepository restaurantRepo;

    public RestaurantService(RestaurantRepository restaurantRepo) {
        this.restaurantRepo = restaurantRepo;
    }

    public void onboard(Restaurant restaurant) { restaurantRepo.save(restaurant); }

    public void upsertMenuItem(String restaurantId, MenuItem item) {
        Restaurant r = restaurantRepo.findById(restaurantId);
        if (r == null) throw new IllegalArgumentException("restaurant not found");
        r.upsertMenuItem(item);
    }

    public List<Restaurant> searchByRestaurantName(String query, String pincode) {
        List<Restaurant> res = new ArrayList<>();
        for (Restaurant r : restaurantRepo.findAll()) {
            if (r.serves(pincode) && r.getName().toLowerCase().contains(query.toLowerCase())) {
                res.add(r);
            }
        }
        return res;
    }

    public List<Restaurant> searchByDishName(String dishQuery, String pincode) {
        List<Restaurant> res = new ArrayList<>();
        for (Restaurant r : restaurantRepo.findAll()) {
            if (!r.serves(pincode)) continue;
            for (MenuItem mi : r.getMenu().values()) {
                if (mi.getName().toLowerCase().contains(dishQuery.toLowerCase())) {
                    res.add(r);
                    break;
                }
            }
        }
        return res;
    }

    public List<Restaurant> sort(List<Restaurant> restaurants, RestaurantSortStrategy sorter) {
        return sorter.sort(restaurants);
    }

    public void addRating(String restaurantId, Review review) {
        Restaurant r = restaurantRepo.findById(restaurantId);
        if (r == null) throw new IllegalArgumentException("restaurant not found");
        r.addReview(review);
    }

    public Restaurant get(String restaurantId) { return restaurantRepo.findById(restaurantId); }
}

final class DriverService {
    private final DriverRepository driverRepo;

    public DriverService(DriverRepository driverRepo) {
        this.driverRepo = driverRepo;
    }

    public void register(Driver driver) { driverRepo.save(driver); }

    public void updateLocation(String driverId, Location loc) {
        Driver d = driverRepo.findById(driverId);
        if (d == null) throw new IllegalArgumentException("driver not found");
        d.updateLocation(loc);
    }

    public List<Driver> availableDrivers() {
        List<Driver> res = new ArrayList<>();
        for (Driver d : driverRepo.findAll()) if (d.isAvailable()) res.add(d);
        return res;
    }

    public Driver get(String driverId) { return driverRepo.findById(driverId); }
}

final class PaymentService {
    private final PaymentMethodFactory factory;

    public PaymentService(PaymentMethodFactory factory) {
        this.factory = factory;
    }

    public PaymentStatus process(User user, BigDecimal amount) {
        PaymentMode preferred = user.getAccount().getPreferredPaymentMode();
        return factory.create(preferred).pay(user, amount);
    }
}

final class NotificationService {
    private final NotificationFactory factory;
    private final String defaultChannel; // keep it simple

    public NotificationService(NotificationFactory factory, String defaultChannel) {
        this.factory = factory;
        this.defaultChannel = defaultChannel;
    }

    public void notifyUser(User user, String msg) {
        factory.create(defaultChannel).send(user, msg);
    }
}

final class OrderService {
    private final OrderRepository orderRepo;
    private final RestaurantRepository restaurantRepo;
    private final DriverRepository driverRepo;

    private final DriverAssignmentStrategy driverStrategy;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    public OrderService(OrderRepository orderRepo,
                        RestaurantRepository restaurantRepo,
                        DriverRepository driverRepo,
                        DriverAssignmentStrategy driverStrategy,
                        PaymentService paymentService,
                        NotificationService notificationService) {

        this.orderRepo = orderRepo;
        this.restaurantRepo = restaurantRepo;
        this.driverRepo = driverRepo;
        this.driverStrategy = driverStrategy;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
    }

    // Core flow: validate -> price -> pay -> create order -> allocate -> assign driver -> notify
    public Order placeOrder(User user, Cart cart, Address deliveryAddress) {
        if (cart == null || cart.isEmpty()) throw new IllegalArgumentException("cart is empty");
        if (cart.getRestaurantId() == null) throw new IllegalArgumentException("cart has no restaurant");

        Restaurant restaurant = restaurantRepo.findById(cart.getRestaurantId());
        if (restaurant == null) throw new IllegalArgumentException("restaurant not found");

        if (!restaurant.serves(deliveryAddress.getPincode())) {
            throw new IllegalArgumentException("restaurant does not serve pincode: " + deliveryAddress.getPincode());
        }

        List<OrderLine> lines = cart.toOrderLines();
        if (!restaurant.canFulfill(lines)) {
            throw new IllegalStateException("restaurant cannot fulfill right now (menu/capacity)");
        }

        BigDecimal amount = restaurant.calculateCost(lines);
        PaymentStatus payStatus = paymentService.process(user, amount);
        if (payStatus == PaymentStatus.FAILED) {
            notificationService.notifyUser(user, "Payment failed. Please try again.");
            Order failed = new Order(user.getId(), restaurant.getId(), deliveryAddress, lines, amount);
            failed.markPayment(PaymentStatus.FAILED);
            orderRepo.save(failed);
            return failed;
        }

        // Payment success -> reserve capacity and create order
        restaurant.allocateCapacity(lines);
        Order order = new Order(user.getId(), restaurant.getId(), deliveryAddress, lines, amount);
        order.markPayment(PaymentStatus.SUCCESS);

        // Assign driver (optional)
        Driver driver = driverStrategy.assign(driverRepo.findAll(), deliveryAddress.getLocation());
        if (driver != null && driver.isAvailable()) {
            order.assignDriver(driver.getId());
            driver.assignOrder(order.getId());
        }

        orderRepo.save(order);
        notificationService.notifyUser(user, "Order placed successfully. OrderId=" + order.getId());
        user.clearCart();
        return order;
    }

    // Driver / system advances state (State pattern)
    public Order advanceOrder(String orderId) {
        Order order = orderRepo.findById(orderId);
        if (order == null) throw new IllegalArgumentException("order not found");

        order.advance();

        // On delivered: release restaurant capacity, free driver
        if (order.isDelivered()) {
            Restaurant r = restaurantRepo.findById(order.getRestaurantId());
            if (r != null) r.releaseCapacity(order.getLines());

            if (order.getDriverId() != null) {
                Driver d = driverRepo.findById(order.getDriverId());
                if (d != null) d.clearOrder();
            }
        }
        return order;
    }

    public Order track(String orderId) { return orderRepo.findById(orderId); }
}

final class OrderHistoryService {
    private final OrderRepository orderRepo;

    public OrderHistoryService(OrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    public List<Order> getUserOrders(String userId) {
        return orderRepo.findByUserId(userId);
    }

    public List<Order> getUserOrdersByStatus(String userId, String status) {
        List<Order> all = orderRepo.findByUserId(userId);
        List<Order> res = new ArrayList<>();
        for (Order o : all) if (o.getStatus().equalsIgnoreCase(status)) res.add(o);
        return res;
    }

    public List<Order> getLastN(String userId, int n) {
        List<Order> all = orderRepo.findByUserId(userId);
        if (n <= 0) return Collections.emptyList();
        int from = Math.max(0, all.size() - n);
        return all.subList(from, all.size());
    }
}

// =========================
// Demo (Minimal)
// =========================
public class FoodOrderingLLDDemo {
    public static void main(String[] args) {
        // Repos
        UserRepository userRepo = new InMemoryUserRepository();
        RestaurantRepository restaurantRepo = new InMemoryRestaurantRepository();
        DriverRepository driverRepo = new InMemoryDriverRepository();
        OrderRepository orderRepo = new InMemoryOrderRepository();

        // Services
        UserService userService = new UserService(userRepo);
        RestaurantService restaurantService = new RestaurantService(restaurantRepo);
        DriverService driverService = new DriverService(driverRepo);

        PaymentService paymentService = new PaymentService(new PaymentMethodFactory());
        NotificationService notificationService = new NotificationService(new NotificationFactory(), "SMS");
        OrderService orderService = new OrderService(
                orderRepo, restaurantRepo, driverRepo,
                new NearestDriverAssignmentStrategy(),
                paymentService,
                notificationService
        );
        OrderHistoryService historyService = new OrderHistoryService(orderRepo);

        // Setup user
        User u1 = new User("U1", "Purna", "phone-1", new Location(17.44, 78.39), new Account(PaymentMode.UPI));
        Address home = new Address("A1", "Home", "HSR", new Location(17.45, 78.40));
        u1.addAddress(home);
        userService.register(u1);

        // Setup restaurants
        Restaurant r1 = new Restaurant("R1", "Food Court-1",
                new Address("RA1", "FC1", "HSR", new Location(17.46, 78.41)),
                15,
                Arrays.asList("HSR", "BTM"));
        r1.upsertMenuItem(new MenuItem("M1", "Samosa Pizza", new BigDecimal("20")));
        r1.upsertMenuItem(new MenuItem("M2", "King Burger", new BigDecimal("15")));
        restaurantService.onboard(r1);

        Restaurant r2 = new Restaurant("R2", "Food Court-2",
                new Address("RA2", "FC2", "BTM", new Location(17.43, 78.38)),
                12,
                Arrays.asList("BTM", "HSR"));
        r2.upsertMenuItem(new MenuItem("M3", "Bendi Macaroni", new BigDecimal("12")));
        r2.upsertMenuItem(new MenuItem("M1", "Samosa Pizza", new BigDecimal("25")));
        restaurantService.onboard(r2);

        // Setup drivers
        Driver d1 = new Driver("D1", "Driver-1", "dphone-1", new Location(17.44, 78.401));
        Driver d2 = new Driver("D2", "Driver-2", "dphone-2", new Location(17.40, 78.35));
        driverService.register(d1);
        driverService.register(d2);

        // Search + Sort (strategy)
        List<Restaurant> byDish = restaurantService.searchByDishName("samosa", home.getPincode());
        byDish = restaurantService.sort(byDish, new SortByNameAsc());
        System.out.println("Restaurants serving 'samosa' near pincode=" + home.getPincode());
        for (Restaurant r : byDish) System.out.println(" - " + r.getName());

        // Cart flow
        Cart cart = u1.getOrCreateCart();
        cart.selectRestaurant("R2");
        cart.addItem("M3", 3);
        cart.addItem("M1", 2);

        // Place order
        Order order = orderService.placeOrder(u1, cart, home);
        System.out.println("Placed: " + order);

        // Advance order states
        System.out.println("Status: " + orderService.track(order.getId()).getStatus());
        orderService.advanceOrder(order.getId()); // CREATED -> ACCEPTED
        System.out.println("Status: " + orderService.track(order.getId()).getStatus());
        orderService.advanceOrder(order.getId()); // ACCEPTED -> PICKED_UP
        System.out.println("Status: " + orderService.track(order.getId()).getStatus());
        orderService.advanceOrder(order.getId()); // PICKED_UP -> DELIVERED (releases capacity, frees driver)
        System.out.println("Status: " + orderService.track(order.getId()).getStatus());

        // Rating
        restaurantService.addRating(order.getRestaurantId(), new Review(5, "Nice food", u1.getId(), LocalDateTime.now()));
        restaurantService.addRating(order.getRestaurantId(), new Review(4, "Good", u1.getId(), LocalDateTime.now()));

        // History
        System.out.println("Order history for user " + u1.getId() + " => " + historyService.getUserOrders(u1.getId()));
        System.out.println("Delivered orders => " + historyService.getUserOrdersByStatus(u1.getId(), "DELIVERED"));
    }
}
