import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Amazon-like E-Commerce LLD (Interview-ready)
 *
 * Focus:
 * - Browse/Search catalog
 * - Cart
 * - Checkout -> Reserve inventory (HOLD with TTL)
 * - Payment
 * - On payment SUCCESS -> Commit reservation (inventory updated)
 * - Concurrency safe when only 1 quantity exists
 *
 * Patterns:
 * - Strategy: Shipping charge, Seller selection
 * - State: Order lifecycle
 * - Factory: Payment methods
 *
 * Inventory Concurrency:
 * - Model: Available = OnHand - Reserved (soft reservation)
 * - HOLD uses synchronized/CAS-like guarded updates
 * - Commit updates inventory only after successful payment
 */

public class AmazonEcommerceLLD {

    // =========================
    // Value Objects
    // =========================
    static final class Money {
        private final BigDecimal amount;
        public Money(String v) { this.amount = new BigDecimal(v); }
        public Money(BigDecimal v) { this.amount = v; }
        public BigDecimal value() { return amount; }
        public Money add(Money other) { return new Money(this.amount.add(other.amount)); }
        public Money multiply(int qty) { return new Money(this.amount.multiply(BigDecimal.valueOf(qty))); }
        @Override public String toString() { return amount.toPlainString(); }
    }

    static final class Address {
        private final String line1;
        private final String city;
        private final String pincode;
        public Address(String line1, String city, String pincode) {
            this.line1 = line1; this.city = city; this.pincode = pincode;
        }
        public String getPincode() { return pincode; }
        @Override public String toString() { return line1 + ", " + city + " - " + pincode; }
    }

    // =========================
    // Users
    // =========================
    static final class Customer {
        private final String id;
        private final String name;
        public Customer(String id, String name) { this.id = id; this.name = name; }
        public String getId() { return id; }
        public String getName() { return name; }
    }

    // =========================
    // Catalog
    // =========================
    static final class Product {
        private final String sku;
        private final String title;
        private final Money price;
        public Product(String sku, String title, Money price) {
            this.sku = sku; this.title = title; this.price = price;
        }
        public String getSku() { return sku; }
        public String getTitle() { return title; }
        public Money getPrice() { return price; }
        @Override public String toString() { return "Product{" + sku + ", " + title + ", price=" + price + "}"; }
    }

    static final class Seller {
        private final String id;
        private final String name;
        private final Set<String> servicePincodes = new HashSet<>();
        public Seller(String id, String name, Collection<String> pincodes) {
            this.id = id; this.name = name;
            if (pincodes != null) servicePincodes.addAll(pincodes);
        }
        public String getId() { return id; }
        public String getName() { return name; }
        public boolean serves(String pincode) { return servicePincodes.contains(pincode); }
    }

    static final class Offer { // product offered by seller
        private final String offerId;
        private final String sku;
        private final String sellerId;
        private final Money price; // (could differ per seller)
        public Offer(String offerId, String sku, String sellerId, Money price) {
            this.offerId = offerId; this.sku = sku; this.sellerId = sellerId; this.price = price;
        }
        public String getOfferId() { return offerId; }
        public String getSku() { return sku; }
        public String getSellerId() { return sellerId; }
        public Money getPrice() { return price; }
    }

    // =========================
    // Cart
    // =========================
    static final class CartItem {
        private final String sku;
        private final int qty;
        public CartItem(String sku, int qty) {
            if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
            this.sku = sku; this.qty = qty;
        }
        public String getSku() { return sku; }
        public int getQty() { return qty; }
    }

    static final class Cart {
        private final String customerId;
        private final Map<String, Integer> skuToQty = new LinkedHashMap<>();
        public Cart(String customerId) { this.customerId = customerId; }
        public void add(String sku, int qty) {
            if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
            skuToQty.put(sku, skuToQty.getOrDefault(sku, 0) + qty);
        }
        public List<CartItem> items() {
            List<CartItem> res = new ArrayList<>();
            for (Map.Entry<String, Integer> e : skuToQty.entrySet()) res.add(new CartItem(e.getKey(), e.getValue()));
            return res;
        }
        public boolean isEmpty() { return skuToQty.isEmpty(); }
        public void clear() { skuToQty.clear(); }
        public String getCustomerId() { return customerId; }
    }

