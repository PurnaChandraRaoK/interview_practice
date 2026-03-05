// 4. Ping-Pong Print
class PingPong {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition pingTurn = lock.newCondition();
    private final Condition pongTurn = lock.newCondition();
    private boolean ping = true;

    public void printPing() {
        for (int i = 0; i < 5; i++) {
            lock.lock();
            try {
                while (!ping) pingTurn.await();
                System.out.println("Ping");
                ping = false;
                pongTurn.signal();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                lock.unlock();
            }
        }
    }

    public void printPong() {
        for (int i = 0; i < 5; i++) {
            lock.lock();
            try {
                while (ping) pongTurn.await();
                System.out.println("Pong");
                ping = true;
                pingTurn.signal();
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
        // 4. Ping-Pong Print
        PingPong pingPong = new PingPong();
        new Thread(pingPong::printPing).start();
        new Thread(pingPong::printPong).start();
    }
}