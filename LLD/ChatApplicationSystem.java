import java.util.*;

public class ChatServiceApp {

    /* =========================
     *  Presence
     * ========================= */
    enum Presence { ONLINE, OFFLINE }

    /* =========================
     *  Presence Notification (Observer)
     * ========================= */
    interface PresenceObserver {
        void onPresenceChanged(User target, Presence newPresence, User watcher);
    }

    /* =========================
     *  Encryption (Strategy)
     * ========================= */
    interface EncryptionStrategy {
        String encrypt(String plainText);
        String decrypt(String cipherText);
    }

    static class StubEncryption implements EncryptionStrategy {
        private static final String PREFIX = "ENCRYPTED(";
        private static final String SUFFIX = ")";

        @Override
        public String encrypt(String plainText) {
            return PREFIX + plainText + SUFFIX;
        }

        @Override
        public String decrypt(String cipherText) {
            if (cipherText == null) return null;
            if (cipherText.startsWith(PREFIX) && cipherText.endsWith(SUFFIX) && cipherText.length() > PREFIX.length() + 1) {
                return cipherText.substring(PREFIX.length(), cipherText.length() - SUFFIX.length());
            }
            return cipherText;
        }
    }

    /* =========================
     *  Message + Observer
     * ========================= */
    enum MessageState { SENT, DELIVERED, SEEN }

    interface MessageObserver {
        void onSeen(Message message, User seenBy);
    }

    interface Message {
        String getId();
        String getType();
        User getSender();
        List<User> getRecipients();
        String getEncryptedContent();
        String getDecryptedContent();

        MessageState getStateFor(User recipient);
        void markDelivered(User recipient);
        void markSeen(User recipient);

        void setObserver(MessageObserver observer);
    }

    static abstract class AbstractMessage implements Message {
        protected final String id;
        protected final User sender;
        protected final List<User> recipients;
        protected final String encryptedContent;
        protected final EncryptionStrategy encryption;

        protected MessageObserver observer;
        private final Map<String, MessageState> stateByRecipientId = new HashMap<>();

        protected AbstractMessage(String id,
                                  User sender,
                                  List<User> recipients,
                                  String plainContent,
                                  EncryptionStrategy encryption) {
            this.id = Objects.requireNonNull(id);
            this.sender = Objects.requireNonNull(sender);
            this.recipients = List.copyOf(Objects.requireNonNull(recipients));
            this.encryption = Objects.requireNonNull(encryption);
            this.encryptedContent = this.encryption.encrypt(plainContent);

            for (User r : this.recipients) {
                stateByRecipientId.put(r.getUserId(), MessageState.SENT);
            }
        }

        @Override public String getId() { return id; }
        @Override public User getSender() { return sender; }
        @Override public List<User> getRecipients() { return recipients; }
        @Override public String getEncryptedContent() { return encryptedContent; }
        @Override public String getDecryptedContent() { return encryption.decrypt(encryptedContent); }

        @Override
        public MessageState getStateFor(User recipient) {
            return stateByRecipientId.getOrDefault(recipient.getUserId(), MessageState.SENT);
        }

        @Override
        public void markDelivered(User recipient) {
            String rid = recipient.getUserId();
            if (stateByRecipientId.containsKey(rid)) {
                stateByRecipientId.put(rid, MessageState.DELIVERED);
            }
        }

        @Override
        public void markSeen(User recipient) {
            String rid = recipient.getUserId();
            if (!stateByRecipientId.containsKey(rid)) return;

            if (stateByRecipientId.get(rid) != MessageState.SEEN) {
                stateByRecipientId.put(rid, MessageState.SEEN);
                if (observer != null) observer.onSeen(this, recipient);
            }
        }

        @Override
        public void setObserver(MessageObserver observer) {
            this.observer = observer;
        }
    }

    static class TextMessage extends AbstractMessage {
        TextMessage(String id, User sender, List<User> recipients, String content, EncryptionStrategy encryption) {
            super(id, sender, recipients, content, encryption);
        }
        @Override public String getType() { return "TEXT"; }
    }