    // =========================
    // Order + State
    // =========================
    enum PaymentStatus { SUCCESS, FAILED }
    enum OrderStatus { CREATED, PAYMENT_SUCCESS, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }

    interface OrderState {
        OrderState next();
        OrderStatus status();
    }
    static final class CreatedState implements OrderState {
        public OrderState next() { return new PaymentSuccessState(); }
        public OrderStatus status() { return OrderStatus.CREATED; }
    }
    static final class PaymentSuccessState implements OrderState {
        public OrderState next() { return new ConfirmedState(); }
        public OrderStatus status() { return OrderStatus.PAYMENT_SUCCESS; }
    }
    static final class ConfirmedState implements OrderState {
        public OrderState next() { return new ShippedState(); }
        public OrderStatus status() { return OrderStatus.CONFIRMED; }
    }
    static final class ShippedState implements OrderState {
        public OrderState next() { return new DeliveredState(); }
        public OrderStatus status() { return OrderStatus.SHIPPED; }
    }
    static final class DeliveredState implements OrderState {
        public OrderState next() { return this; }
        public OrderStatus status() { return OrderStatus.DELIVERED; }
    }
    static final class CancelledState implements OrderState {
        public OrderState next() { return this; }
        public OrderStatus status() { return OrderStatus.CANCELLED; }
    }

    static final class OrderLine {
        private final String sku;
        private final int qty;
        private final Money unitPrice;
        private final String sellerId;
        public OrderLine(String sku, int qty, Money unitPrice, String sellerId) {
            this.sku = sku; this.qty = qty; this.unitPrice = unitPrice; this.sellerId = sellerId;
        }
        public String getSku() { return sku; }
        public int getQty() { return qty; }
        public Money lineTotal() { return unitPrice.multiply(qty); }
        public String getSellerId() { return sellerId; }
    }

    static final class Order {
        private static final AtomicInteger COUNTER = new AtomicInteger(0);

        private final String orderId = "O" + COUNTER.incrementAndGet();
        private final String customerId;
        private final Address shipTo;
        private final List<OrderLine> lines;
        private final Money itemsTotal;
        private Money shippingFee = new Money("0");
        private Money payable = new Money("0");

        private OrderState state = new CreatedState();
        private PaymentStatus paymentStatus;

        // inventory reservations used for this order
        private final List<String> reservationIds = new ArrayList<>();

        public Order(String customerId, Address shipTo, List<OrderLine> lines, Money itemsTotal) {
            this.customerId = customerId;
            this.shipTo = shipTo;
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
            this.itemsTotal = itemsTotal;
        }

        public String getOrderId() { return orderId; }
        public String getCustomerId() { return customerId; }
        public Address getShipTo() { return shipTo; }
        public List<OrderLine> getLines() { return lines; }
        public Money getItemsTotal() { return itemsTotal; }

        public void setShippingFee(Money fee) { this.shippingFee = fee; }
        public void setPayable(Money payable) { this.payable = payable; }
        public Money getPayable() { return payable; }

        public OrderStatus getStatus() { return state.status(); }
        public void markPayment(PaymentStatus ps) { this.paymentStatus = ps; }
        public PaymentStatus getPaymentStatus() { return paymentStatus; }

        public void advance() { this.state = state.next(); }
        public void cancel() { this.state = new CancelledState(); }

        public void addReservation(String resId) { reservationIds.add(resId); }
        public List<String> getReservationIds() { return reservationIds; }

        @Override public String toString() {
            return "Order{" + orderId +
                    ", status=" + getStatus() +
                    ", payment=" + paymentStatus +
                    ", itemsTotal=" + itemsTotal +
                    ", shippingFee=" + shippingFee +
                    ", payable=" + payable +
                    ", reservations=" + reservationIds +
                    '}';
        }
    }

