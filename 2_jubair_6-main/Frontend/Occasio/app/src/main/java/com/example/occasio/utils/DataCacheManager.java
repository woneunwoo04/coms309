package com.example.occasio.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages offline data caching for the application
 * Stores data in SharedPreferences for offline access
 */
public class DataCacheManager {
    private static final String TAG = "DataCacheManager";
    private static final String PREFS_NAME = "OccasioDataCache";
    private static final String KEY_EVENTS = "cached_events";
    private static final String KEY_USER_INFO = "cached_user_info";
    private static final String KEY_FRIENDS = "cached_friends";
    private static final String KEY_GROUPS = "cached_groups";
    private static final String KEY_NOTIFICATIONS = "cached_notifications";
    private static final String KEY_MESSAGES = "cached_messages_";
    private static final String KEY_LAST_SYNC = "last_sync_time";
    
    private SharedPreferences prefs;
    private Gson gson;
    
    public DataCacheManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }
    
    // Save events list
    public void saveEvents(JSONArray events) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_EVENTS, events.toString());
            editor.putLong(KEY_LAST_SYNC, System.currentTimeMillis());
            editor.apply();
            Log.d(TAG, "Cached " + events.length() + " events");
        } catch (Exception e) {
            Log.e(TAG, "Error caching events", e);
        }
    }
    
    // Get cached events
    public JSONArray getCachedEvents() {
        try {
            String eventsJson = prefs.getString(KEY_EVENTS, null);
            if (eventsJson != null) {
                return new JSONArray(eventsJson);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached events", e);
        }
        return new JSONArray();
    }
    
    // Save user information
    public void saveUserInfo(JSONObject userInfo) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_USER_INFO, userInfo.toString());
            editor.apply();
            Log.d(TAG, "Cached user info");
        } catch (Exception e) {
            Log.e(TAG, "Error caching user info", e);
        }
    }
    
    // Get cached user info
    public JSONObject getCachedUserInfo() {
        try {
            String userJson = prefs.getString(KEY_USER_INFO, null);
            if (userJson != null) {
                return new JSONObject(userJson);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached user info", e);
        }
        return null;
    }
    
    // Save friends list
    public void saveFriends(JSONArray friends) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_FRIENDS, friends.toString());
            editor.apply();
            Log.d(TAG, "Cached " + friends.length() + " friends");
        } catch (Exception e) {
            Log.e(TAG, "Error caching friends", e);
        }
    }
    
    // Get cached friends
    public JSONArray getCachedFriends() {
        try {
            String friendsJson = prefs.getString(KEY_FRIENDS, null);
            if (friendsJson != null) {
                return new JSONArray(friendsJson);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached friends", e);
        }
        return new JSONArray();
    }
    
    // Save groups list
    public void saveGroups(JSONArray groups) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_GROUPS, groups.toString());
            editor.apply();
            Log.d(TAG, "Cached " + groups.length() + " groups");
        } catch (Exception e) {
            Log.e(TAG, "Error caching groups", e);
        }
    }
    
    // Get cached groups
    public JSONArray getCachedGroups() {
        try {
            String groupsJson = prefs.getString(KEY_GROUPS, null);
            if (groupsJson != null) {
                return new JSONArray(groupsJson);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached groups", e);
        }
        return new JSONArray();
    }
    
    // Save notifications
    public void saveNotifications(JSONArray notifications) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_NOTIFICATIONS, notifications.toString());
            editor.apply();
            Log.d(TAG, "Cached " + notifications.length() + " notifications");
        } catch (Exception e) {
            Log.e(TAG, "Error caching notifications", e);
        }
    }
    
    // Get cached notifications
    public JSONArray getCachedNotifications() {
        try {
            String notificationsJson = prefs.getString(KEY_NOTIFICATIONS, null);
            if (notificationsJson != null) {
                return new JSONArray(notificationsJson);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached notifications", e);
        }
        return new JSONArray();
    }
    
    // Save messages for a specific chat
    public void saveMessages(Long chatId, JSONArray messages) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_MESSAGES + chatId, messages.toString());
            editor.apply();
            Log.d(TAG, "Cached " + messages.length() + " messages for chat " + chatId);
        } catch (Exception e) {
            Log.e(TAG, "Error caching messages", e);
        }
    }
    
    // Get cached messages for a specific chat
    public JSONArray getCachedMessages(Long chatId) {
        try {
            String messagesJson = prefs.getString(KEY_MESSAGES + chatId, null);
            if (messagesJson != null) {
                return new JSONArray(messagesJson);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting cached messages", e);
        }
        return new JSONArray();
    }
    
    // Check if cache is expired (24 hours)
    public boolean isCacheExpired() {
        long lastSync = prefs.getLong(KEY_LAST_SYNC, 0);
        long currentTime = System.currentTimeMillis();
        long diff = currentTime - lastSync;
        return diff > (24 * 60 * 60 * 1000); // 24 hours in milliseconds
    }
    
    // Clear all cached data
    public void clearCache() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        Log.d(TAG, "Cleared all cached data");
    }
    
    // Get last sync time
    public long getLastSyncTime() {
        return prefs.getLong(KEY_LAST_SYNC, 0);
    }
    
    // Update last sync time
    public void updateLastSyncTime() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_LAST_SYNC, System.currentTimeMillis());
        editor.apply();
    }
}
