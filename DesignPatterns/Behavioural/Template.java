// making beverages. Whether it’s coffee ☕ or tea 🍵, the process 
// is similar—you boil water, brew the drink, pour it into a cup, and
//  add your favorite condiments. Initially, you might write separate 
//  code for coffee and tea that almost looks identical except for a few 
//  steps. Sound familiar? Let’s see how that might look.

// Solution

// Our abstract template that defines the skeleton of beverage preparation
abstract class Beverage {
  // The template method - makes sure the algorithm steps are followed
  final void prepareRecipe() {
    boilWater();
    brew();
    pourInCup();
    addCondiments();
  }
  // Common methods
  void boilWater() {
    System.out.println("Boiling water...");
  }
  void pourInCup() {
    System.out.println("Pouring into cup...");
  }
  // Steps to be customized by subclasses
  abstract void brew();
  abstract void addCondiments();
}

// Concrete implementation for Coffee
class CoffeeBeverage extends Beverage {
  @Override
  void brew() {
    System.out.println("Brewing coffee...");
  }
  @Override
  void addCondiments() {
    System.out.println("Adding sugar and milk...");
  }
}

// Concrete implementation for Tea
class TeaBeverage extends Beverage {
  @Override
  void brew() {
    System.out.println("Steeping tea bag...");
  }
  @Override
  void addCondiments() {
    System.out.println("Adding lemon...");
  }
}

public class BeverageTemplateDemo {
  public static void main(String[] args) {
    Beverage coffee = new CoffeeBeverage();
    Beverage tea = new TeaBeverage();
    System.out.println("Making coffee...");
    coffee.prepareRecipe();
    System.out.println("nMaking tea...");
    tea.prepareRecipe();
  }
}

abstract class BeverageWithHook {
  // The template method with a hook
  final void prepareRecipe() {
    boilWater();
    brew();
    pourInCup();
    // Only add condiments if the customer wants them
    if (customerWantsCondiments()) {
      addCondiments();
    }
  }
  void boilWater() {
    System.out.println("Boiling water...");
  }
  void pourInCup() {
    System.out.println("Pouring into cup...");
  }
  abstract void brew();
  abstract void addCondiments();
  // Hook method with default behavior
  boolean customerWantsCondiments() {
    return true;
  }
}

class CustomCoffee extends BeverageWithHook {
  @Override
  void brew() {
    System.out.println("Brewing coffee...");
  }
  @Override
  void addCondiments() {
    System.out.println("Adding sugar and milk...");
  }
  // Suppose this customer doesn't want condiments
  @Override
  boolean customerWantsCondiments() {
    return false;
  }
}
public class BeverageWithHookDemo {
  public static void main(String[] args) {
    BeverageWithHook coffee = new CustomCoffee();
    System.out.println("Making custom coffee...");
    coffee.prepareRecipe();
  }
}