    // =========================
    // Strategies
    // =========================
    interface ShippingChargeStrategy {
        Money fee(Address shipTo, Money itemsTotal);
    }

    static final class DefaultShippingStrategy implements ShippingChargeStrategy {
        public Money fee(Address shipTo, Money itemsTotal) {
            // Simple rule: free shipping above 500, else 40
            return itemsTotal.value().compareTo(new BigDecimal("500")) >= 0 ? new Money("0") : new Money("40");
        }
    }

    interface SellerSelectionStrategy {
        Offer pickBestOffer(String sku, Address shipTo, List<Offer> offers, Map<String, Seller> sellers);
    }

    static final class FirstEligibleSellerStrategy implements SellerSelectionStrategy {
        public Offer pickBestOffer(String sku, Address shipTo, List<Offer> offers, Map<String, Seller> sellers) {
            for (Offer o : offers) {
                Seller s = sellers.get(o.getSellerId());
                if (s != null && s.serves(shipTo.getPincode())) return o;
            }
            return null;
        }
    }

    // =========================
    // Payment (Factory)
    // =========================
    enum PaymentMode { CARD, UPI, COD }

    interface PaymentMethod {
        PaymentStatus pay(Customer c, Money amount);
    }

    static final class CardPayment implements PaymentMethod {
        public PaymentStatus pay(Customer c, Money amount) { return PaymentStatus.SUCCESS; }
    }
    static final class UpiPayment implements PaymentMethod {
        public PaymentStatus pay(Customer c, Money amount) { return PaymentStatus.SUCCESS; }
    }
    static final class CodPayment implements PaymentMethod {
        public PaymentStatus pay(Customer c, Money amount) { return PaymentStatus.SUCCESS; }
    }

    static final class PaymentMethodFactory {
        public PaymentMethod create(PaymentMode mode) {
            switch (mode) {
                case CARD: return new CardPayment();
                case UPI:  return new UpiPayment();
                case COD:  return new CodPayment();
                default: throw new IllegalArgumentException("Unsupported: " + mode);
            }
        }
    }

    static final class PaymentService {
        private final PaymentMethodFactory factory = new PaymentMethodFactory();
        public PaymentStatus process(Customer c, PaymentMode mode, Money amount) {
            return factory.create(mode).pay(c, amount);
        }
    }

    // =========================
    // Inventory (Concurrency-safe)
    // =========================
    enum ReservationStatus { HELD, COMMITTED, RELEASED, EXPIRED }

    static final class Reservation {
        private final String reservationId;
        private final String orderId;
        private final String sku;
        private final int qty;
        private final Instant expiresAt;
        private ReservationStatus status = ReservationStatus.HELD;

        public Reservation(String reservationId, String orderId, String sku, int qty, Instant expiresAt) {
            this.reservationId = reservationId;
            this.orderId = orderId;
            this.sku = sku;
            this.qty = qty;
            this.expiresAt = expiresAt;
        }
        public String getReservationId() { return reservationId; }
        public String getOrderId() { return orderId; }
        public String getSku() { return sku; }
        public int getQty() { return qty; }
        public Instant getExpiresAt() { return expiresAt; }
        public ReservationStatus getStatus() { return status; }
        public void setStatus(ReservationStatus st) { this.status = st; }
        public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }

    static final class InventoryRecord {
        private final AtomicInteger onHand = new AtomicInteger(0);
        private final AtomicInteger reserved = new AtomicInteger(0);

        // used to make commit/release on same SKU atomic for both counters
        private final Object lock = new Object();

        public int onHand() { return onHand.get(); }
        public int reserved() { return reserved.get(); }
        public int available() { return onHand.get() - reserved.get(); }

        public void addStock(int delta) {
            if (delta <= 0) throw new IllegalArgumentException("delta must be > 0");
            onHand.addAndGet(delta);
        }

        // Reserve: Available = OnHand - Reserved
        // If enough, increment Reserved. (synchronized to keep simple & correct)
        public boolean tryHold(int qty) {
            if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
            synchronized (lock) {
                if (available() < qty) return false;
                reserved.addAndGet(qty);
                return true;
            }
        }

