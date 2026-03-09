import java.util.*;
import java.util.concurrent.locks.*;

// ========================== ENUMS ==========================

enum TransactionType { WITHDRAW, DEPOSIT, CHANGE_PIN, BALANCE_INQUIRY }

enum DenominationType {
    HUNDRED(100),
    TWO_HUNDRED(200),
    FIVE_HUNDRED(500),
    TWO_THOUSAND(2000);

    private final int value;
    DenominationType(int value) { this.value = value; }
    public int getValue() { return value; }
}

// ========================== CARD & ACCOUNT ==========================

final class Card {
    private final String cardNumber;
    private int pin;
    private Account account; // set by Account.addCard()

    public Card(String cardNumber, int pin) {
        this.cardNumber = Objects.requireNonNull(cardNumber);
        this.pin = pin;
    }

    public String getCardNumber() { return cardNumber; }

    Account getAccount() { return account; }                   // package-private style
    void setAccount(Account account) { this.account = account; } // set only by Account

    public boolean validatePin(int enteredPin) { return pin == enteredPin; }

    public void changePin(int newPin) { this.pin = newPin; }
}

final class Account {
    private final String accountNumber;
    private int balance; // in ₹
    private final List<Card> cards = new ArrayList<>();
    private final Lock lock = new ReentrantLock();

    public Account(String accountNumber, int balance) {
        this.accountNumber = Objects.requireNonNull(accountNumber);
        this.balance = balance;
    }

    public String getAccountNumber() { return accountNumber; }

    public int getBalance() {
        lock.lock();
        try { return balance; }
        finally { lock.unlock(); }
    }

    public List<Card> getCards() { return Collections.unmodifiableList(cards); }

    public void addCard(Card card) {
        Objects.requireNonNull(card);
        card.setAccount(this);
        cards.add(card);
    }

    public boolean hasSufficientBalance(int amount) {
        lock.lock();
        try { return balance >= amount; }
        finally { lock.unlock(); }
    }

    public void debit(int amount) {
        lock.lock();
        try {
            if (balance < amount) throw new IllegalStateException("Insufficient balance.");
            balance -= amount;
        } finally {
            lock.unlock();
        }
    }

    public void credit(int amount) {
        lock.lock();
        try { balance += amount; }
        finally { lock.unlock(); }
    }
}

// ========================== CASH INVENTORY ==========================

final class CashInventory {
    private final EnumMap<DenominationType, Integer> inventory = new EnumMap<>(DenominationType.class);

    private final Lock lock = new ReentrantLock();

    public CashInventory() {
        for (DenominationType d : DenominationType.values()) inventory.put(d, 0);
    }

    Lock getLock() { return lock; }

    public int getCount(DenominationType denomination) {
        lock.lock();
        try { return inventory.getOrDefault(denomination, 0); }
        finally { lock.unlock(); }
    }

    public void addNotes(DenominationType denomination, int count) {
        if (count <= 0) throw new IllegalArgumentException("Count must be positive.");
        lock.lock();
        try {
            inventory.put(denomination, inventory.getOrDefault(denomination, 0) + count);
        } finally {
            lock.unlock();
        }
    }

    public void removeNotes(DenominationType denomination, int count) {
        lock.lock();
        try {
            int available = inventory.getOrDefault(denomination, 0);
            if (available < count) throw new IllegalStateException("Not enough " + denomination + " notes in inventory.");
            inventory.put(denomination, available - count);
        } finally {
            lock.unlock();
        }
    }

    public long getTotalCash() {
        lock.lock();
        try {
            long total = 0;
            for (Map.Entry<DenominationType, Integer> e : inventory.entrySet()) {
                total += (long) e.getKey().getValue() * e.getValue();
            }
            return total;
        } finally {
            lock.unlock();
        }
    }

    public void printInventory() {
        lock.lock();
        try {
            System.out.println("--- ATM Cash Inventory ---");
            for (DenominationType d : DenominationType.values()) {
                int c = inventory.getOrDefault(d, 0);
                System.out.println("  ₹" + d.getValue() + " x " + c + " = ₹" + (long) d.getValue() * c);
            }
            System.out.println("  Total: ₹" + getTotalCash());
        } finally {
            lock.unlock();
        }
    }
}

// ================ CHAIN OF RESPONSIBILITY — CASH DISPENSER ================

abstract class CashDispenseHandler {
    protected CashDispenseHandler next;

    public void setNext(CashDispenseHandler next) { this.next = next; }

    public abstract void dispense(int amount, CashInventory inventory, EnumMap<DenominationType, Integer> dispensed);
}

