// Problem: Managing Hierarchies
// Imagine you’re designing a smart home system. Your goal is to control various
//  devices such as rooms and individual appliances within a smart home.


// For example:

// • A Room contains multiple devices like air conditioners, lights, and coffee machines.

// • A Floor may contain multiple rooms.

// • The House may consist of multiple floors.

// You want your SmartHomeController to control devices at any level 
// whether it’s an individual light, an entire room, or the whole house.

public class SmartHomeController {
  public static void main(String[] args) {
    // Manually managing devices and groups
    AirConditioner airConditioner = new AirConditioner();
    SmartLight smartLight = new SmartLight();
    System.out.println("Turning ON devices in Room 1...");
    airConditioner.turnOn();
    smartLight.turnOn();
    System.out.println("Turning OFF devices in Room 1...");
    airConditioner.turnOff();
    smartLight.turnOff();
    // Manually managing multiple rooms
    System.out.println("Turning ON devices in Floor 1...");
    airConditioner.turnOn();
    smartLight.turnOn();
    airConditioner.turnOn(); // Room 2
    smartLight.turnOn(); // Room 2
    System.out.println("Turning OFF devices in Floor 2...");
    airConditioner.turnOff();
    smartLight.turnOff();
    airConditioner.turnOff(); // Room 3
    smartLight.turnOff(); // Room 3
    System.out.println("Turning ON all devices in the house...");
    airConditioner.turnOn();
    smartLight.turnOn();
    // Add more logic as you scale...
  }
}

// Solution

// SmartComponent.java - Common interface for all components
public interface SmartComponent {
    void turnOn();  // Turn on the component
    void turnOff(); // Turn off the component
}

// AirConditioner.java
public class AirConditioner implements SmartComponent {
  @Override
  public void turnOn() {
    System.out.println("Air Conditioner is now ON.");
  }
  @Override
  public void turnOff() {
    System.out.println("Air Conditioner is now OFF.");
  }
}

// SmartLight.java
public class SmartLight implements SmartComponent {
  @Override
  public void turnOn() {
    System.out.println("Smart Light is now ON.");
  }
  @Override
  public void turnOff() {
    System.out.println("Smart Light is now OFF.");
  }
}

// Composite class for groups of components
public class CompositeSmartComponent implements SmartComponent {
  private List<SmartComponent> components = new ArrayList<>();
public void addComponent(SmartComponent component) {
    components.add(component);
  }
  public void removeComponent(SmartComponent component) {
    components.remove(component);
  }
  @Override
  public void turnOn() {
    for (SmartComponent component : components) {
      component.turnOn();
    }
  }
  @Override
  public void turnOff() {
    for (SmartComponent component : components) {
      component.turnOff();
    }
  }
}

public class SmartHomeController {
  public static void main(String[] args) {
    // Create individual devices
    SmartComponent airConditioner = new AirConditioner();
    SmartComponent smartLight = new SmartLight();
    // Create a room and add devices
    CompositeSmartComponent room1 = new CompositeSmartComponent();
    room1.addComponent(airConditioner);
    room1.addComponent(smartLight);
    // Add more rooms for demonstration
    CompositeSmartComponent room2 = new CompositeSmartComponent();
    room2.addComponent(new AirConditioner());
    room2.addComponent(new SmartLight());
    // Create a floor and add rooms
    CompositeSmartComponent floor = new CompositeSmartComponent();
    floor.addComponent(room1);
    floor.addComponent(room2);
    // Create the house and add floors
    CompositeSmartComponent house = new CompositeSmartComponent();
    house.addComponent(floor);
    // Control the entire house
    System.out.println("Turning ON all devices in the house:");
    house.turnOn();
    System.out.println("nTurning OFF all devices in the house:");
    house.turnOff();
    // Control a single floor
    System.out.println("nTurning ON all devices on the first floor:");
    floor.turnOn();
    System.out.println("nTurning OFF all devices on the first floor:");
    floor.turnOff();
    // Control a single room
    System.out.println("nTurning ON all devices in Room 1:");
    room1.turnOn();
    System.out.println("nTurning OFF all devices in Room 1:");
    room1.turnOff();
  }
}