        // Commit: On payment success -> decrement OnHand and Reserved by qty
        public boolean commit(int qty) {
            if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
            synchronized (lock) {
                // At commit time, hold should guarantee this, but keep safety
                if (reserved.get() < qty) return false;
                if (onHand.get() < qty) return false;

                onHand.addAndGet(-qty);
                reserved.addAndGet(-qty);
                return true;
            }
        }

        // Release: On payment failure/cancel/expiry -> decrement Reserved
        public boolean release(int qty) {
            if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
            synchronized (lock) {
                if (reserved.get() < qty) return false;
                reserved.addAndGet(-qty);
                return true;
            }
        }
    }

    static final class InventoryService {
        private final Map<String, InventoryRecord> skuInventory = new ConcurrentHashMap<>();
        private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
        private final AtomicInteger resCounter = new AtomicInteger(0);

        public void addStock(String sku, int qty) {
            skuInventory.computeIfAbsent(sku, k -> new InventoryRecord()).addStock(qty);
        }

        public int available(String sku) {
            InventoryRecord r = skuInventory.get(sku);
            return r == null ? 0 : r.available();
        }

        // HOLD inventory for this order + sku. TTL prevents infinite hoarding.
        public String hold(String orderId, String sku, int qty, Duration ttl) {
            InventoryRecord r = skuInventory.computeIfAbsent(sku, k -> new InventoryRecord());
            boolean ok = r.tryHold(qty);
            if (!ok) return null;

            String resId = "R" + resCounter.incrementAndGet();
            Reservation res = new Reservation(resId, orderId, sku, qty, Instant.now().plus(ttl));
            reservations.put(resId, res);
            return resId;
        }

        public boolean commitReservation(String reservationId) {
            Reservation res = reservations.get(reservationId);
            if (res == null) return false;

            if (res.getStatus() != ReservationStatus.HELD) return (res.getStatus() == ReservationStatus.COMMITTED);

            if (res.isExpired()) {
                // expired -> release hold
                releaseReservation(reservationId);
                return false;
            }

            InventoryRecord r = skuInventory.get(res.getSku());
            if (r == null) return false;

            boolean ok = r.commit(res.getQty());
            if (ok) res.setStatus(ReservationStatus.COMMITTED);
            return ok;
        }

        public boolean releaseReservation(String reservationId) {
            Reservation res = reservations.get(reservationId);
            if (res == null) return false;

            if (res.getStatus() == ReservationStatus.RELEASED) return true;
            if (res.getStatus() == ReservationStatus.COMMITTED) return false; // can't release committed

            InventoryRecord r = skuInventory.get(res.getSku());
            if (r == null) return false;

            boolean ok = r.release(res.getQty());
            if (ok) res.setStatus(res.isExpired() ? ReservationStatus.EXPIRED : ReservationStatus.RELEASED);
            return ok;
        }
    }

    // =========================
    // Repositories (In-memory)
    // =========================
    static final class CatalogRepository {
        private final Map<String, Product> products = new ConcurrentHashMap<>();
        private final Map<String, List<Offer>> offersBySku = new ConcurrentHashMap<>();

        public void saveProduct(Product p) { products.put(p.getSku(), p); }
        public Product getProduct(String sku) { return products.get(sku); }
        public List<Product> search(String query) {
            List<Product> res = new ArrayList<>();
            for (Product p : products.values()) {
                if (p.getTitle().toLowerCase().contains(query.toLowerCase())) res.add(p);
            }
            return res;
        }
        public void addOffer(Offer o) { offersBySku.computeIfAbsent(o.getSku(), k -> new ArrayList<>()).add(o); }
        public List<Offer> offersFor(String sku) { return offersBySku.getOrDefault(sku, Collections.emptyList()); }
    }

    static final class SellerRepository {
        private final Map<String, Seller> sellers = new ConcurrentHashMap<>();
        public void save(Seller s) { sellers.put(s.getId(), s); }
        public Seller get(String id) { return sellers.get(id); }
        public Map<String, Seller> all() { return sellers; }
    }

