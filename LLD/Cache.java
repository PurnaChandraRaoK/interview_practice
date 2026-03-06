import java.util.*;

/**
 * Interview-friendly cache design:
 * - Cache<K,V> uses:
 *    1) CacheStorage<K,V> (where values are stored)
 *    2) EvictionPolicy<K> (how keys are evicted)
 *
 * Strategy Pattern:
 * - Plug LRU or LFU by swapping EvictionPolicy.
 *
 * Notes:
 * - Synchronous (no executors, no DB) to keep it simple for interview.
 * - O(1) average get/put for both LRU and LFU.
 */
public class CacheDesignDemo {

    // -------------------- Core Interfaces --------------------

    public interface CacheStorage<K, V> {
        V get(K key);
        void put(K key, V value);
        void remove(K key);
        boolean containsKey(K key);
        int size();
        int capacity();
    }

    public interface EvictionPolicy<K> {
        // called on both read and write (if key exists or after insert)
        void keyAccessed(K key);

        // called when key removed externally (eviction or explicit remove)
        void keyRemoved(K key);

        // returns key to evict (or null if nothing)
        K evictKey();
    }

    // -------------------- Simple In-Memory Storage --------------------

    public static class InMemoryCacheStorage<K, V> implements CacheStorage<K, V> {
        private final Map<K, V> map;
        private final int capacity;

        public InMemoryCacheStorage(int capacity) {
            this.capacity = Math.max(0, capacity);
            this.map = new HashMap<>();
        }

        @Override public V get(K key) { return map.get(key); }
        @Override public void put(K key, V value) { map.put(key, value); }
        @Override public void remove(K key) { map.remove(key); }
        @Override public boolean containsKey(K key) { return map.containsKey(key); }
        @Override public int size() { return map.size(); }
        @Override public int capacity() { return capacity; }
    }

    // -------------------- Cache (Composition + Strategy) --------------------

    public static class Cache<K, V> {
        private final CacheStorage<K, V> storage;
        private final EvictionPolicy<K> evictionPolicy;

        public Cache(CacheStorage<K, V> storage, EvictionPolicy<K> evictionPolicy) {
            this.storage = storage;
            this.evictionPolicy = evictionPolicy;
        }

        public V get(K key) {
            if (!storage.containsKey(key)) return null;
            evictionPolicy.keyAccessed(key);
            return storage.get(key);
        }

        public void put(K key, V value) {
            if (storage.capacity() == 0) return;

            // update path
            if (storage.containsKey(key)) {
                storage.put(key, value);
                evictionPolicy.keyAccessed(key);
                return;
            }

            // insert path (may need eviction)
            if (storage.size() >= storage.capacity()) {
                K evict = evictionPolicy.evictKey();
                if (evict != null) {
                    storage.remove(evict);
                    evictionPolicy.keyRemoved(evict);
                }
            }

            storage.put(key, value);
            evictionPolicy.keyAccessed(key);
        }

        public void remove(K key) {
            if (!storage.containsKey(key)) return;
            storage.remove(key);
            evictionPolicy.keyRemoved(key);
        }
    }

    // =========================================================
    // ===================== LRU Policy =========================
    // =========================================================

    public static class LRUEvictionPolicy<K> implements EvictionPolicy<K> {

        private static class Node<K> {
            K key;
            Node<K> prev, next;
            Node(K key) { this.key = key; }
        }

        private final Map<K, Node<K>> nodeMap = new HashMap<>();
        private final Node<K> head = new Node<>(null); // dummy
        private final Node<K> tail = new Node<>(null); // dummy

        public LRUEvictionPolicy() {
            head.next = tail;
            tail.prev = head;
        }

        @Override
        public void keyAccessed(K key) {
            Node<K> node = nodeMap.get(key);
            if (node == null) {
                node = new Node<>(key);
                nodeMap.put(key, node);
                addToFront(node);
                return;
            }
            removeNode(node);
            addToFront(node);
        }

        @Override
        public void keyRemoved(K key) {
            Node<K> node = nodeMap.remove(key);
            if (node != null) removeNode(node);
        }

        @Override
        public K evictKey() {
            Node<K> lru = tail.prev;
            if (lru == head) return null;
            return lru.key;
        }

        private void addToFront(Node<K> node) {
            Node<K> next = head.next;
            node.next = next;
            node.prev = head;
            head.next = node;
            next.prev = node;
        }

