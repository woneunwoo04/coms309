package com.example.occasio.friends;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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
import com.android.volley.toolbox.Volley;
import com.example.occasio.api.VolleySingleton;
import com.example.occasio.utils.ServerConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CreateGroupActivity extends AppCompatActivity {
    
    private EditText groupNameInput;
    private EditText groupDescriptionInput;
    private LinearLayout friendsContainer;
    private TextView statusText;
    private Button createGroupBtn;
    private Button cancelBtn;
    
    private RequestQueue requestQueue;
    private SharedPreferences sharedPreferences;
    private List<JSONObject> availableFriends = new ArrayList<>();
    private List<String> selectedFriends = new ArrayList<>();
    
    private static final String BASE_URL = ServerConfig.BASE_URL;
    private static final String GET_FRIENDS_URL = BASE_URL + "/user_info/user_info";
    private static final String CREATE_GROUP_URL = BASE_URL + "/api/groups";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestQueue = VolleySingleton.getInstance(getApplicationContext()).getRequestQueue();
        sharedPreferences = getSharedPreferences("FriendsPrefs", MODE_PRIVATE);
        
        setupUI();
        loadFriends();
    }
    
    private void setupUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 50, 50, 50);
        mainLayout.setBackgroundColor(Color.parseColor("#FFF8DC"));
        
        TextView title = new TextView(this);
        title.setText("🏗️ Create New Group");
        title.setTextSize(28);
        title.setTextColor(Color.parseColor("#8B4513"));
        title.setPadding(0, 0, 0, 30);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mainLayout.addView(title);
        
        TextView subtitle = new TextView(this);
        subtitle.setText("Create a group and invite your friends");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.parseColor("#654321"));
        subtitle.setPadding(0, 0, 0, 30);
        subtitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mainLayout.addView(subtitle);
        
        TextView nameLabel = new TextView(this);
        nameLabel.setText("Group Name *");
        nameLabel.setTextSize(16);
        nameLabel.setTextColor(Color.parseColor("#8B4513"));
        nameLabel.setPadding(0, 0, 0, 10);
        mainLayout.addView(nameLabel);
        
        groupNameInput = new EditText(this);
        groupNameInput.setHint("Enter group name");
        groupNameInput.setBackgroundColor(Color.WHITE);
        groupNameInput.setPadding(20, 15, 20, 15);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        nameParams.setMargins(0, 0, 0, 20);
        groupNameInput.setLayoutParams(nameParams);
        mainLayout.addView(groupNameInput);
        
        TextView descLabel = new TextView(this);
        descLabel.setText("Description (Optional)");
        descLabel.setTextSize(16);
        descLabel.setTextColor(Color.parseColor("#8B4513"));
        descLabel.setPadding(0, 0, 0, 10);
        mainLayout.addView(descLabel);
        
        groupDescriptionInput = new EditText(this);
        groupDescriptionInput.setHint("Enter group description");
        groupDescriptionInput.setBackgroundColor(Color.WHITE);
        groupDescriptionInput.setPadding(20, 15, 20, 15);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descParams.setMargins(0, 0, 0, 20);
        groupDescriptionInput.setLayoutParams(descParams);
        mainLayout.addView(groupDescriptionInput);
        
        TextView friendsLabel = new TextView(this);
        friendsLabel.setText("Invite Friends");
        friendsLabel.setTextSize(18);
        friendsLabel.setTextColor(Color.parseColor("#8B4513"));
        friendsLabel.setPadding(0, 0, 0, 15);
        mainLayout.addView(friendsLabel);
        
        statusText = new TextView(this);
        statusText.setText("Loading friends...");
        statusText.setTextSize(14);
        statusText.setTextColor(Color.parseColor("#654321"));
        statusText.setPadding(0, 0, 0, 15);
        statusText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mainLayout.addView(statusText);
        
        friendsContainer = new LinearLayout(this);
        friendsContainer.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(friendsContainer);
        
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(0, 30, 0, 0);
        
        cancelBtn = new Button(this);
        cancelBtn.setText("Cancel");
        cancelBtn.setBackgroundColor(Color.parseColor("#DC143C"));
        cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setPadding(30, 15, 30, 15);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        cancelParams.setMargins(0, 0, 10, 0);
        cancelBtn.setLayoutParams(cancelParams);
        cancelBtn.setOnClickListener(v -> finish());
        buttonLayout.addView(cancelBtn);
        
        createGroupBtn = new Button(this);
        createGroupBtn.setText("Create Group");
        createGroupBtn.setBackgroundColor(Color.parseColor("#228B22"));
        createGroupBtn.setTextColor(Color.WHITE);
        createGroupBtn.setPadding(30, 15, 30, 15);
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        );
        createParams.setMargins(10, 0, 0, 0);
        createGroupBtn.setLayoutParams(createParams);
        createGroupBtn.setOnClickListener(v -> createGroup());
        buttonLayout.addView(createGroupBtn);
        
        mainLayout.addView(buttonLayout);
        setContentView(mainLayout);
    }
    
    private void loadFriends() {
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                GET_FRIENDS_URL,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            availableFriends.clear();
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject friend = response.getJSONObject(i);
                                availableFriends.add(friend);
                            }
                            displayFriends();
                        } catch (JSONException e) {
                            Log.e("JSON Error", "Error parsing friends: " + e.getMessage());
                            statusText.setText("Error loading friends");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Volley Error", "Failed to load friends: " + error.toString());
                        statusText.setText("Failed to load friends. Please try again.");
                    }
                }
        );
        requestQueue.add(jsonArrayRequest);
    }
    
    private void displayFriends() {
        friendsContainer.removeAllViews();
        
        if (availableFriends.isEmpty()) {
            statusText.setText("No friends available to invite");
            return;
        }
        
        statusText.setText("Select friends to invite:");
        
        for (JSONObject friend : availableFriends) {
            try {
                String username = friend.getString("username");
                String firstName = friend.optString("firstName", "");
                String lastName = friend.optString("lastName", "");
                String displayName = !firstName.isEmpty() ? firstName + " " + lastName : username;
                
                Button friendBtn = new Button(this);
                friendBtn.setText("👤 " + displayName);
                friendBtn.setBackgroundColor(Color.parseColor("#F0F8FF"));
                friendBtn.setTextColor(Color.parseColor("#2F4F4F"));
                friendBtn.setPadding(20, 10, 20, 10);
                
                LinearLayout.LayoutParams friendParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                friendParams.setMargins(0, 5, 0, 5);
                friendBtn.setLayoutParams(friendParams);
                
                friendBtn.setOnClickListener(v -> toggleFriendSelection(username, friendBtn));
                friendsContainer.addView(friendBtn);
                
            } catch (JSONException e) {
                Log.e("JSON Error", "Error displaying friend: " + e.getMessage());
            }
        }
    }
    
    private void toggleFriendSelection(String username, Button friendBtn) {
        if (selectedFriends.contains(username)) {
            selectedFriends.remove(username);
            friendBtn.setBackgroundColor(Color.parseColor("#F0F8FF"));
            friendBtn.setTextColor(Color.parseColor("#2F4F4F"));
        } else {
            selectedFriends.add(username);
            friendBtn.setBackgroundColor(Color.parseColor("#228B22"));
            friendBtn.setTextColor(Color.WHITE);
        }
    }
    
    private void createGroup() {
        String groupName = groupNameInput.getText().toString().trim();
        String description = groupDescriptionInput.getText().toString().trim();
        
        if (groupName.isEmpty()) {
            return;
        }
        
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("orgName", groupName);
            requestBody.put("description", description);
            requestBody.put("createdBy", sharedPreferences.getString("username", "demo_user"));
            requestBody.put("invitedFriends", new JSONArray(selectedFriends));
            
            JsonObjectRequest createRequest = new JsonObjectRequest(
                Request.Method.POST,
                CREATE_GROUP_URL,
                requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("Create Group Response", "Group created: " + response.toString());
                        
                        Intent intent = new Intent(CreateGroupActivity.this, SearchGroupsActivityVolley.class);
                        startActivity(intent);
                        finish();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Create Group Error", "Failed to create group: " + error.toString());
                        Toast.makeText(CreateGroupActivity.this, "❌ Failed to create group. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                }
            );
            requestQueue.add(createRequest);
        } catch (JSONException e) {
            Log.e("JSON Error", "Error creating group request: " + e.getMessage());
            Toast.makeText(this, "❌ Error creating group request", Toast.LENGTH_SHORT).show();
        }
    }
}
