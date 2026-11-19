package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Central dark-mode luxury color palette, typography, and Swing styling helpers.
 */
public final class Theme {

    public static final Color BG_DARK = new Color(0x0B, 0x0F, 0x19);
    public static final Color SURFACE = new Color(0x13, 0x1A, 0x2A);
    public static final Color CARD = new Color(0x1A, 0x23, 0x38);
    public static final Color CARD_HOVER = new Color(0x22, 0x2E, 0x48);
    public static final Color INPUT_BG = new Color(0x0F, 0x15, 0x24);
    public static final Color BORDER = new Color(0x2A, 0x36, 0x52);

    public static final Color GOLD = new Color(0xF5, 0xB7, 0x00);
    public static final Color GOLD_SOFT = new Color(0x47, 0x36, 0x0B);
    public static final Color CRIMSON = new Color(0xE5, 0x09, 0x14);
    public static final Color CRIMSON_HOVER = new Color(0xFF, 0x1F, 0x2B);
    public static final Color EMERALD = new Color(0x10, 0xB9, 0x81);
    public static final Color EMERALD_SOFT = new Color(0x0D, 0x3B, 0x2E);
    public static final Color SKY = new Color(0x38, 0xBD, 0xF8);
    public static final Color SKY_SOFT = new Color(0x14, 0x33, 0x4D);
    public static final Color PURPLE = new Color(0xA8, 0x55, 0xF7);
    public static final Color PURPLE_SOFT = new Color(0x31, 0x1B, 0x4E);
    public static final Color DANGER = new Color(0xEF, 0x44, 0x44);
    public static final Color DANGER_SOFT = new Color(0x45, 0x1A, 0x22);
    public static final Color WARN = new Color(0xF5, 0x9E, 0x0B);

    public static final Color TEXT_PRIMARY = new Color(0xF8, 0xFA, 0xFC);
    public static final Color TEXT_MUTED = new Color(0x94, 0xA3, 0xB8);
    public static final Color TEXT_DARK = new Color(0x0B, 0x0F, 0x19);

