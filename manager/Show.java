package manager;

public class Show {
    public Movie movie;
    public String time;
    public String[][] seats; // null = empty, username = booked

    public Show(Movie movie, String time, int r, int c) {
        this.movie = movie;
        this.time = time;
        seats = new String[r][c];
    }

    public int availableSeats() {
        int count = 0;
        for (String[] row : seats)
            for (String s : row)
                if (s == null) count++;
        return count;
    }
}
