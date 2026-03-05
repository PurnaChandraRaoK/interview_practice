// 10. Bounded Blocking Queue
class BoundedBlockingQueue {
    private final Queue<Integer> queue = new LinkedList<>();
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public BoundedBlockingQueue(int capacity) { this.capacity = capacity; }

    public void enqueue(int x) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == capacity) notFull.await();
            queue.offer(x);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public int dequeue() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) notEmpty.await();
            int result = queue.poll();
            notFull.signal();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }
}

// Main Method to Call All 15 Problems
public class ConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        // 10. Bounded Blocking Queue
        BoundedBlockingQueue bbq = new BoundedBlockingQueue(3);
        new Thread(() -> {
            try { for (int i = 0; i < 5; i++) bbq.enqueue(i); } catch (InterruptedException ignored) {}
        }).start();
        new Thread(() -> {
            try { for (int i = 0; i < 5; i++) bbq.dequeue(); } catch (InterruptedException ignored) {}
        }).start();
    }
}