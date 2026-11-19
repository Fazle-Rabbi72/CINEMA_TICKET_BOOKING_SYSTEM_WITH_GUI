package ui;

import auth.AuthManager;
import auth.LoginGUI;
import auth.User;
import dao.BookingDAO;
import dao.CouponDAO;
import dao.MovieDAO;
import dao.NotificationDAO;
import dao.ShowDAO;
import dao.SnackDAO;
import db.DatabaseConfig;
import manager.Booking;
import manager.Coupon;
import manager.Movie;
import manager.Notification;
import manager.SeatInfo;
import manager.Show;
import manager.SnackItem;
import manager.Ticket;
import net.NotificationClient;
import net.NotificationService;
import security.SessionManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Main CineVault desktop interface featuring a curved theater seat selector,
 * snacks & promo cart, digital E-Tickets, VIP Gold upgrades, and Admin Studio.
 */
public class CinemaGUI extends JFrame {

    private User user;

    private final MovieDAO movieDAO = new MovieDAO();
    private final ShowDAO showDAO = new ShowDAO();
    private final SnackDAO snackDAO = new SnackDAO();
    private final CouponDAO couponDAO = new CouponDAO();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();

    private final CardLayout mainCards = new CardLayout();
    private final JPanel mainContainer = new JPanel(mainCards);

    private Components.GlowButton navNowShowing;
    private Components.GlowButton navMyTickets;
    private Components.GlowButton navNotifications;
    private Components.GlowButton navVip;
    private Components.GlowButton navAdmin;

    private Components.BadgeLabel headerTierBadge;
    private Components.BadgeLabel socketStatusBadge;
    private Components.GlowButton notifBellBtn;
    private Components.DarkTextField searchField;
    private JComboBox<String> genreCombo;

    // Now Showing state
    private List<Movie> movies = new ArrayList<>();
    private Movie selectedMovie;
    private List<Show> movieShows = new ArrayList<>();
    private Show currentShow;
    private SeatInfo[][] currentSeatMap;
    private final List<SeatInfo> selectedSeats = new ArrayList<>();
    private final Map<SnackItem, Integer> selectedSnacks = new LinkedHashMap<>();
    private Coupon appliedCoupon;

    private JPanel movieStripPanel;
    private JLabel movieMetaLabel;
    private JPanel showtimeBar;
    private JPanel seatGridContainer;
    private JLabel selectedSeatsLabel;
    private JPanel snacksListPanel;
    private Components.DarkTextField couponField;
    private JLabel couponStatusLabel;
    private JLabel seatSubtotalVal;
    private JLabel memberDiscVal;
    private JLabel couponDiscVal;
    private JLabel snacksTotalVal;
    private JLabel grandTotalVal;
    private JComboBox<String> paymentCombo;

    // My Tickets state
    private DefaultTableModel ticketsModel;
    private JTable ticketsTable;
    private List<Booking> userBookings = new ArrayList<>();
    private Components.BadgeLabel myTicketsCountBadge;
    private Components.DarkTextField ticketCodeInput;
    private Components.GlowButton myTicketViewBtn;
    private Components.GlowButton myTicketCancelBtn;
    private JPanel ticketPreviewContainer;

    // VIP tab components
    private JLabel vipStatusTitle;
    private JLabel vipStatusDesc;
    private Components.GlowButton vipUpgradeBtn;

    // Admin panel
    private AdminPanel adminPanel;

    // Real-time TCP Socket notifications state
    private NotificationClient notificationClient;
    private JPanel notificationsListPanel;
    private JLabel notifHeaderSummary;

    public CinemaGUI(User user) {
        this.user = user;
        if (!SessionManager.isLoggedIn()
                || SessionManager.getCurrentUser().getId() != user.getId()) {
            SessionManager.login(user);
        }
        Theme.configureUIDefaults();

        setTitle("CineVault  |  " + user.getFullName() + " (" + user.toCustomer().getTierName() + ")");
        setSize(1340, 830);
        setMinimumSize(new Dimension(1180, 740));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG_DARK);

        root.add(buildTopHeader(), BorderLayout.NORTH);
        root.add(buildSideNav(), BorderLayout.WEST);

        mainContainer.setOpaque(false);
        mainContainer.add(buildNowShowingView(), "NOW");
        mainContainer.add(buildMyTicketsView(), "TICKETS");
        mainContainer.add(buildNotificationsView(), "NOTIFICATIONS");
        mainContainer.add(buildVipView(), "VIP");
        if (user.isAdmin()) {
            adminPanel = new AdminPanel(user, this::reloadCatalogFromAdmin);
            mainContainer.add(adminPanel, "ADMIN");
        }
        root.add(mainContainer, BorderLayout.CENTER);

