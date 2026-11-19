package net;

import auth.User;
import manager.Notification;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persistent TCP Socket client receiving real-time notifications and live sync
 * events from NotificationServer with automatic reconnection.
 */
public class NotificationClient {

    public interface Listener {
        void onConnectionChanged(boolean connected, String statusText);
        void onNotificationReceived(Notification notification);
        void onSyncEvent(String event);
    }

    private final User user;
    private final String host;
    private final int port;
    private final Listener listener;

    private volatile boolean running;
    private volatile boolean connected;
    private volatile Socket activeSocket;
    private Thread workerThread;

    public NotificationClient(User user, String host, int port, Listener listener) {
        this.user = user;
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public synchronized void connect() {
        if (running) {
            return;
        }
        running = true;
        workerThread = new Thread(this::runLoop, "CineVault-SocketClient-" + user.getUsername());
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public synchronized void disconnect() {
        running = false;
        connected = false;
        closeActiveSocket();
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    public boolean isConnected() {
        return connected;
    }

    private void runLoop() {
        while (running) {
            try {
                if (isLoopbackHost(host)) {
                    NotificationServer.ensureEmbeddedRunning();
                }

                Socket sock = new Socket();
                sock.connect(new InetSocketAddress(host, port), 2500);
                sock.setSoTimeout(5000);
                activeSocket = sock;

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8));

                String challengeLine = reader.readLine();
                List<String> chParts = SocketProtocol.parseFrame(challengeLine);
                if (chParts.size() < 2 || !"CHALLENGE".equals(chParts.get(0))) {
                    throw new IOException("Invalid server challenge");
                }
                String nonce = chParts.get(1);
                String sig = SocketProtocol.signAuth(
                        user.getId(), user.getUsername(), user.getRole(), nonce);

                writer.write(SocketProtocol.buildFrame(
                        "AUTH", user.getId(), user.getUsername(), user.getRole(), sig));
                writer.write('\n');
                writer.flush();

                String authResp = reader.readLine();
                List<String> respParts = SocketProtocol.parseFrame(authResp);
                if (respParts.isEmpty() || !"AUTH_OK".equals(respParts.get(0))) {
                    throw new IOException("Auth failed: " + authResp);
                }

                sock.setSoTimeout(0);
                connected = true;
                if (listener != null) {
                    listener.onConnectionChanged(true, "LIVE " + host + ":" + port);
                }

                String line;
                while (running && (line = reader.readLine()) != null) {
                    dispatchIncoming(line);
                }
            } catch (IOException ignored) {
            } finally {
                boolean wasConnected = connected;
                connected = false;
                closeActiveSocket();
                if (running && listener != null && wasConnected) {
                    listener.onConnectionChanged(false, "Reconnecting...");
                }
            }

            if (running) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    if (!running) break;
                }
            }
        }
    }

    private void dispatchIncoming(String line) {
        List<String> parts = SocketProtocol.parseFrame(line);
        if (parts.isEmpty() || listener == null) {
            return;
        }
        String cmd = parts.get(0);
        if ("NOTIFY".equals(cmd) && parts.size() >= 8) {
            int id = parseIntSafe(parts.get(1));
            int uid = parseIntSafe(parts.get(2));
            String type = parts.get(3);
            String title = parts.get(4);
            String msg = parts.get(5);
            String code = parts.get(6).isEmpty() ? null : parts.get(6);
            long millis = parseLongSafe(parts.get(7));
            Notification n = new Notification(
                    id, uid, type, title, msg, code, false,
                    new Timestamp(millis > 0 ? millis : System.currentTimeMillis())
            );
            listener.onNotificationReceived(n);
        } else if ("SYNC".equals(cmd) && parts.size() >= 2) {
            listener.onSyncEvent(parts.get(1));
        }
    }

    private void closeActiveSocket() {
        Socket s = activeSocket;
        activeSocket = null;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static boolean isLoopbackHost(String h) {
        return "127.0.0.1".equals(h) || "localhost".equalsIgnoreCase(h);
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLongSafe(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return System.currentTimeMillis();
        }
    }
}
