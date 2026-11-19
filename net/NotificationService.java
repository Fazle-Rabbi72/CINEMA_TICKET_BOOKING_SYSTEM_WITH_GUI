package net;

import auth.User;
import dao.NotificationDAO;
import db.DatabaseConfig;
import manager.Booking;
import manager.Notification;
import manager.Show;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Facade persisting notifications in MySQL and dispatching live TCP socket pushes
 * for booking confirmations, cancellations, and showtime rescheduling.
 */
public final class NotificationService {

    private static final NotificationDAO NOTIF_DAO = new NotificationDAO();
    private static final ExecutorService PUSH_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "CineVault-NotifPublisher");
            t.setDaemon(true);
            return t;
        }
    });

    private static volatile String overrideHost = null;
    private static volatile int overridePort = -1;

    private NotificationService() {
    }

    public static void setEndpointOverride(String host, int port) {
        overrideHost = host;
        overridePort = port;
    }

    public static void clearEndpointOverride() {
        overrideHost = null;
        overridePort = -1;
    }

    public static String getActiveHost() {
        return overrideHost != null ? overrideHost : DatabaseConfig.getSocketHost();
    }

    public static int getActivePort() {
        return overridePort > 0 ? overridePort : DatabaseConfig.getSocketPort();
    }

    public static CompletableFuture<Void> notifyBookingCreated(Booking booking) {
        if (booking == null) {
            return CompletableFuture.completedFuture(null);
        }
        String title = "Ticket Confirmed: " + booking.getBookingCode();
        String msg = String.format(Locale.US,
                "%s (%s at %s, %s) | Seats: %s | Paid: BDT %.2f",
                booking.getMovieTitle(), booking.getShowDate(), booking.getShowTime(),
                booking.getHallName(), booking.getSeatLabelsDisplay(), booking.getTotalAmount());

        List<String> frames = new ArrayList<>();
        try {
            Notification saved = NOTIF_DAO.create(
                    booking.getUserId(), "BOOKED", title, msg, booking.getBookingCode());
            frames.add(buildPublishFrame("USER", booking.getUserId(), saved));
        } catch (SQLException e) {
            System.err.println("Could not persist BOOKED notification: " + e.getMessage());
        }

        String adminMsg = String.format(Locale.US,
                "@%s booked %s (%s) for %s - BDT %.2f",
                booking.getUsername(), booking.getBookingCode(),
                booking.getSeatLabelsDisplay(), booking.getMovieTitle(), booking.getTotalAmount());
        frames.add(SocketProtocol.buildFrame(
                "PUBLISH", "ADMIN", booking.getUserId(), 0, "BOOKED",
                "New Booking: " + booking.getBookingCode(), adminMsg,
                booking.getBookingCode(), System.currentTimeMillis()
        ));
        frames.add(SocketProtocol.buildFrame("SYNC", "SEATS"));

        return dispatchFramesAsync(frames);
    }

    public static CompletableFuture<Void> notifyBookingCancelled(Booking booking, User actingUser) {
        if (booking == null) {
            return CompletableFuture.completedFuture(null);
        }
        boolean byAdmin = actingUser != null && actingUser.getId() != booking.getUserId();
        String title = "Ticket Cancelled: " + booking.getBookingCode();
        String msg = byAdmin
                ? String.format(Locale.US,
                        "Cancelled by Admin (@%s): %s (%s at %s), Seats %s released.",
                        actingUser.getUsername(), booking.getMovieTitle(),
                        booking.getShowDate(), booking.getShowTime(), booking.getSeatLabelsDisplay())
                : String.format(Locale.US,
                        "Your booking for %s (%s at %s) was cancelled. Seats %s released.",
                        booking.getMovieTitle(), booking.getShowDate(), booking.getShowTime(),
                        booking.getSeatLabelsDisplay());

        List<String> frames = new ArrayList<>();
        try {
            Notification saved = NOTIF_DAO.create(
                    booking.getUserId(), "CANCELLED", title, msg, booking.getBookingCode());
            frames.add(buildPublishFrame("USER", booking.getUserId(), saved));
        } catch (SQLException e) {
            System.err.println("Could not persist CANCELLED notification: " + e.getMessage());
        }

        int excludeActorId = actingUser != null ? actingUser.getId() : 0;
        String adminMsg = String.format(Locale.US,
                "Booking %s (%s) for %s was cancelled by @%s.",
                booking.getBookingCode(), booking.getSeatLabelsDisplay(), booking.getMovieTitle(),
                actingUser != null ? actingUser.getUsername() : booking.getUsername());
        frames.add(SocketProtocol.buildFrame(
                "PUBLISH", "ADMIN", excludeActorId, 0, "CANCELLED",
                "Booking Cancelled: " + booking.getBookingCode(), adminMsg,
                booking.getBookingCode(), System.currentTimeMillis()
        ));
        frames.add(SocketProtocol.buildFrame("SYNC", "SEATS"));

        return dispatchFramesAsync(frames);
    }

    public static CompletableFuture<Void> notifyShowRescheduled(Show updatedShow,
                                                                String oldHall, LocalDate oldDate, String oldTime,
                                                                List<Booking> affectedBookings) {
        if (updatedShow == null) {
            return CompletableFuture.completedFuture(null);
        }
        List<String> frames = new ArrayList<>();
        String movieTitle = updatedShow.getMovie() != null ? updatedShow.getMovie().getTitle() : "Movie";
        int count = affectedBookings != null ? affectedBookings.size() : 0;

        if (affectedBookings != null) {
            for (Booking b : affectedBookings) {
                String title = "Show Rescheduled: " + movieTitle;
                String msg = String.format(Locale.US,
                        "Booking %s (%s) moved from %s %s (%s) to %s %s (%s).",
                        b.getBookingCode(), b.getSeatLabelsDisplay(),
                        oldDate, oldTime, oldHall,
                        updatedShow.getShowDate(), updatedShow.getShowTime(), updatedShow.getHallName());
                try {
                    Notification saved = NOTIF_DAO.create(
                            b.getUserId(), "RESCHEDULED", title, msg, b.getBookingCode());
                    frames.add(buildPublishFrame("USER", b.getUserId(), saved));
                } catch (SQLException e) {
                    System.err.println("Could not persist RESCHEDULED notification: " + e.getMessage());
                }
            }
        }

        String adminMsg = String.format(Locale.US,
                "%s moved to %s %s (%s). Notified %d active ticket holder(s).",
                movieTitle, updatedShow.getShowDate(), updatedShow.getShowTime(),
                updatedShow.getHallName(), count);
        frames.add(SocketProtocol.buildFrame(
                "PUBLISH", "ADMIN", 0, 0, "RESCHEDULED",
                "Show Rescheduled: " + movieTitle, adminMsg,
                "", System.currentTimeMillis()
        ));
        frames.add(SocketProtocol.buildFrame("SYNC", "CATALOG"));

        return dispatchFramesAsync(frames);
    }

    private static String buildPublishFrame(String scope, int targetUserId, Notification n) {
        long ts = n.getCreatedAt() != null ? n.getCreatedAt().getTime() : System.currentTimeMillis();
        return SocketProtocol.buildFrame(
                "PUBLISH", scope, targetUserId, n.getId(), n.getType(),
                n.getTitle(), n.getMessage(),
                n.getBookingCode() == null ? "" : n.getBookingCode(), ts
        );
    }

    private static CompletableFuture<Void> dispatchFramesAsync(List<String> frames) {
        final String host = getActiveHost();
        final int port = getActivePort();
        return CompletableFuture.runAsync(() -> sendFramesOverSocket(host, port, frames), PUSH_EXECUTOR);
    }

    private static void sendFramesOverSocket(String host, int port, List<String> frames) {
        if (frames == null || frames.isEmpty()) {
            return;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            socket.setSoTimeout(2000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            List<String> ch = SocketProtocol.parseFrame(reader.readLine());
            if (ch.size() < 2 || !"CHALLENGE".equals(ch.get(0))) {
                return;
            }
            String nonce = ch.get(1);
            String sig = SocketProtocol.signAuth(0, "system-publisher", "PUBLISHER", nonce);
            writer.write(SocketProtocol.buildFrame("AUTH", 0, "system-publisher", "PUBLISHER", sig));
            writer.write('\n');
            writer.flush();

            List<String> authResp = SocketProtocol.parseFrame(reader.readLine());
            if (authResp.isEmpty() || !"AUTH_OK".equals(authResp.get(0))) {
                return;
            }

            for (String frame : frames) {
                writer.write(frame);
                writer.write('\n');
            }
            writer.flush();
        } catch (IOException e) {
            // Socket server offline is non-fatal because DB persistence already succeeded.
            System.err.println("[CineVault Socket] Push skipped (" + host + ":" + port + "): " + e.getMessage());
        }
    }
}
