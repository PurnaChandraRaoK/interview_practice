// 5. FooBar Alternation
class FooBar {
    private final int n;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition fooTurn = lock.newCondition();
    private final Condition barTurn = lock.newCondition();
    private boolean foo = true;

    public FooBar(int n) { this.n = n; }

    public void foo() {
        for (int i = 0; i < n; i++) {
            lock.lock();
            try {
                while (!foo) fooTurn.await();
                System.out.print("Foo");
                foo = false;
                barTurn.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                lock.unlock();
            }
        }
    }

    public void bar() {
        for (int i = 0; i < n; i++) {
            lock.lock();
            try {
                while (foo) barTurn.await();
                System.out.println("Bar");
                foo = true;
                fooTurn.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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
        // 5. FooBar Alternation
        FooBar fooBar = new FooBar(5);
        new Thread(fooBar::foo).start();
        new Thread(fooBar::bar).start();
    }
}