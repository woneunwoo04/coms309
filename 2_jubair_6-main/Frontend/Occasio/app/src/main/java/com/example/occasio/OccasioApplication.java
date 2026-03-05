package com.example.occasio;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.example.occasio.service.WebSocketService;
import com.example.occasio.utils.InAppNotificationHelper;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OccasioApplication extends Application {
    
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_USERNAME = "username";
    
    private AppCompatActivity currentActivity;
    private WebSocketService webSocketService;
    private RequestQueue requestQueue;
    private String currentUsername;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        requestQueue = Volley.newRequestQueue(this);
        
        // Register activity lifecycle callbacks to track current activity
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (activity instanceof AppCompatActivity) {
                    currentActivity = (AppCompatActivity) activity;
                    Log.d("OccasioApplication", "Current activity: " + activity.getClass().getSimpleName());
                }
            }
            
            @Override
            public void onActivityStarted(Activity activity) {
                if (activity instanceof AppCompatActivity) {
                    currentActivity = (AppCompatActivity) activity;
                }
            }
            
            @Override
            public void onActivityResumed(Activity activity) {
                if (activity instanceof AppCompatActivity) {
                    currentActivity = (AppCompatActivity) activity;
                    // Setup WebSocket when activity resumes
                    setupGlobalWebSocket();
                }
            }
            
            @Override
            public void onActivityPaused(Activity activity) {
                // Don't clear currentActivity here - keep it for notifications
            }
            
            @Override
            public void onActivityStopped(Activity activity) {
                // Don't clear currentActivity here
            }
            
            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                // Nothing to do
            }
            
            @Override
            public void onActivityDestroyed(Activity activity) {
                if (currentActivity == activity) {
                    currentActivity = null;
                }
            }
        });
    }
    
    /**
     * Setup global WebSocket listener to show popup notifications from any screen
     */
    private void setupGlobalWebSocket() {
        // Get current username from SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String username = sharedPreferences.getString(KEY_USERNAME, "");
        
        if (username == null || username.isEmpty()) {
            Log.w("OccasioApplication", "⚠️ Cannot setup WebSocket - username is empty");
            return;
        }
        
        // Only setup if username changed or not already connected
        if (username.equals(currentUsername) && webSocketService != null && webSocketService.isConnected()) {
            Log.d("OccasioApplication", "✅ WebSocket already connected for: " + username);
            return;
        }
        
        currentUsername = username;
        
        Log.d("OccasioApplication", "🔌 Setting up global WebSocket for user: " + username);
        
        webSocketService = WebSocketService.getInstance();
        
        // Set listener BEFORE connecting
        webSocketService.setListener(new WebSocketService.WebSocketListener() {
            @Override
            public void onMessage(String message) {
                Log.d("OccasioApplication", "📨 Raw WebSocket message received: " + message);
                
                // Show notification on current activity (if available)
                if (currentActivity != null && !currentActivity.isFinishing()) {
                    currentActivity.runOnUiThread(() -> {
                        try {
                            // Parse structured notification message
                            JSONObject json = new JSONObject(message);
                            String type = json.optString("type", "");
                            
                            Log.d("OccasioApplication", "📨 Parsed message type: " + type);
                            
                            if ("NOTIFICATION".equals(type)) {
                                Object dataObj = json.get("data");
                                
                                // Check if data is a structured object with event info
                                if (dataObj instanceof JSONObject) {
                                    JSONObject data = (JSONObject) dataObj;
                                    String notificationType = data.optString("notificationType", "");
                                    Long eventId = data.optLong("eventId", -1);
                                    String eventName = data.optString("eventName", "");
                                    String notificationMessage = data.optString("message", "");
                                    
                                    Log.d("OccasioApplication", "📨 Received WebSocket notification: type=" + notificationType + ", eventId=" + eventId + ", message=" + notificationMessage);
                                    
                                    // Show ALL notifications - check registration for event-based notifications
                                    if (eventId > 0 && ("ATTENDANCE_STARTED".equals(notificationType) || "EVENT_UPDATED".equals(notificationType))) {
                                        // For event-based notifications, check if user is registered
                                        Log.d("OccasioApplication", "✅ Processing event-based notification: " + notificationType);
                                        checkAndShowGlobalNotification(eventId, eventName, notificationMessage, notificationType);
                                    } else {
                                        // For all other notifications, show directly
                                        String title = getNotificationTitle(notificationType);
                                        Log.d("OccasioApplication", "✅ Showing notification directly: " + title);
                                        InAppNotificationHelper.showNotification(
                                            currentActivity,
                                            title,
                                            notificationMessage.isEmpty() ? "You have a new notification" : notificationMessage
                                        );
                                    }
                                } else {
                                    // Data is a string, not an object
                                    String dataStr = json.optString("data", "");
                                    Log.d("OccasioApplication", "📨 Received simple notification (string data): " + dataStr);
                                    // Show XML notification for simple notifications
                                    InAppNotificationHelper.showNotification(
                                        currentActivity,
                                        "🔔 Notification",
                                        dataStr.isEmpty() ? "You have a new notification" : dataStr
                                    );
                                }
                            } else {
                                Log.d("OccasioApplication", "📨 Received non-notification message: " + type);
                            }
                        } catch (JSONException e) {
                            Log.e("OccasioApplication", "❌ Error parsing WebSocket message: " + e.getMessage());
                            Log.e("OccasioApplication", "❌ Message was: " + message);
                            e.printStackTrace();
                            // Show XML notification even if parsing fails
                            InAppNotificationHelper.showNotification(
                                currentActivity,
                                "🔔 Notification",
                                "New notification received!"
                            );
                        }
                    });
                } else {
                    Log.w("OccasioApplication", "⚠️ Cannot show notification - no current activity");
                }
            }
            
            @Override
            public void onConnect() {
                Log.d("OccasioApplication", "✅ Global WebSocket connected for user: " + currentUsername);
            }
            
            @Override
            public void onDisconnect() {
                Log.d("OccasioApplication", "⚠️ Global WebSocket disconnected");
            }
            
            @Override
            public void onError(Exception e) {
                Log.e("OccasioApplication", "❌ Global WebSocket error: " + e.getMessage());
                e.printStackTrace();
                // Show error notification using XML layout on UI thread
                if (currentActivity != null && !currentActivity.isFinishing()) {
                    currentActivity.runOnUiThread(() -> {
                        try {
                            InAppNotificationHelper.showNotification(
                                currentActivity,
                                "⚠️ Connection Error",
                                "WebSocket error: " + (e.getMessage() != null ? e.getMessage() : "Unknown error")
                            );
                        } catch (Exception notifError) {
                            Log.e("OccasioApplication", "Error showing notification: " + notifError.getMessage());
                        }
                    });
                }
            }
        });
        
        // Connect to WebSocket server using ServerConfig
        String wsUrl = com.example.occasio.utils.ServerConfig.WS_NOTIFICATION_URL;
        Log.d("OccasioApplication", "🔌 WebSocket URL: " + wsUrl);
        Log.d("OccasioApplication", "🔌 Connecting with username: " + username);
        
        // Check if already connected
        if (webSocketService.isConnected()) {
            Log.d("OccasioApplication", "✅ WebSocket already connected");
        } else {
            // Connect to WebSocket
            webSocketService.connect(username, wsUrl);
        }
    }
    
    /**
     * Check if user is registered for event and show popup notification
     */
    private void checkAndShowGlobalNotification(Long eventId, String eventName, String message, String notificationType) {
        if (eventId == null || eventId <= 0) {
            Log.w("OccasioApplication", "Invalid eventId: " + eventId);
            return;
        }
        
        if (currentUsername == null || currentUsername.isEmpty()) {
            Log.w("OccasioApplication", "Cannot check registration - username is empty");
            return;
        }
        
        Log.d("OccasioApplication", "🔔 Received notification for event " + eventId + ", type: " + notificationType);
        
        // Check if user is registered for this event
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/events/user/" + currentUsername;
        
        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        boolean isRegistered = false;
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject event = response.getJSONObject(i);
                            if (event.getLong("id") == eventId) {
                                isRegistered = true;
                                break;
                            }
                        }
                        
                        Log.d("OccasioApplication", "User registration check: " + (isRegistered ? "REGISTERED" : "NOT REGISTERED") + " for event " + eventId);
                        
                        // Show notification for ALL users, regardless of registration
                        if (currentActivity != null && !currentActivity.isFinishing()) {
                            String title = getNotificationTitle(notificationType);
                            
                            Log.d("OccasioApplication", "✅ Showing popup notification: " + title);
                            
                            // Show popup notification using XML layout
                            currentActivity.runOnUiThread(() -> {
                                InAppNotificationHelper.showNotification(
                                    currentActivity,
                                    title,
                                    message.isEmpty() ? "You have a new notification" : message
                                );
                            });
                        }
                    } catch (JSONException e) {
                        Log.e("OccasioApplication", "❌ Error checking registration: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("OccasioApplication", "❌ Error checking registration: " + error.getMessage());
                    // On error, still try to show popup (optimistic approach)
                    if (currentActivity != null && !currentActivity.isFinishing()) {
                        String title = getNotificationTitle(notificationType);
                        
                        Log.d("OccasioApplication", "⚠️ Showing popup optimistically due to error");
                        currentActivity.runOnUiThread(() -> {
                            InAppNotificationHelper.showNotification(
                                currentActivity,
                                title,
                                message.isEmpty() ? "You have a new notification" : message
                            );
                        });
                    }
                }
            }
        );
        
        // Set timeout to 3 seconds - if it takes longer, show popup optimistically
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
            3000, // 3 seconds timeout
            0, // No retries
            com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));
        
        requestQueue.add(request);
    }
    
    /**
     * Get notification title based on notification type
     */
    private String getNotificationTitle(String notificationType) {
        if (notificationType == null || notificationType.isEmpty()) {
            return "🔔 Notification";
        }
        
        switch (notificationType) {
            case "ATTENDANCE_STARTED":
                return "📢 Attendance Started";
            case "EVENT_UPDATED":
                return "✏️ Event Updated";
            case "FRIEND_REQUEST":
                return "👋 Friend Request";
            case "FRIEND_ACCEPTED":
                return "✅ Friend Accepted";
            case "REWARD_EARNED":
                return "🎁 Reward Earned";
            case "MESSAGE_RECEIVED":
                return "💬 New Message";
            case "EVENT_CREATED":
                return "🎉 New Event";
            default:
                return "🔔 Notification";
        }
    }
    
    /**
     * Get the current activity (for external access if needed)
     */
    public AppCompatActivity getCurrentActivity() {
        return currentActivity;
    }
}

