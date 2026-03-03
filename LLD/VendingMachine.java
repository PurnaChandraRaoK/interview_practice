public class VendingMachineDemo {

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        VendingMachine vendingMachine = VendingMachine.getInstance();

        // Add products to the inventory
        Product coke = vendingMachine.addProduct("Coke", new BigDecimal("1.50"), 3);
        Product pepsi = vendingMachine.addProduct("Pepsi", new BigDecimal("1.50"), 2);
        Product water = vendingMachine.addProduct("Water", new BigDecimal("1.00"), 5);

        // Select a product
        vendingMachine.selectProduct(coke);

        // Insert coins
        vendingMachine.insertCoin(Coin.QUARTER);
        vendingMachine.insertCoin(Coin.QUARTER);
        vendingMachine.insertCoin(Coin.QUARTER);
        vendingMachine.insertCoin(Coin.QUARTER);

        // Insert a note
        vendingMachine.insertNote(Note.FIVE);

        // Dispense the product
        vendingMachine.dispenseProduct();

        // Return change
        vendingMachine.returnChange();

        // Select another product
        vendingMachine.selectProduct(pepsi);

        // Insert insufficient payment
        vendingMachine.insertCoin(Coin.QUARTER);

        // Try to dispense the product
        vendingMachine.dispenseProduct();

        // Insert more coins
        vendingMachine.insertCoin(Coin.QUARTER);
        vendingMachine.insertCoin(Coin.QUARTER);
        vendingMachine.insertCoin(Coin.QUARTER);
        vendingMachine.insertCoin(Coin.QUARTER);

        // Dispense the product
        vendingMachine.dispenseProduct();

        // Return change
        vendingMachine.returnChange();
    }
}

enum Coin {
    PENNY("0.01"),
    NICKEL("0.05"),
    DIME("0.10"),
    QUARTER("0.25");

    private final BigDecimal value;

    Coin(String value) {
        this.value = new BigDecimal(value);
    }

    public BigDecimal getValue() {
        return value;
    }
}

enum Note {
    ONE("1.00"),
    FIVE("5.00"),
    TEN("10.00"),
    TWENTY("20.00");

    private final BigDecimal value;

    Note(String value) {
        this.value = new BigDecimal(value);
    }

    public BigDecimal getValue() {
        return value;
    }
}

interface VendingMachineState {
    void selectProduct(Product product);

    void insertCoin(Coin coin);

    void insertNote(Note note);

    void dispenseProduct();

    void returnChange();
}

class IdleState implements VendingMachineState {
    private final VendingMachine vendingMachine;

    public IdleState(VendingMachine vendingMachine) {
        this.vendingMachine = vendingMachine;
    }

    @Override
    public void selectProduct(Product product) {
        if (product == null) {
            System.out.println("Invalid product selection.");
            return;
        }

        if (vendingMachine.getInventory().isAvailable(product)) {
            System.out.println("Product selected: " + product.getName());
            vendingMachine.setSelectedProduct(product);
            vendingMachine.setState(vendingMachine.getReadyState());
        } else {
            System.out.println("Product not available: " + product.getName());
        }
    }

    @Override
    public void insertCoin(Coin coin) {
        System.out.println("Please select a product first.");
    }

    @Override
    public void insertNote(Note note) {
        System.out.println("Please select a product first.");
    }

    @Override
    public void dispenseProduct() {
        System.out.println("Please select a product and make payment.");
    }

    @Override
    public void returnChange() {
        System.out.println("No change to return.");
    }
}

class ReadyState implements VendingMachineState {
    private final VendingMachine vendingMachine;

    public ReadyState(VendingMachine vendingMachine) {
        this.vendingMachine = vendingMachine;
    }

    @Override
    public void selectProduct(Product product) {
        System.out.println("Product already selected. Please make payment.");
    }

    @Override
    public void insertCoin(Coin coin) {
        if (coin == null) {
            System.out.println("Invalid coin.");
            return;
        }

        vendingMachine.addPayment(coin.getValue());
        System.out.println("Coin inserted: " + coin);
        checkPaymentStatus();
    }

    @Override
    public void insertNote(Note note) {
        if (note == null) {
            System.out.println("Invalid note.");
            return;
        }

        vendingMachine.addPayment(note.getValue());
        System.out.println("Note inserted: " + note);
        checkPaymentStatus();
    }

    @Override
    public void dispenseProduct() {
        System.out.println("Please make payment first.");
    }

    @Override
    public void returnChange() {
        System.out.println("Please make payment first.");
    }

    private void checkPaymentStatus() {
        Product selected = vendingMachine.getSelectedProduct();
        if (selected == null) {
            // Defensive: should not happen, but avoid NPE.
            System.out.println("No product selected. Resetting state.");
            vendingMachine.resetSelectedProduct();
            vendingMachine.resetPayment();
            vendingMachine.setState(vendingMachine.getIdleState());
            return;
        }

        if (vendingMachine.getTotalPayment().compareTo(selected.getPrice()) >= 0) {
            vendingMachine.setState(vendingMachine.getDispenseState());
        }
    }
}

class DispenseState implements VendingMachineState {
    private final VendingMachine vendingMachine;

    public DispenseState(VendingMachine vendingMachine) {
        this.vendingMachine = vendingMachine;
    }

    @Override
    public void selectProduct(Product product) {
        System.out.println("Product already selected. Please collect the dispensed product.");
    }

    @Override
    public void insertCoin(Coin coin) {
        System.out.println("Payment already made. Please collect the dispensed product.");
    }

    @Override
    public void insertNote(Note note) {
        System.out.println("Payment already made. Please collect the dispensed product.");
    }

