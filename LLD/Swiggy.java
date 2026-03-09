import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Swiggy-like Food Ordering System (Interview-ready LLD)
 * Added: Bill, Payment record, Delivery entity, DeliveryChargeStrategy, Coupon system, Order history (best way)
 *
 * Patterns:
 * - Strategy: driver assignment, restaurant sorting, delivery charge
 * - State: order lifecycle
 * - Factory: payment method, notification channel, delivery charge strategy
 * - Singleton: Coupon instances (Bank10Coupon)
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
// People
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

enum PaymentMode { CASH, CARD, UPI }
enum PaymentStatus { SUCCESS, FAILED }

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
    private String currentOrderId;

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
// Restaurant, Menu, Rating
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
    private final Map<String, MenuItem> menu = new LinkedHashMap<>();

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

    public void upsertMenuItem(MenuItem item) { menu.put(item.getId(), item); }

    public boolean serves(String pincode) { return pincode != null && servicePincodes.contains(pincode); }

    public boolean canFulfill(List<OrderLine> lines) {
        int neededQty = 0;
        for (OrderLine l : lines) {
            if (!menu.containsKey(l.getMenuItemId())) return false;
            neededQty += l.getQuantity();
        }
        return (capacityInUse + neededQty) <= totalCapacity;
    }

    public void allocateCapacity(List<OrderLine> lines) { capacityInUse += totalQuantity(lines); }

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

    public void addReview(Review review) { ratingAggregate.add(review.getRating()); }

    private int totalQuantity(List<OrderLine> lines) {
        int qty = 0;
        for (OrderLine l : lines) qty += l.getQuantity();
        return qty;
    }
}

// =========================
// Cart, Order
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
    private String restaurantId;
    private final Map<String, Integer> items = new LinkedHashMap<>();

    public Cart(String userId) { this.userId = Objects.requireNonNull(userId); }

    public String getUserId() { return userId; }
    public String getRestaurantId() { return restaurantId; }

    public void selectRestaurant(String restaurantId) {
        if (this.restaurantId != null && !this.restaurantId.equals(restaurantId)) items.clear();
        this.restaurantId = Objects.requireNonNull(restaurantId);
    }

    public void addItem(String menuItemId, int qty) {
        if (restaurantId == null) throw new IllegalStateException("Select restaurant first");
        if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
        items.put(menuItemId, items.getOrDefault(menuItemId, 0) + qty);
    }

    public void removeItem(String menuItemId) { items.remove(menuItemId); }

    public List<OrderLine> toOrderLines() {
        List<OrderLine> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> e : items.entrySet()) lines.add(new OrderLine(e.getKey(), e.getValue()));
        return lines;
    }

    public boolean isEmpty() { return items.isEmpty(); }
}

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

    private final BigDecimal itemTotal;
    private BigDecimal payableAmount; // after tax/discount/delivery
    private PaymentStatus paymentStatus;

    private OrderState state = new CreatedState();
    private String driverId;

    // Links
    private String billId;
    private String deliveryId;

    private final LocalDateTime createdAt = LocalDateTime.now();

    public Order(String userId, String restaurantId, Address deliveryAddress, List<OrderLine> lines, BigDecimal itemTotal) {
        this.id = String.valueOf(COUNTER.incrementAndGet());
        this.userId = Objects.requireNonNull(userId);
        this.restaurantId = Objects.requireNonNull(restaurantId);
        this.deliveryAddress = Objects.requireNonNull(deliveryAddress);
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.itemTotal = Objects.requireNonNull(itemTotal);
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getRestaurantId() { return restaurantId; }
    public Address getDeliveryAddress() { return deliveryAddress; }
    public List<OrderLine> getLines() { return lines; }
    public BigDecimal getItemTotal() { return itemTotal; }

    public BigDecimal getPayableAmount() { return payableAmount; }
    public void setPayableAmount(BigDecimal payableAmount) { this.payableAmount = payableAmount; }

    public String getStatus() { return state.name(); }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public String getDriverId() { return driverId; }

    public String getBillId() { return billId; }
    public String getDeliveryId() { return deliveryId; }
    public void setBillId(String billId) { this.billId = billId; }
    public void setDeliveryId(String deliveryId) { this.deliveryId = deliveryId; }

    public void markPayment(PaymentStatus status) { this.paymentStatus = status; }
    public void assignDriver(String driverId) { this.driverId = driverId; }
    public void advance() { this.state = state.next(); }

    public boolean isDelivered() { return "DELIVERED".equals(state.name()); }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "Order{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", restaurantId='" + restaurantId + '\'' +
                ", itemTotal=" + itemTotal +
                ", payable=" + payableAmount +
                ", paymentStatus=" + paymentStatus +
                ", status=" + state.name() +
                ", driverId=" + driverId +
                ", billId=" + billId +
                ", deliveryId=" + deliveryId +
                ", createdAt=" + createdAt +
                '}';
    }
}

