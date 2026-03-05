// 15. Restaurant Delivery Simulation
class RestaurantDelivery {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition newOrder = lock.newCondition();
    private final Queue<String> orders = new LinkedList<>();

    public void placeOrder(String order) {
        lock.lock();
        try {
            orders.offer(order);
            System.out.println("Order placed: " + order);
            newOrder.signal();
        } finally {
            lock.unlock();
        }
    }

    public void deliverOrder() throws InterruptedException {
        lock.lock();
        try {
            while (orders.isEmpty()) newOrder.await();
            String order = orders.poll();
            System.out.println("Order delivered: " + order);
        } finally {
            lock.unlock();
        }
    }
}

// Main Method to Call All 15 Problems
public class ConcurrencyTest {
    public static void main(String[] args) throws InterruptedException {
        // 15. Restaurant Delivery
        RestaurantDelivery delivery = new RestaurantDelivery();
        new Thread(() -> {
            try { delivery.deliverOrder(); } catch (InterruptedException ignored) {}
        }).start();
        new Thread(() -> delivery.placeOrder("Burger")).start();
    }
}