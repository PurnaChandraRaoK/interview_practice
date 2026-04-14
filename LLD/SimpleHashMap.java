import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class SimpleHashMap<K, V> {

    private static final int INITIAL_CAPACITY = 16;   // buckets count
    private static final float LOAD_FACTOR = 0.75f;   // resize trigger

    private List<Entry<K, V>>[] buckets;
    private int capacity;
    private int size;
    private int threshold;

    // Key/value pair
    private static class Entry<K, V> {
        final K key;
        V value;
        Entry(K k, V v) { key = k; value = v; }
    }

    @SuppressWarnings("unchecked")
    public SimpleHashMap() {
        this.capacity = INITIAL_CAPACITY;
        this.buckets = new LinkedList[capacity];
        for (int i = 0; i < capacity; i++) buckets[i] = new LinkedList<>();
        this.size = 0;
        this.threshold = (int) (capacity * LOAD_FACTOR);
    }

    // Hash -> bucket index
    private int bucketIndex(Object key) {
        int h = Objects.hashCode(key) & 0x7fffffff;  // make non-negative
        return h % capacity;
    }

    // Double capacity and rehash all entries
    @SuppressWarnings("unchecked")
    private void resize() {
        int newCap = capacity * 2;
        List<Entry<K, V>>[] newBuckets = new LinkedList[newCap];
        for (int i = 0; i < newCap; i++) newBuckets[i] = new LinkedList<>();

        for (List<Entry<K, V>> bucket : buckets) {
            for (Entry<K, V> e : bucket) {
                int idx = (Objects.hashCode(e.key) & 0x7fffffff) % newCap;
                newBuckets[idx].add(e);
            }
        }

        buckets = newBuckets;
        capacity = newCap;
        threshold = (int) (capacity * LOAD_FACTOR);
    }

    // Put: insert or replace
    public void put(K key, V value) {
        if (size + 1 > threshold) resize();

        int idx = bucketIndex(key);
        List<Entry<K, V>> bucket = buckets[idx];

        for (Entry<K, V> e : bucket) {
            if (Objects.equals(e.key, key)) {
                e.value = value;       // replace
                return;
            }
        }

        bucket.add(new Entry<>(key, value));
        size++;
    }

    // Get: find value for key
    public V get(K key) {
        int idx = bucketIndex(key);
        for (Entry<K, V> e : buckets[idx]) {
            if (Objects.equals(e.key, key)) return e.value;
        }
        return null;
    }

    // Remove: delete key and return old value
    public V remove(K key) {
        int idx = bucketIndex(key);
        List<Entry<K, V>> bucket = buckets[idx];

        for (Entry<K, V> e : bucket) {
            if (Objects.equals(e.key, key)) {
                V old = e.value;
                bucket.remove(e);
                size--;
                return old;
            }
        }
        return null;
    }

    public int size() { return size; }

    // Quick test
    public static void main(String[] args) {
        SimpleHashMap<String, Integer> map = new SimpleHashMap<>();
        for (int i = 0; i < 20; i++) map.put("key" + i, i);

        System.out.println("Size after inserts: " + map.size());
        System.out.println("key5 -> " + map.get("key5"));

        map.remove("key5");
        System.out.println("Removed key5, get(key5) -> " + map.get("key5"));
        System.out.println("Final size: " + map.size());
    }
}
