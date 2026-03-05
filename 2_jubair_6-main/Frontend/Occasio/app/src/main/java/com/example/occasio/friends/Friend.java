package com.example.occasio.friends;
import com.example.occasio.R;

public class Friend {
    private int id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String profileImageUrl;
    private boolean isFriend;

    public Friend(int id, String username, String email, String firstName, String lastName, String profileImageUrl, boolean isFriend) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.profileImageUrl = profileImageUrl;
        this.isFriend = isFriend;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public boolean isFriend() { return isFriend; }

    public void setId(int id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setEmail(String email) { this.email = email; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
    public void setFriend(boolean friend) { isFriend = friend; }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
