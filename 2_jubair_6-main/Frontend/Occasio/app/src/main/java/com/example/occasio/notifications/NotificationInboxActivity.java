package com.example.occasio.notifications;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.example.occasio.R;
import com.example.occasio.base.BaseNavigationActivity;
import com.example.occasio.api.VolleySingleton;
import com.example.occasio.model.Notification;
import com.example.occasio.service.WebSocketService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NotificationInboxActivity extends BaseNavigationActivity {

    private RecyclerView recyclerView;
    private NotificationAdapter adapter;
    private List<Notification> notificationList;
    private TextView emptyView;
    private Button markAllReadButton;
    private Button selectAllButton;
    private Button deleteSelectedButton;
    private Button cancelSelectionButton;
    private TextView selectionCountText;
    private LinearLayout actionButtonsLayout;
    private LinearLayout selectionBar;
    private Long userId;
    private RequestQueue requestQueue;
    private WebSocketService webSocketService;
    private String currentUsername;
    private static final String BASE_URL = com.example.occasio.utils.ServerConfig.BASE_URL;
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_USERNAME = "username";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_inbox);

        recyclerView = findViewById(R.id.notification_recycler_view);
        emptyView = findViewById(R.id.notification_empty_view);
        markAllReadButton = findViewById(R.id.mark_all_read_button);
        
        // Initialize selection mode views (may be null if not in layout)
        selectAllButton = findViewById(R.id.select_all_button);
        deleteSelectedButton = findViewById(R.id.delete_selected_button);
        cancelSelectionButton = findViewById(R.id.cancel_selection_button);
        selectionCountText = findViewById(R.id.notification_selection_count);
        actionButtonsLayout = findViewById(R.id.notification_action_buttons);
        selectionBar = findViewById(R.id.notification_selection_bar);

        // Null checks for required views
        if (recyclerView == null || emptyView == null || markAllReadButton == null) {
            android.util.Log.e("NotificationInboxActivity", "Critical views not found!");
            finish();
            return;
        }

        notificationList = new ArrayList<>();
        adapter = new NotificationAdapter(notificationList, this);

        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
        }

        requestQueue = VolleySingleton.getInstance(this).getRequestQueue();

        // Try to get username from Intent first (if passed from MainActivity)
        android.content.Intent receivedIntent = getIntent();
        if (receivedIntent != null && receivedIntent.hasExtra("username")) {
            currentUsername = receivedIntent.getStringExtra("username");
        }
        
        // If not in Intent, try SharedPreferences
        if (currentUsername == null || currentUsername.isEmpty()) {
            SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            currentUsername = sharedPreferences.getString(KEY_USERNAME, "");
        }

        // Get userId from backend using username
        if (currentUsername != null && !currentUsername.isEmpty()) {
            fetchUserIdByUsername(currentUsername);
        } else {
            finish();
        }

        if (markAllReadButton != null) {
            markAllReadButton.setOnClickListener(v -> markAllAsRead());
        }
        
        // Setup selection mode buttons if they exist
        if (selectAllButton != null && adapter != null) {
            selectAllButton.setOnClickListener(v -> {
                // Select all notifications
                adapter.selectAll();
                updateSelectionCount();
            });
        }
        
        if (deleteSelectedButton != null && adapter != null) {
            deleteSelectedButton.setOnClickListener(v -> deleteSelectedNotifications());
        }
        
        if (cancelSelectionButton != null && adapter != null) {
            cancelSelectionButton.setOnClickListener(v -> exitSelectionMode());
        }
        
        // Set up selection change listener
        if (adapter != null) {
            adapter.setSelectionChangeListener(count -> updateSelectionCount());
        }
        
        // Setup bottom navigation
        setupBottomNavigation();
        
        // Setup WebSocket to refresh notifications when new ones arrive
        setupWebSocket();
    }
    
    private void setupWebSocket() {
        if (currentUsername != null && !currentUsername.isEmpty()) {
            try {
                webSocketService = WebSocketService.getInstance();
                if (webSocketService == null) {
                    android.util.Log.e("NotificationInboxActivity", "WebSocketService.getInstance() returned null");
                    return;
                }
                webSocketService.setListener(new WebSocketService.WebSocketListener() {
                @Override
                public void onMessage(String message) {
                    runOnUiThread(() -> {
                        // Refresh notification list when new notification arrives
                        if (userId != null) {
                            loadNotifications();
                        }
                    });
                }

                @Override
                public void onConnect() {
                    android.util.Log.d("NotificationInboxActivity", "WebSocket connected");
                }

                @Override
                public void onDisconnect() {
                    android.util.Log.d("NotificationInboxActivity", "WebSocket disconnected");
                }

                @Override
                public void onError(Exception e) {
                    android.util.Log.e("NotificationInboxActivity", "WebSocket error: " + e.getMessage());
                }
            });

                // Connect to WebSocket server
                String wsUrl = com.example.occasio.utils.ServerConfig.WS_NOTIFICATION_URL + currentUsername;
                webSocketService.connect(currentUsername, wsUrl);
            } catch (Exception e) {
                android.util.Log.e("NotificationInboxActivity", "Error setting up WebSocket: " + e.getMessage(), e);
                // Don't crash if WebSocket fails - notifications can still be loaded via API
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up WebSocket listener (but don't disconnect as other activities might use it)
        if (webSocketService != null) {
            webSocketService.setListener(null);
        }
    }

    private void fetchUserIdByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }

        String url = BASE_URL + "/user_info/username/" + username;

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
                                // Once we have userId, load user-specific notifications
                                loadNotifications();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                        Toast.makeText(NotificationInboxActivity.this, "Error fetching user ID: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
        );

        requestQueue.add(request);
    }

    private void loadNotifications() {
        if (userId == null) {
            return;
        }

        // Use user-specific endpoint
        String url = BASE_URL + "/api/notifications/user/" + userId;

        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            android.util.Log.d("NotificationInboxActivity", "Loading notifications, response length: " + response.length());
                            
                            if (notificationList != null) {
                                notificationList.clear();
                            } else {
                                notificationList = new ArrayList<>();
                            }
                            
                            // Add safety check for response size to prevent StackOverflow
                            int maxSize = Math.min(response.length(), 1000);
                            for (int i = 0; i < maxSize; i++) {
                                try {
                                    JSONObject obj = response.getJSONObject(i);
                                    if (obj != null) {
                                        // Log the raw JSON for first notification to debug
                                        if (i == 0) {
                                            android.util.Log.d("NotificationInboxActivity", "First notification JSON: " + obj.toString());
                                        }
                                        Notification notification = parseNotification(obj);
                                        if (notification != null) {
                                            notificationList.add(notification);
                                        }
                                    }
                                } catch (JSONException e) {
                                    android.util.Log.e("NotificationInboxActivity", "Error parsing notification " + i, e);
                                    continue; // Skip this notification and continue
                                }
                            }
                            
                            android.util.Log.d("NotificationInboxActivity", "Loaded " + notificationList.size() + " notifications");
                            
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                            updateEmptyView();
                            // Update badge count after loading notifications
                            updateNotificationBadgeCount();
                        } catch (StackOverflowError e) {
                            android.util.Log.e("NotificationInboxActivity", "StackOverflowError parsing notifications: " + e.getMessage());
                            e.printStackTrace();
                        } catch (Exception e) {
                            android.util.Log.e("NotificationInboxActivity", "Unexpected error parsing notifications: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                        Toast.makeText(NotificationInboxActivity.this, "Error loading notifications", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        requestQueue.add(request);
    }

    private Notification parseNotification(JSONObject obj) throws JSONException {
        if (obj == null) {
            return null;
        }
        
        Notification notification = new Notification();
        
        try {
            if (obj.has("id") && !obj.isNull("id")) {
                notification.setId(obj.getLong("id"));
            }
            if (obj.has("message") && !obj.isNull("message")) {
                notification.setMessage(obj.getString("message"));
            } else {
                notification.setMessage("Notification");
            }
            
            // Try multiple field names for read status (isRead, read, is_read)
            boolean isRead = false;
            if (obj.has("isRead") && !obj.isNull("isRead")) {
                isRead = obj.getBoolean("isRead");
            } else if (obj.has("read") && !obj.isNull("read")) {
                isRead = obj.getBoolean("read");
            } else if (obj.has("is_read") && !obj.isNull("is_read")) {
                isRead = obj.getBoolean("is_read");
            }
            notification.setRead(isRead);
            
            // Log for debugging
            if (notification.getId() != null) {
                android.util.Log.d("NotificationInboxActivity", 
                    "Parsed notification ID: " + notification.getId() + 
                    ", isRead: " + isRead + 
                    ", JSON has isRead: " + obj.has("isRead") +
                    ", JSON value: " + (obj.has("isRead") ? obj.optBoolean("isRead", false) : "N/A"));
            }
            if (obj.has("notificationType") && !obj.isNull("notificationType")) {
                notification.setNotificationType(obj.getString("notificationType"));
            }
            if (obj.has("events") && !obj.isNull("events")) {
                JSONObject eventObj = obj.getJSONObject("events");
                if (eventObj != null) {
                    if (eventObj.has("id") && !eventObj.isNull("id")) {
                        notification.setEventId(eventObj.getLong("id"));
                    }
                    if (eventObj.has("eventName") && !eventObj.isNull("eventName")) {
                        notification.setEventName(eventObj.getString("eventName"));
                    }
                }
            }
            
            // Parse createdAt timestamp if available
            if (obj.has("createdAt") && !obj.isNull("createdAt")) {
                notification.setCreatedAt(obj.getString("createdAt"));
            } else if (obj.has("sentTime") && !obj.isNull("sentTime")) {
                notification.setCreatedAt(obj.getString("sentTime"));
            }
        } catch (Exception e) {
            android.util.Log.e("NotificationInboxActivity", "Error parsing notification: " + e.getMessage(), e);
            e.printStackTrace();
            // Return a basic notification with default values
            notification.setMessage("Notification");
            notification.setRead(false);
        }
        
        return notification;
    }

    private void updateEmptyView() {
        if (recyclerView != null && emptyView != null) {
            if (notificationList == null || notificationList.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            }
        }
    }
    
    @Override
    protected void updateNotificationBadgeCount() {
        // Call parent implementation
        super.updateNotificationBadgeCount();
    }
    
    private void updateSelectionCount() {
        if (selectionCountText != null && adapter != null) {
            Set<Long> selected = adapter.getSelectedNotifications();
            int count = selected != null ? selected.size() : 0;
            selectionCountText.setText(count + " selected");
            if (deleteSelectedButton != null) {
                deleteSelectedButton.setEnabled(count > 0);
            }
        }
    }
    
    public void enterSelectionMode() {
        if (actionButtonsLayout != null) {
            actionButtonsLayout.setVisibility(View.GONE);
        }
        if (selectionBar != null) {
            selectionBar.setVisibility(View.VISIBLE);
        }
        if (adapter != null) {
            adapter.setSelectionMode(true);
        }
    }
    
    private void exitSelectionMode() {
        if (actionButtonsLayout != null) {
            actionButtonsLayout.setVisibility(View.VISIBLE);
        }
        if (selectionBar != null) {
            selectionBar.setVisibility(View.GONE);
        }
        if (adapter != null) {
            adapter.setSelectionMode(false);
            adapter.clearSelection();
        }
    }
    
    private void deleteSelectedNotifications() {
        if (adapter == null) {
            return;
        }
        
        Set<Long> selectedIds = adapter.getSelectedNotifications();
        if (selectedIds.isEmpty()) {
            return;
        }
        
        // Delete selected notifications
        for (Long id : selectedIds) {
            deleteNotification(id);
        }
        
        exitSelectionMode();
    }
    
    private void deleteNotification(Long notificationId) {
        if (notificationId == null || requestQueue == null) {
            return;
        }
        
        String url = BASE_URL + "/api/notifications/" + notificationId;
        
        StringRequest request = new StringRequest(
                Request.Method.DELETE,
                url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Remove from list
                        if (notificationList != null) {
                            notificationList.removeIf(n -> n != null && n.getId() != null && n.getId().equals(notificationId));
                        }
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                        updateEmptyView();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Toast.makeText(NotificationInboxActivity.this, "Error deleting notification", Toast.LENGTH_SHORT).show();
                    }
                }
        );
        
        requestQueue.add(request);
    }

    public void markNotificationAsRead(Long notificationId) {
        if (notificationId == null) {
            return;
        }
        
        String url = BASE_URL + "/api/notifications/" + notificationId + "/read?value=true";

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.PATCH,
                url,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            android.util.Log.d("NotificationInboxActivity", "Mark as read response: " + response.toString());
                            
                            // Parse the response to verify it's marked as read
                            boolean isRead = response.optBoolean("isRead", false);
                            Long id = response.optLong("id", -1);
                            
                            android.util.Log.d("NotificationInboxActivity", 
                                "Backend confirmed notification " + id + " isRead: " + isRead);
                            
                            // Update the notification in the list immediately
                            if (notificationList != null) {
                                for (Notification notification : notificationList) {
                                    if (notification != null && notification.getId() != null && notification.getId().equals(notificationId)) {
                                        notification.setRead(true);
                                        android.util.Log.d("NotificationInboxActivity", "Updated notification " + notificationId + " to read=true locally");
                                        break;
                                    }
                                }
                            }
                            // Update UI immediately
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                            // Update badge after marking as read
                            updateNotificationBadgeCount();
                        } catch (Exception e) {
                            android.util.Log.e("NotificationInboxActivity", "Error parsing mark as read response: " + e.getMessage());
                            // Still update locally even if parsing fails
                            if (notificationList != null) {
                                for (Notification notification : notificationList) {
                                    if (notification != null && notification.getId() != null && notification.getId().equals(notificationId)) {
                                        notification.setRead(true);
                                        break;
                                    }
                                }
                            }
                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                            updateNotificationBadgeCount();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        android.util.Log.e("NotificationInboxActivity", "Error marking notification as read: " + error.getMessage());
                        if (error.networkResponse != null) {
                            android.util.Log.e("NotificationInboxActivity", "Status code: " + error.networkResponse.statusCode);
                            try {
                                String responseBody = new String(error.networkResponse.data);
                                android.util.Log.e("NotificationInboxActivity", "Error response body: " + responseBody);
                            } catch (Exception e) {
                                // Ignore
                            }
                        }
                        Toast.makeText(NotificationInboxActivity.this, "Error marking notification as read", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        if (requestQueue != null) {
            requestQueue.add(request);
        }
    }

    private void markAllAsRead() {
        if (userId == null) {
            return;
        }

        if (notificationList == null || notificationList.isEmpty()) {
            return;
        }

        // Call the backend endpoint to mark all as read
        String url = BASE_URL + "/api/notifications/user/" + userId + "/read-all";

        StringRequest request = new StringRequest(
                Request.Method.PUT,
                url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Update all notifications in the list
                        if (notificationList != null) {
                            for (Notification notification : notificationList) {
                                if (notification != null) {
                                    notification.setRead(true);
                                }
                            }
                        }
                        // Update UI immediately
                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                        // Update badge after marking all as read
                        updateNotificationBadgeCount();
                        // Don't reload - trust the backend response and local update
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        error.printStackTrace();
                        String errorMessage = "Error marking all notifications as read";
                        if (error.networkResponse != null) {
                            int statusCode = error.networkResponse.statusCode;
                            if (statusCode == 404) {
                                errorMessage = "User not found";
                            } else if (statusCode == 500) {
                                errorMessage = "Server error. Please try again later.";
                            } else {
                                errorMessage += " (HTTP " + statusCode + ")";
                            }
                        }
                    }
                }
        );

        if (requestQueue != null) {
            requestQueue.add(request);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Refresh notifications and badge when returning to this activity
        if (userId != null) {
            loadNotifications();
            updateNotificationBadgeCount();
        }
    }
}

