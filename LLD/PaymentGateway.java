// ─────────────────────────────────────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────────────────────────────────────
enum PaymentMethod { CREDIT_CARD, DEBIT_CARD, UPI, NET_BANKING, WALLET }

enum PaymentStatus { CREATED, PROCESSING, SUCCESS, FAILED }

// ─────────────────────────────────────────────────────────────────────────────
// Client
// ─────────────────────────────────────────────────────────────────────────────
final class Client {
    private final String name;
    private final Set<PaymentMethod> supportedMethods = EnumSet.noneOf(PaymentMethod.class);

    Client(String name) { this.name = Objects.requireNonNull(name, "name"); }

    String getName() { return name; }

    boolean supports(PaymentMethod m) { return supportedMethods.contains(m); }

    void addMethod(PaymentMethod m) { supportedMethods.add(Objects.requireNonNull(m, "method")); }

    void removeMethod(PaymentMethod m) { supportedMethods.remove(m); }

    Set<PaymentMethod> getMethods() { return Collections.unmodifiableSet(supportedMethods); }
}

// ─────────────────────────────────────────────────────────────────────────────
// PaymentGateway (ONLY holds clients + their supported methods)  (same as your code)
// ─────────────────────────────────────────────────────────────────────────────
final class PaymentGateway {
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    void addClient(Client c) {
        Objects.requireNonNull(c, "client");
        Client prev = clients.putIfAbsent(c.getName(), c);
        if (prev != null) throw new IllegalArgumentException("Client exists: " + c.getName());
    }

    void removeClient(String name) { clients.remove(name); }

    Client getClient(String name) {
        Client c = clients.get(name);
        if (c == null) throw new NoSuchElementException("Client not found: " + name);
        return c;
    }

    List<PaymentMethod> listSupportedMethods(String clientName) {
        return new ArrayList<>(getClient(clientName).getMethods());
    }

    void addSupport(String clientName, PaymentMethod m) { getClient(clientName).addMethod(m); }

    void removeSupport(String clientName, PaymentMethod m) { getClient(clientName).removeMethod(m); }
}

// ─────────────────────────────────────────────────────────────────────────────
// Payment Details (typed request payload per method)
// ─────────────────────────────────────────────────────────────────────────────
sealed interface PaymentDetails permits CardDetails, UpiDetails, NetBankingDetails, WalletDetails {}

final class CardDetails implements PaymentDetails {
    final String number, cvv, expiry, holder;
    CardDetails(String number, String cvv, String expiry, String holder) {
        this.number = Objects.requireNonNull(number);
        this.cvv = Objects.requireNonNull(cvv);
        this.expiry = Objects.requireNonNull(expiry);
        this.holder = Objects.requireNonNull(holder);
    }
}

final class UpiDetails implements PaymentDetails {
    final String upiId;
    UpiDetails(String upiId) { this.upiId = Objects.requireNonNull(upiId); }
}

final class NetBankingDetails implements PaymentDetails {
    final String username, password;
    NetBankingDetails(String username, String password) {
        this.username = Objects.requireNonNull(username);
        this.password = Objects.requireNonNull(password);
    }
}

final class WalletDetails implements PaymentDetails {
    final String walletId;
    WalletDetails(String walletId) { this.walletId = Objects.requireNonNull(walletId); }
}

// ─────────────────────────────────────────────────────────────────────────────
// API DTOs
// ─────────────────────────────────────────────────────────────────────────────
final class PaymentRequest {
    final String client;
    final PaymentMethod method;
    final PaymentDetails details;
    final double amount;
    final String currency;
    final String idempotencyKey; // REQUIRED for "no double charge" guarantee

    PaymentRequest(String client, PaymentMethod method, PaymentDetails details,
                   double amount, String currency, String idempotencyKey) {
        this.client = Objects.requireNonNull(client, "client");
        this.method = Objects.requireNonNull(method, "method");
        this.details = Objects.requireNonNull(details, "details");
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        this.amount = amount;
        this.currency = (currency == null || currency.isBlank()) ? "INR" : currency;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }
}

final class PaymentResponse {
    final String paymentId;
    final PaymentStatus status;

    PaymentResponse(String paymentId, PaymentStatus status) {
        this.paymentId = paymentId;
        this.status = status;
    }

