// Problem Statement: Extending Functionality Without Modifying the Core Code 
// Imagine you’re designing a coffee shop ordering system. 
// The system needs to manage various coffee orders and their customizations.
//  Customers can start with a basic coffee (e.g., Espresso, Cappuccino) 
//  and then add multiple customizations like milk, sugar, cream, or flavors (e.g., vanilla, hazelnut).

// The Problem: Each coffee type and customization combination 
// would require a new class if we follow a traditional inheritance-based approach. 
// For example, you’d need separate classes for “EspressoWithMilk”, “CappuccinoWithVanilla”, or “LatteWithMilkAndSugar”.
//  This quickly becomes unmanageable as the number of combinations grows.

// The Challenge: How can you dynamically add new functionalities (customizations)
//  to objects without altering their code or creating a complex class hierarchy?

// Solution

// Coffee.java - Common interface for all coffee types
public interface Coffee {
    String getDescription();
    double getCost();
}

public class Espresso implements Coffee {
    @Override
    public String getDescription() {
        return "Espresso";
    }
    @Override
    public double getCost() {
        return 2.00;
    }
}

public class Cappuccino implements Coffee {
    @Override
    public String getDescription() {
        return "Cappuccino";
    }
    @Override
    public double getCost() {
        return 3.00;
    }
}

public abstract class CoffeeDecorator implements Coffee {
    protected Coffee coffee;
    public CoffeeDecorator(Coffee coffee) {
        this.coffee = coffee;
    }
    @Override
    public String getDescription() {
        return coffee.getDescription();
    }
    @Override
    public double getCost() {
        return coffee.getCost();
    }
}

public class MilkDecorator extends CoffeeDecorator {
    public MilkDecorator(Coffee coffee) {
        super(coffee);
    }
    @Override
    public String getDescription() {
        return coffee.getDescription() + ", Milk";
    }
    @Override
    public double getCost() {
        return coffee.getCost() + 0.50;
    }
}

public class SugarDecorator extends CoffeeDecorator {
    public SugarDecorator(Coffee coffee) {
        super(coffee);
    }
    @Override
    public String getDescription() {
        return coffee.getDescription() + ", Sugar";
    }
    @Override
    public double getCost() {
        return coffee.getCost() + 0.25;
    }
}

public class VanillaDecorator extends CoffeeDecorator {
    public VanillaDecorator(Coffee coffee) {
        super(coffee);
    }
    @Override
    public String getDescription() {
        return coffee.getDescription() + ", Vanilla";
    }
    @Override
    public double getCost() {
        return coffee.getCost() + 0.75;
    }
}

public class CoffeeShop {
    public static void main(String[] args) {
        Coffee coffee = new Espresso();
        coffee = new MilkDecorator(coffee);
        coffee = new SugarDecorator(coffee);
        System.out.println("Order: " + coffee.getDescription());
        System.out.println("Total Cost: $" + coffee.getCost());
        Coffee anotherCoffee = new Cappuccino();
        anotherCoffee = new VanillaDecorator(anotherCoffee);
        System.out.println("nOrder: " + anotherCoffee.getDescription());
        System.out.println("Total Cost: $" + anotherCoffee.getCost());
    }
}