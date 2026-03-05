package com.example.occasio.friends;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.example.occasio.R;
import com.example.occasio.api.VolleySingleton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MyFriendsActivity extends AppCompatActivity {

    private RecyclerView friendsRecyclerView;
    private TextView statusText;
    private TextView noFriendsText;
    private TextView friendsCountText;
    private RequestQueue requestQueue;
    private FriendListAdapter adapter;

    private List<JSONObject> myFriends = new ArrayList<>();
    private SharedPreferences sharedPreferences;
    private String currentUsername;

    private static final String BASE_URL = com.example.occasio.utils.ServerConfig.BASE_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_my_friends);
        } catch (Exception e) {
            Log.e("MyFriendsActivity", "Error setting content view: " + e.getMessage());
            e.printStackTrace();
            finish();
            return;
        }

        try {
            requestQueue = VolleySingleton.getInstance(getApplicationContext()).getRequestQueue();
            sharedPreferences = getSharedPreferences("FriendsPrefs", MODE_PRIVATE);
            
            // Get current username from SharedPreferences or Intent
            SharedPreferences userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            currentUsername = userPrefs.getString("username", "");
            if (currentUsername == null || currentUsername.isEmpty()) {
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
            
            loadMyFriends();
        } catch (Exception e) {
            Log.e("MyFriendsActivity", "Error in onCreate: " + e.getMessage());
            e.printStackTrace();
            finish();
        }
    }
    
    private void initializeViews() {
        friendsRecyclerView = findViewById(R.id.my_friends_recycler_view);
        statusText = findViewById(R.id.friends_status_tv);
        noFriendsText = findViewById(R.id.no_friends_tv);
        friendsCountText = findViewById(R.id.friends_count_tv);
        
        // Check if critical views are null
        if (friendsRecyclerView == null) {
            Log.e("MyFriendsActivity", "RecyclerView is null!");
            finish();
            return;
        }
    }
    
    private void setupRecyclerView() {
        if (friendsRecyclerView == null) {
            Log.e("MyFriendsActivity", "Cannot setup RecyclerView - it's null");
            return;
        }
        
        try {
            adapter = new FriendListAdapter(myFriends, new FriendListAdapter.OnFriendActionListener() {
                @Override
                public void onRemoveClick(JSONObject friend) {
                    try {
                        Long friendId = friend.getLong("id");
                        String username = friend.getString("username");
                        showDeleteConfirmation(friendId, username);
                    } catch (JSONException e) {
                        Log.e("MyFriendsActivity", "Error getting friend info: " + e.getMessage());
                    }
                }
            });
            friendsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            friendsRecyclerView.setAdapter(adapter);
        } catch (Exception e) {
            Log.e("MyFriendsActivity", "Error setting up RecyclerView: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void setupClickListeners() {
        // Search Friends button
        Button searchButton = findViewById(R.id.my_friends_search_btn);
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, SearchFriendsActivityVolley.class);
                intent.putExtra("username", currentUsername);
                startActivity(intent);
            });
        }
        
        // Friend Requests button
        Button requestsButton = findViewById(R.id.my_friends_requests_btn);
        if (requestsButton != null) {
            requestsButton.setOnClickListener(v -> {
                Intent intent = new Intent(this, FriendRequestActivity.class);
                intent.putExtra("username", currentUsername);
                startActivity(intent);
            });
        }
        
        // Refresh Friends List button
        Button refreshButton = findViewById(R.id.my_friends_refresh_btn);
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> {
                refreshFriendsList();
            });
        }
    }
    
    private void refreshFriendsList() {
        // Show loading state
        if (statusText != null) {
            statusText.setText("Refreshing friends list...");
        }
        
        // Disable refresh button temporarily to prevent multiple simultaneous requests
        Button refreshButton = findViewById(R.id.my_friends_refresh_btn);
        if (refreshButton != null) {
            refreshButton.setEnabled(false);
            refreshButton.setText("🔄 Refreshing...");
        }
        
        // Clear current list
        myFriends.clear();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        
        // Reload friends from server
        loadMyFriends();
        
        // Re-enable refresh button after a short delay (will be re-enabled in loadMyFriends callback)
        if (refreshButton != null) {
            refreshButton.postDelayed(() -> {
                refreshButton.setEnabled(true);
                refreshButton.setText("🔄 Refresh Friends List");
            }, 2000); // Re-enable after 2 seconds
        }
    }

    private void loadMyFriends() {
        String url = BASE_URL + "/api/friends/" + currentUsername;
        
        if (statusText != null) {
            statusText.setText("Loading your friends...");
        }
        
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        Log.d("MyFriendsActivity", "Friends loaded: " + response.length());
                        myFriends.clear();
                        try {
                            if (response != null) {
                                for (int i = 0; i < response.length(); i++) {
                                    JSONObject user = response.getJSONObject(i);
                                    if (user != null) {
                                        myFriends.add(user);
                                    }
                                }
                            }

                            runOnUiThread(() -> {
                                updateUI();
                                // Re-enable refresh button after successful load
                                Button refreshButton = findViewById(R.id.my_friends_refresh_btn);
                                if (refreshButton != null) {
                                    refreshButton.setEnabled(true);
                                    refreshButton.setText("🔄 Refresh Friends List");
                                }
                            });
                        } catch (JSONException e) {
                            Log.e("MyFriendsActivity", "Error parsing friends data: " + e.getMessage());
                            e.printStackTrace();
                            runOnUiThread(() -> {
                                if (statusText != null) {
                                    statusText.setText("Error parsing friends data.");
                                }
                            });
                        } catch (Exception e) {
                            Log.e("MyFriendsActivity", "Unexpected error: " + e.getMessage());
                            e.printStackTrace();
                            runOnUiThread(() -> {
                                if (statusText != null) {
                                    statusText.setText("Error loading friends.");
                                }
                            });
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("MyFriendsActivity", "Failed to load friends: " + error.toString());
                        runOnUiThread(() -> {
                            if (statusText != null) {
                                statusText.setText("Failed to load friends. Please check backend server.");
                            }
                            Toast.makeText(MyFriendsActivity.this, "Network error. Please try again.", Toast.LENGTH_LONG).show();
                            // Re-enable refresh button on error
                            Button refreshButton = findViewById(R.id.my_friends_refresh_btn);
                            if (refreshButton != null) {
                                refreshButton.setEnabled(true);
                                refreshButton.setText("🔄 Refresh Friends List");
                            }
                        });
                    }
                }
        );
        jsonArrayRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(jsonArrayRequest);
    }
    
    private void updateUI() {
        try {
            if (friendsCountText != null) {
                friendsCountText.setText("My Friends (" + myFriends.size() + ")");
            }
            
            if (myFriends.isEmpty()) {
                if (noFriendsText != null) {
                    noFriendsText.setVisibility(View.VISIBLE);
                }
                if (statusText != null) {
                    statusText.setText("You have no friends yet. Search for some!");
                }
            } else {
                if (noFriendsText != null) {
                    noFriendsText.setVisibility(View.GONE);
                }
                if (statusText != null) {
                    statusText.setText("You have " + myFriends.size() + " friend(s).");
                }
            }
            
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        } catch (Exception e) {
            Log.e("MyFriendsActivity", "Error updating UI: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showDeleteConfirmation(Long friendId, String username) {
        new AlertDialog.Builder(this)
                .setTitle("🗑️ Remove Friend")
                .setMessage("Are you sure you want to remove " + username + " from your friends list?")
                .setPositiveButton("Yes, Remove", (dialog, which) -> deleteFriend(friendId, username))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFriend(Long friendId, String friendUsername) {
        String url = BASE_URL + "/api/friends/remove?username=" + currentUsername + "&friendUsername=" + friendUsername;
        
        com.android.volley.toolbox.StringRequest deleteRequest = new com.android.volley.toolbox.StringRequest(
            Request.Method.DELETE,
            url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    // Remove from local list
                    for (int i = 0; i < myFriends.size(); i++) {
                        try {
                            if (myFriends.get(i).getString("username").equals(friendUsername)) {
                                myFriends.remove(i);
                                break;
                            }
                        } catch (JSONException e) {
                            Log.e("MyFriendsActivity", "Error finding friend to delete: " + e.getMessage());
                        }
                    }
                    updateUI();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMessage = "Failed to remove friend";
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        if (statusCode == 404) {
                            // Friend already removed, remove from local list
                            for (int i = 0; i < myFriends.size(); i++) {
                                try {
                                    if (myFriends.get(i).getString("username").equals(friendUsername)) {
                                        myFriends.remove(i);
                                        break;
                                    }
                                } catch (JSONException e) {
                                    Log.e("MyFriendsActivity", "Error finding friend to delete: " + e.getMessage());
                                }
                            }
                            updateUI();
                            return;
                        }
                        errorMessage += " (HTTP " + statusCode + ")";
                    }
                    Log.e("MyFriendsActivity", "Failed to remove friend: " + error.getMessage());
                }
            }
        );
        
        deleteRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(deleteRequest);
    }
}
