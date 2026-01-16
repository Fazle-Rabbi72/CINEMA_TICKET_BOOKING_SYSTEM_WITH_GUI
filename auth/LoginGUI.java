package auth;

import ui.CinemaGUI;
import javax.swing.*;
import java.awt.*;

public class LoginGUI extends JFrame {

    public LoginGUI() {
        setTitle("Login / Signup");
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new GridLayout(6, 2, 10, 10));

        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();
        JCheckBox premium = new JCheckBox("Premium User");

        JButton loginBtn = new JButton("Login");
        JButton signupBtn = new JButton("Sign Up");

        add(new JLabel("Username:"));
        add(username);
        add(new JLabel("Password:"));
        add(password);
        add(new JLabel(""));
        add(premium);
        add(loginBtn);
        add(signupBtn);

        signupBtn.addActionListener(e -> {
            boolean ok = AuthManager.signup(
                    username.getText(),
                    new String(password.getPassword()),
                    premium.isSelected()
            );
            JOptionPane.showMessageDialog(this,
                    ok ? "Signup successful! Now login." : "User already exists!");
        });

        loginBtn.addActionListener(e -> {
            User user = AuthManager.login(
                    username.getText(),
                    new String(password.getPassword())
            );
            if (user != null) {
                dispose();
                new CinemaGUI(user).setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "Invalid username or password!");
            }
        });
    }
}
