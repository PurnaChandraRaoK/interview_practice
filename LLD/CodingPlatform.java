import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Online Coding Platform - LLD oriented
 * Focus: Flow, Structure, SOLID, Patterns, Readability, Extensibility
 */
public class OnlineCodingPlatform {

    // -------------------- User Management --------------------

    public static class User {
        private final String userId;
        private String username;
        private String email;
        private UserRole role;

        // Safer default initialization
        private final List<Submission> submissions = new ArrayList<>();
        private int totalScore;

        public User(String userId, String username, String email, UserRole role) {
            this.userId = requireNonNull(userId);
            this.username = username;
            this.email = email;
            this.role = role;
        }

        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public UserRole getRole() { return role; }
        public List<Submission> getSubmissions() { return submissions; }
        public int getTotalScore() { return totalScore; }

        public void setUsername(String username) { this.username = username; }
        public void setEmail(String email) { this.email = email; }
        public void setRole(UserRole role) { this.role = role; }
        public void setTotalScore(int totalScore) { this.totalScore = totalScore; }

        public void addSubmission(Submission submission) {
            submissions.add(requireNonNull(submission));
        }
    }

    public enum UserRole { STUDENT, PREMIUM_USER, ADMIN }

    // -------------------- Problem Domain --------------------

    public static class Problem {
        private String problemId;
        private String title;
        private String description;
        private Difficulty difficulty;

        private final List<String> tags = new ArrayList<>();
        private final List<TestCase> testCases = new ArrayList<>();

        // Optional fields (kept from your design)
        private String functionSignature;
        private int acceptanceRate;
        private int totalSubmissions;

        public String getProblemId() { return problemId; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public Difficulty getDifficulty() { return difficulty; }
        public List<String> getTags() { return tags; }
        public List<TestCase> getTestCases() { return testCases; }
        public String getFunctionSignature() { return functionSignature; }
        public int getAcceptanceRate() { return acceptanceRate; }
        public int getTotalSubmissions() { return totalSubmissions; }

        public void setProblemId(String problemId) { this.problemId = problemId; }
        public void setTitle(String title) { this.title = title; }
        public void setDescription(String description) { this.description = description; }
        public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
        public void setFunctionSignature(String functionSignature) { this.functionSignature = functionSignature; }
        public void setAcceptanceRate(int acceptanceRate) { this.acceptanceRate = acceptanceRate; }
        public void setTotalSubmissions(int totalSubmissions) { this.totalSubmissions = totalSubmissions; }
    }

    public enum Difficulty {
        EASY(1), MEDIUM(2), HARD(3);

        private final int points;
        Difficulty(int points) { this.points = points; }
        public int getPoints() { return points; }
    }

    public static class TestCase {
        private String input;
        private String expectedOutput;
        private boolean hidden;

        public TestCase() {}

        public TestCase(String input, String expectedOutput, boolean hidden) {
            this.input = input;
            this.expectedOutput = expectedOutput;
            this.hidden = hidden;
        }

        public String getInput() { return input; }
        public String getExpectedOutput() { return expectedOutput; }
        public boolean isHidden() { return hidden; }

        public void setInput(String input) { this.input = input; }
        public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }
        public void setHidden(boolean hidden) { this.hidden = hidden; }
    }

    // -------------------- Builder Pattern (kept) --------------------

    public static class ProblemBuilder {
        private final Problem problem;

        public ProblemBuilder() {
            this.problem = new Problem();
            // Provide a default id for demo usage
            this.problem.setProblemId(UUID.randomUUID().toString());
        }

        public ProblemBuilder setTitle(String title) {
            problem.setTitle(title);
            return this;
        }

        public ProblemBuilder setDescription(String description) {
            problem.setDescription(description);
            return this;
        }

        public ProblemBuilder setDifficulty(Difficulty difficulty) {
            problem.setDifficulty(difficulty);
            return this;
        }

        public ProblemBuilder addTestCase(String input, String output) {
            TestCase tc = new TestCase(input, output, false);
            problem.getTestCases().add(tc);
            return this;
        }

