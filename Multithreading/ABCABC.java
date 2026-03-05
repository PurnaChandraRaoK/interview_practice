// 3. ABCABC with 3 Threads
class PrintABC {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition[] conditions = new Condition[3];
    private int state = 0;

    public PrintABC() {
        for (int i = 0; i < 3; i++) conditions[i] = lock.newCondition();
    }

    public void print(char ch, int targetState) {
        for (int i = 0; i < 5; i++) {
            lock.lock();
            try {
                while (state % 3 != targetState) conditions[targetState].await();
                System.out.print(ch);
                state++;
                conditions[(targetState + 1) % 3].signal();
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
        // 3. ABCABC with 3 Threads
        PrintABC abc = new PrintABC();
        new Thread(() -> abc.print('A', 0)).start();
        new Thread(() -> abc.print('B', 1)).start();
        new Thread(() -> abc.print('C', 2)).start();
    }
}