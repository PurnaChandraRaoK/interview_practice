import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Emergency Call Center - simplified LLD (Interview friendly)
 * Patterns used:
 *  - Strategy: RoutingStrategy chooses best dispatcher
 *  - Observer: CallListener listens to lifecycle events (logging/notifications)
 */
public class EmergencyCallCenter {

    // ---------- Enums ----------
    enum CallPriority {
        CRITICAL(1), HIGH(2), MEDIUM(3), LOW(4);
        private final int level;
        CallPriority(int level) { this.level = level; }
        public int level() { return level; }
    }

    enum CallType { FIRE, POLICE, MEDICAL, GENERAL_EMERGENCY }

    enum CallStatus { RECEIVED, QUEUED, IN_PROGRESS, DISPATCHED, RESOLVED, CANCELLED }

    enum DispatcherStatus { AVAILABLE, BUSY, OFFLINE }

    // ---------- Value Objects ----------
    static final class Caller {
        private final String id;
        private final String name;
        private final String phoneNumber;

        public Caller(String id, String name, String phoneNumber) {
            this.id = id;
            this.name = name;
            this.phoneNumber = phoneNumber;
        }

        public String id() { return id; }
        public String name() { return name; }
        public String phoneNumber() { return phoneNumber; }
    }

    static final class Location {
        private final String address;
        private final double latitude;
        private final double longitude;

        public Location(String address, double latitude, double longitude) {
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String address() { return address; }
        public double latitude() { return latitude; }
        public double longitude() { return longitude; }

        @Override public String toString() {
            return address + " (" + latitude + ", " + longitude + ")";
        }
    }

    // ---------- Core Entities ----------
    static final class Dispatcher {
        private final String id;
        private final String name;
        private final Set<CallType> skills;
        private DispatcherStatus status = DispatcherStatus.AVAILABLE;
        private int activeCalls = 0;

        public Dispatcher(String id, String name, Set<CallType> skills) {
            this.id = id;
            this.name = name;
            this.skills = new HashSet<>(skills);
        }

        public String id() { return id; }
        public String name() { return name; }
        public Set<CallType> skills() { return Collections.unmodifiableSet(skills); }
        public DispatcherStatus status() { return status; }
        public int activeCalls() { return activeCalls; }

        private void markBusy() { status = DispatcherStatus.BUSY; }
        private void markAvailable() { status = DispatcherStatus.AVAILABLE; }
        private void incCalls() { activeCalls++; }
        private void decCalls() { activeCalls = Math.max(0, activeCalls - 1); }

        @Override public String toString() {
            return "Dispatcher{" + name + ", status=" + status + ", activeCalls=" + activeCalls + "}";
        }
    }

    static final class EmergencyCall {
        private final String id;
        private final Caller caller;
        private final CallType type;
        private final CallPriority priority;
        private final String description;
        private final Location location;
        private final LocalDateTime createdAt;

        private volatile CallStatus status = CallStatus.RECEIVED;
        private volatile LocalDateTime resolvedAt;
        private volatile Dispatcher assignedDispatcher;

        public EmergencyCall(String id, Caller caller, CallType type, CallPriority priority,
                             String description, Location location) {
            this.id = id;
            this.caller = caller;
            this.type = type;
            this.priority = priority;
            this.description = description;
            this.location = location;
            this.createdAt = LocalDateTime.now();
        }

        public String id() { return id; }
        public Caller caller() { return caller; }
        public CallType type() { return type; }
        public CallPriority priority() { return priority; }
        public String description() { return description; }
        public Location location() { return location; }
        public LocalDateTime createdAt() { return createdAt; }
        public CallStatus status() { return status; }
        public Dispatcher assignedDispatcher() { return assignedDispatcher; }
        public LocalDateTime resolvedAt() { return resolvedAt; }

        private void status(CallStatus s) { this.status = s; }
        private void assign(Dispatcher d) { this.assignedDispatcher = d; }
        private void resolvedNow() { this.resolvedAt = LocalDateTime.now(); }

        public long durationMinutes() {
            if (resolvedAt == null) return 0;
            return Duration.between(createdAt, resolvedAt).toMinutes();
        }
    }