    static final class OrderRepository {
        private final Map<String, Order> orders = new ConcurrentHashMap<>();
        private final Map<String, List<String>> byCustomer = new ConcurrentHashMap<>();
        public void save(Order o) {
            orders.put(o.getOrderId(), o);
            byCustomer.computeIfAbsent(o.getCustomerId(), k -> new ArrayList<>()).add(o.getOrderId());
        }
        public Order get(String orderId) { return orders.get(orderId); }
        public List<Order> ordersOf(String customerId) {
            List<String> ids = byCustomer.getOrDefault(customerId, Collections.emptyList());
            List<Order> res = new ArrayList<>();
            for (String id : ids) res.add(orders.get(id));
            return res;
        }
    }

    // =========================
    // Services
    // =========================
    static final class CatalogService {
        private final CatalogRepository repo;
        public CatalogService(CatalogRepository repo) { this.repo = repo; }
        public List<Product> search(String query) { return repo.search(query); }
        public Product get(String sku) { return repo.getProduct(sku); }
        public List<Offer> offers(String sku) { return repo.offersFor(sku); }
    }

    static final class CheckoutService {
        private final CatalogService catalog;
        private final SellerRepository sellerRepo;
        private final InventoryService inventory;
        private final PaymentService payment;
        private final OrderRepository orderRepo;

        private final ShippingChargeStrategy shippingStrategy;
        private final SellerSelectionStrategy sellerStrategy;

        public CheckoutService(CatalogService catalog,
                               SellerRepository sellerRepo,
                               InventoryService inventory,
                               PaymentService payment,
                               OrderRepository orderRepo,
                               ShippingChargeStrategy shippingStrategy,
                               SellerSelectionStrategy sellerStrategy) {
            this.catalog = catalog;
            this.sellerRepo = sellerRepo;
            this.inventory = inventory;
            this.payment = payment;
            this.orderRepo = orderRepo;
            this.shippingStrategy = shippingStrategy;
            this.sellerStrategy = sellerStrategy;
        }

        /*
         * Checkout flow:
         * 1) Validate cart
         * 2) Choose seller offer per sku
         * 3) HOLD inventory (TTL)
         * 4) Create order
         * 5) Process payment
         * 6) If success -> COMMIT reservations (inventory updated here)
         * 7) If fail -> RELEASE reservations
         */
        public Order checkout(Customer c, Cart cart, Address shipTo, PaymentMode mode) {
            if (cart == null || cart.isEmpty()) throw new IllegalArgumentException("Cart is empty");

            // Build order lines + holds
            List<OrderLine> lines = new ArrayList<>();
            Money itemsTotal = new Money("0");

            // Step-1: pick best offer per SKU
            for (CartItem ci : cart.items()) {
                Product p = catalog.get(ci.getSku());
                if (p == null) throw new IllegalArgumentException("SKU not found: " + ci.getSku());

                Offer best = sellerStrategy.pickBestOffer(ci.getSku(), shipTo, catalog.offers(ci.getSku()), sellerRepo.all());
                if (best == null) throw new IllegalStateException("No seller can serve pincode for sku=" + ci.getSku());

                OrderLine ol = new OrderLine(ci.getSku(), ci.getQty(), best.getPrice(), best.getSellerId());
                lines.add(ol);
                itemsTotal = itemsTotal.add(ol.lineTotal());
            }

            Order order = new Order(c.getId(), shipTo, lines, itemsTotal);

            // Step-2: HOLD inventory for each sku (TTL 2 minutes)
            List<String> held = new ArrayList<>();
            try {
                for (OrderLine l : lines) {
                    String resId = inventory.hold(order.getOrderId(), l.getSku(), l.getQty(), Duration.ofMinutes(2));
                    if (resId == null) throw new IllegalStateException("Out of stock for sku=" + l.getSku());
                    order.addReservation(resId);
                    held.add(resId);
                }
            } catch (RuntimeException e) {
                // Release partial holds
                for (String resId : held) inventory.releaseReservation(resId);
                order.cancel();
                order.markPayment(PaymentStatus.FAILED);
                orderRepo.save(order);
                return order;
            }

            // Step-3: shipping fee + payable
            Money shippingFee = shippingStrategy.fee(shipTo, itemsTotal);
            order.setShippingFee(shippingFee);
            order.setPayable(new Money(itemsTotal.value().add(shippingFee.value())));

            // Save created order (optional)
            orderRepo.save(order);

            // Step-4: payment
            PaymentStatus ps = payment.process(c, mode, order.getPayable());
            order.markPayment(ps);

            if (ps == PaymentStatus.FAILED) {
                // Release holds on payment failure
                for (String resId : order.getReservationIds()) inventory.releaseReservation(resId);
                order.cancel();
                orderRepo.save(order);
                return order;
            }

            // Payment SUCCESS
            order.advance(); // CREATED -> PAYMENT_SUCCESS

            // Step-5: COMMIT reservations -> inventory updated here
            boolean allCommitted = true;
            for (String resId : order.getReservationIds()) {
                if (!inventory.commitReservation(resId)) { allCommitted = false; break; }
            }

            if (!allCommitted) {
                // Rare safety: if commit fails (expiry / race / etc), release remaining holds and cancel.
                for (String resId : order.getReservationIds()) inventory.releaseReservation(resId);
                order.cancel();
                orderRepo.save(order);
                return order;
            }

            // Step-6: Order confirmed
            order.advance(); // PAYMENT_SUCCESS -> CONFIRMED
            orderRepo.save(order);

            // Clear cart
            cart.clear();
            return order;
        }
    }

