// 6. FizzBuzz with 4 Threads (FIXED: no busy spin)
class FizzBuzz {
    private final int n;
    private int current = 1;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition turn = lock.newCondition();

    public FizzBuzz(int n) { this.n = n; }

    private void doneWakeAll() {
        turn.signalAll();
    }

    public void fizz() {
        while (true) {
            lock.lock();
            try {
                while (current <= n && !(current % 3 == 0 && current % 5 != 0)) turn.await();
                if (current > n) { doneWakeAll(); break; }
                System.out.println("Fizz");
                current++;
                doneWakeAll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                doneWakeAll();
                break;
            } finally {
                lock.unlock();
            }
        }
    }

    public void buzz() {
        while (true) {
            lock.lock();
            try {
                while (current <= n && !(current % 5 == 0 && current % 3 != 0)) turn.await();
                if (current > n) { doneWakeAll(); break; }
                System.out.println("Buzz");
                current++;
                doneWakeAll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                doneWakeAll();
                break;
            } finally {
                lock.unlock();
            }
        }
    }

    public void fizzbuzz() {
        while (true) {
            lock.lock();
            try {
                while (current <= n && !(current % 15 == 0)) turn.await();
                if (current > n) { doneWakeAll(); break; }
                System.out.println("FizzBuzz");
                current++;
                doneWakeAll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                doneWakeAll();
                break;
            } finally {
                lock.unlock();
            }
        }
    }

    public void number() {
        while (true) {
            lock.lock();
            try {
                while (current <= n && !(current % 3 != 0 && current % 5 != 0)) turn.await();
                if (current > n) { doneWakeAll(); break; }
                System.out.println(current);
                current++;
                doneWakeAll();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                doneWakeAll();
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
        // 6. FizzBuzz
        FizzBuzz fizzBuzz = new FizzBuzz(15);
        new Thread(fizzBuzz::fizz).start();
        new Thread(fizzBuzz::buzz).start();
        new Thread(fizzBuzz::fizzbuzz).start();
        new Thread(fizzBuzz::number).start();
    }
}