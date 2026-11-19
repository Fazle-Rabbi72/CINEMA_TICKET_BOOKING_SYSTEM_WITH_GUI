package manager;

/**
 * Live seat state inside an auditorium grid.
 */
public class SeatInfo {
    private final int row;
    private final int col;
    private final String label;
    private final String tier;
    private final double price;
    private boolean booked;
    private int bookedByUserId;
    private String bookedByUsername;

    public SeatInfo(int row, int col, String label, String tier, double price,
                    boolean booked, int bookedByUserId, String bookedByUsername) {
        this.row = row;
        this.col = col;
        this.label = label;
        this.tier = tier;
        this.price = price;
        this.booked = booked;
        this.bookedByUserId = bookedByUserId;
        this.bookedByUsername = bookedByUsername;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public String getLabel() {
        return label;
    }

    public String getTier() {
        return tier;
    }

    public double getPrice() {
        return price;
    }

    public boolean isBooked() {
        return booked;
    }

    public void setBooked(boolean booked) {
        this.booked = booked;
    }

    public int getBookedByUserId() {
        return bookedByUserId;
    }

    public void setBookedByUserId(int bookedByUserId) {
        this.bookedByUserId = bookedByUserId;
    }

    public String getBookedByUsername() {
        return bookedByUsername;
    }

    public void setBookedByUsername(String bookedByUsername) {
        this.bookedByUsername = bookedByUsername;
    }
}