final class TwoThousandHandler extends CashDispenseHandler {
    @Override
    public void dispense(int amount, CashInventory inventory, EnumMap<DenominationType, Integer> dispensed) {
        int denom = DenominationType.TWO_THOUSAND.getValue();
        if (amount >= denom) {
            int needed = amount / denom;
            int available = inventory.getCount(DenominationType.TWO_THOUSAND);
            int toDispense = Math.min(needed, available);

            if (toDispense > 0) {
                dispensed.put(DenominationType.TWO_THOUSAND, toDispense);
                amount -= toDispense * denom;
            }
        }

        if (amount > 0 && next != null) next.dispense(amount, inventory, dispensed);
        else if (amount > 0) throw new IllegalStateException("ATM cannot dispense the requested amount with available denominations.");
    }
}

final class FiveHundredHandler extends CashDispenseHandler {
    @Override
    public void dispense(int amount, CashInventory inventory, EnumMap<DenominationType, Integer> dispensed) {
        int denom = DenominationType.FIVE_HUNDRED.getValue();
        if (amount >= denom) {
            int needed = amount / denom;
            int available = inventory.getCount(DenominationType.FIVE_HUNDRED);
            int toDispense = Math.min(needed, available);

            if (toDispense > 0) {
                dispensed.put(DenominationType.FIVE_HUNDRED, toDispense);
                amount -= toDispense * denom;
            }
        }

        if (amount > 0 && next != null) next.dispense(amount, inventory, dispensed);
        else if (amount > 0) throw new IllegalStateException("ATM cannot dispense the requested amount with available denominations.");
    }
}

final class TwoHundredHandler extends CashDispenseHandler {
    @Override
    public void dispense(int amount, CashInventory inventory, EnumMap<DenominationType, Integer> dispensed) {
        int denom = DenominationType.TWO_HUNDRED.getValue();
        if (amount >= denom) {
            int needed = amount / denom;
            int available = inventory.getCount(DenominationType.TWO_HUNDRED);
            int toDispense = Math.min(needed, available);

            if (toDispense > 0) {
                dispensed.put(DenominationType.TWO_HUNDRED, toDispense);
                amount -= toDispense * denom;
            }
        }

        if (amount > 0 && next != null) next.dispense(amount, inventory, dispensed);
        else if (amount > 0) throw new IllegalStateException("ATM cannot dispense the requested amount with available denominations.");
    }
}

final class HundredHandler extends CashDispenseHandler {
    @Override
    public void dispense(int amount, CashInventory inventory, EnumMap<DenominationType, Integer> dispensed) {
        int denom = DenominationType.HUNDRED.getValue();
        if (amount >= denom) {
            int needed = amount / denom;
            int available = inventory.getCount(DenominationType.HUNDRED);
            int toDispense = Math.min(needed, available);

            if (toDispense > 0) {
                dispensed.put(DenominationType.HUNDRED, toDispense);
                amount -= toDispense * denom;
            }
        }

        if (amount > 0) throw new IllegalStateException("ATM cannot dispense the requested amount with available denominations.");
    }
}

final class CashDispenser {
    private final CashDispenseHandler chain;

    public CashDispenser() {
        CashDispenseHandler twoThousand = new TwoThousandHandler();
        CashDispenseHandler fiveHundred = new FiveHundredHandler();
        CashDispenseHandler twoHundred = new TwoHundredHandler();
        CashDispenseHandler hundred = new HundredHandler();

        twoThousand.setNext(fiveHundred);
        fiveHundred.setNext(twoHundred);
        twoHundred.setNext(hundred);

        this.chain = twoThousand;
    }

    public EnumMap<DenominationType, Integer> dispense(int amount, CashInventory inventory) {
        inventory.getLock().lock();
        try {
            EnumMap<DenominationType, Integer> dispensed = new EnumMap<>(DenominationType.class);
            chain.dispense(amount, inventory, dispensed);

            for (Map.Entry<DenominationType, Integer> e : dispensed.entrySet()) {
                inventory.removeNotes(e.getKey(), e.getValue());
            }
            return dispensed;
        } finally {
            inventory.getLock().unlock();
        }
    }
}

// ========================== STATE PATTERN ==========================

interface ATMState {
    void insertCard(ATMMachine atm, Card card);
    void enterPin(ATMMachine atm, int pin);
    void selectTransaction(ATMMachine atm, TransactionType type);
    void withdrawCash(ATMMachine atm, int amount);
    void depositCash(ATMMachine atm, DenominationType denomination, int count);
    void changePin(ATMMachine atm, int oldPin, int newPin);
    void ejectCard(ATMMachine atm);
}

