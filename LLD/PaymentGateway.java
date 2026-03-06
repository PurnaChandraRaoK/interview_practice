import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// ─────────────────────────────────────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────────────────────────────────────
enum Bank { HDFC, UBI, PNB }

enum PaymentMethod { CARD, UPI, NET_BANKING }

enum PaymentStatus { IN_PROGRESS, ERROR, SUCCESS }

// ─────────────────────────────────────────────────────────────────────────────
// Configuration for routing
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Router configuration: what percentage of traffic a bank should handle for a method.
 * Immutable config (usage should not live inside config).
 */
final class PGConfig {
    final Bank bank;
    final double percentage; // e.g. 30 means 30%
    final Map<String, String> credentials;

    PGConfig(Bank bank, double percentage) {
        this(bank, percentage, Map.of());
    }

    PGConfig(Bank bank, double percentage, Map<String, String> credentials) {
        this.bank = Objects.requireNonNull(bank, "bank");
        if (percentage <= 0 || percentage > 100) {
            throw new IllegalArgumentException("percentage must be (0, 100]: " + percentage);
        }
        this.percentage = percentage;
        this.credentials = credentials == null ? Map.of() : Map.copyOf(credentials);
    }

    @Override
    public String toString() {
        return bank + "{pct=" + percentage + ", creds=" + credentials.keySet() + '}';
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Client
// ─────────────────────────────────────────────────────────────────────────────
final class Client {
    private final String name;
    private final Set<PaymentMethod> supportedMethods = EnumSet.noneOf(PaymentMethod.class);

    Client(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    String getName() { return name; }

    boolean supports(PaymentMethod m) { return supportedMethods.contains(m); }

    void addMethod(PaymentMethod m) { supportedMethods.add(Objects.requireNonNull(m)); }

    void removeMethod(PaymentMethod m) { supportedMethods.remove(m); }

    Set<PaymentMethod> getMethods() { return Collections.unmodifiableSet(supportedMethods); }
}

// ─────────────────────────────────────────────────────────────────────────────
// Router Strategy (pluggable selection algorithm)
// ─────────────────────────────────────────────────────────────────────────────
interface Router {
    Bank select(PaymentMethod method, List<PGConfig> configs);
    Map<Bank, Integer> usageSnapshot(PaymentMethod method);
}

/**
 * Weighted random router. Keeps usage counts per method+bank (not in config).
 */
final class WeightedRandomRouter implements Router {
    private final Random random;
    private final Map<PaymentMethod, Map<Bank, Integer>> usage = new EnumMap<>(PaymentMethod.class);

    WeightedRandomRouter() { this(new Random()); }

    WeightedRandomRouter(Random random) { this.random = Objects.requireNonNull(random); }

    @Override
    public Bank select(PaymentMethod method, List<PGConfig> configs) {
        Objects.requireNonNull(method);
        if (configs == null || configs.isEmpty()) {
            throw new IllegalStateException(method + " not configured");
        }

        double rnd = random.nextDouble() * 100.0;
        double cumul = 0.0;

        for (PGConfig c : configs) {
            cumul += c.percentage;
            if (rnd <= cumul) {
                bumpUsage(method, c.bank);
                return c.bank;
            }
        }

        // Fallback (floating point edges)
        Bank last = configs.get(configs.size() - 1).bank;
        bumpUsage(method, last);
        return last;
    }

    @Override
    public Map<Bank, Integer> usageSnapshot(PaymentMethod method) {
        Map<Bank, Integer> m = usage.get(method);
        return m == null ? Map.of() : Collections.unmodifiableMap(m);
    }

    private void bumpUsage(PaymentMethod method, Bank bank) {
        usage.computeIfAbsent(method, k -> new EnumMap<>(Bank.class))
             .merge(bank, 1, Integer::sum);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PaymentGateway (holds clients + routing config)
// ─────────────────────────────────────────────────────────────────────────────
final class PaymentGateway {
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    private final Map<PaymentMethod, List<PGConfig>> routerConfig = new EnumMap<>(PaymentMethod.class);
    private final Router router;

    PaymentGateway() {
        this(new WeightedRandomRouter());
    }

    PaymentGateway(Router router) {
        this.router = Objects.requireNonNull(router, "router");
    }

    // Client operations
    void addClient(Client c) {
        Objects.requireNonNull(c, "client");
        Client prev = clients.putIfAbsent(c.getName(), c);
        if (prev != null) throw new IllegalArgumentException("Client exists: " + c.getName());
    }

    void removeClient(String name) {
        clients.remove(name);
    }

    Client getClient(String name) {
        Client c = clients.get(name);
        if (c == null) throw new NoSuchElementException("Client not found: " + name);
        return c;
    }

    // Payment method support per client
    List<PaymentMethod> listSupportedMethods(String clientName) {
        return new ArrayList<>(getClient(clientName).getMethods());
    }

    void addSupport(String clientName, PaymentMethod m) {
        getClient(clientName).addMethod(m);
    }

    void removeSupport(String clientName, PaymentMethod m) {
        getClient(clientName).removeMethod(m);
    }

    // Routing config
    void setRouting(PaymentMethod m, List<PGConfig> configs) {
        Objects.requireNonNull(m, "method");
        Objects.requireNonNull(configs, "configs");
        if (configs.isEmpty()) throw new IllegalArgumentException("configs cannot be empty");

        double total = configs.stream().mapToDouble(c -> c.percentage).sum();
        if (Math.abs(total - 100.0) > 0.001) {
            throw new IllegalArgumentException("Percentages must sum to 100, got: " + total);
        }

        routerConfig.put(m, List.copyOf(configs));
    }

    void showDistribution() {
        routerConfig.forEach((m, cfgs) -> {
            System.out.println("Method " + m + ":");
            cfgs.forEach(c -> System.out.printf("  %s -> %.1f%%%n", c.bank, c.percentage));
            System.out.println("  usage=" + router.usageSnapshot(m));
        });
    }

    Bank selectBank(PaymentMethod m) {
        List<PGConfig> cfgs = routerConfig.get(m);
        return router.select(m, cfgs);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transaction & Repository
// ─────────────────────────────────────────────────────────────────────────────
final class Transaction {
    private final String id;
    private final PaymentMethod method;
    private final String client;
    private final double amount;
    private final Bank bank;

    private PaymentStatus status;
    private String failureReason;

    Transaction(String client, PaymentMethod method, double amount, Bank bank) {
        this.id = UUID.randomUUID().toString();
        this.client = Objects.requireNonNull(client, "client");
        this.method = Objects.requireNonNull(method, "method");
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        this.amount = amount;
        this.bank = Objects.requireNonNull(bank, "bank");
        this.status = PaymentStatus.IN_PROGRESS;
    }

    String getId() { return id; }
    PaymentStatus getStatus() { return status; }

    void markSuccess() {
        this.status = PaymentStatus.SUCCESS;
        this.failureReason = null;
    }

    void markFailure(String reason) {
        this.status = PaymentStatus.ERROR;
        this.failureReason = reason;
    }

    @Override
    public String toString() {
        return String.format(
            "Txn[id=%s, client=%s, method=%s, bank=%s, amount=%.2f, status=%s, reason=%s]",
            id, client, method, bank, amount, status, failureReason
        );
    }
}

final class TransactionRepository {
    private final Map<String, Transaction> store = new LinkedHashMap<>();

    Transaction create(String client, PaymentMethod m, double amt, Bank b) {
        Transaction t = new Transaction(client, m, amt, b);
        store.put(t.getId(), t);
        return t;
    }

    Transaction find(String id) {
        Transaction t = store.get(id);
        if (t == null) throw new NoSuchElementException("Txn not found: " + id);
        return t;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Payment Services (Strategy Pattern)
// ─────────────────────────────────────────────────────────────────────────────
interface PaymentService {
    void process(double amount, Object data);
}

final class CardService implements PaymentService {
    private final Map<Bank, CardProcessor> processors;

    CardService(Map<Bank, CardProcessor> procs) {
        this.processors = Objects.requireNonNull(procs, "processors");
    }

    @Override
    public void process(double amount, Object data) {
        if (!(data instanceof Card card)) {
            throw new IllegalArgumentException("CARD expects Card data");
        }
        CardProcessor proc = processors.get(card.bank);
        if (proc == null) throw new IllegalArgumentException("Unsupported bank for CARD: " + card.bank);
        proc.execute(amount, card);
    }
}

final class UpiService implements PaymentService {
    private final UpiProcessor npci;

    UpiService(UpiProcessor npci) {
        this.npci = Objects.requireNonNull(npci, "npci");
    }

    @Override
    public void process(double amount, Object data) {
        if (!(data instanceof UPI upi)) {
            throw new IllegalArgumentException("UPI expects UPI data");
        }
        npci.execute(amount, upi.upiId);
    }
}

final class NetBankingService implements PaymentService {
    private final Map<Bank, NetBankProcessor> processors;

    NetBankingService(Map<Bank, NetBankProcessor> procs) {
        this.processors = Objects.requireNonNull(procs, "processors");
    }

    @Override
    public void process(double amount, Object data) {
        if (!(data instanceof NetBanking nb)) {
            throw new IllegalArgumentException("NET_BANKING expects NetBanking data");
        }
        NetBankProcessor proc = processors.get(nb.bank);
        if (proc == null) throw new IllegalArgumentException("Unsupported bank for NET_BANKING: " + nb.bank);
        proc.execute(amount, nb);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Processor Interfaces
// ─────────────────────────────────────────────────────────────────────────────
interface CardProcessor { void execute(double amt, Card card); }
interface UpiProcessor { void execute(double amt, String upi); }
interface NetBankProcessor { void execute(double amt, NetBanking nb); }

// ─────────────────────────────────────────────────────────────────────────────
// Data Classes
// ─────────────────────────────────────────────────────────────────────────────
final class Card {
    final Bank bank;
    final String number, cvv, expiry, holder;

    Card(Bank bank, String number, String cvv, String expiry, String holder) {
        this.bank = Objects.requireNonNull(bank);
        this.number = Objects.requireNonNull(number);
        this.cvv = Objects.requireNonNull(cvv);
        this.expiry = Objects.requireNonNull(expiry);
        this.holder = Objects.requireNonNull(holder);
    }
}

final class UPI {
    final String upiId;

    UPI(String upiId) {
        this.upiId = Objects.requireNonNull(upiId);
    }
}

final class NetBanking {
    final Bank bank;
    final String user, pass;

    NetBanking(Bank bank, String user, String pass) {
        this.bank = Objects.requireNonNull(bank);
        this.user = Objects.requireNonNull(user);
        this.pass = Objects.requireNonNull(pass);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transaction Service
// ─────────────────────────────────────────────────────────────────────────────
final class TransactionService {
    private final PaymentGateway gateway;
    private final TransactionRepository repo;
    private final Map<PaymentMethod, PaymentService> services;

    TransactionService(PaymentGateway gw, TransactionRepository repo,
                       Map<PaymentMethod, PaymentService> svcMap) {
        this.gateway = Objects.requireNonNull(gw, "gateway");
        this.repo = Objects.requireNonNull(repo, "repo");
        this.services = new EnumMap<>(PaymentMethod.class);
        if (svcMap != null) this.services.putAll(svcMap);
    }

    Transaction makePayment(String client, PaymentMethod method, Object data, double amount) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(method, "method");

        Client c = gateway.getClient(client);
        if (!c.supports(method)) {
            throw new IllegalArgumentException(method + " not supported for " + client);
        }

        PaymentService svc = services.get(method);
        if (svc == null) {
            throw new IllegalStateException("No PaymentService registered for " + method);
        }

        Bank bank = gateway.selectBank(method);
        Transaction txn = repo.create(client, method, amount, bank);

        try {
            svc.process(amount, data);
            txn.markSuccess();
            return txn;
        } catch (Exception ex) {
            txn.markFailure(ex.getMessage());
            throw ex;
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Example wiring (demo)
// ─────────────────────────────────────────────────────────────────────────────
public class PaymentGatewayApp {
    public static void main(String[] args) {
        // Setup gateway
        PaymentGateway gw = new PaymentGateway();
        Client flipkart = new Client("Flipkart");
        flipkart.addMethod(PaymentMethod.CARD);
        gw.addClient(flipkart);

        gw.setRouting(PaymentMethod.CARD, Arrays.asList(
            new PGConfig(Bank.HDFC, 50),
            new PGConfig(Bank.PNB, 50)
        ));

        // Setup services
        Map<Bank, CardProcessor> cardProcs = Map.of(
            Bank.HDFC, (amt, c) -> System.out.println("HDFC card: " + amt),
            Bank.PNB,  (amt, c) -> System.out.println("PNB card: " + amt)
        );
        PaymentService cardSvc = new CardService(cardProcs);

        TransactionRepository repo = new TransactionRepository();
        TransactionService txnSvc = new TransactionService(
            gw, repo, Map.of(PaymentMethod.CARD, cardSvc)
        );

        // Execute
        Card card = new Card(Bank.HDFC, "1111", "123", "12/25", "Alice");
        Transaction t = txnSvc.makePayment("Flipkart", PaymentMethod.CARD, card, 100.0);
        System.out.println(t);

        gw.showDistribution();
    }
}