        private void removeNode(Node<K> node) {
            Node<K> p = node.prev;
            Node<K> n = node.next;
            p.next = n;
            n.prev = p;
            node.prev = node.next = null;
        }
    }

    // =========================================================
    // ===================== LFU Policy =========================
    // =========================================================

    public static class LFUEvictionPolicy<K> implements EvictionPolicy<K> {

        private static class Node<K> {
            K key;
            int freq = 1;
            Node<K> prev, next;
            Node(K key) { this.key = key; }
        }

        private static class DoublyLinkedList<K> {
            int size = 0;
            final Node<K> head = new Node<>(null); // dummy
            final Node<K> tail = new Node<>(null); // dummy

            DoublyLinkedList() {
                head.next = tail;
                tail.prev = head;
            }

            void addFirst(Node<K> node) {
                Node<K> next = head.next;
                node.next = next;
                node.prev = head;
                head.next = node;
                next.prev = node;
                size++;
            }

            void remove(Node<K> node) {
                Node<K> p = node.prev;
                Node<K> n = node.next;
                p.next = n;
                n.prev = p;
                node.prev = node.next = null;
                size--;
            }

            Node<K> removeLast() {
                if (size == 0) return null;
                Node<K> last = tail.prev;
                remove(last);
                return last;
            }

            boolean isEmpty() { return size == 0; }
        }

        private final Map<K, Node<K>> nodes = new HashMap<>();
        private final Map<Integer, DoublyLinkedList<K>> freqToList = new HashMap<>();
        private int minFreq = 0;

        @Override
        public void keyAccessed(K key) {
            Node<K> node = nodes.get(key);
            if (node == null) {
                // new key observed (after storage insert)
                node = new Node<>(key);
                nodes.put(key, node);
                minFreq = 1;
                freqToList.computeIfAbsent(1, f -> new DoublyLinkedList<>()).addFirst(node);
                return;
            }
            touch(node);
        }

        @Override
        public void keyRemoved(K key) {
            Node<K> node = nodes.remove(key);
            if (node == null) return;
            DoublyLinkedList<K> list = freqToList.get(node.freq);
            if (list != null) {
                list.remove(node);
                if (node.freq == minFreq && list.isEmpty()) {
                    // minFreq will be corrected lazily on next access/insert
                    // (or you can scan forward; keeping it simple)
                }
            }
        }

        @Override
        public K evictKey() {
            DoublyLinkedList<K> list = freqToList.get(minFreq);
            if (list == null || list.isEmpty()) {
                // fix minFreq lazily if needed
                while (true) {
                    minFreq++;
                    list = freqToList.get(minFreq);
                    if (list == null) return null;
                    if (!list.isEmpty()) break;
                }
            }
            Node<K> victim = list.removeLast(); // LRU among min frequency
            return victim == null ? null : victim.key;
        }

        private void touch(Node<K> node) {
            int oldFreq = node.freq;
            DoublyLinkedList<K> oldList = freqToList.get(oldFreq);
            oldList.remove(node);

            if (oldFreq == minFreq && oldList.isEmpty()) {
                minFreq++;
            }

            node.freq++;
            freqToList.computeIfAbsent(node.freq, f -> new DoublyLinkedList<>()).addFirst(node);
        }
    }

    // -------------------- Quick Demo --------------------

    public static void main(String[] args) {
        Cache<String, String> lruCache =
                new Cache<>(new InMemoryCacheStorage<>(3), new LRUEvictionPolicy<>());

        lruCache.put("A", "Apple");
        lruCache.put("B", "Banana");
        lruCache.put("C", "Cherry");
        lruCache.get("A");                 // A becomes most recent
        lruCache.put("D", "Durian");       // evicts B
        System.out.println(lruCache.get("B")); // null

        Cache<String, String> lfuCache =
                new Cache<>(new InMemoryCacheStorage<>(3), new LFUEvictionPolicy<>());

        lfuCache.put("A", "Apple");
        lfuCache.put("B", "Banana");
        lfuCache.put("C", "Cherry");
        lfuCache.get("A"); // A freq=2
        lfuCache.get("A"); // A freq=3
        lfuCache.get("B"); // B freq=2
        lfuCache.put("D", "Durian"); // evicts C (freq=1)
        System.out.println(lfuCache.get("C")); // null
    }
}
