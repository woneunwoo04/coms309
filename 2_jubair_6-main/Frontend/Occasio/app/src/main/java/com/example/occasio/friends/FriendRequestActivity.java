package com.example.occasio.friends;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import com.example.occasio.services.FriendshipApiService;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class FriendRequestActivity extends AppCompatActivity {
    
    private RecyclerView recyclerView;
    private FriendRequestAdapter adapter;
    private TextView statusText;
    private Button pendingTabButton;
    private Button sentTabButton;
    private FriendshipApiService friendshipApiService;
    private String currentUsername;
    private List<JSONObject> pendingRequests = new ArrayList<>();
    private List<JSONObject> sentRequests = new ArrayList<>();
    private boolean showingPending = true;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_requests);
        
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        currentUsername = prefs.getString("username", "");
        if (currentUsername.isEmpty()) {
            Intent intent = getIntent();
            if (intent != null) {
                currentUsername = intent.getStringExtra("username");
            }
        }
        
            if (currentUsername == null || currentUsername.isEmpty()) {
                finish();
                return;
            }
        
        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        
        friendshipApiService = new FriendshipApiService(this);
        loadPendingRequests();
    }
    
    private void initializeViews() {
        recyclerView = findViewById(R.id.friend_requests_recycler_view);
        statusText = findViewById(R.id.friend_requests_status_tv);
        pendingTabButton = findViewById(R.id.friend_requests_pending_tab);
        sentTabButton = findViewById(R.id.friend_requests_sent_tab);
        
        // Check if views are null to prevent crashes
        if (recyclerView == null || statusText == null || 
            pendingTabButton == null || sentTabButton == null) {
            finish();
            return;
        }
    }
    
    private void setupRecyclerView() {
        adapter = new FriendRequestAdapter(new ArrayList<>(), showingPending, new FriendRequestAdapter.OnRequestActionListener() {
            @Override
            public void onAcceptClick(JSONObject friendship) {
                acceptFriendRequest(friendship);
            }
            
            @Override
            public void onRejectClick(JSONObject friendship) {
                rejectFriendRequest(friendship);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }
    
    private void setupClickListeners() {
        Button backButton = findViewById(R.id.friend_requests_back_btn);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        if (pendingTabButton != null) {
            pendingTabButton.setOnClickListener(v -> {
                showingPending = true;
                updateTabButtons();
                loadPendingRequests();
            });
        }
        
        if (sentTabButton != null) {
            sentTabButton.setOnClickListener(v -> {
                showingPending = false;
                updateTabButtons();
                loadSentRequests();
            });
        }
    }
    
    private void updateTabButtons() {
        if (pendingTabButton != null && sentTabButton != null) {
            if (showingPending) {
                pendingTabButton.setAlpha(1.0f);
                sentTabButton.setAlpha(0.5f);
            } else {
                pendingTabButton.setAlpha(0.5f);
                sentTabButton.setAlpha(1.0f);
            }
        }
    }
    
    private void loadPendingRequests() {
        if (statusText == null || friendshipApiService == null) {
            return;
        }
        statusText.setText("Loading pending requests...");
        friendshipApiService.getPendingRequests(currentUsername, new FriendshipApiService.FriendshipsListCallback() {
            @Override
            public void onSuccess(List<JSONObject> friendships) {
                runOnUiThread(() -> {
                    try {
                    pendingRequests.clear();
                    pendingRequests.addAll(friendships);
                        if (adapter != null) {
                    adapter.updateRequests(pendingRequests, true);
                        }
                    updateStatusText();
                    } catch (Exception e) {
                        Log.e("FriendRequestActivity", "Error updating pending requests: " + e.getMessage());
                        e.printStackTrace();
                        if (statusText != null) {
                            statusText.setText("Error displaying requests");
                        }
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e("FriendRequestActivity", "Error loading pending requests: " + error);
                    if (statusText != null) {
                    statusText.setText("Error loading pending requests: " + error);
                    }
                    Toast.makeText(FriendRequestActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void loadSentRequests() {
        if (statusText == null || friendshipApiService == null) {
            return;
        }
        statusText.setText("Loading sent requests...");
        friendshipApiService.getSentRequests(currentUsername, new FriendshipApiService.FriendshipsListCallback() {
            @Override
            public void onSuccess(List<JSONObject> friendships) {
                runOnUiThread(() -> {
                    try {
                    sentRequests.clear();
                    sentRequests.addAll(friendships);
                        if (adapter != null) {
                    adapter.updateRequests(sentRequests, false);
                        }
                    updateStatusText();
                    } catch (Exception e) {
                        Log.e("FriendRequestActivity", "Error updating sent requests: " + e.getMessage());
                        e.printStackTrace();
                        if (statusText != null) {
                            statusText.setText("Error displaying requests");
                        }
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e("FriendRequestActivity", "Error loading sent requests: " + error);
                    if (statusText != null) {
                    statusText.setText("Error loading sent requests: " + error);
                    }
                    Toast.makeText(FriendRequestActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void updateStatusText() {
        if (showingPending) {
            if (pendingRequests.isEmpty()) {
                statusText.setText("No pending friend requests");
            } else {
                statusText.setText(pendingRequests.size() + " pending request(s)");
            }
        } else {
            if (sentRequests.isEmpty()) {
                statusText.setText("No sent friend requests");
            } else {
                statusText.setText(sentRequests.size() + " sent request(s)");
            }
        }
    }
    
    private void acceptFriendRequest(JSONObject friendship) {
        try {
            Long friendshipId = friendship.getLong("id");
            friendshipApiService.acceptFriendRequest(friendshipId, new FriendshipApiService.FriendshipCallback() {
                @Override
                public void onSuccess(JSONObject acceptedFriendship) {
                    runOnUiThread(() -> {
                        loadPendingRequests();
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(FriendRequestActivity.this, "Error accepting request: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (JSONException e) {
            Log.e("FriendRequestActivity", "Error parsing friendship ID: " + e.getMessage());
            Toast.makeText(this, "Error parsing request data", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void rejectFriendRequest(JSONObject friendship) {
        try {
            Long friendshipId = friendship.getLong("id");
            friendshipApiService.rejectFriendRequest(friendshipId, new FriendshipApiService.VoidCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        loadPendingRequests();
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(FriendRequestActivity.this, "Error rejecting request: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (JSONException e) {
            Log.e("FriendRequestActivity", "Error parsing friendship ID: " + e.getMessage());
        }
    }
    
    // Adapter for displaying friend requests
    private static class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.RequestViewHolder> {
        private List<JSONObject> requests;
        private boolean isPending;
        private OnRequestActionListener listener;
        
        public interface OnRequestActionListener {
            void onAcceptClick(JSONObject friendship);
            void onRejectClick(JSONObject friendship);
        }
        
        public FriendRequestAdapter(List<JSONObject> requests, boolean isPending, OnRequestActionListener listener) {
            this.requests = requests;
            this.isPending = isPending;
            this.listener = listener;
        }
        
        public void updateRequests(List<JSONObject> newRequests, boolean isPending) {
            this.requests = newRequests;
            this.isPending = isPending;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friend_request, parent, false);
            return new RequestViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
            JSONObject friendship = requests.get(position);
            holder.bind(friendship, isPending, listener);
        }
        
        @Override
        public int getItemCount() {
            return requests != null ? requests.size() : 0;
        }
        
        static class RequestViewHolder extends RecyclerView.ViewHolder {
            private TextView usernameText;
            private TextView statusText;
            private Button acceptButton;
            private Button rejectButton;
            
            public RequestViewHolder(@NonNull View itemView) {
                super(itemView);
                usernameText = itemView.findViewById(R.id.request_username);
                statusText = itemView.findViewById(R.id.request_status);
                acceptButton = itemView.findViewById(R.id.request_accept_btn);
                rejectButton = itemView.findViewById(R.id.request_reject_btn);
                
                // Check if views are null to prevent crashes
                if (usernameText == null || statusText == null || 
                    acceptButton == null || rejectButton == null) {
                    Log.e("FriendRequestAdapter", "Some views are null in RequestViewHolder");
                }
            }
            
            public void bind(JSONObject friendship, boolean isPending, OnRequestActionListener listener) {
                if (usernameText == null || statusText == null) {
                    Log.e("FriendRequestAdapter", "Views are null, cannot bind");
                    return;
                }
                
                try {
                    // Backend returns requester and addressee, not user/friend
                    JSONObject user = null;
                    if (isPending) {
                        // For pending requests, the requester is the one who sent the request
                        if (friendship.has("requester")) {
                            user = friendship.getJSONObject("requester");
                        } else if (friendship.has("user")) {
                            // Fallback for legacy format
                            user = friendship.getJSONObject("user");
                        }
                    } else {
                        // For sent requests, the addressee is the one who received the request
                        if (friendship.has("addressee")) {
                            user = friendship.getJSONObject("addressee");
                        } else if (friendship.has("friend")) {
                            // Fallback for legacy format
                            user = friendship.getJSONObject("friend");
                        }
                    }
                    
                    if (user == null) {
                        Log.e("FriendRequestAdapter", "Could not find user in friendship: " + friendship.toString());
                        usernameText.setText("Unknown User");
                        statusText.setText("Unable to load user info");
                        return;
                    }
                    
                    String username = user.optString("username", "Unknown");
                    usernameText.setText(username);
                    
                    if (isPending) {
                        statusText.setText("Wants to be your friend");
                        if (acceptButton != null && rejectButton != null) {
                        acceptButton.setVisibility(View.VISIBLE);
                        rejectButton.setVisibility(View.VISIBLE);
                        
                        acceptButton.setOnClickListener(v -> {
                            if (listener != null) {
                                listener.onAcceptClick(friendship);
                            }
                        });
                        
                        rejectButton.setOnClickListener(v -> {
                            if (listener != null) {
                                listener.onRejectClick(friendship);
                            }
                        });
                        }
                    } else {
                        statusText.setText("Request sent - Pending");
                        if (acceptButton != null && rejectButton != null) {
                        acceptButton.setVisibility(View.GONE);
                        rejectButton.setVisibility(View.GONE);
                        }
                    }
                } catch (JSONException e) {
                    Log.e("FriendRequestAdapter", "Error parsing friendship: " + e.getMessage());
                    e.printStackTrace();
                    Log.e("FriendRequestAdapter", "Friendship JSON: " + friendship.toString());
                    usernameText.setText("Error");
                    statusText.setText("Unable to load");
                } catch (Exception e) {
                    Log.e("FriendRequestAdapter", "Unexpected error: " + e.getMessage());
                    e.printStackTrace();
                    usernameText.setText("Error");
                    statusText.setText("Unable to load");
                }
            }
        }
    }
}

