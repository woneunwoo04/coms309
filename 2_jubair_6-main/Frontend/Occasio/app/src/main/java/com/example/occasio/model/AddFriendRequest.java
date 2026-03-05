package com.example.occasio.model;
import com.example.occasio.R;

public class AddFriendRequest {
    private String friendId;
    private String friendUsername;

    public AddFriendRequest() {
        // Default constructor
    }

    public AddFriendRequest(String friendId, String friendUsername) {
        this.friendId = friendId;
        this.friendUsername = friendUsername;
    }

    public String getFriendId() {
        return friendId;
    }

    public void setFriendId(String friendId) {
        this.friendId = friendId;
    }

    public String getFriendUsername() {
        return friendUsername;
    }

    public void setFriendUsername(String friendUsername) {
        this.friendUsername = friendUsername;
    }
}
