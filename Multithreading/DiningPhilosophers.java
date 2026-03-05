// 11. Dining Philosophers
class DiningPhilosophers {
    private final ReentrantLock[] forks;

    public DiningPhilosophers(int n) {
        forks = new ReentrantLock[n];
        for (int i = 0; i < n; i++) forks[i] = new ReentrantLock();
    }

    public void wantsToEat(int id) {
        int left = id;
        int right = (id + 1) % forks.length;

        ReentrantLock first = forks[Math.min(left, right)];
        ReentrantLock second = forks[Math.max(left, right)];

        first.lock();
        second.lock();
        try {
            System.out.println("Philosopher " + id + " is eating.");
        } finally {
            second.unlock();
            first.unlock();
        }
    }
}

// Main Method to Call All 15 Problems
public class ConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        // 11. Dining Philosophers
        DiningPhilosophers dp = new DiningPhilosophers(5);
        for (int i = 0; i < 5; i++) {
            int id = i;
            new Thread(() -> dp.wantsToEat(id)).start();
        }
    }
}