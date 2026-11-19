package net;

import db.DatabaseConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Multithreaded TCP ServerSocket hub pushing real-time booking, cancellation,
 * showtime reschedule notifications, and seat-map sync events to connected clients.
 */
public class NotificationServer {

    private static volatile NotificationServer embeddedInstance;

    private final int requestedPort;
    private final boolean verbose;
    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private volatile int boundPort;

    private final CopyOnWriteArrayList<ClientSession> sessions = new CopyOnWriteArrayList<>();
    private final ExecutorService clientPool = Executors.newCachedThreadPool(new ThreadFactory() {
        private int count = 1;

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "CineVault-SocketWorker-" + (count++));
            t.setDaemon(true);
            return t;
        }
    });

    private static final class ClientSession {
        final int userId;
        final String username;
        final String role;
        final Socket socket;
        final BufferedWriter writer;

        ClientSession(int userId, String username, String role, Socket socket, BufferedWriter writer) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.socket = socket;
            this.writer = writer;
        }

        synchronized boolean sendLine(String frame) {
            try {
                writer.write(frame);
                writer.write('\n');
                writer.flush();
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        void closeQuietly() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public NotificationServer(int port) {
        this(port, false);
    }

    public NotificationServer(int port, boolean verbose) {
        this.requestedPort = port;
        this.verbose = verbose;
    }

    public static synchronized boolean ensureEmbeddedRunning() {
        if (embeddedInstance != null && embeddedInstance.isRunning()) {
            return true;
        }
        int port = DatabaseConfig.getSocketPort();
        NotificationServer srv = new NotificationServer(port, false);
        try {
            srv.start();
            embeddedInstance = srv;
            System.out.println("[CineVault Socket] Embedded notification hub listening on port " + srv.getPort());
            return true;
        } catch (BindException alreadyBound) {
            // Another CineVault instance or standalone server already owns the port.
            System.out.println("[CineVault Socket] Hub already active on port " + port + "; connecting as client.");
            return false;
        } catch (IOException e) {
            System.err.println("[CineVault Socket] Could not start embedded hub: " + e.getMessage());
            return false;
        }
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        serverSocket = new ServerSocket(requestedPort, 50, InetAddress.getByName("0.0.0.0"));
        boundPort = serverSocket.getLocalPort();
        running = true;

        Thread acceptThread = new Thread(this::acceptLoop, "CineVault-SocketAccept-" + boundPort);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        for (ClientSession s : sessions) {
            s.closeQuietly();
        }
        sessions.clear();
        clientPool.shutdownNow();
    }

    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    public int getPort() {
        return boundPort > 0 ? boundPort : requestedPort;
    }

    public int getConnectedClientCount() {
        return sessions.size();
    }

    private void acceptLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                clientPool.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running && verbose) {
                    System.err.println("[Server] Accept error: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        ClientSession registered = null;
        try {
            socket.setSoTimeout(5000);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            String nonce = SocketProtocol.generateNonce();
            writer.write(SocketProtocol.buildFrame("CHALLENGE", nonce));
            writer.write('\n');
            writer.flush();

            String authLine = reader.readLine();
            List<String> authParts = SocketProtocol.parseFrame(authLine);
            if (authParts.size() < 5 || !"AUTH".equals(authParts.get(0))) {
                writer.write(SocketProtocol.buildFrame("AUTH_FAIL", "Malformed handshake"));
                writer.write('\n');
                writer.flush();
                return;
            }

            int userId;
            try {
                userId = Integer.parseInt(authParts.get(1));
            } catch (NumberFormatException ex) {
                writer.write(SocketProtocol.buildFrame("AUTH_FAIL", "Invalid user ID"));
                writer.write('\n');
                writer.flush();
                return;
            }

            String username = authParts.get(2);
            String role = authParts.get(3);
            String sig = authParts.get(4);

            if (!SocketProtocol.verifyAuth(userId, username, role, nonce, sig)) {
                writer.write(SocketProtocol.buildFrame("AUTH_FAIL", "Invalid HMAC signature"));
                writer.write('\n');
                writer.flush();
                if (verbose) {
                    System.out.println("[Server] Rejected unauthenticated socket from "
                            + socket.getRemoteSocketAddress() + " (user=" + username + ")");
                }
                return;
            }

            socket.setSoTimeout(0);
            boolean isPublisher = "PUBLISHER".equalsIgnoreCase(role);
            if (!isPublisher) {
                registered = new ClientSession(userId, username, role, socket, writer);
                sessions.add(registered);
                if (verbose) {
                    System.out.println("[Server] Connected: @" + username + " (" + role
                            + ") | Active clients: " + sessions.size());
                }
            }

            writer.write(SocketProtocol.buildFrame("AUTH_OK", sessions.size()));
            writer.write('\n');
            writer.flush();

            String line;
            while (running && (line = reader.readLine()) != null) {
                List<String> parts = SocketProtocol.parseFrame(line);
                if (parts.isEmpty()) continue;
                String cmd = parts.get(0);
                if ("PING".equals(cmd)) {
                    writer.write("PONG\n");
                    writer.flush();
                } else if ("PUBLISH".equals(cmd) && parts.size() >= 9) {
                    routePublish(parts);
                } else if ("SYNC".equals(cmd) && parts.size() >= 2) {
                    broadcastFrame(SocketProtocol.buildFrame("SYNC", parts.get(1)));
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (registered != null) {
                sessions.remove(registered);
                if (verbose) {
                    System.out.println("[Server] Disconnected: @" + registered.username
                            + " | Active clients: " + sessions.size());
                }
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void routePublish(List<String> parts) {
        String scope = parts.get(1);
        int targetUserId = parseIntSafe(parts.get(2));
        String notifId = parts.get(3);
        String type = parts.get(4);
        String title = parts.get(5);
        String message = parts.get(6);
        String bookingCode = parts.get(7);
        String epochMillis = parts.get(8);

        String notifyFrame = SocketProtocol.buildFrame(
                "NOTIFY", notifId, targetUserId, type, title, message, bookingCode, epochMillis
        );

        if (verbose) {
            System.out.println("[Server] Dispatch [" + scope + " -> #" + targetUserId
                    + "] " + type + ": " + title);
        }

        for (ClientSession s : sessions) {
            boolean deliver = false;
            if ("ALL".equalsIgnoreCase(scope)) {
                deliver = true;
            } else if ("USER".equalsIgnoreCase(scope)) {
                deliver = (s.userId == targetUserId);
            } else if ("ADMIN".equalsIgnoreCase(scope)) {
                deliver = "ADMIN".equalsIgnoreCase(s.role) && s.userId != targetUserId;
            }
            if (deliver && !s.sendLine(notifyFrame)) {
                sessions.remove(s);
                s.closeQuietly();
            }
        }
    }

    private void broadcastFrame(String frame) {
        for (ClientSession s : sessions) {
            if (!s.sendLine(frame)) {
                sessions.remove(s);
                s.closeQuietly();
            }
        }
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        int port = DatabaseConfig.getSocketPort();
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        NotificationServer server = new NotificationServer(port, true);
        server.start();
        System.out.println("[CineVault Socket Server] Listening on 0.0.0.0:" + server.getPort());
        System.out.println("Press Ctrl+C to stop.");
        Thread.currentThread().join();
    }
}