// =========================
// Billing + Payment + Delivery (NEW)
// =========================
final class Bill {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final String billId;
    private final String orderId;

    private final BigDecimal itemTotal;
    private final BigDecimal tax;
    private final BigDecimal discount;
    private final BigDecimal deliveryFee;
    private final BigDecimal totalAmountToBePaid;

    private final LocalDateTime createdAt;

    public Bill(String orderId, BigDecimal itemTotal, BigDecimal tax, BigDecimal discount, BigDecimal deliveryFee) {
        this.billId = "B" + COUNTER.incrementAndGet();
        this.orderId = Objects.requireNonNull(orderId);

        this.itemTotal = nz(itemTotal);
        this.tax = nz(tax);
        this.discount = nz(discount);
        this.deliveryFee = nz(deliveryFee);

        this.totalAmountToBePaid = this.itemTotal.add(this.tax).add(this.deliveryFee).subtract(this.discount);
        this.createdAt = LocalDateTime.now();
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    public String getBillId() { return billId; }
    public String getOrderId() { return orderId; }
    public BigDecimal getTotalAmountToBePaid() { return totalAmountToBePaid; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }

    @Override
    public String toString() {
        return "Bill{" +
                "billId='" + billId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", itemTotal=" + itemTotal +
                ", tax=" + tax +
                ", discount=" + discount +
                ", deliveryFee=" + deliveryFee +
                ", payable=" + totalAmountToBePaid +
                ", createdAt=" + createdAt +
                '}';
    }
}

final class Payment {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final String id;
    private final String billId;
    private final BigDecimal amount;
    private final LocalDateTime date;
    private final PaymentStatus status;
    private final PaymentMode mode;
    private final String couponCode;

    public Payment(String billId, BigDecimal amount, PaymentStatus status, PaymentMode mode, String couponCode) {
        this.id = "P" + COUNTER.incrementAndGet();
        this.billId = Objects.requireNonNull(billId);
        this.amount = Objects.requireNonNull(amount);
        this.status = Objects.requireNonNull(status);
        this.mode = Objects.requireNonNull(mode);
        this.couponCode = couponCode;
        this.date = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getBillId() { return billId; }
    public PaymentStatus getStatus() { return status; }

    @Override
    public String toString() {
        return "Payment{" +
                "id='" + id + '\'' +
                ", billId='" + billId + '\'' +
                ", amount=" + amount +
                ", date=" + date +
                ", status=" + status +
                ", mode=" + mode +
                ", couponCode='" + couponCode + '\'' +
                '}';
    }
}

enum DeliveryStatus { CREATED, ASSIGNED, PICKED_UP, DELIVERED, CANCELLED }

final class Delivery {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final String id;
    private final String orderId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String deliveryBoyId;

    private final String customerNumber;
    private final Address customerAddress;
    private DeliveryStatus status = DeliveryStatus.CREATED;