    // ---------- Strategy: routing ----------
    interface RoutingStrategy {
        Dispatcher choose(EmergencyCall call, List<Dispatcher> dispatchers);
    }

    /**
     * Simple & effective:
     *  1) Prefer AVAILABLE dispatcher with matching skill
     *  2) Else any AVAILABLE dispatcher
     *  3) Tie-breaker: lower activeCalls
     */
    static final class DefaultRoutingStrategy implements RoutingStrategy {
        @Override
        public Dispatcher choose(EmergencyCall call, List<Dispatcher> dispatchers) {
            return dispatchers.stream()
                    .filter(d -> d.status() == DispatcherStatus.AVAILABLE)
                    .sorted(Comparator.comparingInt(Dispatcher::activeCalls))
                    .filter(d -> d.skills().contains(call.type()))
                    .findFirst()
                    .orElseGet(() -> dispatchers.stream()
                            .filter(d -> d.status() == DispatcherStatus.AVAILABLE)
                            .min(Comparator.comparingInt(Dispatcher::activeCalls))
                            .orElse(null));
        }
    }

    // ---------- Observer: listeners ----------
    interface CallListener {
        default void onReceived(EmergencyCall call) {}
        default void onAssigned(EmergencyCall call, Dispatcher dispatcher) {}
        default void onDispatched(EmergencyCall call) {}
        default void onResolved(EmergencyCall call) {}
        default void onCancelled(EmergencyCall call) {}
    }

    static final class CallLogger implements CallListener {
        @Override public void onReceived(EmergencyCall call) {
            System.out.println("LOG: Received call=" + call.id() + " type=" + call.type()
                    + " priority=" + call.priority() + " location=" + call.location());
        }

        @Override public void onAssigned(EmergencyCall call, Dispatcher dispatcher) {
            System.out.println("LOG: Assigned call=" + call.id() + " -> " + dispatcher.name());
        }

        @Override public void onDispatched(EmergencyCall call) {
            System.out.println("LOG: Dispatched for call=" + call.id());
        }

        @Override public void onResolved(EmergencyCall call) {
            System.out.println("LOG: Resolved call=" + call.id() + " duration=" + call.durationMinutes() + " mins");
        }

        @Override public void onCancelled(EmergencyCall call) {
            System.out.println("LOG: Cancelled call=" + call.id());
        }
    }

    static final class CriticalAlertListener implements CallListener {
        @Override public void onReceived(EmergencyCall call) {
            if (call.priority() == CallPriority.CRITICAL) {
                System.out.println("ALERT: CRITICAL call received! id=" + call.id());
            }
        }
    }

    // ---------- CallCenter (Orchestrator) ----------
    static final class CallCenter {
        private final List<Dispatcher> dispatchers = new ArrayList<>();
        private final List<CallListener> listeners = new ArrayList<>();
        private final Map<String, EmergencyCall> callsById = new HashMap<>();

        // Higher priority first, then older calls first
        private final PriorityBlockingQueue<EmergencyCall> queue =
                new PriorityBlockingQueue<>(11, (a, b) -> {
                    int p = Integer.compare(a.priority().level(), b.priority().level());
                    if (p != 0) return p;
                    return a.createdAt().compareTo(b.createdAt());
                });

        private RoutingStrategy routingStrategy = new DefaultRoutingStrategy();

        public synchronized void registerDispatcher(Dispatcher dispatcher) {
            dispatchers.add(dispatcher);
        }

        public synchronized void addListener(CallListener listener) {
            listeners.add(listener);
        }

        public synchronized void setRoutingStrategy(RoutingStrategy strategy) {
            this.routingStrategy = (strategy == null) ? new DefaultRoutingStrategy() : strategy;
        }

        public synchronized void receive(EmergencyCall call) {
            callsById.put(call.id(), call);
            call.status(CallStatus.QUEUED);
            queue.offer(call);
            listeners.forEach(l -> l.onReceived(call));
            tryAssignNext(); // opportunistic assignment
        }

