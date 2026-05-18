package LLD.LldQuestions.TicketBooking;

import LLD.LldQuestions.TicketBooking.lock.InMemorySeatLockProvider;
import LLD.LldQuestions.TicketBooking.lock.SeatLockProvider;
import LLD.LldQuestions.TicketBooking.models.Booking;
import LLD.LldQuestions.TicketBooking.models.Movie;
import LLD.LldQuestions.TicketBooking.models.Screen;
import LLD.LldQuestions.TicketBooking.models.Seat;
import LLD.LldQuestions.TicketBooking.models.SeatType;
import LLD.LldQuestions.TicketBooking.models.Show;
import LLD.LldQuestions.TicketBooking.models.Theatre;
import LLD.LldQuestions.TicketBooking.pricing.DefaultPricingStrategy;
import LLD.LldQuestions.TicketBooking.pricing.PricingStrategy;
import LLD.LldQuestions.TicketBooking.repository.BookingRepository;
import LLD.LldQuestions.TicketBooking.repository.ShowRepository;
import LLD.LldQuestions.TicketBooking.service.BookingService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Demo entry point.
 *
 * Scenario 1: 50 users race to lock the SAME 2 seats. Exactly ONE must win.
 * Scenario 2: Winner pays "too slowly" -> their lock expires -> a new user
 *             takes the same seats successfully.
 * Scenario 3: Two non-overlapping parties on the same show book in parallel
 *             with no contention.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        // ── Wiring ────────────────────────────────────────────────────────────
        ShowRepository showRepository = new ShowRepository();
        BookingRepository bookingRepository = new BookingRepository();

        // Short TTL so scenario 2 is observable in a demo run.
        SeatLockProvider seatLockProvider = new InMemorySeatLockProvider(
                /*lockTimeoutSeconds=*/ 2,
                /*cleanupIntervalSeconds=*/ 1);

        BookingService bookingService =
                new BookingService(showRepository, bookingRepository, seatLockProvider);

        PricingStrategy pricingStrategy = buildPricingStrategy();
        Show show = seedShow(showRepository, pricingStrategy);

        runPricingDemo(pricingStrategy);

        // ── Scenario 1: thundering herd on the same seats ─────────────────────
        runThunderingHerd(bookingService, show.getId());

        // ── Scenario 2: lock TTL expiry hands the seat to a new user ─────────
        runLockExpiryScenario(bookingService, show.getId());

        // ── Scenario 3: independent parties on different seats run in parallel
        runIndependentParties(bookingService, show.getId());

        ((InMemorySeatLockProvider) seatLockProvider).shutdown();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Pricing wiring + demo
    // ────────────────────────────────────────────────────────────────────────

    private static PricingStrategy buildPricingStrategy() {
        Map<String, Double> moviePrice = new HashMap<>();
        moviePrice.put("m_dhurandhar", 500.0); // per-movie override

        Map<String, Double> languagePrice = new HashMap<>();
        languagePrice.put("Tamil", 200.0);
        languagePrice.put("Telugu", 200.0);
        languagePrice.put("Kannada", 200.0);
        languagePrice.put("Malayalam", 200.0);
        languagePrice.put("Hindi", 300.0);
        languagePrice.put("English", 350.0);

        return new DefaultPricingStrategy(moviePrice, languagePrice, /*defaultBase=*/300.0);
    }

    private static void runPricingDemo(PricingStrategy pricingStrategy) {
        System.out.println("\n=== Scenario 0: Pricing strategy demo ===");

        List<Movie> movies = Arrays.asList(
                new Movie("m_dhurandhar", "Dhurandhar",      165, "Hindi"),   // per-movie override -> 500
                new Movie("m_kgf3",       "KGF Chapter 3",   170, "Kannada"), // language default  -> 200
                new Movie("m_inception",  "Inception",       148, "English"));// language default  -> 350

        Seat regular  = new Seat("A1", 0, 1, SeatType.REGULAR);
        Seat premium  = new Seat("C1", 2, 1, SeatType.PREMIUM);
        Seat recliner = new Seat("D1", 3, 1, SeatType.RECLINER);

        for (Movie movie : movies) {
            double r = pricingStrategy.priceFor(movie, regular);
            double p = pricingStrategy.priceFor(movie, premium);
            double x = pricingStrategy.priceFor(movie, recliner);
            System.out.printf("  %-15s (%s) -> REGULAR=%.0f  PREMIUM=%.0f  RECLINER=%.0f%n",
                    movie.getName(), movie.getLanguage(), r, p, x);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Scenarios
    // ────────────────────────────────────────────────────────────────────────

    private static void runThunderingHerd(BookingService bookingService, String showId)
            throws InterruptedException {
        System.out.println("\n=== Scenario 1: 50 users race for seats [A1, A2] ===");

        List<String> contestedSeats = Arrays.asList("A1", "A2");
        int contestants = 50;

        AtomicInteger winners = new AtomicInteger(0);
        AtomicInteger losers = new AtomicInteger(0);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(contestants);

        ExecutorService pool = Executors.newFixedThreadPool(contestants);

        for (int i = 0; i < contestants; i++) {
            final String userId = "u" + i;
            pool.submit(() -> {
                try {
                    start.await(); // align all threads on the firing line
                    Booking b = bookingService.initiateBooking(userId, showId, contestedSeats);
                    winners.incrementAndGet();
                    System.out.println("  WINNER " + userId + " -> bookingId=" + b.getId());
                } catch (Exception e) {
                    losers.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();
        pool.shutdown();

        System.out.println("  total winners=" + winners.get() + " losers=" + losers.get());
        if (winners.get() != 1) {
            throw new IllegalStateException(
                    "INVARIANT VIOLATED: expected exactly 1 winner, got " + winners.get());
        }
    }

    private static void runLockExpiryScenario(BookingService bookingService, String showId)
            throws InterruptedException {
        System.out.println("\n=== Scenario 2: lock TTL expiry transfers seats to a new user ===");

        List<String> seats = Arrays.asList("B1", "B2");

        // u_slow grabs the seats but never confirms; lock will lapse after TTL.
        Booking slowBooking = bookingService.initiateBooking("u_slow", showId, seats);
        System.out.println("  u_slow locked " + seats + " (bookingId=" + slowBooking.getId() + ")");

        // u_fast tries immediately -- expected to fail because the lock is fresh.
        try {
            bookingService.initiateBooking("u_fast", showId, seats);
            throw new IllegalStateException("u_fast should have been rejected!");
        } catch (Exception e) {
            System.out.println("  u_fast rejected (as expected): " + e.getMessage());
        }

        // Wait past TTL (2s) + a small buffer for the cleanup sweep.
        TimeUnit.MILLISECONDS.sleep(2500);

        // Now u_fast retries -- expected to succeed.
        Booking fastBooking = bookingService.initiateBooking("u_fast", showId, seats);
        System.out.println("  u_fast acquired " + seats + " after TTL (bookingId="
                + fastBooking.getId() + ")");

        // u_slow tries to confirm -- expected to fail with SeatLockExpiredException.
        try {
            bookingService.confirmBooking(slowBooking.getId(), "u_slow");
            throw new IllegalStateException("u_slow should not have been able to confirm!");
        } catch (Exception e) {
            System.out.println("  u_slow.confirm rejected (as expected): " + e.getMessage());
        }

        // u_fast confirms successfully.
        Booking confirmed = bookingService.confirmBooking(fastBooking.getId(), "u_fast");
        System.out.println("  u_fast confirmed -> status=" + confirmed.getStatus());
    }

    private static void runIndependentParties(BookingService bookingService, String showId)
            throws InterruptedException {
        System.out.println("\n=== Scenario 3: independent parties book the same show in parallel ===");

        Runnable party1 = () -> {
            Booking b = bookingService.initiateBooking(
                    "party1", showId, Arrays.asList("C1", "C2", "C3"));
            bookingService.confirmBooking(b.getId(), "party1");
            System.out.println("  party1 confirmed seats [C1,C2,C3]");
        };
        Runnable party2 = () -> {
            Booking b = bookingService.initiateBooking(
                    "party2", showId, Arrays.asList("D1", "D2"));
            bookingService.confirmBooking(b.getId(), "party2");
            System.out.println("  party2 confirmed seats [D1,D2]");
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(party1);
        pool.submit(party2);
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Seed data
    // ────────────────────────────────────────────────────────────────────────

    private static Show seedShow(ShowRepository showRepository, PricingStrategy pricingStrategy) {
        Movie movie = new Movie("m1", "Inception", 148, "English");
        Theatre theatre = new Theatre("t1", "PVR Forum", "Bengaluru");
        Screen screen = new Screen("s1", "Audi-1", theatre.getId());

        // 4 rows (A-D) x 5 columns -- enough seats for all three scenarios.
        char[] rows = {'A', 'B', 'C', 'D'};
        for (char row : rows) {
            for (int col = 1; col <= 5; col++) {
                String id = "" + row + col;
                SeatType type = (row == 'D') ? SeatType.RECLINER
                              : (row == 'C') ? SeatType.PREMIUM
                              : SeatType.REGULAR;
                screen.addSeat(new Seat(id, row - 'A', col, type));
            }
        }
        theatre.addScreen(screen);

        Show show = new Show(
                "show-1",
                movie,
                screen,
                LocalDateTime.now().plusHours(2),
                LocalDateTime.now().plusHours(4),
                pricingStrategy);
        showRepository.addShow(show);
        return show;
    }

    @SuppressWarnings("unused")
    private static List<String> mutable(String... seats) {
        // The Booking model wraps the list with unmodifiableList, so callers
        // can pass a normal mutable list here without worrying about leaking it.
        return new ArrayList<>(Arrays.asList(seats));
    }
}
