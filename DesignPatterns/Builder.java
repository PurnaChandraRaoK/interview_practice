public class Car {
  private String engine;
  private int wheels;
  private int seats;
  private String color;
  private boolean sunroof;
  private boolean navigationSystem;
  // Car constructor should be private, ensuring it's only created through the

  // builder
  private Car(CarBuilder builder) {
    this.engine = builder.engine;
    this.wheels = builder.wheels;
    this.seats = builder.seats;
    this.color = builder.color;
    this.sunroof = builder.sunroof;
    this.navigationSystem = builder.navigationSystem;
  }

  // Getter methods for the fields
  public String getEngine() {
    return engine;
  }
  public int getWheels() {
    return wheels;
  }
  public int getSeats() {
    return seats;
  }
  public String getColor() {
    return color;
  }
  public boolean hasSunroof() {
    return sunroof;
  }
  public boolean hasNavigationSystem() {
    return navigationSystem;
  }
  @Override
  public String toString() {
    return "Car [engine=" + engine + ", wheels=" + wheels + ", seats=" + seats
        + ", color=" + color + ", sunroof=" + sunroof
        + ", navigationSystem=" + navigationSystem + "]";
  }

  // CarBuilder nested class
  public static class CarBuilder {
    private String engine;
    private int wheels = 4; // Default value
    private int seats = 5; // Default value
    private String color = "Black"; // Default value
    private boolean sunroof = false; // Default value
    private boolean navigationSystem = false; // Default value

    // Builder methods to set attributes
    public CarBuilder setEngine(String engine) {
      this.engine = engine;
      return this;
    }
    public CarBuilder setWheels(int wheels) {
      this.wheels = wheels;
      return this;
    }
    public CarBuilder setSeats(int seats) {
      this.seats = seats;
      return this;
    }
    public CarBuilder setColor(String color) {
      this.color = color;
      return this;
    }
    public CarBuilder setSunroof(boolean sunroof) {
      this.sunroof = sunroof;
      return this;
    }
    public CarBuilder setNavigationSystem(boolean navigationSystem) {
      this.navigationSystem = navigationSystem;
      return this;
    }

    // Build method to create a Car object
    public Car build() {
      return new Car(
          this); // Return a new Car created using the builder's values
    }
  }
}

public class Main {
  public static void main(String[] args) {
    // Creating a car using the Builder pattern
    Car.CarBuilder builder = new Car.CarBuilder();
    Car car1 = builder.setEngine("V8")
                   .setColor("Red")
                   .setSeats(5)
                   .setSunroof(true)
                   .build(); // The build method returns the final product
    System.out.println(car1);

    // Creating another car with different specifications
    Car car2 = builder.setEngine("V6")
                   .setColor("Blue")
                   .setSeats(4)
                   .build(); // Sunroof and Navigation are default
    System.out.println(car2);
  }
}