// ---------- Idle State ----------
final class IdleState implements ATMState {
    @Override
    public void insertCard(ATMMachine atm, Card card) {
        System.out.println("Card inserted.");
        atm.setCurrentCard(card);
        atm.setState(new CardInsertedState());
    }

    @Override public void enterPin(ATMMachine atm, int pin) { System.out.println("Please insert a card first."); }
    @Override public void selectTransaction(ATMMachine atm, TransactionType type) { System.out.println("Please insert a card first."); }
    @Override public void withdrawCash(ATMMachine atm, int amount) { System.out.println("Please insert a card first."); }
    @Override public void depositCash(ATMMachine atm, DenominationType denomination, int count) { System.out.println("Please insert a card first."); }
    @Override public void changePin(ATMMachine atm, int oldPin, int newPin) { System.out.println("Please insert a card first."); }
    @Override public void ejectCard(ATMMachine atm) { System.out.println("No card to eject."); }
}

// ---------- Card Inserted State ----------
final class CardInsertedState implements ATMState {
    private int attempts = 0;
    private static final int MAX_ATTEMPTS = 3;

    @Override public void insertCard(ATMMachine atm, Card card) { System.out.println("Card already inserted."); }

    @Override
    public void enterPin(ATMMachine atm, int pin) {
        Card current = atm.getCurrentCard();
        if (current != null && current.validatePin(pin)) {
            System.out.println("PIN verified successfully.");
            atm.setState(new AuthenticatedState());
            return;
        }

        attempts++;
        System.out.println("Incorrect PIN. Attempts remaining: " + (MAX_ATTEMPTS - attempts));
        if (attempts >= MAX_ATTEMPTS) {
            System.out.println("Too many incorrect attempts. Card ejected.");
            ejectCard(atm);
        }
    }

    @Override public void selectTransaction(ATMMachine atm, TransactionType type) { System.out.println("Please enter your PIN first."); }
    @Override public void withdrawCash(ATMMachine atm, int amount) { System.out.println("Please enter your PIN first."); }
    @Override public void depositCash(ATMMachine atm, DenominationType denomination, int count) { System.out.println("Please enter your PIN first."); }
    @Override public void changePin(ATMMachine atm, int oldPin, int newPin) { System.out.println("Please enter your PIN first."); }

    @Override
    public void ejectCard(ATMMachine atm) {
        System.out.println("Card ejected.");
        atm.setCurrentCard(null);
        atm.setState(new IdleState());
    }
}

// ---------- Authenticated State ----------
final class AuthenticatedState implements ATMState {
    @Override public void insertCard(ATMMachine atm, Card card) { System.out.println("Card already inserted. Complete or cancel current session."); }
    @Override public void enterPin(ATMMachine atm, int pin) { System.out.println("Already authenticated."); }

    @Override
    public void selectTransaction(ATMMachine atm, TransactionType type) {
        System.out.println("Transaction selected: " + type);
        switch (type) {
            case WITHDRAW -> atm.setState(new WithdrawState());
            case DEPOSIT -> atm.setState(new DepositState());
            case CHANGE_PIN -> atm.setState(new ChangePinState());
            case BALANCE_INQUIRY -> {
                Card c = atm.getCurrentCard();
                System.out.println("Current Balance: ₹" + (c != null ? c.getAccount().getBalance() : 0));
            }
        }
    }

    @Override public void withdrawCash(ATMMachine atm, int amount) { System.out.println("Please select a transaction type first."); }
    @Override public void depositCash(ATMMachine atm, DenominationType denomination, int count) { System.out.println("Please select a transaction type first."); }
    @Override public void changePin(ATMMachine atm, int oldPin, int newPin) { System.out.println("Please select a transaction type first."); }

    @Override
    public void ejectCard(ATMMachine atm) {
        System.out.println("Session ended. Card ejected.");
        atm.setCurrentCard(null);
        atm.setState(new IdleState());
    }
}

// ---------- Withdraw State ----------
final class WithdrawState implements ATMState {
    @Override public void insertCard(ATMMachine atm, Card card) { System.out.println("Transaction in progress."); }
    @Override public void enterPin(ATMMachine atm, int pin) { System.out.println("Already authenticated."); }
    @Override public void selectTransaction(ATMMachine atm, TransactionType type) { System.out.println("Complete current withdrawal first."); }