    static class ImageMessage extends AbstractMessage {
        ImageMessage(String id, User sender, List<User> recipients, String imageUrl, EncryptionStrategy encryption) {
            super(id, sender, recipients, imageUrl, encryption);
        }
        @Override public String getType() { return "IMAGE"; }
    }

    /* =========================
     *  Factory (creates messages + wires observer)
     * ========================= */
    static class MessageFactory {
        private final EncryptionStrategy encryption;

        MessageFactory(EncryptionStrategy encryption) {
            this.encryption = Objects.requireNonNull(encryption);
        }

        Message createText(User sender, List<User> recipients, String content) {
            Message m = new TextMessage(UUID.randomUUID().toString(), sender, recipients, content, encryption);
            attachSeenObserver(sender, m);
            return m;
        }

        Message createImage(User sender, List<User> recipients, String imageUrl) {
            Message m = new ImageMessage(UUID.randomUUID().toString(), sender, recipients, imageUrl, encryption);
            attachSeenObserver(sender, m);
            return m;
        }

        private void attachSeenObserver(User sender, Message message) {
            message.setObserver((msg, seenBy) -> {
                System.out.println("🔔 Notification to " + sender.getUsername()
                        + ": " + seenBy.getUsername()
                        + " saw your " + msg.getType()
                        + " message (id=" + msg.getId() + ")");
            });
        }
    }

    /* =========================
     *  User
     * ========================= */
    static class User {
        private final String userId;
        private final String username;
        private final List<Message> inbox = new ArrayList<>();

        User(String userId, String username) {
            this.userId = Objects.requireNonNull(userId);
            this.username = Objects.requireNonNull(username);
        }

        String getUserId() { return userId; }
        String getUsername() { return username; }

        void receive(Message message) {
            inbox.add(message);
            message.markDelivered(this);
            System.out.println(username + " received (encrypted): " + message.getEncryptedContent());
        }

