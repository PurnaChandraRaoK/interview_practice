import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.*;

// ===== Core Contracts =====

public interface Subscriber {
    void consume(Message message);
}

// ===== Simple Subscribers =====

final class PrintSubscriber implements Subscriber {
    private final String name;

    public PrintSubscriber(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public void consume(Message message) {
        System.out.println("Subscriber " + name + " received message: " + message.payload());
    }
}

final class LoggingSubscriber implements Subscriber {
    private final String name;

    public LoggingSubscriber(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public void consume(Message message) {
        System.out.println("[LOG] " + name + " received: " + message.payload());
    }
}

// ===== Publisher =====

final class Publisher {
    private final String id;
    private final Broker broker;

    public Publisher(String id, Broker broker) {
        this.id = Objects.requireNonNull(id, "id");
        this.broker = Objects.requireNonNull(broker, "broker");
    }

    public void publish(String topic, String payload) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(payload, "payload");
        broker.publish(topic, new Message(payload));
    }

    public String getId() {
        return id;
    }
}

// ===== Message =====

final class Message {
    private final String payload;

    public Message(String payload) {
        this.payload = Objects.requireNonNull(payload, "payload");
    }

    public String payload() {
        return payload;
    }
}

// ===== Dispatcher (async delivery) =====

final class Dispatcher implements AutoCloseable {
    private final ExecutorService executor;

    public Dispatcher(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void dispatch(Subscriber subscriber, Message message) {
        executor.submit(() -> {
            try {
                subscriber.consume(message);
            } catch (Exception e) {
                System.err.println("Dispatch error: " + e.getMessage());
            }
        });
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}

// ===== Topic =====

final class Topic {
    private final String name;
    private final Set<Subscriber> subscribers = new CopyOnWriteArraySet<>();
    private final Dispatcher dispatcher;

    public Topic(String name, Dispatcher dispatcher) {
        this.name = Objects.requireNonNull(name, "name");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public String getName() {
        return name;
    }

    public void addSubscriber(Subscriber subscriber) {
        subscribers.add(Objects.requireNonNull(subscriber, "subscriber"));
    }

    public void removeSubscriber(Subscriber subscriber) {
        subscribers.remove(Objects.requireNonNull(subscriber, "subscriber"));
    }

    public void broadcast(Message message) {
        Objects.requireNonNull(message, "message");
        for (Subscriber subscriber : subscribers) {
            dispatcher.dispatch(subscriber, message);
        }
    }
}

// ===== Broker =====

final class Broker {
    private final Map<String, Topic> topics = new ConcurrentHashMap<>();
    private final Dispatcher dispatcher;

    public Broker(Dispatcher dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    public void createTopic(String name) {
        Objects.requireNonNull(name, "name");
        topics.putIfAbsent(name, new Topic(name, dispatcher));
    }

    public void subscribe(String topicName, Subscriber subscriber) {
        Objects.requireNonNull(topicName, "topicName");
        Objects.requireNonNull(subscriber, "subscriber");

        Topic topic = topics.get(topicName);
        if (topic == null) throw new IllegalArgumentException("Topic not found: " + topicName);

        topic.addSubscriber(subscriber);
    }

    public void unsubscribe(String topicName, Subscriber subscriber) {
        Objects.requireNonNull(topicName, "topicName");
        Objects.requireNonNull(subscriber, "subscriber");

        Topic topic = topics.get(topicName);
        if (topic != null) topic.removeSubscriber(subscriber);
    }

    public void publish(String topicName, Message message) {
        Objects.requireNonNull(topicName, "topicName");
        Objects.requireNonNull(message, "message");

        Topic topic = topics.get(topicName);
        if (topic == null) throw new IllegalArgumentException("Topic not found: " + topicName);

        topic.broadcast(message);
    }
}

// ===== Demo =====

final class PubSubSystemDemo {
    public static void run() throws InterruptedException {
        // Prefer fixed pool for safety; still simple.
        try (Dispatcher dispatcher = new Dispatcher(Executors.newFixedThreadPool(4))) {

            Broker broker = new Broker(dispatcher);

            broker.createTopic("topic1");
            broker.createTopic("topic2");

            Publisher publisher1 = new Publisher("publisher1", broker);
            Publisher publisher2 = new Publisher("publisher2", broker);

            Subscriber subscriber1 = new PrintSubscriber("PrintSubscriber1");
            Subscriber subscriber2 = new PrintSubscriber("PrintSubscriber2");
            Subscriber subscriber3 = new LoggingSubscriber("LoggingSubscriber3");

            broker.subscribe("topic1", subscriber1);
            broker.subscribe("topic1", subscriber2);
            broker.subscribe("topic2", subscriber3);

            publisher1.publish("topic1", "Message1 for Topic1");
            publisher1.publish("topic1", "Message2 for Topic1");
            publisher1.publish("topic2", "Message1 for Topic2");

            broker.unsubscribe("topic1", subscriber2);

            publisher1.publish("topic1", "Message3 for Topic1");
            publisher2.publish("topic2", "Message2 for Topic2");

            Thread.sleep(200); // allow async delivery in demo
        }
    }

    public static void main(String[] args) throws InterruptedException {
        run();
    }
}
