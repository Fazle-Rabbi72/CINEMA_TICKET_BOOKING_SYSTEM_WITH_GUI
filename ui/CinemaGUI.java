package ui;

import auth.User;
import auth.LoginGUI;
import entity.*;
import manager.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class CinemaGUI extends JFrame {

    private User user;
    private ArrayList<Show> shows = new ArrayList<>();
    private Show currentShow;
    private JPanel seatPanel = new JPanel();
    private JLabel info = new JLabel();

    public CinemaGUI(User user) {
        this.user = user;
        setTitle("Cinema Booking - " + user.getUsername());
        setSize(900, 650);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        
        shows.add(new Show(new Movie("Avengers", 180), "10:00 AM", 5, 5));
        shows.add(new Show(new Movie("Inception", 150), "1:00 PM", 4, 4));
        shows.add(new Show(new Movie("Interstellar", 169), "3:30 PM", 6, 6));
        shows.add(new Show(new Movie("Titanic", 195), "6:00 PM", 5, 6));
        shows.add(new Show(new Movie("Joker", 122), "8:30 PM", 4, 5));
        shows.add(new Show(new Movie("The Dark Knight", 152), "11:00 AM", 6, 5));
        shows.add(new Show(new Movie("Doctor Strange", 115), "12:45 PM", 4, 4));
        shows.add(new Show(new Movie("Spider-Man: No Way Home", 148), "2:15 PM", 6, 6));
        shows.add(new Show(new Movie("Iron Man", 126), "4:45 PM", 5, 5));
        shows.add(new Show(new Movie("Black Panther", 134), "7:15 PM", 5, 5));
        shows.add(new Show(new Movie("Avatar", 162), "9:45 PM", 6, 6));
        shows.add(new Show(new Movie("Fast & Furious 7", 137), "10:30 AM", 5, 4));
        shows.add(new Show(new Movie("John Wick", 101), "1:30 PM", 4, 4));
        shows.add(new Show(new Movie("Mission Impossible", 131), "5:00 PM", 5, 5));
        shows.add(new Show(new Movie("The Matrix", 136), "7:45 PM", 5, 5));
        shows.add(new Show(new Movie("Gladiator", 155), "9:00 PM", 6, 5));
        currentShow = shows.get(0);

        
        JComboBox<String> box = new JComboBox<>();
        for (Show s : shows)
            box.addItem(s.movie.name + " - " + s.time);

        box.addActionListener(e -> {
            currentShow = shows.get(box.getSelectedIndex());
            drawSeats();
        });

       
        JButton cancelBtn = new JButton("Cancel My Ticket");
        JButton logoutBtn = new JButton("Logout");
        JButton exitBtn = new JButton("Exit");

        cancelBtn.addActionListener(e -> cancelTicket());

        logoutBtn.addActionListener(e -> {
            dispose(); 
            new LoginGUI().setVisible(true); 
        });

        exitBtn.addActionListener(e -> System.exit(0));

       
        JPanel top = new JPanel();
        top.add(new JLabel("Select Show:"));
        top.add(box);
        top.add(cancelBtn);
        top.add(logoutBtn);
        top.add(exitBtn);

        add(top, BorderLayout.NORTH);
        add(seatPanel, BorderLayout.CENTER);
        add(info, BorderLayout.SOUTH);

        drawSeats();
    }

    private void drawSeats() {
        seatPanel.removeAll();
        seatPanel.setLayout(new GridLayout(
                currentShow.seats.length,
                currentShow.seats[0].length, 5, 5));

        for (int i = 0; i < currentShow.seats.length; i++) {
            for (int j = 0; j < currentShow.seats[i].length; j++) {

                JButton btn = new JButton(i + "-" + j);
                btn.setBackground(
                        currentShow.seats[i][j] == null ? Color.GREEN : Color.RED
                );
                btn.setEnabled(currentShow.seats[i][j] == null);

                int r = i, c = j;
                btn.addActionListener(e -> {
                    currentShow.seats[r][c] = user.getUsername();

                    Customer customer = user.isPremium()
                            ? new PremiumCustomer(user.getUsername())
                            : new RegularCustomer(user.getUsername());

                    new Ticket(customer, 1).bookTicket();
                    drawSeats();
                });

                seatPanel.add(btn);
            }
        }

        
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("Logged in as: ").append(user.getUsername()).append("<br>");
        sb.append("Available seats: ").append(currentShow.availableSeats()).append("<br><br>");
        sb.append("Booked Seats:<br>");

        for (int i = 0; i < currentShow.seats.length; i++) {
            for (int j = 0; j < currentShow.seats[i].length; j++) {
                if (currentShow.seats[i][j] != null) {
                    sb.append("Seat ")
                      .append(i).append("-").append(j)
                      .append(" → ")
                      .append(currentShow.seats[i][j])
                      .append("<br>");
                }
            }
        }
        sb.append("</html>");

        info.setText(sb.toString());
        revalidate();
        repaint();
    }

    private void cancelTicket() {
        String seat = JOptionPane.showInputDialog(this, "Enter seat (row-col):");
        if (seat == null) return;

        String[] p = seat.split("-");
        int r = Integer.parseInt(p[0]);
        int c = Integer.parseInt(p[1]);

        if (currentShow.seats[r][c] == null) {
            JOptionPane.showMessageDialog(this, "Seat not booked!");
        } else if (!currentShow.seats[r][c].equals(user.getUsername())) {
            JOptionPane.showMessageDialog(this, "You can cancel only YOUR ticket!");
        } else {
            currentShow.seats[r][c] = null;
            JOptionPane.showMessageDialog(this, "Ticket cancelled!");
            drawSeats();
        }
    }
}
