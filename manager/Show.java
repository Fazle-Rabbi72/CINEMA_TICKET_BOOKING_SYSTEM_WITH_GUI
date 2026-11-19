package manager;

import java.time.LocalDate;

/**
 * Represents a scheduled screening with auditorium dimensions and tiered seat pricing.
 */
public class Show {
    private int id;
    private Movie movie;
    private String hallName;
    private LocalDate showDate;
    private String showTime;
    private int rows;
    private int cols;
    private double basePrice;
    private double goldPrice;
    private double vipPrice;
    private boolean active;
    private int bookedCount;

    public Show(Movie movie, String time, int r, int c) {
        this(0, movie, "Hall 1 - IMAX 3D", LocalDate.now(), time, r, c, 350.0, 500.0, 750.0, true, 0);
    }

    public Show(int id, Movie movie, String hallName, LocalDate showDate, String showTime,
                int rows, int cols, double basePrice, double goldPrice, double vipPrice,
                boolean active, int bookedCount) {
        this.id = id;
        this.movie = movie;
        this.hallName = hallName;
        this.showDate = showDate;
        this.showTime = showTime;
        this.rows = rows;
        this.cols = cols;
        this.basePrice = basePrice;
        this.goldPrice = goldPrice;
        this.vipPrice = vipPrice;
        this.active = active;
        this.bookedCount = bookedCount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Movie getMovie() {
        return movie;
    }

    public void setMovie(Movie movie) {
        this.movie = movie;
    }

    public String getHallName() {
        return hallName;
    }

    public LocalDate getShowDate() {
        return showDate;
    }

    public String getShowTime() {
        return showTime;
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public double getGoldPrice() {
        return goldPrice;
    }

    public double getVipPrice() {
        return vipPrice;
    }

    public boolean isActive() {
        return active;
    }

    public int getBookedCount() {
        return bookedCount;
    }

    public void setBookedCount(int bookedCount) {
        this.bookedCount = bookedCount;
    }

    public int getTotalCapacity() {
        return rows * cols;
    }

    public int availableSeats() {
        return Math.max(0, getTotalCapacity() - bookedCount);
    }

    /**
     * Rows 0..1 are front Standard seats, rows 2..3 are Gold seats, rows 4+ are back VIP recliners.
     * Adapts gracefully when an admin creates an auditorium with fewer than 5 rows.
     */
    public String getSeatTier(int row) {
        if (rows <= 2) {
            return row == 0 ? "STANDARD" : "GOLD";
        }
        if (rows <= 4) {
            if (row == 0) return "STANDARD";
            if (row < rows - 1) return "GOLD";
            return "VIP";
        }
        if (row <= 1) return "STANDARD";
        if (row <= 3) return "GOLD";
        return "VIP";
    }

    public double getSeatPrice(int row) {
        String tier = getSeatTier(row);
        if ("VIP".equals(tier)) return vipPrice;
        if ("GOLD".equals(tier)) return goldPrice;
        return basePrice;
    }

    public static String seatLabel(int row, int col) {
        char rowLetter = (char) ('A' + row);
        return rowLetter + String.valueOf(col + 1);
    }
}
