import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentHashMapCustom<K, V> {

    private static class Node<K, V> {
        final K key;
        volatile V value;      // visible to readers
        Node<K, V> next;

        Node(K key, V value, Node<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private static final int DEFAULT_CAPACITY = 16; // power of 2

    @SuppressWarnings("unchecked")
    private final Node<K, V>[] table = (Node<K, V>[]) new Node[DEFAULT_CAPACITY];

    private final ReentrantLock[] locks = new ReentrantLock[DEFAULT_CAPACITY];
    private final AtomicInteger size = new AtomicInteger(0);

    public ConcurrentHashMapCustom() {
        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    private int bucketIndex(Object key) {
        int h = key.hashCode();
        h ^= (h >>> 16);                 // spread bits (like HashMap)
        return h & (DEFAULT_CAPACITY - 1);
    }

    public V get(Object key) {
        if (key == null) return null;
        int idx = bucketIndex(key);

        ReentrantLock lock = locks[idx];
        lock.lock();
        try {
            for (Node<K, V> curr = table[idx]; curr != null; curr = curr.next) {
                if (curr.key.equals(key)) return curr.value;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public V put(K key, V value) {
        if (key == null) throw new NullPointerException("null keys not supported in this simple impl");
        int idx = bucketIndex(key);

        ReentrantLock lock = locks[idx];
        lock.lock();
        try {
            Node<K, V> head = table[idx];
            for (Node<K, V> curr = head; curr != null; curr = curr.next) {
                if (curr.key.equals(key)) {
                    V old = curr.value;
                    curr.value = value;
                    return old;
                }
            }
            table[idx] = new Node<>(key, value, head);
            size.incrementAndGet();
            return null;
        } finally {
            lock.unlock();
        }
    }

    public V remove(Object key) {
        if (key == null) return null;
        int idx = bucketIndex(key);

        ReentrantLock lock = locks[idx];
        lock.lock();
        try {
            Node<K, V> curr = table[idx];
            Node<K, V> prev = null;

            while (curr != null) {
                if (curr.key.equals(key)) {
                    V old = curr.value;
                    if (prev == null) table[idx] = curr.next;
                    else prev.next = curr.next;

                    size.decrementAndGet();
                    return old;
                }
                prev = curr;
                curr = curr.next;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        return size.get();
    }

    public static void main(String[] args) throws Exception {
        ConcurrentHashMapCustom<String, Integer> map = new ConcurrentHashMapCustom<>();
        map.put("a", 1);
        map.put("b", 2);
        System.out.println(map.get("a"));     // 1
        System.out.println(map.remove("a"));  // 1
        System.out.println(map.get("a"));     // null
    }
}
