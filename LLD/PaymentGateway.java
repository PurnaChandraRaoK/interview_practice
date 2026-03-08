import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// ─────────────────────────────────────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────────────────────────────────────
enum PaymentMethod { CREDIT_CARD, DEBIT_CARD, UPI, NET_BANKING, WALLET }
enum PaymentStatus { CREATED, PROCESSING, SUCCESS, FAILED }

// ─────────────────────────────────────────────────────────────────────────────
// Client + Gateway
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

final class PaymentGateway {
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    void addClient(Client c) {
        Objects.requireNonNull(c, "client");
        Client prev = clients.putIfAbsent(c.getName(), c);
        if (prev != null) throw new IllegalArgumentException("Client exists: " + c.getName());
    }

    Client getClient(String name) {
        Client c = clients.get(name);
        if (c == null) throw new NoSuchElementException("Client not found: " + name);
        return c;
    }

    void addSupport(String clientName, PaymentMethod m) { getClient(clientName).addMethod(m); }
    void removeSupport(String clientName, PaymentMethod m) { getClient(clientName).removeMethod(m); }
}

// ─────────────────────────────────────────────────────────────────────────────
// PaymentDetails (Java 8 simplified)
// ─────────────────────────────────────────────────────────────────────────────
interface PaymentDetails { }

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
    final String idempotencyKey; // same request should not charge twice

    PaymentRequest(String client, PaymentMethod method, PaymentDetails details,
                   double amount, String currency, String idempotencyKey) {
        this.client = Objects.requireNonNull(client, "client");
        this.method = Objects.requireNonNull(method, "method");
        this.details = Objects.requireNonNull(details, "details");
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        this.amount = amount;
        this.currency = (currency == null || currency.trim().isEmpty()) ? "INR" : currency;
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
// Attempt (replacement for List<String> attempts)
// ─────────────────────────────────────────────────────────────────────────────
enum AttemptOutcome { TRY, SUCCESS, FAIL }

final class Attempt {
    final int number;
    final String provider;
    final AttemptOutcome outcome;
    final String reason; // null unless FAIL

    Attempt(int number, String provider, AttemptOutcome outcome, String reason) {
        this.number = number;
        this.provider = Objects.requireNonNull(provider, "provider");
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.reason = reason;
    }

    static Attempt tryAttempt(int number, String provider) {
        return new Attempt(number, provider, AttemptOutcome.TRY, null);
    }

    static Attempt successAttempt(int number, String provider) {
        return new Attempt(number, provider, AttemptOutcome.SUCCESS, null);
    }

    static Attempt failAttempt(int number, String provider, String reason) {
        return new Attempt(number, provider, AttemptOutcome.FAIL, reason);
    }

    @Override public String toString() {
        if (outcome == AttemptOutcome.FAIL) return "#" + number + " " + provider + " FAIL (" + reason + ")";
        return "#" + number + " " + provider + " " + outcome;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Payment + Repository (idempotency)
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

    // ✅ Typed attempts
    private final List<Attempt> attempts = new ArrayList<>();

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

    synchronized void addAttempt(Attempt a) { attempts.add(a); }

    synchronized List<Attempt> getAttempts() { return new ArrayList<>(attempts); }

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
        String id = idempotencyIndex.get(composite(client, idemKey));
        return id == null ? null : byId.get(id);
    }

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
// Method validators (Strategy Pattern)
// ─────────────────────────────────────────────────────────────────────────────
interface PaymentService { void validate(PaymentRequest req); }

final class CardPaymentService implements PaymentService {
    private final PaymentMethod type;
    CardPaymentService(PaymentMethod type) { this.type = type; }

    public void validate(PaymentRequest req) {
        if (req.method != type) throw new IllegalArgumentException("Wrong service for " + req.method);
        if (!(req.details instanceof CardDetails)) throw new IllegalArgumentException(type + " expects CardDetails");
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
// Providers
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
    Set<PaymentMethod> supportedMethods();
    ProviderChargeResult charge(Payment payment, PaymentRequest request);
}

final class StripeProvider implements PaymentProvider {
    private final Random rnd;
    StripeProvider(Random rnd) { this.rnd = Objects.requireNonNull(rnd); }

    public String name() { return "Stripe"; }

    public Set<PaymentMethod> supportedMethods() {
        return EnumSet.of(PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD, PaymentMethod.UPI, PaymentMethod.WALLET);
    }

    public ProviderChargeResult charge(Payment payment, PaymentRequest request) {
        if (rnd.nextInt(100) < 20) throw new ProviderException("Stripe provider error");
        System.out.println("Stripe charged: " + request.amount + " " + request.currency + " via " + request.method);
        return new ProviderChargeResult("st_" + UUID.randomUUID().toString().substring(0, 8));
    }
}

final class RazorpayProvider implements PaymentProvider {
    private final Random rnd;
    RazorpayProvider(Random rnd) { this.rnd = Objects.requireNonNull(rnd); }

    public String name() { return "Razorpay"; }

    public Set<PaymentMethod> supportedMethods() {
        return EnumSet.of(PaymentMethod.UPI, PaymentMethod.NET_BANKING, PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD);
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

    public Set<PaymentMethod> supportedMethods() {
        return EnumSet.of(PaymentMethod.WALLET, PaymentMethod.CREDIT_CARD, PaymentMethod.DEBIT_CARD);
    }

    public ProviderChargeResult charge(Payment payment, PaymentRequest request) {
        if (rnd.nextInt(100) < 30) throw new ProviderException("PayPal rejected transaction");
        System.out.println("PayPal charged: " + request.amount + " " + request.currency + " via " + request.method);
        return new ProviderChargeResult("pp_" + UUID.randomUUID().toString().substring(0, 8));
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Provider Registry (method -> list of providers) + Router + Health
// ─────────────────────────────────────────────────────────────────────────────
final class ProviderRegistry {
    private final Map<PaymentMethod, List<PaymentProvider>> providers = new EnumMap<>(PaymentMethod.class);

    public void register(PaymentProvider provider) {
        Objects.requireNonNull(provider, "provider");
        for (PaymentMethod method : provider.supportedMethods()) {
            providers.computeIfAbsent(method, m -> new ArrayList<>()).add(provider);
        }
    }

    public List<PaymentProvider> getProviders(PaymentMethod method) {
        return providers.getOrDefault(method, Collections.<PaymentProvider>emptyList());
    }
}

final class ProviderHealth {
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();
    private final int failureThreshold;

    ProviderHealth(int failureThreshold) { this.failureThreshold = Math.max(1, failureThreshold); }

    boolean isHealthy(String provider) { return consecutiveFailures.getOrDefault(provider, 0) < failureThreshold; }
    void recordSuccess(String provider) { consecutiveFailures.put(provider, 0); }
    void recordFailure(String provider) { consecutiveFailures.merge(provider, 1, Integer::sum); }
}

interface ProviderRouter {
    List<PaymentProvider> routeCandidates(PaymentMethod method);
}

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
        List<PaymentProvider> list = new ArrayList<>(registry.getProviders(method));
        if (list.isEmpty()) return Collections.emptyList();

        // prefer healthy providers
        List<PaymentProvider> healthy = new ArrayList<>();
        for (PaymentProvider p : list) if (health.isHealthy(p.name())) healthy.add(p);
        if (!healthy.isEmpty()) list = healthy;

        // rotate list
        int start = Math.floorMod(rr.get(method).getAndIncrement(), list.size());
        List<PaymentProvider> ordered = new ArrayList<>(list.size());
        ordered.addAll(list.subList(start, list.size()));
        ordered.addAll(list.subList(0, start));
        return ordered;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Retry Policy
// ─────────────────────────────────────────────────────────────────────────────
final class RetryPolicy {
    final int maxAttempts; // total provider attempts for this payment
    RetryPolicy(int maxAttempts) {
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be > 0");
        this.maxAttempts = maxAttempts;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Orchestrator (idempotency + routing + provider failover + retries up to 3)
// ─────────────────────────────────────────────────────────────────────────────
final class PaymentOrchestrator {
    private final PaymentGateway gateway;
    private final PaymentRepository repo;
    private final Map<PaymentMethod, PaymentService> validators;
    private final ProviderRouter router;
    private final ProviderHealth health;
    private final RetryPolicy retryPolicy;

    PaymentOrchestrator(PaymentGateway gateway,
                        PaymentRepository repo,
                        Map<PaymentMethod, PaymentService> validators,
                        ProviderRouter router,
                        ProviderHealth health,
                        RetryPolicy retryPolicy) {
        this.gateway = Objects.requireNonNull(gateway);
        this.repo = Objects.requireNonNull(repo);
        this.validators = new EnumMap<>(PaymentMethod.class);
        if (validators != null) this.validators.putAll(validators);
        this.router = Objects.requireNonNull(router);
        this.health = Objects.requireNonNull(health);
        this.retryPolicy = Objects.requireNonNull(retryPolicy);
    }

    PaymentResponse initiatePayment(PaymentRequest req) {
        Objects.requireNonNull(req, "request");

        Client c = gateway.getClient(req.client);
        if (!c.supports(req.method)) throw new IllegalArgumentException(req.method + " not supported for " + req.client);

        PaymentService validator = validators.get(req.method);
        if (validator == null) throw new IllegalStateException("No validator registered for " + req.method);
        validator.validate(req);

        // Idempotency: same key => same Payment returned; no re-charge
        Payment existing = repo.findByIdempotency(req.client, req.idempotencyKey);
        if (existing != null) return new PaymentResponse(existing.getId(), existing.getStatus());

        Payment payment = repo.createIfAbsent(req.client, req.method, req.amount, req.currency, req.idempotencyKey);

        // race safety
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED) {
            return new PaymentResponse(payment.getId(), payment.getStatus());
        }

        int attemptNo = 0;
        String lastErr = null;

        while (attemptNo < retryPolicy.maxAttempts) {
            List<PaymentProvider> candidates = router.routeCandidates(req.method);
            if (candidates.isEmpty()) {
                payment.markFailed("No providers available for " + req.method);
                return new PaymentResponse(payment.getId(), payment.getStatus());
            }

            boolean triedSomething = false;

            for (PaymentProvider p : candidates) {
                if (attemptNo >= retryPolicy.maxAttempts) break;

                attemptNo++;
                triedSomething = true;

                // record TRY
                payment.addAttempt(Attempt.tryAttempt(attemptNo, p.name()));

                try {
                    payment.markProcessing(p.name()); // CREATED -> PROCESSING

                    ProviderChargeResult res = p.charge(payment, req);

                    health.recordSuccess(p.name());
                    payment.addAttempt(Attempt.successAttempt(attemptNo, p.name()));

                    payment.markSuccess(p.name(), res.providerRef); // PROCESSING -> SUCCESS
                    return new PaymentResponse(payment.getId(), payment.getStatus());

                } catch (ProviderException ex) {
                    health.recordFailure(p.name());
                    lastErr = p.name() + ": " + ex.getMessage();
                    payment.addAttempt(Attempt.failAttempt(attemptNo, p.name(), ex.getMessage()));
                } catch (RuntimeException ex) {
                    health.recordFailure(p.name());
                    lastErr = p.name() + ": " + ex.getMessage();
                    payment.addAttempt(Attempt.failAttempt(attemptNo, p.name(), ex.getMessage()));
                }
            }

            if (!triedSomething) break;
        }

        payment.markFailed(lastErr == null ? "All retries exhausted" : "All retries exhausted. Last=" + lastErr);
        return new PaymentResponse(payment.getId(), payment.getStatus());
    }

    PaymentStatus getPaymentStatus(String paymentId) {
        return repo.findById(paymentId).getStatus();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Thin API Facade
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
        // Gateway + client
        PaymentGateway gw = new PaymentGateway();
        Client flipkart = new Client("Flipkart");
        for (PaymentMethod m : PaymentMethod.values()) flipkart.addMethod(m);
        gw.addClient(flipkart);

        // Providers
        Random rnd = new Random(7);
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new StripeProvider(rnd));
        registry.register(new RazorpayProvider(rnd));
        registry.register(new PaypalProvider(rnd));

        ProviderHealth health = new ProviderHealth(3);
        ProviderRouter router = new RoundRobinRouter(registry, health);

        // Validators
        Map<PaymentMethod, PaymentService> validators = new EnumMap<>(PaymentMethod.class);
        validators.put(PaymentMethod.CREDIT_CARD, new CardPaymentService(PaymentMethod.CREDIT_CARD));
        validators.put(PaymentMethod.DEBIT_CARD, new CardPaymentService(PaymentMethod.DEBIT_CARD));
        validators.put(PaymentMethod.UPI, new UpiPaymentService());
        validators.put(PaymentMethod.NET_BANKING, new NetBankingPaymentService());
        validators.put(PaymentMethod.WALLET, new WalletPaymentService());

        PaymentRepository repo = new PaymentRepository();
        RetryPolicy retryPolicy = new RetryPolicy(3); // ✅ retries up to 3 total provider attempts

        PaymentOrchestrator orchestrator = new PaymentOrchestrator(gw, repo, validators, router, health, retryPolicy);
        PaymentApi api = new PaymentApi(orchestrator);

        // Initiate
        String idemKey = "order_123_payment_1";
        PaymentRequest req = new PaymentRequest(
            "Flipkart",
            PaymentMethod.CREDIT_CARD,
            new CardDetails("4111111111111111", "123", "12/28", "Alice"),
            100.0,
            "INR",
            idemKey
        );

        PaymentResponse r1 = api.initiatePayment(req);
        System.out.println(r1);
        System.out.println("Status: " + api.getPaymentStatus(r1.paymentId));

        // Retry same request -> idempotent: returns same paymentId/status, no extra charge attempt
        PaymentResponse r2 = api.initiatePayment(req);
        System.out.println("Retry same key: " + r2);
        System.out.println("Same paymentId? " + r1.paymentId.equals(r2.paymentId));
    }
}
