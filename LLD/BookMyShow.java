import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Movie Ticket Booking System - Single file LLD
 * Focus: Flow + design patterns + extensibility + readability
 */
public class MovieBookingLLD {

    /* ===========================
     *           DOMAIN
     * =========================== */

    enum BookingStatus { CREATED, CONFIRMED, EXPIRED }

    static final class Movie {
        private final String id;
        private final String name;

        public Movie(String id, String name) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
        }

        public String getId() { return id; }
        public String getName() { return name; }
    }

    static final class Theatre {
        private final String id;
        private final String name;
        private final List<Screen> screens = new ArrayList<>();

        public Theatre(String id, String name) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public List<Screen> getScreens() { return Collections.unmodifiableList(screens); }

        public void addScreen(Screen screen) {
            screens.add(Objects.requireNonNull(screen));
        }
    }

    static final class Screen {
        private final String id;
        private final String name;
        private final Theatre theatre;
        private final List<Seat> seats = new ArrayList<>();

        public Screen(String id, String name, Theatre theatre) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
            this.theatre = Objects.requireNonNull(theatre);
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public Theatre getTheatre() { return theatre; }
        public List<Seat> getSeats() { return Collections.unmodifiableList(seats); }

        public void addSeat(Seat seat) {
            seats.add(Objects.requireNonNull(seat));
        }
    }

    /**
     * Seat should be identity-based by seatId.
     * For LLD flow: treat seatId as stable identity across the system.
     */
    static final class Seat {
        private final String id;
        private final int rowNo;
        private final int seatNo;

        public Seat(String id, int rowNo, int seatNo) {
            this.id = Objects.requireNonNull(id);
            this.rowNo = rowNo;
            this.seatNo = seatNo;
        }

        public String getId() { return id; }
        public int getRowNo() { return rowNo; }
        public int getSeatNo() { return seatNo; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Seat)) return false;
            Seat seat = (Seat) o;
            return id.equals(seat.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    static final class Show {
        private final String id;
        private final Movie movie;
        private final Screen screen;
        private final Date startTime;
        private final int durationInSeconds;

        public Show(String id, Movie movie, Screen screen, Date startTime, int durationInSeconds) {
            this.id = Objects.requireNonNull(id);
            this.movie = Objects.requireNonNull(movie);
            this.screen = Objects.requireNonNull(screen);
            this.startTime = Objects.requireNonNull(startTime);
            this.durationInSeconds = durationInSeconds;
        }

        public String getId() { return id; }
        public Movie getMovie() { return movie; }
        public Screen getScreen() { return screen; }
        public Date getStartTime() { return startTime; }
        public int getDurationInSeconds() { return durationInSeconds; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Show)) return false;
            Show show = (Show) o;
            return id.equals(show.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }

    /**
     * Booking as a small state machine:
     * CREATED -> CONFIRMED
     * CREATED -> EXPIRED
     */
    static final class Booking {
        private final String id;
        private final Show show;
        private final List<Seat> seatsBooked;
        private final String user;
        private BookingStatus status;

        public Booking(String id, Show show, String user, List<Seat> seatsBooked) {
            this.id = Objects.requireNonNull(id);
            this.show = Objects.requireNonNull(show);
            this.user = Objects.requireNonNull(user);
            this.seatsBooked = List.copyOf(Objects.requireNonNull(seatsBooked)); // immutable copy
            this.status = BookingStatus.CREATED;
        }

        public String getId() { return id; }
        public Show getShow() { return show; }
        public String getUser() { return user; }
        public List<Seat> getSeatsBooked() { return seatsBooked; }
        public BookingStatus getStatus() { return status; }

        public boolean isConfirmed() {
            return status == BookingStatus.CONFIRMED;
        }

        public void confirm() {
            if (status != BookingStatus.CREATED) throw new IllegalStateException("Invalid transition: " + status);
            status = BookingStatus.CONFIRMED;
        }

        public void expire() {
            if (status != BookingStatus.CREATED) throw new IllegalStateException("Invalid transition: " + status);
            status = BookingStatus.EXPIRED;
        }
    }

    /* ===========================
     *         SEAT LOCKING
     * =========================== */

    static final class SeatLock {
        private final Seat seat;
        private final Show show;
        private final int timeoutInSeconds;
        private final Instant lockTime;
        private final String lockedBy;

        public SeatLock(Seat seat, Show show, int timeoutInSeconds, Instant lockTime, String lockedBy) {
            this.seat = Objects.requireNonNull(seat);
            this.show = Objects.requireNonNull(show);
            this.timeoutInSeconds = timeoutInSeconds;
            this.lockTime = Objects.requireNonNull(lockTime);
            this.lockedBy = Objects.requireNonNull(lockedBy);
        }

        public String getLockedBy() { return lockedBy; }

        public boolean isExpired(Instant now) {
            return lockTime.plusSeconds(timeoutInSeconds).isBefore(now);
        }
    }

    /**
     * Strategy pattern: swap InMemory -> Redis -> DB.
     * Same contract stays.
     */
    interface SeatLockProvider {
        void lockSeats(Show show, List<Seat> seats, String user);
        void unlockSeats(Show show, List<Seat> seats, String user);
        boolean validateLock(Show show, Seat seat, String user);
        List<Seat> getLockedSeats(Show show);
    }

    /**
     * InMemory implementation (simple for interview).
     * Keyed by showId + seatId implicitly through equals/hashCode.
     */
    static final class InMemorySeatLockProvider implements SeatLockProvider {
        private final int lockTimeoutSeconds;
        private final Clock clock;

        // show -> (seat -> lock)
        private final Map<Show, Map<Seat, SeatLock>> locks = new ConcurrentHashMap<>();

        public InMemorySeatLockProvider(int lockTimeoutSeconds) {
            this(lockTimeoutSeconds, Clock.systemUTC());
        }

        public InMemorySeatLockProvider(int lockTimeoutSeconds, Clock clock) {
            this.lockTimeoutSeconds = lockTimeoutSeconds;
            this.clock = Objects.requireNonNull(clock);
        }

        @Override
        public synchronized void lockSeats(Show show, List<Seat> seats, String user) {
            for (Seat seat : seats) {
                if (isSeatLocked(show, seat)) {
                    throw new RuntimeException("SeatTemporaryUnavailableException");
                }
            }
            for (Seat seat : seats) {
                lockSeat(show, seat, user);
            }
        }

        @Override
        public synchronized void unlockSeats(Show show, List<Seat> seats, String user) {
            for (Seat seat : seats) {
                if (validateLock(show, seat, user)) {
                    unlockSeat(show, seat);
                }
            }
        }

        @Override
        public synchronized boolean validateLock(Show show, Seat seat, String user) {
            SeatLock lock = getLock(show, seat);
            if (lock == null) return false;
            if (lock.isExpired(Instant.now(clock))) {
                unlockSeat(show, seat); // lazy cleanup
                return false;
            }
            return user.equals(lock.getLockedBy());
        }

        @Override
        public synchronized List<Seat> getLockedSeats(Show show) {
            Map<Seat, SeatLock> seatLocks = locks.get(show);
            if (seatLocks == null) return List.of();

            Instant now = Instant.now(clock);
            List<Seat> locked = new ArrayList<>();
            for (Map.Entry<Seat, SeatLock> e : seatLocks.entrySet()) {
                if (e.getValue().isExpired(now)) {
                    // cleanup
                    // (don’t remove inside iteration in real code; this is fine for interview flow)
                } else {
                    locked.add(e.getKey());
                }
            }
            return locked;
        }

        private boolean isSeatLocked(Show show, Seat seat) {
            SeatLock lock = getLock(show, seat);
            if (lock == null) return false;
            if (lock.isExpired(Instant.now(clock))) {
                unlockSeat(show, seat);
                return false;
            }
            return true;
        }

        private SeatLock getLock(Show show, Seat seat) {
            Map<Seat, SeatLock> seatLocks = locks.get(show);
            if (seatLocks == null) return null;
            return seatLocks.get(seat);
        }

        private void lockSeat(Show show, Seat seat, String user) {
            locks.computeIfAbsent(show, k -> new HashMap<>());
            SeatLock lock = new SeatLock(seat, show, lockTimeoutSeconds, Instant.now(clock), user);
            locks.get(show).put(seat, lock);
        }

        private void unlockSeat(Show show, Seat seat) {
            Map<Seat, SeatLock> seatLocks = locks.get(show);
            if (seatLocks == null) return;
            seatLocks.remove(seat);
        }
    }

    /* ===========================
     *          SERVICES
     * =========================== */

    static final class MovieService {
        private final Map<String, Movie> movies = new HashMap<>();

        public Movie getMovie(String movieId) {
            Movie m = movies.get(movieId);
            if (m == null) throw new RuntimeException("NotFoundException");
            return m;
        }

        public Movie createMovie(String movieName) {
            String id = UUID.randomUUID().toString();
            Movie movie = new Movie(id, movieName);
            movies.put(id, movie);
            return movie;
        }
    }

    static final class TheatreService {
        private final Map<String, Theatre> theatres = new HashMap<>();
        private final Map<String, Screen> screens = new HashMap<>();
        private final Map<String, Seat> seats = new HashMap<>();

        public Theatre getTheatre(String theatreId) {
            Theatre t = theatres.get(theatreId);
            if (t == null) throw new RuntimeException("NotFoundException");
            return t;
        }

        public Screen getScreen(String screenId) {
            Screen s = screens.get(screenId);
            if (s == null) throw new RuntimeException("NotFoundException");
            return s;
        }

        public Seat getSeat(String seatId) {
            Seat s = seats.get(seatId);
            if (s == null) throw new RuntimeException("NotFoundException");
            return s;
        }

        public Theatre createTheatre(String theatreName) {
            String id = UUID.randomUUID().toString();
            Theatre t = new Theatre(id, theatreName);
            theatres.put(id, t);
            return t;
        }

        public Screen createScreenInTheatre(String screenName, Theatre theatre) {
            String id = UUID.randomUUID().toString();
            Screen screen = new Screen(id, screenName, theatre);
            screens.put(id, screen);
            theatre.addScreen(screen);
            return screen;
        }

        public Seat createSeatInScreen(int rowNo, int seatNo, Screen screen) {
            String id = UUID.randomUUID().toString();
            Seat seat = new Seat(id, rowNo, seatNo);
            seats.put(id, seat);
            screen.addSeat(seat);
            return seat;
        }
    }

    static final class ShowService {
        private final Map<String, Show> shows = new HashMap<>();

        public Show getShow(String showId) {
            Show s = shows.get(showId);
            if (s == null) throw new RuntimeException("NotFoundException");
            return s;
        }

        public Show createShow(Movie movie, Screen screen, Date startTime, int durationInSeconds) {
            if (!checkIfShowCreationAllowed(screen, startTime, durationInSeconds)) {
                throw new RuntimeException("ScreenAlreadyOccupiedException");
            }
            String id = UUID.randomUUID().toString();
            Show show = new Show(id, movie, screen, startTime, durationInSeconds);
            shows.put(id, show);
            return show;
        }

        private boolean checkIfShowCreationAllowed(Screen screen, Date startTime, int durationInSeconds) {
            // slot overlap checks can be added later (extensible)
            return true;
        }
    }

    /**
     * Booking flow:
     * 1) createBooking -> seatLockProvider.lockSeats(...) -> Booking CREATED
     * 2) payment success -> confirmBooking -> validates lock -> Booking CONFIRMED
     * 3) payment fails too many times -> unlockSeats
     */
    static final class BookingService {
        private final Map<String, Booking> bookingById = new HashMap<>();
        private final SeatLockProvider seatLockProvider;

        public BookingService(SeatLockProvider seatLockProvider) {
            this.seatLockProvider = Objects.requireNonNull(seatLockProvider);
        }

        public Booking getBooking(String bookingId) {
            Booking b = bookingById.get(bookingId);
            if (b == null) throw new RuntimeException("NotFoundException");
            return b;
        }

        public Booking createBooking(String userId, Show show, List<Seat> seats) {
            if (isAnySeatAlreadyBooked(show, seats)) {
                throw new RuntimeException("SeatPermanentlyUnavailableException");
            }
            seatLockProvider.lockSeats(show, seats, userId);

            String bookingId = UUID.randomUUID().toString();
            Booking booking = new Booking(bookingId, show, userId, seats);
            bookingById.put(bookingId, booking);

            // (Extensible hook) booking expiry scheduler can be plugged here.
            return booking;
        }

        public void confirmBooking(String bookingId, String user) {
            Booking booking = getBooking(bookingId);

            if (!booking.getUser().equals(user)) throw new RuntimeException("BadRequestException");

            for (Seat seat : booking.getSeatsBooked()) {
                if (!seatLockProvider.validateLock(booking.getShow(), seat, user)) {
                    throw new RuntimeException("BadRequestException");
                }
            }

            booking.confirm();

            // optional cleanup: release locks after successful booking
            seatLockProvider.unlockSeats(booking.getShow(), booking.getSeatsBooked(), user);
        }

        public List<Seat> getBookedSeats(Show show) {
            return bookingById.values().stream()
                    .filter(b -> b.getShow().equals(show))
                    .filter(Booking::isConfirmed)
                    .map(Booking::getSeatsBooked)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }

        private boolean isAnySeatAlreadyBooked(Show show, List<Seat> seats) {
            List<Seat> booked = getBookedSeats(show);
            for (Seat s : seats) {
                if (booked.contains(s)) return true;
            }
            return false;
        }
    }

    static final class PaymentsService {
        private final Map<String, Integer> bookingFailures = new HashMap<>(); // bookingId -> count
        private final int allowedRetries;
        private final SeatLockProvider seatLockProvider;

        public PaymentsService(int allowedRetries, SeatLockProvider seatLockProvider) {
            this.allowedRetries = allowedRetries;
            this.seatLockProvider = Objects.requireNonNull(seatLockProvider);
        }

        public void processPaymentFailed(Booking booking, String user) {
            if (!booking.getUser().equals(user)) throw new RuntimeException("BadRequestException");

            int count = bookingFailures.getOrDefault(booking.getId(), 0) + 1;
            bookingFailures.put(booking.getId(), count);

            if (count > allowedRetries) {
                seatLockProvider.unlockSeats(booking.getShow(), booking.getSeatsBooked(), booking.getUser());
            }
        }
    }

    static final class SeatAvailabilityService {
        private final BookingService bookingService;
        private final SeatLockProvider seatLockProvider;

        public SeatAvailabilityService(BookingService bookingService, SeatLockProvider seatLockProvider) {
            this.bookingService = Objects.requireNonNull(bookingService);
            this.seatLockProvider = Objects.requireNonNull(seatLockProvider);
        }

        public List<Seat> getAvailableSeats(Show show) {
            List<Seat> allSeats = show.getScreen().getSeats();
            List<Seat> unavailable = getUnavailableSeats(show);

            List<Seat> available = new ArrayList<>(allSeats);
            available.removeAll(unavailable);
            return available;
        }

        private List<Seat> getUnavailableSeats(Show show) {
            List<Seat> unavailable = new ArrayList<>(bookingService.getBookedSeats(show));
            unavailable.addAll(seatLockProvider.getLockedSeats(show));
            return unavailable;
        }
    }

    /* ===========================
     *         CONTROLLERS
     * =========================== */

    static final class TheatreController {
        private final TheatreService theatreService;

        public TheatreController(TheatreService theatreService) {
            this.theatreService = theatreService;
        }

        public String createTheatre(String theatreName) {
            return theatreService.createTheatre(theatreName).getId();
        }

        public String createScreenInTheatre(String screenName, String theatreId) {
            Theatre t = theatreService.getTheatre(theatreId);
            return theatreService.createScreenInTheatre(screenName, t).getId();
        }

        public String createSeatInScreen(int rowNo, int seatNo, String screenId) {
            Screen s = theatreService.getScreen(screenId);
            return theatreService.createSeatInScreen(rowNo, seatNo, s).getId();
        }
    }

    static final class MovieController {
        private final MovieService movieService;

        public MovieController(MovieService movieService) {
            this.movieService = movieService;
        }

        public String createMovie(String movieName) {
            return movieService.createMovie(movieName).getId();
        }
    }

    static final class ShowController {
        private final ShowService showService;
        private final TheatreService theatreService;
        private final MovieService movieService;
        private final SeatAvailabilityService seatAvailabilityService;

        public ShowController(ShowService showService,
                              TheatreService theatreService,
                              MovieService movieService,
                              SeatAvailabilityService seatAvailabilityService) {
            this.showService = showService;
            this.theatreService = theatreService;
            this.movieService = movieService;
            this.seatAvailabilityService = seatAvailabilityService;
        }

        public String createShow(String movieId, String screenId, Date startTime, int durationInSeconds) {
            Screen screen = theatreService.getScreen(screenId);
            Movie movie = movieService.getMovie(movieId);
            return showService.createShow(movie, screen, startTime, durationInSeconds).getId();
        }

        public List<String> getAvailableSeats(String showId) {
            Show show = showService.getShow(showId);
            return seatAvailabilityService.getAvailableSeats(show).stream()
                    .map(Seat::getId)
                    .collect(Collectors.toList());
        }
    }

    static final class BookingController {
        private final ShowService showService;
        private final TheatreService theatreService;
        private final BookingService bookingService;

        public BookingController(ShowService showService, TheatreService theatreService, BookingService bookingService) {
            this.showService = showService;
            this.theatreService = theatreService;
            this.bookingService = bookingService;
        }

        public String createBooking(String userId, String showId, List<String> seatIds) {
            Show show = showService.getShow(showId);
            List<Seat> seats = seatIds.stream()
                    .map(theatreService::getSeat)
                    .collect(Collectors.toList());
            return bookingService.createBooking(userId, show, seats).getId();
        }
    }

    static final class PaymentsController {
        private final PaymentsService paymentsService;
        private final BookingService bookingService;

        public PaymentsController(PaymentsService paymentsService, BookingService bookingService) {
            this.paymentsService = paymentsService;
            this.bookingService = bookingService;
        }

        public void paymentFailed(String bookingId, String user) {
            Booking booking = bookingService.getBooking(bookingId);
            paymentsService.processPaymentFailed(booking, user);
        }

        public void paymentSuccess(String bookingId, String user) {
            bookingService.confirmBooking(bookingId, user);
        }
    }
}