package com.example.occasio.friends;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.occasio.R;
import com.example.occasio.api.VolleySingleton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SearchFriendsActivityVolley extends AppCompatActivity {

    private LinearLayout searchResultsContainer;
    private TextView statusText;
    private EditText searchInput;
    private RequestQueue requestQueue;

    // REAL BACKEND
    private static final String BASE_URL = com.example.occasio.utils.ServerConfig.BASE_URL;
    private static final String GET_ALL_USERS_URL = com.example.occasio.utils.ServerConfig.USER_INFO_URL;
    private static final String SEND_FRIEND_REQUEST_URL = BASE_URL + "/api/friends/request";
    private static final String CHECK_FRIENDSHIP_URL = BASE_URL + "/api/friends/check";
    
    private String currentUsername;

    private List<JSONObject> allUsers = new ArrayList<>();
    private List<JSONObject> filteredUsers = new ArrayList<>();
    private com.example.occasio.services.FriendshipApiService friendshipApiService;
    
    // Cache friendship statuses to avoid multiple API calls
    private java.util.Map<String, String> friendshipStatusCache = new java.util.HashMap<>(); // username -> status: "FRIEND", "PENDING", "NONE"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_friends);

        requestQueue = VolleySingleton.getInstance(getApplicationContext()).getRequestQueue();
        friendshipApiService = new com.example.occasio.services.FriendshipApiService(this);
        
        // Get current username from SharedPreferences or Intent
        SharedPreferences userPrefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        currentUsername = userPrefs.getString("username", "");
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
        setupClickListeners();
        setupSearchFunctionality();
        // Don't load all users initially - wait for user input
        if (statusText != null) {
            statusText.setText("Type a username or email to search for friends...");
        }
    }
    
    private void initializeViews() {
        searchInput = findViewById(R.id.search_friends_input);
        statusText = findViewById(R.id.search_friends_status_tv);
        searchResultsContainer = findViewById(R.id.search_friends_results_container);
        
        // Check if views are null
        if (searchInput == null || statusText == null || searchResultsContainer == null) {
            finish();
            return;
        }
    }
    
    private void setupClickListeners() {
        Button backButton = findViewById(R.id.search_friends_back_btn);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        Button myFriendsBtn = findViewById(R.id.search_friends_my_friends_btn);
        if (myFriendsBtn != null) {
            myFriendsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(this, MyFriendsActivity.class);
                intent.putExtra("username", currentUsername);
                startActivity(intent);
            });
        }
        
        Button requestsBtn = findViewById(R.id.search_friends_requests_btn);
        if (requestsBtn != null) {
            requestsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(this, FriendRequestActivity.class);
                intent.putExtra("username", currentUsername);
                startActivity(intent);
            });
        }
    }

    private void setupSearchFunctionality() {
        // Search on Enter key press
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.getAction() == android.view.KeyEvent.ACTION_DOWN && 
                 event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                performSearch();
                // Hide keyboard
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                    getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });
        
        // Search button click
        Button searchButton = findViewById(R.id.search_friends_search_btn);
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> performSearch());
        }
    }
    
    private void performSearch() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            if (statusText != null) {
                statusText.setText("Please enter a username or email to search...");
            }
            searchResultsContainer.removeAllViews();
            filteredUsers.clear();
            return;
        }
        
        filterUsers(query);
    }

    private void loadAllUsers(Runnable onComplete) {
        Log.d("SearchFriends", "Loading users from: " + GET_ALL_USERS_URL);
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                GET_ALL_USERS_URL,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        Log.d("SearchFriends", "Users loaded: " + response.length());
                        allUsers.clear();
                        try {
                            // Filter out current user from the start
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject user = response.getJSONObject(i);
                                String username = user.optString("username", "");
                                // Don't add current user to the list
                                if (!username.equals(currentUsername)) {
                                    allUsers.add(user);
                                }
                            }
                            Log.d("SearchFriends", "Total users (excluding self): " + allUsers.size());
                            
                            // Execute callback if provided
                            if (onComplete != null) {
                                runOnUiThread(onComplete);
                            }
                        } catch (JSONException e) {
                            Log.e("SearchFriends", "Error parsing user data: " + e.getMessage());
                            e.printStackTrace();
                            if (statusText != null) {
                                statusText.setText("Error parsing user data: " + e.getMessage());
                            }
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("SearchFriends", "Failed to load users: " + error.toString());
                        if (error.networkResponse != null) {
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                Log.e("SearchFriends", "Error response body: " + responseBody);
                                Log.e("SearchFriends", "Status code: " + error.networkResponse.statusCode);
                            } catch (Exception e) {
                                Log.e("SearchFriends", "Error reading error response: " + e.getMessage());
                            }
                        }
                        if (statusText != null) {
                            statusText.setText("Failed to load users. Please check backend server.");
                        }
                        Toast.makeText(SearchFriendsActivityVolley.this, "Network error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
        jsonArrayRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(jsonArrayRequest);
    }

    private void filterUsers(String query) {
        searchResultsContainer.removeAllViews();
        filteredUsers.clear();
        
        if (query == null || query.trim().isEmpty()) {
            statusText.setText("Type a username or email to search for friends...");
            return;
        }
        
        // If users haven't been loaded yet, load them first
        if (allUsers.isEmpty()) {
            statusText.setText("Loading users...");
            loadAllUsers(() -> {
                // After loading, filter with the current query
                filterUsersAfterLoad(query);
            });
            return;
        }

        filterUsersAfterLoad(query);
    }
    
    private void filterUsersAfterLoad(String query) {
        if (query == null || query.trim().isEmpty()) {
            statusText.setText("Type a username or email to search for friends...");
            return;
        }
        
        String lowerQuery = query.toLowerCase().trim();
        for (JSONObject user : allUsers) {
            try {
                String username = user.optString("username", "");
                String email = user.optString("email", "");
                
                // Skip current user
                if (username.equals(currentUsername)) {
                    continue;
                }
                
                if (username.toLowerCase().contains(lowerQuery) ||
                        email.toLowerCase().contains(lowerQuery)) {
                    filteredUsers.add(user);
                }
            } catch (Exception e) {
                Log.e("SearchFriends", "Error filtering user: " + e.getMessage());
            }
        }

        if (filteredUsers.isEmpty()) {
            statusText.setText("No users found matching '" + query + "'");
        } else {
            statusText.setText("Found " + filteredUsers.size() + " users matching '" + query + "'");
            displaySearchResults();
        }
    }

    private void displaySearchResults() {
        searchResultsContainer.removeAllViews();
        friendshipStatusCache.clear(); // Clear cache for fresh results
        
        // First, check friendship status for all filtered users
        checkFriendshipStatuses();
    }
    
    private void checkFriendshipStatuses() {
        if (filteredUsers.isEmpty()) {
            return;
        }
        
        final int[] checkedCount = {0};
        final int totalUsers = filteredUsers.size();
        
        for (JSONObject user : filteredUsers) {
            try {
                String username = user.getString("username");
                
                // Skip if it's the current user
                if (username.equals(currentUsername)) {
                    checkedCount[0]++;
                    if (checkedCount[0] == totalUsers) {
                        renderSearchResults();
                    }
                    continue;
                }
                
                // Check friendship status
                friendshipApiService.checkFriendship(currentUsername, username, 
                    new com.example.occasio.services.FriendshipApiService.BooleanCallback() {
                        @Override
                        public void onSuccess(boolean areFriends) {
                            synchronized (friendshipStatusCache) {
                                friendshipStatusCache.put(username, areFriends ? "FRIEND" : "NONE");
                                checkedCount[0]++;
                                
                                // Check if all statuses are loaded
                                if (checkedCount[0] == totalUsers) {
                                    runOnUiThread(() -> renderSearchResults());
                                }
                            }
                        }
                        
                        @Override
                        public void onError(String error) {
                            synchronized (friendshipStatusCache) {
                                // Default to "NONE" if check fails
                                try {
                                    friendshipStatusCache.put(user.getString("username"), "NONE");
                                } catch (JSONException e) {
                                    Log.e("JSON Error", "Error getting username: " + e.getMessage());
                                }
                                checkedCount[0]++;
                                
                                if (checkedCount[0] == totalUsers) {
                                    runOnUiThread(() -> renderSearchResults());
                                }
                            }
                        }
                    });
            } catch (JSONException e) {
                Log.e("JSON Error", "Error checking friendship: " + e.getMessage());
                checkedCount[0]++;
                if (checkedCount[0] == totalUsers) {
                    renderSearchResults();
                }
            }
        }
    }
    
    private void renderSearchResults() {
        searchResultsContainer.removeAllViews();
        
        // Also check for pending requests
        loadPendingRequestsAndRender();
    }
    
    private void loadPendingRequestsAndRender() {
        // Get sent requests to check for pending status
        friendshipApiService.getSentRequests(currentUsername, 
            new com.example.occasio.services.FriendshipApiService.FriendshipsListCallback() {
                @Override
                public void onSuccess(List<JSONObject> sentRequests) {
                    // Mark users with pending requests
                    for (JSONObject request : sentRequests) {
                        try {
                            JSONObject friendship = request;
                            JSONObject addressee = friendship.getJSONObject("addressee");
                            String username = addressee.getString("username");
                            if (friendshipStatusCache.containsKey(username)) {
                                friendshipStatusCache.put(username, "PENDING");
                            }
                        } catch (JSONException e) {
                            Log.e("JSON Error", "Error parsing sent request: " + e.getMessage());
                        }
                    }
                    
                    // Now render all results
                    renderUserItems();
                }
                
                @Override
                public void onError(String error) {
                    // Render anyway if we can't get pending requests
                    renderUserItems();
                }
            });
    }
    
    private void renderUserItems() {
        if (searchResultsContainer == null) {
            return;
        }
        
        searchResultsContainer.removeAllViews();
        
        for (JSONObject user : filteredUsers) {
            try {
                String username = user.optString("username", "Unknown");
                String email = user.optString("email", "");
                
                // Skip current user
                if (username.equals(currentUsername)) {
                    continue;
                }

                // Inflate the item layout
                View userItemView = LayoutInflater.from(this).inflate(R.layout.item_search_friend, searchResultsContainer, false);
                
                TextView usernameText = userItemView.findViewById(R.id.search_friend_username);
                TextView emailText = userItemView.findViewById(R.id.search_friend_email);
                Button actionBtn = userItemView.findViewById(R.id.search_friend_action_btn);
                
                if (usernameText != null) {
                    usernameText.setText(username);
                }
                if (emailText != null) {
                    emailText.setText(email);
                }
                
                if (actionBtn != null) {
                    String status = friendshipStatusCache.getOrDefault(username, "NONE");
                    
                    if ("FRIEND".equals(status)) {
                        // Already friends
                        actionBtn.setText("✓ Friends");
                        actionBtn.setBackgroundResource(R.drawable.button_secondary_fall);
                        actionBtn.setEnabled(false);
                        actionBtn.setAlpha(0.6f);
                    } else if ("PENDING".equals(status)) {
                        // Request pending
                        actionBtn.setText("⏳ Pending");
                        actionBtn.setBackgroundResource(R.drawable.button_secondary_fall);
                        actionBtn.setEnabled(false);
                        actionBtn.setAlpha(0.8f);
                    } else {
                        // Not friends - show add button
                        actionBtn.setText("➕ Add");
                        actionBtn.setBackgroundResource(R.drawable.button_primary_fall);
                        actionBtn.setEnabled(true);
                        actionBtn.setAlpha(1.0f);
                        actionBtn.setOnClickListener(v -> addFriend(user));
                    }
                }
                
                searchResultsContainer.addView(userItemView);

            } catch (Exception e) {
                Log.e("SearchFriends", "Error displaying user: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void addFriend(JSONObject user) {
        try {
            String friendUsername = user.getString("username");

            JSONObject requestBody = new JSONObject();
            requestBody.put("username", currentUsername);
            requestBody.put("friendUsername", friendUsername);

            JsonObjectRequest addFriendRequest = new JsonObjectRequest(
                Request.Method.POST,
                SEND_FRIEND_REQUEST_URL,
                requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("Volley Response", "Friend request sent: " + response.toString());
                        
                        // Update status to pending and refresh display
                        friendshipStatusCache.put(friendUsername, "PENDING");
                        renderUserItems();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMessage = "Failed to send friend request";
                        if (error.networkResponse != null) {
                            int statusCode = error.networkResponse.statusCode;
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                if (!responseBody.isEmpty()) {
                                    JSONObject errorJson = new JSONObject(responseBody);
                                    errorMessage = errorJson.optString("error", errorJson.optString("message", errorMessage));
                                }
                            } catch (Exception e) {
                                if (statusCode == 400) {
                                    errorMessage = "Invalid request. Please check your information.";
                                } else if (statusCode == 409) {
                                    errorMessage = "Friend request already sent or friendship already exists.";
                                } else if (statusCode == 404) {
                                    errorMessage = "User not found.";
                                }
                            }
                        }
                        Log.e("Volley Error", "Failed to send friend request: " + error.toString());
                    }
                }
            );

            addFriendRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            requestQueue.add(addFriendRequest);

        } catch (JSONException e) {
            Log.e("JSON Error", "Error creating friend request: " + e.getMessage());
        }
    }
}