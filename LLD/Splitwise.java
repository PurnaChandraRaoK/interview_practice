abstract class Expense {
    private final String id;
    private final double amount;
    private final User paidBy;
    private final List<Split> splits;
    private final ExpenseData expenseData;

    protected Expense(double amount, User paidBy, List<Split> splits, ExpenseData expenseData) {
        this.id = UUID.randomUUID().toString();
        this.amount = amount;
        this.paidBy = paidBy;
        this.splits = splits;
        this.expenseData = expenseData;
    }

    public String getId() { return id; }
    public double getAmount() { return amount; }
    public User getPaidBy() { return paidBy; }
    public List<Split> getSplits() { return splits; }
    public ExpenseData getExpenseData() { return expenseData; }

    public abstract boolean validate();
}

class ExpenseData {
    private final String name;
    public ExpenseData(String name) { this.name = name; }
    public String getName() { return name; }
}

enum ExpenseType { EQUAL, EXACT, PERCENT; }

class User {
    private final int userId;
    private final String userName;
    private final String email;
    private final String mobileNumber;

    public User(int userId, String userName, String email, String mobileNumber) {
        this.userId = userId;
        this.userName = userName;
        this.email = email;
        this.mobileNumber = mobileNumber;
    }

    public int getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getEmail() { return email; }
    public String getMobileNumber() { return mobileNumber; }
}

abstract class Split {
    private final User user;
    private double amount;

    protected Split(User user) { this.user = user; }

    public User getUser() { return user; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}

class EqualSplit extends Split { public EqualSplit(User user) { super(user); } }

class ExactSplit extends Split {
    public ExactSplit(User user, double amount) { super(user); setAmount(amount); }
}

class PercentSplit extends Split {
    private final double percent;
    public PercentSplit(User user, double percent) { super(user); this.percent = percent; }
    public double getPercent() { return percent; }
}

class EqualExpense extends Expense {
    public EqualExpense(double amount, User paidBy, List<Split> splits, ExpenseData data) {
        super(amount, paidBy, splits, data);
    }

    @Override
    public boolean validate() {
        for (Split s : getSplits()) if (!(s instanceof EqualSplit)) return false;
        return true;
    }
}

class ExactExpense extends Expense {
    public ExactExpense(double amount, User paidBy, List<Split> splits, ExpenseData data) {
        super(amount, paidBy, splits, data);
    }

    @Override
    public boolean validate() {
        double total = getAmount();
        double splitTotal = 0;
        for (Split s : getSplits()) {
            if (!(s instanceof ExactSplit)) return false;
            splitTotal += s.getAmount();
        }
        return Double.compare(total, splitTotal) == 0;
    }
}

class PercentageExpense extends Expense {
    public PercentageExpense(double amount, User paidBy, List<Split> splits, ExpenseData data) {
        super(amount, paidBy, splits, data);
    }

    @Override
    public boolean validate() {
        double percentTotal = 0;
        for (Split s : getSplits()) {
            if (!(s instanceof PercentSplit)) return false;
            percentTotal += ((PercentSplit) s).getPercent();
        }
        return Double.compare(100.0, percentTotal) == 0;
    }
}

interface UserRepository {
    void add(User user);
    User getByUserName(String userName);
}

class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> users = new HashMap<>();

    @Override
    public void add(User user) { users.put(user.getUserName(), user); }

    @Override
    public User getByUserName(String userName) { return users.get(userName); }
}

interface ExpenseRepository {
    void add(Expense e);
    List<Expense> all();
}

class InMemoryExpenseRepository implements ExpenseRepository {
    private final List<Expense> expenses = new ArrayList<>();

    @Override
    public void add(Expense e) { expenses.add(e); }

    @Override
    public List<Expense> all() { return Collections.unmodifiableList(expenses); }
}

class BalanceSheet {
    // balanceSheet[a][b] = money a should receive from b (positive means b owes a)
    private final Map<String, Map<String, Double>> sheet = new HashMap<>();

    public void ensureUser(String userName) {
        sheet.computeIfAbsent(userName, k -> new HashMap<>());
    }

