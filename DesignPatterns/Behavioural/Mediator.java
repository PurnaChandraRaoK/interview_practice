// Imagine an auction system where multiple bidders are excitedly placing their bids.
//  Instead of all bidders talking to each other directly—which would be chaotic and messy—we
//  introduce a central auction house that coordinates everything. This auction house is our mediator!
//  It makes communication smooth and structured, reducing direct dependencies between bidders

// Solution 

interface AuctionMediator {
  void registerBidder(Bidder bidder);
  void placeBid(Bidder bidder, int amount);
}

class AuctionHouse implements AuctionMediator {
 private
  List<Bidder> bidders = new ArrayList<>();

  @Override public void registerBidder(Bidder bidder) {
    bidders.add(bidder);
  }

  @Override public void placeBid(Bidder bidder, int amount) {
    System.out.println(bidder.getName() + " placed a bid of " + amount);
    for (Bidder b : bidders) {
      if (b != bidder) {
        b.receiveBid(bidder, amount);
      }
    }
  }
}

class Bidder {
 private
  String name;
 private
  AuctionMediator mediator;
 public
  Bidder(String name, AuctionMediator mediator) {
    this.name = name;
    this.mediator = mediator;
  }
 public
  String getName() { return name; }
 public
  void placeBid(int amount) { mediator.placeBid(this, amount); }
 public
  void receiveBid(Bidder bidder, int amount) {
    System.out.println(name + " is notified: " + bidder.getName() +
                       " placed a bid of " + amount);
  }
}

public
class AuctionMediatorDemo {
 public
  static void main(String[] args) {
    AuctionMediator auctionHouse = new AuctionHouse();
    Bidder bidder1 = new Bidder("Alice", auctionHouse);
    Bidder bidder2 = new Bidder("Bob", auctionHouse);
    Bidder bidder3 = new Bidder("Charlie", auctionHouse);
    auctionHouse.registerBidder(bidder1);
    auctionHouse.registerBidder(bidder2);
    auctionHouse.registerBidder(bidder3);
    bidder1.placeBid(100);
    bidder2.placeBid(150);
    bidder3.placeBid(200);
  }
}