    // =========================
    // Demo: Concurrency test for "1 qty left"
    // =========================
    public static void main(String[] args) throws Exception {
        // Setup
        CatalogRepository catalogRepo = new CatalogRepository();
        SellerRepository sellerRepo = new SellerRepository();
        OrderRepository orderRepo = new OrderRepository();

        CatalogService catalogService = new CatalogService(catalogRepo);
        InventoryService inventoryService = new InventoryService();
        PaymentService paymentService = new PaymentService();

        CheckoutService checkout = new CheckoutService(
                catalogService,
                sellerRepo,
                inventoryService,
                paymentService,
                orderRepo,
                new DefaultShippingStrategy(),
                new FirstEligibleSellerStrategy()
        );

        // Data
        Product p1 = new Product("SKU-IPHONE", "iPhone 16 Pro", new Money("999"));
        catalogRepo.saveProduct(p1);

        Seller s1 = new Seller("S1", "CloudRetail", Arrays.asList("500081", "500032"));
        sellerRepo.save(s1);

        // Offer (seller-specific pricing)
        catalogRepo.addOffer(new Offer("OFFER1", "SKU-IPHONE", "S1", new Money("999")));

        // Inventory: ONLY 1 qty available
        inventoryService.addStock("SKU-IPHONE", 1);
        System.out.println("Initial available = " + inventoryService.available("SKU-IPHONE"));

        // Two customers try to buy same SKU concurrently
        Customer c1 = new Customer("C1", "Alice");
        Customer c2 = new Customer("C2", "Bob");
        Address addr = new Address("Street 1", "Hyderabad", "500081");

        Cart cart1 = new Cart(c1.getId());
        cart1.add("SKU-IPHONE", 1);

        Cart cart2 = new Cart(c2.getId());
        cart2.add("SKU-IPHONE", 1);

        ExecutorService es = Executors.newFixedThreadPool(2);

        Future<Order> f1 = es.submit(() -> checkout.checkout(c1, cart1, addr, PaymentMode.UPI));
        Future<Order> f2 = es.submit(() -> checkout.checkout(c2, cart2, addr, PaymentMode.UPI));

        Order o1 = f1.get();
        Order o2 = f2.get();

        es.shutdown();

        System.out.println("Order1 => " + o1);
        System.out.println("Order2 => " + o2);
        System.out.println("Final available = " + inventoryService.available("SKU-IPHONE"));

        // Expectation:
        // - Exactly 1 order becomes CONFIRMED
        // - Other gets CANCELLED due to out-of-stock during HOLD
    }
}
