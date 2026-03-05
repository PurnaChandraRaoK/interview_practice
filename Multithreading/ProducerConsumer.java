// 9. Producer-Consumer using ReentrantLock + Conditions
class ProducerConsumer {
    private final Queue<Integer> queue = new LinkedList<>();
    private final int CAPACITY = 5;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public void produce(int value) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == CAPACITY) notFull.await();
            queue.add(value);
            System.out.println("Produced: " + value);
            notEmpty.signal(); // one consumer enough
        } finally {
            lock.unlock();
        }
    }

    public int consume() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) notEmpty.await();
            int val = queue.poll();
            System.out.println("Consumed: " + val);
            notFull.signal(); // one producer enough
            return val;
        } finally {
            lock.unlock();
        }
    }
}

// Main Method to Call All 15 Problems
public class ConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        // 9. Producer-Consumer
        ProducerConsumer pc = new ProducerConsumer();
        new Thread(() -> {
            try { for (int i = 0; i < 5; i++) pc.produce(i); } catch (InterruptedException ignored) {}
        }).start();
        new Thread(() -> {
            try { for (int i = 0; i < 5; i++) pc.consume(); } catch (InterruptedException ignored) {}
        }).start();
    }
}