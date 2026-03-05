// 12. Reader-Writer Lock
class ReaderWriterLock {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();
    private int readers = 0;
    private boolean writer = false;

    public void read(Runnable readerTask) throws InterruptedException {
        lock.lock();
        try {
            while (writer) cond.await();
            readers++;
        } finally {
            lock.unlock();
        }

        readerTask.run();

        lock.lock();
        try {
            readers--;
            if (readers == 0) cond.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void write(Runnable writerTask) throws InterruptedException {
        lock.lock();
        try {
            while (writer || readers > 0) cond.await();
            writer = true;
        } finally {
            lock.unlock();
        }

        writerTask.run();

        lock.lock();
        try {
            writer = false;
            cond.signalAll();
        } finally {
            lock.unlock();
        }
    }
}

// Main Method to Call All 15 Problems
public class ConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        // 12. Reader-Writer Lock
        ReaderWriterLock rwLock = new ReaderWriterLock();
        new Thread(() -> {
            try { rwLock.read(() -> System.out.println("Reading")); } catch (InterruptedException ignored) {}
        }).start();
        new Thread(() -> {
            try { rwLock.write(() -> System.out.println("Writing")); } catch (InterruptedException ignored) {}
        }).start();
    }
}