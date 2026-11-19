package ui;

import manager.Booking;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/**
 * Modal dialog displaying a high-luxury digital E-Ticket with PNG export.
 */
public class TicketDialog extends JDialog {

    public TicketDialog(Window owner, Booking booking) {
        super(owner, "CineVault E-Ticket - " + booking.getBookingCode(), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.BG_DARK);
        root.setBorder(new EmptyBorder(18, 20, 18, 20));

        Components.TicketPassPanel passPanel = new Components.TicketPassPanel(booking);

        Components.GlowButton saveBtn = new Components.GlowButton(
                "Save E-Ticket (PNG)", Components.GlowButton.Variant.GOLD);
        saveBtn.setPreferredSize(new Dimension(190, 40));

        Components.GlowButton closeBtn = new Components.GlowButton(
                "Close", Components.GlowButton.Variant.GHOST);
        closeBtn.setPreferredSize(new Dimension(110, 40));
        closeBtn.addActionListener(e -> dispose());

        saveBtn.addActionListener(e -> {
            File defaultDir = new File("tickets");
            if (!defaultDir.exists()) {
                defaultDir.mkdirs();
            }
            JFileChooser chooser = new JFileChooser(defaultDir);
            chooser.setSelectedFile(new File(defaultDir, booking.getBookingCode() + ".png"));
            int res = chooser.showSaveDialog(this);
            if (res == JFileChooser.APPROVE_OPTION) {
                File target = chooser.getSelectedFile();
                if (!target.getName().toLowerCase().endsWith(".png")) {
                    target = new File(target.getParentFile(), target.getName() + ".png");
                }
                try {
                    boolean ok = passPanel.exportToPng(target);
                    if (ok) {
                        Theme.showStyledMessage(this, "E-Ticket Exported",
                                "Saved digital E-Ticket pass to:\n" + target.getAbsolutePath(),
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        Theme.showStyledMessage(this, "Export Error",
                                "Could not write PNG image.", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    Theme.showStyledMessage(this, "Export Error",
                            "Failed to export E-Ticket: " + ex.getMessage(), JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.add(saveBtn);
        footer.add(closeBtn);

        root.add(passPanel, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(owner);
    }
}
