/* ======================= ENUMS ======================= */

enum Channel {
    EMAIL, SMS, PUSH
}

enum NotificationType {
    NEW_MESSAGE, FRIEND_REQUEST, SYSTEM_ALERT, CUSTOM_EVENT
}

/* ======================= NOTIFICATION ======================= */

interface Notification {
    Channel getChannel();
    NotificationType getType();
    String getRecipient();
    String getContent();
}

/* ======================= NOTIFICATION IMPLEMENTATIONS ======================= */

final class Email implements Notification {
    private final String to;
    private final String subject;
    private final String body;
    private final NotificationType type;

    public Email(String to, String subject, String body, NotificationType type) {
        this.to = to;
        this.subject = subject;
        this.body = body;
        this.type = type;
    }

    public String getSubject() { return subject; }

    @Override public Channel getChannel() { return Channel.EMAIL; }
    @Override public NotificationType getType() { return type; }
    @Override public String getRecipient() { return to; }
    @Override public String getContent() { return body; }
}

final class SMS implements Notification {
    private final String to;
    private final String message;
    private final NotificationType type;

    public SMS(String to, String message, NotificationType type) {
        this.to = to;
        this.message = message;
        this.type = type;
    }

    @Override public Channel getChannel() { return Channel.SMS; }
    @Override public NotificationType getType() { return type; }
    @Override public String getRecipient() { return to; }
    @Override public String getContent() { return message; }
}

final class Push implements Notification {
    private final String deviceId;
    private final String title;
    private final String payload;
    private final NotificationType type;

    public Push(String deviceId, String title, String payload, NotificationType type) {
        this.deviceId = deviceId;
        this.title = title;
        this.payload = payload;
        this.type = type;
    }

    public String getTitle() { return title; }

    @Override public Channel getChannel() { return Channel.PUSH; }
    @Override public NotificationType getType() { return type; }
    @Override public String getRecipient() { return deviceId; }
    @Override public String getContent() { return payload; }
}

/* ======================= PROVIDERS ======================= */

interface NotificationSender {
    String getProviderName();
    Set<Channel> supportedChannels();

    default boolean supports(Channel channel) {
        return supportedChannels().contains(channel);
    }

    void send(Notification notification);
}

interface SchedulableNotificationSender extends NotificationSender {
    void schedule(Notification notification, LocalDateTime dateTime);
}

/* ======================= PROVIDER IMPLEMENTATIONS ======================= */

class EmailProvider implements SchedulableNotificationSender {

    @Override public String getProviderName() { return "EmailProvider"; }
    @Override public Set<Channel> supportedChannels() { return Set.of(Channel.EMAIL); }

    @Override
    public void send(Notification notification) {
        System.out.println("Sending EMAIL to " + notification.getRecipient());
    }

    @Override
    public void schedule(Notification notification, LocalDateTime dateTime) {
        System.out.println("Scheduling EMAIL to " + notification.getRecipient() + " at " + dateTime);
    }
}

class SMSProvider implements SchedulableNotificationSender {

    @Override public String getProviderName() { return "SMSProvider"; }
    @Override public Set<Channel> supportedChannels() { return Set.of(Channel.SMS); }

    @Override
    public void send(Notification notification) {
        System.out.println("Sending SMS to " + notification.getRecipient());
    }

    @Override
    public void schedule(Notification notification, LocalDateTime dateTime) {
        System.out.println("Scheduling SMS to " + notification.getRecipient() + " at " + dateTime);
    }
}

class PushProvider implements SchedulableNotificationSender {

    @Override public String getProviderName() { return "PushProvider"; }
    @Override public Set<Channel> supportedChannels() { return Set.of(Channel.PUSH); }

    @Override
    public void send(Notification notification) {
        System.out.println("Sending PUSH to " + notification.getRecipient());
    }

    @Override
    public void schedule(Notification notification, LocalDateTime dateTime) {
        System.out.println("Scheduling PUSH to " + notification.getRecipient() + " at " + dateTime);
    }
}

/* ======================= PROVIDER REGISTRY ======================= */

class ProviderRegistry {
    private final Map<Channel, List<NotificationSender>> providers = new HashMap<>();

    public void register(NotificationSender sender) {
        for (Channel channel : sender.supportedChannels()) {
            providers.computeIfAbsent(channel, c -> new ArrayList<>()).add(sender);
        }
    }

    public List<NotificationSender> getProviders(Channel channel) {
        return providers.getOrDefault(channel, List.of());
    }
}

/* ======================= USER ======================= */

class User {
    private final String userId;
    private final Set<NotificationType> subscriptions = new HashSet<>();
    private final Map<NotificationType, List<Channel>> preferences = new HashMap<>();

    public User(String userId) {
        this.userId = Objects.requireNonNull(userId);
    }

    public String getUserId() { return userId; }

    public void subscribe(NotificationType type, List<Channel> preferredChannels) {
        subscriptions.add(type);
        preferences.put(type, new ArrayList<>(preferredChannels));
    }

    public boolean isSubscribed(NotificationType type) {
        return subscriptions.contains(type);
    }

    public List<Channel> preferredChannels(NotificationType type) {
        return preferences.getOrDefault(type, List.of());
    }
}

/* ======================= DISPATCHER ======================= */

class NotificationDispatcher {

    private final ProviderRegistry registry;

    public NotificationDispatcher(ProviderRegistry registry) {
        this.registry = registry;
    }

    public void dispatch(User user, Notification notification) {

        if (!user.isSubscribed(notification.getType())) {
            System.out.println("User " + user.getUserId() + " not subscribed to " + notification.getType());
            return;
        }

        if (!user.preferredChannels(notification.getType()).contains(notification.getChannel())) {
            System.out.println("User " + user.getUserId() + " prefers other channels");
            return;
        }

        List<NotificationSender> candidates = registry.getProviders(notification.getChannel());
        if (candidates.isEmpty()) {
            System.out.println("No providers for channel " + notification.getChannel());
            return;
        }

        NotificationSender provider =
                candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        provider.send(notification);
    }

    public void schedule(User user, Notification notification, LocalDateTime time) {
        registry.getProviders(notification.getChannel()).stream()
                .filter(p -> p instanceof SchedulableNotificationSender)
                .map(p -> (SchedulableNotificationSender) p)
                .findAny()
                .ifPresentOrElse(
                        p -> p.schedule(notification, time),
                        () -> System.out.println("Scheduling not supported for " + notification.getChannel())
                );
    }
}

/* ======================= APP ======================= */

public class NotificationSystemApp {

    public static void main(String[] args) {

        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new EmailProvider());
        registry.register(new SMSProvider());
        registry.register(new PushProvider());

        NotificationDispatcher dispatcher = new NotificationDispatcher(registry);

        User alice = new User("alice-123");
        alice.subscribe(NotificationType.NEW_MESSAGE, List.of(Channel.EMAIL, Channel.PUSH));
        alice.subscribe(NotificationType.SYSTEM_ALERT, List.of(Channel.SMS));

        Notification email = new Email(
                "alice@example.com",
                "Welcome",
                "Hello Alice",
                NotificationType.NEW_MESSAGE
        );

        dispatcher.dispatch(alice, email);
        dispatcher.schedule(alice, email, LocalDateTime.now().plusHours(2));
    }
}