    @Override public String toString() {
        return "PaymentResponse[paymentId=" + paymentId + ", status=" + status + "]";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Payment (Transaction) + Repository
// ─────────────────────────────────────────────────────────────────────────────
final class Payment {
    private final String id;
    private final String client;
    private final PaymentMethod method;
    private final double amount;
    private final String currency;
    private final String idempotencyKey;

    private volatile PaymentStatus status = PaymentStatus.CREATED;
    private volatile String providerUsed;
    private volatile String providerRef;
    private volatile String failureReason;

    private final List<String> attempts = new ArrayList<>();

    Payment(String client, PaymentMethod method, double amount, String currency, String idempotencyKey) {
        this.id = UUID.randomUUID().toString();
        this.client = client;
        this.method = method;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
    }

    String getId() { return id; }
    PaymentStatus getStatus() { return status; }
    String getIdempotencyKey() { return idempotencyKey; }

    synchronized void markProcessing(String provider) {
        this.status = PaymentStatus.PROCESSING;
        this.providerUsed = provider;
    }

    synchronized void markSuccess(String provider, String providerRef) {
        this.status = PaymentStatus.SUCCESS;
        this.providerUsed = provider;
        this.providerRef = providerRef;
        this.failureReason = null;
    }

    synchronized void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    synchronized void addAttempt(String s) { attempts.add(s); }

    @Override public String toString() {
        return "Payment[id=" + id +
               ", client=" + client +
               ", method=" + method +
               ", amount=" + amount + " " + currency +
               ", status=" + status +
               ", provider=" + (providerUsed == null ? "-" : providerUsed) +
               ", providerRef=" + (providerRef == null ? "-" : providerRef) +
               ", reason=" + (failureReason == null ? "-" : failureReason) +
               ", attempts=" + attempts + "]";
    }
}

final class PaymentRepository {
    private final Map<String, Payment> byId = new ConcurrentHashMap<>();
    // composite key = client|idempotencyKey -> paymentId
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    Payment findById(String id) {
        Payment p = byId.get(id);
        if (p == null) throw new NoSuchElementException("Payment not found: " + id);
        return p;
    }

    Payment findByIdempotency(String client, String idemKey) {
        String k = composite(client, idemKey);
        String id = idempotencyIndex.get(k);
        return (id == null) ? null : byId.get(id);
    }

    // Ensures idempotency: same client+key returns same Payment object
    Payment createIfAbsent(String client, PaymentMethod method, double amount, String currency, String idemKey) {
        String k = composite(client, idemKey);

        String existingId = idempotencyIndex.get(k);
        if (existingId != null) return byId.get(existingId);

        synchronized (this) {
            existingId = idempotencyIndex.get(k);
            if (existingId != null) return byId.get(existingId);

            Payment p = new Payment(client, method, amount, currency, idemKey);
            byId.put(p.getId(), p);
            idempotencyIndex.put(k, p.getId());
            return p;
        }
    }

    private static String composite(String client, String idemKey) { return client + "|" + idemKey; }
}

// ─────────────────────────────────────────────────────────────────────────────
// Provider abstraction + failures
// ─────────────────────────────────────────────────────────────────────────────
final class ProviderException extends RuntimeException {
    ProviderException(String msg) { super(msg); }
}

final class ProviderChargeResult {
    final String providerRef;
    ProviderChargeResult(String providerRef) { this.providerRef = providerRef; }
}

interface PaymentProvider {
    String name();
    boolean supports(PaymentMethod method);
    ProviderChargeResult charge(Payment payment, PaymentRequest request);
}

// Mock Providers (simulate success/failure)
final class StripeProvider implements PaymentProvider {
    private final Random rnd;
    StripeProvider(Random rnd) { this.rnd = Objects.requireNonNull(rnd); }

    public String name() { return "Stripe"; }

    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CREDIT_CARD || method == PaymentMethod.DEBIT_CARD ||
               method == PaymentMethod.UPI || method == PaymentMethod.WALLET;
    }

    public ProviderChargeResult charge(Payment payment, PaymentRequest request) {
        // Simulate occasional provider failure
        if (rnd.nextInt(100) < 20) throw new ProviderException("Stripe provider error");
        System.out.println("Stripe charged: " + request.amount + " " + request.currency + " via " + request.method);
        return new ProviderChargeResult("st_" + UUID.randomUUID().toString().substring(0, 8));
    }
}

final class RazorpayProvider implements PaymentProvider {
    private final Random rnd;
    RazorpayProvider(Random rnd) { this.rnd = Objects.requireNonNull(rnd); }

