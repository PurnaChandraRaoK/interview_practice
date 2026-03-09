// ---------- Enums ----------
enum ProductCategory {
    ELECTRONICS,
    CLOTHING,
    GROCERY,
    FURNITURE,
    OTHER
}

// ---------- Model ----------
abstract class Product {
    private final String sku;
    private final String name;
    private final double price;
    private int quantity;          // warehouse-specific quantity (mutated by Warehouse)
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
    public int getQuantity() { return quantity; }
    public int getThreshold() { return threshold; }
    public ProductCategory getCategory() { return category; }

    // Keep mutation controlled
    void increaseQuantity(int delta) {
        if (delta <= 0) throw new IllegalArgumentException("delta must be > 0");
        quantity += delta;
    }

    void decreaseQuantity(int delta) {
        if (delta <= 0) throw new IllegalArgumentException("delta must be > 0");
        if (quantity < delta) throw new IllegalArgumentException("insufficient quantity");
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
        this.expiryDate = expiryDate; // can be null if not applicable
        this.refrigerated = refrigerated;
    }

    public LocalDate getExpiryDate() { return expiryDate; }
    public boolean isRefrigerated() { return refrigerated; }
}

// ---------- Factory ----------
final class ProductFactory {

    // Keep your factory pattern, but avoid switch explosion by keeping it simple.
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
    private final Map<String, Product> products = new HashMap<>(); // SKU -> Product

    public Warehouse(String name) {
        this.name = requireNonBlank(name, "name");
    }

    public String getName() { return name; }
    public String getLocation() { return location; }

    public void setLocation(String location) {
        this.location = location;
    }

    // Add quantity to warehouse inventory
    public void addProduct(Product product, int quantityToAdd) {
        Objects.requireNonNull(product, "product");
        if (quantityToAdd <= 0) throw new IllegalArgumentException("quantityToAdd must be > 0");

        String sku = product.getSku();
        Product existing = products.get(sku);

        if (existing != null) {
            existing.increaseQuantity(quantityToAdd);
        } else {
            // store the provided product instance in this warehouse
            product.increaseQuantity(quantityToAdd); // aligns with your existing behavior (sets/updates)
            products.put(sku, product);
        }

        System.out.println(quantityToAdd + " units of " + product.getName()
                + " (SKU: " + sku + ") added to " + name
                + ". New quantity: " + getAvailableQuantity(sku));
    }

    public boolean removeProduct(String sku, int quantityToRemove) {
        if (sku == null || sku.trim().isEmpty()) throw new IllegalArgumentException("sku cannot be blank");
        if (quantityToRemove <= 0) throw new IllegalArgumentException("quantityToRemove must be > 0");

        Product product = products.get(sku);
        if (product == null) {
            System.out.println("Error: Product with SKU " + sku + " not found in " + name);
            return false;
        }

        int currentQty = product.getQuantity();
        if (currentQty < quantityToRemove) {
            System.out.println("Error: Insufficient inventory. Requested: " + quantityToRemove + ", Available: " + currentQty);
            return false;
        }

        product.decreaseQuantity(quantityToRemove);
        System.out.println(quantityToRemove + " units of " + product.getName()
                + " (SKU: " + sku + ") removed from " + name
                + ". Remaining quantity: " + product.getQuantity());

        if (product.getQuantity() == 0) {
            products.remove(sku);
            System.out.println("Product " + product.getName() + " removed from inventory as quantity is now zero.");
        }

        return true;
    }

    public int getAvailableQuantity(String sku) {
        Product p = products.get(sku);
        return p == null ? 0 : p.getQuantity();
    }

    public Product getProductBySku(String sku) {
        return products.get(sku);
    }

    public Collection<Product> getAllProducts() {
        return Collections.unmodifiableCollection(products.values());
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
        // Keep empty for interview (plug real logic later)
    }
}

final class BulkOrderStrategy implements ReplenishmentStrategy {
    @Override
    public void replenish(Product product) {
        System.out.println("Applying Bulk Order replenishment for " + product.getName());
        // Keep empty for interview (plug real logic later)
    }
}

// ---------- Inventory Manager (no Singleton) ----------
final class InventoryManager {
    private final List<Warehouse> warehouses = new ArrayList<>();
    private final ProductFactory productFactory;
    private ReplenishmentStrategy replenishmentStrategy;

    public InventoryManager(ProductFactory productFactory, ReplenishmentStrategy replenishmentStrategy) {
        this.productFactory = Objects.requireNonNull(productFactory, "productFactory");
        this.replenishmentStrategy = replenishmentStrategy; // can be null
    }

    public void setReplenishmentStrategy(ReplenishmentStrategy replenishmentStrategy) {
        this.replenishmentStrategy = replenishmentStrategy;
    }

    public void addWarehouse(Warehouse warehouse) {
        warehouses.add(Objects.requireNonNull(warehouse, "warehouse"));
    }

    public void removeWarehouse(Warehouse warehouse) {
        warehouses.remove(warehouse);
    }

    public Product createProduct(ProductCategory category, String sku, String name, double price, int quantity, int threshold) {
        return productFactory.createProduct(category, sku, name, price, quantity, threshold);
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
        Product apple  = manager.createProduct(ProductCategory.GROCERY, "SKU789", "Apple", 1.0, 0, 200);

        w1.addProduct(laptop, 15);
        w1.addProduct(tshirt, 20);
        w2.addProduct(apple, 50);

        manager.performInventoryCheck();

        manager.setReplenishmentStrategy(new BulkOrderStrategy());
        manager.checkAndReplenish("SKU123");
    }
}