    public static final Font FONT_HERO = new Font("Segoe UI", Font.BOLD, 28);
    public static final Font FONT_H1 = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font FONT_H2 = new Font("Segoe UI", Font.BOLD, 17);
    public static final Font FONT_H3 = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_SMALL_BOLD = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);
    public static final Font FONT_MONO_BOLD = new Font("Consolas", Font.BOLD, 13);

    private Theme() {
    }

    public static void configureUIDefaults() {
        UIManager.put("Panel.background", BG_DARK);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
        UIManager.put("ToolTip.background", CARD);
        UIManager.put("ToolTip.foreground", TEXT_PRIMARY);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(GOLD, 1));
        UIManager.put("ToolTip.font", FONT_SMALL);
        UIManager.put("ComboBox.background", INPUT_BG);
        UIManager.put("ComboBox.foreground", TEXT_PRIMARY);
        UIManager.put("ComboBox.selectionBackground", CARD_HOVER);
        UIManager.put("ComboBox.selectionForeground", GOLD);
        UIManager.put("ScrollPane.background", BG_DARK);
        UIManager.put("Viewport.background", BG_DARK);
    }

    public static Color parseHexColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        try {
            String clean = hex.trim();
            if (!clean.startsWith("#")) {
                clean = "#" + clean;
            }
            return Color.decode(clean);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static void styleTable(JTable table) {
        table.setBackground(SURFACE);
        table.setForeground(TEXT_PRIMARY);
        table.setFont(FONT_BODY);
        table.setRowHeight(36);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setGridColor(BORDER);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setSelectionBackground(new Color(0x1E, 0x3A, 0x5F));
        table.setSelectionForeground(GOLD);
        table.setIntercellSpacing(new Dimension(8, 0));
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setBackground(CARD);
        header.setForeground(GOLD);
        header.setFont(FONT_SMALL_BOLD);
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 38));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object val, boolean selected,
                                                           boolean focused, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(tbl, val, selected, focused, row, col);
                lbl.setBorder(new EmptyBorder(0, 10, 0, 10));
                if (selected) {
                    lbl.setBackground(new Color(0x1E, 0x3A, 0x5F));
                    lbl.setForeground(GOLD);
                    lbl.setFont(FONT_BODY_BOLD);
                } else {
                    lbl.setBackground(row % 2 == 0 ? SURFACE : new Color(0x16, 0x1F, 0x33));
                    lbl.setForeground(TEXT_PRIMARY);
                    lbl.setFont(FONT_BODY);
                }
                return lbl;
            }
        };
        table.setDefaultRenderer(Object.class, cellRenderer);
    }

    public static void styleScrollPane(JScrollPane sp) {
        sp.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        sp.getViewport().setBackground(BG_DARK);
        sp.setBackground(BG_DARK);
        sp.getVerticalScrollBar().setUnitIncrement(20);
        sp.getHorizontalScrollBar().setUnitIncrement(20);
        styleScrollBar(sp.getVerticalScrollBar());
        styleScrollBar(sp.getHorizontalScrollBar());
    }

    public static void styleScrollBar(JScrollBar sb) {
        sb.setBackground(BG_DARK);
        sb.setPreferredSize(new Dimension(10, 10));
        sb.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = new Color(0x3B, 0x4C, 0x70);
                this.thumbDarkShadowColor = new Color(0x2A, 0x36, 0x52);
                this.thumbHighlightColor = GOLD;
                this.trackColor = BG_DARK;
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return emptyButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return emptyButton();
            }

            private JButton emptyButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }
        });
    }

    public static <T> void styleComboBox(JComboBox<T> combo) {
        combo.setBackground(INPUT_BG);
        combo.setForeground(TEXT_PRIMARY);
        combo.setFont(FONT_BODY);
        combo.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                lbl.setBorder(new EmptyBorder(6, 10, 6, 10));
                lbl.setBackground(isSelected ? CARD_HOVER : INPUT_BG);
                lbl.setForeground(isSelected ? GOLD : TEXT_PRIMARY);
                return lbl;
            }
        });
    }

    public static void showStyledMessage(Component parent, String title, String message, int messageType) {
        Window owner = parent instanceof Window ? (Window) parent
                : (parent != null ? SwingUtilities.getWindowAncestor(parent) : null);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        Color accent = messageType == JOptionPane.ERROR_MESSAGE ? DANGER
                : (messageType == JOptionPane.WARNING_MESSAGE ? WARN : EMERALD);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(SURFACE);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent, 1),
                new EmptyBorder(20, 24, 18, 24)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(FONT_H2);
        titleLbl.setForeground(accent);

        JTextArea body = new JTextArea(message);
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(FONT_BODY);
        body.setForeground(TEXT_PRIMARY);
        body.setBackground(SURFACE);
        body.setBorder(null);
        body.setColumns(34);
        body.setRows(Math.min(7, Math.max(2, (message.length() / 42) + 1)));

        Components.GlowButton okBtn = new Components.GlowButton("OK", Components.GlowButton.Variant.GOLD);
        okBtn.setPreferredSize(new Dimension(110, 36));
        okBtn.addActionListener(e -> dialog.dispose());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(okBtn);

        root.add(titleLbl, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.getRootPane().setDefaultButton(okBtn);
        dialog.setVisible(true);
    }

    public static boolean confirmStyled(Component parent, String title, String message) {
        Window owner = parent instanceof Window ? (Window) parent
                : (parent != null ? SwingUtilities.getWindowAncestor(parent) : null);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        final boolean[] confirmed = {false};

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(SURFACE);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD, 1),
                new EmptyBorder(20, 24, 18, 24)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(FONT_H2);
        titleLbl.setForeground(GOLD);

        JTextArea body = new JTextArea(message);
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(FONT_BODY);
        body.setForeground(TEXT_PRIMARY);
        body.setBackground(SURFACE);
        body.setBorder(null);
        body.setColumns(36);
        body.setRows(Math.min(7, Math.max(2, (message.length() / 44) + 1)));

        Components.GlowButton cancelBtn = new Components.GlowButton("Cancel", Components.GlowButton.Variant.GHOST);
        cancelBtn.setPreferredSize(new Dimension(100, 36));
        cancelBtn.addActionListener(e -> dialog.dispose());

        Components.GlowButton yesBtn = new Components.GlowButton("Confirm", Components.GlowButton.Variant.PRIMARY);
        yesBtn.setPreferredSize(new Dimension(115, 36));
        yesBtn.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.add(cancelBtn);
        footer.add(yesBtn);

        root.add(titleLbl, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.getRootPane().setDefaultButton(yesBtn);
        dialog.setVisible(true);
        return confirmed[0];
    }
}