    @Override
    public void withdrawCash(ATMMachine atm, int amount) {
        if (amount <= 0 || amount % 100 != 0) {
            System.out.println("Amount must be positive and a multiple of 100.");
            return;
        }

        Card card = atm.getCurrentCard();
        if (card == null) {
            System.out.println("No card present.");
            atm.setState(new IdleState());
            return;
        }

        Account account = card.getAccount();

        if (!account.hasSufficientBalance(amount)) {
            System.out.println("Insufficient account balance.");
            atm.setState(new AuthenticatedState());
            return;
        }

        if (atm.getInventory().getTotalCash() < amount) {
            System.out.println("ATM has insufficient cash. Try a smaller amount.");
            atm.setState(new AuthenticatedState());
            return;
        }

        try {
            EnumMap<DenominationType, Integer> dispensed = atm.getDispenser().dispense(amount, atm.getInventory());
            account.debit(amount);

            System.out.println("₹" + amount + " withdrawn successfully. Notes dispensed:");
            for (Map.Entry<DenominationType, Integer> e : dispensed.entrySet()) {
                System.out.println("  ₹" + e.getKey().getValue() + " x " + e.getValue());
            }
            System.out.println("Remaining balance: ₹" + account.getBalance());
        } catch (IllegalStateException ex) {
            System.out.println("Withdrawal failed: " + ex.getMessage());
        }

        atm.setState(new AuthenticatedState());
    }

    @Override public void depositCash(ATMMachine atm, DenominationType denomination, int count) { System.out.println("Currently in withdrawal mode."); }
    @Override public void changePin(ATMMachine atm, int oldPin, int newPin) { System.out.println("Currently in withdrawal mode."); }

    @Override
    public void ejectCard(ATMMachine atm) {
        System.out.println("Withdrawal cancelled. Card ejected.");
        atm.setCurrentCard(null);
        atm.setState(new IdleState());
    }
}

// ---------- Deposit State ----------
final class DepositState implements ATMState {
    @Override public void insertCard(ATMMachine atm, Card card) { System.out.println("Transaction in progress."); }
    @Override public void enterPin(ATMMachine atm, int pin) { System.out.println("Already authenticated."); }
    @Override public void selectTransaction(ATMMachine atm, TransactionType type) { System.out.println("Complete current deposit first."); }
    @Override public void withdrawCash(ATMMachine atm, int amount) { System.out.println("Currently in deposit mode."); }

    @Override
    public void depositCash(ATMMachine atm, DenominationType denomination, int count) {
        if (count <= 0) {
            System.out.println("Note count must be positive.");
            return;
        }

        Card card = atm.getCurrentCard();
        if (card == null) {
            System.out.println("No card present.");
            atm.setState(new IdleState());
            return;
        }

        int amount = denomination.getValue() * count;
        atm.getInventory().addNotes(denomination, count);
        card.getAccount().credit(amount);

        System.out.println("₹" + amount + " deposited (" + count + " x ₹" + denomination.getValue() + " notes).");
        System.out.println("Updated balance: ₹" + card.getAccount().getBalance());
        atm.setState(new AuthenticatedState());
    }

    @Override public void changePin(ATMMachine atm, int oldPin, int newPin) { System.out.println("Currently in deposit mode."); }

    @Override
    public void ejectCard(ATMMachine atm) {
        System.out.println("Deposit cancelled. Card ejected.");
        atm.setCurrentCard(null);
        atm.setState(new IdleState());
    }
}

// ---------- Change PIN State ----------
final class ChangePinState implements ATMState {
    @Override public void insertCard(ATMMachine atm, Card card) { System.out.println("Transaction in progress."); }
    @Override public void enterPin(ATMMachine atm, int pin) { System.out.println("Already authenticated."); }
    @Override public void selectTransaction(ATMMachine atm, TransactionType type) { System.out.println("Complete PIN change first."); }
    @Override public void withdrawCash(ATMMachine atm, int amount) { System.out.println("Currently in change PIN mode."); }
    @Override public void depositCash(ATMMachine atm, DenominationType denomination, int count) { System.out.println("Currently in change PIN mode."); }

    @Override
    public void changePin(ATMMachine atm, int oldPin, int newPin) {
        Card card = atm.getCurrentCard();
        if (card == null) {
            System.out.println("No card present.");
            atm.setState(new IdleState());
            return;
        }

        if (!card.validatePin(oldPin)) {
            System.out.println("Old PIN is incorrect. PIN change failed.");
            atm.setState(new AuthenticatedState());
            return;
        }

        if (newPin < 1000 || newPin > 9999) {
            System.out.println("New PIN must be a 4-digit number.");
            atm.setState(new AuthenticatedState());
            return;
        }

        card.changePin(newPin);
        System.out.println("PIN changed successfully.");
        atm.setState(new AuthenticatedState());
    }

