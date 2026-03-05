// 7. ZeroEvenOdd Problem
class ZeroEvenOdd {
    private final int n;
    private int current = 1;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition zeroTurn = lock.newCondition();
    private final Condition evenTurn = lock.newCondition();
    private final Condition oddTurn = lock.newCondition();
    private boolean zeroPrinted = false;

    public ZeroEvenOdd(int n) { this.n = n; }

    public void zero() {
        for (int i = 0; i < n; i++) {
            lock.lock();
            try {
                while (zeroPrinted) zeroTurn.await();
                System.out.print(0);
                zeroPrinted = true;
                if ((current & 1) == 0) evenTurn.signal();
                else oddTurn.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                evenTurn.signal();
                oddTurn.signal();
                break;
            } finally {
                lock.unlock();
            }
        }
    }

    public void even() {
        while (true) {
            lock.lock();
            try {
                while (current <= n && (!zeroPrinted || (current & 1) == 1)) evenTurn.await();
                if (current > n) { zeroTurn.signal(); break; }
                System.out.print(current);
                current++;
                zeroPrinted = false;
                zeroTurn.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                zeroTurn.signal();
                break;
            } finally {
                lock.unlock();
            }
        }
    }

    public void odd() {
        while (true) {
            lock.lock();
            try {
                while (current <= n && (!zeroPrinted || (current & 1) == 0)) oddTurn.await();
                if (current > n) { zeroTurn.signal(); break; }
                System.out.print(current);
                current++;
                zeroPrinted = false;
                zeroTurn.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                zeroTurn.signal();
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
        // 7. ZeroEvenOdd
        ZeroEvenOdd zeroEvenOdd = new ZeroEvenOdd(5);
        new Thread(zeroEvenOdd::zero).start();
        new Thread(zeroEvenOdd::even).start();
        new Thread(zeroEvenOdd::odd).start();
    }
}