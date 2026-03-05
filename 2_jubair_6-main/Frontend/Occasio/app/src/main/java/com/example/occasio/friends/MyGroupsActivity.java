package com.example.occasio.friends;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import com.example.occasio.api.VolleySingleton;
import com.example.occasio.utils.ServerConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MyGroupsActivity extends AppCompatActivity {

    private LinearLayout groupsContainer;
    private TextView statusText;
    private RequestQueue requestQueue;

    private List<JSONObject> myGroups = new ArrayList<>();

    private static final String BASE_URL = ServerConfig.BASE_URL;
    private static final String GET_MY_GROUPS_URL = BASE_URL + "/api/groups";
    private static final String LEAVE_GROUP_URL = BASE_URL + "/api/groups";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestQueue = VolleySingleton.getInstance(getApplicationContext()).getRequestQueue();

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 50, 50, 50);
        mainLayout.setBackgroundColor(Color.parseColor("#FFF8DC"));
        mainLayout.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("👥 My Groups");
        title.setTextSize(28);
        title.setTextColor(Color.parseColor("#8B4513"));
        title.setPadding(0, 0, 0, 20);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mainLayout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("View and manage your joined groups");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.parseColor("#654321"));
        subtitle.setPadding(0, 0, 0, 20);
        subtitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mainLayout.addView(subtitle);

        statusText = new TextView(this);
        statusText.setText("Loading your groups...");
        statusText.setTextSize(16);
        statusText.setTextColor(Color.parseColor("#654321"));
        statusText.setPadding(0, 0, 0, 20);
        statusText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mainLayout.addView(statusText);

        groupsContainer = new LinearLayout(this);
        groupsContainer.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(groupsContainer);

        Button backButton = new Button(this);
        backButton.setText("← Back to Search Groups");
        backButton.setBackgroundColor(Color.parseColor("#D2B48C"));
        backButton.setTextColor(Color.WHITE);
        backButton.setPadding(30, 15, 30, 15);
        LinearLayout.LayoutParams backButtonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        backButtonParams.setMargins(0, 30, 0, 0);
        backButton.setLayoutParams(backButtonParams);
        backButton.setOnClickListener(v -> finish());
        mainLayout.addView(backButton);

        setContentView(mainLayout);

        loadMyGroups();
    }

    private void loadMyGroups() {
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                GET_MY_GROUPS_URL,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        Log.d("Volley Response", "Groups loaded: " + response.length());
                        myGroups.clear();
                        try {
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject group = response.getJSONObject(i);
                                if (group.optString("orgName").equals("Book Club")) {
                                    myGroups.add(group);
                                }
                            }

                            if (myGroups.isEmpty()) {
                                statusText.setText("You haven't joined any groups yet. Search for some!");
                            } else {
                                statusText.setText("You have joined " + myGroups.size() + " group(s).");
                                displayMyGroups();
                            }
                        } catch (JSONException e) {
                            Log.e("JSON Error", "Error parsing groups data: " + e.getMessage());
                            statusText.setText("Error parsing groups data.");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Volley Error", "Failed to load groups: " + error.toString());
                        statusText.setText("Failed to load groups. Please check backend server.");
                        Toast.makeText(MyGroupsActivity.this, "Network error. Please try again.", Toast.LENGTH_LONG).show();
                    }
                }
        );
        requestQueue.add(jsonArrayRequest);
    }

    private void displayMyGroups() {
        groupsContainer.removeAllViews();
        for (JSONObject group : myGroups) {
            try {
                String orgName = group.getString("orgName");
                String description = group.optString("description", "No description");
                Long groupId = group.getLong("id");

                LinearLayout groupItem = new LinearLayout(this);
                groupItem.setOrientation(LinearLayout.HORIZONTAL);
                groupItem.setPadding(20, 20, 20, 20);
                groupItem.setBackgroundColor(Color.parseColor("#FDF5E6"));
                LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                itemParams.setMargins(0, 0, 0, 10);
                groupItem.setLayoutParams(itemParams);

                TextView groupInfo = new TextView(this);
                groupInfo.setText(orgName + " (" + description + ")");
                groupInfo.setTextSize(18);
                groupInfo.setTextColor(Color.parseColor("#8B4513"));
                LinearLayout.LayoutParams groupInfoParams = new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f
                );
                groupItem.addView(groupInfo, groupInfoParams);

                Button leaveBtn = new Button(this);
                leaveBtn.setText("🚪 Leave");
                leaveBtn.setBackgroundColor(Color.parseColor("#FF6347"));
                leaveBtn.setTextColor(Color.WHITE);
                leaveBtn.setPadding(20, 10, 20, 10);
                LinearLayout.LayoutParams leaveBtnParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                leaveBtnParams.setMargins(20, 0, 0, 0);
                leaveBtn.setLayoutParams(leaveBtnParams);
                leaveBtn.setOnClickListener(v -> showLeaveConfirmation(groupId, orgName));

                groupItem.addView(leaveBtn);
                groupsContainer.addView(groupItem);

            } catch (JSONException e) {
                Log.e("JSON Error", "Error displaying group: " + e.getMessage());
            }
        }
    }

    private void showLeaveConfirmation(Long groupId, String groupName) {
        new AlertDialog.Builder(this)
                .setTitle("🚪 Leave Group")
                .setMessage("Are you sure you want to leave the group '" + groupName + "'?")
                .setPositiveButton("Yes, Leave", (dialog, which) -> leaveGroup(groupId, groupName))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void leaveGroup(Long groupId, String groupName) {
        JsonObjectRequest leaveRequest = new JsonObjectRequest(
            Request.Method.DELETE,
            LEAVE_GROUP_URL + "/" + groupId,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    for (int i = 0; i < myGroups.size(); i++) {
                        try {
                            if (myGroups.get(i).getLong("id") == groupId) {
                                myGroups.remove(i);
                                break;
                            }
                        } catch (JSONException e) {
                            Log.e("JSON Error", "Error finding group to leave: " + e.getMessage());
                        }
                    }
                    displayMyGroups();
                    if (myGroups.isEmpty()) {
                        statusText.setText("You haven't joined any groups yet. Search for some!");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Toast.makeText(MyGroupsActivity.this, "Failed to leave group. Please try again.", Toast.LENGTH_SHORT).show();
                    Log.e("Leave Group Error", "Error: " + error.getMessage());
                }
            }
        );
        
        requestQueue.add(leaveRequest);
    }
}