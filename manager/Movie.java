package manager;

/**
 * Represents a movie in the CineVault catalog.
 */
public class Movie {
    private int id;
    private String title;
    private String genre;
    private String ageRating;
    private String language;
    private int durationMin;
    private double imdbRating;
    private String synopsis;
    private String accentColor;
    private boolean active;

    public Movie(String name, int duration) {
        this(0, name, "Drama", "PG-13", "English", duration, 8.0, "", "#E50914", true);
    }

    public Movie(int id, String title, String genre, String ageRating, String language,
                 int durationMin, double imdbRating, String synopsis, String accentColor, boolean active) {
        this.id = id;
        this.title = title;
        this.genre = genre;
        this.ageRating = ageRating;
        this.language = language;
        this.durationMin = durationMin;
        this.imdbRating = imdbRating;
        this.synopsis = synopsis;
        this.accentColor = accentColor == null || accentColor.isBlank() ? "#E50914" : accentColor;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getName() {
        return title;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getAgeRating() {
        return ageRating;
    }

    public void setAgeRating(String ageRating) {
        this.ageRating = ageRating;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(int durationMin) {
        this.durationMin = durationMin;
    }

    public int getDuration() {
        return durationMin;
    }

    public double getImdbRating() {
        return imdbRating;
    }

    public void setImdbRating(double imdbRating) {
        this.imdbRating = imdbRating;
    }

    public String getSynopsis() {
        return synopsis;
    }

    public void setSynopsis(String synopsis) {
        this.synopsis = synopsis;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(String accentColor) {
        this.accentColor = accentColor;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return title;
    }
}