        public ProblemBuilder addTag(String tag) {
            problem.getTags().add(tag);
            return this;
        }

        public Problem build() {
            return problem;
        }
    }

    // -------------------- Factory Pattern (kept, but made consistent) --------------------

    public interface ProblemFactory {
        Problem createProblem(String title, String description, Difficulty difficulty);
    }

    public static class AlgorithmProblemFactory implements ProblemFactory {
        @Override
        public Problem createProblem(String title, String description, Difficulty difficulty) {
            // Using Builder under the hood for consistency
            return new ProblemBuilder()
                    .setTitle(title)
                    .setDescription(description)
                    .setDifficulty(difficulty)
                    .build();
        }
    }

    public static class DatabaseProblemFactory implements ProblemFactory {
        @Override
        public Problem createProblem(String title, String description, Difficulty difficulty) {
            return new ProblemBuilder()
                    .setTitle(title)
                    .setDescription(description)
                    .setDifficulty(difficulty)
                    .build();
        }
    }

    // -------------------- Submission Domain --------------------

    public static class Submission {
        private String submissionId;
        private String userId;
        private String problemId;

        private String code;
        private Language language;
        private SubmissionStatus status;

        private long submissionTime;
        private int executionTime;

        private String errorMessage;
        private List<TestCaseResult> testResults = new ArrayList<>();

        public String getSubmissionId() { return submissionId; }
        public String getUserId() { return userId; }
        public String getProblemId() { return problemId; }
        public String getCode() { return code; }
        public Language getLanguage() { return language; }
        public SubmissionStatus getStatus() { return status; }
        public long getSubmissionTime() { return submissionTime; }
        public int getExecutionTime() { return executionTime; }
        public String getErrorMessage() { return errorMessage; }
        public List<TestCaseResult> getTestResults() { return testResults; }

        public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }
        public void setUserId(String userId) { this.userId = userId; }
        public void setProblemId(String problemId) { this.problemId = problemId; }
        public void setCode(String code) { this.code = code; }
        public void setLanguage(Language language) { this.language = language; }
        public void setStatus(SubmissionStatus status) { this.status = status; }
        public void setSubmissionTime(long submissionTime) { this.submissionTime = submissionTime; }
        public void setExecutionTime(int executionTime) { this.executionTime = executionTime; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public void setTestResults(List<TestCaseResult> testResults) {
            this.testResults = (testResults == null) ? new ArrayList<>() : testResults;
        }
    }

    public enum Language {
        JAVA("java"),
        PYTHON("python"),
        CPP("cpp"),
        JAVASCRIPT("javascript");

        private final String extension;
        Language(String extension) { this.extension = extension; }
        public String getExtension() { return extension; }
    }

    public enum SubmissionStatus {
        PENDING,
        RUNNING,
        ACCEPTED,
        WRONG_ANSWER,
        TIME_LIMIT_EXCEEDED,
        COMPILATION_ERROR,
        RUNTIME_ERROR,
        FAILED // small addition for robustness (not a major logic change)
    }

    public static class TestCaseResult {
        private String input;
        private String expectedOutput;
        private String actualOutput;
        private boolean passed;
        private long executionTime;

        public String getInput() { return input; }
        public String getExpectedOutput() { return expectedOutput; }
        public String getActualOutput() { return actualOutput; }
        public boolean isPassed() { return passed; }
        public long getExecutionTime() { return executionTime; }

        public void setInput(String input) { this.input = input; }
        public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }
        public void setActualOutput(String actualOutput) { this.actualOutput = actualOutput; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public void setExecutionTime(long executionTime) { this.executionTime = executionTime; }
    }

    // -------------------- Strategy Pattern for Code Execution --------------------

    public interface CodeExecutionStrategy {
        ExecutionResult execute(String code, Language language, List<TestCase> testCases);
    }

