package com.example.occasio;
import com.example.occasio.R;

import android.content.Context;
import android.content.SharedPreferences;

public class UserPreferences {
    private static final String PREF_NAME = "user_preferences";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_FIRST_NAME = "first_name";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_USER_CLASS = "user_class";
    private static final String KEY_BIO = "bio";
    private static final String KEY_PROFILE_PICTURE = "profile_picture";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_REMEMBER_ME = "remember_me";
    
    private SharedPreferences sharedPreferences;
    
    public UserPreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public void saveUser(User user) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USERNAME, user.getUsername());
        editor.putString(KEY_EMAIL, user.getEmail());
        editor.putString(KEY_FIRST_NAME, user.getFirstName());
        editor.putString(KEY_LAST_NAME, user.getLastName());
        editor.putString(KEY_PHONE, user.getPhone());
        editor.putString(KEY_USER_CLASS, user.getUserClass());
        editor.putString(KEY_BIO, user.getBio());
        editor.putString(KEY_PROFILE_PICTURE, user.getProfilePicturePath());
        editor.apply();
    }
    
    public User getUser() {
        String username = sharedPreferences.getString(KEY_USERNAME, "");
        String email = sharedPreferences.getString(KEY_EMAIL, "");
        String firstName = sharedPreferences.getString(KEY_FIRST_NAME, "");
        String lastName = sharedPreferences.getString(KEY_LAST_NAME, "");
        String phone = sharedPreferences.getString(KEY_PHONE, "");
        String userClass = sharedPreferences.getString(KEY_USER_CLASS, "");
        String bio = sharedPreferences.getString(KEY_BIO, "");
        String profilePicture = sharedPreferences.getString(KEY_PROFILE_PICTURE, "");
        
        if (username.isEmpty()) {
            return null;
        }
        
        return new User(username, email, firstName, lastName, phone, userClass, bio, profilePicture);
    }
    
    public void saveAuthToken(String token) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_AUTH_TOKEN, token);
        editor.apply();
    }
    
    public String getAuthToken() {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, "");
    }
    
    public void setLoggedIn(boolean isLoggedIn) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, isLoggedIn);
        editor.apply();
    }
    
    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }
    
    public void setRememberMe(boolean rememberMe) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_REMEMBER_ME, rememberMe);
        editor.apply();
    }
    
    public boolean isRememberMe() {
        return sharedPreferences.getBoolean(KEY_REMEMBER_ME, false);
    }
    
    public void clearAll() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }
    
    public void clearUserData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_USERNAME);
        editor.remove(KEY_EMAIL);
        editor.remove(KEY_FIRST_NAME);
        editor.remove(KEY_LAST_NAME);
        editor.remove(KEY_PHONE);
        editor.remove(KEY_USER_CLASS);
        editor.remove(KEY_BIO);
        editor.remove(KEY_PROFILE_PICTURE);
        editor.remove(KEY_AUTH_TOKEN);
        editor.remove(KEY_IS_LOGGED_IN);
        editor.apply();
    }
}
