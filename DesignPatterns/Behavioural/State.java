// Consider a traffic light system. A traffic light can be in one of three states:

// • Red: Cars must stop.

// • Green: Cars can go.

// • Yellow: Cars should slow down and prepare to stop.

// Each state dictates different behaviors and transitions. Managing these states efficiently 
// in code ensures that our traffic light system remains scalable and easy to maintain.

// Problem

public class TrafficLight {
  private String color;
  public TrafficLight() {
    this.color = "RED";
  }
  public void next() {
    if (color.equals("RED")) {
      color = "GREEN";
      System.out.println("Change to GREEN. Cars go!");
    } else if (color.equals("GREEN")) {
      color = "YELLOW";
      System.out.println("Change to YELLOW. Slow down!");
    } else if (color.equals("YELLOW")) {
      color = "RED";
      System.out.println("Change to RED. Stop!");
    } else if (color.equals("BLINKING")) {
      color = "MAINTENANCE";
      System.out.println("Switching to MAINTENANCE mode...");
    } else if (color.equals("MAINTENANCE")) {
      color = "RED";
      System.out.println("Maintenance done, back to RED!");
    }
    // Potentially more states and conditions...
  }
  public String getColor() {
    return color;
  }
}

// Solution

// State Interface
interface TrafficLightState {
    void next(TrafficLightContext context);
    String getColor();
}

// Concrete State: Red
class RedState implements TrafficLightState {
  @Override
  public void next(TrafficLightContext context) {
    System.out.println("Switching from RED to GREEN. Cars go!");
    context.setState(new GreenState());
  }
  @Override
  public String getColor() {
    return "RED";
  }
}

// Concrete State: Green
class GreenState implements TrafficLightState {
  @Override
  public void next(TrafficLightContext context) {
    System.out.println("Switching from GREEN to YELLOW. Slow down!");
    context.setState(new YellowState());
  }
  @Override
  public String getColor() {
    return "GREEN";
  }
}

// Concrete State: Yellow
class YellowState implements TrafficLightState {
  @Override
  public void next(TrafficLightContext context) {
    System.out.println("Switching from YELLOW to RED. Stop!");
    context.setState(new RedState());
  }
  @Override
  public String getColor() {
    return "YELLOW";
  }
}

// Context Class
class TrafficLightContext {
  private TrafficLightState currentState;
  public TrafficLightContext() {
    currentState = new RedState(); // Start with RED
  }
  public void setState(TrafficLightState state) {
    this.currentState = state;
  }
  public void next() {
    currentState.next(this);
  }
  public String getColor() {
    return currentState.getColor();
  }
}

// Driver Class
public class TrafficLightTest {
  public static void main(String[] args) {
    TrafficLightContext trafficLight = new TrafficLightContext();
    trafficLight.next(); // RED -> GREEN
    trafficLight.next(); // GREEN -> YELLOW
    trafficLight.next(); // YELLOW -> RED
    trafficLight.next(); // RED -> GREEN
    // Adding new states like BLINKING or MAINTENANCE is easy now
  }
}