package ui;

import manager.Booking;
import manager.Movie;
import manager.SeatInfo;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Custom anti-aliased Graphics2D luxury Swing components for CineVault.
 */
public final class Components {

    private Components() {
    }

    public static class RoundedPanel extends JPanel {
        private int radius;
        private Color fillColor;
        private Color gradientEnd;
        private Color borderColor;

        public RoundedPanel(int radius, Color fillColor, Color borderColor) {
            this(radius, fillColor, null, borderColor);
        }

        public RoundedPanel(int radius, Color fillColor, Color gradientEnd, Color borderColor) {
            this.radius = radius;
            this.fillColor = fillColor;
            this.gradientEnd = gradientEnd;
            this.borderColor = borderColor;
            setOpaque(false);
        }

        public void setBorderColor(Color borderColor) {
            this.borderColor = borderColor;
            repaint();
        }

        public void setFillColor(Color fillColor) {
            this.fillColor = fillColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Shape round = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, radius, radius);
            if (gradientEnd != null) {
                g2.setPaint(new GradientPaint(0, 0, fillColor, 0, h, gradientEnd));
            } else {
                g2.setColor(fillColor);
            }
            g2.fill(round);
            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(round);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class GlowButton extends JButton {
        public enum Variant { PRIMARY, GOLD, SUCCESS, GHOST, DANGER, NAV }

        private Variant variant;
        private boolean hovered;
        private boolean pressed;
        private boolean navActive;
        private int radius = 10;

        public GlowButton(String text, Variant variant) {
            super(text);
            this.variant = variant;
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.FONT_BODY_BOLD);
            setBorder(new EmptyBorder(8, 16, 8, 16));
            if (variant == Variant.NAV) {
                setHorizontalAlignment(SwingConstants.LEFT);
            }
            syncForeground();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        hovered = true;
                        syncForeground();
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    pressed = false;
                    syncForeground();
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (isEnabled()) {
                        pressed = true;
                        syncForeground();
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    pressed = false;
                    syncForeground();
                    repaint();
                }
            });
        }

        public void setNavActive(boolean navActive) {
            this.navActive = navActive;
            syncForeground();
            repaint();
        }

        public void setVariant(Variant variant) {
            this.variant = variant;
            syncForeground();
            repaint();
        }

        @Override
        public void setEnabled(boolean b) {
            super.setEnabled(b);
            syncForeground();
            repaint();
        }

        public void setRadius(int radius) {
            this.radius = radius;
            repaint();
        }

        private Color resolveForegroundColor() {
            if (!isEnabled()) {
                return Theme.TEXT_MUTED;
            }
            switch (variant) {
                case PRIMARY:
                    return Theme.TEXT_PRIMARY;
                case GOLD:
                case SUCCESS:
                    return Theme.TEXT_DARK;
                case DANGER:
                    return hovered ? Theme.TEXT_PRIMARY : Theme.DANGER;
                case NAV:
                    return navActive ? Theme.GOLD : (hovered ? Theme.TEXT_PRIMARY : Theme.TEXT_MUTED);
                case GHOST:
                default:
                    return hovered ? Theme.GOLD : Theme.TEXT_PRIMARY;
            }
        }

        private void syncForeground() {
            Color fg = resolveForegroundColor();
            if (!fg.equals(getForeground())) {
                setForeground(fg);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            Color bg;
            Color border = null;

            switch (variant) {
                case PRIMARY:
                    bg = pressed ? new Color(0xB8, 0x07, 0x10) : (hovered ? Theme.CRIMSON_HOVER : Theme.CRIMSON);
                    break;
                case GOLD:
                    bg = pressed ? new Color(0xC9, 0x94, 0x00) : (hovered ? new Color(0xFF, 0xCA, 0x28) : Theme.GOLD);
                    break;
                case SUCCESS:
                    bg = pressed ? new Color(0x0A, 0x8F, 0x64) : (hovered ? new Color(0x16, 0xD1, 0x94) : Theme.EMERALD);
                    break;
                case DANGER:
                    bg = hovered ? Theme.DANGER : Theme.DANGER_SOFT;
                    border = Theme.DANGER;
                    break;
                case NAV:
                    bg = navActive ? Theme.CARD : (hovered ? Theme.withAlpha(Theme.CARD, 160) : new Color(0, 0, 0, 0));
                    border = navActive ? Theme.withAlpha(Theme.GOLD, 110) : null;
                    break;
                case GHOST:
                default:
                    bg = hovered ? Theme.CARD_HOVER : Theme.CARD;
                    border = hovered ? Theme.GOLD : Theme.BORDER;
                    break;
            }

            if (!isEnabled()) {
                bg = Theme.withAlpha(Theme.CARD, 140);
                border = Theme.BORDER;
            }

            Shape shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, radius, radius);
            if (bg.getAlpha() > 0) {
                g2.setColor(bg);
                g2.fill(shape);
            }
            if (border != null) {
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(shape);
            }
            if (variant == Variant.NAV && navActive) {
                g2.setColor(Theme.GOLD);
                g2.fillRoundRect(4, 8, 4, h - 16, 4, 4);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel() {
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 80;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    public static class DarkTextField extends JTextField {
        private final String placeholder;
        private boolean focused;

        public DarkTextField(String placeholder) {
            this.placeholder = placeholder;
            setOpaque(false);
            setForeground(Theme.TEXT_PRIMARY);
            setCaretColor(Theme.GOLD);
            setFont(Theme.FONT_BODY);
            setBorder(new EmptyBorder(9, 12, 9, 12));
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    focused = true;
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    focused = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape box = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 8, 8);
            g2.setColor(Theme.INPUT_BG);
            g2.fill(box);
            g2.setColor(focused ? Theme.GOLD : Theme.BORDER);
            g2.setStroke(new BasicStroke(focused ? 1.4f : 1f));
            g2.draw(box);
            g2.dispose();

            super.paintComponent(g);

            if (getText().isEmpty() && placeholder != null && !placeholder.isEmpty()) {
                Graphics2D gp = (Graphics2D) g.create();
                gp.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                gp.setFont(getFont());
                gp.setColor(Theme.withAlpha(Theme.TEXT_MUTED, 160));
                FontMetrics fm = gp.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                gp.drawString(placeholder, 13, y);
                gp.dispose();
            }
        }
    }

    public static class DarkPasswordField extends JPasswordField {
        private final String placeholder;
        private boolean focused;

        public DarkPasswordField(String placeholder) {
            this.placeholder = placeholder;
            setOpaque(false);
            setForeground(Theme.TEXT_PRIMARY);
            setCaretColor(Theme.GOLD);
            setFont(Theme.FONT_BODY);
            setBorder(new EmptyBorder(9, 12, 9, 12));
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    focused = true;
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    focused = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape box = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 8, 8);
            g2.setColor(Theme.INPUT_BG);
            g2.fill(box);
            g2.setColor(focused ? Theme.GOLD : Theme.BORDER);
            g2.setStroke(new BasicStroke(focused ? 1.4f : 1f));
            g2.draw(box);
            g2.dispose();

            super.paintComponent(g);

            if (getPassword().length == 0 && placeholder != null && !placeholder.isEmpty()) {
                Graphics2D gp = (Graphics2D) g.create();
                gp.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                gp.setFont(getFont());
                gp.setColor(Theme.withAlpha(Theme.TEXT_MUTED, 160));
                FontMetrics fm = gp.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                gp.drawString(placeholder, 13, y);
                gp.dispose();
            }
        }
    }

    public static class BadgeLabel extends JLabel {
        private Color pillBg;

        public BadgeLabel(String text, Color pillBg, Color fg) {
            super(text);
            this.pillBg = pillBg;
            setForeground(fg);
            setFont(Theme.FONT_SMALL_BOLD);
            setOpaque(false);
            setBorder(new EmptyBorder(3, 10, 3, 10));
        }

        public void setBadgeColors(Color bg, Color fg) {
            this.pillBg = bg;
            setForeground(fg);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(pillBg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class MoviePosterCard extends JPanel {
        private final Movie movie;
        private boolean selected;
        private boolean hovered;

        public MoviePosterCard(Movie movie, boolean selected, Runnable onClick) {
            this.movie = movie;
            this.selected = selected;
            setOpaque(false);
            setPreferredSize(new Dimension(196, 178));
            setMinimumSize(new Dimension(196, 178));
            setMaximumSize(new Dimension(196, 178));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(movie.getTitle() + " (" + movie.getDurationMin() + " min) - " + movie.getSynopsis());

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onClick != null) {
                        onClick.run();
                    }
                }
            });
        }

        public Movie getMovie() {
            return movie;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            Color accent = Theme.parseHexColor(movie.getAccentColor(), Theme.CRIMSON);

            Shape cardShape = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, 14, 14);
            g2.setColor(hovered ? Theme.CARD_HOVER : Theme.CARD);
            g2.fill(cardShape);

            // Poster banner top area
            Shape oldClip = g2.getClip();
            g2.setClip(new RoundRectangle2D.Float(1f, 1f, w - 2f, 102f, 14, 14));
            g2.setPaint(new GradientPaint(0, 0, accent, w, 102, Theme.SURFACE));
            g2.fillRect(0, 0, w, 102);

            // Subtle cinema film perforations on banner
            g2.setColor(new Color(255, 255, 255, 26));
            for (int x = 10; x < w - 10; x += 18) {
                g2.fillRoundRect(x, 8, 9, 5, 2, 2);
            }
            g2.setColor(new Color(255, 255, 255, 22));
            g2.fillOval(w - 58, 22, 74, 74);

            // Monogram watermark in poster banner
            String mono = movie.getTitle().isEmpty() ? "CV"
                    : movie.getTitle().substring(0, Math.min(2, movie.getTitle().length())).toUpperCase(Locale.US);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 34));
            g2.setColor(new Color(255, 255, 255, 45));
            g2.drawString(mono, 14, 72);

            // IMDb rating chip
            String ratingTxt = "\u2605 " + String.format(Locale.US, "%.1f", movie.getImdbRating());
            g2.setColor(new Color(11, 15, 25, 210));
            g2.fillRoundRect(w - 62, 66, 52, 22, 10, 10);
            g2.setFont(Theme.FONT_SMALL_BOLD);
            g2.setColor(Theme.GOLD);
            g2.drawString(ratingTxt, w - 54, 81);

            // Age rating pill
            g2.setColor(new Color(11, 15, 25, 190));
            g2.fillRoundRect(10, 66, 48, 22, 10, 10);
            g2.setColor(Theme.TEXT_PRIMARY);
            g2.drawString(movie.getAgeRating(), 17, 81);
            g2.setClip(oldClip);

            // Title & meta text
            g2.setFont(Theme.FONT_H3);
            g2.setColor(selected ? Theme.GOLD : Theme.TEXT_PRIMARY);
            String title = fitText(movie.getTitle(), g2.getFontMetrics(), w - 22);
            g2.drawString(title, 12, 124);

            g2.setFont(Theme.FONT_SMALL);
            g2.setColor(Theme.TEXT_MUTED);
            g2.drawString(movie.getGenre() + "  \u2022  " + movie.getDurationMin() + " min", 12, 144);

            g2.setColor(accent);
            g2.fillRoundRect(12, 156, 36, 4, 4, 4);
            g2.setFont(Theme.FONT_SMALL_BOLD);
            g2.setColor(Theme.TEXT_MUTED);
            g2.drawString(movie.getLanguage(), 56, 161);

            // Border highlight
            g2.setColor(selected ? Theme.GOLD : (hovered ? Theme.SKY : Theme.BORDER));
            g2.setStroke(new BasicStroke(selected ? 2.2f : 1f));
            g2.draw(cardShape);
            g2.dispose();
        }

        private static String fitText(String text, FontMetrics fm, int maxWidth) {
            if (fm.stringWidth(text) <= maxWidth) return text;
            String s = text;
            while (s.length() > 3 && fm.stringWidth(s + "...") > maxWidth) {
                s = s.substring(0, s.length() - 1);
            }
            return s + "...";
        }
    }

    public static class CurvedScreenPanel extends JPanel {
        public CurvedScreenPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(560, 56));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int pad = Math.max(40, w / 8);
            int sw = w - (pad * 2);

            // Ambient spotlight cone below screen
            Path2D cone = new Path2D.Float();
            cone.moveTo(pad, 24);
            cone.quadTo(w / 2.0, 6, w - pad, 24);
            cone.lineTo(w - pad + 26, 54);
            cone.lineTo(pad - 26, 54);
            cone.closePath();
            g2.setPaint(new GradientPaint(0, 14, Theme.withAlpha(Theme.SKY, 55),
                    0, 54, Theme.withAlpha(Theme.SKY, 0)));
            g2.fill(cone);

            // Glowing curved screen line
            g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Theme.withAlpha(Theme.SKY, 90));
            g2.draw(new Arc2D.Float(pad, 12, sw, 36, 18, 144, Arc2D.OPEN));

            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Theme.SKY);
            g2.draw(new Arc2D.Float(pad, 10, sw, 36, 18, 144, Arc2D.OPEN));

            g2.setFont(Theme.FONT_SMALL_BOLD);
            g2.setColor(Theme.TEXT_MUTED);
            String label = "ALL EYES THIS WAY  \u2022  CINEMA SCREEN";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, (w - fm.stringWidth(label)) / 2, 42);
            g2.dispose();
        }
    }

    public static class SeatButton extends JButton {
        private final SeatInfo seat;
        private final int currentUserId;
        private boolean selectedInCart;
        private boolean hovered;

        public SeatButton(SeatInfo seat, int currentUserId) {
            this.seat = seat;
            this.currentUserId = currentUserId;
            setOpaque(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setPreferredSize(new Dimension(54, 44));
            setMinimumSize(new Dimension(48, 40));

            boolean available = !seat.isBooked();
            setEnabled(available);
            if (available) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText(String.format(Locale.US, "%s (%s) - BDT %.0f",
                        seat.getLabel(), seat.getTier(), seat.getPrice()));
            } else if (seat.getBookedByUserId() == currentUserId) {
                setToolTipText(seat.getLabel() + " - Your Booked Seat (" + seat.getTier() + ")");
            } else {
                setToolTipText(seat.getLabel() + " - Already Booked");
            }

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        hovered = true;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        public SeatInfo getSeat() {
            return seat;
        }

        public boolean isSelectedInCart() {
            return selectedInCart;
        }

        public void setSelectedInCart(boolean selectedInCart) {
            this.selectedInCart = selectedInCart;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            Color fill;
            Color stroke;
            Color textColor;

            if (seat.isBooked()) {
                if (seat.getBookedByUserId() == currentUserId) {
                    fill = Theme.PURPLE_SOFT;
                    stroke = Theme.PURPLE;
                    textColor = Theme.TEXT_PRIMARY;
                } else {
                    fill = Theme.DANGER_SOFT;
                    stroke = Theme.withAlpha(Theme.DANGER, 140);
                    textColor = Theme.withAlpha(Theme.DANGER, 210);
                }
            } else if (selectedInCart) {
                fill = Theme.SKY;
                stroke = Theme.TEXT_PRIMARY;
                textColor = Theme.TEXT_DARK;
            } else {
                String tier = seat.getTier();
                if ("VIP".equals(tier)) {
                    fill = hovered ? new Color(0x5E, 0x45, 0x10) : Theme.GOLD_SOFT;
                    stroke = Theme.GOLD;
                    textColor = Theme.GOLD;
                } else if ("GOLD".equals(tier)) {
                    fill = hovered ? new Color(0x1E, 0x47, 0x6B) : Theme.SKY_SOFT;
                    stroke = Theme.SKY;
                    textColor = Theme.TEXT_PRIMARY;
                } else {
                    fill = hovered ? new Color(0x13, 0x52, 0x40) : Theme.EMERALD_SOFT;
                    stroke = Theme.EMERALD;
                    textColor = Theme.TEXT_PRIMARY;
                }
            }

            // Armchair silhouette
            g2.setColor(fill);
            g2.fillRoundRect(5, 3, w - 10, h - 10, 10, 10);
            g2.fillRoundRect(2, h - 14, w - 4, 10, 6, 6);

            g2.setColor(stroke);
            g2.setStroke(new BasicStroke(selectedInCart ? 1.8f : 1.1f));
            g2.drawRoundRect(5, 3, w - 10, h - 10, 10, 10);
            g2.drawRoundRect(2, h - 14, w - 4, 10, 6, 6);

            g2.setFont(Theme.FONT_SMALL_BOLD);
            g2.setColor(textColor);
            String label = (seat.isBooked() && seat.getBookedByUserId() != currentUserId)
                    ? "\u2715" : seat.getLabel();
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(label)) / 2;
            int ty = ((h - 6) - fm.getHeight()) / 2 + fm.getAscent() + 2;
            g2.drawString(label, tx, ty);

            g2.dispose();
        }
    }

    public static class TicketPassPanel extends JPanel {
        private final Booking booking;
        private final boolean[][] qrMatrix;

        public TicketPassPanel(Booking booking) {
            this.booking = booking;
            this.qrMatrix = buildQrMatrix(booking.getBookingCode() + "|" + booking.getTicketSignature());
            setOpaque(false);
            setPreferredSize(new Dimension(690, 340));
            setMinimumSize(new Dimension(690, 340));
        }

        public boolean exportToPng(File file) throws IOException {
            int w = Math.max(getWidth(), getPreferredSize().width);
            int h = Math.max(getHeight(), getPreferredSize().height);
            setSize(w, h);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setColor(Theme.BG_DARK);
            g2.fillRect(0, 0, w, h);
            paint(g2);
            g2.dispose();
            return ImageIO.write(img, "png", file);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int splitX = (int) (w * 0.68);

            // Ticket outer card
            Shape card = new RoundRectangle2D.Float(8, 8, w - 16, h - 16, 20, 20);
            g2.setPaint(new GradientPaint(0, 0, Theme.CARD, w, h, Theme.SURFACE));
            g2.fill(card);

            // Header bar inside ticket
            Shape oldClip = g2.getClip();
            g2.setClip(card);
            g2.setPaint(new GradientPaint(0, 0, Theme.CRIMSON, splitX, 60, new Color(0x88, 0x06, 0x10)));
            g2.fillRect(8, 8, w - 16, 56);
            g2.setColor(Theme.GOLD);
            g2.fillRect(8, 62, w - 16, 2);
            g2.setClip(oldClip);

            g2.setColor(Theme.GOLD);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(card);

            // Header text
            g2.setFont(Theme.FONT_H2);
            g2.setColor(Theme.TEXT_PRIMARY);
            g2.drawString("CINEVAULT  LUXURY  E-TICKET", 26, 42);

            String status = booking.isConfirmed() ? "CONFIRMED" : "CANCELLED";
            Color statusColor = booking.isConfirmed() ? Theme.EMERALD : Theme.DANGER;
            g2.setColor(Theme.BG_DARK);
            g2.fillRoundRect(w - 134, 22, 106, 26, 12, 12);
            g2.setColor(statusColor);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(w - 134, 22, 106, 26, 12, 12);
            g2.setFont(Theme.FONT_SMALL_BOLD);
            FontMetrics fmS = g2.getFontMetrics();
            g2.drawString(status, w - 134 + (106 - fmS.stringWidth(status)) / 2, 39);

            // Left details
            g2.setFont(Theme.FONT_H1);
            g2.setColor(Theme.GOLD);
            g2.drawString(fit(booking.getMovieTitle(), g2.getFontMetrics(), splitX - 44), 26, 98);

            g2.setFont(Theme.FONT_BODY);
            g2.setColor(Theme.TEXT_MUTED);
            g2.drawString(booking.getHallName() + "   \u2022   " + booking.getShowDate()
                    + " at " + booking.getShowTime(), 26, 122);

            drawField(g2, "PASSENGER", booking.getCustomerFullName() + " (@" + booking.getUsername() + ")", 26, 155);
            drawField(g2, "SEATS", booking.getSeatLabelsDisplay(), 26, 196);
            drawField(g2, "CONCESSIONS", fit(booking.getSnacksSummary(), g2.getFontMetrics(), splitX - 48), 26, 237);

            String totals = String.format(Locale.US,
                    "Subtotal: BDT %.0f  |  Saved: BDT %.0f  |  Snacks: BDT %.0f",
                    booking.getSubtotal(), booking.getDiscountAmount(), booking.getSnacksAmount());
            g2.setFont(Theme.FONT_SMALL);
            g2.setColor(Theme.TEXT_MUTED);
            g2.drawString(totals, 26, 278);

            g2.setFont(Theme.FONT_H2);
            g2.setColor(Theme.EMERALD);
            g2.drawString(String.format(Locale.US, "PAID: BDT %.2f (%s)",
                    booking.getTotalAmount(), booking.getPaymentMethod()), 26, 308);

            // Perforation dashed line & notches
            Stroke dashed = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[]{6f, 6f}, 0f);
            g2.setStroke(dashed);
            g2.setColor(Theme.BORDER);
            g2.drawLine(splitX, 72, splitX, h - 18);

            g2.setColor(Theme.BG_DARK);
            g2.fillOval(splitX - 11, -3, 22, 22);
            g2.fillOval(splitX - 11, h - 19, 22, 22);

            // Right QR stub
            int qrSize = 136;
            int qrX = splitX + ((w - splitX - qrSize) / 2);
            int qrY = 82;

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(qrX - 6, qrY - 6, qrSize + 12, qrSize + 12, 10, 10);

            int cell = qrSize / qrMatrix.length;
            int offset = (qrSize - cell * qrMatrix.length) / 2;
            g2.setColor(Theme.BG_DARK);
            for (int r = 0; r < qrMatrix.length; r++) {
                for (int c = 0; c < qrMatrix[r].length; c++) {
                    if (qrMatrix[r][c]) {
                        g2.fillRect(qrX + offset + c * cell, qrY + offset + r * cell, cell, cell);
                    }
                }
            }

            g2.setFont(Theme.FONT_MONO_BOLD);
            g2.setColor(Theme.GOLD);
            String code = booking.getBookingCode();
            FontMetrics fmC = g2.getFontMetrics();
            g2.drawString(code, splitX + (w - splitX - fmC.stringWidth(code)) / 2, qrY + qrSize + 30);

            String sig = booking.getTicketSignature();
            String sigShort = "HMAC: " + (sig.length() > 14 ? sig.substring(0, 14) + "..." : sig);
            g2.setFont(Theme.FONT_MONO);
            g2.setColor(Theme.SKY);
            FontMetrics fmSig = g2.getFontMetrics();
            g2.drawString(sigShort, splitX + (w - splitX - fmSig.stringWidth(sigShort)) / 2, qrY + qrSize + 52);

            g2.setFont(Theme.FONT_SMALL);
            g2.setColor(Theme.TEXT_MUTED);
            String hint = "Scan or verify code at Gate";
            FontMetrics fmH = g2.getFontMetrics();
            g2.drawString(hint, splitX + (w - splitX - fmH.stringWidth(hint)) / 2, qrY + qrSize + 74);

            g2.dispose();
        }

        private void drawField(Graphics2D g2, String label, String value, int x, int y) {
            g2.setFont(Theme.FONT_SMALL_BOLD);
            g2.setColor(Theme.TEXT_MUTED);
            g2.drawString(label, x, y);
            g2.setFont(Theme.FONT_BODY_BOLD);
            g2.setColor(Theme.TEXT_PRIMARY);
            g2.drawString(value, x, y + 17);
        }

        private static String fit(String s, FontMetrics fm, int maxW) {
            if (s == null) return "";
            if (fm.stringWidth(s) <= maxW) return s;
            String t = s;
            while (t.length() > 3 && fm.stringWidth(t + "...") > maxW) {
                t = t.substring(0, t.length() - 1);
            }
            return t + "...";
        }

        private static boolean[][] buildQrMatrix(String seed) {
            int n = 21;
            boolean[][] m = new boolean[n][n];
            drawFinder(m, 0, 0);
            drawFinder(m, 0, n - 7);
            drawFinder(m, n - 7, 0);

            byte[] hash;
            try {
                hash = MessageDigest.getInstance("SHA-256")
                        .digest(seed.getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                hash = seed.getBytes(StandardCharsets.UTF_8);
            }
            int bitIdx = 0;
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    if (inFinder(r, c, n)) continue;
                    byte b = hash[(bitIdx / 8) % hash.length];
                    m[r][c] = ((b >> (bitIdx % 8)) & 1) == 1 || ((r + c) % 5 == 0);
                    bitIdx++;
                }
            }
            return m;
        }

        private static boolean inFinder(int r, int c, int n) {
            return (r < 8 && c < 8) || (r < 8 && c >= n - 8) || (r >= n - 8 && c < 8);
        }

        private static void drawFinder(boolean[][] m, int sr, int sc) {
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < 7; c++) {
                    boolean border = r == 0 || r == 6 || c == 0 || c == 6;
                    boolean core = r >= 2 && r <= 4 && c >= 2 && c <= 4;
                    m[sr + r][sc + c] = border || core;
                }
            }
        }
    }

    public static class RevenueBarChartPanel extends JPanel {
        private final Map<String, Double> data = new LinkedHashMap<>();

        public RevenueBarChartPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(500, 260));
        }

        public void setData(Map<String, Double> revenueMap) {
            data.clear();
            if (revenueMap != null) {
                int count = 0;
                for (Map.Entry<String, Double> e : revenueMap.entrySet()) {
                    data.put(e.getKey(), e.getValue());
                    if (++count >= 8) break;
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (data.isEmpty()) {
                g2.setFont(Theme.FONT_BODY);
                g2.setColor(Theme.TEXT_MUTED);
                g2.drawString("No revenue data recorded yet.", 20, h / 2);
                g2.dispose();
                return;
            }

            double max = 100.0;
            for (double v : data.values()) {
                if (v > max) max = v;
            }

            int leftLabelW = 165;
            int rightValW = 95;
            int chartW = Math.max(80, w - leftLabelW - rightValW - 20);
            int rows = data.size();
            int rowH = Math.max(22, (h - 24) / Math.max(1, rows));

            // Gridlines
            g2.setColor(Theme.withAlpha(Theme.BORDER, 130));
            for (int i = 0; i <= 4; i++) {
                int gx = leftLabelW + (chartW * i) / 4;
                g2.drawLine(gx, 8, gx, h - 14);
            }

            int idx = 0;
            for (Map.Entry<String, Double> entry : data.entrySet()) {
                int y = 12 + idx * rowH;
                int barH = Math.min(20, rowH - 8);
                double val = entry.getValue();
                int barW = val <= 0 ? 4 : (int) Math.max(6, (val / max) * chartW);

                g2.setFont(Theme.FONT_SMALL_BOLD);
                g2.setColor(Theme.TEXT_PRIMARY);
                String name = entry.getKey();
                if (name.length() > 22) name = name.substring(0, 19) + "...";
                g2.drawString(name, 8, y + barH - 4);

                g2.setPaint(new GradientPaint(leftLabelW, y, Theme.GOLD,
                        leftLabelW + barW, y, Theme.CRIMSON));
                g2.fillRoundRect(leftLabelW, y, barW, barH, 8, 8);

                g2.setFont(Theme.FONT_MONO);
                g2.setColor(val > 0 ? Theme.GOLD : Theme.TEXT_MUTED);
                g2.drawString(String.format(Locale.US, "BDT %.0f", val), leftLabelW + barW + 10, y + barH - 4);
                idx++;
            }
            g2.dispose();
        }
    }
}