    public Delivery(String orderId, String customerNumber, Address customerAddress) {
        this.id = "DEL" + COUNTER.incrementAndGet();
        this.orderId = Objects.requireNonNull(orderId);
        this.customerNumber = Objects.requireNonNull(customerNumber);
        this.customerAddress = Objects.requireNonNull(customerAddress);
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public DeliveryStatus getStatus() { return status; }

    public void assignDriver(String driverId) {
        this.deliveryBoyId = driverId;
        this.status = DeliveryStatus.ASSIGNED;
    }

    public void markPickedUp() {
        this.startDateTime = LocalDateTime.now();
        this.status = DeliveryStatus.PICKED_UP;
    }

    public void markDelivered() {
        this.endDateTime = LocalDateTime.now();
        this.status = DeliveryStatus.DELIVERED;
    }

    @Override
    public String toString() {
        return "Delivery{" +
                "id='" + id + '\'' +
                ", orderId='" + orderId + '\'' +
                ", startDateTime=" + startDateTime +
                ", endDateTime=" + endDateTime +
                ", deliveryBoyId='" + deliveryBoyId + '\'' +
                ", status=" + status +
                '}';
    }
}

// =========================
// Coupon System (NEW integration)
// =========================
enum CouponType { PERCENTAGE, FLAT }

interface Coupon {
    String code();
    boolean isEligible(User user, BigDecimal itemTotal);
    BigDecimal discountAmount(BigDecimal itemTotal);
    String getDescription();
}

final class Bank10Coupon implements Coupon {
    private static final Bank10Coupon INSTANCE =
            new Bank10Coupon("BANK10", "10% discount on BANK debit card", new BigDecimal("0.10"));

    private final String code;
    private final String description;
    private final BigDecimal pct;

    private Bank10Coupon(String code, String description, BigDecimal pct) {
        this.code = code;
        this.description = description;
        this.pct = pct;
    }

    public static Bank10Coupon getInstance() { return INSTANCE; }

    public String code() { return code; }

    public boolean isEligible(User user, BigDecimal itemTotal) {
        // Keep simple: min item total condition
        return itemTotal.compareTo(new BigDecimal("100.00")) >= 0;
    }

    public BigDecimal discountAmount(BigDecimal itemTotal) {
        return itemTotal.multiply(pct);
    }

    public String getDescription() { return description; }
}

final class CouponManager {
    private final Map<String, Coupon> couponsByCode = new HashMap<>();

    public CouponManager() {
        add(Bank10Coupon.getInstance());
    }

    public void add(Coupon c) { couponsByCode.put(c.code().toUpperCase(), c); }

    public Optional<Coupon> find(String code) {
        if (code == null || code.trim().isEmpty()) return Optional.empty();
        return Optional.ofNullable(couponsByCode.get(code.trim().toUpperCase()));
    }

    public Collection<Coupon> getAll() { return Collections.unmodifiableCollection(couponsByCode.values()); }
}

// =========================
// Repositories
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

    // Best way: query by partition key / index userId -> orderIds
    List<Order> findByUserId(String userId);
    List<Order> findByUserId(String userId, int limit);
    List<Order> findByUserIdAndStatus(String userId, String status);
}

interface BillRepository {
    void save(Bill bill);
    Bill findById(String billId);
    Bill findByOrderId(String orderId);
}

interface PaymentRepository {
    void save(Payment payment);
    List<Payment> findByBillId(String billId);
}

interface DeliveryRepository {
    void save(Delivery delivery);
    Delivery findById(String deliveryId);
    Delivery findByOrderId(String orderId);
}

// =========================
// In-Memory Repositories
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

    // ✅ best way: secondary index
    private final Map<String, List<String>> userOrders = new HashMap<>();

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

    public List<Order> findByUserId(String userId, int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<Order> all = findByUserId(userId);
        int from = Math.max(0, all.size() - limit);
        return all.subList(from, all.size());
    }

    public List<Order> findByUserIdAndStatus(String userId, String status) {
        List<Order> all = findByUserId(userId);
        List<Order> res = new ArrayList<>();
        for (Order o : all) if (o.getStatus().equalsIgnoreCase(status)) res.add(o);
        return res;
    }
}

final class InMemoryBillRepository implements BillRepository {
    private final Map<String, Bill> byId = new HashMap<>();
    private final Map<String, String> orderToBill = new HashMap<>();

    public void save(Bill bill) {
        byId.put(bill.getBillId(), bill);
        orderToBill.put(bill.getOrderId(), bill.getBillId());
    }

    public Bill findById(String billId) { return byId.get(billId); }

    public Bill findByOrderId(String orderId) {
        String billId = orderToBill.get(orderId);
        return billId == null ? null : byId.get(billId);
    }
}

final class InMemoryPaymentRepository implements PaymentRepository {
    private final Map<String, List<Payment>> billPayments = new HashMap<>();

    public void save(Payment payment) {
        billPayments.computeIfAbsent(payment.getBillId(), k -> new ArrayList<>()).add(payment);
    }

