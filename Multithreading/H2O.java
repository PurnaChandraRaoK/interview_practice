// 13. H2O Formation (FIXED: always release permits)
class H2O {
    private final Semaphore h = new Semaphore(2);
    private final Semaphore o = new Semaphore(1);
    private final CyclicBarrier barrier = new CyclicBarrier(3);

    public void hydrogen(Runnable releaseHydrogen) throws InterruptedException {
        h.acquire();
        try {
            barrier.await();
            releaseHydrogen.run();
        } catch (BrokenBarrierException e) {
            Thread.currentThread().interrupt();
        } finally {
            h.release();
        }
    }

    public void oxygen(Runnable releaseOxygen) throws InterruptedException {
        o.acquire();
        try {
            barrier.await();
            releaseOxygen.run();
        } catch (BrokenBarrierException e) {
            Thread.currentThread().interrupt();
        } finally {
            o.release();
        }
    }
}

// Main Method to Call All 15 Problems
public class ConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        // 13. H2O Formation
        H2O h2o = new H2O();
        for (int i = 0; i < 6; i++) new Thread(() -> {
            try { h2o.hydrogen(() -> System.out.print("H")); } catch (InterruptedException ignored) {}
        }).start();
        for (int i = 0; i < 3; i++) new Thread(() -> {
            try { h2o.oxygen(() -> System.out.print("O")); } catch (InterruptedException ignored) {}
        }).start();
    }
}