    public void applyExpense(String paidBy, Expense expense) {
        ensureUser(paidBy);

        for (Split split : expense.getSplits()) {
            String paidTo = split.getUser().getUserName();
            ensureUser(paidTo);

            double amt = split.getAmount();

            // paidBy should receive amt from paidTo
            sheet.get(paidBy).put(paidTo, sheet.get(paidBy).getOrDefault(paidTo, 0.0) + amt);

            // paidTo owes paidBy => inverse entry
            sheet.get(paidTo).put(paidBy, sheet.get(paidTo).getOrDefault(paidBy, 0.0) - amt);
        }
    }

    public List<String> balancesFor(String userName) {
        List<String> out = new ArrayList<>();
        Map<String, Double> m = sheet.getOrDefault(userName, Collections.emptyMap());

        for (Map.Entry<String, Double> e : m.entrySet()) {
            if (Double.compare(e.getValue(), 0.0) != 0) {
                out.add(format(userName, e.getKey(), e.getValue()));
            }
        }
        return out;
    }

    public List<String> allBalances() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> a : sheet.entrySet()) {
            for (Map.Entry<String, Double> b : a.getValue().entrySet()) {
                if (b.getValue() > 0) out.add(format(a.getKey(), b.getKey(), b.getValue()));
            }
        }
        return out;
    }

    private String format(String user1, String user2, double amount) {
        // amount is balanceSheet[user1][user2]
        if (amount < 0) return user1 + " owes " + user2 + ": " + Math.abs(amount);
        if (amount > 0) return user2 + " owes " + user1 + ": " + Math.abs(amount);
        return "";
    }
}

class ExpenseFactory {
    public static Expense create(ExpenseType type, double amount, User paidBy, List<Split> splits, ExpenseData data) {
        switch (type) {
            case EXACT:
                return new ExactExpense(amount, paidBy, splits, data);

            case PERCENT:
                for (Split s : splits) {
                    PercentSplit ps = (PercentSplit) s;
                    s.setAmount((amount * ps.getPercent()) / 100.0);
                }
                return new PercentageExpense(amount, paidBy, splits, data);

            case EQUAL:
                int n = splits.size();
                double splitAmount = ((double) Math.round(amount * 100 / n)) / 100.0;
                for (Split s : splits) s.setAmount(splitAmount);
                return new EqualExpense(amount, paidBy, splits, data);

            default:
                throw new IllegalArgumentException("Unsupported expense type: " + type);
        }
    }
}

class UserService {
    private final UserRepository userRepo;
    private final BalanceSheet balanceSheet;

    public UserService(UserRepository userRepo, BalanceSheet balanceSheet) {
        this.userRepo = userRepo;
        this.balanceSheet = balanceSheet;
    }

    public void addUser(User user) {
        userRepo.add(user);
        balanceSheet.ensureUser(user.getUserName());
    }

    public User getUser(String userName) {
        return userRepo.getByUserName(userName);
    }
}

class SplitWiseService {
    private final UserRepository userRepo;
    private final ExpenseRepository expenseRepo;
    private final BalanceSheet balanceSheet;

    public SplitWiseService(UserRepository userRepo, ExpenseRepository expenseRepo, BalanceSheet balanceSheet) {
        this.userRepo = userRepo;
        this.expenseRepo = expenseRepo;
        this.balanceSheet = balanceSheet;
    }

    public void addExpense(ExpenseType type, double amount, String paidByName, List<Split> splits, ExpenseData data) {
        User paidBy = userRepo.getByUserName(paidByName);
        Expense expense = ExpenseFactory.create(type, amount, paidBy, splits, data);

        if (expense == null || !expense.validate()) {
            throw new IllegalArgumentException("Invalid expense input");
        }

        expenseRepo.add(expense);
        balanceSheet.applyExpense(paidByName, expense);
    }

    public void showBalance(String userName) {
        List<String> b = balanceSheet.balancesFor(userName);
        if (b.isEmpty()) System.out.println("No balances");
        else b.forEach(System.out::println);
    }

    public void showBalances() {
        List<String> b = balanceSheet.allBalances();
        if (b.isEmpty()) System.out.println("No balances");
        else b.forEach(System.out::println);
    }
}

public class Main {