    public static class LocalExecutionStrategy implements CodeExecutionStrategy {
        @Override
        public ExecutionResult execute(String code, Language language, List<TestCase> testCases) {
            // Placeholder - real impl would sandbox locally
            ExecutionResult result = new ExecutionResult();
            result.setStatus(SubmissionStatus.ACCEPTED);
            result.setTotalExecutionTime(12);
            result.setTestResults(Collections.emptyList());
            return result;
        }
    }

    public static class DockerExecutionStrategy implements CodeExecutionStrategy {
        @Override
        public ExecutionResult execute(String code, Language language, List<TestCase> testCases) {
            ExecutionResult result = new ExecutionResult();
            result.setStatus(SubmissionStatus.ACCEPTED);
            result.setTotalExecutionTime(25);
            result.setTestResults(Collections.emptyList());
            return result;
        }
    }

    public static class CloudExecutionStrategy implements CodeExecutionStrategy {
        @Override
        public ExecutionResult execute(String code, Language language, List<TestCase> testCases) {
            ExecutionResult result = new ExecutionResult();
            result.setStatus(SubmissionStatus.ACCEPTED);
            result.setTotalExecutionTime(40);
            result.setTestResults(Collections.emptyList());
            return result;
        }
    }

    public static class ExecutionResult {
        private SubmissionStatus status;
        private List<TestCaseResult> testResults = new ArrayList<>();
        private long totalExecutionTime;
        private String errorMessage;

        public SubmissionStatus getStatus() { return status; }
        public List<TestCaseResult> getTestResults() { return testResults; }
        public long getTotalExecutionTime() { return totalExecutionTime; }
        public String getErrorMessage() { return errorMessage; }