    public List<Payment> findByBillId(String billId) {
        return new ArrayList<>(billPayments.getOrDefault(billId, Collections.emptyList()));
    }
}

final class InMemoryDeliveryRepository implements DeliveryRepository {
    private final Map<String, Delivery> byId = new HashMap<>();
    private final Map<String, String> orderToDelivery = new HashMap<>();

    public void save(Delivery delivery) {
        byId.put(delivery.getId(), delivery);
        orderToDelivery.put(delivery.getOrderId(), delivery.getId());
    }

    public Delivery findById(String deliveryId) { return byId.get(deliveryId); }

    public Delivery findByOrderId(String orderId) {
        String delId = orderToDelivery.get(orderId);
        return delId == null ? null : byId.get(delId);
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
// Delivery Charge Strategy (NEW)
// =========================
interface DeliveryChargeStrategy {
    BigDecimal getPrice(Address from, Address to);
}

final class DefaultDeliveryChargeStrategy implements DeliveryChargeStrategy {
    // base + perKm * distance (distance is euclidean in this demo)
    public BigDecimal getPrice(Address from, Address to) {
        double dist = from.getLocation().distanceTo(to.getLocation());
        BigDecimal base = new BigDecimal("10.00");
        BigDecimal perKm = new BigDecimal("5.00");
        return base.add(perKm.multiply(BigDecimal.valueOf(dist)));
    }
}

final class SurgePricingDeliveryChargeStrategy implements DeliveryChargeStrategy {
    private final DeliveryChargeStrategy base;
    private final BigDecimal multiplier;

    public SurgePricingDeliveryChargeStrategy(DeliveryChargeStrategy base, BigDecimal multiplier) {
        this.base = base;
        this.multiplier = multiplier;
    }

    public BigDecimal getPrice(Address from, Address to) {
        return base.getPrice(from, to).multiply(multiplier);
    }
}

final class DeliveryChargeStrategyFactory {
    public DeliveryChargeStrategy create(boolean surgeOn) {
        DeliveryChargeStrategy base = new DefaultDeliveryChargeStrategy();
        if (!surgeOn) return base;
        return new SurgePricingDeliveryChargeStrategy(base, new BigDecimal("1.5"));
    }
}

// =========================
// Factories (Payment, Notification)
// =========================
interface PaymentMethod {
    PaymentStatus pay(User user, BigDecimal amount);
}

final class CardPayment implements PaymentMethod {
    public PaymentStatus pay(User user, BigDecimal amount) { return PaymentStatus.SUCCESS; }
}
final class UpiPayment implements PaymentMethod {
    public PaymentStatus pay(User user, BigDecimal amount) { return PaymentStatus.SUCCESS; }
}
final class CashPayment implements PaymentMethod {
    public PaymentStatus pay(User user, BigDecimal amount) { return PaymentStatus.SUCCESS; }
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
    public UserService(UserRepository userRepo) { this.userRepo = userRepo; }
    public void register(User user) { userRepo.save(user); }
    public User get(String userId) { return userRepo.findById(userId); }
}

final class RestaurantService {
    private final RestaurantRepository restaurantRepo;
    public RestaurantService(RestaurantRepository restaurantRepo) { this.restaurantRepo = restaurantRepo; }

    public void onboard(Restaurant restaurant) { restaurantRepo.save(restaurant); }

    public void upsertMenuItem(String restaurantId, MenuItem item) {
        Restaurant r = restaurantRepo.findById(restaurantId);
        if (r == null) throw new IllegalArgumentException("restaurant not found");
        r.upsertMenuItem(item);
    }

    public List<Restaurant> searchByRestaurantName(String query, String pincode) {
        List<Restaurant> res = new ArrayList<>();
        for (Restaurant r : restaurantRepo.findAll()) {
            if (r.serves(pincode) && r.getName().toLowerCase().contains(query.toLowerCase())) res.add(r);
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

    public List<Restaurant> sort(List<Restaurant> restaurants, RestaurantSortStrategy sorter) { return sorter.sort(restaurants); }

    public void addRating(String restaurantId, Review review) {
        Restaurant r = restaurantRepo.findById(restaurantId);
        if (r == null) throw new IllegalArgumentException("restaurant not found");
        r.addReview(review);
    }

    public Restaurant get(String restaurantId) { return restaurantRepo.findById(restaurantId); }
}

final class DriverService {
    private final DriverRepository driverRepo;
    public DriverService(DriverRepository driverRepo) { this.driverRepo = driverRepo; }

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
}

final class PaymentService {
    private final PaymentMethodFactory factory;
    public PaymentService(PaymentMethodFactory factory) { this.factory = factory; }

    public PaymentStatus process(User user, BigDecimal amount) {
        PaymentMode preferred = user.getAccount().getPreferredPaymentMode();
        return factory.create(preferred).pay(user, amount);
    }
}

final class NotificationService {
    private final NotificationFactory factory;
    private final String defaultChannel;

    public NotificationService(NotificationFactory factory, String defaultChannel) {
        this.factory = factory;
        this.defaultChannel = defaultChannel;
    }

    public void notifyUser(User user, String msg) { factory.create(defaultChannel).send(user, msg); }
}

// NEW: Billing / Delivery record services
final class BillingService {
    private final BillRepository billRepo;

    public BillingService(BillRepository billRepo) { this.billRepo = billRepo; }

    public Bill createBill(String orderId, BigDecimal itemTotal, BigDecimal tax, BigDecimal discount, BigDecimal deliveryFee) {
        Bill b = new Bill(orderId, itemTotal, tax, discount, deliveryFee);
        billRepo.save(b);
        return b;
    }

    public Bill getBillByOrder(String orderId) { return billRepo.findByOrderId(orderId); }
}

final class PaymentRecordService {
    private final PaymentRepository paymentRepo;

    public PaymentRecordService(PaymentRepository paymentRepo) { this.paymentRepo = paymentRepo; }

    public Payment record(String billId, BigDecimal amount, PaymentStatus status, PaymentMode mode, String couponCode) {
        Payment p = new Payment(billId, amount, status, mode, couponCode);
        paymentRepo.save(p);
        return p;
    }
}

final class DeliveryService {
    private final DeliveryRepository deliveryRepo;

    public DeliveryService(DeliveryRepository deliveryRepo) { this.deliveryRepo = deliveryRepo; }

    public Delivery createForOrder(String orderId, User user, Address addr) {
        Delivery d = new Delivery(orderId, user.getPhoneNumber(), addr);
        deliveryRepo.save(d);
        return d;
    }

    public void assignDriver(String orderId, String driverId) {
        Delivery d = deliveryRepo.findByOrderId(orderId);
        if (d != null) d.assignDriver(driverId);
    }

    public void markPickedUp(String orderId) {
        Delivery d = deliveryRepo.findByOrderId(orderId);
        if (d != null) d.markPickedUp();
    }

    public void markDelivered(String orderId) {
        Delivery d = deliveryRepo.findByOrderId(orderId);
        if (d != null) d.markDelivered();
    }

    public Delivery track(String orderId) { return deliveryRepo.findByOrderId(orderId); }
}

// =========================
// Order Service (extended)
// =========================
final class OrderService {
    private final OrderRepository orderRepo;
    private final RestaurantRepository restaurantRepo;
    private final DriverRepository driverRepo;

    private final DriverAssignmentStrategy driverStrategy;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    // NEW
    private final BillingService billingService;
    private final PaymentRecordService paymentRecordService;
    private final DeliveryService deliveryService;
    private final CouponManager couponManager;
    private final DeliveryChargeStrategyFactory deliveryChargeFactory;

    public OrderService(OrderRepository orderRepo,
                        RestaurantRepository restaurantRepo,
                        DriverRepository driverRepo,
                        DriverAssignmentStrategy driverStrategy,
                        PaymentService paymentService,
                        NotificationService notificationService,
                        BillingService billingService,
                        PaymentRecordService paymentRecordService,
                        DeliveryService deliveryService,
                        CouponManager couponManager,
                        DeliveryChargeStrategyFactory deliveryChargeFactory) {

        this.orderRepo = orderRepo;
        this.restaurantRepo = restaurantRepo;
        this.driverRepo = driverRepo;
        this.driverStrategy = driverStrategy;
        this.paymentService = paymentService;
        this.notificationService = notificationService;

        this.billingService = billingService;
        this.paymentRecordService = paymentRecordService;
        this.deliveryService = deliveryService;
        this.couponManager = couponManager;
        this.deliveryChargeFactory = deliveryChargeFactory;
    }

    /*
     * placeOrder:
     * validate -> compute itemTotal -> compute deliveryFee (strategy) -> apply coupon -> tax -> create order
     * -> create bill -> pay -> record payment -> allocate capacity -> create delivery -> assign driver -> notify
     */
    public Order placeOrder(User user, Cart cart, Address deliveryAddress, String couponCode, boolean surgeOn) {
        if (cart == null || cart.isEmpty()) throw new IllegalArgumentException("cart is empty");
        if (cart.getRestaurantId() == null) throw new IllegalArgumentException("cart has no restaurant");

        Restaurant restaurant = restaurantRepo.findById(cart.getRestaurantId());
        if (restaurant == null) throw new IllegalArgumentException("restaurant not found");

        if (!restaurant.serves(deliveryAddress.getPincode())) {
            throw new IllegalArgumentException("restaurant does not serve pincode: " + deliveryAddress.getPincode());
        }

        List<OrderLine> lines = cart.toOrderLines();
        if (!restaurant.canFulfill(lines)) throw new IllegalStateException("restaurant cannot fulfill right now");

        BigDecimal itemTotal = restaurant.calculateCost(lines);

        // Delivery fee
        DeliveryChargeStrategy deliveryStrategy = deliveryChargeFactory.create(surgeOn);
        BigDecimal deliveryFee = deliveryStrategy.getPrice(restaurant.getAddress(), deliveryAddress);

        // Coupon discount (on itemTotal only - keep simple)
        BigDecimal discount = BigDecimal.ZERO;
        Optional<Coupon> couponOpt = couponManager.find(couponCode);
        if (couponOpt.isPresent()) {
            Coupon c = couponOpt.get();
            if (c.isEligible(user, itemTotal)) discount = c.discountAmount(itemTotal);
        }

        // Tax (simple constant 5% on itemTotal)
        BigDecimal tax = itemTotal.multiply(new BigDecimal("0.05"));

        // Create order (itemTotal now; payable after bill calc)
        Order order = new Order(user.getId(), restaurant.getId(), deliveryAddress, lines, itemTotal);

        // Create bill (final payable)
        Bill bill = billingService.createBill(order.getId(), itemTotal, tax, discount, deliveryFee);
        order.setBillId(bill.getBillId());
        order.setPayableAmount(bill.getTotalAmountToBePaid());

        // Payment
        PaymentMode mode = user.getAccount().getPreferredPaymentMode();
        PaymentStatus payStatus = paymentService.process(user, bill.getTotalAmountToBePaid());
        order.markPayment(payStatus);

        // Record payment attempt
        paymentRecordService.record(bill.getBillId(), bill.getTotalAmountToBePaid(), payStatus, mode, couponCode);

        if (payStatus == PaymentStatus.FAILED) {
            orderRepo.save(order);
            notificationService.notifyUser(user, "Payment failed. Please try again. OrderId=" + order.getId());
            return order;
        }

        // Reserve capacity only after payment success (simplification)
        restaurant.allocateCapacity(lines);

        // Create delivery entity
        Delivery delivery = deliveryService.createForOrder(order.getId(), user, deliveryAddress);
        order.setDeliveryId(delivery.getId());

        // Assign driver
        Driver driver = driverStrategy.assign(driverRepo.findAll(), deliveryAddress.getLocation());
        if (driver != null && driver.isAvailable()) {
            order.assignDriver(driver.getId());
            driver.assignOrder(order.getId());
            deliveryService.assignDriver(order.getId(), driver.getId());
        }

        orderRepo.save(order);
        user.clearCart();

        notificationService.notifyUser(user, "Order placed. OrderId=" + order.getId() + ", BillId=" + bill.getBillId());
        return order;
    }

    // State advance + keep Delivery in sync
    public Order advanceOrder(String orderId) {
        Order order = orderRepo.findById(orderId);
        if (order == null) throw new IllegalArgumentException("order not found");

        String before = order.getStatus();
        order.advance();
        String after = order.getStatus();

        // Update delivery milestones
        if (!before.equals(after)) {
            if ("PICKED_UP".equals(after)) deliveryService.markPickedUp(orderId);
            if ("DELIVERED".equals(after)) deliveryService.markDelivered(orderId);
        }

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
    public Delivery trackDelivery(String orderId) { return deliveryService.track(orderId); }
    public Bill getBill(String orderId) { return billingService.getBillByOrder(orderId); }
}

// =========================
// Order History Service
// =========================
final class OrderHistoryService {
    private final OrderRepository orderRepo;
    public OrderHistoryService(OrderRepository orderRepo) { this.orderRepo = orderRepo; }

    // best: repo already indexed by userId
    public List<Order> getUserOrders(String userId) { return orderRepo.findByUserId(userId); }
    public List<Order> getUserOrdersByStatus(String userId, String status) { return orderRepo.findByUserIdAndStatus(userId, status); }
    public List<Order> getLastN(String userId, int n) { return orderRepo.findByUserId(userId, n); }
}

// =========================
// Demo
// =========================
public class FoodOrderingLLDDemo_Extended {
    public static void main(String[] args) {
        // Repos
        UserRepository userRepo = new InMemoryUserRepository();
        RestaurantRepository restaurantRepo = new InMemoryRestaurantRepository();
        DriverRepository driverRepo = new InMemoryDriverRepository();
        OrderRepository orderRepo = new InMemoryOrderRepository();

        BillRepository billRepo = new InMemoryBillRepository();
        PaymentRepository paymentRepo = new InMemoryPaymentRepository();
        DeliveryRepository deliveryRepo = new InMemoryDeliveryRepository();

        // Services
        UserService userService = new UserService(userRepo);
        RestaurantService restaurantService = new RestaurantService(restaurantRepo);

        PaymentService paymentService = new PaymentService(new PaymentMethodFactory());
        NotificationService notificationService = new NotificationService(new NotificationFactory(), "SMS");

        BillingService billingService = new BillingService(billRepo);
        PaymentRecordService paymentRecordService = new PaymentRecordService(paymentRepo);
        DeliveryService deliveryService = new DeliveryService(deliveryRepo);

        CouponManager couponManager = new CouponManager();
        DeliveryChargeStrategyFactory deliveryChargeFactory = new DeliveryChargeStrategyFactory();

        OrderService orderService = new OrderService(
                orderRepo, restaurantRepo, driverRepo,
                new NearestDriverAssignmentStrategy(),
                paymentService, notificationService,
                billingService, paymentRecordService, deliveryService,
                couponManager, deliveryChargeFactory
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

        // Setup drivers
        Driver d1 = new Driver("D1", "Driver-1", "dphone-1", new Location(17.44, 78.401));
        Driver d2 = new Driver("D2", "Driver-2", "dphone-2", new Location(17.40, 78.35));
        driverRepo.save(d1);
        driverRepo.save(d2);

        // Cart flow
        Cart cart = u1.getOrCreateCart();
        cart.selectRestaurant("R1");
        cart.addItem("M1", 3);
        cart.addItem("M2", 2);

        // Place order with coupon + surge pricing enabled
        Order order = orderService.placeOrder(u1, cart, home, "BANK10", true);
        System.out.println("Placed: " + order);
        System.out.println("Bill  : " + orderService.getBill(order.getId()));
        System.out.println("Deliv : " + orderService.trackDelivery(order.getId()));

        // Advance states
        System.out.println("Status: " + orderService.track(order.getId()).getStatus());
        orderService.advanceOrder(order.getId());
        System.out.println("Status: " + orderService.track(order.getId()).getStatus());
        orderService.advanceOrder(order.getId());
        System.out.println("Status: " + orderService.track(order.getId()).getStatus());
        System.out.println("Deliv : " + orderService.trackDelivery(order.getId()));
        orderService.advanceOrder(order.getId());
        System.out.println("Status: " + orderService.track(order.getId()).getStatus());
        System.out.println("Deliv : " + orderService.trackDelivery(order.getId()));

        // History (best way: by userId index)
        System.out.println("All orders of U1 => " + historyService.getUserOrders("U1"));
        System.out.println("Delivered orders => " + historyService.getUserOrdersByStatus("U1", "DELIVERED"));
        System.out.println("Last 1 order      => " + historyService.getLastN("U1", 1));
    }
}
