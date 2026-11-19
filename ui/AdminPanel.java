package ui;

import auth.User;
import dao.AuditLogDAO;
import dao.BookingDAO;
import dao.MovieDAO;
import dao.ShowDAO;
import dao.UserDAO;
import manager.AuditLog;
import manager.Booking;
import manager.Movie;
import manager.Show;
import net.NotificationService;
import security.SessionManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full-featured Administrator Studio protected by RBAC:
 * Revenue Analytics, Movie & Show Catalog CRUD, HMAC Ticket Verifier,
 * User Lockout Management, and Live Security Audit Logs.
 */
public class AdminPanel extends JPanel {

    private final User adminUser;
    private final Runnable onCatalogChanged;

    private final MovieDAO movieDAO = new MovieDAO();
    private final ShowDAO showDAO = new ShowDAO();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final UserDAO userDAO = new UserDAO();
    private final AuditLogDAO auditDAO = new AuditLogDAO();

    private final CardLayout subCards = new CardLayout();
    private final JPanel subContainer = new JPanel(subCards);
    private final Components.GlowButton[] tabButtons = new Components.GlowButton[5];

    // Overview components
    private JLabel kpiRevenueVal;
    private JLabel kpiBookingsVal;
    private JLabel kpiUsersVal;
    private JLabel kpiMoviesVal;
    private Components.RevenueBarChartPanel revenueChart;
    private JPanel hallOccupancyPanel;

    // Catalog components
    private DefaultTableModel moviesModel;
    private JTable moviesTable;
    private DefaultTableModel showsModel;
    private JTable showsTable;
    private JComboBox<Movie> showMovieCombo;

    // Verifier components
    private Components.DarkTextField verifyCodeField;
    private JLabel verifyStatusBanner;
    private JTextArea verifyDetailsArea;
    private Components.GlowButton viewVerifiedPassBtn;
    private Components.GlowButton cancelVerifiedBtn;
    private Booking lastVerifiedBooking;

    // Users components
    private DefaultTableModel usersModel;
    private JTable usersTable;

    // Audit components
    private DefaultTableModel auditModel;
    private JTable auditTable;
    private JComboBox<String> auditSeverityCombo;
    private Components.DarkTextField auditSearchField;

    public AdminPanel(User adminUser, Runnable onCatalogChanged) {
        SessionManager.requireAdmin();
        this.adminUser = adminUser;
        this.onCatalogChanged = onCatalogChanged;

        setLayout(new BorderLayout(0, 14));
        setBackground(Theme.BG_DARK);
        setBorder(new EmptyBorder(16, 18, 16, 18));

        add(buildHeaderBar(), BorderLayout.NORTH);

        subContainer.setOpaque(false);
        subContainer.add(buildOverviewTab(), "OVERVIEW");
        subContainer.add(buildCatalogTab(), "CATALOG");
        subContainer.add(buildVerifierTab(), "VERIFIER");
        subContainer.add(buildUsersTab(), "USERS");
        subContainer.add(buildAuditTab(), "AUDIT");
        add(subContainer, BorderLayout.CENTER);

        switchTab(0, "OVERVIEW");
        refreshAll();
    }