    private static final String NO_BALANCES = "No balances";
    private static final String QUITTING = "Quiting...";
    private static final String INVALID_ARGS = "No Expected Argument Found";

    public static void main(String[] args) {

        // ---- Init repositories/services (same as your flow) ----
        ExpenseRepository expenseRepository = new ExpenseRepository();
        UserService userService = new UserService(expenseRepository);
        SplitWiseService splitWiseService = new SplitWiseService(expenseRepository);

        // ---- Sample Users (same as your flow) ----
        userService.addUser(new User(1, "u1", "u1@gmail.com", "9890098900"));
        userService.addUser(new User(2, "u2", "u2@gmail.com", "9999999999"));
        userService.addUser(new User(3, "u3", "u3@gmail.com", "9898989899"));
        userService.addUser(new User(4, "u4", "u4@gmail.com", "8976478292"));

        // ---- Command loop (keep same behavior) ----
        Scanner scan = new Scanner(System.in);
        while (true) {
            String line = scan.nextLine();
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            String[] commands = line.trim().split(" ");
            Type type = Type.of(commands[0]);

            switch (type) {
                case EXPENSE:
                    handleExpense(commands, userService, splitWiseService);
                    break;

                case SHOW:
                    handleShow(commands, splitWiseService);
                    break;

                case QUIT:
                    System.out.println(QUITTING);
                    return;

                default:
                    System.out.println(INVALID_ARGS);
                    break;
            }
        }
    }

    // -------------------- EXPENSE --------------------
    private static void handleExpense(
            String[] commands,
            UserService userService,
            SplitWiseService service
    ) {
        // Same parsing positions as your original:
        // EXPENSE <paidBy> <amount> <totalMembers> <members...> <EXPENSETYPE> <values...>
        String paidBy = commands[1];
        double amount = Double.parseDouble(commands[2]);
        int totalMembers = Integer.parseInt(commands[3]);

        // Same calculation: expenseIndex = 3 + totalMembers + 1
        int expenseIndex = 3 + totalMembers + 1;

        List<Split> splits = new ArrayList<>();
        ExpenseType expenseType = ExpenseType.of(commands[expenseIndex]);

        switch (expenseType) {
            case EQUAL:
                addSplits(splits, totalMembers, i -> new EqualSplit(userService.getUser(commands[4 + i])));
                service.addExpense(ExpenseType.EQUAL, amount, paidBy, splits, new ExpenseData("GoaFlight"));
                break;

            case EXACT:
                addSplits(splits, totalMembers, i -> {
                    User user = userService.getUser(commands[4 + i]);
                    double splitAmt = Double.parseDouble(commands[expenseIndex + i + 1]);
                    return new ExactSplit(user, splitAmt);
                });
                service.addExpense(ExpenseType.EXACT, amount, paidBy, splits, new ExpenseData("CabTickets"));
                break;

            case PERCENT:
                addSplits(splits, totalMembers, i -> {
                    User user = userService.getUser(commands[4 + i]);
                    double pct = Double.parseDouble(commands[expenseIndex + i + 1]);
                    return new PercentSplit(user, pct);
                });
                service.addExpense(ExpenseType.PERCENT, amount, paidBy, splits, new ExpenseData("Dinner"));
                break;

            default:
                System.out.println(INVALID_ARGS);
        }
    }

    private static void addSplits(List<Split> splits, int totalMembers, Function<Integer, Split> builder) {
        for (int i = 0; i < totalMembers; i++) {
            splits.add(builder.apply(i));
        }
    }

    // -------------------- SHOW --------------------
    private static void handleShow(String[] commands, SplitWiseService service) {
        // Same behavior:
        // SHOW -> showBalances()
        // SHOW <userName> -> showBalance(userName)
        if (commands.length == 1) {
            service.showBalances();
        } else {
            service.showBalance(commands[1]);
        }
    }
}

// EXPENSE u1 1000 4 u1 u2 u3 u4 EQUAL
// EXPENSE u2 1250 3 u1 u2 u3 EXACT 250 500 500
// EXPENSE u3 1200 3 u1 u3 u4 PERCENT 40 40 20
// SHOW
// SHOW u1
// QUIT
