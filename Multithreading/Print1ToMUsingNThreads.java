// 2. Print 1 to M using N Threads
class PrintOneToMUsingNThreads {
    private int counter = 1;
    private final int max;
    private final ReentrantLock lock = new ReentrantLock();

    public PrintOneToMUsingNThreads(int max) { this.max = max; }

    public void print() {
        while (true) {
            lock.lock();
            try {
                if (counter > max) break;
                System.out.println(Thread.currentThread().getName() + ": " + counter++);
            } finally {
                lock.unlock();
            }
        }
    }
}

// Main Method to Call All 15 Problems
public class ConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        // 2. Print 1 to M using N Threads
        PrintOneToMUsingNThreads printer = new PrintOneToMUsingNThreads(10);
        for (int i = 0; i < 3; i++) new Thread(printer::print, "P" + i).start();
    }
}