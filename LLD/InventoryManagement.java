import java.time.LocalDate;
import java.util.*;

// ---------- Enums ----------
enum ProductCategory {
    ELECTRONICS,
    CLOTHING,
    GROCERY,
    FURNITURE,
    OTHER
}

enum TransferStatus {
    INITIATED,
    IN_TRANSIT,
    COMPLETED
}

// ---------- Model ----------
abstract class Product {
    private final String sku;
    private final String name;
    private final double price;
    private int quantity;          // On-hand (warehouse-specific)
    private final int threshold;
    private final ProductCategory category;

    protected Product(String sku, String name, double price, int quantity, int threshold, ProductCategory category) {
        this.sku = requireNonBlank(sku, "sku");
        this.name = requireNonBlank(name, "name");
        this.price = requireNonNegative(price, "price");
        this.quantity = requireNonNegative(quantity, "quantity");
        this.threshold = requireNonNegative(threshold, "threshold");
        this.category = Objects.requireNonNull(category, "category");
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; } // on-hand
    public int getThreshold() { return threshold; }
    public ProductCategory getCategory() { return category; }

    void increaseQuantity(int delta) {
        if (delta <= 0) throw new IllegalArgumentException("delta must be > 0");
        quantity += delta;
    }

    void decreaseQuantity(int delta) {
        if (delta <= 0) throw new IllegalArgumentException("delta must be > 0");
        if (quantity < delta) throw new IllegalArgumentException("insufficient on-hand quantity");
        quantity -= delta;
    }

    private static String requireNonBlank(String v, String field) {
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return v.trim();
    }

    private static int requireNonNegative(int v, String field) {
        if (v < 0) throw new IllegalArgumentException(field + " cannot be negative");
        return v;
    }

    private static double requireNonNegative(double v, String field) {
        if (v < 0) throw new IllegalArgumentException(field + " cannot be negative");
        return v;
    }
}

final class ElectronicsProduct extends Product {
    private final String brand;
    private final int warrantyPeriodMonths;

    public ElectronicsProduct(String sku, String name, double price, int quantity, int threshold,
                              String brand, int warrantyPeriodMonths) {
        super(sku, name, price, quantity, threshold, ProductCategory.ELECTRONICS);
        this.brand = brand == null ? "" : brand.trim();
        if (warrantyPeriodMonths < 0) throw new IllegalArgumentException("warrantyPeriodMonths cannot be negative");
        this.warrantyPeriodMonths = warrantyPeriodMonths;
    }

    public String getBrand() { return brand; }
    public int getWarrantyPeriodMonths() { return warrantyPeriodMonths; }
}

final class ClothingProduct extends Product {
    private final String size;
    private final String color;

    public ClothingProduct(String sku, String name, double price, int quantity, int threshold,
                           String size, String color) {
        super(sku, name, price, quantity, threshold, ProductCategory.CLOTHING);
        this.size = size == null ? "" : size.trim();
        this.color = color == null ? "" : color.trim();
    }

    public String getSize() { return size; }
    public String getColor() { return color; }
}

final class GroceryProduct extends Product {
    private final LocalDate expiryDate;
    private final boolean refrigerated;

    public GroceryProduct(String sku, String name, double price, int quantity, int threshold,
                          LocalDate expiryDate, boolean refrigerated) {
        super(sku, name, price, quantity, threshold, ProductCategory.GROCERY);
        this.expiryDate = expiryDate; // can be null
        this.refrigerated = refrigerated;
    }

    public LocalDate getExpiryDate() { return expiryDate; }
    public boolean isRefrigerated() { return refrigerated; }
}

// ---------- Factory ----------
final class ProductFactory {
    public Product createProduct(ProductCategory category, String sku, String name, double price, int quantity, int threshold) {
        Objects.requireNonNull(category, "category");
        return switch (category) {
            case ELECTRONICS -> new ElectronicsProduct(sku, name, price, quantity, threshold, "Generic", 12);
            case CLOTHING    -> new ClothingProduct(sku, name, price, quantity, threshold, "M", "Black");
            case GROCERY     -> new GroceryProduct(sku, name, price, quantity, threshold, null, false);
            default          -> new Product(sku, name, price, quantity, threshold, category) {};
        };
    }
}

// ---------- Warehouse ----------
final class Warehouse {
    private final String name;
    private String location;

    // SKU -> Product (contains on-hand quantity)
    private final Map<String, Product> products = new HashMap<>();

    public Warehouse(String name) {
        this.name = requireNonBlank(name, "name");
    }

