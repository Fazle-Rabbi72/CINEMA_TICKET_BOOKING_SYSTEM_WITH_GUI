import auth.LoginGUI;
import db.DatabaseConfig;
import db.DatabaseInitializer;
import net.NotificationServer;
import ui.Components;
import ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;

/**
 * Application entry point: configures high-DPI rendering, initializes the
 * MySQL/MariaDB database via JDBC, and opens the secure Login window.
 */
public class Main {

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            Theme.configureUIDefaults();
            if (ensureDatabaseReady()) {
                NotificationServer.ensureEmbeddedRunning();
                new LoginGUI().setVisible(true);
            }
        });
    }

    private static boolean ensureDatabaseReady() {
        while (true) {
            try {
                DatabaseInitializer.initialize();
                return true;
            } catch (SQLException ex) {
                int choice = showDatabaseTroubleshootingDialog(ex.getMessage());
                if (choice != 0) {
                    return false;
                }
                DatabaseConfig.load();
            }
        }
    }

    private static int showDatabaseTroubleshootingDialog(String errorMsg) {
        JDialog dialog = new JDialog((Frame) null, "Database Connection Required", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        final int[] action = {1}; // 0 = retry, 1 = exit

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.SURFACE);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.CRIMSON, 1),
                new EmptyBorder(22, 26, 20, 26)
        ));

        JLabel title = new JLabel("MySQL Database Connection Failed");
        title.setFont(Theme.FONT_H1);
        title.setForeground(Theme.CRIMSON);

        JTextArea info = new JTextArea(
                "CineVault could not connect to MySQL at "
                        + DatabaseConfig.getHost() + ":" + DatabaseConfig.getPort() + ".\n\n"
                        + "How to fix:\n"
                        + "1. Open XAMPP Control Panel (C:\\xampp\\xampp-control.exe)\n"
                        + "2. Click 'Start' next to MySQL\n"
                        + "3. Verify credentials in config/db.properties\n\n"
                        + "Technical detail:\n" + errorMsg
        );
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setFont(Theme.FONT_BODY);
        info.setForeground(Theme.TEXT_PRIMARY);
        info.setBackground(Theme.SURFACE);
        info.setColumns(44);
        info.setRows(9);

        Components.GlowButton exitBtn = new Components.GlowButton("Exit", Components.GlowButton.Variant.DANGER);
        exitBtn.setPreferredSize(new Dimension(110, 38));
        exitBtn.addActionListener(e -> {
            action[0] = 1;
            dialog.dispose();
        });

        Components.GlowButton retryBtn = new Components.GlowButton("Retry Connection", Components.GlowButton.Variant.SUCCESS);
        retryBtn.setPreferredSize(new Dimension(160, 38));
        retryBtn.addActionListener(e -> {
            action[0] = 0;
            dialog.dispose();
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.add(exitBtn);
        footer.add(retryBtn);

        root.add(title, BorderLayout.NORTH);
        root.add(info, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.getRootPane().setDefaultButton(retryBtn);
        dialog.setVisible(true);
        return action[0];
    }
}
