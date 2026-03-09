import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// -------------------- Strategy Pattern (NEW) --------------------
interface BiddingStrategy {
    /**
     * Returns the effective bid amount the system should place.
     * Strategy may transform/limit the user input based on rules (e.g., auto-bid max cap).
     */
    BigDecimal computeBidAmount(Auction auction, User bidder, BigDecimal userInputAmount);
}

final class ManualBidStrategy implements BiddingStrategy {
    @Override
    public BigDecimal computeBidAmount(Auction auction, User bidder, BigDecimal userInputAmount) {
        return userInputAmount; // same behavior as before
    }
}

/**
 * Simple auto-bid:
 * - bidder provides a maxCap (maximum willing to pay)
 * - when asked to bid, it bids just enough to beat current highest by minIncrement, but never above maxCap
 */
final class MaxAutoBidStrategy implements BiddingStrategy {
    private final BigDecimal maxCap;
    private final BigDecimal minIncrement;

    public MaxAutoBidStrategy(BigDecimal maxCap, BigDecimal minIncrement) {
        this.maxCap = requirePositive(maxCap, "maxCap");
        this.minIncrement = requirePositive(minIncrement, "minIncrement");
    }

    @Override
    public BigDecimal computeBidAmount(Auction auction, User bidder, BigDecimal userInputAmount) {
        // userInputAmount can be ignored for auto-bid (or treated as "maxCap request"); keep simple:
        BigDecimal next = auction.getCurrentHighestBid().add(minIncrement);
        if (next.compareTo(maxCap) > 0) {
            // Can't beat current bid without exceeding cap
            return maxCap; // will be rejected by state if not > currentHighest
        }
        return next;
    }

    private static BigDecimal requirePositive(BigDecimal v, String name) {
        Objects.requireNonNull(v, name);
        if (v.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(name + " must be > 0");
        return v;
    }
}

// -------------------- State Pattern --------------------
interface AuctionState {
    void startBidding(Auction auction);
    void placeBid(Auction auction, Bid bid);
    void endBidding(Auction auction);
}

final class CreatedState implements AuctionState {
    @Override
    public void startBidding(Auction auction) {
        auction.markStarted();
        auction.setState(new ActiveState());
        System.out.println("Auction " + auction.getId() + " started");
    }

    @Override
    public void placeBid(Auction auction, Bid bid) {
        throw new IllegalStateException("Cannot place bid on inactive auction");
    }

    @Override
    public void endBidding(Auction auction) {
        throw new IllegalStateException("Cannot end auction that hasn't started");
    }
}

final class ActiveState implements AuctionState {
    @Override
    public void startBidding(Auction auction) {
        throw new IllegalStateException("Auction already active");
    }

    @Override
    public void placeBid(Auction auction, Bid bid) {
        Objects.requireNonNull(bid, "bid");

        if (bid.getAmount().compareTo(auction.getCurrentHighestBid()) <= 0) {
            throw new IllegalArgumentException("Bid amount must be higher than current bid");
        }

        auction.setCurrentHighestBid(bid.getAmount());
        auction.setHighestBidder(bid.getBidder());
        auction.recordBidAndNotify(bid);
    }

    @Override
    public void endBidding(Auction auction) {
        auction.markEnded();
        auction.setState(new EndedState());

        User winner = auction.getHighestBidder();
        if (winner == null) {
            System.out.println("Auction " + auction.getId() + " ended. No bids placed.");
        } else {
            System.out.println("Auction " + auction.getId() + " ended. Winner: " + winner.getName());
        }
    }
}

final class EndedState implements AuctionState {
    @Override
    public void startBidding(Auction auction) {
        throw new IllegalStateException("Cannot restart ended auction");
    }

    @Override
    public void placeBid(Auction auction, Bid bid) {
        throw new IllegalStateException("Cannot place bid on ended auction");
    }

    @Override
    public void endBidding(Auction auction) {
        throw new IllegalStateException("Auction already ended");
    }
}

// -------------------- Observer Pattern --------------------
interface BidObserver {
    void onNewBid(Bid bid);
}

final class BidNotificationService implements BidObserver {
    @Override
    public void onNewBid(Bid bid) {
        System.out.println("New bid: " + bid.getBidder().getName() +
                " bid $" + bid.getAmount() + " at " + bid.getTimestamp());
    }
}

// -------------------- Entities --------------------
final class User {
    private final String id;
    private final String name;
    private final String email;

