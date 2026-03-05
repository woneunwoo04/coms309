package com.example.occasio.base;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.occasio.R;
import com.example.occasio.events.AllEventsActivity;
import com.example.occasio.messaging.ChatListActivity;
import com.example.occasio.messaging.MessagingActivity;
import com.example.occasio.notifications.NotificationInboxActivity;
import com.example.occasio.profile.EditProfileActivity;
import com.example.occasio.service.WebSocketService;
import com.example.occasio.utils.InAppNotificationHelper;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class BaseNavigationActivity extends AppCompatActivity {
    
    protected LinearLayout navEvents;
    protected LinearLayout navMessages;
    protected LinearLayout navNotifications;
    protected LinearLayout navProfile;
    protected TextView navNotificationBadge;
    protected String currentUsername;
    protected SharedPreferences sharedPreferences;
    protected Long userId;
    protected RequestQueue requestQueue;
    protected WebSocketService webSocketService;
    protected com.example.occasio.service.ChatWebSocketService chatWebSocketService;
    protected com.example.occasio.services.ChatApiService chatApiService;
    
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_USERNAME = "username";
    protected static final String BASE_URL = com.example.occasio.utils.ServerConfig.BASE_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentUsername = sharedPreferences.getString(KEY_USERNAME, "");
        
        // ✅ Get from Intent if available, and save to SharedPreferences
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("username")) {
            String usernameFromIntent = intent.getStringExtra("username");
            if (usernameFromIntent != null && !usernameFromIntent.isEmpty()) {
                currentUsername = usernameFromIntent;
                // Save to SharedPreferences for persistence
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_USERNAME, currentUsername);
                editor.apply();
            }
        }
        
        // ✅ If still empty, try to get from SharedPreferences one more time
        if ((currentUsername == null || currentUsername.isEmpty()) && sharedPreferences != null) {
            currentUsername = sharedPreferences.getString(KEY_USERNAME, "");
        }
        
        requestQueue = Volley.newRequestQueue(this);
        
        // ✅ Setup Chat WebSocket for notifications on all pages
        // Note: ChatActivity and GroupChatActivity don't extend BaseNavigationActivity,
        // so they won't get this setup - they handle their own WebSocket
        chatApiService = new com.example.occasio.services.ChatApiService(this);
        fetchUserIdForChatWebSocket();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Update notification badge when returning to activity
        if (currentUsername != null && !currentUsername.isEmpty()) {
            fetchUserIdAndUpdateBadge();
        }
        
        // ✅ Update Chat WebSocket listener when activity resumes
        // Note: ChatActivity and GroupChatActivity don't extend BaseNavigationActivity,
        // so they won't get this setup - they handle their own WebSocket
        if (userId != null) {
            setupChatWebSocket();
        } else if (currentUsername != null && !currentUsername.isEmpty()) {
            fetchUserIdForChatWebSocket();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // WebSocket is managed globally by Application class
        // No need to disconnect here
    }
    
    /**
     * DEPRECATED: WebSocket notifications are now handled globally by OccasioApplication
     * This method is kept for backward compatibility but does nothing
     */
    @Deprecated
    protected void setupGlobalWebSocket() {
        // WebSocket notifications are now handled globally by OccasioApplication
        // This method is deprecated and does nothing
        android.util.Log.d("BaseNavigationActivity", "⚠️ setupGlobalWebSocket() called but notifications are handled by Application class");
    }
    
    protected void fetchUserIdAndUpdateBadge() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            return;
        }
        
        String url = BASE_URL + "/user_info/username/" + currentUsername;
        
        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            if (response.has("id")) {
                                userId = response.getLong("id");
                                // Update badge once we have userId
                                updateNotificationBadgeCount();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Silently fail - badge will just not show
                    }
                }
        );
        
        requestQueue.add(request);
    }
    
    protected void updateNotificationBadgeCount() {
        if (userId == null) {
            return;
        }
        
        String url = BASE_URL + "/api/notifications/user/" + userId + "/unread";
        
        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            int unreadCount = response.length();
                            updateNotificationBadge(unreadCount);
                        } catch (Exception e) {
                            updateNotificationBadge(0);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        updateNotificationBadge(0);
                    }
                }
        );
        
        requestQueue.add(request);
    }

    protected void setupBottomNavigation() {
        navEvents = findViewById(R.id.nav_events);
        navMessages = findViewById(R.id.nav_messages);
        navNotifications = findViewById(R.id.nav_notifications);
        navProfile = findViewById(R.id.nav_profile);
        navNotificationBadge = findViewById(R.id.nav_notification_badge);
        
        if (navEvents != null) {
            navEvents.setOnClickListener(v -> navigateToEvents());
        }
        
        if (navMessages != null) {
            navMessages.setOnClickListener(v -> navigateToMessages());
        }
        
        if (navNotifications != null) {
            navNotifications.setOnClickListener(v -> navigateToNotifications());
        }
        
        if (navProfile != null) {
            navProfile.setOnClickListener(v -> navigateToProfile());
        }
        
        // Update selected state
        updateSelectedState();
    }
    
    protected void updateSelectedState() {
        // Reset all nav items
        resetNavItem(navEvents);
        resetNavItem(navMessages);
        resetNavItem(navNotifications);
        resetNavItem(navProfile);
        
        // Highlight current page
        if (this instanceof AllEventsActivity) {
            highlightNavItem(navEvents);
        } else if (this instanceof MessagingActivity) {
            highlightNavItem(navMessages);
        } else if (this instanceof NotificationInboxActivity) {
            highlightNavItem(navNotifications);
        } else if (this instanceof EditProfileActivity) {
            highlightNavItem(navProfile);
        }
    }
    
    private void resetNavItem(LinearLayout navItem) {
        if (navItem == null) return;
        navItem.setAlpha(0.6f);
        // Find ImageView and TextView in the layout
        for (int i = 0; i < navItem.getChildCount(); i++) {
            View child = navItem.getChildAt(i);
            if (child instanceof ImageView) {
                child.setAlpha(0.6f);
            } else if (child instanceof TextView) {
                child.setAlpha(0.6f);
            } else if (child instanceof android.widget.RelativeLayout) {
                // For notifications tab which has RelativeLayout
                android.widget.RelativeLayout rl = (android.widget.RelativeLayout) child;
                for (int j = 0; j < rl.getChildCount(); j++) {
                    View rlChild = rl.getChildAt(j);
                    if (rlChild instanceof ImageView) {
                        rlChild.setAlpha(0.6f);
                    }
                }
            }
        }
    }
    
    private void highlightNavItem(LinearLayout navItem) {
        if (navItem == null) return;
        navItem.setAlpha(1.0f);
        // Find ImageView and TextView in the layout
        for (int i = 0; i < navItem.getChildCount(); i++) {
            View child = navItem.getChildAt(i);
            if (child instanceof ImageView) {
                child.setAlpha(1.0f);
            } else if (child instanceof TextView) {
                child.setAlpha(1.0f);
            } else if (child instanceof android.widget.RelativeLayout) {
                // For notifications tab which has RelativeLayout
                android.widget.RelativeLayout rl = (android.widget.RelativeLayout) child;
                for (int j = 0; j < rl.getChildCount(); j++) {
                    View rlChild = rl.getChildAt(j);
                    if (rlChild instanceof ImageView) {
                        rlChild.setAlpha(1.0f);
                    }
                }
            }
        }
    }
    
    protected void navigateToEvents() {
        if (this instanceof AllEventsActivity) {
            return; // Already on events page
        }
        Intent intent = new Intent(this, AllEventsActivity.class);
        intent.putExtra("username", currentUsername);
        startActivity(intent);
        finish();
    }
    
    protected void navigateToMessages() {
        if (this instanceof MessagingActivity) {
            return; // Already on messages page
        }
        Intent intent = new Intent(this, MessagingActivity.class);
        intent.putExtra("username", currentUsername);
        startActivity(intent);
        finish();
    }
    
    protected void navigateToNotifications() {
        if (this instanceof NotificationInboxActivity) {
            return; // Already on notifications page
        }
        Intent intent = new Intent(this, NotificationInboxActivity.class);
        intent.putExtra("username", currentUsername);
        startActivity(intent);
        finish();
    }
    
    protected void navigateToProfile() {
        if (this instanceof com.example.occasio.profile.ProfileMenuActivity) {
            return; // Already on profile page
        }
        Intent intent = new Intent(this, com.example.occasio.profile.ProfileMenuActivity.class);
        intent.putExtra("username", currentUsername);
        startActivity(intent);
        finish();
    }
    
    protected void updateNotificationBadge(int count) {
        if (navNotificationBadge == null) return;
        
        if (count > 0) {
            navNotificationBadge.setVisibility(View.VISIBLE);
            navNotificationBadge.setText(String.valueOf(count));
            if (count > 99) {
                navNotificationBadge.setText("99+");
            }
        } else {
            navNotificationBadge.setVisibility(View.GONE);
        }
    }
    
    // ✅ Fetch userId for Chat WebSocket connection
    protected void fetchUserIdForChatWebSocket() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            android.util.Log.e("BaseNavigationActivity", "Cannot fetch userId: username is empty");
            return;
        }
        
        if (chatApiService == null) {
            chatApiService = new com.example.occasio.services.ChatApiService(this);
        }
        
        chatApiService.getUserIdFromUsername(currentUsername, new com.example.occasio.services.ChatApiService.UserIdCallback() {
            @Override
            public void onSuccess(Long fetchedUserId) {
                userId = fetchedUserId;
                android.util.Log.d("BaseNavigationActivity", "User ID fetched for Chat WebSocket: " + userId);
                setupChatWebSocket();
            }
            
            @Override
            public void onError(String error) {
                android.util.Log.e("BaseNavigationActivity", "Error fetching user ID for Chat WebSocket: " + error);
                // ✅ Don't show error toast - just log it silently
                // This is a background operation and shouldn't interrupt the user
            }
        });
    }
    
    // ✅ Setup Chat WebSocket for notifications on all pages (except ChatActivity)
    protected void setupChatWebSocket() {
        if (userId == null) {
            android.util.Log.e("BaseNavigationActivity", "Cannot setup Chat WebSocket: userId is null");
            return;
        }
        
        // Note: ChatActivity and GroupChatActivity don't extend BaseNavigationActivity,
        // so they won't call this method - they handle their own WebSocket
        
        if (chatWebSocketService == null) {
            chatWebSocketService = com.example.occasio.service.ChatWebSocketService.getInstance();
        }
        
        chatWebSocketService.setListener(new com.example.occasio.service.ChatWebSocketService.ChatWebSocketListener() {
            @Override
            public void onMessage(com.example.occasio.messaging.Message message) {
                // Handle legacy message format
            }
            
            @Override
            public void onChatCreated(org.json.JSONObject chat) {
                // Handle chat created event
            }
            
            @Override
            public void onNewMessage(org.json.JSONObject messageData) {
                // ✅ Show notification popup when NOT in the chat that the message is from
                runOnUiThread(() -> {
                    try {
                        android.util.Log.d("BaseNavigationActivity", "📨 onNewMessage received: " + (messageData != null ? messageData.toString() : "null"));
                        
                        if (messageData == null) {
                            android.util.Log.e("BaseNavigationActivity", "❌ messageData is null in onNewMessage");
                            return;
                        }
                        
                        // Parse message data
                        String content = messageData.optString("content", "");
                        org.json.JSONObject senderObj = messageData.optJSONObject("sender");
                        String sender = "";
                        if (senderObj != null) {
                            sender = senderObj.optString("username", "");
                        }
                        
                        // Get chat info
                        org.json.JSONObject chatObj = messageData.optJSONObject("chat");
                        Long messageChatId = null;
                        if (chatObj != null) {
                            messageChatId = chatObj.getLong("id");
                        } else {
                            messageChatId = messageData.optLong("chatId", -1);
                            if (messageChatId == -1) {
                                messageChatId = null;
                            }
                        }
                        
                        // ✅ Check if we're in ChatActivity or GroupChatActivity viewing this chat
                        // Since ChatActivity and GroupChatActivity are excluded from BaseNavigationActivity setup,
                        // we don't need to check here - they handle their own WebSocket
                        // This listener is only set up for pages that extend BaseNavigationActivity but are NOT ChatActivity/GroupChatActivity
                        boolean isInThisChat = false;
                        // Note: ChatActivity and GroupChatActivity are excluded from this setup, so isInThisChat will always be false here
                        
                        // ✅ Check for attachment
                        org.json.JSONObject attachmentObj = messageData.optJSONObject("attachment");
                        boolean hasAttachment = attachmentObj != null;
                        String attachmentType = "";
                        String attachmentName = "";
                        if (hasAttachment) {
                            attachmentType = attachmentObj.optString("fileType", "");
                            attachmentName = attachmentObj.optString("fileName", "");
                        }
                        
                        // ✅ Show notification popup when NOT in the chat that the message is from
                        if (!isInThisChat && sender != null && !sender.isEmpty()) {
                            // Check if this message is from the current user
                            if (currentUsername != null && !sender.equals(currentUsername)) {
                                android.util.Log.d("BaseNavigationActivity", "✅ Showing notification popup for message from: " + sender);
                                
                                String notificationText;
                                if (hasAttachment) {
                                    // Show attachment notification
                                    String attachmentDisplay = attachmentName.isEmpty() ? 
                                        (attachmentType.isEmpty() ? "an attachment" : attachmentType + " file") : 
                                        attachmentName;
                                    notificationText = "📎 " + attachmentDisplay;
                                } else if (content != null && !content.isEmpty()) {
                                    notificationText = content.length() > 50 ? content.substring(0, 50) + "..." : content;
                                } else {
                                    notificationText = "New message";
                                }
                                
                                android.util.Log.d("BaseNavigationActivity", "🔔 Calling InAppNotificationHelper.showNotification for: " + sender);
                                com.example.occasio.utils.InAppNotificationHelper.showNotification(
                                    BaseNavigationActivity.this,
                                    "💬 New message from " + sender,
                                    notificationText
                                );
                                android.util.Log.d("BaseNavigationActivity", "✅ InAppNotificationHelper.showNotification called");
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("BaseNavigationActivity", "❌ Error handling new_message event: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
            
            @Override
            public void onMessageEdited(org.json.JSONObject messageData) {
                // Handle message edit
            }
            
            @Override
            public void onMessageDeleted(Long messageId) {
                // Handle message deletion
            }
            
            @Override
            public void onTyping(Long userId, boolean isTyping) {
                // Handle typing indicator
            }
            
            @Override
            public void onReaction(Long messageId, String emoji, Long reactionUserId, String action) {
                // ✅ Show notification popup for reactions when NOT in chat
                runOnUiThread(() -> {
                    try {
                        android.util.Log.d("BaseNavigationActivity", "✅ Reaction event: " + action + " " + emoji + " on message " + messageId);
                        
                        // ✅ Show notification popup for reactions when not in chat
                        if (reactionUserId != null && !reactionUserId.equals(userId)) {
                            // Get sender username from userId (simplified - you might want to cache this)
                            String senderName = "Someone";
                            
                            // Show notification
                            String notificationText = "reacted with " + emoji;
                            if ("add".equals(action)) {
                                com.example.occasio.utils.InAppNotificationHelper.showNotification(
                                    BaseNavigationActivity.this,
                                    "💬 " + senderName + " reacted",
                                    notificationText
                                );
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("BaseNavigationActivity", "Error handling reaction event: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
            
            @Override
            public void onConnect() {
                android.util.Log.d("BaseNavigationActivity", "✅ Chat WebSocket connected successfully - ready to receive notifications");
            }
            
            @Override
            public void onDisconnect() {
                android.util.Log.d("BaseNavigationActivity", "⚠️ Chat WebSocket disconnected - notifications will not work");
            }
            
            @Override
            public void onError(Exception e) {
                android.util.Log.e("BaseNavigationActivity", "❌ Chat WebSocket error: " + (e != null ? e.getMessage() : "unknown error"));
                if (e != null) {
                    e.printStackTrace();
                }
            }
        });
        
        // Connect to WebSocket
        if (!chatWebSocketService.isConnected()) {
            String serverUrl = com.example.occasio.utils.ServerConfig.WS_CHAT_URL;
            android.util.Log.d("BaseNavigationActivity", "🔌 Connecting to Chat WebSocket for user: " + userId + " at " + serverUrl);
            chatWebSocketService.connect(userId, serverUrl);
            
            // ✅ Verify connection after a short delay
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (chatWebSocketService.isConnected()) {
                    android.util.Log.d("BaseNavigationActivity", "✅ Chat WebSocket connection verified - ready to receive messages and notifications");
                } else {
                    android.util.Log.e("BaseNavigationActivity", "❌ Chat WebSocket connection failed - notifications will not work. Retrying...");
                    // Retry connection
                    chatWebSocketService.connect(userId, serverUrl);
                }
            }, 1000);
        } else {
            android.util.Log.d("BaseNavigationActivity", "✅ Chat WebSocket already connected - notifications active");
        }
    }
}