    @Override
    public void dispenseProduct() {
        Product product = vendingMachine.getSelectedProduct();
        if (product == null) {
            System.out.println("No product selected. Cannot dispense.");
            vendingMachine.setState(vendingMachine.getIdleState());
            return;
        }

        // Defensive check: inventory might have changed
        if (!vendingMachine.getInventory().isAvailable(product)) {
            System.out.println("Product went out of stock: " + product.getName());
            System.out.println("Returning full payment: $" + vendingMachine.getTotalPayment());
            vendingMachine.setState(vendingMachine.getReturnChangeState());
            return;
        }

        vendingMachine.getInventory().updateQuantity(product,
                vendingMachine.getInventory().getQuantity(product) - 1);

        System.out.println("Product dispensed: " + product.getName());
        vendingMachine.setState(vendingMachine.getReturnChangeState());
    }

    @Override
    public void returnChange() {
        System.out.println("Please collect the dispensed product first.");
    }
}

class ReturnChangeState implements VendingMachineState {
    private final VendingMachine vendingMachine;

    public ReturnChangeState(VendingMachine vendingMachine) {
        this.vendingMachine = vendingMachine;
    }

    @Override
    public void selectProduct(Product product) {
        System.out.println("Please collect the change first.");
    }

    @Override
    public void insertCoin(Coin coin) {
        System.out.println("Please collect the change first.");
    }

    @Override
    public void insertNote(Note note) {
        System.out.println("Please collect the change first.");
    }

    @Override
    public void dispenseProduct() {
        System.out.println("Product already dispensed. Please collect the change.");
    }

    @Override
    public void returnChange() {
        Product selected = vendingMachine.getSelectedProduct();
        if (selected == null) {
            System.out.println("No product selected. Nothing to return.");
            vendingMachine.resetPayment();
            vendingMachine.setState(vendingMachine.getIdleState());
            return;
        }

        BigDecimal change = vendingMachine.getTotalPayment().subtract(selected.getPrice());
        change = change.setScale(2, RoundingMode.HALF_UP);

        if (change.compareTo(BigDecimal.ZERO) > 0) {
            System.out.println("Change returned: $" + change);
        } else {
            System.out.println("No change to return.");
        }

        vendingMachine.resetPayment();
        vendingMachine.resetSelectedProduct();
        vendingMachine.setState(vendingMachine.getIdleState());
    }
}

class Inventory {
    private final Map<Product, Integer> products = new ConcurrentHashMap<>();

    public void addProduct(Product product, int quantity) {
        if (product == null || quantity <= 0) return;
        products.merge(product, quantity, Integer::sum);
    }

    public void removeProduct(Product product) {
        if (product == null) return;
        products.remove(product);
    }

    public void updateQuantity(Product product, int quantity) {
        if (product == null) return;

        if (quantity <= 0) {
            products.remove(product);
        } else {
            products.put(product, quantity);
        }
    }

    public int getQuantity(Product product) {
        if (product == null) return 0;
        return products.getOrDefault(product, 0);
    }

    public boolean isAvailable(Product product) {
        return getQuantity(product) > 0;
    }
}

final class Product {
    private final String name;
    private final BigDecimal price;

    public Product(String name, BigDecimal price) {
        this.name = Objects.requireNonNull(name, "name");
        this.price = Objects.requireNonNull(price, "price").setScale(2, RoundingMode.HALF_UP);
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    // Important because Product is used as a Map key in Inventory
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product)) return false;
        Product product = (Product) o;
        return name.equals(product.name) && price.equals(product.price);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, price);
    }
}

class VendingMachine {
    private static VendingMachine instance;

    private final Inventory inventory;

    private final VendingMachineState idleState;
    private final VendingMachineState readyState;
    private final VendingMachineState dispenseState;
    private final VendingMachineState returnChangeState;

    private VendingMachineState currentState;
    private Product selectedProduct;
    private BigDecimal totalPayment;

    private VendingMachine() {
        this.inventory = new Inventory();
        this.idleState = new IdleState(this);
        this.readyState = new ReadyState(this);
        this.dispenseState = new DispenseState(this);
        this.returnChangeState = new ReturnChangeState(this);

        this.currentState = idleState;
        this.selectedProduct = null;
        this.totalPayment = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public static synchronized VendingMachine getInstance() {
        if (instance == null) {
            instance = new VendingMachine();
        }
        return instance;
    }

    public Product addProduct(String name, BigDecimal price, int quantity) {
        Product product = new Product(name, price);
        inventory.addProduct(product, quantity);
        return product;
    }

    public void selectProduct(Product product) {
        currentState.selectProduct(product);
    }

    public void insertCoin(Coin coin) {
        currentState.insertCoin(coin);
    }

    public void insertNote(Note note) {
        currentState.insertNote(note);
    }

    public void dispenseProduct() {
        currentState.dispenseProduct();
    }

    public void returnChange() {
        currentState.returnChange();
    }

    void setState(VendingMachineState state) {
        this.currentState = state;
    }

    Inventory getInventory() {
        return inventory;
    }

    VendingMachineState getIdleState() {
        return idleState;
    }

    VendingMachineState getReadyState() {
        return readyState;
    }

    VendingMachineState getDispenseState() {
        return dispenseState;
    }

    VendingMachineState getReturnChangeState() {
        return returnChangeState;
    }

    Product getSelectedProduct() {
        return selectedProduct;
    }

    void setSelectedProduct(Product product) {
        this.selectedProduct = product;
    }

    void resetSelectedProduct() {
        this.selectedProduct = null;
    }

    BigDecimal getTotalPayment() {
        return totalPayment;
    }

    void addPayment(BigDecimal amount) {
        if (amount == null) return;
        this.totalPayment = this.totalPayment.add(amount).setScale(2, RoundingMode.HALF_UP);
    }

    void resetPayment() {
        this.totalPayment = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
