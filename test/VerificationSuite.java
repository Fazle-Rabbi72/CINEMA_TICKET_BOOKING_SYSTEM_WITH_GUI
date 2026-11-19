package test;

import auth.AuthManager;
import auth.LoginGUI;
import auth.User;
import dao.AuditLogDAO;
import dao.BookingDAO;
import dao.CouponDAO;
import dao.MovieDAO;
import dao.NotificationDAO;
import dao.ShowDAO;
import dao.SnackDAO;
import dao.UserDAO;
import db.DatabaseConnection;
import db.DatabaseInitializer;
import entity.PremiumCustomer;
import entity.RegularCustomer;
import interfaces.IBookingOperations;
import manager.AuditLog;
import manager.Booking;
import manager.Coupon;
import manager.Movie;
import manager.Notification;
import manager.SeatInfo;
import manager.Show;
import manager.SnackItem;
import manager.Ticket;
import net.NotificationClient;
import net.NotificationServer;
import net.NotificationService;
import net.SocketProtocol;
import security.PasswordHasher;
import security.SessionManager;
import security.TicketSigner;
import ui.CinemaGUI;
import ui.TicketDialog;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end automated verification suite covering database schema, PBKDF2 hashing,
 * SQLi/brute-force protection, OOP contracts, ACID booking, double-booking guard,
 * HMAC ticket verification, cancellation + re-booking, analytics, and GUI smoke tests.
 */
public class VerificationSuite {

    private static int passed = 0;
    private static final String TEST_USERNAME = "test_verify_user";
    private static final String TEST_PASS = "Verify@2026!";
    private static final List<String> CREATED_CODES = new ArrayList<>();
    private static boolean usedWelcomeCoupon = false;

    public static void main(String[] args) throws Exception {
        System.out.println("=== CineVault Automated Verification Suite ===");

        try {
            testDatabaseInitialization();
            testPasswordHasher();
            testOopContracts();
            testAuthAndLockoutSecurity();
            testBookingVerificationCancellationAndRebooking();
            testRealTimeSocketNotifications();
            testAnalyticsAndAuditLogs();
            testGuiSmokeInstantiation();
            System.out.println("\nALL TESTS PASSED (" + passed + " checks).");
        } finally {
            cleanupTestData();
        }
    }

