package com.example.occasio.messaging;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import com.example.occasio.base.BaseNavigationActivity;
import com.example.occasio.friends.MyFriendsActivity;
import com.example.occasio.friends.MyGroupsActivity;
import com.example.occasio.services.ChatApiService;
import android.content.SharedPreferences;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class ChatListActivity extends BaseNavigationActivity {
    
    private RecyclerView friendsRecyclerView;
    private RecyclerView groupsRecyclerView;
    private ChatListAdapter friendsAdapter;
    private ChatListAdapter groupsAdapter;
    private List<ChatItem> friendsList;
    private List<ChatItem> groupsList;
    private TextView friendsHeader;
    private TextView groupsHeader;
    private LinearLayout friendsEmptyState;
    private LinearLayout groupsEmptyState;
    private Button friendsViewFriendsBtn;
    private Button groupsViewGroupsBtn;
    private ChatApiService chatApiService;
    private Long currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        initializeViews();
        setupRecyclerViews();
        setupClickListeners();
        
        // Setup bottom navigation
        setupBottomNavigation();
        
        chatApiService = new ChatApiService(this);
        fetchUserId();
        
        loadChats();
    }

    private void initializeViews() {
        try {
            friendsRecyclerView = findViewById(R.id.friends_recycler_view);
            groupsRecyclerView = findViewById(R.id.groups_recycler_view);
            friendsHeader = findViewById(R.id.friends_header);
            groupsHeader = findViewById(R.id.groups_header);
            friendsEmptyState = findViewById(R.id.friends_empty_state);
            groupsEmptyState = findViewById(R.id.groups_empty_state);
            friendsViewFriendsBtn = findViewById(R.id.friends_view_friends_btn);
            groupsViewGroupsBtn = findViewById(R.id.groups_view_groups_btn);
            
            if (friendsRecyclerView == null || groupsRecyclerView == null) {
                android.util.Log.e("ChatListActivity", "RecyclerViews not found in layout!");
            }
            
            friendsList = new ArrayList<>();
            groupsList = new ArrayList<>();
        } catch (Exception e) {
            android.util.Log.e("ChatListActivity", "Error initializing views: " + e.getMessage(), e);
        }
    }

    private void setupRecyclerViews() {
        try {
            if (friendsRecyclerView != null) {
                friendsAdapter = new ChatListAdapter(friendsList, this::onChatItemClick);
                friendsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                friendsRecyclerView.setAdapter(friendsAdapter);
            }
            
            if (groupsRecyclerView != null) {
                groupsAdapter = new ChatListAdapter(groupsList, this::onChatItemClick);
                groupsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                groupsRecyclerView.setAdapter(groupsAdapter);
            }
            
            android.util.Log.d("ChatListActivity", "RecyclerViews setup complete. Friends: " + friendsList.size() + ", Groups: " + groupsList.size());
        } catch (Exception e) {
            android.util.Log.e("ChatListActivity", "Error setting up RecyclerViews: " + e.getMessage(), e);
        }
    }


    private void onChatItemClick(ChatItem chatItem) {
        android.util.Log.d("ChatListActivity", "Chat item clicked: " + chatItem.getName());
        
        try {
            Intent intent;
            
            if (chatItem.isGroup()) {
                android.util.Log.d("ChatListActivity", "Creating GroupChatActivity intent");
                intent = new Intent(this, GroupChatActivity.class);
            } else {
                android.util.Log.d("ChatListActivity", "Creating MessagingActivity intent");
                intent = new Intent(this, MessagingActivity.class);
            }
            
            intent.putExtra("chat_id", chatItem.getId());
            intent.putExtra("chat_name", chatItem.getName());
            intent.putExtra("is_group", chatItem.isGroup());
            
            android.util.Log.d("ChatListActivity", "Starting activity with extras: " + 
                "chat_id=" + chatItem.getId() + 
                ", chat_name=" + chatItem.getName() + 
                ", is_group=" + chatItem.isGroup());
            
            startActivity(intent);
            android.util.Log.d("ChatListActivity", "Activity started successfully");
            
        } catch (Exception e) {
            android.util.Log.e("ChatListActivity", "Error starting activity: " + e.getMessage(), e);
        }
    }

    private void fetchUserId() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");
        
        if (username.isEmpty()) {
            android.util.Log.e("ChatListActivity", "Username not found in preferences");
            loadSampleData(); // Fallback to sample data
            return;
        }
        
        String url = com.example.occasio.utils.ServerConfig.USER_INFO_BY_USERNAME + username;
        com.android.volley.toolbox.JsonObjectRequest request = new com.android.volley.toolbox.JsonObjectRequest(
            com.android.volley.Request.Method.GET,
            url,
            null,
            response -> {
                try {
                    currentUserId = response.getLong("id");
                    android.util.Log.d("ChatListActivity", "User ID fetched: " + currentUserId);
                    loadChats(); // Load chats after getting user ID
                } catch (Exception e) {
                    android.util.Log.e("ChatListActivity", "Error parsing user ID: " + e.getMessage());
                    loadSampleData(); // Fallback to sample data
                }
            },
            error -> {
                android.util.Log.e("ChatListActivity", "Error fetching user ID: " + error.getMessage());
                loadSampleData(); // Fallback to sample data
            }
        );
        
        com.example.occasio.api.VolleySingleton.getInstance(this).addToRequestQueue(request);
    }
    
    private void loadChats() {
        if (currentUserId == null || chatApiService == null) {
            android.util.Log.w("ChatListActivity", "User ID or ChatApiService not available, loading sample data");
            loadSampleData();
            return;
        }
        
        // Load direct and group chats separately
        loadDirectChats();
        loadGroupChats();
    }
    
    private void loadDirectChats() {
        if (currentUserId == null || chatApiService == null) {
            return;
        }
        
        chatApiService.getDirectChats(currentUserId, new ChatApiService.ChatsListCallback() {
            @Override
            public void onSuccess(List<org.json.JSONObject> chats) {
                runOnUiThread(() -> {
                    friendsList.clear();
                    try {
                        for (org.json.JSONObject chat : chats) {
                            String chatId = String.valueOf(chat.getLong("id"));
                            String chatName = chat.optString("name", "Unknown");
                            String lastMessage = chat.optString("lastMessage", "");
                            String lastMessageAt = chat.optString("lastMessageAt", "");
                            friendsList.add(new ChatItem(chatId, chatName, lastMessage, lastMessageAt, false, ""));
                        }
                        friendsAdapter.notifyDataSetChanged();
                        updateEmptyStates();
                    } catch (Exception e) {
                        android.util.Log.e("ChatListActivity", "Error parsing direct chats: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("ChatListActivity", "Error loading direct chats: " + error);
            }
        });
    }
    
    private void loadGroupChats() {
        if (currentUserId == null || chatApiService == null) {
            return;
        }
        
        chatApiService.getGroupChats(currentUserId, new ChatApiService.ChatsListCallback() {
            @Override
            public void onSuccess(List<org.json.JSONObject> chats) {
                runOnUiThread(() -> {
                    groupsList.clear();
                    try {
                        for (org.json.JSONObject chat : chats) {
                            String chatId = String.valueOf(chat.getLong("id"));
                            String chatName = chat.optString("name", "Unknown Group");
                            String lastMessage = chat.optString("lastMessage", "");
                            String lastMessageAt = chat.optString("lastMessageAt", "");
                            groupsList.add(new ChatItem(chatId, chatName, lastMessage, lastMessageAt, true, ""));
                        }
                        groupsAdapter.notifyDataSetChanged();
                        updateEmptyStates();
                    } catch (Exception e) {
                        android.util.Log.e("ChatListActivity", "Error parsing group chats: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("ChatListActivity", "Error loading group chats: " + error);
            }
        });
    }
    
    private void loadSampleData() {
        friendsList.add(new ChatItem("1", "Alice Johnson", "Hey! How are you?", "2 min ago", false, "alice_avatar"));
        groupsList.add(new ChatItem("g1", "Study Group", "Sarah: Let's meet at 3pm", "10 min ago", true, "study_group_avatar"));

        friendsAdapter.notifyDataSetChanged();
        groupsAdapter.notifyDataSetChanged();
        
        updateEmptyStates();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateEmptyStates();
    }
    
    private void updateEmptyStates() {
        if (friendsList.isEmpty()) {
            if (friendsRecyclerView != null) {
                friendsRecyclerView.setVisibility(View.GONE);
            }
            if (friendsEmptyState != null) {
                friendsEmptyState.setVisibility(View.VISIBLE);
            }
        } else {
            if (friendsRecyclerView != null) {
                friendsRecyclerView.setVisibility(View.VISIBLE);
            }
            if (friendsEmptyState != null) {
                friendsEmptyState.setVisibility(View.GONE);
            }
        }
        
        if (groupsList.isEmpty()) {
            if (groupsRecyclerView != null) {
                groupsRecyclerView.setVisibility(View.GONE);
            }
            if (groupsEmptyState != null) {
                groupsEmptyState.setVisibility(View.VISIBLE);
            }
        } else {
            if (groupsRecyclerView != null) {
                groupsRecyclerView.setVisibility(View.VISIBLE);
            }
            if (groupsEmptyState != null) {
                groupsEmptyState.setVisibility(View.GONE);
            }
        }
    }

    private void setupClickListeners() {
        // Back button is hidden now, navigation handled by bottom nav
        if (friendsViewFriendsBtn != null) {
            friendsViewFriendsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(ChatListActivity.this, MyFriendsActivity.class);
                startActivity(intent);
            });
        }
        
        if (groupsViewGroupsBtn != null) {
            groupsViewGroupsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(ChatListActivity.this, MyGroupsActivity.class);
                startActivity(intent);
            });
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}