        public synchronized boolean tryAssignNext() {
            EmergencyCall call = queue.peek();
            if (call == null) return false;

            Dispatcher dispatcher = routingStrategy.choose(call, dispatchers);
            if (dispatcher == null) return false; // no one available now

            // Remove only when we are sure we can assign
            queue.poll();

            dispatcher.markBusy();
            dispatcher.incCalls();

            call.assign(dispatcher);
            call.status(CallStatus.IN_PROGRESS);
            listeners.forEach(l -> l.onAssigned(call, dispatcher));
            return true;
        }

        public synchronized void dispatch(String callId) {
            EmergencyCall call = callsById.get(callId);
            if (call == null) throw new IllegalArgumentException("Unknown callId=" + callId);

            if (call.status() != CallStatus.IN_PROGRESS)
                throw new IllegalStateException("Call must be IN_PROGRESS to dispatch. Current=" + call.status());

            call.status(CallStatus.DISPATCHED);
            listeners.forEach(l -> l.onDispatched(call));
        }

        public synchronized void resolve(String callId) {
            EmergencyCall call = callsById.get(callId);
            if (call == null) throw new IllegalArgumentException("Unknown callId=" + callId);

            if (call.status() == CallStatus.RESOLVED || call.status() == CallStatus.CANCELLED) return;

            if (call.status() != CallStatus.IN_PROGRESS && call.status() != CallStatus.DISPATCHED)
                throw new IllegalStateException("Call must be IN_PROGRESS/DISPATCHED to resolve. Current=" + call.status());

            call.status(CallStatus.RESOLVED);
            call.resolvedNow();

            Dispatcher d = call.assignedDispatcher();
            if (d != null) {
                d.decCalls();
                d.markAvailable();
            }

            listeners.forEach(l -> l.onResolved(call));
            tryAssignNext(); // after freeing dispatcher, assign next
        }

        public synchronized void cancel(String callId) {
            EmergencyCall call = callsById.get(callId);
            if (call == null) throw new IllegalArgumentException("Unknown callId=" + callId);

            if (call.status() == CallStatus.RESOLVED || call.status() == CallStatus.CANCELLED) return;

            // If queued, remove from queue
            if (call.status() == CallStatus.QUEUED) {
                queue.remove(call);
            }

            // If assigned, free dispatcher
            Dispatcher d = call.assignedDispatcher();
            if (d != null) {
                d.decCalls();
                d.markAvailable();
            }

            call.status(CallStatus.CANCELLED);
            listeners.forEach(l -> l.onCancelled(call));
            tryAssignNext();
        }

        public synchronized EmergencyCall get(String callId) {
            return callsById.get(callId);
        }

        public synchronized int queuedCount() { return queue.size(); }
    }

    // ---------- Simple ID generator ----------
    static final class Ids {
        private static final AtomicLong seq = new AtomicLong(1000);
        public static String nextCallId() { return "C" + seq.incrementAndGet(); }
    }

    // ---------- Demo ----------
    public static void main(String[] args) {
        CallCenter center = new CallCenter();
        center.addListener(new CallLogger());
        center.addListener(new CriticalAlertListener());

        center.registerDispatcher(new Dispatcher("D1", "Alice",
                EnumSet.of(CallType.MEDICAL, CallType.GENERAL_EMERGENCY)));
        center.registerDispatcher(new Dispatcher("D2", "Bob",
                EnumSet.of(CallType.FIRE, CallType.POLICE)));
        center.registerDispatcher(new Dispatcher("D3", "Charlie",
                EnumSet.of(CallType.GENERAL_EMERGENCY)));

        Caller caller = new Caller("U1", "Raj", "+91-90000-00000");
        Location loc = new Location("Nallagandla, Hyderabad", 17.4700, 78.3600);

        EmergencyCall medical = new EmergencyCall(Ids.nextCallId(), caller, CallType.MEDICAL,
                CallPriority.CRITICAL, "Severe chest pain", loc);
        EmergencyCall fire = new EmergencyCall(Ids.nextCallId(), caller, CallType.FIRE,
                CallPriority.HIGH, "Kitchen fire", loc);

        center.receive(medical);
        center.receive(fire);

        // simulate operator actions
        center.dispatch(medical.id());
        center.resolve(medical.id());

        center.tryAssignNext(); // ensure next gets assigned if possible
        center.dispatch(fire.id());
        center.resolve(fire.id());
    }
}