    private static void assertTrue(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("FAIL: " + name);
        }
        passed++;
        System.out.println("PASS: " + name);
    }

    private static void testDatabaseInitialization() throws Exception {
        DatabaseInitializer.initialize();
        DatabaseConnection.ConnectionStatus st = DatabaseConnection.testConnection();
        assertTrue(st.isConnected(), "MySQL JDBC connection healthy (" + st.getServerVersion() + ")");

        String[] expectedTables = {
                "users", "movies", "shows", "snacks", "coupons",
                "bookings", "booking_seats", "booking_snacks", "audit_logs", "notifications"
        };
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (String tbl : expectedTables) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
                    ps.setString(1, tbl);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next() && rs.getInt(1) == 1, "Table exists: " + tbl);
                    }
                }
            }
        }

        MovieDAO movieDAO = new MovieDAO();
        ShowDAO showDAO = new ShowDAO();
        SnackDAO snackDAO = new SnackDAO();
        UserDAO userDAO = new UserDAO();
        assertTrue(movieDAO.findAllActive().size() >= 10, "Seeded >= 10 movies");
        assertTrue(showDAO.findAllActiveShows().size() >= 14, "Seeded >= 14 shows");
        assertTrue(snackDAO.findAllAvailable().size() >= 6, "Seeded >= 6 snacks");
        assertTrue(userDAO.findByUsername("admin") != null && userDAO.findByUsername("admin").isAdmin(),
                "Seeded admin user exists with ADMIN role");
    }

    private static void testPasswordHasher() {
        char[] raw1 = "StrongPass#99".toCharArray();
        char[] raw2 = "StrongPass#99".toCharArray();
        String h1 = PasswordHasher.hash(raw1);
        String h2 = PasswordHasher.hash(raw2);
        assertTrue(h1.startsWith("PBKDF2$65536$"), "PBKDF2 hash format valid");
        assertTrue(!h1.equals(h2), "Unique random salt generated per hash");
        assertTrue(PasswordHasher.verify("StrongPass#99".toCharArray(), h1), "Valid password verifies");
        assertTrue(!PasswordHasher.verify("WrongPass#99".toCharArray(), h1), "Wrong password rejected");
        PasswordHasher.wipe(raw1);
        PasswordHasher.wipe(raw2);
    }

    private static void testOopContracts() {
        RegularCustomer reg = new RegularCustomer("Regular");
        PremiumCustomer prem = new PremiumCustomer("Premium");
        assertTrue(Math.abs(reg.getDiscount() - 0.0) < 0.0001, "RegularCustomer discount is 0%");
        assertTrue(Math.abs(prem.getDiscount() - 0.20) < 0.0001, "PremiumCustomer discount is 20%");
        Ticket t = new Ticket(null, null, Collections.emptyList(), Collections.emptyMap(), null, "Card");
        assertTrue(t instanceof IBookingOperations, "Ticket implements IBookingOperations");
    }

    private static void testAuthAndLockoutSecurity() throws Exception {
        cleanupTestData();

        AuthManager.AuthResult weakSignup = AuthManager.signup(
                "Test User", TEST_USERNAME, "testverify@cinevault.local", "+8801711112222", "123".toCharArray());
        assertTrue(!weakSignup.isSuccess(), "Weak password rejected on signup");

        AuthManager.AuthResult sqli = AuthManager.login("' OR '1'='1", "anything".toCharArray());
        assertTrue(!sqli.isSuccess(), "SQL injection payload safely rejected");

        AuthManager.AuthResult okSignup = AuthManager.signup(
                "Verification Test User", TEST_USERNAME, "testverify@cinevault.local",
                "+8801711112222", TEST_PASS.toCharArray());
        assertTrue(okSignup.isSuccess() && okSignup.getUser() != null, "Valid user signup succeeds");

        for (int i = 1; i <= AuthManager.MAX_FAILED_ATTEMPTS; i++) {
            AuthManager.AuthResult fail = AuthManager.login(TEST_USERNAME, "WrongPassword!9".toCharArray());
            assertTrue(!fail.isSuccess(), "Failed login #" + i + " rejected");
        }

        AuthManager.AuthResult lockedAttempt = AuthManager.login(TEST_USERNAME, TEST_PASS.toCharArray());
        assertTrue(!lockedAttempt.isSuccess() && lockedAttempt.getMessage().toLowerCase().contains("locked"),
                "Correct password blocked while account locked");

        UserDAO userDAO = new UserDAO();
        User lockedUser = userDAO.findByUsername(TEST_USERNAME);
        userDAO.unlockAccount(lockedUser.getId());

        AuthManager.AuthResult unlockedLogin = AuthManager.login(TEST_USERNAME, TEST_PASS.toCharArray());
        assertTrue(unlockedLogin.isSuccess() && SessionManager.isLoggedIn(),
                "Login succeeds after admin account unlock");
        AuthManager.logout();
    }

    private static void testBookingVerificationCancellationAndRebooking() throws Exception {
        UserDAO userDAO = new UserDAO();
        ShowDAO showDAO = new ShowDAO();
        SnackDAO snackDAO = new SnackDAO();
        CouponDAO couponDAO = new CouponDAO();
        BookingDAO bookingDAO = new BookingDAO();

        User testUser = userDAO.findByUsername(TEST_USERNAME);
        User admin = userDAO.findByUsername("admin");
        List<Show> shows = showDAO.findAllActiveShows();
        Show show = shows.get(0);
        SeatInfo[][] map = showDAO.getSeatMap(show);

        // Find two free seats in the back VIP row
        int targetRow = show.getRows() - 1;
        List<SeatInfo> chosen = new ArrayList<>();
        for (int c = 0; c < show.getCols() && chosen.size() < 2; c++) {
            if (!map[targetRow][c].isBooked()) {
                chosen.add(map[targetRow][c]);
            }
        }
        assertTrue(chosen.size() == 2, "Found 2 available VIP seats for test booking");

        List<SnackItem> snacks = snackDAO.findAllAvailable();
        Map<SnackItem, Integer> snackOrder = new LinkedHashMap<>();
        snackOrder.put(snacks.get(0), 2);
        Coupon coupon = couponDAO.findValidByCode("WELCOME10");
        assertTrue(coupon != null, "Coupon WELCOME10 active in DB");

        Ticket ticket = new Ticket(testUser, show, chosen, snackOrder, coupon, "bKash");
        double expectedTotal = ticket.calculateTotal();
        ticket.bookTicket();
        Booking created = ticket.getPersistedBooking();
        if (created != null) {
            CREATED_CODES.add(created.getBookingCode());
            usedWelcomeCoupon = true;
        }

        assertTrue(created != null && created.getBookingCode().startsWith("CV-"),
                "ACID booking created with booking code " + (created != null ? created.getBookingCode() : ""));
        assertTrue(Math.abs(created.getTotalAmount() - expectedTotal) < 0.01,
                "Persisted total matches calculated total (BDT " + expectedTotal + ")");
        assertTrue(ticket.verifyTicket(), "Ticket.verifyTicket() confirms authentic HMAC-SHA256 signature");

        List<Booking> userHistory = bookingDAO.findByUser(testUser.getId());
        assertTrue(userHistory.stream().anyMatch(b -> b.getBookingCode().equals(created.getBookingCode())),
                "BookingDAO.findByUser() returns newly created booking");

        // Double booking guard test on first seat
        boolean blockedDouble = false;
        try {
            Ticket dupTicket = new Ticket(testUser, show,
                    Collections.singletonList(chosen.get(0)), Collections.emptyMap(), null, "Card");
            dupTicket.bookTicket();
        } catch (BookingDAO.SeatAlreadyBookedException ex) {
            blockedDouble = true;
        }
        assertTrue(blockedDouble, "Double-booking same seat blocked by UNIQUE constraint & transaction rollback");

        // Verification & tamper detection
        BookingDAO.VerificationResult vr = bookingDAO.verifyBookingByCode(created.getBookingCode(), admin);
        assertTrue(vr.isFound() && vr.isAuthentic() && vr.isConfirmed(),
                "Admin verifier confirms ticket AUTHENTIC & CONFIRMED");

        boolean tamperedTotal = TicketSigner.verify(
                created.getBookingCode(), created.getUserId(), created.getShowId(),
                created.getSeatLabels(), created.getTotalAmount() + 1.00, created.getTicketSignature());
        assertTrue(!tamperedTotal, "Tampered total fails HMAC-SHA256 signature verification");

        boolean tamperedSeat = TicketSigner.verify(
                created.getBookingCode(), created.getUserId(), created.getShowId(),
                Arrays.asList("Z9"), created.getTotalAmount(), created.getTicketSignature());
        assertTrue(!tamperedSeat, "Tampered seat fails HMAC-SHA256 signature verification");

        // Authorization check: guest cannot cancel testUser's ticket
        User guest = userDAO.findByUsername("guest");
        boolean unauthorizedBlocked = false;
        try {
            bookingDAO.cancelBooking(created.getId(), guest);
        } catch (SecurityException ex) {
            unauthorizedBlocked = true;
        }
        assertTrue(unauthorizedBlocked, "Unauthorized cancellation by another user blocked");

        // Cancel & verify re-booking works
        ticket.cancelTicket();
        Booking cancelled = bookingDAO.findByCode(created.getBookingCode());
        assertTrue(cancelled != null && !cancelled.isConfirmed(), "Booking status updated to CANCELLED");

        BookingDAO.VerificationResult vrCancelled = bookingDAO.verifyBookingByCode(created.getBookingCode(), admin);
        assertTrue(vrCancelled.isFound() && vrCancelled.isAuthentic() && !vrCancelled.isConfirmed(),
                "Cancelled ticket retains authentic signature while marked CANCELLED");

        Ticket rebook = new Ticket(testUser, show,
                Collections.singletonList(chosen.get(0)), Collections.emptyMap(), null, "Card");
        rebook.bookTicket();
        Booking rebooked = rebook.getPersistedBooking();
        if (rebooked != null) {
            CREATED_CODES.add(rebooked.getBookingCode());
        }
        assertTrue(rebooked != null, "Released seat can be cleanly re-booked after cancellation");
        rebook.cancelTicket();
    }

    private static void testRealTimeSocketNotifications() throws Exception {
        UserDAO userDAO = new UserDAO();
        ShowDAO showDAO = new ShowDAO();
        BookingDAO bookingDAO = new BookingDAO();
        NotificationDAO notifDAO = new NotificationDAO();

        User testUser = userDAO.findByUsername(TEST_USERNAME);
        User admin = userDAO.findByUsername("admin");
        Show show = showDAO.findAllActiveShows().get(0);
        String origHall = show.getHallName();
        LocalDate origDate = show.getShowDate();
        String origTime = show.getShowTime();

        NotificationServer testServer = new NotificationServer(0, false);
        testServer.start();
        int port = testServer.getPort();
        NotificationService.setEndpointOverride("127.0.0.1", port);

        NotificationClient userClient = null;
        NotificationClient adminClient = null;
        try {
            // 1. Forged socket handshake must be rejected with AUTH_FAIL
            try (Socket badSock = new Socket("127.0.0.1", port)) {
                badSock.setSoTimeout(3000);
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(badSock.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter bw = new BufferedWriter(
                        new OutputStreamWriter(badSock.getOutputStream(), StandardCharsets.UTF_8));
                String ch = br.readLine();
                assertTrue(ch != null && ch.startsWith("CHALLENGE|"), "Socket server issues CHALLENGE nonce");
                bw.write(SocketProtocol.buildFrame("AUTH", testUser.getId(), testUser.getUsername(), "CUSTOMER", "DEADBEEF_FORGED"));
                bw.write('\n');
                bw.flush();
                String resp = br.readLine();
                assertTrue(resp != null && resp.startsWith("AUTH_FAIL|"), "Forged socket HMAC handshake rejected");
            }

            // 2. Connect authenticated user & admin TCP socket clients
            CountDownLatch connectLatch = new CountDownLatch(2);
            CountDownLatch bookedLatch = new CountDownLatch(1);
            CountDownLatch reschedLatch = new CountDownLatch(1);
            CountDownLatch cancelLatch = new CountDownLatch(1);
            CountDownLatch syncLatch = new CountDownLatch(1);
            List<Notification> userReceived = new CopyOnWriteArrayList<>();

            userClient = new NotificationClient(testUser, "127.0.0.1", port, new NotificationClient.Listener() {
                @Override
                public void onConnectionChanged(boolean connected, String statusText) {
                    if (connected) connectLatch.countDown();
                }

                @Override
                public void onNotificationReceived(Notification n) {
                    userReceived.add(n);
                    if ("BOOKED".equals(n.getType())) bookedLatch.countDown();
                    if ("RESCHEDULED".equals(n.getType())) reschedLatch.countDown();
                    if ("CANCELLED".equals(n.getType())) cancelLatch.countDown();
                }

                @Override
                public void onSyncEvent(String event) {
                    syncLatch.countDown();
                }
            });

            adminClient = new NotificationClient(admin, "127.0.0.1", port, new NotificationClient.Listener() {
                @Override
                public void onConnectionChanged(boolean connected, String statusText) {
                    if (connected) connectLatch.countDown();
                }

                @Override
                public void onNotificationReceived(Notification n) {
                }

                @Override
                public void onSyncEvent(String event) {
                }
            });

            userClient.connect();
            adminClient.connect();
            assertTrue(connectLatch.await(4, TimeUnit.SECONDS), "Authenticated TCP NotificationClients connected");

            // Pick 1 free seat on the show
            SeatInfo[][] map = showDAO.getSeatMap(show);
            SeatInfo chosenSeat = null;
            for (int c = 0; c < show.getCols(); c++) {
                if (!map[0][c].isBooked()) {
                    chosenSeat = map[0][c];
                    break;
                }
            }
            assertTrue(chosenSeat != null, "Found free seat for live notification test");

            // 3. Test BOOKED notification
            Ticket ticket = new Ticket(testUser, show, Collections.singletonList(chosenSeat),
                    Collections.emptyMap(), null, "Card");
            ticket.bookTicket();
            Booking booked = ticket.getPersistedBooking();
            CREATED_CODES.add(booked.getBookingCode());

            NotificationService.notifyBookingCreated(booked).get(4, TimeUnit.SECONDS);
            assertTrue(bookedLatch.await(4, TimeUnit.SECONDS), "Real-time BOOKED socket notification received");
            assertTrue(syncLatch.await(4, TimeUnit.SECONDS), "Real-time SYNC socket broadcast received");

            // 4. Test RESCHEDULED notification (and restore show afterward)
            LocalDate newDate = origDate.plusDays(3);
            Show updated = showDAO.rescheduleShow(show.getId(), "Hall 3 - VIP Lounge", newDate, "11:00 PM");
            try {
                List<Booking> affected = bookingDAO.findConfirmedBookingsByShow(show.getId());
                assertTrue(affected.stream().anyMatch(b -> b.getBookingCode().equals(booked.getBookingCode())),
                        "findConfirmedBookingsByShow() includes affected ticket");
                NotificationService.notifyShowRescheduled(updated, origHall, origDate, origTime, affected)
                        .get(4, TimeUnit.SECONDS);
                assertTrue(reschedLatch.await(4, TimeUnit.SECONDS),
                        "Real-time RESCHEDULED socket notification received");
            } finally {
                showDAO.rescheduleShow(show.getId(), origHall, origDate, origTime);
            }

            // 5. Test CANCELLED notification by Admin
            bookingDAO.cancelBooking(booked.getId(), admin);
            NotificationService.notifyBookingCancelled(booked, admin).get(4, TimeUnit.SECONDS);
            assertTrue(cancelLatch.await(4, TimeUnit.SECONDS), "Real-time CANCELLED socket notification received");

            // 6. Verify DB notification persistence & read-state API
            assertTrue(notifDAO.countUnread(testUser.getId()) >= 3,
                    "All 3 notification types persisted as unread in MySQL");
            List<Notification> stored = notifDAO.findByUser(testUser.getId(), 10);
            assertTrue(stored.size() >= 3, "NotificationDAO.findByUser returns persisted notifications");
            notifDAO.markRead(stored.get(0).getId(), testUser.getId());
            notifDAO.markAllRead(testUser.getId());
            assertTrue(notifDAO.countUnread(testUser.getId()) == 0,
                    "NotificationDAO.markAllRead marks all notifications read");
        } finally {
            if (userClient != null) userClient.disconnect();
            if (adminClient != null) adminClient.disconnect();
            NotificationService.clearEndpointOverride();
            testServer.stop();
        }
    }

    private static void testAnalyticsAndAuditLogs() throws Exception {
        BookingDAO bookingDAO = new BookingDAO();
        AuditLogDAO auditDAO = new AuditLogDAO();

        assertTrue(bookingDAO.getTotalRevenue() >= 0.0, "Total revenue query executes");
        assertTrue(bookingDAO.getActiveBookingCount() >= 0, "Active booking count query executes");
        assertTrue(!bookingDAO.getRevenueByMovie().isEmpty(), "Revenue by movie analytics populated");
        assertTrue(!bookingDAO.getHallOccupancyStats().isEmpty(), "Hall occupancy stats populated");

        List<AuditLog> logs = auditDAO.findRecent(20, "ALL", "");
        assertTrue(!logs.isEmpty(), "Audit log entries recorded and queried");
    }

    private static void testGuiSmokeInstantiation() throws Exception {
        UserDAO userDAO = new UserDAO();
        User admin = userDAO.findByUsername("admin");
        User guest = userDAO.findByUsername("guest");
        Booking sampleBooking = new Booking(
                9999, "CV-2026-DEMO01", admin.getId(), admin.getUsername(), admin.getFullName(),
                1, "Interstellar", "Hall 1 - IMAX 3D", java.time.LocalDate.now(), "07:30 PM",
                null, 1500.0, 300.0, 280.0, 1480.0, "Card / Visa", "CONFIRMED",
                "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF",
                new java.sql.Timestamp(System.currentTimeMillis()),
                Arrays.asList("E1", "E2"), "Large Caramel Popcorn x1",
                Collections.emptyList(), Collections.emptyMap()
        );

        SwingUtilities.invokeAndWait(() -> {
            LoginGUI login = new LoginGUI();
            login.dispose();

            SessionManager.login(admin);
            CinemaGUI adminGui = new CinemaGUI(admin);
            adminGui.dispose();

            SessionManager.login(guest);
            CinemaGUI guestGui = new CinemaGUI(guest);
            guestGui.dispose();

            TicketDialog td = new TicketDialog(null, sampleBooking);
            td.dispose();

            SessionManager.logout();
        });
        assertTrue(true, "GUI smoke instantiation succeeded for LoginGUI, CinemaGUI (Admin + Guest), and TicketDialog");
    }

    private static void cleanupTestData() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (String code : CREATED_CODES) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM audit_logs WHERE details LIKE ?")) {
                    ps.setString(1, "%" + code + "%");
                    ps.executeUpdate();
                }
            }
            CREATED_CODES.clear();

            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM audit_logs WHERE username IN (?, ?) OR details LIKE ?")) {
                ps.setString(1, TEST_USERNAME);
                ps.setString(2, "' OR '1'='1");
                ps.setString(3, "%" + TEST_USERNAME + "%");
                ps.executeUpdate();
            }
            if (usedWelcomeCoupon) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE coupons SET used_count = GREATEST(0, used_count - 1) WHERE code = 'WELCOME10'")) {
                    ps.executeUpdate();
                }
                usedWelcomeCoupon = false;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM users WHERE username = ?")) {
                ps.setString(1, TEST_USERNAME);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Cleanup warning: " + e.getMessage());
        }
    }
}
