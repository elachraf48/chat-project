package com.javachat.mobile.auth;

/**
 * Holds the authenticated session in memory.
 * No persistent storage — session lives only while the app process is alive.
 */
public class AuthenticationManager {

    private static AuthenticationManager instance;

    private String username;
    private boolean loggedIn = false;

    private AuthenticationManager() {}

    public static synchronized AuthenticationManager getInstance() {
        if (instance == null) {
            instance = new AuthenticationManager();
        }
        return instance;
    }

    public void setLoggedIn(String username) {
        this.username = username;
        this.loggedIn = true;
    }

    public void logout() {
        this.username = null;
        this.loggedIn = false;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public String getUsername() {
        return username;
    }
}