    public String getName() { return name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public int getOnHandQuantity(String sku) {
        Product p = products.get(sku);
        return p == null ? 0 : p.getQuantity();
    }

    // With reservation removed, available == on-hand
    public int getAvailableQuantity(String sku) {
        return getOnHandQuantity(sku);
    }

    public Product getProductBySku(String sku) { return products.get(sku); }

    public Collection<Product> getAllProducts() {
        return Collections.unmodifiableCollection(products.values());
    }

    public synchronized void addProduct(Product product, int quantityToAdd) {
        Objects.requireNonNull(product, "product");
        if (quantityToAdd <= 0) throw new IllegalArgumentException("quantityToAdd must be > 0");

        String sku = product.getSku();
        Product existing = products.get(sku);

        if (existing != null) {
            existing.increaseQuantity(quantityToAdd);
        } else {
            product.increaseQuantity(quantityToAdd);
            products.put(sku, product);
        }

        System.out.println(quantityToAdd + " units of " + product.getName()
                + " (SKU: " + sku + ") added to " + name
                + ". OnHand: " + getOnHandQuantity(sku));
    }

    public synchronized boolean removeProduct(String sku, int quantityToRemove) {
        if (sku == null || sku.trim().isEmpty()) throw new IllegalArgumentException("sku cannot be blank");
        if (quantityToRemove <= 0) throw new IllegalArgumentException("quantityToRemove must be > 0");

        Product product = products.get(sku);
        if (product == null) {
            System.out.println("Error: Product with SKU " + sku + " not found in " + name);
            return false;
        }

        int onHand = product.getQuantity();
        if (onHand < quantityToRemove) {
            System.out.println("Error: Insufficient inventory. Requested: " + quantityToRemove + ", OnHand: " + onHand);
            return false;
        }

        product.decreaseQuantity(quantityToRemove);

        System.out.println(quantityToRemove + " units of " + product.getName()
                + " (SKU: " + sku + ") removed from " + name
                + ". OnHand now: " + product.getQuantity());

        if (product.getQuantity() == 0) {
            products.remove(sku);
            System.out.println("Product " + product.getName() + " removed from inventory as on-hand is now zero.");
        }

        return true;
    }

    private static String requireNonBlank(String v, String field) {
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return v.trim();
    }
}

// ---------- Strategy ----------
interface ReplenishmentStrategy {
    void replenish(Product product);
}

final class JustInTimeStrategy implements ReplenishmentStrategy {
    @Override
    public void replenish(Product product) {
        System.out.println("Applying Just-In-Time replenishment for " + product.getName());
    }
}

final class BulkOrderStrategy implements ReplenishmentStrategy {
    @Override
    public void replenish(Product product) {
        System.out.println("Applying Bulk Order replenishment for " + product.getName());
    }
}

// ---------- Stock Transfer ----------
final class StockTransfer {
    private final String transferId;
    private final String fromWarehouse;
    private final String toWarehouse;
    private final String sku;
    private final int qty;
    private TransferStatus status;

    StockTransfer(String transferId, String fromWarehouse, String toWarehouse, String sku, int qty) {
        this.transferId = requireNonBlank(transferId, "transferId");
        this.fromWarehouse = requireNonBlank(fromWarehouse, "fromWarehouse");
        this.toWarehouse = requireNonBlank(toWarehouse, "toWarehouse");
        this.sku = requireNonBlank(sku, "sku");
        if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
        this.qty = qty;
        this.status = TransferStatus.INITIATED;
    }

    public String getTransferId() { return transferId; }
    public TransferStatus getStatus() { return status; }
    public void setStatus(TransferStatus status) { this.status = Objects.requireNonNull(status); }

    public String getFromWarehouse() { return fromWarehouse; }
    public String getToWarehouse() { return toWarehouse; }
    public String getSku() { return sku; }
    public int getQty() { return qty; }

    private static String requireNonBlank(String v, String field) {
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return v.trim();
    }
}

// ---------- Inventory Manager ----------
final class InventoryManager {
    private final List<Warehouse> warehouses = new ArrayList<>();
    private final ProductFactory productFactory;
    private ReplenishmentStrategy replenishmentStrategy;

    private final Map<String, StockTransfer> transfers = new HashMap<>(); // idempotent by transferId

    public InventoryManager(ProductFactory productFactory, ReplenishmentStrategy replenishmentStrategy) {
        this.productFactory = Objects.requireNonNull(productFactory, "productFactory");
        this.replenishmentStrategy = replenishmentStrategy;
    }

    public void setReplenishmentStrategy(ReplenishmentStrategy replenishmentStrategy) {
        this.replenishmentStrategy = replenishmentStrategy;
    }

    public void addWarehouse(Warehouse warehouse) {
        warehouses.add(Objects.requireNonNull(warehouse, "warehouse"));
    }

