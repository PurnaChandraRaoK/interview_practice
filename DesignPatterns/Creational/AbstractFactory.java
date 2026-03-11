// Vehicle.java
public interface Vehicle {
    void start();
    void stop();
}

// Electric vehicles
public class ElectricCar implements Vehicle {
    @Override
    public void start() { System.out.println("Electric Car is starting silently..."); }

    @Override
    public void stop() { System.out.println("Electric Car is stopping..."); }
}

public class ElectricTruck implements Vehicle {
    @Override
    public void start() { System.out.println("Electric Truck is starting with high torque..."); }

    @Override
    public void stop() { System.out.println("Electric Truck is stopping..."); }
}

public class ElectricBike implements Vehicle {
    @Override
    public void start() { System.out.println("Electric Bike is starting with a whirr..."); }

    @Override
    public void stop() { System.out.println("Electric Bike is stopping..."); }
}

// Fuel vehicles
public class FuelCar implements Vehicle {
    @Override
    public void start() { System.out.println("Fuel Car is starting with ignition..."); }

    @Override
    public void stop() { System.out.println("Fuel Car is stopping..."); }
}

public class FuelTruck implements Vehicle {
    @Override
    public void start() { System.out.println("Fuel Truck is starting with a roar..."); }

    @Override
    public void stop() { System.out.println("Fuel Truck is stopping..."); }
}

public class FuelBike implements Vehicle {
    @Override
    public void start() { System.out.println("Fuel Bike is starting with a rev..."); }

    @Override
    public void stop() { System.out.println("Fuel Bike is stopping..."); }
}

// VehicleAbstractFactory.java
public interface VehicleAbstractFactory {
    Vehicle createCar();
    Vehicle createTruck();
    Vehicle createBike();
}

// ElectricVehicleFactory.java
public class ElectricVehicleFactory implements VehicleAbstractFactory {
    @Override
    public Vehicle createCar() { return new ElectricCar(); }

    @Override
    public Vehicle createTruck() { return new ElectricTruck(); }

    @Override
    public Vehicle createBike() { return new ElectricBike(); }
}

// FuelVehicleFactory.java
public class FuelVehicleFactory implements VehicleAbstractFactory {
    @Override
    public Vehicle createCar() { return new FuelCar(); }

    @Override
    public Vehicle createTruck() { return new FuelTruck(); }

    @Override
    public Vehicle createBike() { return new FuelBike(); }
}

// VehicleFamily.java
public enum VehicleFamily {
    ELECTRIC,
    FUEL
}

// FactoryProvider.java
public class FactoryProvider {
    private FactoryProvider() { } // prevent instantiation

    public static VehicleAbstractFactory getFactory(VehicleFamily family) {
        return switch (family) {
            case ELECTRIC -> new ElectricVehicleFactory();
            case FUEL -> new FuelVehicleFactory();
        };
    }
}

// Main.java
public class Main {
    public static void main(String[] args) {

        // Choose ELECTRIC family
        VehicleAbstractFactory electricFactory =
                FactoryProvider.getFactory(VehicleFamily.ELECTRIC);

        Vehicle eCar = electricFactory.createCar();
        eCar.start();
        eCar.stop();

        Vehicle eTruck = electricFactory.createTruck();
        eTruck.start();
        eTruck.stop();

        // Choose FUEL family
        VehicleAbstractFactory fuelFactory =
                FactoryProvider.getFactory(VehicleFamily.FUEL);

        Vehicle fBike = fuelFactory.createBike();
        fBike.start();
        fBike.stop();
    }
}