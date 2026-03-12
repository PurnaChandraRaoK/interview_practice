// Problem Statement: Managing Memory Usage 
// Imagine you're developing a massive multiplayer game where thousands of 
// particles need to be rendered simultaneously (e.g., bullets, explosions, 
// or visual effects). Each particle object contains properties like position, 
// velocity, and appearance. Creating separate objects for each particle would consume excessive memory.

// The Problem: Creating individual objects for repetitive elements can lead to:

// • Excessive memory consumption

// • Poor system performance

// • Increased garbage collection overhead

// The Challenge: How can you create a solution that efficiently handles large numbers of similar objects while minimizing memory usage?

// Solution

// ParticleType.java (Flyweight)
public class ParticleType {
    private final String color;
    private final String sprite;
    public ParticleType(String color, String sprite) {
        this.color = color;
        this.sprite = sprite;
    }

    public void render(float x, float y, float velocity) {
        System.out.println("Rendering " + color + " particle at (" + x + "," + y + 
                         ") with sprite " + sprite);
    }
}

// ParticleTypeFactory.java
public class ParticleTypeFactory {
    private Map<String, ParticleType> particleTypes = new HashMap<>();
    public ParticleType getParticleType(String color, String sprite) {
        String key = color + "_" + sprite;
        return particleTypes.computeIfAbsent(key,
            k -> new ParticleType(color, sprite));
    }
}

// Particle.java
public class Particle {
    private ParticleType type; // reference to flyweight
    private float x;
    private float y;
    private float velocity;

    public Particle(ParticleType type, float x, float y, float velocity) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.velocity = velocity;
    }

    public void update() {
        y += velocity;
        type.render(x, y, velocity);
    }
}

// Game.java
public class Game {
    public static void main(String[] args) {
        ParticleTypeFactory factory = new ParticleTypeFactory();
        List<Particle> particles = new ArrayList<>();
        // Create thousands of particles using shared flyweights
        ParticleType explosionType = factory.getParticleType("red", "explosion.png");
        
        for (int i = 0; i < 1000; i++) {
            particles.add(new Particle(explosionType,
                                     (float) Math.random() * 100,
                                     (float) Math.random() * 100,
                                     1.0f));
        }
        // Update all particles
        for (Particle particle : particles) {
            particle.update();
        }
    }
}

