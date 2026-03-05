package com.example.occasio.friends;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.example.occasio.api.VolleySingleton;
import com.example.occasio.utils.ServerConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SearchGroupsActivityVolley extends AppCompatActivity {

    private LinearLayout searchResultsContainer;
    private TextView statusText;
    private EditText searchInput;
    private RequestQueue requestQueue;

    // REAL BACKEND
    private static final String BASE_URL = ServerConfig.BASE_URL;
    private static final String GET_ALL_GROUPS_URL = BASE_URL + "/api/groups";
    private static final String JOIN_GROUP_URL = BASE_URL + "/api/groups";

    private List<JSONObject> allGroups = new ArrayList<>();
    private List<JSONObject> filteredGroups = new ArrayList<>();

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
        title.setText("👥 Search Groups");
        title.setTextSize(28);
        title.setTextColor(Color.parseColor("#8B4513"));
        title.setPadding(0, 0, 0, 20);
        title.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mainLayout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Search for groups and join them");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.parseColor("#654321"));
        subtitle.setPadding(0, 0, 0, 20);
        subtitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mainLayout.addView(subtitle);

        searchInput = new EditText(this);
        searchInput.setHint("Search by organization name or description...");
        searchInput.setPadding(30, 30, 30, 30);
        searchInput.setBackgroundColor(Color.parseColor("#F5DEB3"));
        searchInput.setTextColor(Color.parseColor("#000000"));
        searchInput.setHintTextColor(Color.parseColor("#654321"));
        LinearLayout.LayoutParams searchInputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        searchInputParams.setMargins(0, 0, 0, 30);
        searchInput.setLayoutParams(searchInputParams);
        mainLayout.addView(searchInput);

        statusText = new TextView(this);
        statusText.setText("Loading groups...");
        statusText.setTextSize(16);
        statusText.setTextColor(Color.parseColor("#654321"));
        statusText.setPadding(0, 0, 0, 20);
        statusText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        mainLayout.addView(statusText);

        searchResultsContainer = new LinearLayout(this);
        searchResultsContainer.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(searchResultsContainer);

        Button myGroupsBtn = new Button(this);
        myGroupsBtn.setText("👥 My Groups");
        myGroupsBtn.setBackgroundColor(Color.parseColor("#D2691E"));
        myGroupsBtn.setTextColor(Color.WHITE);
        myGroupsBtn.setPadding(30, 15, 30, 15);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, 30, 0, 0);
        myGroupsBtn.setLayoutParams(buttonParams);
        myGroupsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, MyGroupsActivity.class);
            startActivity(intent);
        });
        mainLayout.addView(myGroupsBtn);

        Button createGroupFab = new Button(this);
        createGroupFab.setText("+");
        createGroupFab.setTextSize(24);
        createGroupFab.setBackgroundColor(Color.parseColor("#228B22"));
        createGroupFab.setTextColor(Color.WHITE);
        createGroupFab.setPadding(20, 20, 20, 20);
        LinearLayout.LayoutParams fabParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        fabParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        fabParams.setMargins(0, 20, 0, 0);
        createGroupFab.setLayoutParams(fabParams);
        
        createGroupFab.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateGroupActivity.class);
            startActivity(intent);
        });
        mainLayout.addView(createGroupFab);

        setContentView(mainLayout);

        setupSearchFunctionality();
        loadAllGroups();
    }

    private void setupSearchFunctionality() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterGroups(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadAllGroups() {
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                GET_ALL_GROUPS_URL,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        Log.d("Volley Response", "Groups loaded: " + response.length());
                        allGroups.clear();
                        try {
                            for (int i = 0; i < response.length(); i++) {
                                JSONObject group = response.getJSONObject(i);
                                allGroups.add(group);
                            }
                            statusText.setText("Found " + allGroups.size() + " groups. Start typing to search...");
                            filterGroups(searchInput.getText().toString());
                        } catch (JSONException e) {
                            Log.e("JSON Error", "Error parsing group data: " + e.getMessage());
                            statusText.setText("Error parsing group data.");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Volley Error", "Failed to load groups: " + error.toString());
                        statusText.setText("Failed to load groups. Please check backend server.");
                        Toast.makeText(SearchGroupsActivityVolley.this, "Network error. Please try again.", Toast.LENGTH_LONG).show();
                    }
                }
        );
        requestQueue.add(jsonArrayRequest);
    }

    private void filterGroups(String query) {
        searchResultsContainer.removeAllViews();
        filteredGroups.clear();
        if (query.isEmpty()) {
            statusText.setText("Found " + allGroups.size() + " groups. Start typing to search...");
            return;
        }

        for (JSONObject group : allGroups) {
            try {
                String orgName = group.getString("orgName");
                String description = group.optString("description", "");
                if (orgName.toLowerCase().contains(query.toLowerCase()) ||
                        description.toLowerCase().contains(query.toLowerCase())) {
                    filteredGroups.add(group);
                }
            } catch (JSONException e) {
                Log.e("JSON Error", "Error getting group data for filter: " + e.getMessage());
            }
        }

        if (filteredGroups.isEmpty()) {
            statusText.setText("No groups found matching '" + query + "'");
        } else {
            statusText.setText("Found " + filteredGroups.size() + " groups matching '" + query + "'");
            displaySearchResults();
        }
    }

    private void displaySearchResults() {
        searchResultsContainer.removeAllViews();
        for (JSONObject group : filteredGroups) {
            try {
                String orgName = group.getString("orgName");
                String description = group.optString("description", "No description");

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

                Button joinBtn = new Button(this);
                joinBtn.setText("➕ Join");
                joinBtn.setBackgroundColor(Color.parseColor("#8FBC8F"));
                joinBtn.setTextColor(Color.WHITE);
                joinBtn.setPadding(20, 10, 20, 10);
                LinearLayout.LayoutParams joinBtnParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                joinBtnParams.setMargins(20, 0, 0, 0);
                joinBtn.setLayoutParams(joinBtnParams);
                joinBtn.setOnClickListener(v -> joinGroup(group));

                groupItem.addView(joinBtn);
                searchResultsContainer.addView(groupItem);

            } catch (JSONException e) {
                Log.e("JSON Error", "Error displaying group: " + e.getMessage());
            }
        }
    }

    private void joinGroup(JSONObject group) {
        try {
            String groupName = group.getString("orgName");

            JSONObject requestBody = new JSONObject();
            requestBody.put("action", "join_group");
            requestBody.put("group_name", groupName);

            JsonObjectRequest joinGroupRequest = new JsonObjectRequest(
                Request.Method.POST,
                JOIN_GROUP_URL,
                requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("Volley Response", "Group joined: " + response.toString());

                        filteredGroups.remove(group);
                        displaySearchResults();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Volley Error", "Failed to join group: " + error.toString());
                        Toast.makeText(SearchGroupsActivityVolley.this, "Failed to join group. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                }
            );

            requestQueue.add(joinGroupRequest);

        } catch (JSONException e) {
            Log.e("JSON Error", "Error creating join group request: " + e.getMessage());
            Toast.makeText(this, "Error joining group", Toast.LENGTH_SHORT).show();
        }
    }
}