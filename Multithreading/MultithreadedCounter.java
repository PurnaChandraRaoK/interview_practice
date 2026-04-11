class Counter {
    private int count = 0;

    public synchronized void increment() {
        count++;
    }

    public int getCount() {
        return count;
    }
}

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++)
                counter.increment();
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++)
                counter.increment();
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        System.out.println(counter.getCount()); // 2000
    }
}

import java.util.concurrent.*;

public class Demo {
  public static void main(String[] args) throws Exception {
    Counter counter = new Counter(); // pick AtomicInteger or LongAdder inside

    ExecutorService es = Executors.newFixedThreadPool(8);
    for (int t = 0; t < 8; t++) {
      es.submit(() -> { for (int i = 0; i < 100_000; i++) counter.inc(); });
    }
    es.shutdown();
    es.awaitTermination(1, TimeUnit.MINUTES);

    System.out.println(counter.get());
  }
}


import java.util.concurrent.atomic.AtomicInteger;

class Counter {
    private AtomicInteger count = new AtomicInteger(0);

    public void increment() {
        count.incrementAndGet();
    }

    public int getCount() {
        return count.get();
    }
}

import java.util.concurrent.atomic.LongAdder;

class Counter {
    private final LongAdder c = new LongAdder();
    public void inc() { c.increment(); }
    public long get() { return c.sum(); }
}