        void readAll() {
            for (Message m : inbox) {
                m.markSeen(this);
                System.out.println(username + " read (decrypted): " + m.getDecryptedContent());
            }
            inbox.clear();
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof User)) return false;
            return userId.equals(((User) o).userId);
        }

        @Override public int hashCode() {
            return Objects.hash(userId);
        }
    }

    /* =========================
     *  Group
     * ========================= */
    static class Group {
        private final String groupId;
        private final String name;
        private final List<User> members = new ArrayList<>();

        Group(String groupId, String name) {
            this.groupId = Objects.requireNonNull(groupId);
            this.name = Objects.requireNonNull(name);
        }

        String getGroupId() { return groupId; }
        String getName() { return name; }

        void addMember(User user) {
            if (!members.contains(user)) members.add(user);
        }

        List<User> members() {
            return List.copyOf(members);
        }
    }

    /* =========================
     *  ChatService (single entry point)
     * ========================= */
    static class ChatService {
        private final Map<String, User> users = new HashMap<>();
        private final Map<String, Group> groups = new HashMap<>();
        private final Map<String, Presence> presence = new HashMap<>();

        // key = targetUserId, value = set of watcherUserIds
        private final Map<String, Set<String>> followers = new HashMap<>();

        private final List<PresenceObserver> presenceObservers = new ArrayList<>();

        private final MessageFactory messageFactory;

        ChatService() {
            this.messageFactory = new MessageFactory(new StubEncryption());

            // default presence observer (prints notification)
            addPresenceObserver((target, newPresence, watcher) -> {
                System.out.println("🔔 Notification to " + watcher.getUsername()
                        + ": " + target.getUsername() + " is now " + newPresence);
            });
        }

        void addPresenceObserver(PresenceObserver observer) {
            presenceObservers.add(observer);
        }

        // onboarding
        User onboardUser(String userId, String username) {
            User u = new User(userId, username);
            users.put(userId, u);
            presence.put(userId, Presence.OFFLINE);
            followers.putIfAbsent(userId, new HashSet<>());
            System.out.println("✅ Onboarded user: " + username);
            return u;
        }

        // NEW: subscribe watcher to target presence changes
        void subscribePresence(User watcher, User target) {
            followers.putIfAbsent(target.getUserId(), new HashSet<>());
            followers.get(target.getUserId()).add(watcher.getUserId());
            System.out.println("👀 " + watcher.getUsername() + " will be notified about " + target.getUsername() + " presence.");
        }

        // presence
        void setPresence(User user, Presence p) {
            Presence old = presence.getOrDefault(user.getUserId(), Presence.OFFLINE);
            if (old == p) return; // avoid duplicate notifications

            presence.put(user.getUserId(), p);
            System.out.println("🟢 Presence: " + user.getUsername() + " -> " + p);

            // notify followers
            Set<String> watcherIds = followers.getOrDefault(user.getUserId(), Set.of());
            for (String wid : watcherIds) {
                User watcher = users.get(wid);
                if (watcher == null) continue;
                for (PresenceObserver obs : presenceObservers) {
                    obs.onPresenceChanged(user, p, watcher);
                }
            }
        }

        boolean isOnline(User user) {
            return presence.getOrDefault(user.getUserId(), Presence.OFFLINE) == Presence.ONLINE;
        }

        // groups
        Group createGroup(String groupId, String name, List<User> members) {
            Group g = new Group(groupId, name);
            for (User u : members) g.addMember(u);
            groups.put(groupId, g);
            System.out.println("👥 Created group: " + name + " (" + groupId + ")");
            return g;
        }

        // direct message
        void sendDirectText(User sender, User receiver, String content) {
            Message msg = messageFactory.createText(sender, List.of(receiver), content);
            deliver(receiver, msg);
        }

        // group message
        void sendGroupText(User sender, String groupId, String content) {
            Group g = groups.get(groupId);
            if (g == null) throw new IllegalArgumentException("Group not found: " + groupId);

            List<User> recipients = new ArrayList<>(g.members());
            recipients.remove(sender);
            if (recipients.isEmpty()) return;

            Message msg = messageFactory.createText(sender, recipients, "[Group:" + g.getName() + "] " + content);
            for (User r : recipients) deliver(r, msg);
        }

        private void deliver(User receiver, Message msg) {
            if (!isOnline(receiver)) {
                System.out.println("📥 " + receiver.getUsername() + " is OFFLINE. Queuing message...");
            }
            receiver.receive(msg);
        }
    }

    /* =========================
     *  Demo
     * ========================= */
    public static void main(String[] args) {
        ChatService chat = new ChatService();

        User john  = chat.onboardUser("u1", "John");
        User alice = chat.onboardUser("u2", "Alice");
        User bob   = chat.onboardUser("u3", "Bob");

        // NEW: Subscribe to presence updates
        chat.subscribePresence(john, bob);    // John wants notifications about Bob
        chat.subscribePresence(alice, bob);   // Alice wants notifications about Bob
        chat.subscribePresence(bob, john);    // Bob wants notifications about John

        chat.setPresence(john, Presence.ONLINE);
        chat.setPresence(alice, Presence.ONLINE);
        chat.setPresence(bob, Presence.OFFLINE);

        System.out.println("\n--- Bob comes ONLINE ---");
        chat.setPresence(bob, Presence.ONLINE);  // should notify John & Alice

        System.out.println("\n--- Direct message ---");
        chat.sendDirectText(john, alice, "Hello Alice!");
        alice.readAll();

        System.out.println("\n--- Group message ---");
        chat.createGroup("g1", "DevTeam", List.of(john, alice, bob));
        chat.sendGroupText(john, "g1", "Hi team!");
        alice.readAll();
        bob.readAll();

        System.out.println("\n--- Bob goes OFFLINE ---");
        chat.setPresence(bob, Presence.OFFLINE); // should notify John & Alice
    }
}
