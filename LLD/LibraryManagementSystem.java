public class LibraryLLD {

    /* =========================
     *  Enums
     * ========================= */
    public enum BookFormat { HARDCOVER, PAPERBACK, AUDIO_BOOK, EBOOK, NEWSPAPER, MAGAZINE, JOURNAL }
    public enum BookStatus { AVAILABLE, RESERVED, LOANED, LOST }
    public enum ReservationStatus { WAITING, PENDING, CANCELED, NONE, COMPLETED }
    public enum AccountStatus { ACTIVE, CLOSED, CANCELED, BLACKLISTED, NONE }

    /* =========================
     *  Value Objects
     * ========================= */
    public static final class Address {
        private final String street;
        private final String city;
        private final String state;
        private final String zipCode;
        private final String country;

        public Address(String street, String city, String state, String zipCode, String country) {
            this.street = street;
            this.city = city;
            this.state = state;
            this.zipCode = zipCode;
            this.country = country;
        }

        public String street() { return street; }
        public String city() { return city; }
        public String state() { return state; }
        public String zipCode() { return zipCode; }
        public String country() { return country; }
    }

    public static final class LibraryCard {
        private final String cardNumber;
        private final String barCode;
        private final LocalDate issuedAt;
        private boolean active;

        public LibraryCard(String cardNumber, String barCode, LocalDate issuedAt, boolean active) {
            this.cardNumber = cardNumber;
            this.barCode = barCode;
            this.issuedAt = issuedAt;
            this.active = active;
        }

        public String getCardNumber() { return cardNumber; }
        public String getBarCode() { return barCode; }
        public LocalDate getIssuedAt() { return issuedAt; }
        public boolean isActive() { return active; }
        public void deactivate() { this.active = false; }
        public void activate() { this.active = true; }
    }

    public static final class Library {
        private final String name;
        private final Address address;

        public Library(String name, Address address) {
            this.name = Objects.requireNonNull(name);
            this.address = Objects.requireNonNull(address);
        }

        public String getName() { return name; }
        public Address getAddress() { return address; }
    }

    /* =========================
     *  Person + Account
     * ========================= */
    public static abstract class Person {
        private final String name;
        private final Address address;
        private final String email;
        private final String phone;

        protected Person(String name, Address address, String email, String phone) {
            this.name = Objects.requireNonNull(name);
            this.address = address;
            this.email = email;
            this.phone = phone;
        }

        public String getName() { return name; }
        public Address getAddress() { return address; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }
    }

    public static final class BasicPerson extends Person {
        public BasicPerson(String name, Address address, String email, String phone) {
            super(name, address, email, phone);
        }
    }

    public static abstract class Account {
        private final String id;
        private String password;
        private AccountStatus status;
        private final Person person;
        private LibraryCard libraryCard;

        protected Account(String id, String password, Person person, AccountStatus status) {
            this.id = Objects.requireNonNull(id);
            this.password = Objects.requireNonNull(password);
            this.person = Objects.requireNonNull(person);
            this.status = status == null ? AccountStatus.NONE : status;
        }

        public String getId() { return id; }
        public Person getPerson() { return person; }
        public AccountStatus getStatus() { return status; }
        public void setStatus(AccountStatus status) { this.status = status; }
        public LibraryCard getLibraryCard() { return libraryCard; }
        public void setLibraryCard(LibraryCard libraryCard) { this.libraryCard = libraryCard; }

        public abstract void resetPassword(String newPassword);
        protected void setPassword(String newPassword) { this.password = Objects.requireNonNull(newPassword); }
    }

    public static final class Librarian extends Account {
        private final Catalog catalog;

        public Librarian(String id, String password, Person person, Catalog catalog) {
            super(id, password, person, AccountStatus.ACTIVE);
            this.catalog = Objects.requireNonNull(catalog);
        }

        @Override
        public void resetPassword(String newPassword) {
            // placeholder; interviewer: add OTP/email verification
            setPassword(newPassword);
            setStatus(AccountStatus.ACTIVE);
        }

        public void addBookItem(BookItem item) {
            catalog.addBookItem(item);
        }

        public void blockMember(Member member) { member.setStatus(AccountStatus.BLACKLISTED); }
        public void unblockMember(Member member) { member.setStatus(AccountStatus.ACTIVE); }
    }

    public static final class Member extends Account {
        private final LocalDate dateOfMembership = LocalDate.now();
        private int totalBooksCheckedOut = 0;

        private final CirculationService circulationService;

        public Member(String id, String password, Person person, CirculationService circulationService) {
            super(id, password, person, AccountStatus.ACTIVE);
            this.circulationService = Objects.requireNonNull(circulationService);
        }

        @Override
        public void resetPassword(String newPassword) {
            // placeholder; interviewer: OTP/email verification
            setPassword(newPassword);
        }

        public LocalDate getDateOfMembership() { return dateOfMembership; }
        public int getTotalBooksCheckedOut() { return totalBooksCheckedOut; }

        void incrementBooks() { totalBooksCheckedOut++; }
        void decrementBooks() { if (totalBooksCheckedOut > 0) totalBooksCheckedOut--; }

        // Minimal behavior change: delegate workflow to service (keeps your flow intact)
        public boolean checkoutBookItem(BookItem item) { return circulationService.checkout(this, item); }
        public void returnBookItem(BookItem item) { circulationService.returnBook(this, item); }
        public boolean renewBookItem(BookItem item) { return circulationService.renew(this, item); }
    }

    /* =========================
     *  Domain: Book + BookItem
     * ========================= */
    public static abstract class Book {
        private final String ISBN;
        private final String title;
        private final String subject;
        private final String publisher;
        private final String language;
        private final int numberOfPages;
        private final List<String> authors = new ArrayList<>();

        protected Book(String ISBN, String title, String subject, String publisher, String language, int numberOfPages) {
            this.ISBN = Objects.requireNonNull(ISBN);
            this.title = Objects.requireNonNull(title);
            this.subject = Objects.requireNonNull(subject);
            this.publisher = publisher;
            this.language = language;
            this.numberOfPages = numberOfPages;
        }

        public String getISBN() { return ISBN; }
        public String getTitle() { return title; }
        public String getSubject() { return subject; }
        public String getPublisher() { return publisher; }
        public String getLanguage() { return language; }
        public int getNumberOfPages() { return numberOfPages; }
        public List<String> getAuthors() { return List.copyOf(authors); }

        public void addAuthor(String author) {
            if (author != null && !author.isBlank()) authors.add(author);
        }
    }

    public static final class Rack {
        private final int number;
        private final String locationIdentifier;

        public Rack(int number, String locationIdentifier) {
            this.number = number;
            this.locationIdentifier = locationIdentifier;
        }

        public int getNumber() { return number; }
        public String getLocationIdentifier() { return locationIdentifier; }
    }

    public static final class BookItem extends Book {
        private final String barcode;
        private final boolean referenceOnly;
        private final double price;
        private final BookFormat format;
        private BookStatus status;
        private final LocalDate dateOfPurchase;
        private final LocalDate publicationDate;
        private final Rack placedAt;

        private LocalDate dueDate;

        public BookItem(
                String ISBN, String title, String subject, String publisher, String language, int numberOfPages,
                String barcode, boolean referenceOnly, double price, BookFormat format, BookStatus status,
                LocalDate dateOfPurchase, LocalDate publicationDate, Rack placedAt
        ) {
            super(ISBN, title, subject, publisher, language, numberOfPages);
            this.barcode = Objects.requireNonNull(barcode);
            this.referenceOnly = referenceOnly;
            this.price = price;
            this.format = format == null ? BookFormat.PAPERBACK : format;
            this.status = status == null ? BookStatus.AVAILABLE : status;
            this.dateOfPurchase = dateOfPurchase;
            this.publicationDate = publicationDate;
            this.placedAt = placedAt;
        }

        public String getBarcode() { return barcode; }
        public boolean isReferenceOnly() { return referenceOnly; }
        public double getPrice() { return price; }
        public BookFormat getFormat() { return format; }
        public BookStatus getStatus() { return status; }
        public void setStatus(BookStatus status) { this.status = status; }
        public LocalDate getDueDate() { return dueDate; }
        public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
        public LocalDate getPublicationDate() { return publicationDate; }
        public Rack getPlacedAt() { return placedAt; }

        public boolean isAvailable() { return status == BookStatus.AVAILABLE; }
    }

    /* =========================
     *  Search + Catalog
     * ========================= */
    public interface Search {
        List<BookItem> searchByTitle(String title);
        List<BookItem> searchByAuthor(String author);
        List<BookItem> searchBySubject(String subject);
        List<BookItem> searchByPublicationDate(LocalDate publishDate);
    }

    public static final class Catalog implements Search {
        private final Map<String, List<BookItem>> byTitle = new ConcurrentHashMap<>();
        private final Map<String, List<BookItem>> byAuthor = new ConcurrentHashMap<>();
        private final Map<String, List<BookItem>> bySubject = new ConcurrentHashMap<>();
        private final Map<LocalDate, List<BookItem>> byPubDate = new ConcurrentHashMap<>();

        public void addBookItem(BookItem item) {
            Objects.requireNonNull(item);

            byTitle.computeIfAbsent(key(item.getTitle()), k -> Collections.synchronizedList(new ArrayList<>())).add(item);
            for (String a : item.getAuthors()) {
                byAuthor.computeIfAbsent(key(a), k -> Collections.synchronizedList(new ArrayList<>())).add(item);
            }
            bySubject.computeIfAbsent(key(item.getSubject()), k -> Collections.synchronizedList(new ArrayList<>())).add(item);

            if (item.getPublicationDate() != null) {
                byPubDate.computeIfAbsent(item.getPublicationDate(), k -> Collections.synchronizedList(new ArrayList<>())).add(item);
            }
        }

        private String key(String s) { return s == null ? "" : s.trim().toLowerCase(); }

        @Override public List<BookItem> searchByTitle(String title) { return byTitle.getOrDefault(key(title), List.of()); }
        @Override public List<BookItem> searchByAuthor(String author) { return byAuthor.getOrDefault(key(author), List.of()); }
        @Override public List<BookItem> searchBySubject(String subject) { return bySubject.getOrDefault(key(subject), List.of()); }
        @Override public List<BookItem> searchByPublicationDate(LocalDate publishDate) { return byPubDate.getOrDefault(publishDate, List.of()); }
    }

    /* =========================
     *  Reservation + Lending models
     * ========================= */
    public static final class BookReservation {
        private final LocalDate creationDate;
        private ReservationStatus status;
        private final String bookBarcode;
        private final String memberId;

        public BookReservation(LocalDate creationDate, ReservationStatus status, String bookBarcode, String memberId) {
            this.creationDate = creationDate == null ? LocalDate.now() : creationDate;
            this.status = status == null ? ReservationStatus.WAITING : status;
            this.bookBarcode = Objects.requireNonNull(bookBarcode);
            this.memberId = Objects.requireNonNull(memberId);
        }

        public LocalDate getCreationDate() { return creationDate; }
        public ReservationStatus getStatus() { return status; }
        public String getBookBarcode() { return bookBarcode; }
        public String getMemberId() { return memberId; }

        public void updateStatus(ReservationStatus newStatus) { this.status = newStatus; }
    }

    public static final class BookLending {
        private final LocalDate creationDate;
        private LocalDate dueDate;
        private LocalDate returnDate;
        private final String bookBarcode;
        private final String memberId;

        public BookLending(LocalDate creationDate, LocalDate dueDate, String bookBarcode, String memberId) {
            this.creationDate = creationDate == null ? LocalDate.now() : creationDate;
            this.dueDate = dueDate;
            this.bookBarcode = Objects.requireNonNull(bookBarcode);
            this.memberId = Objects.requireNonNull(memberId);
        }

        public LocalDate getCreationDate() { return creationDate; }
        public LocalDate getDueDate() { return dueDate; }
        public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
        public LocalDate getReturnDate() { return returnDate; }
        public void markReturned(LocalDate returnDate) { this.returnDate = returnDate == null ? LocalDate.now() : returnDate; }
        public String getBookBarcode() { return bookBarcode; }
        public String getMemberId() { return memberId; }
    }

    /* =========================
     *  Repositories (in-memory for demo)
     * ========================= */
    public interface ReservationRepository {
        Optional<BookReservation> findActiveByBarcode(String barcode);
        void save(BookReservation reservation);
        void delete(String barcode);
    }

    public static final class InMemoryReservationRepository implements ReservationRepository {
        private final Map<String, BookReservation> byBarcode = new ConcurrentHashMap<>();

        @Override public Optional<BookReservation> findActiveByBarcode(String barcode) {
            BookReservation r = byBarcode.get(barcode);
            if (r == null) return Optional.empty();
            // Treat COMPLETED/CANCELED as inactive
            if (r.getStatus() == ReservationStatus.COMPLETED || r.getStatus() == ReservationStatus.CANCELED) return Optional.empty();
            return Optional.of(r);
        }

        @Override public void save(BookReservation reservation) { byBarcode.put(reservation.getBookBarcode(), reservation); }

        @Override public void delete(String barcode) { byBarcode.remove(barcode); }
    }

    public interface LendingRepository {
        Optional<BookLending> findActiveByBarcode(String barcode);
        void save(BookLending lending);
        void close(String barcode, LocalDate returnDate);
    }

    public static final class InMemoryLendingRepository implements LendingRepository {
        private final Map<String, BookLending> active = new ConcurrentHashMap<>();

        @Override public Optional<BookLending> findActiveByBarcode(String barcode) { return Optional.ofNullable(active.get(barcode)); }

        @Override public void save(BookLending lending) { active.put(lending.getBookBarcode(), lending); }

        @Override public void close(String barcode, LocalDate returnDate) {
            BookLending lending = active.remove(barcode);
            if (lending != null) lending.markReturned(returnDate);
        }
    }

    /* =========================
     *  Policies (Strategy pattern)
     * ========================= */
    public interface LendingPolicy {
        int maxBooksPerMember();
        int maxLendingDays();
        default boolean canCheckout(Member member) {
            return member.getTotalBooksCheckedOut() < maxBooksPerMember();
        }
    }

    public static final class DefaultLendingPolicy implements LendingPolicy {
        private final int maxBooks;
        private final int maxDays;

        public DefaultLendingPolicy(int maxBooks, int maxDays) {
            this.maxBooks = maxBooks;
            this.maxDays = maxDays;
        }

        @Override public int maxBooksPerMember() { return maxBooks; }
        @Override public int maxLendingDays() { return maxDays; }
    }

    public interface FinePolicy {
        int finePerDay();
        default int calculateFine(LocalDate dueDate, LocalDate today) {
            if (dueDate == null || today == null || !today.isAfter(dueDate)) return 0;
            long daysLate = ChronoUnit.DAYS.between(dueDate, today);
            return Math.toIntExact(daysLate * finePerDay());
        }
    }

    public static final class DefaultFinePolicy implements FinePolicy {
        private final int perDay;
        public DefaultFinePolicy(int perDay) { this.perDay = perDay; }
        @Override public int finePerDay() { return perDay; }
    }

    /* =========================
     *  Notification (extensible)
     * ========================= */
    public interface Notifier {
        void notifyBookAvailable(String memberId, String barcode);
    }

    public static final class ConsoleNotifier implements Notifier {
        @Override public void notifyBookAvailable(String memberId, String barcode) {
            System.out.println("NOTIFY: Book available. memberId=" + memberId + ", barcode=" + barcode);
        }
    }

    /* =========================
     *  Circulation Service (owns workflow)
     * ========================= */
    public static final class CirculationService {
        private final ReservationRepository reservationRepo;
        private final LendingRepository lendingRepo;
        private final LendingPolicy lendingPolicy;
        private final FinePolicy finePolicy;
        private final Notifier notifier;

        // lock-per-book to avoid double checkout
        private final Map<String, Object> bookLocks = new ConcurrentHashMap<>();

        public CirculationService(
                ReservationRepository reservationRepo,
                LendingRepository lendingRepo,
                LendingPolicy lendingPolicy,
                FinePolicy finePolicy,
                Notifier notifier
        ) {
            this.reservationRepo = Objects.requireNonNull(reservationRepo);
            this.lendingRepo = Objects.requireNonNull(lendingRepo);
            this.lendingPolicy = Objects.requireNonNull(lendingPolicy);
            this.finePolicy = Objects.requireNonNull(finePolicy);
            this.notifier = Objects.requireNonNull(notifier);
        }

        public boolean checkout(Member member, BookItem item) {
            Objects.requireNonNull(member);
            Objects.requireNonNull(item);

            if (member.getStatus() != AccountStatus.ACTIVE) {
                System.out.println("Member not active");
                return false;
            }
            if (!lendingPolicy.canCheckout(member)) {
                System.out.println("Max books already checked out");
                return false;
            }
            if (item.isReferenceOnly()) {
                System.out.println("Reference-only; cannot be checked out.");
                return false;
            }

            Object lock = bookLocks.computeIfAbsent(item.getBarcode(), k -> new Object());
            synchronized (lock) {
                if (item.getStatus() != BookStatus.AVAILABLE && item.getStatus() != BookStatus.RESERVED) {
                    System.out.println("Book not available");
                    return false;
                }

                Optional<BookReservation> resvOpt = reservationRepo.findActiveByBarcode(item.getBarcode());
                if (resvOpt.isPresent() && !resvOpt.get().getMemberId().equals(member.getId())) {
                    System.out.println("Reserved by another member");
                    return false;
                } else if (resvOpt.isPresent()) {
                    resvOpt.get().updateStatus(ReservationStatus.COMPLETED);
                    reservationRepo.save(resvOpt.get());
                }

                // create lending
                LocalDate due = LocalDate.now().plusDays(lendingPolicy.maxLendingDays());
                lendingRepo.save(new BookLending(LocalDate.now(), due, item.getBarcode(), member.getId()));
                item.setStatus(BookStatus.LOANED);
                item.setDueDate(due);

                member.incrementBooks();
                return true;
            }
        }

        public boolean reserveBook(Member member, BookItem item) {
            Objects.requireNonNull(member);
            Objects.requireNonNull(item);

            Optional<BookReservation> existing =
                    reservationRepo.findActiveByBarcode(item.getBarcode());

            if (existing.isPresent()) {
                System.out.println("Book already reserved");
                return false;
            }

            BookReservation reservation = new BookReservation(
                    LocalDate.now(),
                    ReservationStatus.WAITING,
                    item.getBarcode(),
                    member.getId()
            );

            reservationRepo.save(reservation);
            item.setStatus(BookStatus.RESERVED);
            return true;
        }

        public void returnBook(Member member, BookItem item) {
            Objects.requireNonNull(member);
            Objects.requireNonNull(item);

            Object lock = bookLocks.computeIfAbsent(item.getBarcode(), k -> new Object());
            synchronized (lock) {
                // fine check (same flow as your checkForFine)
                collectFineIfAny(item.getBarcode(), member.getId());

                lendingRepo.close(item.getBarcode(), LocalDate.now());

                Optional<BookReservation> resvOpt = reservationRepo.findActiveByBarcode(item.getBarcode());
                if (resvOpt.isPresent()) {
                    item.setStatus(BookStatus.RESERVED);
                    notifier.notifyBookAvailable(resvOpt.get().getMemberId(), item.getBarcode());
                } else {
                    item.setStatus(BookStatus.AVAILABLE);
                }

                item.setDueDate(null);
                member.decrementBooks();
            }
        }

        public boolean renew(Member member, BookItem item) {
            Objects.requireNonNull(member);
            Objects.requireNonNull(item);

            Object lock = bookLocks.computeIfAbsent(item.getBarcode(), k -> new Object());
            synchronized (lock) {
                collectFineIfAny(item.getBarcode(), member.getId());

                Optional<BookReservation> resvOpt = reservationRepo.findActiveByBarcode(item.getBarcode());
                if (resvOpt.isPresent() && !resvOpt.get().getMemberId().equals(member.getId())) {
                    // same intent as your logic: if someone else reserved, renewal fails
                    System.out.println("Reserved by another member");
                    item.setStatus(BookStatus.RESERVED);
                    notifier.notifyBookAvailable(resvOpt.get().getMemberId(), item.getBarcode());
                    return false;
                } else if (resvOpt.isPresent()) {
                    resvOpt.get().updateStatus(ReservationStatus.COMPLETED);
                    reservationRepo.save(resvOpt.get());
                }

                // extend due date
                LocalDate newDue = LocalDate.now().plusDays(lendingPolicy.maxLendingDays());
                lendingRepo.findActiveByBarcode(item.getBarcode()).ifPresent(l -> l.setDueDate(newDue));
                item.setDueDate(newDue);
                item.setStatus(BookStatus.LOANED);
                return true;
            }
        }

        private void collectFineIfAny(String barcode, String memberId) {
            Optional<BookLending> lendOpt = lendingRepo.findActiveByBarcode(barcode);
            lendOpt.ifPresent(lending -> {
                int fine = finePolicy.calculateFine(lending.getDueDate(), LocalDate.now());
                if (fine > 0) {
                    System.out.println("FINE: memberId=" + memberId + " amount=" + fine);
                    // interviewer follow-up: persist FineRecord + payment workflow
                }
            });
        }
    }

    /* =========================
     *  Demo / Usage
     * ========================= */
    public static void main(String[] args) {
        Catalog catalog = new Catalog();

        ReservationRepository reservationRepo = new InMemoryReservationRepository();
        LendingRepository lendingRepo = new InMemoryLendingRepository();
        LendingPolicy lendingPolicy = new DefaultLendingPolicy(5, 10);
        FinePolicy finePolicy = new DefaultFinePolicy(1);
        Notifier notifier = new ConsoleNotifier();

        CirculationService circulation = new CirculationService(reservationRepo, lendingRepo, lendingPolicy, finePolicy, notifier);

        Person p1 = new BasicPerson("John Doe", null, "john@example.com", "9999999999");
        Member m1 = new Member("M-1", "pass", p1, circulation);

        Librarian librarian = new Librarian("L-1", "pass", new BasicPerson("Lib", null, "lib@x.com", "111"), catalog);

        Rack rack = new Rack(1, "A-1");
        BookItem b1 = new BookItem(
                "ISBN1", "Book 1", "Coding", "TATA", "EN", 100,
                "BC-1", false, 199.0, BookFormat.PAPERBACK, BookStatus.AVAILABLE,
                LocalDate.now(), LocalDate.of(2020, 1, 1), rack
        );
        b1.addAuthor("Author 1");
        librarian.addBookItem(b1);

        System.out.println("Checkout = " + m1.checkoutBookItem(b1));
        System.out.println("Renew = " + m1.renewBookItem(b1));
        m1.returnBookItem(b1);

        System.out.println("Search by author = " + catalog.searchByAuthor("Author 1").size());
    }
}
