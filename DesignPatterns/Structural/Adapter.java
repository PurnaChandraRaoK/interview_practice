// The Problem: Each device uses a unique communication protocol, 
// and your app would become a mess if you hard-code the logic for each device. 
// It will be difficult to maintain and extend as more devices are added.

// The Challenge: How can you create a clean, scalable solution to connect all these devices?

public class SmartHomeController {
  public static void main(String[] args) {
    String deviceType = "SmartLight"; // Imagine this value is dynamic
    if (deviceType.equals("AirConditioner")) {
      AirConditioner airConditioner = new AirConditioner();
      airConditioner.connectViaBluetooth();
      airConditioner.startCooling();
    } else if (deviceType.equals("SmartLight")) {
      SmartLight smartLight = new SmartLight();
      smartLight.connectToWiFi();
      smartLight.switchOn();
    } else if (deviceType.equals("CoffeeMachine")) {
      CoffeeMachine coffeeMachine = new CoffeeMachine();
      coffeeMachine.initializeZigbeeConnection();
      coffeeMachine.startBrewing();
    } else {
      System.out.println("Device type not supported!");
    }
  }
}

// Solution


// Adapter for Air Conditioner
public class AirConditionerAdapter implements SmartDevice {
  private AirConditioner airConditioner;
  // Constructor
  public AirConditionerAdapter(AirConditioner airConditioner) {
    this.airConditioner = airConditioner;
  }

  @Override
  public void turnOn() {
    airConditioner.connectViaBluetooth();
    airConditioner.startCooling();
  }

  @Override
  public void turnOff() {
    airConditioner.stopCooling();
    airConditioner.disconnectBluetooth();
  }
}

// Adapter for Smart Light
public class SmartLightAdapter implements SmartDevice {
  private SmartLight smartLight;
  public SmartLightAdapter(SmartLight smartLight) {
    this.smartLight = smartLight;
  }

  @Override
  public void turnOn() {
    smartLight.connectToWiFi();
    smartLight.switchOn();
  }

  @Override
  public void turnOff() {
    smartLight.switchOff();
    smartLight.disconnectWiFi();
  }
}

// Adapter for Coffee Machine
public class CoffeeMachineAdapter implements SmartDevice {
  private CoffeeMachine coffeeMachine;
  public CoffeeMachineAdapter(CoffeeMachine coffeeMachine) {
    this.coffeeMachine = coffeeMachine;
  }

  @Override
  public void turnOn() {
    coffeeMachine.initializeZigbeeConnection();
    coffeeMachine.startBrewing();
  }

  @Override
  public void turnOff() {
    coffeeMachine.stopBrewing();
    coffeeMachine.terminateZigbeeConnection();
  }
}

public class SmartHomeController {
  public static void main(String[] args) {
    // Create adapters for each device
    SmartDevice airConditioner =
        new AirConditionerAdapter(new AirConditioner());
    SmartDevice smartLight = new SmartLightAdapter(new SmartLight());
    SmartDevice coffeeMachine = new CoffeeMachineAdapter(new CoffeeMachine());
    // Control devices through the unified interface
    airConditioner.turnOn();
    smartLight.turnOn();
    coffeeMachine.turnOn();
    airConditioner.turnOff();
    smartLight.turnOff();
    coffeeMachine.turnOff();
  }
}

// SmartDevice.java - Common interface for all smart devices
public interface SmartDevice {
    void turnOn(); // method to turn on a specific Device
    void turnOff(); // method to turn off a specific Device
}

// AirConditioner.java - Device using Bluetooth for communication
public class AirConditioner {
  // Method to connect to the Air Conditioner via Bluetooth
  public void connectViaBluetooth() {
    System.out.println("Air Conditioner connected via Bluetooth.");
  }

  // Method to start the cooling process
  public void startCooling() {
    System.out.println("Air Conditioner is now cooling.");
  }

  // Method to stop the cooling process
  public void stopCooling() {
    System.out.println("Air Conditioner stopped cooling.");
  }

  // Method to disconnect Bluetooth connection
  public void disconnectBluetooth() {
    System.out.println("Air Conditioner disconnected from Bluetooth.");
  }
}

// SmartLight.java - Device using Wi-Fi for communication
public class SmartLight {
  // Method to connect the Smart Light to Wi-Fi
  public void connectToWiFi() {
    System.out.println("Smart Light connected to Wi-Fi.");
  }

  // Method to turn the Smart Light on
  public void switchOn() {
    System.out.println("Smart Light is now ON.");
  }

  // Method to turn the Smart Light off
  public void switchOff() {
    System.out.println("Smart Light is now OFF.");
  }

  // Method to disconnect Wi-Fi connection
  public void disconnectWiFi() {
    System.out.println("Smart Light disconnected from Wi-Fi.");
  }
}

// CoffeeMachine.java - Device using Zigbee for communication
public class CoffeeMachine {
  // Method to initialize the Zigbee connection
  public void initializeZigbeeConnection() {
    System.out.println("Coffee Machine connected via Zigbee.");
  }

  // Method to start brewing coffee
  public void startBrewing() {
    System.out.println("Coffee Machine is now brewing coffee.");
  }

  // Method to stop brewing coffee
  public void stopBrewing() {
    System.out.println("Coffee Machine stopped brewing coffee.");
  }

  // Method to terminate the Zigbee connection
  public void terminateZigbeeConnection() {
    System.out.println("Coffee Machine disconnected from Zigbee.");
  }
}
