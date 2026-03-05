package com.example.occasio.messaging;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import com.example.occasio.base.BaseNavigationActivity;
import com.example.occasio.events.AllEventsActivity;
import com.example.occasio.services.ChatApiService;
import com.example.occasio.service.ChatWebSocketService;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class MessagingActivity extends BaseNavigationActivity {
    
    private RecyclerView friendsRecyclerView;
    private RecyclerView groupsRecyclerView;
    private ChatListAdapter friendsAdapter;
    private ChatListAdapter groupsAdapter;
    private List<ChatItem> friendsList;
    private List<ChatItem> groupsList;
    private TextView statusTextView;
    private TextView friendsHeader;
    private TextView groupsHeader;
    private LinearLayout friendsEmptyState;
    private LinearLayout groupsEmptyState;
    private Button groupChatButton;
    private Button backButton;
    private ChatApiService chatApiService;
    private ChatWebSocketService webSocketService;
    private Long currentUserId;
    private String currentUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ✅ Call super.onCreate() FIRST - this sets currentUsername from BaseNavigationActivity
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messaging);

        try {
            initializeViews();
            setupRecyclerView();
            setupClickListeners();
            setupBottomNavigation();
        } catch (Exception e) {
            android.util.Log.e("MessagingActivity", "Error in onCreate: " + e.getMessage(), e);
            finish();
            return;
        }
        
        // ✅ BaseNavigationActivity.onCreate() should have set currentUsername
        // But check SharedPreferences as fallback if it's still empty
        if (currentUsername == null || currentUsername.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            currentUsername = prefs.getString("username", "");
            android.util.Log.d("MessagingActivity", "Username loaded from SharedPreferences: " + currentUsername);
            
            // If still empty, try to get from Intent
            Intent intent = getIntent();
            if (intent != null) {
                String usernameFromIntent = intent.getStringExtra("username");
                if (usernameFromIntent != null && !usernameFromIntent.isEmpty()) {
                    // Save username from Intent to SharedPreferences
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("username", usernameFromIntent);
                    editor.apply();
                    currentUsername = usernameFromIntent;
                    android.util.Log.d("MessagingActivity", "Username loaded from Intent: " + usernameFromIntent);
                }
            }
        } else {
            android.util.Log.d("MessagingActivity", "Username already set by BaseNavigationActivity: " + currentUsername);
        }
        
        try {
            chatApiService = new ChatApiService(this);
            fetchUserId();
            
            loadConversations();
            
            // Setup WebSocket connection after userId is fetched
            setupWebSocket();
        } catch (Exception e) {
            android.util.Log.e("MessagingActivity", "Error setting up services: " + e.getMessage(), e);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reconnect WebSocket if needed
        if (currentUserId != null && webSocketService != null && !webSocketService.isConnected()) {
            setupWebSocket();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Don't disconnect WebSocket - keep it connected for notifications
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Disconnect WebSocket when activity is destroyed
        if (webSocketService != null) {
            webSocketService.disconnect();
        }
    }
    
    private void setupWebSocket() {
        if (currentUserId == null) {
            // Will be set up after userId is fetched
            return;
        }
        
        try {
            if (webSocketService == null) {
                webSocketService = ChatWebSocketService.getInstance();
            }
            
            if (webSocketService == null) {
                android.util.Log.e("MessagingActivity", "ChatWebSocketService.getInstance() returned null");
                return;
            }
            
            webSocketService.setListener(new ChatWebSocketService.ChatWebSocketListener() {
            @Override
            public void onMessage(com.example.occasio.messaging.Message message) {
                // Handle legacy message format
            }
            
            @Override
            public void onChatCreated(org.json.JSONObject chat) {
                runOnUiThread(() -> {
                    try {
                        Long chatId = chat.getLong("id");
                        String chatName = chat.optString("chatName", "New Chat");
                        String chatType = chat.optString("chatType", "DIRECT");
                        
                        Log.d("MessagingActivity", "Chat created via WebSocket: " + chatId + " - " + chatName + " (type: " + chatType + ")");
                        
                        // ✅ Handle both direct and group chats
                        if ("DIRECT".equals(chatType)) {
                            boolean exists = false;
                            for (ChatItem item : friendsList) {
                                if (item.getId().equals(String.valueOf(chatId))) {
                                    exists = true;
                                    break;
                                }
                            }
                            
                            if (!exists) {
                                friendsList.add(new ChatItem(
                                    String.valueOf(chatId),
                                    chatName,
                                    "",
                                    formatTimestamp(System.currentTimeMillis()),
                                    false,
                                    ""
                                ));
                                friendsAdapter.notifyDataSetChanged();
                                updateEmptyStates();
                                
                                    android.util.Log.d("MessagingActivity", "New chat created: " + chatName);
                            }
                        } else if ("GROUP".equals(chatType)) {
                            // ✅ Add group chat to groups list
                            boolean exists = false;
                            for (ChatItem item : groupsList) {
                                if (item.getId().equals(String.valueOf(chatId))) {
                                    exists = true;
                                    break;
                                }
                            }
                            
                            if (!exists) {
                                groupsList.add(new ChatItem(
                                    String.valueOf(chatId),
                                    chatName,
                                    "",
                                    formatTimestamp(System.currentTimeMillis()),
                                    true,
                                    ""
                                ));
                                groupsAdapter.notifyDataSetChanged();
                                updateEmptyStates();
                                
                                    android.util.Log.d("MessagingActivity", "New group chat created: " + chatName);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("MessagingActivity", "Error handling chat_created event: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
            
            @Override
            public void onNewMessage(org.json.JSONObject messageData) {
                // ✅ Handle new message notifications - show popup when NOT in chat
                runOnUiThread(() -> {
                    try {
                        android.util.Log.d("MessagingActivity", "✅ onNewMessage received: " + messageData.toString());
                        
                        // Parse message data
                        String content = messageData.optString("content", "");
                        org.json.JSONObject senderObj = messageData.optJSONObject("sender");
                        String sender = "";
                        if (senderObj != null) {
                            sender = senderObj.optString("username", "");
                        }
                        
                        // Get chat info
                        org.json.JSONObject chatObj = messageData.optJSONObject("chat");
                        Long chatId = null;
                        if (chatObj != null) {
                            chatId = chatObj.getLong("id");
                        } else {
                            // Try to get chatId from messageData directly
                            chatId = messageData.optLong("chatId", -1);
                            if (chatId == -1) {
                                chatId = null;
                            }
                        }
                        
                        // Parse timestamp - use SimpleDateFormat for API 24+ compatibility
                        String createdAt = messageData.optString("createdAt", "");
                        long timestamp = System.currentTimeMillis();
                        if (!createdAt.isEmpty()) {
                            try {
                                // Try ISO 8601 format: "2024-01-01T00:00:00" or "2024-01-01T00:00:00.000000"
                                String dateStr = createdAt.replace("Z", "").replace("T", " ");
                                // Remove microseconds if present
                                if (dateStr.contains(".")) {
                                    int dotIndex = dateStr.indexOf(".");
                                    dateStr = dateStr.substring(0, dotIndex);
                                }
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                                java.util.Date date = sdf.parse(dateStr);
                                timestamp = date.getTime();
                            } catch (Exception e) {
                                android.util.Log.e("MessagingActivity", "Error parsing timestamp: " + e.getMessage());
                                timestamp = System.currentTimeMillis();
                            }
                        }
                        
                        // ✅ Check for attachment
                        org.json.JSONObject attachmentObj = messageData.optJSONObject("attachment");
                        boolean hasAttachment = attachmentObj != null;
                        String attachmentType = "";
                        String attachmentName = "";
                        if (hasAttachment) {
                            attachmentType = attachmentObj.optString("fileType", "");
                            attachmentName = attachmentObj.optString("fileName", "");
                        }
                        
                        // ✅ Show notification popup when NOT in the chat
                        if (sender != null && !sender.isEmpty()) {
                            // Check if this message is from the current user
                            String currentUsername = MessagingActivity.this.currentUsername;
                            if (currentUsername != null && !sender.equals(currentUsername)) {
                                android.util.Log.d("MessagingActivity", "✅ Showing notification popup for message from: " + sender);
                                
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
                                
                                android.util.Log.d("MessagingActivity", "🔔 Calling InAppNotificationHelper.showNotification for: " + sender);
                                com.example.occasio.utils.InAppNotificationHelper.showNotification(
                                    MessagingActivity.this,
                                    "💬 New message from " + sender,
                                    notificationText
                                );
                                android.util.Log.d("MessagingActivity", "✅ InAppNotificationHelper.showNotification called");
                            }
                        }
                        
                        // ✅ Update conversation list in real-time (no API call needed)
                        if (chatId != null) {
                            // Format last message text - show attachment indicator if needed
                            String lastMessageText = content;
                            if (hasAttachment) {
                                String attachmentDisplay = attachmentName.isEmpty() ? 
                                    (attachmentType.isEmpty() ? "📎 Attachment" : "📎 " + attachmentType + " file") : 
                                    "📎 " + attachmentName;
                                lastMessageText = attachmentDisplay;
                            }
                            updateConversationList(chatId, lastMessageText, timestamp);
                        } else {
                            // If chatId is not available, reload conversations
                            android.util.Log.d("MessagingActivity", "⚠️ chatId not found, reloading conversations");
                            loadConversations();
                        }
                    } catch (Exception e) {
                        Log.e("MessagingActivity", "❌ Error handling new_message event: " + e.getMessage());
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
                // ✅ Handle reaction events - show notification when NOT in chat
                runOnUiThread(() -> {
                    try {
                        android.util.Log.d("MessagingActivity", "✅ Reaction event: " + action + " " + emoji + " on message " + messageId);
                        
                        // ✅ Show notification popup for reactions when not in chat
                        if (reactionUserId != null && !reactionUserId.equals(currentUserId)) {
                            // Get sender username from userId (simplified - you might want to cache this)
                            String senderName = "Someone";
                            
                            // Show notification
                            String notificationText = "reacted with " + emoji;
                            if ("add".equals(action)) {
                                com.example.occasio.utils.InAppNotificationHelper.showNotification(
                                    MessagingActivity.this,
                                    "💬 " + senderName + " reacted",
                                    notificationText
                                );
                            }
                        }
                        
                        // ✅ Update conversation list if needed (reactions don't change last message, but we can refresh)
                        // Note: Reactions don't typically update the conversation list, but we can reload if needed
                    } catch (Exception e) {
                        Log.e("MessagingActivity", "Error handling reaction event: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
            
            @Override
            public void onConnect() {
                Log.d("MessagingActivity", "✅ WebSocket connected successfully");
                android.util.Log.d("MessagingActivity", "✅ WebSocket is now listening for messages");
            }
            
            @Override
            public void onDisconnect() {
                Log.d("MessagingActivity", "WebSocket disconnected");
            }
            
            @Override
            public void onError(Exception e) {
                Log.e("MessagingActivity", "WebSocket error: " + e.getMessage());
            }
        });
        
        // Connect to WebSocket
        if (!webSocketService.isConnected()) {
            // ✅ Use ServerConfig for WebSocket URL (supports local and production)
            String serverUrl = com.example.occasio.utils.ServerConfig.WS_CHAT_URL;
            android.util.Log.d("MessagingActivity", "🔌 Connecting to WebSocket for user: " + currentUserId + " at " + serverUrl);
            webSocketService.connect(currentUserId, serverUrl);
            
            // ✅ Verify connection after a short delay
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (webSocketService.isConnected()) {
                    android.util.Log.d("MessagingActivity", "✅ WebSocket connection verified - ready to receive messages");
                } else {
                    android.util.Log.e("MessagingActivity", "❌ WebSocket connection failed - not connected");
                }
            }, 1000);
        } else {
            android.util.Log.d("MessagingActivity", "✅ WebSocket already connected");
        }
        } catch (Exception e) {
            android.util.Log.e("MessagingActivity", "Error setting up WebSocket: " + e.getMessage(), e);
            // Don't crash if WebSocket fails - messaging can still work via API
        }
    }

    private void initializeViews() {
        backButton = findViewById(R.id.messaging_back_btn);
        statusTextView = findViewById(R.id.messaging_status_tv);
        groupChatButton = findViewById(R.id.messaging_group_chat_btn);
        friendsRecyclerView = findViewById(R.id.friends_recycler_view);
        groupsRecyclerView = findViewById(R.id.groups_recycler_view);
        friendsHeader = findViewById(R.id.friends_header);
        groupsHeader = findViewById(R.id.groups_header);
        friendsEmptyState = findViewById(R.id.friends_empty_state);
        groupsEmptyState = findViewById(R.id.groups_empty_state);
        
        // Null checks for critical views
        if (friendsRecyclerView == null || groupsRecyclerView == null) {
            android.util.Log.e("MessagingActivity", "Critical views not found!");
            finish();
            return;
        }
        
        friendsList = new ArrayList<>();
        groupsList = new ArrayList<>();
    }

    private void setupRecyclerView() {
        if (friendsRecyclerView == null || groupsRecyclerView == null) {
            android.util.Log.e("MessagingActivity", "RecyclerViews not initialized!");
            return;
        }
        
        friendsAdapter = new ChatListAdapter(friendsList, this::onConversationClick);
        // ✅ Disable nested scrolling since RecyclerView is inside ScrollView
        friendsRecyclerView.setNestedScrollingEnabled(false);
        // ✅ Allow RecyclerView to expand to show all items
        friendsRecyclerView.setHasFixedSize(false);
        // ✅ Use a custom LinearLayoutManager that doesn't recycle views (shows all items)
        friendsRecyclerView.setLayoutManager(new LinearLayoutManager(this) {
            @Override
            public boolean canScrollVertically() {
                // ✅ Disable RecyclerView's own scrolling since parent ScrollView handles it
                return false;
            }
            
            @Override
            public void onMeasure(androidx.recyclerview.widget.RecyclerView.Recycler recycler, androidx.recyclerview.widget.RecyclerView.State state, int widthSpec, int heightSpec) {
                // ✅ Measure all items to ensure RecyclerView expands to full height
                int itemCount = state.getItemCount();
                if (itemCount > 0 && android.view.View.MeasureSpec.getMode(heightSpec) == android.view.View.MeasureSpec.UNSPECIFIED) {
                    int totalHeight = 0;
                    int width = android.view.View.MeasureSpec.getSize(widthSpec);
                    for (int i = 0; i < itemCount; i++) {
                        android.view.View child = recycler.getViewForPosition(i);
                        if (child != null) {
                            android.view.ViewGroup.LayoutParams lp = child.getLayoutParams();
                            int childWidthSpec = android.view.ViewGroup.getChildMeasureSpec(
                                widthSpec, 0, lp.width);
                            int childHeightSpec = android.view.ViewGroup.getChildMeasureSpec(
                                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED), 0, lp.height);
                            child.measure(childWidthSpec, childHeightSpec);
                            totalHeight += child.getMeasuredHeight();
                            recycler.recycleView(child);
                        }
                    }
                    // Add padding from RecyclerView
                    if (friendsRecyclerView != null) {
                        totalHeight += friendsRecyclerView.getPaddingTop() + friendsRecyclerView.getPaddingBottom();
                    }
                    int height = android.view.View.MeasureSpec.makeMeasureSpec(totalHeight, android.view.View.MeasureSpec.EXACTLY);
                    super.onMeasure(recycler, state, widthSpec, height);
                } else {
                    super.onMeasure(recycler, state, widthSpec, heightSpec);
                }
            }
        });
        if (friendsRecyclerView != null) {
            friendsRecyclerView.setAdapter(friendsAdapter);
        }
        
        groupsAdapter = new ChatListAdapter(groupsList, this::onConversationClick);
        // ✅ Disable nested scrolling since RecyclerView is inside ScrollView
        if (groupsRecyclerView != null) {
            groupsRecyclerView.setNestedScrollingEnabled(false);
            // ✅ Allow RecyclerView to expand to show all items
            groupsRecyclerView.setHasFixedSize(false);
            // ✅ Use a custom LinearLayoutManager that doesn't recycle views (shows all items)
            groupsRecyclerView.setLayoutManager(new LinearLayoutManager(this) {
                @Override
                public boolean canScrollVertically() {
                    // ✅ Disable RecyclerView's own scrolling since parent ScrollView handles it
                    return false;
                }
                
                @Override
                public void onMeasure(androidx.recyclerview.widget.RecyclerView.Recycler recycler, androidx.recyclerview.widget.RecyclerView.State state, int widthSpec, int heightSpec) {
                    // ✅ Measure all items to ensure RecyclerView expands to full height
                    int itemCount = state.getItemCount();
                    if (itemCount > 0 && android.view.View.MeasureSpec.getMode(heightSpec) == android.view.View.MeasureSpec.UNSPECIFIED) {
                        int totalHeight = 0;
                        int width = android.view.View.MeasureSpec.getSize(widthSpec);
                        for (int i = 0; i < itemCount; i++) {
                            android.view.View child = recycler.getViewForPosition(i);
                            if (child != null) {
                                android.view.ViewGroup.LayoutParams lp = child.getLayoutParams();
                                int childWidthSpec = android.view.ViewGroup.getChildMeasureSpec(
                                    widthSpec, 0, lp.width);
                                int childHeightSpec = android.view.ViewGroup.getChildMeasureSpec(
                                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED), 0, lp.height);
                                child.measure(childWidthSpec, childHeightSpec);
                                totalHeight += child.getMeasuredHeight();
                                recycler.recycleView(child);
                            }
                        }
                        // Add padding from RecyclerView
                        if (groupsRecyclerView != null) {
                            totalHeight += groupsRecyclerView.getPaddingTop() + groupsRecyclerView.getPaddingBottom();
                        }
                        int height = android.view.View.MeasureSpec.makeMeasureSpec(totalHeight, android.view.View.MeasureSpec.EXACTLY);
                        super.onMeasure(recycler, state, widthSpec, height);
                    } else {
                        super.onMeasure(recycler, state, widthSpec, heightSpec);
                    }
                }
            });
            groupsRecyclerView.setAdapter(groupsAdapter);
        }
    }

    private void setupClickListeners() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, AllEventsActivity.class);
                intent.putExtra("username", getIntent().getStringExtra("username"));
                startActivity(intent);
                finish();
            });
        }
        
        if (groupChatButton != null) {
            groupChatButton.setOnClickListener(v -> {
                showCreateNewChatDialog();
            });
        }
    }
    
    private void onConversationClick(ChatItem chatItem) {
        try {
            Intent intent;
            String chatIdStr = chatItem.getId();
            
            if (chatItem.isGroup()) {
                intent = new Intent(this, GroupChatActivity.class);
                intent.putExtra("chat_id", chatIdStr != null ? chatIdStr : "");
            } else {
                // Validate and parse chat ID for friend chats
                if (chatIdStr == null || chatIdStr.isEmpty()) {
                    android.util.Log.e("MessagingActivity", "Chat ID is null or empty for friend chat: " + chatItem.getName());
                    return;
                }
                
                try {
                    Long chatId = Long.parseLong(chatIdStr);
                    intent = new Intent(this, ChatActivity.class);
                    intent.putExtra("chatId", chatId);
                    intent.putExtra("chatName", chatItem.getName());
                    if (currentUserId != null) {
                        intent.putExtra("userId", currentUserId);
                    }
                } catch (NumberFormatException e) {
                    android.util.Log.e("MessagingActivity", "Invalid chat ID format: " + chatIdStr + " for chat: " + chatItem.getName());
                    return;
                }
            }
            
            intent.putExtra("chat_name", chatItem.getName());
            intent.putExtra("is_group", chatItem.isGroup());
            
            startActivity(intent);
        } catch (Exception e) {
            android.util.Log.e("MessagingActivity", "Error opening conversation: " + e.getMessage(), e);
        }
    }

    private void fetchUserId() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        currentUsername = prefs.getString("username", "");
        
        // ✅ Don't default to demo_user - require actual login
        if (currentUsername.isEmpty()) {
            runOnUiThread(() -> {
                updateStatus("⚠️ Please log in to use messaging");
                
                // Redirect to login after a delay
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    Intent intent = new Intent(MessagingActivity.this, com.example.occasio.auth.LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }, 2000);
            });
            return;
        }
        
        if (chatApiService == null) {
            chatApiService = new ChatApiService(this);
        }
        
        chatApiService.getUserIdFromUsername(currentUsername, new ChatApiService.UserIdCallback() {
            @Override
            public void onSuccess(Long userId) {
                currentUserId = userId;
                android.util.Log.d("MessagingActivity", "User ID fetched: " + currentUserId);
                loadConversations();
                // Setup WebSocket after userId is fetched
                setupWebSocket();
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("MessagingActivity", "Error fetching user ID: " + error);
                
                // ✅ Handle specific error cases
                if (error != null && error.contains("USER_NOT_FOUND")) {
                    runOnUiThread(() -> {
                        updateStatus("⚠️ User not found: " + currentUsername);
                    });
                    // Don't set default userId - user should fix their username
                    currentUserId = null;
                    // ✅ Don't load sample data - show empty state instead
                    friendsList.clear();
                    groupsList.clear();
                    friendsAdapter.notifyDataSetChanged();
                    groupsAdapter.notifyDataSetChanged();
                    updateEmptyStates();
                } else if (error != null && (error.contains("NETWORK_ERROR") || 
                    error.contains("UnknownHostException") || 
                    error.contains("Unable to resolve host") || 
                    error.contains("No address associated") ||
                    error.contains("Connection refused"))) {
                    runOnUiThread(() -> {
                        updateStatus("⚠️ Backend server unavailable. Showing offline conversations.");
                        android.util.Log.w("MessagingActivity", "Backend server is not reachable. Using offline mode.");
                    });
                    // Set default userId for offline mode
                    currentUserId = 1L;
                    loadSampleConversations();
                } else {
                    runOnUiThread(() -> {
                        updateStatus("⚠️ Error loading user data: " + (error != null ? error : "Unknown error"));
                        android.util.Log.e("MessagingActivity", "Error loading user data: " + (error != null ? error : "Unknown error"));
                    });
                    // ✅ Don't load sample data for non-network errors - show empty state
                    currentUserId = null;
                    friendsList.clear();
                    groupsList.clear();
                    friendsAdapter.notifyDataSetChanged();
                    groupsAdapter.notifyDataSetChanged();
                    updateEmptyStates();
                }
            }
        });
    }
    
    private void loadConversations() {
        if (currentUserId == null || chatApiService == null) {
            // ✅ Don't load sample data - show empty state instead
            runOnUiThread(() -> {
                friendsList.clear();
                groupsList.clear();
                friendsAdapter.notifyDataSetChanged();
                groupsAdapter.notifyDataSetChanged();
                updateEmptyStates();
                updateStatus("No chats available. Create a chat to get started!");
            });
            return;
        }
        
        long startTime = System.currentTimeMillis();
        android.util.Log.d("MessagingActivity", "⏱️ Starting to load direct chats for userId: " + currentUserId);
        
        chatApiService.getDirectChats(currentUserId, new ChatApiService.ChatsListCallback() {
            @Override
            public void onSuccess(List<org.json.JSONObject> chats) {
                long parseStartTime = System.currentTimeMillis();
                android.util.Log.d("MessagingActivity", "✅ Received " + (chats != null ? chats.size() : 0) + " direct chats from API in " + (parseStartTime - startTime) + "ms");
                
                runOnUiThread(() -> {
                    friendsList.clear();
                    String currentUsername = MessagingActivity.this.currentUsername; // ✅ Cache outside loop
                    
                    for (org.json.JSONObject chat : chats) {
                        try {
                            String chatId = String.valueOf(chat.getLong("id"));
                            String chatName = chat.optString("chatName", "");
                            
                            // ✅ Optimized: Only parse participants if chatName is missing or wrong
                            if (chatName == null || chatName.isEmpty() || 
                                chatName.equals("Direct Chat") || chatName.equals("Unknown") ||
                                (currentUsername != null && chatName.equals(currentUsername))) {
                                
                                // ✅ Derive chat name from participants only when needed
                                if (chat.has("participants")) {
                                    org.json.JSONArray participants = chat.getJSONArray("participants");
                                    
                                    for (int i = 0; i < participants.length(); i++) {
                                        org.json.JSONObject participant = participants.getJSONObject(i);
                                        String participantUsername = participant.optString("username", "");
                                        
                                        // Find the participant that's not the current user
                                        if (currentUsername != null && !participantUsername.equals(currentUsername)) {
                                            chatName = participantUsername;
                                            break;
                                        }
                                    }
                                }
                                
                                // ✅ Final fallback
                                if (chatName == null || chatName.isEmpty() || 
                                    (currentUsername != null && chatName.equals(currentUsername))) {
                                    chatName = "Unknown User";
                                }
                            }
                            
                            String lastMessage = "";
                            String timestamp = formatTimestamp(System.currentTimeMillis());
                            
                            friendsList.add(new ChatItem(chatId, chatName, lastMessage, timestamp, false, ""));
                        } catch (Exception e) {
                            android.util.Log.e("MessagingActivity", "Error parsing direct chat: " + e.getMessage());
                        }
                    }
                    
                    long parseEndTime = System.currentTimeMillis();
                    android.util.Log.d("MessagingActivity", "✅ Parsed and added " + friendsList.size() + " direct chats in " + (parseEndTime - parseStartTime) + "ms");
                    android.util.Log.d("MessagingActivity", "⏱️ Total time to load direct chats: " + (parseEndTime - startTime) + "ms");
                    
                    friendsAdapter.notifyDataSetChanged();
                    
                    // ✅ Force RecyclerView to measure all items so ScrollView can scroll properly
                    if (friendsRecyclerView != null && friendsAdapter.getItemCount() > 0) {
                        friendsRecyclerView.post(() -> {
                            // Request layout to ensure RecyclerView measures all items
                            friendsRecyclerView.requestLayout();
                            // Force measure to ensure all items are measured
                            int widthSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                                friendsRecyclerView.getWidth() > 0 ? friendsRecyclerView.getWidth() : 
                                android.view.View.MeasureSpec.getSize(android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)),
                                android.view.View.MeasureSpec.EXACTLY
                            );
                            int heightSpec = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED);
                            friendsRecyclerView.measure(widthSpec, heightSpec);
                            android.util.Log.d("MessagingActivity", "Friends RecyclerView measured - items: " + friendsList.size() + ", measured height: " + friendsRecyclerView.getMeasuredHeight());
                        });
                    }
                    
                    updateEmptyStates();
                });
            }
            
            @Override
            public void onError(String error) {
                android.util.Log.e("MessagingActivity", "Error loading direct chats: " + error);
                String errorMsg = error != null ? error : "";
                // ✅ Only load sample data for actual network errors, not for empty lists
                if (errorMsg.contains("UnknownHostException") || 
                    errorMsg.contains("Unable to resolve host") || 
                    errorMsg.contains("No address associated") ||
                    errorMsg.contains("Connection refused")) {
                runOnUiThread(() -> {
                        updateStatus("⚠️ Backend server unavailable. Showing offline conversations.");
                    loadSampleConversations();
                });
                } else {
                    // ✅ For other errors, just show empty state
                    runOnUiThread(() -> {
                        updateStatus("⚠️ Error loading direct chats. Showing empty state.");
                        friendsList.clear();
                        friendsAdapter.notifyDataSetChanged();
                        updateEmptyStates();
                    });
                }
            }
        });
        
        chatApiService.getGroupChats(currentUserId, new ChatApiService.ChatsListCallback() {
            @Override
            public void onSuccess(List<org.json.JSONObject> chats) {
                runOnUiThread(() -> {
                    android.util.Log.d("MessagingActivity", "✅ Received " + chats.size() + " group chats from backend");
                    groupsList.clear();
                    for (org.json.JSONObject chat : chats) {
                        try {
                            String chatId = String.valueOf(chat.getLong("id"));
                            String chatName = chat.optString("chatName", "Unknown Group");
                            String lastMessage = "";
                            String timestamp = formatTimestamp(System.currentTimeMillis());
                            
                            android.util.Log.d("MessagingActivity", "Adding group chat: ID=" + chatId + ", Name=" + chatName);
                            groupsList.add(new ChatItem(chatId, chatName, lastMessage, timestamp, true, ""));
                        } catch (Exception e) {
                            android.util.Log.e("MessagingActivity", "Error parsing group chat: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    
                    android.util.Log.d("MessagingActivity", "✅ Total group chats in list: " + groupsList.size());
                    // ✅ Empty list is valid - don't load sample data
                        groupsAdapter.notifyDataSetChanged();
                    
                    // ✅ Force RecyclerView to remeasure and relayout to show all items
                    if (groupsRecyclerView != null && groupsAdapter.getItemCount() > 0) {
                        groupsRecyclerView.post(() -> {
                            // Request layout to ensure RecyclerView measures all items
                            groupsRecyclerView.requestLayout();
                            // Force invalidate to trigger remeasure
                            groupsRecyclerView.invalidate();
                            android.util.Log.d("MessagingActivity", "Groups RecyclerView - items: " + groupsList.size() + ", requesting layout");
                        });
                    }
                    
                        updateStatus("Friend chats: " + friendsList.size() + " | Group chats: " + groupsList.size());
                        updateEmptyStates();
                });
            }
            
            @Override
            public void onError(String error) {
                android.util.Log.e("MessagingActivity", "Error loading group chats: " + error);
                String errorMsg = error != null ? error : "";
                // ✅ Only load sample data for actual network errors, not for empty lists
                if (errorMsg.contains("UnknownHostException") || 
                    errorMsg.contains("Unable to resolve host") || 
                    errorMsg.contains("No address associated") ||
                    errorMsg.contains("Connection refused")) {
                    runOnUiThread(() -> {
                        updateStatus("⚠️ Backend server unavailable. Showing offline conversations.");
                        loadSampleConversations();
                    });
                } else {
                    // ✅ For other errors, just show empty state
                runOnUiThread(() -> {
                        updateStatus("⚠️ Error loading group chats. Showing empty state.");
                        groupsList.clear();
                        groupsAdapter.notifyDataSetChanged();
                        updateEmptyStates();
                });
                }
            }
        });
    }
    
    /**
     * ⚠️ DEPRECATED: Only use for offline mode when backend is truly unavailable
     * This method loads sample/dummy data for testing purposes only
     * Should NOT be called for empty lists or normal errors
     */
    private void loadSampleConversations() {
        android.util.Log.w("MessagingActivity", "⚠️ Loading sample conversations - this should only happen in offline mode");
        
        friendsList.clear();
        groupsList.clear();
        
        long time1 = System.currentTimeMillis() - 300000;
        long time2 = System.currentTimeMillis() - 7200000;
        long time3 = System.currentTimeMillis() - 86400000;
        long time4 = System.currentTimeMillis() - 3600000;
        
        friendsList.add(new ChatItem(
            "1",
            "Alice Johnson",
            "Hey! How are you?",
            formatTimestamp(time1),
            false,
            ""
        ));
        
        friendsList.add(new ChatItem(
            "2",
            "Bob Smith",
            "Thanks for the help yesterday!",
            formatTimestamp(time2),
            false,
            ""
        ));
        
        groupsList.add(new ChatItem(
            "3",
            "Study Group",
            "Let's meet at the library",
            formatTimestamp(time3),
            true,
            ""
        ));
        
        groupsList.add(new ChatItem(
            "4",
            "Event Planning",
            "Don't forget about the event tomorrow!",
            formatTimestamp(time4),
            true,
            ""
        ));
        
        updateStatus("⚠️ OFFLINE MODE - Sample data (Friend chats: " + friendsList.size() + " | Group chats: " + groupsList.size() + ")");
        friendsAdapter.notifyDataSetChanged();
        groupsAdapter.notifyDataSetChanged();
        updateEmptyStates();
    }
    
    private void updateEmptyStates() {
        if (friendsList.isEmpty()) {
            if (friendsEmptyState != null) {
                friendsEmptyState.setVisibility(View.VISIBLE);
            }
            if (friendsRecyclerView != null) {
                friendsRecyclerView.setVisibility(View.GONE);
            }
        } else {
            if (friendsEmptyState != null) {
                friendsEmptyState.setVisibility(View.GONE);
            }
            if (friendsRecyclerView != null) {
                friendsRecyclerView.setVisibility(View.VISIBLE);
            }
        }
        
        if (groupsList.isEmpty()) {
            if (groupsEmptyState != null) {
                groupsEmptyState.setVisibility(View.VISIBLE);
            }
            if (groupsRecyclerView != null) {
                groupsRecyclerView.setVisibility(View.GONE);
            }
        } else {
            if (groupsEmptyState != null) {
                groupsEmptyState.setVisibility(View.GONE);
            }
            if (groupsRecyclerView != null) {
                groupsRecyclerView.setVisibility(View.VISIBLE);
            }
        }
    }
    
    private String formatTimestamp(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, h:mm a", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date(timestamp));
    }
    
    private void updateStatus(String message) {
        if (statusTextView != null) {
            statusTextView.setText(message);
        }
    }
    
    // ✅ Update conversation list in real-time when a new message arrives
    private void updateConversationList(Long chatId, String lastMessage, long timestamp) {
        try {
            String chatIdStr = String.valueOf(chatId);
            boolean found = false;
            
            // Update in friends list
            for (int i = 0; i < friendsList.size(); i++) {
                ChatItem item = friendsList.get(i);
                if (item.getId().equals(chatIdStr)) {
                    // Update existing chat item
                    item.setLastMessage(lastMessage);
                    item.setTimestamp(formatTimestamp(timestamp));
                    friendsAdapter.notifyItemChanged(i);
                    found = true;
                    
                    // Move to top of list
                    friendsList.remove(i);
                    friendsList.add(0, item);
                    friendsAdapter.notifyItemMoved(i, 0);
                    android.util.Log.d("MessagingActivity", "✅ Updated friend chat: " + item.getName());
                    break;
                }
            }
            
            // Update in groups list if not found in friends
            if (!found) {
                for (int i = 0; i < groupsList.size(); i++) {
                    ChatItem item = groupsList.get(i);
                    if (item.getId().equals(chatIdStr)) {
                        // Update existing chat item
                        item.setLastMessage(lastMessage);
                        item.setTimestamp(formatTimestamp(timestamp));
                        groupsAdapter.notifyItemChanged(i);
                        found = true;
                        
                        // Move to top of list
                        groupsList.remove(i);
                        groupsList.add(0, item);
                        groupsAdapter.notifyItemMoved(i, 0);
                        android.util.Log.d("MessagingActivity", "✅ Updated group chat: " + item.getName());
                        break;
                    }
                }
            }
            
            // If chat not found, reload conversations to get it
            if (!found) {
                android.util.Log.d("MessagingActivity", "⚠️ Chat not found in list, reloading conversations");
                loadConversations();
            } else {
                updateEmptyStates();
            }
        } catch (Exception e) {
            android.util.Log.e("MessagingActivity", "❌ Error updating conversation list: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void showCreateNewChatDialog() {
        if (currentUserId == null || currentUserId == -1) {
            if (currentUsername == null || currentUsername.isEmpty()) {
                SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                currentUsername = prefs.getString("username", "");
            }
            
            // ✅ Check if username is still empty
            if (currentUsername == null || currentUsername.isEmpty()) {
                Intent intent = new Intent(this, com.example.occasio.auth.LoginActivity.class);
                startActivity(intent);
                return;
            }
            
            
            chatApiService.getUserIdFromUsername(currentUsername, new ChatApiService.UserIdCallback() {
                @Override
                public void onSuccess(Long userId) {
                    currentUserId = userId;
                    runOnUiThread(() -> {
                        showCreateNewChatDialogInternal();
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        currentUserId = 1L;
                        showCreateNewChatDialogInternal();
                    });
                }
            });
            return;
        }
        
        showCreateNewChatDialogInternal();
    }
    
    private void showCreateNewChatDialogInternal() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Create New Chat");
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
        
        android.widget.Button individualChatBtn = new android.widget.Button(this);
        individualChatBtn.setText("💬 Individual Chat");
        individualChatBtn.setPadding(16, 16, 16, 16);
        individualChatBtn.setTextSize(16);
        individualChatBtn.setBackgroundResource(R.drawable.button_primary_fall);
        individualChatBtn.setTextColor(getResources().getColor(R.color.text_on_fall));
        individualChatBtn.setOnClickListener(v -> {
            if (dialogRef[0] != null && dialogRef[0].isShowing()) {
                dialogRef[0].dismiss();
            }
            showCreateIndividualChatDialog();
        });
        layout.addView(individualChatBtn);
        
        android.widget.TextView spacer = new android.widget.TextView(this);
        spacer.setHeight(16);
        layout.addView(spacer);
        
        android.widget.Button groupChatBtn = new android.widget.Button(this);
        groupChatBtn.setText("👥 Group Chat");
        groupChatBtn.setPadding(16, 16, 16, 16);
        groupChatBtn.setTextSize(16);
        groupChatBtn.setBackgroundResource(R.drawable.button_secondary_fall);
        groupChatBtn.setTextColor(getResources().getColor(R.color.text_on_fall));
        groupChatBtn.setOnClickListener(v -> {
            if (dialogRef[0] != null && dialogRef[0].isShowing()) {
                dialogRef[0].dismiss();
            }
            showCreateGroupChatDialogInternal();
        });
        layout.addView(groupChatBtn);
        
        builder.setView(layout);
        dialogRef[0] = builder.create();
        dialogRef[0].show();
    }
    
    private void showCreateIndividualChatDialog() {
        if (currentUserId == null || currentUserId == -1) {
            if (currentUsername == null || currentUsername.isEmpty()) {
                SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                currentUsername = prefs.getString("username", "");
            }
            
            // ✅ Check if username is still empty
            if (currentUsername == null || currentUsername.isEmpty()) {
                Intent intent = new Intent(this, com.example.occasio.auth.LoginActivity.class);
                startActivity(intent);
                return;
            }
            
            
            chatApiService.getUserIdFromUsername(currentUsername, new ChatApiService.UserIdCallback() {
                @Override
                public void onSuccess(Long userId) {
                    currentUserId = userId;
                    runOnUiThread(() -> {
                        showCreateIndividualChatDialogInternal();
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        currentUserId = 1L;
                        showCreateIndividualChatDialogInternal();
                    });
                }
            });
            return;
        }
        
        showCreateIndividualChatDialogInternal();
    }
    
    private void showCreateIndividualChatDialogInternal() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Start Individual Chat");
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        android.widget.TextView friendsLabel = new android.widget.TextView(this);
        friendsLabel.setText("Select a Friend:");
        friendsLabel.setTextSize(16);
        friendsLabel.setPadding(0, 0, 0, 8);
        layout.addView(friendsLabel);
        
        android.widget.LinearLayout friendsContainer = new android.widget.LinearLayout(this);
        friendsContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        friendsContainer.setPadding(0, 8, 0, 16);
        layout.addView(friendsContainer);
        
        android.widget.TextView loadingText = new android.widget.TextView(this);
        loadingText.setText("Loading friends...");
        loadingText.setPadding(16, 16, 16, 16);
        loadingText.setGravity(android.view.Gravity.CENTER);
        loadingText.setVisibility(android.view.View.VISIBLE);
        friendsContainer.addView(loadingText);
        
        scrollView.addView(layout);
        builder.setView(scrollView);
        
        final java.util.List<org.json.JSONObject>[] friendsList = new java.util.List[]{new java.util.ArrayList<org.json.JSONObject>()};
        
        builder.setNegativeButton("Cancel", null);
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        if (currentUsername == null || currentUsername.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            currentUsername = prefs.getString("username", "");
            if (currentUsername.isEmpty()) {
                Intent intent = new Intent(this, com.example.occasio.auth.LoginActivity.class);
                startActivity(intent);
                return;
            }
        }
        
        String friendsUrl = com.example.occasio.utils.ServerConfig.FRIENDS_URL + "/" + currentUsername;
        com.android.volley.toolbox.JsonArrayRequest friendsRequest = new com.android.volley.toolbox.JsonArrayRequest(
            com.android.volley.Request.Method.GET,
            friendsUrl,
            null,
            response -> {
                try {
                    friendsList[0].clear();
                    for (int i = 0; i < response.length(); i++) {
                        friendsList[0].add(response.getJSONObject(i));
                    }
                    
                    runOnUiThread(() -> {
                        loadingText.setVisibility(android.view.View.GONE);
                        friendsContainer.removeAllViews();
                        
                        if (friendsList[0].isEmpty()) {
                            android.widget.TextView noFriendsText = new android.widget.TextView(MessagingActivity.this);
                            noFriendsText.setText("No friends available. Add friends first.");
                            noFriendsText.setPadding(16, 16, 16, 16);
                            noFriendsText.setGravity(android.view.Gravity.CENTER);
                            friendsContainer.addView(noFriendsText);
                            return;
                        }
                        
                        for (org.json.JSONObject friend : friendsList[0]) {
                            try {
                                Long friendId = friend.getLong("id");
                                String friendName = friend.optString("username", 
                                    friend.optString("firstName", "Unknown"));
                                
                                android.widget.Button friendButton = new android.widget.Button(MessagingActivity.this);
                                friendButton.setText(friendName);
                                friendButton.setPadding(16, 16, 16, 16);
                                friendButton.setTextSize(16);
                                friendButton.setBackgroundResource(R.drawable.button_secondary_fall);
                                friendButton.setTextColor(getResources().getColor(R.color.text_on_fall));
                                friendButton.setOnClickListener(v -> {
                                    if (dialog != null) {
                                        dialog.dismiss();
                                    }
                                    createIndividualChat(friendId, friendName);
                                });
                                friendsContainer.addView(friendButton);
                                
                                android.widget.TextView btnSpacer = new android.widget.TextView(MessagingActivity.this);
                                btnSpacer.setHeight(8);
                                friendsContainer.addView(btnSpacer);
                            } catch (Exception e) {
                                android.util.Log.e("MessagingActivity", "Error parsing friend: " + e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("MessagingActivity", "Error parsing friends: " + e.getMessage());
                    runOnUiThread(() -> {
                        loadingText.setText("Error loading friends");
                    });
                }
            },
            error -> {
                android.util.Log.e("MessagingActivity", "Error fetching friends: " + error.getMessage());
                runOnUiThread(() -> {
                    loadingText.setVisibility(android.view.View.GONE);
                    friendsContainer.removeAllViews();
                    
                    android.widget.TextView errorText = new android.widget.TextView(MessagingActivity.this);
                    errorText.setText("Error loading friends. Please try again later.");
                    errorText.setPadding(16, 16, 16, 16);
                    errorText.setGravity(android.view.Gravity.CENTER);
                    friendsContainer.addView(errorText);
                    
                });
            }
        );
        
        com.example.occasio.api.VolleySingleton.getInstance(this).addToRequestQueue(friendsRequest);
    }
    
    private void createIndividualChat(Long friendId, String friendName) {
        if (currentUserId == null) {
            return;
        }
        
        chatApiService.createDirectChat(currentUserId, friendId, new ChatApiService.ChatCallback() {
            @Override
            public void onSuccess(Long chatId) {
                runOnUiThread(() -> {
                    
                    Intent intent = new Intent(MessagingActivity.this, ChatActivity.class);
                    intent.putExtra("chatId", chatId);
                    intent.putExtra("chatName", friendName);
                    intent.putExtra("userId", currentUserId);
                    startActivity(intent);
                    
                    loadConversations();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    android.util.Log.e("MessagingActivity", "Error creating individual chat: " + error);
                    Toast.makeText(MessagingActivity.this, "Error creating chat: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void showCreateGroupChatDialogInternal() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Create Group Chat");
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        android.widget.TextView friendsLabel = new android.widget.TextView(this);
        friendsLabel.setText("Select Friends:");
        friendsLabel.setTextSize(16);
        friendsLabel.setPadding(0, 0, 0, 8);
        layout.addView(friendsLabel);
        
        android.widget.LinearLayout friendsContainer = new android.widget.LinearLayout(this);
        friendsContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        friendsContainer.setPadding(0, 8, 0, 16);
        layout.addView(friendsContainer);
        
        android.widget.TextView loadingText = new android.widget.TextView(this);
        loadingText.setText("Loading friends...");
        loadingText.setPadding(16, 16, 16, 16);
        loadingText.setGravity(android.view.Gravity.CENTER);
        loadingText.setVisibility(android.view.View.VISIBLE);
        friendsContainer.addView(loadingText);
        
        android.widget.TextView nameLabel = new android.widget.TextView(this);
        nameLabel.setText("Chat Name:");
        nameLabel.setTextSize(16);
        nameLabel.setPadding(0, 16, 0, 8);
        layout.addView(nameLabel);
        
        android.widget.EditText chatNameInput = new android.widget.EditText(this);
        chatNameInput.setHint("Enter chat name");
        chatNameInput.setPadding(8, 8, 8, 8);
        layout.addView(chatNameInput);
        
        scrollView.addView(layout);
        builder.setView(scrollView);
        
        final java.util.List<org.json.JSONObject>[] friendsList = new java.util.List[]{new java.util.ArrayList<org.json.JSONObject>()};
        final java.util.Set<Long>[] selectedFriendIds = new java.util.Set[]{new java.util.HashSet<Long>()};
        final Long userIdForRequest = currentUserId != null ? currentUserId : 1L;
        
        builder.setPositiveButton("Create", (dialog, which) -> {
            if (selectedFriendIds[0].isEmpty()) {
                return;
            }
            
            String chatName = chatNameInput.getText().toString().trim();
            if (chatName.isEmpty()) {
                chatName = "Group Chat";
            }
            
            createGroupChatWithFriends(selectedFriendIds[0], chatName);
        });
        
        builder.setNegativeButton("Cancel", null);
        
        android.app.AlertDialog dialog = builder.create();
        dialog.show();
        
        if (currentUsername == null || currentUsername.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            currentUsername = prefs.getString("username", "");
            if (currentUsername.isEmpty()) {
                Intent intent = new Intent(this, com.example.occasio.auth.LoginActivity.class);
                startActivity(intent);
                return;
            }
        }
        
        String friendsUrl = com.example.occasio.utils.ServerConfig.FRIENDS_URL + "/" + currentUsername;
        com.android.volley.toolbox.JsonArrayRequest friendsRequest = new com.android.volley.toolbox.JsonArrayRequest(
            com.android.volley.Request.Method.GET,
            friendsUrl,
            null,
            response -> {
                try {
                    friendsList[0].clear();
                    for (int i = 0; i < response.length(); i++) {
                        friendsList[0].add(response.getJSONObject(i));
                    }
                    
                    runOnUiThread(() -> {
                        loadingText.setVisibility(android.view.View.GONE);
                        friendsContainer.removeAllViews();
                        
                        if (friendsList[0].isEmpty()) {
                            android.widget.TextView noFriendsText = new android.widget.TextView(MessagingActivity.this);
                            noFriendsText.setText("No friends available. Add friends first.");
                            noFriendsText.setPadding(16, 16, 16, 16);
                            noFriendsText.setGravity(android.view.Gravity.CENTER);
                            friendsContainer.addView(noFriendsText);
                            return;
                        }
                        
                        for (org.json.JSONObject friend : friendsList[0]) {
                            try {
                                Long friendId = friend.getLong("id");
                                String friendName = friend.optString("username", 
                                    friend.optString("firstName", "Unknown"));
                                
                                android.widget.CheckBox checkBox = new android.widget.CheckBox(MessagingActivity.this);
                                checkBox.setText(friendName);
                                checkBox.setPadding(8, 8, 8, 8);
                                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                                    if (isChecked) {
                                        selectedFriendIds[0].add(friendId);
                                    } else {
                                        selectedFriendIds[0].remove(friendId);
                                    }
                                });
                                friendsContainer.addView(checkBox);
                            } catch (Exception e) {
                                android.util.Log.e("MessagingActivity", "Error parsing friend: " + e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("MessagingActivity", "Error parsing friends: " + e.getMessage());
                    runOnUiThread(() -> {
                        loadingText.setText("Error loading friends");
                    });
                }
            },
            error -> {
                android.util.Log.e("MessagingActivity", "Error fetching friends: " + error.getMessage());
                runOnUiThread(() -> {
                    loadingText.setVisibility(android.view.View.GONE);
                    friendsContainer.removeAllViews();
                    
                    android.widget.TextView errorText = new android.widget.TextView(MessagingActivity.this);
                    errorText.setText("Error loading friends. Please try again later.");
                    errorText.setPadding(16, 16, 16, 16);
                    errorText.setGravity(android.view.Gravity.CENTER);
                    friendsContainer.addView(errorText);
                    
                });
            }
        );
        
        com.example.occasio.api.VolleySingleton.getInstance(this).addToRequestQueue(friendsRequest);
    }
    
    private void createGroupChatWithFriends(java.util.Set<Long> friendIds, String chatName) {
        if (friendIds.isEmpty()) {
            return;
        }
        
        if (currentUserId == null) {
            return;
        }
        
        
        String groupName = chatName + " Group";
        String groupDescription = "Group chat with " + friendIds.size() + " friends";
        
        String createGroupUrl = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/groups";
        try {
            org.json.JSONObject groupRequest = new org.json.JSONObject();
            groupRequest.put("name", groupName);
            groupRequest.put("description", groupDescription);
            groupRequest.put("createdById", currentUserId);
            
            com.android.volley.toolbox.JsonObjectRequest createGroupRequest = new com.android.volley.toolbox.JsonObjectRequest(
                com.android.volley.Request.Method.POST,
                createGroupUrl,
                groupRequest,
                response -> {
                    try {
                        Long groupId = response.getLong("id");
                        android.util.Log.d("MessagingActivity", "Group created with ID: " + groupId + ", adding " + friendIds.size() + " members");
                        
                        // ✅ Add current user to the group first (they're the creator but might not be in members yet)
                        java.util.Set<Long> allMemberIds = new java.util.HashSet<>(friendIds);
                        allMemberIds.add(currentUserId); // Include creator
                        
                        // ✅ Track how many members have been added
                        final java.util.concurrent.atomic.AtomicInteger membersAdded = new java.util.concurrent.atomic.AtomicInteger(0);
                        final int totalMembers = allMemberIds.size();
                        
                        for (Long memberId : allMemberIds) {
                            String addMemberUrl = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/groups/" + groupId + "/members";
                            org.json.JSONObject memberRequest = new org.json.JSONObject();
                            memberRequest.put("userId", memberId);
                            
                            com.android.volley.toolbox.JsonObjectRequest addMemberRequest = new com.android.volley.toolbox.JsonObjectRequest(
                                com.android.volley.Request.Method.POST,
                                addMemberUrl,
                                memberRequest,
                                addResponse -> {
                                    int added = membersAdded.incrementAndGet();
                                    android.util.Log.d("MessagingActivity", "Member " + memberId + " added (" + added + "/" + totalMembers + ")");
                                    
                                    // ✅ Only create group chat after ALL members are added
                                    if (added == totalMembers) {
                                        android.util.Log.d("MessagingActivity", "All members added, creating group chat...");
                        chatApiService.createGroupChat(groupId, chatName, new ChatApiService.ChatCallback() {
                            @Override
                            public void onSuccess(Long chatId) {
                                runOnUiThread(() -> {
                                    
                                    Intent intent = new Intent(MessagingActivity.this, GroupChatActivity.class);
                                    intent.putExtra("chatId", chatId);
                                    intent.putExtra("chatName", chatName);
                                    intent.putExtra("is_group", true);
                                    intent.putExtra("userId", currentUserId);
                                    startActivity(intent);
                                    
                                                    // ✅ Reload conversations to show the new group chat
                                    loadConversations();
                                });
                            }
                            
                            @Override
                            public void onError(String error) {
                                runOnUiThread(() -> {
                                    android.util.Log.e("MessagingActivity", "Error creating group chat: " + error);
                                    Toast.makeText(MessagingActivity.this, "Group created but chat creation failed: " + error, Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                                    }
                                },
                                error -> {
                                    android.util.Log.e("MessagingActivity", "Error adding member " + memberId + ": " + error.getMessage());
                                    // ✅ Still try to create chat even if some members fail
                                    int added = membersAdded.incrementAndGet();
                                    if (added == totalMembers) {
                                        android.util.Log.d("MessagingActivity", "All member requests completed (some may have failed), creating group chat...");
                                        chatApiService.createGroupChat(groupId, chatName, new ChatApiService.ChatCallback() {
                                            @Override
                                            public void onSuccess(Long chatId) {
                                                runOnUiThread(() -> {
                                                    loadConversations();
                                                });
                                            }
                            
                                            @Override
                                            public void onError(String error) {
                                                runOnUiThread(() -> {
                                                    android.util.Log.e("MessagingActivity", "Error creating group chat: " + error);
                                                    Toast.makeText(MessagingActivity.this, "Group created but chat creation failed: " + error, Toast.LENGTH_LONG).show();
                                                });
                                            }
                                        });
                                    }
                                }
                            );
                            com.example.occasio.api.VolleySingleton.getInstance(MessagingActivity.this).addToRequestQueue(addMemberRequest);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("MessagingActivity", "Error parsing group response: " + e.getMessage());
                        runOnUiThread(() -> {
                        });
                    }
                },
                error -> {
                    android.util.Log.e("MessagingActivity", "Error creating group: " + error.getMessage());
                    runOnUiThread(() -> {
                    });
                }
            );
            
            com.example.occasio.api.VolleySingleton.getInstance(this).addToRequestQueue(createGroupRequest);
        } catch (Exception e) {
            android.util.Log.e("MessagingActivity", "Error creating group request: " + e.getMessage());
        }
    }
}
