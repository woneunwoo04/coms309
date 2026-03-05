package com.example.occasio.model;
import com.example.occasio.R;

public class LoginRequest {
    private String email;
    private String password;
    private String username; // For username-based login

    public LoginRequest() {
        // Default constructor
    }

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public LoginRequest(String username, String password, boolean isUsername) {
        if (isUsername) {
            this.username = username;
        } else {
            this.email = username;
        }
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