    private JPanel buildHeaderBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 10));
        bar.setOpaque(false);

        JPanel titleBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleBox.setOpaque(false);
        JLabel title = new JLabel("Admin Command Studio");
        title.setFont(Theme.FONT_H1);
        title.setForeground(Theme.TEXT_PRIMARY);
        Components.BadgeLabel rbac = new Components.BadgeLabel("RBAC ADMIN", Theme.GOLD_SOFT, Theme.GOLD);
        titleBox.add(title);
        titleBox.add(rbac);

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tabs.setOpaque(false);
        String[] names = {"Overview & Revenue", "Movies & Shows", "Ticket Verifier", "Users & Security", "Audit Logs"};
        String[] keys = {"OVERVIEW", "CATALOG", "VERIFIER", "USERS", "AUDIT"};

        for (int i = 0; i < names.length; i++) {
            int idx = i;
            String key = keys[i];
            Components.GlowButton btn = new Components.GlowButton(names[i], Components.GlowButton.Variant.GHOST);
            btn.setFont(Theme.FONT_SMALL_BOLD);
            btn.addActionListener(e -> {
                switchTab(idx, key);
                refreshAll();
            });
            tabButtons[i] = btn;
            tabs.add(btn);
        }

        bar.add(titleBox, BorderLayout.WEST);
        bar.add(tabs, BorderLayout.EAST);
        return bar;
    }

    private void switchTab(int activeIdx, String key) {
        subCards.show(subContainer, key);
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].setVariant(i == activeIdx
                    ? Components.GlowButton.Variant.GOLD
                    : Components.GlowButton.Variant.GHOST);
        }
    }

    private JPanel buildOverviewTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setOpaque(false);

        JPanel kpiGrid = new JPanel(new GridLayout(1, 4, 14, 0));
        kpiGrid.setOpaque(false);

        kpiRevenueVal = new JLabel("BDT 0.00");
        kpiBookingsVal = new JLabel("0");
        kpiUsersVal = new JLabel("0");
        kpiMoviesVal = new JLabel("0");

        kpiGrid.add(makeKpiCard("CONFIRMED REVENUE", kpiRevenueVal, Theme.EMERALD));
        kpiGrid.add(makeKpiCard("ACTIVE BOOKINGS", kpiBookingsVal, Theme.SKY));
        kpiGrid.add(makeKpiCard("REGISTERED USERS", kpiUsersVal, Theme.GOLD));
        kpiGrid.add(makeKpiCard("NOW SHOWING MOVIES", kpiMoviesVal, Theme.CRIMSON));

        JPanel middle = new JPanel(new GridLayout(1, 2, 16, 0));
        middle.setOpaque(false);

        Components.RoundedPanel chartCard = new Components.RoundedPanel(14, Theme.SURFACE, Theme.BORDER);
        chartCard.setLayout(new BorderLayout(0, 10));
        chartCard.setBorder(new EmptyBorder(16, 18, 16, 18));
        JLabel chartTitle = new JLabel("Revenue by Movie (BDT)");
        chartTitle.setFont(Theme.FONT_H2);
        chartTitle.setForeground(Theme.GOLD);
        revenueChart = new Components.RevenueBarChartPanel();
        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(revenueChart, BorderLayout.CENTER);

        Components.RoundedPanel occCard = new Components.RoundedPanel(14, Theme.SURFACE, Theme.BORDER);
        occCard.setLayout(new BorderLayout(0, 12));
        occCard.setBorder(new EmptyBorder(16, 18, 16, 18));
        JPanel occTop = new JPanel(new BorderLayout());
        occTop.setOpaque(false);
        JLabel occTitle = new JLabel("Auditorium Occupancy");
        occTitle.setFont(Theme.FONT_H2);
        occTitle.setForeground(Theme.SKY);
        Components.GlowButton refreshBtn = new Components.GlowButton("Refresh", Components.GlowButton.Variant.GHOST);
        refreshBtn.setFont(Theme.FONT_SMALL_BOLD);
        refreshBtn.addActionListener(e -> refreshOverview());
        occTop.add(occTitle, BorderLayout.WEST);
        occTop.add(refreshBtn, BorderLayout.EAST);

        hallOccupancyPanel = new JPanel();
        hallOccupancyPanel.setOpaque(false);
        hallOccupancyPanel.setLayout(new BoxLayout(hallOccupancyPanel, BoxLayout.Y_AXIS));

        occCard.add(occTop, BorderLayout.NORTH);
        occCard.add(hallOccupancyPanel, BorderLayout.CENTER);

        middle.add(chartCard);
        middle.add(occCard);

        panel.add(kpiGrid, BorderLayout.NORTH);
        panel.add(middle, BorderLayout.CENTER);
        return panel;
    }

    private JPanel makeKpiCard(String label, JLabel valueLabel, Color accent) {
        Components.RoundedPanel card = new Components.RoundedPanel(14, Theme.CARD, Theme.BORDER);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        JLabel lbl = new JLabel(label);
        lbl.setFont(Theme.FONT_SMALL_BOLD);
        lbl.setForeground(Theme.TEXT_MUTED);

        valueLabel.setFont(Theme.FONT_H1);
        valueLabel.setForeground(accent);

        JPanel line = new JPanel();
        line.setBackground(accent);
        line.setMaximumSize(new Dimension(48, 3));
        line.setPreferredSize(new Dimension(48, 3));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(lbl);
        card.add(Box.createVerticalStrut(8));
        card.add(valueLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(line);
        return card;
    }

    private JPanel buildCatalogTab() {
        JPanel split = new JPanel(new GridLayout(2, 1, 0, 14));
        split.setOpaque(false);

        // Top: Movies section
        Components.RoundedPanel movieSec = new Components.RoundedPanel(14, Theme.SURFACE, Theme.BORDER);
        movieSec.setLayout(new BorderLayout(14, 0));
        movieSec.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel addMovieForm = new JPanel();
        addMovieForm.setOpaque(false);
        addMovieForm.setLayout(new BoxLayout(addMovieForm, BoxLayout.Y_AXIS));
        addMovieForm.setPreferredSize(new Dimension(340, 220));

        JLabel mHead = new JLabel("Add New Movie");
        mHead.setFont(Theme.FONT_H3);
        mHead.setForeground(Theme.GOLD);

        Components.DarkTextField mTitle = new Components.DarkTextField("Movie Title");
        JPanel mRow1 = new JPanel(new GridLayout(1, 3, 6, 0));
        mRow1.setOpaque(false);
        JComboBox<String> mGenre = new JComboBox<>(new String[]{"Action", "Sci-Fi", "Thriller", "Drama", "Adventure", "Comedy", "Horror"});
        Theme.styleComboBox(mGenre);
        Components.DarkTextField mDuration = new Components.DarkTextField("Min (e.g. 145)");
        Components.DarkTextField mImdb = new Components.DarkTextField("IMDb (8.5)");
        mRow1.add(mGenre);
        mRow1.add(mDuration);
        mRow1.add(mImdb);

        JPanel mRow2 = new JPanel(new GridLayout(1, 3, 6, 0));
        mRow2.setOpaque(false);
        JComboBox<String> mRating = new JComboBox<>(new String[]{"PG-13", "PG", "R", "G"});
        Theme.styleComboBox(mRating);
        Components.DarkTextField mLang = new Components.DarkTextField("Language (English)");
        JComboBox<String> mColor = new JComboBox<>(new String[]{"#E50914", "#38BDF8", "#F59E0B", "#8B5CF6", "#10B981", "#EC4899"});
        Theme.styleComboBox(mColor);
        mRow2.add(mRating);
        mRow2.add(mLang);
        mRow2.add(mColor);

        Components.DarkTextField mSynopsis = new Components.DarkTextField("Short movie synopsis...");

        JPanel mBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        mBtns.setOpaque(false);
        Components.GlowButton addMovieBtn = new Components.GlowButton("Add Movie", Components.GlowButton.Variant.PRIMARY);
        Components.GlowButton toggleMovieBtn = new Components.GlowButton("Toggle Active", Components.GlowButton.Variant.GHOST);
        mBtns.add(addMovieBtn);
        mBtns.add(toggleMovieBtn);

        addMovieForm.add(mHead);
        addMovieForm.add(Box.createVerticalStrut(6));
        addMovieForm.add(mTitle);
        addMovieForm.add(Box.createVerticalStrut(6));
        addMovieForm.add(mRow1);
        addMovieForm.add(Box.createVerticalStrut(6));
        addMovieForm.add(mRow2);
        addMovieForm.add(Box.createVerticalStrut(6));
        addMovieForm.add(mSynopsis);
        addMovieForm.add(Box.createVerticalStrut(8));
        addMovieForm.add(mBtns);

        moviesModel = new DefaultTableModel(new String[]{"ID", "Title", "Genre", "Duration", "IMDb", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        moviesTable = new JTable(moviesModel);
        Theme.styleTable(moviesTable);
        JScrollPane mScroll = new JScrollPane(moviesTable);
        Theme.styleScrollPane(mScroll);

        addMovieBtn.addActionListener(e -> {
            try {
                String title = mTitle.getText().trim();
                if (title.isEmpty()) {
                    throw new IllegalArgumentException("Movie title is required.");
                }
                int dur = Integer.parseInt(mDuration.getText().trim());
                double imdb = Double.parseDouble(mImdb.getText().trim());
                if (dur <= 0 || imdb < 0 || imdb > 10) {
                    throw new IllegalArgumentException("Enter valid duration (>0) and IMDb rating (0-10).");
                }
                String lang = mLang.getText().trim().isEmpty() ? "English" : mLang.getText().trim();
                String syn = mSynopsis.getText().trim().isEmpty() ? "Special screening at CineVault." : mSynopsis.getText().trim();
                Movie m = new Movie(0, title, (String) mGenre.getSelectedItem(),
                        (String) mRating.getSelectedItem(), lang, dur, imdb, syn,
                        (String) mColor.getSelectedItem(), true);
                movieDAO.insert(m);
                auditDAO.log(adminUser.getId(), adminUser.getUsername(), "MOVIE_ADDED",
                        "Added movie '" + title + "' (" + dur + " min)", "INFO");
                mTitle.setText("");
                mDuration.setText("");
                mImdb.setText("");
                mSynopsis.setText("");
                refreshCatalog();
                if (onCatalogChanged != null) onCatalogChanged.run();
            } catch (Exception ex) {
                Theme.showStyledMessage(this, "Movie Validation", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });

        toggleMovieBtn.addActionListener(e -> {
            int row = moviesTable.getSelectedRow();
            if (row < 0) {
                Theme.showStyledMessage(this, "Select Movie", "Select a movie row first.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int id = (int) moviesModel.getValueAt(row, 0);
            boolean activeNow = "ACTIVE".equals(moviesModel.getValueAt(row, 5));
            try {
                movieDAO.setActive(id, !activeNow);
                auditDAO.log(adminUser.getId(), adminUser.getUsername(), "MOVIE_STATUS_CHANGED",
                        "Set movie #" + id + " active=" + (!activeNow), "INFO");
                refreshCatalog();
                if (onCatalogChanged != null) onCatalogChanged.run();
            } catch (SQLException ex) {
                Theme.showStyledMessage(this, "Database Error", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });

        movieSec.add(addMovieForm, BorderLayout.WEST);
        movieSec.add(mScroll, BorderLayout.CENTER);

        // Bottom: Shows section
        Components.RoundedPanel showSec = new Components.RoundedPanel(14, Theme.SURFACE, Theme.BORDER);
        showSec.setLayout(new BorderLayout(14, 0));
        showSec.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel addShowForm = new JPanel();
        addShowForm.setOpaque(false);
        addShowForm.setLayout(new BoxLayout(addShowForm, BoxLayout.Y_AXIS));
        addShowForm.setPreferredSize(new Dimension(340, 220));

        JLabel sHead = new JLabel("Schedule New Showtime");
        sHead.setFont(Theme.FONT_H3);
        sHead.setForeground(Theme.SKY);

        showMovieCombo = new JComboBox<>();
        Theme.styleComboBox(showMovieCombo);

        JPanel sRow1 = new JPanel(new GridLayout(1, 2, 6, 0));
        sRow1.setOpaque(false);
        JComboBox<String> sHall = new JComboBox<>(new String[]{
                "Hall 1 - IMAX 3D", "Hall 2 - Dolby Atmos", "Hall 3 - VIP Lounge"
        });
        Theme.styleComboBox(sHall);
        Components.DarkTextField sTime = new Components.DarkTextField("Time (e.g. 09:30 PM)");
        sRow1.add(sHall);
        sRow1.add(sTime);

        JPanel sRow2 = new JPanel(new GridLayout(1, 3, 6, 0));
        sRow2.setOpaque(false);
        Components.DarkTextField sDate = new Components.DarkTextField("YYYY-MM-DD");
        sDate.setText(LocalDate.now().toString());
        JSpinner sRows = new JSpinner(new SpinnerNumberModel(6, 3, 9, 1));
        JSpinner sCols = new JSpinner(new SpinnerNumberModel(8, 4, 12, 1));
        sRow2.add(sDate);
        sRow2.add(sRows);
        sRow2.add(sCols);

        JPanel sRow3 = new JPanel(new GridLayout(1, 3, 6, 0));
        sRow3.setOpaque(false);
        Components.DarkTextField pStd = new Components.DarkTextField("Std (350)");
        pStd.setText("350");
        Components.DarkTextField pGold = new Components.DarkTextField("Gold (500)");
        pGold.setText("500");
        Components.DarkTextField pVip = new Components.DarkTextField("VIP (750)");
        pVip.setText("750");
        sRow3.add(pStd);
        sRow3.add(pGold);
        sRow3.add(pVip);

        JPanel sBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sBtns.setOpaque(false);
        Components.GlowButton addShowBtn = new Components.GlowButton("Add Show", Components.GlowButton.Variant.SUCCESS);
        addShowBtn.setFont(Theme.FONT_SMALL_BOLD);
        addShowBtn.setBorder(new EmptyBorder(7, 10, 7, 10));
        Components.GlowButton reschedShowBtn = new Components.GlowButton("Reschedule", Components.GlowButton.Variant.GOLD);
        reschedShowBtn.setFont(Theme.FONT_SMALL_BOLD);
        reschedShowBtn.setBorder(new EmptyBorder(7, 10, 7, 10));
        Components.GlowButton delShowBtn = new Components.GlowButton("Delete Show", Components.GlowButton.Variant.DANGER);
        delShowBtn.setFont(Theme.FONT_SMALL_BOLD);
        delShowBtn.setBorder(new EmptyBorder(7, 10, 7, 10));
        sBtns.add(addShowBtn);
        sBtns.add(reschedShowBtn);
        sBtns.add(delShowBtn);

        addShowForm.add(sHead);
        addShowForm.add(Box.createVerticalStrut(6));
        addShowForm.add(showMovieCombo);
        addShowForm.add(Box.createVerticalStrut(6));
        addShowForm.add(sRow1);
        addShowForm.add(Box.createVerticalStrut(6));
        addShowForm.add(sRow2);
        addShowForm.add(Box.createVerticalStrut(6));
        addShowForm.add(sRow3);
        addShowForm.add(Box.createVerticalStrut(8));
        addShowForm.add(sBtns);

        showsModel = new DefaultTableModel(new String[]{"ID", "Movie", "Hall", "Date", "Time", "Grid", "Booked"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        showsTable = new JTable(showsModel);
        Theme.styleTable(showsTable);
        JScrollPane sScroll = new JScrollPane(showsTable);
        Theme.styleScrollPane(sScroll);

        addShowBtn.addActionListener(e -> {
            try {
                Movie selected = (Movie) showMovieCombo.getSelectedItem();
                if (selected == null) {
                    throw new IllegalArgumentException("Select a movie first.");
                }
                LocalDate date = LocalDate.parse(sDate.getText().trim());
                String time = sTime.getText().trim();
                if (time.isEmpty()) {
                    throw new IllegalArgumentException("Show time is required (e.g. 07:30 PM).");
                }
                int rows = (Integer) sRows.getValue();
                int cols = (Integer) sCols.getValue();
                double base = Double.parseDouble(pStd.getText().trim());
                double gold = Double.parseDouble(pGold.getText().trim());
                double vip = Double.parseDouble(pVip.getText().trim());

                Show show = new Show(0, selected, (String) sHall.getSelectedItem(),
                        date, time, rows, cols, base, gold, vip, true, 0);
                showDAO.insert(show);
                auditDAO.log(adminUser.getId(), adminUser.getUsername(), "SHOW_ADDED",
                        "Scheduled show for '" + selected.getTitle() + "' on " + date + " " + time, "INFO");
                sTime.setText("");
                refreshCatalog();
                if (onCatalogChanged != null) onCatalogChanged.run();
            } catch (Exception ex) {
                Theme.showStyledMessage(this, "Show Validation", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });

        reschedShowBtn.addActionListener(e -> handleRescheduleSelectedShow());

        delShowBtn.addActionListener(e -> {
            int row = showsTable.getSelectedRow();
            if (row < 0) {
                Theme.showStyledMessage(this, "Select Show", "Select a show row to delete.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int showId = (int) showsModel.getValueAt(row, 0);
            if (!Theme.confirmStyled(this, "Delete Show", "Delete show #" + showId + " and its booking history?")) {
                return;
            }
            try {
                showDAO.delete(showId);
                auditDAO.log(adminUser.getId(), adminUser.getUsername(), "SHOW_DELETED",
                        "Deleted show #" + showId, "WARN");
                refreshCatalog();
                if (onCatalogChanged != null) onCatalogChanged.run();
            } catch (SQLException ex) {
                Theme.showStyledMessage(this, "Database Error", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });

        showSec.add(addShowForm, BorderLayout.WEST);
        showSec.add(sScroll, BorderLayout.CENTER);

        split.add(movieSec);
        split.add(showSec);
        return split;
    }

    private void handleRescheduleSelectedShow() {
        int row = showsTable.getSelectedRow();
        if (row < 0) {
            Theme.showStyledMessage(this, "Select Show",
                    "Select a showtime row in the table to reschedule.", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int showId = (int) showsModel.getValueAt(row, 0);
        Show existing;
        try {
            existing = showDAO.findById(showId);
            if (existing == null) {
                Theme.showStyledMessage(this, "Show Not Found",
                        "Show #" + showId + " no longer exists.", JOptionPane.WARNING_MESSAGE);
                return;
            }
        } catch (SQLException ex) {
            Theme.showStyledMessage(this, "Database Error", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Reschedule Show #" + showId, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Theme.SURFACE);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.GOLD, 1),
                new EmptyBorder(20, 24, 18, 24)
        ));

        JLabel titleLbl = new JLabel("Reschedule: " + existing.getMovie().getTitle());
        titleLbl.setFont(Theme.FONT_H2);
        titleLbl.setForeground(Theme.GOLD);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(360, 190));

        JComboBox<String> hallCombo = new JComboBox<>(new String[]{
                "Hall 1 - IMAX 3D", "Hall 2 - Dolby Atmos", "Hall 3 - VIP Lounge"
        });
        Theme.styleComboBox(hallCombo);
        hallCombo.setSelectedItem(existing.getHallName());
        hallCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        hallCombo.setAlignmentX(Component.LEFT_ALIGNMENT);

        Components.DarkTextField dateField = new Components.DarkTextField("YYYY-MM-DD");
        dateField.setText(existing.getShowDate().toString());
        dateField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        dateField.setAlignmentX(Component.LEFT_ALIGNMENT);

        Components.DarkTextField timeField = new Components.DarkTextField("Time (e.g. 09:00 PM)");
        timeField.setText(existing.getShowTime());
        timeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        timeField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hallLbl = new JLabel("Hall");
        hallLbl.setFont(Theme.FONT_SMALL_BOLD);
        hallLbl.setForeground(Theme.TEXT_MUTED);
        hallLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel dateLbl = new JLabel("New Show Date (YYYY-MM-DD)");
        dateLbl.setFont(Theme.FONT_SMALL_BOLD);
        dateLbl.setForeground(Theme.TEXT_MUTED);
        dateLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel timeLbl = new JLabel("New Show Time");
        timeLbl.setFont(Theme.FONT_SMALL_BOLD);
        timeLbl.setForeground(Theme.TEXT_MUTED);
        timeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        form.add(hallLbl);
        form.add(Box.createVerticalStrut(4));
        form.add(hallCombo);
        form.add(Box.createVerticalStrut(10));
        form.add(dateLbl);
        form.add(Box.createVerticalStrut(4));
        form.add(dateField);
        form.add(Box.createVerticalStrut(10));
        form.add(timeLbl);
        form.add(Box.createVerticalStrut(4));
        form.add(timeField);

        Components.GlowButton cancelBtn = new Components.GlowButton("Cancel", Components.GlowButton.Variant.GHOST);
        cancelBtn.setPreferredSize(new Dimension(100, 36));
        cancelBtn.addActionListener(ev -> dialog.dispose());

        Components.GlowButton saveBtn = new Components.GlowButton("Save & Notify Users", Components.GlowButton.Variant.GOLD);
        saveBtn.setPreferredSize(new Dimension(175, 36));
        saveBtn.addActionListener(ev -> {
            try {
                String newHall = (String) hallCombo.getSelectedItem();
                LocalDate newDate = LocalDate.parse(dateField.getText().trim());
                String newTime = timeField.getText().trim();
                if (newTime.isEmpty()) {
                    throw new IllegalArgumentException("New show time cannot be empty.");
                }
                String oldHall = existing.getHallName();
                LocalDate oldDate = existing.getShowDate();
                String oldTime = existing.getShowTime();

                Show updated = showDAO.rescheduleShow(showId, newHall, newDate, newTime);
                List<Booking> affected = bookingDAO.findConfirmedBookingsByShow(showId);
                auditDAO.log(adminUser.getId(), adminUser.getUsername(), "SHOW_RESCHEDULED",
                        "Rescheduled show #" + showId + " (" + existing.getMovie().getTitle() + ") from "
                                + oldDate + " " + oldTime + " to " + newDate + " " + newTime
                                + " (" + newHall + "); notified " + affected.size() + " booking(s).",
                        "WARN");

                NotificationService.notifyShowRescheduled(updated, oldHall, oldDate, oldTime, affected);
                dialog.dispose();
                refreshCatalog();
                refreshAudit();
                if (onCatalogChanged != null) onCatalogChanged.run();
                Theme.showStyledMessage(this, "Show Rescheduled",
                        "Updated showtime and sent real-time socket notifications to "
                                + affected.size() + " active ticket holder(s).",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                Theme.showStyledMessage(dialog, "Reschedule Validation",
                        ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.add(cancelBtn);
        footer.add(saveBtn);

        root.add(titleLbl, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.getRootPane().setDefaultButton(saveBtn);
        dialog.setVisible(true);
    }

    private JPanel buildVerifierTab() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setOpaque(false);

        Components.RoundedPanel card = new Components.RoundedPanel(16, Theme.SURFACE, Theme.BORDER);
        card.setLayout(new BorderLayout(0, 16));
        card.setBorder(new EmptyBorder(24, 28, 24, 28));
        card.setPreferredSize(new Dimension(760, 440));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Cryptographic Ticket Anti-Fraud Verifier");
        title.setFont(Theme.FONT_H1);
        title.setForeground(Theme.GOLD);

        JLabel desc = new JLabel("Enter a Booking Code (e.g. CV-2026-XXXXXX) to validate its HMAC-SHA256 signature against the database.");
        desc.setFont(Theme.FONT_BODY);
        desc.setForeground(Theme.TEXT_MUTED);

        JPanel searchRow = new JPanel(new BorderLayout(10, 0));
        searchRow.setOpaque(false);
        verifyCodeField = new Components.DarkTextField("Enter Booking Code (e.g., CV-2026-A1B2C3)");
        Components.GlowButton verifyBtn = new Components.GlowButton("Verify Authenticity", Components.GlowButton.Variant.GOLD);
        verifyBtn.setPreferredSize(new Dimension(190, 42));
        searchRow.add(verifyCodeField, BorderLayout.CENTER);
        searchRow.add(verifyBtn, BorderLayout.EAST);

        top.add(title);
        top.add(Box.createVerticalStrut(4));
        top.add(desc);
        top.add(Box.createVerticalStrut(14));
        top.add(searchRow);

        verifyStatusBanner = new JLabel("Awaiting ticket code...");
        verifyStatusBanner.setFont(Theme.FONT_H3);
        verifyStatusBanner.setForeground(Theme.TEXT_MUTED);
        verifyStatusBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                new EmptyBorder(12, 16, 12, 16)
        ));
        verifyStatusBanner.setOpaque(true);
        verifyStatusBanner.setBackground(Theme.CARD);

        verifyDetailsArea = new JTextArea();
        verifyDetailsArea.setEditable(false);
        verifyDetailsArea.setFont(Theme.FONT_MONO);
        verifyDetailsArea.setBackground(Theme.INPUT_BG);
        verifyDetailsArea.setForeground(Theme.TEXT_PRIMARY);
        verifyDetailsArea.setBorder(new EmptyBorder(14, 16, 14, 16));
        verifyDetailsArea.setText("Ticket payload, seat assignment, and HMAC signature details will appear here.");

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);
        center.add(verifyStatusBanner, BorderLayout.NORTH);
        center.add(verifyDetailsArea, BorderLayout.CENTER);

        viewVerifiedPassBtn = new Components.GlowButton("Open Visual E-Ticket Pass", Components.GlowButton.Variant.PRIMARY);
        viewVerifiedPassBtn.setEnabled(false);
        viewVerifiedPassBtn.addActionListener(e -> {
            if (lastVerifiedBooking != null) {
                new TicketDialog(SwingUtilities.getWindowAncestor(this), lastVerifiedBooking).setVisible(true);
            }
        });

        cancelVerifiedBtn = new Components.GlowButton("Cancel Ticket (Admin)", Components.GlowButton.Variant.DANGER);
        cancelVerifiedBtn.setEnabled(false);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.add(cancelVerifiedBtn);
        footer.add(viewVerifiedPassBtn);

        Runnable doVerify = () -> {
            String code = verifyCodeField.getText().trim().toUpperCase(Locale.US);
            if (code.isEmpty()) {
                Theme.showStyledMessage(this, "Enter Code", "Please enter a booking code to verify.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                BookingDAO.VerificationResult res = bookingDAO.verifyBookingByCode(code, adminUser);
                lastVerifiedBooking = res.getBooking();
                viewVerifiedPassBtn.setEnabled(lastVerifiedBooking != null);
                cancelVerifiedBtn.setEnabled(lastVerifiedBooking != null && lastVerifiedBooking.isConfirmed());

                if (!res.isFound()) {
                    verifyStatusBanner.setBackground(Theme.DANGER_SOFT);
                    verifyStatusBanner.setForeground(Theme.DANGER);
                    verifyStatusBanner.setText("NOT FOUND: " + res.getSummary());
                    verifyDetailsArea.setText("No record in bookings table for code: " + code);
                } else if (!res.isAuthentic()) {
                    verifyStatusBanner.setBackground(Theme.DANGER_SOFT);
                    verifyStatusBanner.setForeground(Theme.CRIMSON);
                    verifyStatusBanner.setText("TAMPER ALERT: " + res.getSummary());
                    verifyDetailsArea.setText(formatBookingDetails(res.getBooking(), false));
                } else if (!res.isConfirmed()) {
                    verifyStatusBanner.setBackground(Theme.GOLD_SOFT);
                    verifyStatusBanner.setForeground(Theme.WARN);
                    verifyStatusBanner.setText("CANCELLED TICKET: " + res.getSummary());
                    verifyDetailsArea.setText(formatBookingDetails(res.getBooking(), true));
                } else {
                    verifyStatusBanner.setBackground(Theme.EMERALD_SOFT);
                    verifyStatusBanner.setForeground(Theme.EMERALD);
                    verifyStatusBanner.setText("VERIFIED & VALID: " + res.getSummary());
                    verifyDetailsArea.setText(formatBookingDetails(res.getBooking(), true));
                }
            } catch (SQLException ex) {
                Theme.showStyledMessage(this, "Database Error", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        };

        cancelVerifiedBtn.addActionListener(e -> {
            if (lastVerifiedBooking == null || !lastVerifiedBooking.isConfirmed()) {
                return;
            }
            Booking target = lastVerifiedBooking;
            if (!Theme.confirmStyled(this, "Admin Cancel Ticket",
                    "Cancel booking " + target.getBookingCode() + " (@" + target.getUsername()
                            + ") and send real-time cancellation alert?")) {
                return;
            }
            try {
                bookingDAO.cancelBooking(target.getId(), adminUser);
                NotificationService.notifyBookingCancelled(target, adminUser);
                verifyCodeField.setText(target.getBookingCode());
                doVerify.run();
                refreshAll();
                if (onCatalogChanged != null) onCatalogChanged.run();
                Theme.showStyledMessage(this, "Ticket Cancelled",
                        "Cancelled " + target.getBookingCode() + " and dispatched real-time notification to @"
                                + target.getUsername() + ".",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                Theme.showStyledMessage(this, "Cancel Error", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });

        verifyBtn.addActionListener(e -> doVerify.run());
        verifyCodeField.addActionListener(e -> doVerify.run());

        card.add(top, BorderLayout.NORTH);
        card.add(center, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        outer.add(card);
        return outer;
    }

    private static String formatBookingDetails(Booking b, boolean sigMatch) {
        return String.format(Locale.US,
                "Booking Code : %s%n"
                        + "Status       : %s%n"
                        + "HMAC Match   : %s%n"
                        + "Customer     : %s (@%s)%n"
                        + "Movie        : %s%n"
                        + "Showtime     : %s | %s at %s%n"
                        + "Seats        : %s%n"
                        + "Snacks       : %s%n"
                        + "Total Paid   : BDT %.2f (%s)%n"
                        + "Signature    : %s",
                b.getBookingCode(), b.getStatus(), sigMatch ? "VALID (SHA-256)" : "MISMATCH",
                b.getCustomerFullName(), b.getUsername(), b.getMovieTitle(),
                b.getHallName(), b.getShowDate(), b.getShowTime(),
                b.getSeatLabelsDisplay(), b.getSnacksSummary(),
                b.getTotalAmount(), b.getPaymentMethod(), b.getTicketSignature());
    }

    private JPanel buildUsersTab() {
        Components.RoundedPanel panel = new Components.RoundedPanel(14, Theme.SURFACE, Theme.BORDER);
        panel.setLayout(new BorderLayout(0, 12));
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("User Accounts & Brute-Force Lockout Controls");
        title.setFont(Theme.FONT_H2);
        title.setForeground(Theme.TEXT_PRIMARY);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        Components.GlowButton unlockBtn = new Components.GlowButton("Unlock Selected", Components.GlowButton.Variant.SUCCESS);
        Components.GlowButton vipBtn = new Components.GlowButton("Toggle VIP Gold", Components.GlowButton.Variant.GOLD);
        Components.GlowButton refBtn = new Components.GlowButton("Refresh", Components.GlowButton.Variant.GHOST);
        actions.add(unlockBtn);
        actions.add(vipBtn);
        actions.add(refBtn);

        top.add(title, BorderLayout.WEST);
        top.add(actions, BorderLayout.EAST);

        usersModel = new DefaultTableModel(
                new String[]{"ID", "Username", "Full Name", "Email", "Role", "Tier", "Failed Attempts", "Lock Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        usersTable = new JTable(usersModel);
        Theme.styleTable(usersTable);
        JScrollPane sp = new JScrollPane(usersTable);
        Theme.styleScrollPane(sp);

        unlockBtn.addActionListener(e -> {
            int row = usersTable.getSelectedRow();
            if (row < 0) {
                Theme.showStyledMessage(this, "Select User", "Select a user row first.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int uid = (int) usersModel.getValueAt(row, 0);
            String uname = String.valueOf(usersModel.getValueAt(row, 1));
            try {
                userDAO.unlockAccount(uid);
                auditDAO.log(adminUser.getId(), adminUser.getUsername(), "ADMIN_UNLOCK_USER",
                        "Unlocked account and reset failed attempts for @" + uname, "INFO");
                refreshUsers();
            } catch (SQLException ex) {
                Theme.showStyledMessage(this, "Error", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });

        vipBtn.addActionListener(e -> {
            int row = usersTable.getSelectedRow();
            if (row < 0) {
                Theme.showStyledMessage(this, "Select User", "Select a user row first.", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int uid = (int) usersModel.getValueAt(row, 0);
            String uname = String.valueOf(usersModel.getValueAt(row, 1));
            boolean isVip = "VIP Gold".equals(usersModel.getValueAt(row, 5));
            try {
                userDAO.updatePremiumStatus(uid, !isVip);
                auditDAO.log(adminUser.getId(), adminUser.getUsername(), "ADMIN_TOGGLE_VIP",
                        "Changed VIP status for @" + uname + " to " + (!isVip), "INFO");
                refreshUsers();
            } catch (SQLException ex) {
                Theme.showStyledMessage(this, "Error", ex.getMessage(), JOptionPane.ERROR_MESSAGE);
            }
        });

        refBtn.addActionListener(e -> refreshUsers());

        panel.add(top, BorderLayout.NORTH);
        panel.add(sp, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAuditTab() {
        Components.RoundedPanel panel = new Components.RoundedPanel(14, Theme.SURFACE, Theme.BORDER);
        panel.setLayout(new BorderLayout(0, 12));
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        JLabel title = new JLabel("Live Security & Operational Audit Trail");
        title.setFont(Theme.FONT_H2);
        title.setForeground(Theme.TEXT_PRIMARY);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filters.setOpaque(false);
        auditSeverityCombo = new JComboBox<>(new String[]{"ALL", "SECURITY", "WARN", "INFO"});
        Theme.styleComboBox(auditSeverityCombo);
        auditSearchField = new Components.DarkTextField("Filter user/action/details...");
        auditSearchField.setPreferredSize(new Dimension(220, 34));
        Components.GlowButton applyBtn = new Components.GlowButton("Filter", Components.GlowButton.Variant.GOLD);
        filters.add(auditSeverityCombo);
        filters.add(auditSearchField);
        filters.add(applyBtn);

        top.add(title, BorderLayout.WEST);
        top.add(filters, BorderLayout.EAST);

        auditModel = new DefaultTableModel(
                new String[]{"ID", "Timestamp", "Severity", "Username", "Action", "Details"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        auditTable = new JTable(auditModel);
        Theme.styleTable(auditTable);
        auditTable.getColumnModel().getColumn(0).setMaxWidth(55);
        auditTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        auditTable.getColumnModel().getColumn(2).setMaxWidth(95);
        auditTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        auditTable.getColumnModel().getColumn(4).setPreferredWidth(160);
        auditTable.getColumnModel().getColumn(5).setPreferredWidth(400);

        auditTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object val, boolean sel,
                                                           boolean foc, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(tbl, val, sel, foc, row, col);
                String sev = String.valueOf(val);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setFont(Theme.FONT_SMALL_BOLD);
                lbl.setBackground(row % 2 == 0 ? Theme.SURFACE : new Color(0x16, 0x1F, 0x33));
                if ("SECURITY".equals(sev)) lbl.setForeground(Theme.CRIMSON);
                else if ("WARN".equals(sev)) lbl.setForeground(Theme.WARN);
                else lbl.setForeground(Theme.EMERALD);
                return lbl;
            }
        });

        JScrollPane sp = new JScrollPane(auditTable);
        Theme.styleScrollPane(sp);

        applyBtn.addActionListener(e -> refreshAudit());
        auditSeverityCombo.addActionListener(e -> refreshAudit());
        auditSearchField.addActionListener(e -> refreshAudit());

        panel.add(top, BorderLayout.NORTH);
        panel.add(sp, BorderLayout.CENTER);
        return panel;
    }

    public void refreshAll() {
        refreshOverview();
        refreshCatalog();
        refreshUsers();
        refreshAudit();
    }

    private void refreshOverview() {
        try {
            double rev = bookingDAO.getTotalRevenue();
            int bookings = bookingDAO.getActiveBookingCount();
            int users = userDAO.countUsers();
            int movies = movieDAO.countActiveMovies();
            Map<String, Double> revByMovie = bookingDAO.getRevenueByMovie();
            List<BookingDAO.HallOccupancy> occ = bookingDAO.getHallOccupancyStats();

            kpiRevenueVal.setText(String.format(Locale.US, "BDT %.2f", rev));
            kpiBookingsVal.setText(String.valueOf(bookings));
            kpiUsersVal.setText(String.valueOf(users));
            kpiMoviesVal.setText(String.valueOf(movies));
            revenueChart.setData(revByMovie);

            hallOccupancyPanel.removeAll();
            for (BookingDAO.HallOccupancy h : occ) {
                JPanel row = new JPanel(new BorderLayout(0, 4));
                row.setOpaque(false);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
                row.setBorder(new EmptyBorder(4, 0, 8, 0));

                JLabel lbl = new JLabel(String.format(Locale.US, "%s  —  %d / %d seats (%.1f%%)",
                        h.getHallName(), h.getBookedSeats(), h.getTotalCapacity(), h.getOccupancyPercent()));
                lbl.setFont(Theme.FONT_BODY_BOLD);
                lbl.setForeground(Theme.TEXT_PRIMARY);

                JProgressBar bar = new JProgressBar(0, 100);
                bar.setValue((int) Math.round(h.getOccupancyPercent()));
                bar.setForeground(Theme.SKY);
                bar.setBackground(Theme.INPUT_BG);
                bar.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
                bar.setPreferredSize(new Dimension(200, 14));

                row.add(lbl, BorderLayout.NORTH);
                row.add(bar, BorderLayout.CENTER);
                hallOccupancyPanel.add(row);
            }
            hallOccupancyPanel.revalidate();
            hallOccupancyPanel.repaint();
        } catch (SQLException e) {
            System.err.println("Admin overview refresh error: " + e.getMessage());
        }
    }

    private void refreshCatalog() {
        try {
            List<Movie> movies = movieDAO.findAll();
            moviesModel.setRowCount(0);
            showMovieCombo.removeAllItems();
            for (Movie m : movies) {
                moviesModel.addRow(new Object[]{
                        m.getId(), m.getTitle(), m.getGenre(),
                        m.getDurationMin() + "m", m.getImdbRating(),
                        m.isActive() ? "ACTIVE" : "INACTIVE"
                });
                if (m.isActive()) {
                    showMovieCombo.addItem(m);
                }
            }

            List<Show> shows = showDAO.findAllShows();
            showsModel.setRowCount(0);
            for (Show s : shows) {
                showsModel.addRow(new Object[]{
                        s.getId(), s.getMovie().getTitle(), s.getHallName(),
                        s.getShowDate(), s.getShowTime(),
                        s.getRows() + "x" + s.getCols(),
                        s.getBookedCount() + "/" + s.getTotalCapacity()
                });
            }
        } catch (SQLException e) {
            System.err.println("Admin catalog refresh error: " + e.getMessage());
        }
    }

    private void refreshUsers() {
        try {
            List<User> users = userDAO.listAllUsers();
            usersModel.setRowCount(0);
            for (User u : users) {
                String lock = u.isCurrentlyLocked()
                        ? "LOCKED (" + u.remainingLockSeconds() + "s)"
                        : "Active";
                usersModel.addRow(new Object[]{
                        u.getId(), u.getUsername(), u.getFullName(), u.getEmail(),
                        u.getRole(), u.isPremium() ? "VIP Gold" : "Standard",
                        u.getFailedAttempts(), lock
                });
            }
        } catch (SQLException e) {
            System.err.println("Admin users refresh error: " + e.getMessage());
        }
    }

    private void refreshAudit() {
        try {
            String sev = auditSeverityCombo == null ? "ALL" : (String) auditSeverityCombo.getSelectedItem();
            String query = auditSearchField == null ? "" : auditSearchField.getText();
            List<AuditLog> logs = auditDAO.findRecent(150, sev, query);
            auditModel.setRowCount(0);
            for (AuditLog log : logs) {
                auditModel.addRow(new Object[]{
                        log.getId(), log.getCreatedAt(), log.getSeverity(),
                        log.getUsername(), log.getAction(), log.getDetails()
                });
            }
        } catch (SQLException e) {
            System.err.println("Admin audit refresh error: " + e.getMessage());
        }
    }
}
