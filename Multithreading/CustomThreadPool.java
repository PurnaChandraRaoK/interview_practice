// 14. Custom ThreadPool Executor
class SimpleThreadPool {
    private final BlockingQueue<Runnable> taskQueue;
    private final List<Worker> workers;
    private volatile boolean isStopped = false;

    public SimpleThreadPool(int numThreads) {
        taskQueue = new LinkedBlockingQueue<>();
        workers = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            Worker worker = new Worker();
            worker.start();
            workers.add(worker);
        }
    }

    public void submit(Runnable task) {
        if (!isStopped) taskQueue.offer(task);
    }

    public void stop() {
        isStopped = true;
        for (Worker w : workers) w.interrupt();
    }

    private class Worker extends Thread {
        public void run() {
            while (!isStopped || !taskQueue.isEmpty()) {
                try {
                    Runnable task = taskQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task != null) task.run();
                } catch (InterruptedException ignored) {
                    // allow loop to re-check stop condition
                }
            }
        }
    }
}

// Main Method to Call All 15 Problems
public class ConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        // 14. ThreadPool
        SimpleThreadPool pool = new SimpleThreadPool(3);
        for (int i = 0; i < 5; i++) {
            int taskNum = i;
            pool.submit(() -> System.out.println("Executing task: " + taskNum));
        }
        Thread.sleep(1000);
        pool.stop();
    }
}