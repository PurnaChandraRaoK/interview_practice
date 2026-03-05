// 8. Traffic Light Controller
class TrafficLight {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition green = lock.newCondition();
    private int light = 0; // 0=North, 1=East, 2=South, 3=West

    public void pass(int direction) throws InterruptedException {
        lock.lock();
        try {
            while (light != direction) green.await();
            System.out.println("Car passed in direction: " + direction);
        } finally {
            lock.unlock();
        }
    }

    public void changeLight(int next) {
        lock.lock();
        try {
            light = next;
            green.signalAll();
        } finally {
            lock.unlock();
        }
    }
}

// Main Method to Call All 15 Problems
public class ConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        // 8. Traffic Light
        TrafficLight light = new TrafficLight();
        light.changeLight(2);
        new Thread(() -> {
            try { light.pass(2); } catch (InterruptedException ignored) {}
        }).start();
    }
}