    public String name() { return "Razorpay"; }

    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.UPI || method == PaymentMethod.NET_BANKING ||
               method == PaymentMethod.CREDIT_CARD || method == PaymentMethod.DEBIT_CARD;
    }

    public ProviderChargeResult charge(Payment payment, PaymentRequest request) {
        if (rnd.nextInt(100) < 25) throw new ProviderException("Razorpay provider timeout");
        System.out.println("Razorpay charged: " + request.amount + " " + request.currency + " via " + request.method);
        return new ProviderChargeResult("rzp_" + UUID.randomUUID().toString().substring(0, 8));
    }
}

final class PaypalProvider implements PaymentProvider {
    private final Random rnd;
    PaypalProvider(Random rnd) { this.rnd = Objects.requireNonNull(rnd); }

    public String name() { return "PayPal"; }

    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.WALLET || method == PaymentMethod.CREDIT_CARD || method == PaymentMethod.DEBIT_CARD;
    }

    public ProviderChargeResult charge(Payment payment, PaymentRequest request) {
        if (rnd.nextInt(100) < 30) throw new ProviderException("PayPal rejected transaction");
        System.out.println("PayPal charged: " + request.amount + " " + request.currency + " via " + request.method);
        return new ProviderChargeResult("pp_" + UUID.randomUUID().toString().substring(0, 8));
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Provider Registry + Router (route + failover)
// ─────────────────────────────────────────────────────────────────────────────
final class ProviderRegistry {
    private final List<PaymentProvider> providers = new ArrayList<>();

    void register(PaymentProvider p) { providers.add(Objects.requireNonNull(p)); }

    List<PaymentProvider> all() { return Collections.unmodifiableList(providers); }
}

// simple circuit breaker-ish health tracking (kept interview-simple)
final class ProviderHealth {
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final int failureThreshold;

    ProviderHealth(int failureThreshold) { this.failureThreshold = Math.max(1, failureThreshold); }

    boolean isHealthy(String provider) {
        return consecutiveFailures.getOrDefault(provider, 0) < failureThreshold;
    }

    void recordSuccess(String provider) { consecutiveFailures.put(provider, 0); }

    void recordFailure(String provider) {
        consecutiveFailures.merge(provider, 1, Integer::sum);
    }
}

interface ProviderRouter {
    List<PaymentProvider> routeCandidates(PaymentMethod method);
}

// round-robin per method + skip unhealthy providers
final class RoundRobinRouter implements ProviderRouter {
    private final ProviderRegistry registry;
    private final ProviderHealth health;
    private final Map<PaymentMethod, AtomicInteger> rr = new EnumMap<>(PaymentMethod.class);

    RoundRobinRouter(ProviderRegistry registry, ProviderHealth health) {
        this.registry = Objects.requireNonNull(registry);
        this.health = Objects.requireNonNull(health);
        for (PaymentMethod m : PaymentMethod.values()) rr.put(m, new AtomicInteger(0));
    }

    @Override
    public List<PaymentProvider> routeCandidates(PaymentMethod method) {
        List<PaymentProvider> supported = new ArrayList<>();
        for (PaymentProvider p : registry.all()) {
            if (p.supports(method) && health.isHealthy(p.name())) supported.add(p);
        }
        if (supported.isEmpty()) {
            // If all unhealthy, allow fallback to any provider that supports method
            for (PaymentProvider p : registry.all()) if (p.supports(method)) supported.add(p);
        }
        if (supported.isEmpty()) return List.of();

        // rotate list based on RR pointer
        int start = Math.floorMod(rr.get(method).getAndIncrement(), supported.size());
        List<PaymentProvider> ordered = new ArrayList<>(supported.size());
        ordered.addAll(supported.subList(start, supported.size()));
        ordered.addAll(supported.subList(0, start));
        return ordered;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Payment method validation (Strategy Pattern stays, just expanded)
// ─────────────────────────────────────────────────────────────────────────────
interface PaymentService {
    void validate(PaymentRequest req);
}

final class CardPaymentService implements PaymentService {
    private final PaymentMethod cardType;
    CardPaymentService(PaymentMethod cardType) { this.cardType = cardType; }

    public void validate(PaymentRequest req) {
        if (req.method != cardType) throw new IllegalArgumentException("Wrong service for " + req.method);
        if (!(req.details instanceof CardDetails)) throw new IllegalArgumentException(cardType + " expects CardDetails");
    }
}

final class UpiPaymentService implements PaymentService {
    public void validate(PaymentRequest req) {
        if (req.method != PaymentMethod.UPI) throw new IllegalArgumentException("Wrong service for " + req.method);
        if (!(req.details instanceof UpiDetails)) throw new IllegalArgumentException("UPI expects UpiDetails");
    }
}

final class NetBankingPaymentService implements PaymentService {
    public void validate(PaymentRequest req) {
        if (req.method != PaymentMethod.NET_BANKING) throw new IllegalArgumentException("Wrong service for " + req.method);
        if (!(req.details instanceof NetBankingDetails)) throw new IllegalArgumentException("NET_BANKING expects NetBankingDetails");
    }
}

final class WalletPaymentService implements PaymentService {
    public void validate(PaymentRequest req) {
        if (req.method != PaymentMethod.WALLET) throw new IllegalArgumentException("Wrong service for " + req.method);
        if (!(req.details instanceof WalletDetails)) throw new IllegalArgumentException("WALLET expects WalletDetails");
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Core Orchestrator (TransactionService -> now routes to providers + idempotency)
// ─────────────────────────────────────────────────────────────────────────────
final class PaymentOrchestrator {
    private final PaymentGateway gateway;
    private final PaymentRepository repo;
    private final Map<PaymentMethod, PaymentService> methodValidators;
    private final ProviderRouter router;
    private final ProviderHealth health;

    PaymentOrchestrator(PaymentGateway gateway,
                        PaymentRepository repo,
                        Map<PaymentMethod, PaymentService> validators,
                        ProviderRouter router,
                        ProviderHealth health) {
        this.gateway = Objects.requireNonNull(gateway);
        this.repo = Objects.requireNonNull(repo);
        this.methodValidators = new EnumMap<>(PaymentMethod.class);
        if (validators != null) this.methodValidators.putAll(validators);
        this.router = Objects.requireNonNull(router);
        this.health = Objects.requireNonNull(health);
    }

    // API: Initiate a payment (idempotent)
    PaymentResponse initiatePayment(PaymentRequest req) {
        Objects.requireNonNull(req, "request");

        Client c = gateway.getClient(req.client);
        if (!c.supports(req.method)) throw new IllegalArgumentException(req.method + " not supported for " + req.client);

        PaymentService validator = methodValidators.get(req.method);
        if (validator == null) throw new IllegalStateException("No validator registered for " + req.method);
        validator.validate(req);

        // Idempotency: same key returns same payment + does NOT re-charge
        Payment existing = repo.findByIdempotency(req.client, req.idempotencyKey);
        if (existing != null) return new PaymentResponse(existing.getId(), existing.getStatus());

        Payment payment = repo.createIfAbsent(req.client, req.method, req.amount, req.currency, req.idempotencyKey);

        // If already completed (race), just return
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED) {
            return new PaymentResponse(payment.getId(), payment.getStatus());
        }

        // Route + failover
        List<PaymentProvider> candidates = router.routeCandidates(req.method);
        if (candidates.isEmpty()) {
            payment.markFailed("No providers available for " + req.method);
            return new PaymentResponse(payment.getId(), payment.getStatus());
        }

        String lastErr = null;
        for (PaymentProvider p : candidates) {
            try {
                payment.markProcessing(p.name());
                payment.addAttempt("TRY " + p.name());

                ProviderChargeResult res = p.charge(payment, req);

                health.recordSuccess(p.name());
                payment.addAttempt("OK " + p.name());
                payment.markSuccess(p.name(), res.providerRef);

                return new PaymentResponse(payment.getId(), payment.getStatus());
            } catch (ProviderException ex) {
                health.recordFailure(p.name());
                lastErr = p.name() + ": " + ex.getMessage();
                payment.addAttempt("FAIL " + lastErr);
                // continue to next provider
            } catch (RuntimeException ex) {
                // treat as provider failure for demo
                health.recordFailure(p.name());
                lastErr = p.name() + ": " + ex.getMessage();
                payment.addAttempt("FAIL " + lastErr);
            }
        }

        payment.markFailed(lastErr == null ? "All providers failed" : lastErr);
        return new PaymentResponse(payment.getId(), payment.getStatus());
    }

    // API: Check status
    PaymentStatus getPaymentStatus(String paymentId) {
        return repo.findById(paymentId).getStatus();
    }

    // Optional convenience: status by idempotency key
    PaymentStatus getPaymentStatus(String client, String idempotencyKey) {
        Payment p = repo.findByIdempotency(client, idempotencyKey);
        if (p == null) throw new NoSuchElementException("No payment for idempotency key");
        return p.getStatus();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Thin API Facade (what controllers would call)
// ─────────────────────────────────────────────────────────────────────────────
final class PaymentApi {
    private final PaymentOrchestrator orchestrator;

    PaymentApi(PaymentOrchestrator orchestrator) { this.orchestrator = Objects.requireNonNull(orchestrator); }

    PaymentResponse initiatePayment(PaymentRequest req) { return orchestrator.initiatePayment(req); }

    PaymentStatus getPaymentStatus(String paymentId) { return orchestrator.getPaymentStatus(paymentId); }
}

// ─────────────────────────────────────────────────────────────────────────────
// Demo / Wiring
// ─────────────────────────────────────────────────────────────────────────────
public class PaymentGatewayApp {
    public static void main(String[] args) {
        // Gateway + clients
        PaymentGateway gw = new PaymentGateway();
        Client flipkart = new Client("Flipkart");
        flipkart.addMethod(PaymentMethod.CREDIT_CARD);
        flipkart.addMethod(PaymentMethod.DEBIT_CARD);
        flipkart.addMethod(PaymentMethod.UPI);
        flipkart.addMethod(PaymentMethod.NET_BANKING);
        flipkart.addMethod(PaymentMethod.WALLET);
        gw.addClient(flipkart);

        // Providers
        Random rnd = new Random(7);
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new StripeProvider(rnd));
        registry.register(new RazorpayProvider(rnd));
        registry.register(new PaypalProvider(rnd));

        ProviderHealth health = new ProviderHealth(3);
        ProviderRouter router = new RoundRobinRouter(registry, health);

        // Method validators (Strategy stays like your original approach)
        Map<PaymentMethod, PaymentService> validators = Map.of(
            PaymentMethod.CREDIT_CARD, new CardPaymentService(PaymentMethod.CREDIT_CARD),
            PaymentMethod.DEBIT_CARD, new CardPaymentService(PaymentMethod.DEBIT_CARD),
            PaymentMethod.UPI, new UpiPaymentService(),
            PaymentMethod.NET_BANKING, new NetBankingPaymentService(),
            PaymentMethod.WALLET, new WalletPaymentService()
        );

        PaymentRepository repo = new PaymentRepository();
        PaymentOrchestrator orchestrator = new PaymentOrchestrator(gw, repo, validators, router, health);
        PaymentApi api = new PaymentApi(orchestrator);

        // Initiate payment (idempotent)
        String idemKey = "order_123_payment_1"; // client should reuse on retries
        PaymentRequest r1 = new PaymentRequest(
            "Flipkart",
            PaymentMethod.CREDIT_CARD,
            new CardDetails("4111111111111111", "123", "12/28", "Alice"),
            100.0,
            "INR",
            idemKey
        );

        PaymentResponse resp1 = api.initiatePayment(r1);
        System.out.println(resp1);
        System.out.println("Status: " + api.getPaymentStatus(resp1.paymentId));

        // Retry SAME request (same idempotencyKey) -> should NOT charge again; returns same paymentId/status
        PaymentResponse resp2 = api.initiatePayment(r1);
        System.out.println("Retry: " + resp2);
        System.out.println("Same paymentId? " + resp1.paymentId.equals(resp2.paymentId));
    }
}
