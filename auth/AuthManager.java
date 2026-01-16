package auth;

import java.util.ArrayList;

public class AuthManager {

    private static ArrayList<User> users = new ArrayList<>();

    public static boolean signup(String username, String password, boolean premium) {
        for (User u : users) {
            if (u.getUsername().equals(username)) {
                return false;
            }
        }
        users.add(new User(username, password, premium));
        return true;
    }

    public static User login(String username, String password) {
        for (User u : users) {
            if (u.getUsername().equals(username) &&
                u.getPassword().equals(password)) {
                return u;
            }
        }
        return null;
    }
}
