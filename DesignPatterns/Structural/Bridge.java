// Bridge will help us to decouple
// abstraction from its implementation so that both can exist independently

// Problem: Tight coupling (LivingThing + breathing behavior are glued together)
abstract class LivingThing {
    abstract void breatheProcess();
}

class Dog extends LivingThing {
    @Override
    void breatheProcess() {
        System.out.println("Dog breathing:");
        System.out.println("- breathe through nose");
        System.out.println("- inhale oxygen");
        System.out.println("- exhale carbon dioxide");
    }
}

class Fish extends LivingThing {
    @Override
    void breatheProcess() {
        System.out.println("Fish breathing:");
        System.out.println("- breathe through gills");
        System.out.println("- absorb oxygen");
        System.out.println("- release carbon dioxide");
    }
}

class Tree extends LivingThing {
    @Override
    void breatheProcess() {
        System.out.println("Tree breathing:");
        System.out.println("- breathe through leaves");
        System.out.println("- absorb carbon dioxide");
        System.out.println("- release oxygen");
    }
}

public class BeforeDemo {
    public static void main(String[] args) {
        LivingThing dog = new Dog();
        LivingThing fish = new Fish();
        LivingThing tree = new Tree();

        dog.breatheProcess();
        fish.breatheProcess();
        tree.breatheProcess();
    }
}

// Suppose in this example we want to add a new breathing mechanism,
// for example: a Bird that breathes through nostrils,
// takes in oxygen (O2) and releases carbon dioxide (CO2).

// Without using the Bridge Pattern, we cannot introduce this new
// breathing behavior without creating a new Bird class and tightly
// coupling the breathing logic inside it.

// As the number of living things and breathing mechanisms grows,
// this approach leads to class explosion and poor maintainability.

// The Bridge Pattern solves this problem by separating
// the "LivingThing" hierarchy from the "BreatheProcess" hierarchy,
// allowing both to evolve independently.

// With Bridge, we can add new breathing processes (e.g., BirdBreathing)
// or new living things without modifying existing classes.


// Solution

// IMPLEMENTOR (bridge side): "HOW to breathe"
interface BreatheImplementor {
    void breatheProcess();
}

class LandBreathe implements BreatheImplementor {
    @Override
    public void breatheProcess() {
        System.out.println("- breathe through nose");
        System.out.println("- inhale oxygen");
        System.out.println("- exhale carbon dioxide");
    }
}

class WaterBreathe implements BreatheImplementor {
    @Override
    public void breatheProcess() {
        System.out.println("- breathe through gills");
        System.out.println("- absorb oxygen");
        System.out.println("- release carbon dioxide");
    }
}

class TreeBreathe implements BreatheImplementor {
    @Override
    public void breatheProcess() {
        System.out.println("- breathe through leaves");
        System.out.println("- absorb carbon dioxide");
        System.out.println("- release oxygen");
    }
}


// ABSTRACTION (business side): "WHAT it is"
abstract class LivingThing {
    protected final BreatheImplementor breatheImplementor;

    protected LivingThing(BreatheImplementor breatheImplementor) {
        this.breatheImplementor = breatheImplementor;
    }

    abstract void breatheProcess();
}

class Dog extends LivingThing {
    public Dog(BreatheImplementor breatheImplementor) {
        super(breatheImplementor);
    }

    @Override
    void breatheProcess() {
        System.out.println("Dog breathing:");
        breatheImplementor.breatheProcess();
    }
}

class Fish extends LivingThing {
    public Fish(BreatheImplementor breatheImplementor) {
        super(breatheImplementor);
    }

    @Override
    void breatheProcess() {
        System.out.println("Fish breathing:");
        breatheImplementor.breatheProcess();
    }
}

class Tree extends LivingThing {
    public Tree(BreatheImplementor breatheImplementor) {
        super(breatheImplementor);
    }

    @Override
    void breatheProcess() {
        System.out.println("Tree breathing:");
        breatheImplementor.breatheProcess();
    }
}


// DEMO
public class BridgeDemo {
    public static void main(String[] args) {
        LivingThing dog  = new Dog(new LandBreathe());
        LivingThing fish = new Fish(new WaterBreathe());
        LivingThing tree = new Tree(new TreeBreathe());

        dog.breatheProcess();
        System.out.println();

        fish.breatheProcess();
        System.out.println();

        tree.breatheProcess();
        System.out.println();
    }
}