    public User(String id, String name, String email) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
}

final class Item {
    private final String id;
    private final String name;
    private final String description;
    private final BigDecimal startingPrice;

    public Item(String id, String name, String description, BigDecimal startingPrice) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.startingPrice = requirePositive(startingPrice, "startingPrice");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getStartingPrice() { return startingPrice; }

    private static BigDecimal requirePositive(BigDecimal v, String name) {
        Objects.requireNonNull(v, name);
        if (v.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(name + " must be > 0");
        return v;
    }
}

final class Bid {
    private final String id;
    private final User bidder;
    private final BigDecimal amount;
    private final LocalDateTime timestamp;

    public Bid(String id, User bidder, BigDecimal amount) {
        this.id = Objects.requireNonNull(id, "id");
        this.bidder = Objects.requireNonNull(bidder, "bidder");
        this.amount = requirePositive(amount, "amount");
        this.timestamp = LocalDateTime.now();
    }

    public String getId() { return id; }
    public User getBidder() { return bidder; }
    public BigDecimal getAmount() { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }

    private static BigDecimal requirePositive(BigDecimal v, String name) {
        Objects.requireNonNull(v, name);
        if (v.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(name + " must be > 0");
        return v;
    }
}

// -------------------- Auction Domain --------------------
enum AuctionType { TIMED, FIXED_PRICE }

final class Auction {
    private final String id;
    private final Item item;
    private final User seller;

    private AuctionState state = new CreatedState();

    private BigDecimal currentHighestBid;
    private User highestBidder;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private final AuctionType type;
    private final LocalDateTime scheduledEndTime; // optional for TIMED
    private final BigDecimal fixedPrice;          // optional for FIXED_PRICE

    private final List<BidObserver> observers = new CopyOnWriteArrayList<>();
    private final List<Bid> bidHistory = new ArrayList<>();

    Auction(String id, Item item, User seller, AuctionType type, LocalDateTime scheduledEndTime, BigDecimal fixedPrice) {
        this.id = Objects.requireNonNull(id, "id");
        this.item = Objects.requireNonNull(item, "item");
        this.seller = Objects.requireNonNull(seller, "seller");
        this.type = Objects.requireNonNull(type, "type");
        this.scheduledEndTime = scheduledEndTime;
        this.fixedPrice = fixedPrice;
        this.currentHighestBid = item.getStartingPrice();
    }

