// 1. Even-Odd Printer
class EvenOddPrinter {
    private int number = 1;
    private final int max;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition oddTurn = lock.newCondition();
    private final Condition evenTurn = lock.newCondition();

    public EvenOddPrinter(int max) { this.max = max; }

    public void printOdd() {
        while (true) {
            lock.lock();
            try {
                while (number % 2 == 0 && number <= max) oddTurn.await();
                if (number > max) {
                    evenTurn.signal(); // wake even if waiting
                    break;
                }
                System.out.println("Odd: " + number++);
                evenTurn.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // wake other side so it can exit if needed
                evenTurn.signal();
                break;
            } finally {
                lock.unlock();
            }
        }
    }

    public void printEven() {
        while (true) {
            lock.lock();
            try {
                while (number % 2 == 1 && number <= max) evenTurn.await();
                if (number > max) {
                    oddTurn.signal(); // wake odd if waiting
                    break;
                }
                System.out.println("Even: " + number++);
                oddTurn.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                oddTurn.signal();
                break;
            } finally {
                lock.unlock();
            }
        }
    }
}

// Main Method to Call All 15 Problems
public class ConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {

        // 1. Even-Odd Printer
        EvenOddPrinter evenOdd = new EvenOddPrinter(10);
        new Thread(evenOdd::printOdd, "OddThread").start();
        new Thread(evenOdd::printEven, "EvenThread").start();
    }
}