        public void setStatus(SubmissionStatus status) { this.status = status; }
        public void setTestResults(List<TestCaseResult> testResults) {
            this.testResults = (testResults == null) ? new ArrayList<>() : testResults;
        }
        public void setTotalExecutionTime(long totalExecutionTime) { this.totalExecutionTime = totalExecutionTime; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public static class CodeExecutor {
        private CodeExecutionStrategy strategy;

        public CodeExecutor(CodeExecutionStrategy strategy) {
            this.strategy = requireNonNull(strategy);
        }

        public void setStrategy(CodeExecutionStrategy strategy) {
            this.strategy = requireNonNull(strategy);
        }

        public ExecutionResult executeCode(String code, Language language, List<TestCase> testCases) {
            return strategy.execute(code, language, testCases);
        }
    }

    // -------------------- Command Pattern for Submission Processing --------------------

    public interface Command { void execute(); }

    public static class SubmissionCommand implements Command {
        private final SubmissionService submissionService;
        private final Submission submission;

        public SubmissionCommand(SubmissionService submissionService, Submission submission) {
            this.submissionService = requireNonNull(submissionService);
            this.submission = requireNonNull(submission);
        }

        @Override
        public void execute() {
            submissionService.processSubmission(submission);
        }
    }

    public static class SubmissionInvoker {
        private final Queue<Command> commandQueue = new ConcurrentLinkedQueue<>();

        public void addCommand(Command command) {
            commandQueue.offer(requireNonNull(command));
        }

        public void processCommands() {
            while (!commandQueue.isEmpty()) {
                Command command = commandQueue.poll();
                if (command != null) command.execute();
            }
        }
    }

    // -------------------- Observer Pattern (type-safe version) --------------------

    public enum SubmissionEventType {
        SUBMISSION_PROCESSED,
        SUBMISSION_ACCEPTED
    }

    public static class SubmissionEvent {
        private final SubmissionEventType type;
        private final Submission submission;

        public SubmissionEvent(SubmissionEventType type, Submission submission) {
            this.type = requireNonNull(type);
            this.submission = requireNonNull(submission);
        }

        public SubmissionEventType getType() { return type; }
        public Submission getSubmission() { return submission; }
    }

    public interface Observer {
        void onEvent(SubmissionEvent event);
    }

    public interface Subject {
        void addObserver(Observer observer);
        void removeObserver(Observer observer);
        void notifyObservers(SubmissionEvent event);
    }

    public static class LeaderboardObserver implements Observer {
        @Override
        public void onEvent(SubmissionEvent event) {
            if (event.getType() == SubmissionEventType.SUBMISSION_ACCEPTED) {
                updateLeaderboard(event.getSubmission());
            }
        }

        private void updateLeaderboard(Submission submission) {
            // Update leaderboard logic
        }
    }

    public static class NotificationObserver implements Observer {
        @Override
        public void onEvent(SubmissionEvent event) {
            if (event.getType() == SubmissionEventType.SUBMISSION_PROCESSED) {
                sendNotification(event.getSubmission());
            }
        }

        private void sendNotification(Submission submission) {
            // Send notification logic
        }
    }

    // -------------------- Singleton Pattern for System Configuration --------------------

    public static class SystemConfiguration {
        private static volatile SystemConfiguration instance;

        private static final String KEY_MAX_EXECUTION_TIME = "max.execution.time";
        private static final String KEY_MAX_MEMORY_LIMIT  = "max.memory.limit";

        private final Properties config = new Properties();

        private SystemConfiguration() {
            loadConfiguration();
        }

        public static SystemConfiguration getInstance() {
            if (instance == null) {
                synchronized (SystemConfiguration.class) {
                    if (instance == null) {
                        instance = new SystemConfiguration();
                    }
                }
            }
            return instance;
        }

        private void loadConfiguration() {
            // Load configuration from file or environment (placeholder)
            config.setProperty(KEY_MAX_EXECUTION_TIME, "5000");
            config.setProperty(KEY_MAX_MEMORY_LIMIT, "128MB");
        }

        public String getProperty(String key) {
            return config.getProperty(key);
        }
    }

    // -------------------- Repository Pattern for Data Access --------------------

    public interface Repository<T> {
        T save(T entity);
        T findById(String id);
        List<T> findAll();
        void delete(String id);
    }

    public static class ProblemRepository implements Repository<Problem> {
        private final Map<String, Problem> problems = new ConcurrentHashMap<>();

        @Override
        public Problem save(Problem problem) {
            requireNonNull(problem);
            requireNonNull(problem.getProblemId());
            problems.put(problem.getProblemId(), problem);
            return problem;
        }

        @Override
        public Problem findById(String id) {
            return problems.get(id);
        }

        @Override
        public List<Problem> findAll() {
            return new ArrayList<>(problems.values());
        }

        @Override
        public void delete(String id) {
            problems.remove(id);
        }

        public List<Problem> findByDifficulty(Difficulty difficulty) {
            return problems.values()
                    .stream()
                    .filter(p -> p.getDifficulty() == difficulty)
                    .collect(Collectors.toList());
        }
    }

    public static class SubmissionRepository implements Repository<Submission> {
        private final Map<String, Submission> submissions = new ConcurrentHashMap<>();

        @Override
        public Submission save(Submission submission) {
            requireNonNull(submission);
            requireNonNull(submission.getSubmissionId());
            submissions.put(submission.getSubmissionId(), submission);
            return submission;
        }

        @Override
        public Submission findById(String id) {
            return submissions.get(id);
        }

        @Override
        public List<Submission> findAll() {
            return new ArrayList<>(submissions.values());
        }

        @Override
        public void delete(String id) {
            submissions.remove(id);
        }

        public List<Submission> findByUserId(String userId) {
            return submissions.values()
                    .stream()
                    .filter(s -> Objects.equals(s.getUserId(), userId))
                    .collect(Collectors.toList());
        }
    }

    // -------------------- Service Layer --------------------

    public static class SubmissionService implements Subject {
        private final List<Observer> observers = new ArrayList<>();

        private final SubmissionRepository submissionRepository;
        private final ProblemRepository problemRepository;
        private final CodeExecutor codeExecutor;

        public SubmissionService(SubmissionRepository submissionRepository,
                                 ProblemRepository problemRepository,
                                 CodeExecutor codeExecutor) {
            this.submissionRepository = requireNonNull(submissionRepository);
            this.problemRepository = requireNonNull(problemRepository);
            this.codeExecutor = requireNonNull(codeExecutor);
        }

        public void processSubmission(Submission submission) {
            requireNonNull(submission);

            // Minimal correctness: ensure id exists
            if (submission.getSubmissionId() == null) {
                submission.setSubmissionId(UUID.randomUUID().toString());
            }

            submission.setStatus(SubmissionStatus.RUNNING);
            submission.setSubmissionTime(System.currentTimeMillis());
            submissionRepository.save(submission);

            Problem problem = problemRepository.findById(submission.getProblemId());
            if (problem == null) {
                submission.setStatus(SubmissionStatus.FAILED);
                submission.setErrorMessage("Problem not found: " + submission.getProblemId());
                submissionRepository.save(submission);

                notifyObservers(new SubmissionEvent(SubmissionEventType.SUBMISSION_PROCESSED, submission));
                return;
            }

            ExecutionResult result = codeExecutor.executeCode(
                    submission.getCode(),
                    submission.getLanguage(),
                    problem.getTestCases()
            );

            submission.setStatus(result.getStatus());
            submission.setTestResults(result.getTestResults());
            submission.setExecutionTime((int) result.getTotalExecutionTime());
            submission.setErrorMessage(result.getErrorMessage());

            submissionRepository.save(submission);

            notifyObservers(new SubmissionEvent(SubmissionEventType.SUBMISSION_PROCESSED, submission));

            if (submission.getStatus() == SubmissionStatus.ACCEPTED) {
                notifyObservers(new SubmissionEvent(SubmissionEventType.SUBMISSION_ACCEPTED, submission));
            }
        }

        @Override
        public void addObserver(Observer observer) {
            observers.add(requireNonNull(observer));
        }

        @Override
        public void removeObserver(Observer observer) {
            observers.remove(observer);
        }

        @Override
        public void notifyObservers(SubmissionEvent event) {
            for (Observer observer : observers) {
                observer.onEvent(event);
            }
        }
    }

    public static class ProblemService {
        private final ProblemRepository problemRepository;

        public ProblemService(ProblemRepository problemRepository) {
            this.problemRepository = requireNonNull(problemRepository);
        }

        public Problem createProblem(String title, String description, Difficulty difficulty) {
            Problem problem = new ProblemBuilder()
                    .setTitle(title)
                    .setDescription(description)
                    .setDifficulty(difficulty)
                    .build();

            return problemRepository.save(problem);
        }

        public List<Problem> getProblemsByDifficulty(Difficulty difficulty) {
            return problemRepository.findByDifficulty(difficulty);
        }
    }

    // -------------------- Main / Demo --------------------

    public static void main(String[] args) {

        // Initialize repositories
        ProblemRepository problemRepo = new ProblemRepository();
        SubmissionRepository submissionRepo = new SubmissionRepository();

        // Initialize execution strategy + executor
        CodeExecutionStrategy strategy = new DockerExecutionStrategy();
        CodeExecutor executor = new CodeExecutor(strategy);

        // Initialize services
        SubmissionService submissionService = new SubmissionService(submissionRepo, problemRepo, executor);
        ProblemService problemService = new ProblemService(problemRepo);

        // Add observers
        submissionService.addObserver(new LeaderboardObserver());
        submissionService.addObserver(new NotificationObserver());

        // Create a sample problem
        Problem problem = problemService.createProblem(
                "Two Sum",
                "Find two numbers that add up to target",
                Difficulty.EASY
        );

        // Add some test cases (minimal demo)
        problem.getTestCases().add(new TestCase("nums=[2,7,11,15], target=9", "[0,1]", false));
        problemRepo.save(problem);

        // Create a submission
        Submission submission = new Submission();
        submission.setUserId("user123");
        submission.setProblemId(problem.getProblemId());
        submission.setCode("public int[] twoSum(int[] nums, int target) { ... }");
        submission.setLanguage(Language.JAVA);

        // Command pattern usage
        SubmissionInvoker invoker = new SubmissionInvoker();
        Command submissionCommand = new SubmissionCommand(submissionService, submission);

        invoker.addCommand(submissionCommand);
        invoker.processCommands();
    }
}
