package ui;

import manager.Notification;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Floating dark-luxury toast popup anchored at the bottom-right of the active window
 * whenever a real-time socket notification arrives.
 */
public final class ToastNotification {

    private static volatile JWindow activeToast;

    private ToastNotification() {
    }

    public static void show(Window owner, Notification notification, Runnable onClick) {
        if (owner == null || !owner.isShowing() || notification == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!owner.isShowing()) {
                return;
            }
            if (activeToast != null) {
                activeToast.dispose();
                activeToast = null;
            }

            JWindow toast = new JWindow(owner);
            toast.setFocusableWindowState(false);

            Color accent = colorForType(notification.getType());

            JPanel container = new JPanel(new BorderLayout(12, 0));
            container.setBackground(Theme.CARD);
            container.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(accent, 2),
                    new EmptyBorder(12, 14, 12, 14)
            ));

            JPanel stripe = new JPanel();
            stripe.setBackground(accent);
            stripe.setPreferredSize(new Dimension(5, 0));

            JPanel content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

            JPanel topRow = new JPanel(new BorderLayout(8, 0));
            topRow.setOpaque(false);

            Components.BadgeLabel badge = new Components.BadgeLabel(
                    notification.getType(), Theme.withAlpha(accent, 45), accent);

            JLabel closeBtn = new JLabel(" \u2715 ");
            closeBtn.setFont(Theme.FONT_SMALL_BOLD);
            closeBtn.setForeground(Theme.TEXT_MUTED);
            closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            closeBtn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toast.dispose();
                }
            });

            topRow.add(badge, BorderLayout.WEST);
            topRow.add(closeBtn, BorderLayout.EAST);
            topRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel titleLbl = new JLabel(notification.getTitle());
            titleLbl.setFont(Theme.FONT_BODY_BOLD);
            titleLbl.setForeground(Theme.TEXT_PRIMARY);
            titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel msgLbl = new JLabel("<html><body style='width:290px'>"
                    + escapeHtml(notification.getMessage()) + "</body></html>");
            msgLbl.setFont(Theme.FONT_SMALL);
            msgLbl.setForeground(Theme.TEXT_MUTED);
            msgLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel hintLbl = new JLabel("Click to view in Notification Center");
            hintLbl.setFont(new Font("Segoe UI", Font.ITALIC, 10));
            hintLbl.setForeground(accent);
            hintLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            content.add(topRow);
            content.add(Box.createVerticalStrut(6));
            content.add(titleLbl);
            content.add(Box.createVerticalStrut(4));
            content.add(msgLbl);
            content.add(Box.createVerticalStrut(6));
            content.add(hintLbl);

            container.add(stripe, BorderLayout.WEST);
            container.add(content, BorderLayout.CENTER);
            container.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            container.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toast.dispose();
                    if (onClick != null) {
                        onClick.run();
                    }
                }
            });

            toast.setContentPane(container);
            toast.pack();

            Point loc = owner.getLocationOnScreen();
            int x = loc.x + owner.getWidth() - toast.getWidth() - 22;
            int y = loc.y + owner.getHeight() - toast.getHeight() - 22;
            toast.setLocation(Math.max(10, x), Math.max(10, y));

            activeToast = toast;
            toast.setVisible(true);

            try {
                Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored) {
            }

            Timer autoClose = new Timer(6000, e -> {
                toast.dispose();
                if (activeToast == toast) {
                    activeToast = null;
                }
            });
            autoClose.setRepeats(false);
            autoClose.start();
        });
    }

    public static Color colorForType(String type) {
        if ("BOOKED".equalsIgnoreCase(type)) return Theme.EMERALD;
        if ("CANCELLED".equalsIgnoreCase(type)) return Theme.CRIMSON;
        if ("RESCHEDULED".equalsIgnoreCase(type)) return Theme.GOLD;
        return Theme.SKY;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