    public Product createProduct(ProductCategory category, String sku, String name, double price, int quantity, int threshold) {
        return productFactory.createProduct(category, sku, name, price, quantity, threshold);
    }

    public Warehouse getWarehouseByName(String name) {
        for (Warehouse w : warehouses) {
            if (w.getName().equalsIgnoreCase(name)) return w;
        }
        return null;
    }

    public Product getProductBySku(String sku) {
        for (Warehouse w : warehouses) {
            Product p = w.getProductBySku(sku);
            if (p != null) return p;
        }
        return null;
    }

    public void checkAndReplenish(String sku) {
        Product product = getProductBySku(sku);
        if (product == null) return;

        if (product.getQuantity() < product.getThreshold() && replenishmentStrategy != null) {
            replenishmentStrategy.replenish(product);
        }
    }

    public void performInventoryCheck() {
        if (replenishmentStrategy == null) return;

        for (Warehouse w : warehouses) {
            for (Product p : w.getAllProducts()) {
                if (p.getQuantity() < p.getThreshold()) {
                    replenishmentStrategy.replenish(p);
                }
            }
        }
    }

    // ---------- Stock Transfer (Atomic movement + status) ----------
    public StockTransfer transferStock(String transferId, String fromWh, String toWh, String sku, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");

        // Idempotent: return existing transfer if retried
        StockTransfer existing = transfers.get(transferId);
        if (existing != null) return existing;

        Warehouse from = requireWarehouse(fromWh);
        Warehouse to = requireWarehouse(toWh);

        StockTransfer t = new StockTransfer(transferId, fromWh, toWh, sku, qty);
        transfers.put(transferId, t);

        // Lock ordering to avoid deadlocks
        Warehouse first = from.getName().compareToIgnoreCase(to.getName()) <= 0 ? from : to;
        Warehouse second = (first == from) ? to : from;

        synchronized (first) {
            synchronized (second) {
                t.setStatus(TransferStatus.INITIATED);

                if (from.getAvailableQuantity(sku) < qty) {
                    System.out.println("Transfer failed: insufficient stock in source. sku=" + sku
                            + " qty=" + qty + " onHand=" + from.getOnHandQuantity(sku));
                    return t;
                }

                // capture source product before removal (might become 0 and removed)
                Product src = from.getProductBySku(sku);
                if (src == null) {
                    System.out.println("Transfer failed: sku not found in source: " + sku);
                    return t;
                }

                t.setStatus(TransferStatus.IN_TRANSIT);

                boolean removed = from.removeProduct(sku, qty);
                if (!removed) return t;

                Product dest = to.getProductBySku(sku);
                if (dest == null) {
                    Product cloned = productFactory.createProduct(
                            src.getCategory(), src.getSku(), src.getName(), src.getPrice(), 0, src.getThreshold()
                    );
                    to.addProduct(cloned, qty);
                } else {
                    to.addProduct(dest, qty);
                }

                t.setStatus(TransferStatus.COMPLETED);
                System.out.println("Transfer completed: " + transferId + " status=" + t.getStatus());
                return t;
            }
        }
    }

    public TransferStatus getTransferStatus(String transferId) {
        StockTransfer t = transfers.get(transferId);
        return t == null ? null : t.getStatus();
    }

    private Warehouse requireWarehouse(String name) {
        Warehouse w = getWarehouseByName(name);
        if (w == null) throw new IllegalArgumentException("Warehouse not found: " + name);
        return w;
    }
}

// ---------- Demo ----------
public class Main {
    public static void main(String[] args) {
        ProductFactory factory = new ProductFactory();
        InventoryManager manager = new InventoryManager(factory, new JustInTimeStrategy());

        Warehouse w1 = new Warehouse("Warehouse 1");
        Warehouse w2 = new Warehouse("Warehouse 2");
        manager.addWarehouse(w1);
        manager.addWarehouse(w2);

        Product laptop = manager.createProduct(ProductCategory.ELECTRONICS, "SKU123", "Laptop", 1000.0, 0, 25);
        Product tshirt = manager.createProduct(ProductCategory.CLOTHING, "SKU456", "T-Shirt", 20.0, 0, 100);

        w1.addProduct(laptop, 15);
        w1.addProduct(tshirt, 20);

        manager.performInventoryCheck();

        System.out.println("\n--- TRANSFER STOCK (atomic + status) ---");
        StockTransfer t = manager.transferStock("T1", "Warehouse 1", "Warehouse 2", "SKU456", 5);
        System.out.println("Transfer " + t.getTransferId() + " status=" + t.getStatus());
    }
}