        setContentPane(root);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownSocketClient();
            }
        });

        loadSnacks();
        loadMovies("", "All Genres");
        loadMyTickets();
        refreshNotificationsView();
        refreshVipCard();
        switchView("NOW");
        startNotificationClient();
    }

    @Override
    public void dispose() {
        shutdownSocketClient();
        super.dispose();
    }

    private JPanel buildTopHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(Theme.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                new EmptyBorder(12, 20, 12, 20)
        ));

        JPanel brandBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brandBox.setOpaque(false);
        JLabel logo = new JLabel("CINEVAULT");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        logo.setForeground(Theme.GOLD);
        JLabel sub = new JLabel("IMAX & VIP LOUNGE");
        sub.setFont(Theme.FONT_SMALL_BOLD);
        sub.setForeground(Theme.TEXT_MUTED);
        brandBox.add(logo);
        brandBox.add(sub);

        JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        searchBox.setOpaque(false);
        searchField = new Components.DarkTextField("Search movies by title or keyword...");
        searchField.setPreferredSize(new Dimension(280, 36));
        genreCombo = new JComboBox<>(new String[]{"All Genres", "Action", "Sci-Fi", "Thriller", "Drama", "Adventure", "Comedy", "Horror"});
        Theme.styleComboBox(genreCombo);
        genreCombo.setPreferredSize(new Dimension(130, 36));
        Components.GlowButton searchBtn = new Components.GlowButton("Search", Components.GlowButton.Variant.GHOST);
        searchBtn.setPreferredSize(new Dimension(92, 36));

        Runnable doSearch = () -> {
            switchView("NOW");
            loadMovies(searchField.getText(), (String) genreCombo.getSelectedItem());
        };
        searchBtn.addActionListener(e -> doSearch.run());
        searchField.addActionListener(e -> doSearch.run());
        genreCombo.addActionListener(e -> doSearch.run());

        searchBox.add(searchField);
        searchBox.add(genreCombo);
        searchBox.add(searchBtn);

        JPanel profileBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        profileBox.setOpaque(false);

        socketStatusBadge = new Components.BadgeLabel(
                "\u25CF SOCKET", Theme.CARD, Theme.TEXT_MUTED);
        socketStatusBadge.setToolTipText("Real-time TCP Socket connection status");

        notifBellBtn = new Components.GlowButton("Alerts", Components.GlowButton.Variant.GHOST);
        notifBellBtn.setFont(Theme.FONT_SMALL_BOLD);
        notifBellBtn.setBorder(new EmptyBorder(7, 12, 7, 12));
        notifBellBtn.addActionListener(e -> openNotificationsView());

        JLabel nameLbl = new JLabel(user.getFullName() + " (@" + user.getUsername() + ")");
        nameLbl.setFont(Theme.FONT_BODY_BOLD);
        nameLbl.setForeground(Theme.TEXT_PRIMARY);

        headerTierBadge = new Components.BadgeLabel(
                user.isPremium() ? "VIP GOLD (20% OFF)" : "STANDARD",
                user.isPremium() ? Theme.GOLD_SOFT : Theme.CARD,
                user.isPremium() ? Theme.GOLD : Theme.TEXT_MUTED
        );

        profileBox.add(socketStatusBadge);
        profileBox.add(notifBellBtn);
        profileBox.add(nameLbl);
        profileBox.add(headerTierBadge);

        if (user.isAdmin()) {
            profileBox.add(new Components.BadgeLabel("ADMIN", Theme.DANGER_SOFT, Theme.CRIMSON));
        }

        Components.GlowButton logoutBtn = new Components.GlowButton("Logout", Components.GlowButton.Variant.DANGER);
        logoutBtn.setFont(Theme.FONT_SMALL_BOLD);
        logoutBtn.setPreferredSize(new Dimension(88, 34));
        logoutBtn.addActionListener(e -> {
            AuthManager.logout();
            dispose();
            new LoginGUI().setVisible(true);
        });
        profileBox.add(logoutBtn);

        header.add(brandBox, BorderLayout.WEST);
        header.add(searchBox, BorderLayout.CENTER);
        header.add(profileBox, BorderLayout.EAST);
        return header;
    }

    private JPanel buildSideNav() {
        JPanel rail = new JPanel(new BorderLayout(0, 12));
        rail.setBackground(Theme.SURFACE);
        rail.setPreferredSize(new Dimension(212, 0));
        rail.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
                new EmptyBorder(18, 12, 16, 12)
        ));

        JPanel navList = new JPanel();
        navList.setOpaque(false);
        navList.setLayout(new BoxLayout(navList, BoxLayout.Y_AXIS));

        JLabel navLabel = new JLabel("  NAVIGATION");
        navLabel.setFont(Theme.FONT_SMALL_BOLD);
        navLabel.setForeground(Theme.TEXT_MUTED);
        navLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        navNowShowing = makeNavBtn("Now Showing", () -> switchView("NOW"));
        navMyTickets = makeNavBtn("My Tickets", () -> {
            loadMyTickets();
            switchView("TICKETS");
        });
        navNotifications = makeNavBtn("Notifications", this::openNotificationsView);
        navVip = makeNavBtn("VIP Membership", () -> {
            refreshVipCard();
            switchView("VIP");
        });

        navList.add(navLabel);
        navList.add(Box.createVerticalStrut(10));
        navList.add(navNowShowing);
        navList.add(Box.createVerticalStrut(6));
        navList.add(navMyTickets);
        navList.add(Box.createVerticalStrut(6));
        navList.add(navNotifications);
        navList.add(Box.createVerticalStrut(6));
        navList.add(navVip);

        if (user.isAdmin()) {
            navAdmin = makeNavBtn("Admin Studio", () -> {
                if (adminPanel != null) {
                    adminPanel.refreshAll();
                }
                switchView("ADMIN");
            });
            navList.add(Box.createVerticalStrut(12));
            JLabel admLbl = new JLabel("  MANAGEMENT");
            admLbl.setFont(Theme.FONT_SMALL_BOLD);
            admLbl.setForeground(Theme.GOLD);
            admLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            navList.add(admLbl);
            navList.add(Box.createVerticalStrut(6));
            navList.add(navAdmin);
        }

        Components.RoundedPanel secBox = new Components.RoundedPanel(12, Theme.CARD, Theme.BORDER);
        secBox.setLayout(new BoxLayout(secBox, BoxLayout.Y_AXIS));
        secBox.setBorder(new EmptyBorder(12, 12, 12, 12));
        JLabel secHead = new JLabel("SECURE CHECKOUT");
        secHead.setFont(Theme.FONT_SMALL_BOLD);
        secHead.setForeground(Theme.EMERALD);
        JLabel secBody = new JLabel("<html>Seats lock atomically via JDBC ACID transactions. Every ticket carries an HMAC-SHA256 seal.</html>");
        secBody.setFont(Theme.FONT_SMALL);
        secBody.setForeground(Theme.TEXT_MUTED);
        secBox.add(secHead);
        secBox.add(Box.createVerticalStrut(4));
        secBox.add(secBody);

        rail.add(navList, BorderLayout.NORTH);
        rail.add(secBox, BorderLayout.SOUTH);
        return rail;
    }

    private Components.GlowButton makeNavBtn(String text, Runnable action) {
        Components.GlowButton b = new Components.GlowButton(text, Components.GlowButton.Variant.NAV);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        b.setPreferredSize(new Dimension(184, 42));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void switchView(String key) {
        mainCards.show(mainContainer, key);
        navNowShowing.setNavActive("NOW".equals(key));
        navMyTickets.setNavActive("TICKETS".equals(key));
        navNotifications.setNavActive("NOTIFICATIONS".equals(key));
        navVip.setNavActive("VIP".equals(key));
        if (navAdmin != null) {
            navAdmin.setNavActive("ADMIN".equals(key));
        }
    }

    private JPanel buildNowShowingView() {
        JPanel view = new JPanel(new BorderLayout(0, 12));
        view.setBackground(Theme.BG_DARK);
        view.setBorder(new EmptyBorder(14, 16, 14, 16));

        // Top section: movie cards strip + selected movie meta + showtime pills
        JPanel topSection = new JPanel(new BorderLayout(0, 10));
        topSection.setOpaque(false);

        movieStripPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        movieStripPanel.setBackground(Theme.BG_DARK);
        JScrollPane movieScroll = new JScrollPane(movieStripPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        Theme.styleScrollPane(movieScroll);
        movieScroll.setBorder(null);
        movieScroll.setPreferredSize(new Dimension(0, 198));
        movieScroll.addMouseWheelListener(e -> {
            JScrollBar hBar = movieScroll.getHorizontalScrollBar();
            hBar.setValue(hBar.getValue() + (e.getWheelRotation() * 48));
        });

        Components.RoundedPanel showBarPanel = new Components.RoundedPanel(12, Theme.SURFACE, Theme.BORDER);
        showBarPanel.setLayout(new BorderLayout(12, 6));
        showBarPanel.setBorder(new EmptyBorder(10, 14, 10, 14));

        movieMetaLabel = new JLabel("Select a movie to browse showtimes");
        movieMetaLabel.setFont(Theme.FONT_BODY_BOLD);
        movieMetaLabel.setForeground(Theme.TEXT_PRIMARY);

        showtimeBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        showtimeBar.setOpaque(false);

        showBarPanel.add(movieMetaLabel, BorderLayout.NORTH);
        showBarPanel.add(showtimeBar, BorderLayout.CENTER);

        topSection.add(movieScroll, BorderLayout.NORTH);
        topSection.add(showBarPanel, BorderLayout.SOUTH);

        // Center split: Auditorium + Checkout Cart
        JPanel centerSplit = new JPanel(new BorderLayout(14, 0));
        centerSplit.setOpaque(false);

        // Auditorium panel
        Components.RoundedPanel auditorium = new Components.RoundedPanel(14, Theme.SURFACE, Theme.BORDER);
        auditorium.setLayout(new BorderLayout(0, 10));
        auditorium.setBorder(new EmptyBorder(12, 16, 14, 16));

        JPanel screenHeader = new JPanel(new BorderLayout());
        screenHeader.setOpaque(false);
        screenHeader.add(new Components.CurvedScreenPanel(), BorderLayout.CENTER);

        Components.GlowButton refreshSeatsBtn = new Components.GlowButton("Refresh Map", Components.GlowButton.Variant.GHOST);
        refreshSeatsBtn.setFont(Theme.FONT_SMALL_BOLD);
        refreshSeatsBtn.setPreferredSize(new Dimension(112, 30));
        refreshSeatsBtn.addActionListener(e -> reloadSeats());
        JPanel refreshWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        refreshWrap.setOpaque(false);
        refreshWrap.add(refreshSeatsBtn);
        screenHeader.add(refreshWrap, BorderLayout.EAST);

        seatGridContainer = new JPanel(new GridBagLayout());
        seatGridContainer.setOpaque(false);
        JScrollPane seatScroll = new JScrollPane(seatGridContainer);
        Theme.styleScrollPane(seatScroll);
        seatScroll.setBorder(null);
        seatScroll.getViewport().setBackground(Theme.SURFACE);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 4));
        legend.setOpaque(false);
        legend.add(makeLegendItem("Standard", Theme.EMERALD));
        legend.add(makeLegendItem("Gold", Theme.SKY));
        legend.add(makeLegendItem("VIP Recliner", Theme.GOLD));
        legend.add(makeLegendItem("In Cart", Theme.TEXT_PRIMARY));
        legend.add(makeLegendItem("Occupied", Theme.DANGER));
        legend.add(makeLegendItem("Your Seat", Theme.PURPLE));

        auditorium.add(screenHeader, BorderLayout.NORTH);
        auditorium.add(seatScroll, BorderLayout.CENTER);
        auditorium.add(legend, BorderLayout.SOUTH);

        // Checkout cart sidebar
        centerSplit.add(auditorium, BorderLayout.CENTER);
        centerSplit.add(buildCheckoutCart(), BorderLayout.EAST);

        view.add(topSection, BorderLayout.NORTH);
        view.add(centerSplit, BorderLayout.CENTER);
        return view;
    }

    private JPanel makeLegendItem(String text, Color dotColor) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        p.setOpaque(false);
        JPanel dot = new JPanel();
        dot.setBackground(dotColor);
        dot.setPreferredSize(new Dimension(11, 11));
        JLabel lbl = new JLabel(text);
        lbl.setFont(Theme.FONT_SMALL);
        lbl.setForeground(Theme.TEXT_MUTED);
        p.add(dot);
        p.add(lbl);
        return p;
    }

    private JPanel buildCheckoutCart() {
        Components.RoundedPanel cart = new Components.RoundedPanel(14, Theme.SURFACE, Theme.BORDER);
        cart.setPreferredSize(new Dimension(350, 0));
        cart.setLayout(new BorderLayout(0, 10));
        cart.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel cartTitle = new JLabel("Booking Cart");
        cartTitle.setFont(Theme.FONT_H2);
        cartTitle.setForeground(Theme.GOLD);
        Components.GlowButton clearBtn = new Components.GlowButton("Clear Seats", Components.GlowButton.Variant.GHOST);
        clearBtn.setFont(Theme.FONT_SMALL_BOLD);
        clearBtn.setBorder(new EmptyBorder(4, 10, 4, 10));
        clearBtn.addActionListener(e -> {
            selectedSeats.clear();
            renderSeatGrid();
            recalculateCartTotals();
        });
        top.add(cartTitle, BorderLayout.WEST);
        top.add(clearBtn, BorderLayout.EAST);

        Components.ScrollablePanel body = new Components.ScrollablePanel();
        body.setBackground(Theme.SURFACE);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(4, 2, 10, 8));

        selectedSeatsLabel = new JLabel("No seats selected yet. Click seats on the map.");
        selectedSeatsLabel.setFont(Theme.FONT_SMALL_BOLD);
        selectedSeatsLabel.setForeground(Theme.SKY);
        selectedSeatsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel snacksHead = new JLabel("CONCESSIONS & COMBOS");
        snacksHead.setFont(Theme.FONT_SMALL_BOLD);
        snacksHead.setForeground(Theme.TEXT_MUTED);
        snacksHead.setAlignmentX(Component.LEFT_ALIGNMENT);

        snacksListPanel = new JPanel();
        snacksListPanel.setOpaque(false);
        snacksListPanel.setLayout(new BoxLayout(snacksListPanel, BoxLayout.Y_AXIS));
        Components.RoundedPanel snacksCard = new Components.RoundedPanel(10, Theme.INPUT_BG, Theme.BORDER);
        snacksCard.setLayout(new BorderLayout());
        snacksCard.setBorder(new EmptyBorder(4, 4, 4, 4));
        snacksCard.add(snacksListPanel, BorderLayout.CENTER);
        snacksCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel couponHead = new JLabel("PROMO CODE (WELCOME10, STUDENT15, CINE20)");
        couponHead.setFont(Theme.FONT_SMALL_BOLD);
        couponHead.setForeground(Theme.TEXT_MUTED);
        couponHead.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel couponRow = new JPanel(new BorderLayout(6, 0));
        couponRow.setOpaque(false);
        couponRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        couponRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        couponField = new Components.DarkTextField("Enter promo code");
        Components.GlowButton applyCouponBtn = new Components.GlowButton("Apply", Components.GlowButton.Variant.GOLD);
        applyCouponBtn.setPreferredSize(new Dimension(80, 36));
        applyCouponBtn.addActionListener(e -> applyCoupon());
        couponRow.add(couponField, BorderLayout.CENTER);
        couponRow.add(applyCouponBtn, BorderLayout.EAST);

        couponStatusLabel = new JLabel(" ");
        couponStatusLabel.setFont(Theme.FONT_SMALL);
        couponStatusLabel.setForeground(Theme.TEXT_MUTED);
        couponStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Totals card
        Components.RoundedPanel totalsCard = new Components.RoundedPanel(10, Theme.CARD, Theme.BORDER);
        totalsCard.setLayout(new GridLayout(5, 2, 6, 4));
        totalsCard.setBorder(new EmptyBorder(10, 12, 10, 12));
        totalsCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        totalsCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        seatSubtotalVal = makeValLabel(Theme.TEXT_PRIMARY);
        memberDiscVal = makeValLabel(Theme.GOLD);
        couponDiscVal = makeValLabel(Theme.EMERALD);
        snacksTotalVal = makeValLabel(Theme.TEXT_PRIMARY);
        grandTotalVal = makeValLabel(Theme.EMERALD);
        grandTotalVal.setFont(Theme.FONT_H3);

        totalsCard.add(makeKeyLabel("Seat Subtotal:"));
        totalsCard.add(seatSubtotalVal);
        totalsCard.add(makeKeyLabel("VIP Discount:"));
        totalsCard.add(memberDiscVal);
        totalsCard.add(makeKeyLabel("Promo Discount:"));
        totalsCard.add(couponDiscVal);
        totalsCard.add(makeKeyLabel("Snacks & Combos:"));
        totalsCard.add(snacksTotalVal);
        totalsCard.add(makeKeyLabel("GRAND TOTAL:"));
        totalsCard.add(grandTotalVal);

        paymentCombo = new JComboBox<>(new String[]{"Card / Visa", "bKash", "Nagad", "Cash at Counter"});
        Theme.styleComboBox(paymentCombo);
        paymentCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        paymentCombo.setAlignmentX(Component.LEFT_ALIGNMENT);

        body.add(selectedSeatsLabel);
        body.add(Box.createVerticalStrut(10));
        body.add(snacksHead);
        body.add(Box.createVerticalStrut(4));
        body.add(snacksCard);
        body.add(Box.createVerticalStrut(10));
        body.add(couponHead);
        body.add(Box.createVerticalStrut(4));
        body.add(couponRow);
        body.add(Box.createVerticalStrut(2));
        body.add(couponStatusLabel);
        body.add(Box.createVerticalStrut(8));
        body.add(totalsCard);
        body.add(Box.createVerticalStrut(8));
        body.add(makeKeyLabel("Payment Method:"));
        body.add(Box.createVerticalStrut(4));
        body.add(paymentCombo);

        JScrollPane cartScroll = new JScrollPane(body,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        Theme.styleScrollPane(cartScroll);
        cartScroll.setBorder(null);
        cartScroll.getViewport().setBackground(Theme.SURFACE);
        cartScroll.getVerticalScrollBar().setUnitIncrement(22);

        Components.GlowButton payBtn = new Components.GlowButton(
                "Confirm & Pay", Components.GlowButton.Variant.PRIMARY);
        payBtn.setPreferredSize(new Dimension(0, 44));
        payBtn.addActionListener(e -> confirmBooking());

        cart.add(top, BorderLayout.NORTH);
        cart.add(cartScroll, BorderLayout.CENTER);
        cart.add(payBtn, BorderLayout.SOUTH);
        return cart;
    }

    private JLabel makeKeyLabel(String txt) {
        JLabel l = new JLabel(txt);
        l.setFont(Theme.FONT_SMALL_BOLD);
        l.setForeground(Theme.TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel makeValLabel(Color color) {
        JLabel l = new JLabel("BDT 0.00", SwingConstants.RIGHT);
        l.setFont(Theme.FONT_SMALL_BOLD);
        l.setForeground(color);
        return l;
    }

    private JPanel buildMyTicketsView() {
        JPanel view = new JPanel(new BorderLayout(0, 12));
        view.setBackground(Theme.BG_DARK);
        view.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel headerWrap = new JPanel(new BorderLayout(0, 10));
        headerWrap.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleRow.setOpaque(false);
        JLabel title = new JLabel("My Bookings & Signed Digital E-Tickets");
        title.setFont(Theme.FONT_H1);
        title.setForeground(Theme.TEXT_PRIMARY);
        myTicketsCountBadge = new Components.BadgeLabel(
                "@" + user.getUsername() + "  \u2022  0 bookings", Theme.CARD, Theme.GOLD);
        titleRow.add(title);
        titleRow.add(myTicketsCountBadge);

        Components.RoundedPanel controlBar = new Components.RoundedPanel(12, Theme.SURFACE, Theme.BORDER);
        controlBar.setLayout(new BorderLayout(10, 0));
        controlBar.setBorder(new EmptyBorder(10, 14, 10, 14));

        ticketCodeInput = new Components.DarkTextField("Enter Booking Code (e.g. CV-2026-XXXXXX) or click a row below");
        ticketCodeInput.addActionListener(e -> previewTicketFromInput());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        Components.GlowButton findBtn = new Components.GlowButton("Find Ticket", Components.GlowButton.Variant.GHOST);
        myTicketViewBtn = new Components.GlowButton("Open E-Ticket / Save PNG", Components.GlowButton.Variant.GOLD);
        myTicketCancelBtn = new Components.GlowButton("Cancel Ticket", Components.GlowButton.Variant.DANGER);
        Components.GlowButton refBtn = new Components.GlowButton("Refresh", Components.GlowButton.Variant.GHOST);

        findBtn.addActionListener(e -> previewTicketFromInput());
        myTicketViewBtn.addActionListener(e -> openSelectedTicketPass());
        myTicketCancelBtn.addActionListener(e -> cancelSelectedBooking());
        refBtn.addActionListener(e -> loadMyTickets());

        actions.add(findBtn);
        actions.add(myTicketViewBtn);
        actions.add(myTicketCancelBtn);
        actions.add(refBtn);

        controlBar.add(ticketCodeInput, BorderLayout.CENTER);
        controlBar.add(actions, BorderLayout.EAST);

        headerWrap.add(titleRow, BorderLayout.NORTH);
        headerWrap.add(controlBar, BorderLayout.SOUTH);

        ticketsModel = new DefaultTableModel(
                new String[]{"Booking Code", "Movie", "Hall & Showtime", "Seats", "Concessions", "Total (BDT)", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        ticketsTable = new JTable(ticketsModel);
        Theme.styleTable(ticketsTable);
        ticketsTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object val, boolean selected,
                                                           boolean focused, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(tbl, val, selected, focused, row, col);
                String st = String.valueOf(val);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setFont(Theme.FONT_SMALL_BOLD);
                lbl.setBackground(selected ? new Color(0x1E, 0x3A, 0x5F)
                        : (row % 2 == 0 ? Theme.SURFACE : new Color(0x16, 0x1F, 0x33)));
                lbl.setForeground("CONFIRMED".equalsIgnoreCase(st) ? Theme.EMERALD : Theme.DANGER);
                return lbl;
            }
        });

        ticketsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = ticketsTable.getSelectedRow();
                if (row >= 0 && row < userBookings.size()) {
                    Booking b = userBookings.get(row);
                    ticketCodeInput.setText(b.getBookingCode());
                    renderLiveTicketPreview(b);
                }
            }
        });

        ticketsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && ticketsTable.getSelectedRow() >= 0) {
                    openSelectedTicketPass();
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(ticketsTable);
        Theme.styleScrollPane(tableScroll);
        tableScroll.setMinimumSize(new Dimension(0, 150));

        ticketPreviewContainer = new JPanel(new GridBagLayout());
        ticketPreviewContainer.setBackground(Theme.BG_DARK);
        ticketPreviewContainer.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane previewScroll = new JScrollPane(ticketPreviewContainer);
        Theme.styleScrollPane(previewScroll);
        previewScroll.getViewport().setBackground(Theme.BG_DARK);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, previewScroll);
        splitPane.setResizeWeight(0.40);
        splitPane.setDividerSize(7);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        splitPane.setBackground(Theme.BG_DARK);

        view.add(headerWrap, BorderLayout.NORTH);
        view.add(splitPane, BorderLayout.CENTER);
        return view;
    }

    private JPanel buildVipView() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(Theme.BG_DARK);

        Components.RoundedPanel card = new Components.RoundedPanel(
                18, Theme.CARD, Theme.SURFACE, Theme.GOLD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(28, 34, 28, 34));
        card.setPreferredSize(new Dimension(620, 410));

        Components.BadgeLabel badge = new Components.BadgeLabel(
                "POLYMORPHIC MEMBERSHIP TIER", Theme.GOLD_SOFT, Theme.GOLD);
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);

        vipStatusTitle = new JLabel("Membership Status");
        vipStatusTitle.setFont(Theme.FONT_HERO);
        vipStatusTitle.setForeground(Theme.TEXT_PRIMARY);
        vipStatusTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        vipStatusDesc = new JLabel(" ");
        vipStatusDesc.setFont(Theme.FONT_BODY);
        vipStatusDesc.setForeground(Theme.TEXT_MUTED);
        vipStatusDesc.setAlignmentX(Component.LEFT_ALIGNMENT);

        Components.RoundedPanel perksBox = new Components.RoundedPanel(12, Theme.SURFACE, Theme.BORDER);
        perksBox.setLayout(new BoxLayout(perksBox, BoxLayout.Y_AXIS));
        perksBox.setBorder(new EmptyBorder(16, 18, 16, 18));
        perksBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        perksBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        String[] perks = {
                "\u2605  Instant 20% discount on Standard, Gold & VIP Recliner seats",
                "\u2605  Stacks with promotional coupon codes (WELCOME10, CINE20)",
                "\u2605  Priority VIP Gold badge across profile & HMAC E-Tickets",
                "\u2605  One-time lifetime membership activation (BDT 999)"
        };
        for (String p : perks) {
            JLabel l = new JLabel(p);
            l.setFont(Theme.FONT_BODY);
            l.setForeground(Theme.TEXT_PRIMARY);
            l.setBorder(new EmptyBorder(4, 0, 4, 0));
            perksBox.add(l);
        }

        vipUpgradeBtn = new Components.GlowButton(
                "Upgrade to VIP Gold (BDT 999)", Components.GlowButton.Variant.GOLD);
        vipUpgradeBtn.setMaximumSize(new Dimension(320, 46));
        vipUpgradeBtn.setPreferredSize(new Dimension(320, 46));
        vipUpgradeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        vipUpgradeBtn.addActionListener(e -> handleVipUpgrade());

        card.add(badge);
        card.add(Box.createVerticalStrut(14));
        card.add(vipStatusTitle);
        card.add(Box.createVerticalStrut(6));
        card.add(vipStatusDesc);
        card.add(Box.createVerticalStrut(20));
        card.add(perksBox);
        card.add(Box.createVerticalStrut(24));
        card.add(vipUpgradeBtn);

        outer.add(card);
        return outer;
    }

    private void loadSnacks() {
        snacksListPanel.removeAll();
        selectedSnacks.clear();
        try {
            List<SnackItem> snacks = snackDAO.findAllAvailable();
            for (SnackItem item : snacks) {
                selectedSnacks.put(item, 0);

                JPanel row = new JPanel(new BorderLayout(6, 0));
                row.setOpaque(false);
                row.setBorder(new EmptyBorder(5, 8, 5, 8));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

                JLabel nameLbl = new JLabel(item.getName() + " (BDT " + (int) item.getPrice() + ")");
                nameLbl.setFont(Theme.FONT_SMALL);
                nameLbl.setForeground(Theme.TEXT_PRIMARY);

                JPanel qtyBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
                qtyBox.setOpaque(false);

                JLabel qtyLbl = new JLabel("0", SwingConstants.CENTER);
                qtyLbl.setFont(Theme.FONT_SMALL_BOLD);
                qtyLbl.setForeground(Theme.GOLD);
                qtyLbl.setPreferredSize(new Dimension(22, 22));

                Components.GlowButton minus = new Components.GlowButton("-", Components.GlowButton.Variant.GHOST);
                minus.setFont(Theme.FONT_SMALL_BOLD);
                minus.setBorder(new EmptyBorder(2, 8, 2, 8));
                minus.addActionListener(e -> {
                    int cur = selectedSnacks.getOrDefault(item, 0);
                    if (cur > 0) {
                        selectedSnacks.put(item, cur - 1);
                        qtyLbl.setText(String.valueOf(cur - 1));
                        recalculateCartTotals();
                    }
                });

                Components.GlowButton plus = new Components.GlowButton("+", Components.GlowButton.Variant.GHOST);
                plus.setFont(Theme.FONT_SMALL_BOLD);
                plus.setBorder(new EmptyBorder(2, 8, 2, 8));
                plus.addActionListener(e -> {
                    int cur = selectedSnacks.getOrDefault(item, 0);
                    if (cur < 10) {
                        selectedSnacks.put(item, cur + 1);
                        qtyLbl.setText(String.valueOf(cur + 1));
                        recalculateCartTotals();
                    }
                });

                qtyBox.add(minus);
                qtyBox.add(qtyLbl);
                qtyBox.add(plus);

                row.add(nameLbl, BorderLayout.CENTER);
                row.add(qtyBox, BorderLayout.EAST);
                snacksListPanel.add(row);
            }
        } catch (SQLException e) {
            System.err.println("Failed to load snacks: " + e.getMessage());
        }
        snacksListPanel.revalidate();
        snacksListPanel.repaint();
    }

    private void loadMovies(String query, String genre) {
        try {
            movies = movieDAO.search(query, genre);
            if (movies.isEmpty()) {
                selectedMovie = null;
                movieShows = new ArrayList<>();
                currentShow = null;
                movieMetaLabel.setText("No active movies matched your filter.");
                showtimeBar.removeAll();
                showtimeBar.revalidate();
                showtimeBar.repaint();
                renderMovieCards();
                seatGridContainer.removeAll();
                seatGridContainer.revalidate();
                seatGridContainer.repaint();
                return;
            }
            if (selectedMovie == null || movies.stream().noneMatch(m -> m.getId() == selectedMovie.getId())) {
                selectedMovie = movies.get(0);
            }
            renderMovieCards();
            loadShowsForSelectedMovie();
        } catch (SQLException e) {
            Theme.showStyledMessage(this, "Database Error", "Could not load movies: " + e.getMessage(),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renderMovieCards() {
        movieStripPanel.removeAll();
        for (Movie m : movies) {
            boolean isSel = selectedMovie != null && m.getId() == selectedMovie.getId();
            Components.MoviePosterCard card = new Components.MoviePosterCard(m, isSel, () -> {
                selectedMovie = m;
                renderMovieCards();
                loadShowsForSelectedMovie();
            });
            movieStripPanel.add(card);
        }
        movieStripPanel.revalidate();
        movieStripPanel.repaint();
    }

    private void loadShowsForSelectedMovie() {
        if (selectedMovie == null) return;
        try {
            movieShows = showDAO.findActiveShowsByMovie(selectedMovie.getId());
            movieMetaLabel.setText(String.format(Locale.US,
                    "%s  |  %s  |  %d min  |  IMDb %.1f  -  %s",
                    selectedMovie.getTitle(), selectedMovie.getGenre(),
                    selectedMovie.getDurationMin(), selectedMovie.getImdbRating(),
                    selectedMovie.getSynopsis()));

            if (movieShows.isEmpty()) {
                currentShow = null;
                showtimeBar.removeAll();
                JLabel none = new JLabel("No showtimes scheduled for this movie yet.");
                none.setFont(Theme.FONT_SMALL_BOLD);
                none.setForeground(Theme.WARN);
                showtimeBar.add(none);
                showtimeBar.revalidate();
                showtimeBar.repaint();
                seatGridContainer.removeAll();
                seatGridContainer.revalidate();
                seatGridContainer.repaint();
                selectedSeats.clear();
                recalculateCartTotals();
                return;
            }

            if (currentShow == null || movieShows.stream().noneMatch(s -> s.getId() == currentShow.getId())) {
                currentShow = movieShows.get(0);
            }
            renderShowtimePills();
            reloadSeats();
        } catch (SQLException e) {
            Theme.showStyledMessage(this, "Database Error", e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renderShowtimePills() {
        showtimeBar.removeAll();
        for (Show s : movieShows) {
            boolean active = currentShow != null && s.getId() == currentShow.getId();
            String label = s.getShowDate() + "  " + s.getShowTime()
                    + "  (" + s.getHallName() + " \u2022 " + s.availableSeats() + " free)";
            Components.GlowButton pill = new Components.GlowButton(
                    label, active ? Components.GlowButton.Variant.GOLD : Components.GlowButton.Variant.GHOST);
            pill.setFont(Theme.FONT_SMALL_BOLD);
            pill.setBorder(new EmptyBorder(6, 12, 6, 12));
            pill.addActionListener(e -> {
                currentShow = s;
                renderShowtimePills();
                reloadSeats();
            });
            showtimeBar.add(pill);
        }
        showtimeBar.revalidate();
        showtimeBar.repaint();
    }

    private void reloadSeats() {
        if (currentShow == null) return;
        selectedSeats.clear();
        try {
            currentShow = showDAO.findById(currentShow.getId());
            if (currentShow == null) return;
            currentSeatMap = showDAO.getSeatMap(currentShow);
            renderShowtimePills();
            renderSeatGrid();
            recalculateCartTotals();
        } catch (SQLException e) {
            Theme.showStyledMessage(this, "Database Error", e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renderSeatGrid() {
        seatGridContainer.removeAll();
        if (currentShow == null || currentSeatMap == null) {
            seatGridContainer.revalidate();
            seatGridContainer.repaint();
            return;
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);

        for (int r = 0; r < currentSeatMap.length; r++) {
            String tier = currentShow.getSeatTier(r);
            JLabel rowHeader = new JLabel(String.valueOf((char) ('A' + r)) + " (" + tier.charAt(0) + ")");
            rowHeader.setFont(Theme.FONT_SMALL_BOLD);
            rowHeader.setForeground("VIP".equals(tier) ? Theme.GOLD
                    : ("GOLD".equals(tier) ? Theme.SKY : Theme.EMERALD));
            gbc.gridx = 0;
            gbc.gridy = r;
            seatGridContainer.add(rowHeader, gbc);

            for (int c = 0; c < currentSeatMap[r].length; c++) {
                SeatInfo seat = currentSeatMap[r][c];
                Components.SeatButton btn = new Components.SeatButton(seat, user.getId());
                btn.setSelectedInCart(isSeatInCart(seat));

                btn.addActionListener(e -> {
                    if (isSeatInCart(seat)) {
                        removeSeatFromCart(seat);
                        btn.setSelectedInCart(false);
                    } else {
                        if (selectedSeats.size() >= 8) {
                            Theme.showStyledMessage(this, "Seat Limit",
                                    "You can reserve up to 8 seats in a single transaction.",
                                    JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                        selectedSeats.add(seat);
                        btn.setSelectedInCart(true);
                    }
                    recalculateCartTotals();
                });

                // Add central aisle spacing when cols >= 6
                int colIdx = c + 1;
                gbc.gridx = (currentSeatMap[r].length >= 6 && c >= currentSeatMap[r].length / 2)
                        ? colIdx + 1 : colIdx;
                gbc.gridy = r;
                seatGridContainer.add(btn, gbc);
            }
            if (currentSeatMap[r].length >= 6) {
                gbc.gridx = (currentSeatMap[r].length / 2) + 1;
                gbc.gridy = r;
                seatGridContainer.add(Box.createHorizontalStrut(18), gbc);
            }
        }
        seatGridContainer.revalidate();
        seatGridContainer.repaint();
    }

    private boolean isSeatInCart(SeatInfo seat) {
        return selectedSeats.stream().anyMatch(
                s -> s.getRow() == seat.getRow() && s.getCol() == seat.getCol());
    }

    private void removeSeatFromCart(SeatInfo seat) {
        selectedSeats.removeIf(s -> s.getRow() == seat.getRow() && s.getCol() == seat.getCol());
    }

    private void applyCoupon() {
        String code = couponField.getText().trim().toUpperCase(Locale.US);
        if (code.isEmpty()) {
            appliedCoupon = null;
            couponStatusLabel.setText("Coupon cleared.");
            couponStatusLabel.setForeground(Theme.TEXT_MUTED);
            recalculateCartTotals();
            return;
        }
        try {
            Coupon c = couponDAO.findValidByCode(code);
            if (c == null) {
                appliedCoupon = null;
                couponStatusLabel.setText("Invalid, expired, or exhausted promo code.");
                couponStatusLabel.setForeground(Theme.DANGER);
            } else {
                appliedCoupon = c;
                couponStatusLabel.setText(String.format(Locale.US,
                        "Applied %s: %.0f%% extra discount!", c.getCode(), c.getDiscountPercent()));
                couponStatusLabel.setForeground(Theme.EMERALD);
            }
            recalculateCartTotals();
        } catch (SQLException e) {
            Theme.showStyledMessage(this, "Coupon Error", e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void recalculateCartTotals() {
        if (selectedSeats.isEmpty()) {
            selectedSeatsLabel.setText("No seats selected yet. Click seats on the map.");
        } else {
            List<String> tags = new ArrayList<>();
            for (SeatInfo s : selectedSeats) {
                tags.add(s.getLabel() + " (" + s.getTier() + ")");
            }
            selectedSeatsLabel.setText("<html><body style='width:270px'>Seats: "
                    + String.join(", ", tags) + "</body></html>");
        }

        BookingDAO.PricingBreakdown pb = BookingDAO.calculatePricing(
                user.toCustomer(), currentShow, selectedSeats, selectedSnacks, appliedCoupon);

        seatSubtotalVal.setText(String.format(Locale.US, "BDT %.2f", pb.getSeatSubtotal()));
        memberDiscVal.setText(String.format(Locale.US, "- BDT %.2f", pb.getMemberDiscount()));
        couponDiscVal.setText(String.format(Locale.US, "- BDT %.2f", pb.getCouponDiscount()));
        snacksTotalVal.setText(String.format(Locale.US, "BDT %.2f", pb.getSnacksSubtotal()));
        grandTotalVal.setText(String.format(Locale.US, "BDT %.2f", pb.getGrandTotal()));
    }

    private void confirmBooking() {
        if (currentShow == null) {
            Theme.showStyledMessage(this, "No Show Selected", "Please select an active showtime.", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selectedSeats.isEmpty()) {
            Theme.showStyledMessage(this, "Select Seats", "Click at least one green/gold/VIP seat on the map.",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        String payment = (String) paymentCombo.getSelectedItem();
        Ticket ticket = new Ticket(user, currentShow, new ArrayList<>(selectedSeats),
                new LinkedHashMap<>(selectedSnacks), appliedCoupon, payment);

        double total = ticket.calculateTotal();
        String msg = String.format(Locale.US,
                "Movie: %s\nShowtime: %s at %s (%s)\nSeats: %d seat(s)\nPayment: %s\nTotal Payable: BDT %.2f\n\nProceed with secure checkout?",
                currentShow.getMovie().getTitle(), currentShow.getShowDate(), currentShow.getShowTime(),
                currentShow.getHallName(), selectedSeats.size(), payment, total);

        if (!Theme.confirmStyled(this, "Confirm Booking", msg)) {
            return;
        }

        try {
            ticket.bookTicket();
            Booking booked = ticket.getPersistedBooking();
            appliedCoupon = null;
            couponField.setText("");
            couponStatusLabel.setText(" ");
            if (booked != null && ticketCodeInput != null) {
                ticketCodeInput.setText(booked.getBookingCode());
            }
            if (booked != null) {
                NotificationService.notifyBookingCreated(booked);
            }
            loadSnacks();
            reloadSeats();
            loadMyTickets();
            refreshNotificationsView();
            if (adminPanel != null) {
                adminPanel.refreshAll();
            }
            if (booked != null) {
                new TicketDialog(this, booked).setVisible(true);
            }
        } catch (BookingDAO.SeatAlreadyBookedException conflict) {
            Theme.showStyledMessage(this, "Seat Conflict Blocked", conflict.getMessage(), JOptionPane.WARNING_MESSAGE);
            reloadSeats();
        } catch (Exception ex) {
            Theme.showStyledMessage(this, "Booking Error", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            reloadSeats();
        }
    }

    private void loadMyTickets() {
        String prevCode = ticketCodeInput != null ? ticketCodeInput.getText().trim().toUpperCase(Locale.US) : "";
        try {
            userBookings = bookingDAO.findByUser(user.getId());
            if (myTicketsCountBadge != null) {
                myTicketsCountBadge.setText("@" + user.getUsername() + "  \u2022  "
                        + userBookings.size() + (userBookings.size() == 1 ? " booking" : " bookings"));
            }

            ticketsModel.setRowCount(0);
            int matchRow = -1;
            for (int i = 0; i < userBookings.size(); i++) {
                Booking b = userBookings.get(i);
                ticketsModel.addRow(new Object[]{
                        b.getBookingCode(),
                        b.getMovieTitle(),
                        b.getHallName() + " (" + b.getShowDate() + " " + b.getShowTime() + ")",
                        b.getSeatLabelsDisplay(),
                        b.getSnacksSummary(),
                        String.format(Locale.US, "%.2f", b.getTotalAmount()),
                        b.getStatus()
                });
                if (!prevCode.isEmpty() && b.getBookingCode().equalsIgnoreCase(prevCode)) {
                    matchRow = i;
                }
            }

            if (!userBookings.isEmpty()) {
                int targetRow = matchRow >= 0 ? matchRow : 0;
                ticketsTable.setRowSelectionInterval(targetRow, targetRow);
                Booking selected = userBookings.get(targetRow);
                ticketCodeInput.setText(selected.getBookingCode());
                renderLiveTicketPreview(selected);
            } else {
                if (ticketCodeInput != null && prevCode.isEmpty()) {
                    ticketCodeInput.setText("");
                }
                renderEmptyTicketPreview();
                if (myTicketCancelBtn != null) {
                    myTicketCancelBtn.setEnabled(false);
                }
            }
        } catch (SQLException e) {
            Theme.showStyledMessage(this, "Database Error",
                    "Could not load bookings: " + e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void previewTicketFromInput() {
        Booking target = resolveTargetBooking(true);
        if (target != null) {
            renderLiveTicketPreview(target);
        }
    }

    private Booking resolveTargetBooking(boolean showFeedback) {
        String typed = ticketCodeInput != null ? ticketCodeInput.getText().trim().toUpperCase(Locale.US) : "";
        if (!typed.isEmpty()) {
            for (int i = 0; i < userBookings.size(); i++) {
                Booking b = userBookings.get(i);
                if (b.getBookingCode().equalsIgnoreCase(typed)) {
                    ticketsTable.setRowSelectionInterval(i, i);
                    return b;
                }
            }
            try {
                Booking found = bookingDAO.findByCode(typed);
                if (found == null) {
                    if (showFeedback) {
                        Theme.showStyledMessage(this, "Ticket Not Found",
                                "No booking record found for code: " + typed, JOptionPane.WARNING_MESSAGE);
                    }
                    return null;
                }
                if (found.getUserId() != user.getId() && !user.isAdmin()) {
                    if (showFeedback) {
                        Theme.showStyledMessage(this, "Access Denied",
                                "Booking " + typed + " belongs to another customer account.",
                                JOptionPane.ERROR_MESSAGE);
                    }
                    return null;
                }
                return found;
            } catch (SQLException e) {
                if (showFeedback) {
                    Theme.showStyledMessage(this, "Database Error", e.getMessage(), JOptionPane.ERROR_MESSAGE);
                }
                return null;
            }
        }

        int row = ticketsTable.getSelectedRow();
        if (row >= 0 && row < userBookings.size()) {
            return userBookings.get(row);
        }
        if (showFeedback) {
            Theme.showStyledMessage(this, "Select Ticket",
                    "Click a booking row in the table or enter a Booking Code first.",
                    JOptionPane.WARNING_MESSAGE);
        }
        return null;
    }

    private void renderLiveTicketPreview(Booking b) {
        if (ticketPreviewContainer == null) return;
        ticketPreviewContainer.removeAll();

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        Components.TicketPassPanel passPanel = new Components.TicketPassPanel(b);
        passPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        Components.GlowButton savePngBtn = new Components.GlowButton(
                "Save E-Ticket (PNG)", Components.GlowButton.Variant.GOLD);
        savePngBtn.addActionListener(e -> exportTicketPng(passPanel, b));

        Components.GlowButton cancelDirectBtn = new Components.GlowButton(
                b.isConfirmed() ? "Cancel This Booking" : "Booking Cancelled",
                Components.GlowButton.Variant.DANGER);
        cancelDirectBtn.setEnabled(b.isConfirmed());
        cancelDirectBtn.addActionListener(e -> cancelSpecificBooking(b));

        btnRow.add(savePngBtn);
        btnRow.add(cancelDirectBtn);

        stack.add(passPanel);
        stack.add(Box.createVerticalStrut(10));
        stack.add(btnRow);

        ticketPreviewContainer.add(stack);
        if (myTicketCancelBtn != null) {
            myTicketCancelBtn.setEnabled(b.isConfirmed());
        }
        ticketPreviewContainer.revalidate();
        ticketPreviewContainer.repaint();
    }

    private void renderEmptyTicketPreview() {
        if (ticketPreviewContainer == null) return;
        ticketPreviewContainer.removeAll();

        Components.RoundedPanel emptyCard = new Components.RoundedPanel(14, Theme.SURFACE, Theme.BORDER);
        emptyCard.setLayout(new BoxLayout(emptyCard, BoxLayout.Y_AXIS));
        emptyCard.setBorder(new EmptyBorder(26, 34, 26, 34));

        JLabel head = new JLabel("No Bookings Found for @" + user.getUsername());
        head.setFont(Theme.FONT_H2);
        head.setForeground(Theme.GOLD);
        head.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("<html><center>Reserve seats in <b>Now Showing</b> to generate your HMAC-SHA256 Digital E-Ticket,<br>"
                + "or enter a valid Booking Code in the input bar above.</center></html>");
        sub.setFont(Theme.FONT_BODY);
        sub.setForeground(Theme.TEXT_MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        Components.GlowButton goBookBtn = new Components.GlowButton(
                "Browse Movies & Book Seats", Components.GlowButton.Variant.PRIMARY);
        goBookBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        goBookBtn.addActionListener(e -> switchView("NOW"));

        emptyCard.add(head);
        emptyCard.add(Box.createVerticalStrut(8));
        emptyCard.add(sub);
        emptyCard.add(Box.createVerticalStrut(16));
        emptyCard.add(goBookBtn);

        ticketPreviewContainer.add(emptyCard);
        ticketPreviewContainer.revalidate();
        ticketPreviewContainer.repaint();
    }

    private void exportTicketPng(Components.TicketPassPanel passPanel, Booking booking) {
        File defaultDir = new File("tickets");
        if (!defaultDir.exists()) {
            defaultDir.mkdirs();
        }
        JFileChooser chooser = new JFileChooser(defaultDir);
        chooser.setSelectedFile(new File(defaultDir, booking.getBookingCode() + ".png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            if (!target.getName().toLowerCase().endsWith(".png")) {
                target = new File(target.getParentFile(), target.getName() + ".png");
            }
            try {
                if (passPanel.exportToPng(target)) {
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
    }

    private void openSelectedTicketPass() {
        Booking b = resolveTargetBooking(true);
        if (b == null) return;
        renderLiveTicketPreview(b);
        new TicketDialog(this, b).setVisible(true);
    }

    private void cancelSelectedBooking() {
        Booking b = resolveTargetBooking(true);
        if (b == null) return;
        cancelSpecificBooking(b);
    }

    private void cancelSpecificBooking(Booking b) {
        if (!b.isConfirmed()) {
            Theme.showStyledMessage(this, "Already Cancelled",
                    "Booking " + b.getBookingCode() + " is already cancelled.",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!Theme.confirmStyled(this, "Cancel Ticket",
                "Cancel booking " + b.getBookingCode() + " (" + b.getSeatLabelsDisplay() + ") and release seats?")) {
            return;
        }
        try {
            new Ticket(b, user).cancelTicket();
            NotificationService.notifyBookingCancelled(b, user);
            if (ticketCodeInput != null) {
                ticketCodeInput.setText(b.getBookingCode());
            }
            loadMyTickets();
            refreshNotificationsView();
            Booking refreshed = bookingDAO.findByCode(b.getBookingCode());
            if (refreshed != null) {
                renderLiveTicketPreview(refreshed);
            }
            reloadSeats();
            if (adminPanel != null) {
                adminPanel.refreshAll();
            }
            Theme.showStyledMessage(this, "Ticket Cancelled",
                    "Booking " + b.getBookingCode() + " cancelled and seats (" + b.getSeatLabelsDisplay() + ") released.",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            Theme.showStyledMessage(this, "Cancellation Error", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshVipCard() {
        boolean vip = user.isPremium();
        vipStatusTitle.setText(vip ? "VIP Gold Member (Active)" : "Standard Member");
        vipStatusTitle.setForeground(vip ? Theme.GOLD : Theme.TEXT_PRIMARY);
        vipStatusDesc.setText(user.toCustomer().getPerkSummary());
        vipUpgradeBtn.setEnabled(!vip);
        vipUpgradeBtn.setText(vip ? "VIP Gold Active (20% Discount Enabled)" : "Upgrade to VIP Gold (BDT 999)");

        headerTierBadge.setText(vip ? "VIP GOLD (20% OFF)" : "STANDARD");
        headerTierBadge.setBadgeColors(vip ? Theme.GOLD_SOFT : Theme.CARD, vip ? Theme.GOLD : Theme.TEXT_MUTED);
        setTitle("CineVault  |  " + user.getFullName() + " (" + user.toCustomer().getTierName() + ")");
    }

    private void handleVipUpgrade() {
        if (user.isPremium()) return;
        if (!Theme.confirmStyled(this, "Activate VIP Gold",
                "Confirm instant VIP Gold activation (BDT 999) for 20% discount on all future bookings?")) {
            return;
        }
        try {
            this.user = AuthManager.upgradeToPremium(this.user);
            refreshVipCard();
            recalculateCartTotals();
            if (adminPanel != null) {
                adminPanel.refreshAll();
            }
            Theme.showStyledMessage(this, "Welcome to VIP Gold",
                    "VIP Gold activated! Your 20% instant discount is now active in your booking cart.",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            Theme.showStyledMessage(this, "Upgrade Error", e.getMessage(), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reloadCatalogFromAdmin() {
        loadMovies(searchField.getText(), (String) genreCombo.getSelectedItem());
    }

    private JPanel buildNotificationsView() {
        JPanel view = new JPanel(new BorderLayout(0, 14));
        view.setBackground(Theme.BG_DARK);
        view.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Real-Time Notification Center");
        title.setFont(Theme.FONT_H1);
        title.setForeground(Theme.TEXT_PRIMARY);

        notifHeaderSummary = new JLabel("TCP Socket Endpoint: "
                + DatabaseConfig.getSocketHost() + ":" + DatabaseConfig.getSocketPort());
        notifHeaderSummary.setFont(Theme.FONT_SMALL);
        notifHeaderSummary.setForeground(Theme.TEXT_MUTED);

        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(notifHeaderSummary);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        Components.GlowButton markAllBtn = new Components.GlowButton("Mark All Read", Components.GlowButton.Variant.GOLD);
        Components.GlowButton refBtn = new Components.GlowButton("Refresh", Components.GlowButton.Variant.GHOST);

        markAllBtn.addActionListener(e -> {
            try {
                notificationDAO.markAllRead(user.getId());
                refreshNotificationsView();
            } catch (SQLException ex) {
                Theme.showStyledMessage(this, "Database Error", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });
        refBtn.addActionListener(e -> refreshNotificationsView());

        actions.add(markAllBtn);
        actions.add(refBtn);

        top.add(titleBox, BorderLayout.WEST);
        top.add(actions, BorderLayout.EAST);

        notificationsListPanel = new Components.ScrollablePanel();
        notificationsListPanel.setBackground(Theme.BG_DARK);
        notificationsListPanel.setLayout(new BoxLayout(notificationsListPanel, BoxLayout.Y_AXIS));
        notificationsListPanel.setBorder(new EmptyBorder(6, 4, 12, 8));

        JScrollPane scroll = new JScrollPane(notificationsListPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        Theme.styleScrollPane(scroll);
        scroll.getViewport().setBackground(Theme.BG_DARK);
        scroll.getVerticalScrollBar().setUnitIncrement(22);

        view.add(top, BorderLayout.NORTH);
        view.add(scroll, BorderLayout.CENTER);
        return view;
    }

    private void openNotificationsView() {
        refreshNotificationsView();
        switchView("NOTIFICATIONS");
    }

    private void refreshNotificationsView() {
        if (notificationsListPanel == null) {
            return;
        }
        try {
            List<Notification> list = notificationDAO.findByUser(user.getId(), 50);
            int unread = notificationDAO.countUnread(user.getId());

            if (notifBellBtn != null) {
                notifBellBtn.setText(unread > 0 ? "Alerts (" + unread + ")" : "Alerts");
                notifBellBtn.setVariant(unread > 0
                        ? Components.GlowButton.Variant.GOLD
                        : Components.GlowButton.Variant.GHOST);
            }
            if (navNotifications != null) {
                navNotifications.setText(unread > 0 ? "Notifications (" + unread + ")" : "Notifications");
            }
            if (notifHeaderSummary != null) {
                notifHeaderSummary.setText(String.format(Locale.US,
                        "TCP Socket %s:%d  \u2022  %d unread / %d total notifications",
                        DatabaseConfig.getSocketHost(), DatabaseConfig.getSocketPort(), unread, list.size()));
            }

            notificationsListPanel.removeAll();
            if (list.isEmpty()) {
                Components.RoundedPanel empty = new Components.RoundedPanel(14, Theme.SURFACE, Theme.BORDER);
                empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
                empty.setBorder(new EmptyBorder(24, 28, 24, 28));
                empty.setAlignmentX(Component.LEFT_ALIGNMENT);
                empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

                JLabel h = new JLabel("No Notifications Yet");
                h.setFont(Theme.FONT_H2);
                h.setForeground(Theme.GOLD);
                JLabel p = new JLabel("You will receive instant real-time alerts here whenever a ticket is booked, cancelled, or rescheduled.");
                p.setFont(Theme.FONT_BODY);
                p.setForeground(Theme.TEXT_MUTED);
                empty.add(h);
                empty.add(Box.createVerticalStrut(6));
                empty.add(p);
                notificationsListPanel.add(empty);
            } else {
                for (Notification n : list) {
                    notificationsListPanel.add(makeNotificationCard(n));
                    notificationsListPanel.add(Box.createVerticalStrut(10));
                }
            }
            notificationsListPanel.revalidate();
            notificationsListPanel.repaint();
        } catch (SQLException e) {
            System.err.println("Could not refresh notifications: " + e.getMessage());
        }
    }

    private JPanel makeNotificationCard(Notification n) {
        Color accent = ToastNotification.colorForType(n.getType());
        Color border = n.isRead() ? Theme.BORDER : accent;

        Components.RoundedPanel card = new Components.RoundedPanel(
                12, n.isRead() ? Theme.SURFACE : Theme.CARD, border);
        card.setLayout(new BorderLayout(14, 0));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        meta.setOpaque(false);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        Components.BadgeLabel typeBadge = new Components.BadgeLabel(
                n.getType(), Theme.withAlpha(accent, 45), accent);
        meta.add(typeBadge);

        if (!n.isRead()) {
            meta.add(new Components.BadgeLabel("NEW", Theme.GOLD_SOFT, Theme.GOLD));
        }

        JLabel timeLbl = new JLabel(n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
        timeLbl.setFont(Theme.FONT_SMALL);
        timeLbl.setForeground(Theme.TEXT_MUTED);
        meta.add(timeLbl);

        JLabel titleLbl = new JLabel(n.getTitle());
        titleLbl.setFont(Theme.FONT_H3);
        titleLbl.setForeground(Theme.TEXT_PRIMARY);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel msgLbl = new JLabel("<html><body style='width:680px'>" + n.getMessage() + "</body></html>");
        msgLbl.setFont(Theme.FONT_BODY);
        msgLbl.setForeground(Theme.TEXT_MUTED);
        msgLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(meta);
        left.add(Box.createVerticalStrut(6));
        left.add(titleLbl);
        left.add(Box.createVerticalStrut(4));
        left.add(msgLbl);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
        right.setOpaque(false);

        if (n.getBookingCode() != null && !n.getBookingCode().isBlank()) {
            Components.GlowButton openBtn = new Components.GlowButton("Open Ticket", Components.GlowButton.Variant.GOLD);
            openBtn.setFont(Theme.FONT_SMALL_BOLD);
            openBtn.addActionListener(e -> {
                try {
                    if (!n.isRead()) {
                        notificationDAO.markRead(n.getId(), user.getId());
                    }
                } catch (SQLException ignored) {
                }
                if (ticketCodeInput != null) {
                    ticketCodeInput.setText(n.getBookingCode());
                }
                loadMyTickets();
                refreshNotificationsView();
                switchView("TICKETS");
            });
            right.add(openBtn);
        }

        if (!n.isRead()) {
            Components.GlowButton readBtn = new Components.GlowButton("Mark Read", Components.GlowButton.Variant.GHOST);
            readBtn.setFont(Theme.FONT_SMALL_BOLD);
            readBtn.addActionListener(e -> {
                try {
                    notificationDAO.markRead(n.getId(), user.getId());
                    refreshNotificationsView();
                } catch (SQLException ex) {
                    Theme.showStyledMessage(this, "Error", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
                }
            });
            right.add(readBtn);
        }

        card.add(left, BorderLayout.CENTER);
        card.add(right, BorderLayout.EAST);
        return card;
    }

    private void startNotificationClient() {
        notificationClient = new NotificationClient(
                user,
                DatabaseConfig.getSocketHost(),
                DatabaseConfig.getSocketPort(),
                new NotificationClient.Listener() {
                    @Override
                    public void onConnectionChanged(boolean connected, String statusText) {
                        SwingUtilities.invokeLater(() -> {
                            if (socketStatusBadge != null) {
                                socketStatusBadge.setText(connected ? "\u25CF LIVE TCP" : "\u25CF RETRYING");
                                socketStatusBadge.setBadgeColors(
                                        connected ? Theme.EMERALD_SOFT : Theme.GOLD_SOFT,
                                        connected ? Theme.EMERALD : Theme.WARN
                                );
                                socketStatusBadge.setToolTipText(statusText);
                            }
                        });
                    }

                    @Override
                    public void onNotificationReceived(Notification notification) {
                        SwingUtilities.invokeLater(() -> {
                            refreshNotificationsView();
                            loadMyTickets();
                            ToastNotification.show(CinemaGUI.this, notification, CinemaGUI.this::openNotificationsView);
                        });
                    }

                    @Override
                    public void onSyncEvent(String event) {
                        SwingUtilities.invokeLater(() -> {
                            if ("SEATS".equalsIgnoreCase(event)) {
                                syncSeatsFromNetwork();
                                loadMyTickets();
                                if (adminPanel != null) {
                                    adminPanel.refreshAll();
                                }
                            } else if ("CATALOG".equalsIgnoreCase(event)) {
                                reloadCatalogFromAdmin();
                                loadMyTickets();
                                if (adminPanel != null) {
                                    adminPanel.refreshAll();
                                }
                            }
                        });
                    }
                }
        );
        notificationClient.connect();
    }

    private void syncSeatsFromNetwork() {
        if (currentShow == null) {
            return;
        }
        try {
            currentShow = showDAO.findById(currentShow.getId());
            if (currentShow == null) {
                return;
            }
            currentSeatMap = showDAO.getSeatMap(currentShow);
            selectedSeats.removeIf(s ->
                    s.getRow() < 0 || s.getRow() >= currentSeatMap.length
                            || s.getCol() < 0 || s.getCol() >= currentSeatMap[0].length
                            || currentSeatMap[s.getRow()][s.getCol()].isBooked());
            renderShowtimePills();
            renderSeatGrid();
            recalculateCartTotals();
        } catch (SQLException ignored) {
        }
    }

    private void shutdownSocketClient() {
        if (notificationClient != null) {
            notificationClient.disconnect();
            notificationClient = null;
        }
    }
}
