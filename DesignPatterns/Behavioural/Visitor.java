// Visitor says remove the algorithm from object

// Problem

// in hospital, we have three kinds of patients
// ChildPatient, AdultPatient, and SeniorPatient. 
// Each patient type requires a tailored approach for tasks such as diagnosis and billing. 
// Traditionally, each patient class might handle its own operations. But as new operations arise
//  (say, a health report or medication calculation), you’d end up cluttering your classes with extra methods or endless if-else checks.

// Solution - Example 1

// The Visitor Design Pattern is like having a specialized doctor 
// who visits each patient and performs the necessary operation,
//  whether it’s diagnosing, billing, or generating a health report—without overloading 
// the patient with extra code. This pattern makes your system cleaner, easier to maintain,
//  and extremely flexible for future changes.

interface Patient {
    void accept(Visitor visitor);
}

// ChildPatient.java
class ChildPatient implements Patient {
  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}

// AdultPatient.java
class AdultPatient implements Patient {
  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}

// SeniorPatient.java
class SeniorPatient implements Patient {
  @Override
  public void accept(Visitor visitor) {
    visitor.visit(this);
  }
}

interface Visitor {
    void visit(ChildPatient childPatient);
    void visit(AdultPatient adultPatient);
    void visit(SeniorPatient seniorPatient);
}

class DiagnosisVisitor implements Visitor {
  @Override
  public void visit(ChildPatient childPatient) {
    System.out.println(
        "Diagnosing a child patient: Check-up and pediatric care.");
  }
  @Override
  public void visit(AdultPatient adultPatient) {
    System.out.println(
        "Diagnosing an adult patient: Routine exams and lifestyle advice.");
  }
  @Override
  public void visit(SeniorPatient seniorPatient) {
    System.out.println(
        "Diagnosing a senior patient: Comprehensive geriatric evaluation.");
  }
}

class BillingVisitor implements Visitor {
  @Override
  public void visit(ChildPatient childPatient) {
    System.out.println("Calculating billing for a child patient.");
  }
  @Override
  public void visit(AdultPatient adultPatient) {
    System.out.println("Calculating billing for an adult patient.");
  }
  @Override
  public void visit(SeniorPatient seniorPatient) {
    System.out.println("Calculating billing for a senior patient.");
  }
}

public class HospitalVisitorDemo {
  public static void main(String[] args) {
    // Create an array of patients
    Patient[] patients = {
        new ChildPatient(), new AdultPatient(), new SeniorPatient()};
    // Create visitors for different operations
    Visitor diagnosisVisitor = new DiagnosisVisitor();
    Visitor billingVisitor = new BillingVisitor();
    // Each patient accepts the visitors to perform the operations
    for (Patient patient : patients) {
      patient.accept(diagnosisVisitor);
      patient.accept(billingVisitor);
    }
  }
}

// -----------------------------------------------------------------------------------------------------------------

// Example 2

// Object - element
public interface Shape {
    void accept(ShapeVisitor visitor);
}

public class Square implements Shape {
    private final double length;

    public Square(final double length) {
        this.length = length;
    }

    public double getLength() {
        return length;
    }

    @Override
    public void accept(final ShapeVisitor visitor) {
        visitor.visit(this);
    }
}

public class Circle implements Shape {
    private final double radius;

    public Circle(final double radius) {
        this.radius = radius;
    }

    public double getRadius() {
        return radius;
    }

    @Override
    public void accept(final ShapeVisitor visitor) {
        visitor.visit(this);
    }
}

public class Rectangle implements Shape {
    private final double length;
    private final double width;

    public Rectangle(final double length, final double width) {
        this.length = length;
        this.width = width;
    }

    public double getLength() {
        return length;
    }

    public double getWidth() {
        return width;
    }

    @Override
    public void accept(final ShapeVisitor visitor) {
        visitor.visit(this);
    }
}

public interface ShapeVisitor {
    void visit(Circle circle);
    void visit(Square square);
    void visit(Rectangle rectangle);
}

public class AreaVisitor implements ShapeVisitor {
    private double area;

    // Pi*r2
    @Override
    public void visit(final Circle circle) {
        area = Math.PI * Math.pow(circle.getRadius(), 2);
    }

    // 2*l
    @Override
    public void visit(final Square square) {
        area = 2 * square.getLength();
    }

    // l*b
    @Override
    public void visit(final Rectangle rectangle) {
        area = rectangle.getLength() * rectangle.getWidth();
    }

    public double get() {
        return this.area;
    }
}

public class PerimeterVisitor implements ShapeVisitor {
    private double perimeter;

    // 2*pi*r
    @Override
    public void visit(final Circle circle) {
        perimeter = 2 * Math.PI * circle.getRadius();
    }

    // 4*a
    @Override
    public void visit(final Square square) {
        perimeter = 4 * square.getLength();
    }

    // 2*(l+b)
    @Override
    public void visit(final Rectangle rectangle) {
        perimeter = 2 * (rectangle.getLength() + rectangle.getWidth());
    }

    public double get() {
        return this.perimeter;
    }
}

public class Main {
    public static void main(String[] args) {
        final List<Shape> shapes = new ArrayList<>();

        shapes.add(new Circle(10));
        shapes.add(new Square(10));
        shapes.add(new Rectangle(10, 2));

        final AreaVisitor areaVisitor = new AreaVisitor();
        final PerimeterVisitor perimeterVisitor = new PerimeterVisitor();

        for (Shape shape: shapes) {
            shape.accept(areaVisitor);
            final double area = areaVisitor.get();

            System.out.printf(
                    "Area of %s: %.2f%n",
                    shape.getClass().getSimpleName(),
                    area
            );
        }

        System.out.println("---------------------------------");

        for (Shape shape: shapes) {
            shape.accept(perimeterVisitor);
            final double perimeter = perimeterVisitor.get();

            System.out.printf(
                    "Perimeter of %s: %.2f%n",
                    shape.getClass().getSimpleName(),
                    perimeter
            );
        }
    }
}