    @Override
    public void ejectCard(ATMMachine atm) {
        System.out.println("PIN change cancelled. Card ejected.");
        atm.setCurrentCard(null);
        atm.setState(new IdleState());
    }
}

// ========================== ATM MACHINE (Context) ==========================

final class ATMMachine {
    private ATMState currentState = new IdleState();
    private Card currentCard;

    private final CashInventory inventory = new CashInventory();
    private final CashDispenser dispenser = new CashDispenser();

    private final Lock sessionLock = new ReentrantLock(true);

    public void setState(ATMState state) { this.currentState = state; }
    public void setCurrentCard(Card card) { this.currentCard = card; }
    public Card getCurrentCard() { return currentCard; }

    public CashInventory getInventory() { return inventory; }
    public CashDispenser getDispenser() { return dispenser; }

    // Delegated operations to current state (all serialized by sessionLock)
    public void insertCard(Card card) {
        sessionLock.lock();
        try { currentState.insertCard(this, card); }
        finally { sessionLock.unlock(); }
    }

    public void enterPin(int pin) {
        sessionLock.lock();
        try { currentState.enterPin(this, pin); }
        finally { sessionLock.unlock(); }
    }

    public void selectTransaction(TransactionType type) {
        sessionLock.lock();
        try { currentState.selectTransaction(this, type); }
        finally { sessionLock.unlock(); }
    }

    public void withdrawCash(int amount) {
        sessionLock.lock();
        try { currentState.withdrawCash(this, amount); }
        finally { sessionLock.unlock(); }
    }

    public void depositCash(DenominationType denomination, int count) {
        sessionLock.lock();
        try { currentState.depositCash(this, denomination, count); }
        finally { sessionLock.unlock(); }
    }

    public void changePin(int oldPin, int newPin) {
        sessionLock.lock();
        try { currentState.changePin(this, oldPin, newPin); }
        finally { sessionLock.unlock(); }
    }

    public void ejectCard() {
        sessionLock.lock();
        try { currentState.ejectCard(this); }
        finally { sessionLock.unlock(); }
    }

    public void loadCash(DenominationType denomination, int count) {
        sessionLock.lock();
        try {
            inventory.addNotes(denomination, count);
            System.out.println("Loaded " + count + " x ₹" + denomination.getValue() + " notes into ATM.");
        } finally {
            sessionLock.unlock();
        }
    }
}

// ========================== DEMO ==========================

public class Program {
    public static void main(String[] args) {
        // Setup — Account owns Cards (1:N relationship)
        Account account = new Account("ACC-10001", 50000);
        Card card = new Card("4111-1111-1111-1111", 1234);
        Card card2 = new Card("4222-2222-2222-2222", 4321);
        account.addCard(card);
        account.addCard(card2);

        ATMMachine atm = new ATMMachine();
        atm.loadCash(DenominationType.TWO_THOUSAND, 10);
        atm.loadCash(DenominationType.FIVE_HUNDRED, 20);
        atm.loadCash(DenominationType.TWO_HUNDRED, 30);
        atm.loadCash(DenominationType.HUNDRED, 50);
        atm.getInventory().printInventory();

        System.out.println("\n===== WITHDRAW CASH =====");
        atm.insertCard(card);
        atm.enterPin(1234);
        atm.selectTransaction(TransactionType.WITHDRAW);
        atm.withdrawCash(4700);

        System.out.println("\n===== BALANCE INQUIRY =====");
        atm.selectTransaction(TransactionType.BALANCE_INQUIRY);

        System.out.println("\n===== DEPOSIT CASH =====");
        atm.selectTransaction(TransactionType.DEPOSIT);
        atm.depositCash(DenominationType.FIVE_HUNDRED, 4);

        System.out.println("\n===== CHANGE PIN =====");
        atm.selectTransaction(TransactionType.CHANGE_PIN);
        atm.changePin(1234, 5678);

        System.out.println("\n===== WITHDRAW WITH NEW PIN =====");
        atm.ejectCard();
        atm.insertCard(card);
        atm.enterPin(5678);
        atm.selectTransaction(TransactionType.WITHDRAW);
        atm.withdrawCash(2500);
        atm.ejectCard();

        System.out.println("\n===== WRONG PIN ATTEMPT =====");
        atm.insertCard(card);
        atm.enterPin(0);
        atm.enterPin(0);
        atm.enterPin(0);

        System.out.println();
        atm.getInventory().printInventory();
    }
}
