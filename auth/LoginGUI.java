package auth;

import db.DatabaseConnection;
import security.InputValidator;
import ui.CinemaGUI;
import ui.Components;
import ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Arrays;

/**
 * Luxury split-pane authentication screen with Sign In, Sign Up,
 * live password strength meter, MySQL health pill, and quick demo autofill.
 */
public class LoginGUI extends JFrame {

    private final CardLayout formCards = new CardLayout();
    private final JPanel formCardContainer = new JPanel(formCards);

    private Components.DarkTextField loginUserField;
    private Components.DarkPasswordField loginPassField;
    private JLabel loginStatusLabel;
    private Components.GlowButton signInTabBtn;
    private Components.GlowButton signUpTabBtn;

    private Components.DarkTextField signupNameField;
    private Components.DarkTextField signupUserField;
    private Components.DarkTextField signupEmailField;
    private Components.DarkTextField signupPhoneField;
    private Components.DarkPasswordField signupPassField;
    private JLabel signupStatusLabel;
    private final JPanel[] strengthBars = new JPanel[4];
    private JLabel strengthTextLabel;

    public LoginGUI() {
        Theme.configureUIDefaults();
        setTitle("CineVault  |  Secure Cinema Booking");
        setSize(980, 620);
        setMinimumSize(new Dimension(900, 580));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.BG_DARK);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        gbc.gridx = 0;
        gbc.weightx = 0.43;
        root.add(buildHeroPanel(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.57;
        root.add(buildAuthPanel(), gbc);

        setContentPane(root);
    }

    private JPanel buildHeroPanel() {
        Components.RoundedPanel hero = new Components.RoundedPanel(
                0, new Color(0x10, 0x16, 0x26), new Color(0x0B, 0x0F, 0x19), null);
        hero.setLayout(new BorderLayout(0, 18));
        hero.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
                new EmptyBorder(28, 28, 24, 28)
        ));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        Components.BadgeLabel editionBadge = new Components.BadgeLabel(
                "JDBC  \u2022  HIGH SECURITY EDITION", Theme.GOLD_SOFT, Theme.GOLD);
        editionBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel brand = new JLabel("CINEVAULT");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 34));
        brand.setForeground(Theme.TEXT_PRIMARY);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tagline = new JLabel("<html>Luxury Cinema Booking & Tamper-Proof<br>Digital E-Ticket Platform</html>");
        tagline.setFont(Theme.FONT_BODY);
        tagline.setForeground(Theme.TEXT_MUTED);
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        top.add(editionBadge);
        top.add(Box.createVerticalStrut(12));
        top.add(brand);
        top.add(Box.createVerticalStrut(6));
        top.add(tagline);

        // Security highlights card
        Components.RoundedPanel secCard = new Components.RoundedPanel(14, Theme.CARD, Theme.BORDER);
        secCard.setLayout(new BoxLayout(secCard, BoxLayout.Y_AXIS));
        secCard.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel secTitle = new JLabel("SECURITY ARCHITECTURE");
        secTitle.setFont(Theme.FONT_SMALL_BOLD);
        secTitle.setForeground(Theme.GOLD);
        secCard.add(secTitle);
        secCard.add(Box.createVerticalStrut(10));

        String[] bullets = {
                "\u2714  PBKDF2-HMAC-SHA256 Salted Password Hashing",
                "\u2714  100% PreparedStatement SQL Injection Guard",
                "\u2714  ACID Transaction Double-Booking Prevention",
                "\u2714  HMAC-SHA256 Signed Verifiable E-Tickets",
                "\u2714  Brute-Force Lockout & Security Audit Trail"
        };
        for (String b : bullets) {
            JLabel lbl = new JLabel(b);
            lbl.setFont(Theme.FONT_SMALL);
            lbl.setForeground(Theme.TEXT_PRIMARY);
            lbl.setBorder(new EmptyBorder(3, 0, 3, 0));
            secCard.add(lbl);
        }

        // Bottom: DB health pill + demo quick fill
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        DatabaseConnection.ConnectionStatus dbStatus = DatabaseConnection.testConnection();
        Components.BadgeLabel dbPill = new Components.BadgeLabel(
                dbStatus.isConnected()
                        ? "\u25CF  MySQL Connected: " + dbStatus.getServerVersion()
                        : "\u25CF  MySQL Disconnected",
                dbStatus.isConnected() ? Theme.EMERALD_SOFT : Theme.DANGER_SOFT,
                dbStatus.isConnected() ? Theme.EMERALD : Theme.DANGER
        );
        dbPill.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel demoTitle = new JLabel("QUICK DEMO ACCOUNTS (CLICK TO FILL)");
        demoTitle.setFont(Theme.FONT_SMALL_BOLD);
        demoTitle.setForeground(Theme.TEXT_MUTED);
        demoTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel demoRow = new JPanel(new GridLayout(1, 3, 8, 0));
        demoRow.setOpaque(false);
        demoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        demoRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        demoRow.add(makeDemoBtn("Admin", "admin", "Admin@1234"));
        demoRow.add(makeDemoBtn("VIP User", "vipuser", "Vip@12345"));
        demoRow.add(makeDemoBtn("Regular", "guest", "Guest@123"));

        bottom.add(dbPill);
        bottom.add(Box.createVerticalStrut(14));
        bottom.add(demoTitle);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(demoRow);

        hero.add(top, BorderLayout.NORTH);
        hero.add(secCard, BorderLayout.CENTER);
        hero.add(bottom, BorderLayout.SOUTH);
        return hero;
    }

    private Components.GlowButton makeDemoBtn(String label, String user, String pass) {
        Components.GlowButton b = new Components.GlowButton(label, Components.GlowButton.Variant.GHOST);
        b.setFont(Theme.FONT_SMALL_BOLD);
        b.setBorder(new EmptyBorder(6, 8, 6, 8));
        b.setToolTipText("Fill credentials: " + user + " / " + pass);
        b.addActionListener(e -> {
            switchMode(true);
            loginUserField.setText(user);
            loginPassField.setText(pass);
            loginStatusLabel.setText("Demo credentials loaded for '" + user + "'. Press Sign In.");
            loginStatusLabel.setForeground(Theme.SKY);
        });
        return b;
    }

    private JPanel buildAuthPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG_DARK);
        wrapper.setBorder(new EmptyBorder(28, 42, 28, 42));

        // Top mode toggle
        JPanel toggleBar = new JPanel(new GridLayout(1, 2, 10, 0));
        toggleBar.setOpaque(false);
        toggleBar.setPreferredSize(new Dimension(320, 42));

        signInTabBtn = new Components.GlowButton("Sign In", Components.GlowButton.Variant.GOLD);
        signUpTabBtn = new Components.GlowButton("Create Account", Components.GlowButton.Variant.GHOST);
        signInTabBtn.addActionListener(e -> switchMode(true));
        signUpTabBtn.addActionListener(e -> switchMode(false));

        toggleBar.add(signInTabBtn);
        toggleBar.add(signUpTabBtn);

        formCardContainer.setOpaque(false);
        formCardContainer.add(buildSignInForm(), "LOGIN");
        formCardContainer.add(buildSignUpForm(), "SIGNUP");

        wrapper.add(toggleBar, BorderLayout.NORTH);
        wrapper.add(formCardContainer, BorderLayout.CENTER);
        return wrapper;
    }

    private void switchMode(boolean login) {
        formCards.show(formCardContainer, login ? "LOGIN" : "SIGNUP");
        signInTabBtn.setVariant(login ? Components.GlowButton.Variant.GOLD : Components.GlowButton.Variant.GHOST);
        signUpTabBtn.setVariant(login ? Components.GlowButton.Variant.GHOST : Components.GlowButton.Variant.GOLD);
        if (login && loginUserField != null) {
            SwingUtilities.invokeLater(() -> loginUserField.requestFocusInWindow());
        }
    }

    private JPanel buildSignInForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(410, 360));

        JLabel title = new JLabel("Welcome Back");
        title.setFont(Theme.FONT_HERO);
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sub = new JLabel("Sign in to reserve IMAX & VIP Lounge seats");
        sub.setFont(Theme.FONT_BODY);
        sub.setForeground(Theme.TEXT_MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        loginUserField = new Components.DarkTextField("Enter username");
        loginUserField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        loginUserField.setAlignmentX(Component.LEFT_ALIGNMENT);

        loginPassField = new Components.DarkPasswordField("Enter password");
        loginPassField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        loginPassField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox showPwd = new JCheckBox("Show password");
        showPwd.setOpaque(false);
        showPwd.setForeground(Theme.TEXT_MUTED);
        showPwd.setFont(Theme.FONT_SMALL);
        showPwd.setAlignmentX(Component.LEFT_ALIGNMENT);
        char defaultEcho = loginPassField.getEchoChar();
        showPwd.addActionListener(e -> loginPassField.setEchoChar(showPwd.isSelected() ? (char) 0 : defaultEcho));

        loginStatusLabel = new JLabel(" ");
        loginStatusLabel.setFont(Theme.FONT_SMALL_BOLD);
        loginStatusLabel.setForeground(Theme.DANGER);
        loginStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        Components.GlowButton loginBtn = new Components.GlowButton("Sign In to CineVault", Components.GlowButton.Variant.PRIMARY);
        loginBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        loginBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        Runnable performLogin = () -> {
            char[] pwd = loginPassField.getPassword();
            AuthManager.AuthResult res = AuthManager.login(loginUserField.getText(), pwd);
            if (res.isSuccess() && res.getUser() != null) {
                dispose();
                new CinemaGUI(res.getUser()).setVisible(true);
            } else {
                loginStatusLabel.setForeground(Theme.DANGER);
                loginStatusLabel.setText("<html>" + res.getMessage() + "</html>");
            }
        };

        loginBtn.addActionListener(e -> performLogin.run());
        loginPassField.addActionListener(e -> performLogin.run());
        loginUserField.addActionListener(e -> loginPassField.requestFocusInWindow());

        form.add(title);
        form.add(Box.createVerticalStrut(4));
        form.add(sub);
        form.add(Box.createVerticalStrut(24));
        form.add(makeFieldLabel("Username"));
        form.add(Box.createVerticalStrut(6));
        form.add(loginUserField);
        form.add(Box.createVerticalStrut(14));
        form.add(makeFieldLabel("Password"));
        form.add(Box.createVerticalStrut(6));
        form.add(loginPassField);
        form.add(Box.createVerticalStrut(8));
        form.add(showPwd);
        form.add(Box.createVerticalStrut(10));
        form.add(loginStatusLabel);
        form.add(Box.createVerticalStrut(14));
        form.add(loginBtn);

        panel.add(form);
        return panel;
    }

    private JPanel buildSignUpForm() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setOpaque(false);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(420, 460));

        JLabel title = new JLabel("Create CineVault Account");
        title.setFont(Theme.FONT_H1);
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        signupNameField = new Components.DarkTextField("Full Name (e.g., Tanvir Ahmed)");
        signupUserField = new Components.DarkTextField("Username (letters, numbers, _)");
        signupEmailField = new Components.DarkTextField("Email (e.g., tanvir@mail.com)");
        signupPhoneField = new Components.DarkTextField("Phone (e.g., +8801712345678)");
        signupPassField = new Components.DarkPasswordField("Strong password (min 8 chars, mixed types)");

        for (JComponent f : new JComponent[]{signupNameField, signupUserField, signupEmailField, signupPhoneField, signupPassField}) {
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            f.setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        // Password strength meter
        JPanel meterRow = new JPanel(new GridLayout(1, 4, 6, 0));
        meterRow.setOpaque(false);
        meterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        meterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int i = 0; i < 4; i++) {
            strengthBars[i] = new JPanel();
            strengthBars[i].setBackground(Theme.BORDER);
            meterRow.add(strengthBars[i]);
        }

        strengthTextLabel = new JLabel("Password Strength: Empty");
        strengthTextLabel.setFont(Theme.FONT_SMALL);
        strengthTextLabel.setForeground(Theme.TEXT_MUTED);
        strengthTextLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        signupPassField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateStrengthMeter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateStrengthMeter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateStrengthMeter();
            }
        });

        signupStatusLabel = new JLabel(" ");
        signupStatusLabel.setFont(Theme.FONT_SMALL_BOLD);
        signupStatusLabel.setForeground(Theme.DANGER);
        signupStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        Components.GlowButton createBtn = new Components.GlowButton(
                "Create Secure Account", Components.GlowButton.Variant.SUCCESS);
        createBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        createBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        Runnable performSignup = () -> {
            char[] pwd = signupPassField.getPassword();
            AuthManager.AuthResult res = AuthManager.signup(
                    signupNameField.getText(),
                    signupUserField.getText(),
                    signupEmailField.getText(),
                    signupPhoneField.getText(),
                    pwd
            );
            if (res.isSuccess()) {
                String createdUser = signupUserField.getText().trim();
                signupNameField.setText("");
                signupUserField.setText("");
                signupEmailField.setText("");
                signupPhoneField.setText("");
                signupPassField.setText("");
                switchMode(true);
                loginUserField.setText(createdUser);
                loginPassField.setText("");
                loginStatusLabel.setForeground(Theme.EMERALD);
                loginStatusLabel.setText(res.getMessage());
            } else {
                signupStatusLabel.setForeground(Theme.DANGER);
                signupStatusLabel.setText("<html>" + res.getMessage() + "</html>");
            }
        };

        createBtn.addActionListener(e -> performSignup.run());
        signupPassField.addActionListener(e -> performSignup.run());

        form.add(title);
        form.add(Box.createVerticalStrut(14));
        form.add(makeFieldLabel("Full Name"));
        form.add(Box.createVerticalStrut(4));
        form.add(signupNameField);
        form.add(Box.createVerticalStrut(8));
        form.add(makeFieldLabel("Username"));
        form.add(Box.createVerticalStrut(4));
        form.add(signupUserField);
        form.add(Box.createVerticalStrut(8));
        form.add(makeFieldLabel("Email Address"));
        form.add(Box.createVerticalStrut(4));
        form.add(signupEmailField);
        form.add(Box.createVerticalStrut(8));
        form.add(makeFieldLabel("Phone Number"));
        form.add(Box.createVerticalStrut(4));
        form.add(signupPhoneField);
        form.add(Box.createVerticalStrut(8));
        form.add(makeFieldLabel("Password"));
        form.add(Box.createVerticalStrut(4));
        form.add(signupPassField);
        form.add(Box.createVerticalStrut(6));
        form.add(meterRow);
        form.add(Box.createVerticalStrut(4));
        form.add(strengthTextLabel);
        form.add(Box.createVerticalStrut(8));
        form.add(signupStatusLabel);
        form.add(Box.createVerticalStrut(10));
        form.add(createBtn);

        outer.add(form);
        return outer;
    }

    private void updateStrengthMeter() {
        char[] pwd = signupPassField.getPassword();
        try {
            InputValidator.StrengthResult res = InputValidator.passwordStrength(pwd);
            int score = res.getScore();
            Color activeColor = score <= 1 ? Theme.DANGER
                    : (score == 2 ? Theme.WARN : (score == 3 ? Theme.SKY : Theme.EMERALD));
            for (int i = 0; i < 4; i++) {
                strengthBars[i].setBackground(i < score ? activeColor : Theme.BORDER);
            }
            strengthTextLabel.setText("Password Strength: " + res.getLabel());
            strengthTextLabel.setForeground(score == 0 ? Theme.TEXT_MUTED : activeColor);
        } finally {
            Arrays.fill(pwd, '\0');
        }
    }

    private static JLabel makeFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(Theme.FONT_SMALL_BOLD);
        lbl.setForeground(Theme.TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }
}