    public void addObserver(BidObserver observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    public void removeObserver(BidObserver observer) {
        observers.remove(observer);
    }

    void recordBidAndNotify(Bid bid) {
        bidHistory.add(bid);
        for (BidObserver observer : observers) {
            observer.onNewBid(bid);
        }
    }

    public void startBidding() {
        state.startBidding(this);
    }

    public void placeBid(Bid bid) {
        if (bid.getBidder().getId().equals(seller.getId())) {
            throw new IllegalArgumentException("Seller cannot bid on own auction");
        }
        state.placeBid(this, bid);
    }

    public void endBidding() {
        state.endBidding(this);
    }

    void markStarted() { this.startTime = LocalDateTime.now(); }
    void markEnded() { this.endTime = LocalDateTime.now(); }

    public String getId() { return id; }
    public Item getItem() { return item; }
    public User getSeller() { return seller; }
    public BigDecimal getCurrentHighestBid() { return currentHighestBid; }
    public void setCurrentHighestBid(BigDecimal amount) { this.currentHighestBid = amount; }
    public User getHighestBidder() { return highestBidder; }
    public void setHighestBidder(User bidder) { this.highestBidder = bidder; }
    public AuctionType getType() { return type; }
    public LocalDateTime getScheduledEndTime() { return scheduledEndTime; }
    public BigDecimal getFixedPrice() { return fixedPrice; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public List<Bid> getBidHistory() { return Collections.unmodifiableList(bidHistory); }

    void setState(AuctionState state) { this.state = Objects.requireNonNull(state, "state"); }
}

// -------------------- Factory Pattern --------------------
final class AuctionFactory {
    private AuctionFactory() {}

    public static Auction createTimedAuction(String id, Item item, User seller, int durationMinutes) {
        LocalDateTime scheduledEnd = LocalDateTime.now().plusMinutes(durationMinutes);
        return new Auction(id, item, seller, AuctionType.TIMED, scheduledEnd, null);
    }

    public static Auction createFixedPriceAuction(String id, Item item, User seller, BigDecimal fixedPrice) {
        Objects.requireNonNull(fixedPrice, "fixedPrice");
        return new Auction(id, item, seller, AuctionType.FIXED_PRICE, null, fixedPrice);
    }
}

// -------------------- Singleton + Mediator (Auction House) --------------------
final class AuctionManagementSystem {
    private static final AuctionManagementSystem INSTANCE = new AuctionManagementSystem();

    private final Map<String, Auction> activeAuctions = new ConcurrentHashMap<>();
    private final Map<String, User> users = new ConcurrentHashMap<>();

    // Strategy registry: (auctionId -> (bidderId -> strategy))
    private final Map<String, Map<String, BiddingStrategy>> biddingStrategies = new ConcurrentHashMap<>();

    private AuctionManagementSystem() {}

    public static AuctionManagementSystem getInstance() {
        return INSTANCE;
    }

    public void registerUser(User user) {
        users.put(user.getId(), user);
    }

    public Auction createTimedAuction(String auctionId, Item item, String sellerId, int durationMinutes) {
        User seller = users.get(sellerId);
        if (seller == null) throw new IllegalArgumentException("Seller not found");

        Auction auction = AuctionFactory.createTimedAuction(auctionId, item, seller, durationMinutes);
        auction.addObserver(new BidNotificationService());

        activeAuctions.put(auctionId, auction);
        biddingStrategies.putIfAbsent(auctionId, new ConcurrentHashMap<>());

        return auction;
    }

    public Auction createFixedPriceAuction(String auctionId, Item item, String sellerId, BigDecimal fixedPrice) {
        User seller = users.get(sellerId);
        if (seller == null) throw new IllegalArgumentException("Seller not found");

        Auction auction = AuctionFactory.createFixedPriceAuction(auctionId, item, seller, fixedPrice);
        auction.addObserver(new BidNotificationService());

        activeAuctions.put(auctionId, auction);
        biddingStrategies.putIfAbsent(auctionId, new ConcurrentHashMap<>());

        return auction;
    }

    public void startAuction(String auctionId) {
        getRequiredAuction(auctionId).startBidding();
    }

    public void endAuction(String auctionId) {
        getRequiredAuction(auctionId).endBidding();
    }

    // NEW: allow bidder to register a strategy per auction
    public void setBiddingStrategy(String auctionId, String bidderId, BiddingStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy");
        getRequiredAuction(auctionId); // validate auction exists
        getRequiredUser(bidderId);     // validate bidder exists

        biddingStrategies
                .computeIfAbsent(auctionId, k -> new ConcurrentHashMap<>())
                .put(bidderId, strategy);
    }

    /**
     * Bid entry point (Mediator):
     * - finds bidder + auction
     * - applies bidder's strategy (or default manual)
     * - places bid into auction (state validates + observer notifies)
     */
    public void placeBid(String auctionId, String bidderId, BigDecimal userInputAmount) {
        Auction auction = getRequiredAuction(auctionId);
        User bidder = getRequiredUser(bidderId);

        BiddingStrategy strategy = biddingStrategies
                .getOrDefault(auctionId, Collections.emptyMap())
                .getOrDefault(bidderId, new ManualBidStrategy());

        BigDecimal effectiveAmount = strategy.computeBidAmount(auction, bidder, userInputAmount);

        Bid bid = new Bid(UUID.randomUUID().toString(), bidder, effectiveAmount);
        auction.placeBid(bid);
    }

    public Auction getAuction(String auctionId) {
        return activeAuctions.get(auctionId);
    }

    public Collection<Auction> getAllActiveAuctions() {
        return activeAuctions.values();
    }

    private Auction getRequiredAuction(String auctionId) {
        Auction auction = activeAuctions.get(auctionId);
        if (auction == null) throw new IllegalArgumentException("Auction not found");
        return auction;
    }

    private User getRequiredUser(String userId) {
        User user = users.get(userId);
        if (user == null) throw new IllegalArgumentException("User not found");
        return user;
    }
}
