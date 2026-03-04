public class SprintTaskSystemLLD {

    // Work item types
    enum WorkItemType { USER_STORY, BUG, TASK, WORK_ITEM }

    // ---------------------------
    // USER MODEL (Inheritance)
    // ---------------------------

    static abstract class User {
        private final long id;
        private final String name;

        protected User(long id, String name) {
            this.id = id;
            this.name = Objects.requireNonNull(name);
        }

        public long id() { return id; }
        public String name() { return name; }

        public abstract boolean isManager();

        @Override public String toString() {
            return getClass().getSimpleName() + "{" + id + ", " + name + "}";
        }
    }

    static final class EmployeeUser extends User {
        public EmployeeUser(long id, String name) { super(id, name); }
        @Override public boolean isManager() { return false; }
    }

    static final class ManagerUser extends User {
        private final List<EmployeeUser> directReports = new ArrayList<>();

        public ManagerUser(long id, String name) { super(id, name); }

        @Override public boolean isManager() { return true; }

        public List<EmployeeUser> directReports() { return Collections.unmodifiableList(directReports); }

        public void addReport(EmployeeUser employee) {
            directReports.add(Objects.requireNonNull(employee));
        }
    }

    // ---------------------------
    // Metadata
    // ---------------------------

    static final class TaskMetadata {
        private final String title;
        private final String description;
        private int estimatedHours;
        private final List<String> comments = new ArrayList<>();

        TaskMetadata(String title, String description, int estimatedHours) {
            this.title = Objects.requireNonNull(title);
            this.description = Objects.requireNonNull(description);
            if (estimatedHours < 0) throw new IllegalArgumentException("estimatedHours cannot be negative");
            this.estimatedHours = estimatedHours;
        }

        public String title() { return title; }
        public String description() { return description; }
        public int estimatedHours() { return estimatedHours; }
        public List<String> comments() { return Collections.unmodifiableList(comments); }

        public void addComment(String comment) { comments.add(Objects.requireNonNull(comment)); }
        public void updateEstimate(int hours) {
            if (hours < 0) throw new IllegalArgumentException("estimatedHours cannot be negative");
            this.estimatedHours = hours;
        }
    }

    // ---------------------------
    // Sprint
    // ---------------------------

    enum SprintStatus { ACTIVE, COMPLETED }

    static final class Sprint {
        private final long id;
        private final String name;
        private final ManagerUser manager;
        private SprintStatus status = SprintStatus.ACTIVE;

        Sprint(long id, String name, ManagerUser manager) {
            this.id = id;
            this.name = Objects.requireNonNull(name);
            this.manager = Objects.requireNonNull(manager);
        }

        public long id() { return id; }
        public String name() { return name; }
        public ManagerUser manager() { return manager; }
        public SprintStatus status() { return status; }

        void complete() { status = SprintStatus.COMPLETED; }

        @Override public String toString() { return "Sprint{" + id + ", " + name + ", " + status + "}"; }
    }

    // ---------------------------
    // Task State + Events
    // ---------------------------

    enum TaskStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

    interface TaskState {
        TaskStatus status();
        TaskState start();
        TaskState complete();
    }

    static final class NotStartedState implements TaskState {
        @Override public TaskStatus status() { return TaskStatus.NOT_STARTED; }
        @Override public TaskState start() { return new InProgressState(); }
        @Override public TaskState complete() { return new CompletedState(); }
    }

    static final class InProgressState implements TaskState {
        @Override public TaskStatus status() { return TaskStatus.IN_PROGRESS; }
        @Override public TaskState start() { return this; }
        @Override public TaskState complete() { return new CompletedState(); }
    }

    static final class CompletedState implements TaskState {
        @Override public TaskStatus status() { return TaskStatus.COMPLETED; }
        @Override public TaskState start() { return this; }
        @Override public TaskState complete() { return this; }
    }

    interface TaskEvent { long taskId(); }
    static final class TaskCompletedEvent implements TaskEvent {
        private final long taskId;
        TaskCompletedEvent(long taskId) { this.taskId = taskId; }
        @Override public long taskId() { return taskId; }
    }
    interface TaskEventListener { void onEvent(TaskEvent event); }

    static final class SprintMove {
        final long fromSprintId;
        final long toSprintId;
        final Instant movedAt;

        SprintMove(long fromSprintId, long toSprintId, Instant movedAt) {
            this.fromSprintId = fromSprintId;
            this.toSprintId = toSprintId;
            this.movedAt = movedAt;
        }
    }

    static final class Task {
        private final long id;
        private final WorkItemType type;
        private final TaskMetadata metadata;
        private final Instant createdAt;

        private User assignee;
        private Long sprintId;
        private Task parent;

        private TaskState state = new NotStartedState();
        private final List<Task> children = new ArrayList<>();
        private final List<SprintMove> sprintMoves = new ArrayList<>();
        private final List<TaskEventListener> listeners = new ArrayList<>();

        Task(long id, WorkItemType type, TaskMetadata metadata, User assignee, Instant createdAt) {
            this.id = id;
            this.type = Objects.requireNonNull(type);
            this.metadata = Objects.requireNonNull(metadata);
            this.assignee = assignee;
            this.createdAt = Objects.requireNonNull(createdAt);
        }

        public long id() { return id; }
        public WorkItemType type() { return type; }
        public TaskMetadata metadata() { return metadata; }
        public Instant createdAt() { return createdAt; }
        public User assignee() { return assignee; }
        public TaskStatus status() { return state.status(); }
        public Long sprintId() { return sprintId; }
        public List<Task> children() { return Collections.unmodifiableList(children); }
        public List<SprintMove> sprintMoves() { return Collections.unmodifiableList(sprintMoves); }

        public void assignTo(User user) { this.assignee = user; }

        public void subscribe(TaskEventListener listener) { listeners.add(listener); }
        private void publish(TaskEvent event) { for (TaskEventListener l : listeners) l.onEvent(event); }

        public void addChild(Task child) {
            Objects.requireNonNull(child);
            if (child.parent != null) throw new IllegalStateException("Child already has parent");
            child.parent = this;
            children.add(child);

            child.subscribe(e -> {
                if (e instanceof TaskCompletedEvent) this.tryAutoCompleteIfAllChildrenDone();
            });

            if (this.sprintId != null) {
                child.attachToSprint(this.sprintId, child.sprintId, Instant.now());
            }
        }

        public void attachToSprint(Long newSprintId, Long fromSprintId, Instant now) {
            Long prev = this.sprintId;
            this.sprintId = Objects.requireNonNull(newSprintId);

            if (prev != null && !Objects.equals(prev, newSprintId)) {
                sprintMoves.add(new SprintMove(prev, newSprintId, now));
            } else if (fromSprintId != null && !Objects.equals(fromSprintId, newSprintId)) {
                sprintMoves.add(new SprintMove(fromSprintId, newSprintId, now));
            }

            for (Task child : children) {
                child.attachToSprint(newSprintId, child.sprintId, now);
            }
        }

        public void start() { state = state.start(); }

        public void complete() {
            if (!children.isEmpty() && children.stream().anyMatch(c -> c.status() != TaskStatus.COMPLETED)) {
                throw new IllegalStateException("Cannot complete parent while children not completed");
            }
            TaskStatus before = state.status();
            state = state.complete();
            if (before != TaskStatus.COMPLETED && state.status() == TaskStatus.COMPLETED) {
                publish(new TaskCompletedEvent(id));
                if (parent != null) parent.tryAutoCompleteIfAllChildrenDone();
            }
        }

        private void tryAutoCompleteIfAllChildrenDone() {
            if (!children.isEmpty() && children.stream().allMatch(c -> c.status() == TaskStatus.COMPLETED)
                    && this.status() != TaskStatus.COMPLETED) {
                this.state = new CompletedState();
                publish(new TaskCompletedEvent(this.id));
            }
        }
    }

    // ---------------------------
    // Repositories
    // ---------------------------

    interface UserRepository {
        User save(User user);
        Optional<User> findById(long id);
        List<User> findAll();
    }

    static final class InMemoryUserRepository implements UserRepository {
        private final Map<Long, User> store = new HashMap<>();
        @Override public User save(User user) { store.put(user.id(), user); return user; }
        @Override public Optional<User> findById(long id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<User> findAll() { return new ArrayList<>(store.values()); }
    }

    interface TaskRepository {
        Task save(Task task);
        List<Task> findBySprint(long sprintId);
        List<Task> findAll();
    }

    static final class InMemoryTaskRepository implements TaskRepository {
        private final Map<Long, Task> store = new HashMap<>();
        @Override public Task save(Task task) { store.put(task.id(), task); return task; }
        @Override public List<Task> findBySprint(long sprintId) {
            return store.values().stream().filter(t -> Objects.equals(t.sprintId(), sprintId)).collect(Collectors.toList());
        }
        @Override public List<Task> findAll() { return new ArrayList<>(store.values()); }
    }

    interface SprintRepository {
        Sprint save(Sprint sprint);
        List<Sprint> findByManager(long managerId);
    }

    static final class InMemorySprintRepository implements SprintRepository {
        private final Map<Long, Sprint> store = new HashMap<>();
        @Override public Sprint save(Sprint sprint) { store.put(sprint.id(), sprint); return sprint; }
        @Override public List<Sprint> findByManager(long managerId) {
            return store.values().stream().filter(s -> s.manager().id() == managerId).collect(Collectors.toList());
        }
    }

    // ---------------------------
    // Factories + Services
    // ---------------------------

    interface IdGenerator { long nextId(); }
    static final class AtomicIdGenerator implements IdGenerator {
        private final AtomicLong seq = new AtomicLong(1);
        @Override public long nextId() { return seq.getAndIncrement(); }
    }

    interface Clock { Instant now(); }
    static final class SystemClock implements Clock { @Override public Instant now() { return Instant.now(); } }

    static final class TaskFactory {
        private final IdGenerator ids; private final Clock clock;
        TaskFactory(IdGenerator ids, Clock clock) { this.ids = ids; this.clock = clock; }
        Task create(WorkItemType type, String title, String desc, int hours, User assignee) {
            return new Task(ids.nextId(), type, new TaskMetadata(title, desc, hours), assignee, clock.now());
        }
    }

    static final class SprintFactory {
        private final IdGenerator ids;
        SprintFactory(IdGenerator ids) { this.ids = ids; }
        Sprint create(String name, ManagerUser manager) { return new Sprint(ids.nextId(), name, manager); }
    }

    static final class UserService {
        private final UserRepository userRepo;
        private final IdGenerator ids;

        UserService(UserRepository userRepo, IdGenerator ids) {
            this.userRepo = userRepo;
            this.ids = ids;
        }

        public ManagerUser onboardManager(String name) {
            ManagerUser m = new ManagerUser(ids.nextId(), name);
            userRepo.save(m);
            return m;
        }

        public EmployeeUser onboardEmployee(ManagerUser manager, String name) {
            EmployeeUser e = new EmployeeUser(ids.nextId(), name);
            userRepo.save(e);
            manager.addReport(e); // relationship lives inside manager
            userRepo.save(manager); // persist update
            return e;
        }
    }

    static final class TaskService {
        private final TaskRepository taskRepo;
        private final TaskFactory taskFactory;
        private final Clock clock;

        TaskService(TaskRepository taskRepo, TaskFactory taskFactory, Clock clock) {
            this.taskRepo = taskRepo;
            this.taskFactory = taskFactory;
            this.clock = clock;
        }

        public Task createWorkItem(WorkItemType type, String title, String desc, int hours, User assignee) {
            Task t = taskFactory.create(type, title, desc, hours, assignee);
            taskRepo.save(t);
            return t;
        }

        public Task createChildWorkItem(Task parent, WorkItemType type, String title, String desc, int hours, User assignee) {
            Task child = taskFactory.create(type, title, desc, hours, assignee);
            parent.addChild(child);
            taskRepo.save(child);
            taskRepo.save(parent);
            return child;
        }
        
        public void attachToSprint(Task task, Sprint sprint) {
            task.attachToSprint(sprint.id(), task.sprintId(), clock.now());
            taskRepo.save(task);
        }

        public void start(Task task) { task.start(); taskRepo.save(task); }
        public void complete(Task task) { task.complete(); taskRepo.save(task); }
    }

    static final class SprintService {
        private final SprintRepository sprintRepo;
        private final TaskRepository taskRepo;
        private final SprintFactory sprintFactory;
        private final Clock clock;

        SprintService(SprintRepository sprintRepo, TaskRepository taskRepo, SprintFactory sprintFactory, Clock clock) {
            this.sprintRepo = sprintRepo;
            this.taskRepo = taskRepo;
            this.sprintFactory = sprintFactory;
            this.clock = clock;
        }

        public Sprint startSprint(ManagerUser manager, String name) {
            Sprint s = sprintFactory.create(name, manager);
            sprintRepo.save(s);
            return s;
        }

        public void completeSprint(Sprint sprint) { sprint.complete(); sprintRepo.save(sprint); }

        public Sprint startNextSprintWithCarryOver(ManagerUser manager, Sprint previous, String newName) {
            Sprint next = sprintFactory.create(newName, manager);
            List<Task> prevTasks = taskRepo.findBySprint(previous.id());
            for (Task t : prevTasks) {
                if (t.status() != TaskStatus.COMPLETED) {
                    t.attachToSprint(next.id(), t.sprintId(), clock.now());
                    taskRepo.save(t);
                }
            }
            sprintRepo.save(next);
            return next;
        }
    }

    // ---------------------------
    // Dashboard (Strategy)
    // ---------------------------

    interface DashboardQuery {
        String name();
        List<Task> execute(ManagerUser manager,
                        Sprint currentSprint,
                        List<Sprint> allSprints,
                        List<Task> allTasks);
    }

    static final class CompletedTasksInSprintQuery implements DashboardQuery {
        private final long sprintId;

        CompletedTasksInSprintQuery(long sprintId) {
            this.sprintId = sprintId;
        }

        @Override
        public String name() {
            return "Completed Tasks in Sprint " + sprintId;
        }

        @Override
        public List<Task> execute(ManagerUser manager, Sprint currentSprint,
                                List<Sprint> allSprints, List<Task> allTasks) {
            return allTasks.stream()
                    .filter(t -> Objects.equals(t.sprintId(), sprintId))
                    .filter(t -> t.status() == TaskStatus.COMPLETED)
                    .collect(Collectors.toList());
        }
    }

    static final class CarriedOverTasksQuery implements DashboardQuery {
        private final long fromSprintId;
        private final long toSprintId;

        CarriedOverTasksQuery(long fromSprintId, long toSprintId) {
            this.fromSprintId = fromSprintId;
            this.toSprintId = toSprintId;
        }

        @Override
        public String name() {
            return "Tasks carried from Sprint " + fromSprintId + " to " + toSprintId;
        }

        @Override
        public List<Task> execute(ManagerUser manager, Sprint currentSprint,
                                List<Sprint> allSprints, List<Task> allTasks) {
            return allTasks.stream()
                    .filter(t -> t.sprintMoves().stream()
                            .anyMatch(m -> m.fromSprintId == fromSprintId && m.toSprintId == toSprintId))
                    .collect(Collectors.toList());
        }
    }

    static final class PendingTasksInSprintQuery implements DashboardQuery {

        @Override
        public String name() {
            return "Pending Tasks in Current Sprint";
        }

        @Override
        public List<Task> execute(ManagerUser manager, Sprint currentSprint,
                                List<Sprint> allSprints, List<Task> allTasks) {
            return allTasks.stream()
                    .filter(t -> Objects.equals(t.sprintId(), currentSprint.id()))
                    .filter(t -> t.status() != TaskStatus.COMPLETED)
                    .collect(Collectors.toList());
        }
    }


    static final class DashboardService {
        private final SprintRepository sprintRepo;
        private final TaskRepository taskRepo;

        DashboardService(SprintRepository sprintRepo, TaskRepository taskRepo) {
            this.sprintRepo = sprintRepo;
            this.taskRepo = taskRepo;
        }

        public Map<String, List<Task>> generate(
                ManagerUser manager,
                Sprint currentSprint,
                DashboardQuery... queries) {

            List<Sprint> sprints = sprintRepo.findByManager(manager.id());
            List<Task> tasks = taskRepo.findAll();

            Map<String, List<Task>> result = new LinkedHashMap<>();
            for (DashboardQuery q : queries) {
                result.put(q.name(), q.execute(manager, currentSprint, sprints, tasks));
            }
            return result;
        }
    }

    // ---------------------------
    // Demo
    // ---------------------------
    public static void main(String[] args) {
        IdGenerator ids = new AtomicIdGenerator();
        Clock clock = new SystemClock();

        UserRepository userRepo = new InMemoryUserRepository();
        TaskRepository taskRepo = new InMemoryTaskRepository();
        SprintRepository sprintRepo = new InMemorySprintRepository();

        UserService userService = new UserService(userRepo, ids);
        TaskFactory taskFactory = new TaskFactory(ids, clock);
        SprintFactory sprintFactory = new SprintFactory(ids);

        TaskService taskService = new TaskService(taskRepo, taskFactory, clock);
        SprintService sprintService = new SprintService(sprintRepo, taskRepo, sprintFactory, clock);

        ManagerUser mgr = userService.onboardManager("Kaushik");
        EmployeeUser e1 = userService.onboardEmployee(mgr, "Alice");
        EmployeeUser e2 = userService.onboardEmployee(mgr, "Bob");

        Sprint s1 = sprintService.startSprint(mgr, "Sprint-1");

        Task story = taskService.createWorkItem(WorkItemType.USER_STORY, "UserStory: Payment Flow", "Story", 16, mgr);
        taskService.attachToSprint(story, s1);

        Task t1 = taskService.createChildWorkItem(story, WorkItemType.TASK, "Task: API", "Integrate API", 8, e1);
        Task t2 = taskService.createChildWorkItem(story, WorkItemType.TASK, "Task: UI", "Update UI", 6, e2);

        taskService.start(t1); taskService.start(t2);
        taskService.complete(t1); taskService.complete(t2); // story auto completes

        Task bug = taskService.createWorkItem(WorkItemType.BUG, "Bug: Crash", "Crash on pay", 3, e1);
        taskService.setBugDetails(bug, "Open checkout -> Pay", "No crash");
        taskService.attachToSprint(bug, s1);
        taskService.start(bug); // will carry over

        sprintService.completeSprint(s1);

        Sprint s2 = sprintService.startNextSprintWithCarryOver(mgr, s1, "Sprint-2");
        System.out.println("Carry over -> bug sprintId=" + bug.sprintId() + ", newSprint=" + s2.id());

        DashboardService dashboardService =
        new DashboardService(sprintRepo, taskRepo);

        Map<String, List<Task>> dashboard =
                dashboardService.generate(
                        mgr,
                        s2,
                        new CompletedTasksInSprintQuery(s1.id()),
                        new CarriedOverTasksQuery(s1.id(), s2.id()),
                        new PendingTasksInSprintQuery()
                );

        System.out.println("\n=== MANAGER DASHBOARD ===");
        dashboard.forEach((title, tasks) -> {
            System.out.println("\n" + title);
            if (tasks.isEmpty()) {
                System.out.println("  (none)");
            } else {
                tasks.forEach(t -> System.out.println("  - " + t));
            }
        });
    }
}
