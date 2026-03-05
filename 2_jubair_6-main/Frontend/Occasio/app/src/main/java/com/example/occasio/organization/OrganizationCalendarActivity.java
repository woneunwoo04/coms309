package com.example.occasio.organization;

import com.example.occasio.R;
import com.example.occasio.utils.ServerConfig;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OrganizationCalendarActivity extends AppCompatActivity {
    
    private RecyclerView calendarRecyclerView;
    private Button backButton;
    private Button refreshButton;
    private TextView headerTextView;
    
    private Long currentOrgId;
    private String currentOrgName;
    private List<OrganizationEvent> eventsList;
    private OrganizationCalendarAdapter calendarAdapter;
    private RequestQueue requestQueue;
    
    private static final String BASE_URL = ServerConfig.BASE_URL;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organization_calendar);

        android.util.Log.d("OrganizationCalendarActivity", "onCreate started");

        // Initialize views
        calendarRecyclerView = findViewById(R.id.org_calendar_recycler_view);
        backButton = findViewById(R.id.org_calendar_back_btn);
        refreshButton = findViewById(R.id.org_calendar_refresh_btn);
        headerTextView = findViewById(R.id.org_calendar_header_tv);

        // Get organization info from intent
        Intent receivedIntent = getIntent();
        if (receivedIntent != null) {
            currentOrgId = receivedIntent.getLongExtra("orgId", -1L);
            currentOrgName = receivedIntent.getStringExtra("orgName");
        }

        if (currentOrgId == null || currentOrgId <= 0) {
            android.util.Log.e("OrganizationCalendarActivity", "No organization ID provided");
            finish();
            return;
        }

        if (currentOrgName != null && !currentOrgName.isEmpty()) {
            headerTextView.setText(currentOrgName + " Calendar");
        }

        // Initialize data
        eventsList = new ArrayList<>();
        requestQueue = Volley.newRequestQueue(this);
        
        // Setup RecyclerView
        calendarAdapter = new OrganizationCalendarAdapter(eventsList);
        calendarRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        calendarRecyclerView.setAdapter(calendarAdapter);

        // Set up click listeners
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(OrganizationCalendarActivity.this, OrganizationDashboardActivity.class);
            intent.putExtra("orgName", currentOrgName);
            intent.putExtra("orgId", currentOrgId);
            startActivity(intent);
            finish();
        });

        refreshButton.setOnClickListener(v -> {
            android.util.Log.d("OrganizationCalendarActivity", "Refresh button clicked");
            loadOrganizationEvents();
        });

        // Load organization events
        loadOrganizationEvents();
    }

    private void loadOrganizationEvents() {
        android.util.Log.d("OrganizationCalendarActivity", "Loading events for organization ID: " + currentOrgId);
        
        String url = BASE_URL + "/api/events/organization/" + currentOrgId;
        android.util.Log.d("OrganizationCalendarActivity", "Request URL: " + url);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    android.util.Log.d("OrganizationCalendarActivity", "Response received: " + response.length() + " events");
                    try {
                        eventsList.clear();
                        
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject eventJson = response.getJSONObject(i);
                            
                            OrganizationEvent event = new OrganizationEvent();
                            event.setId(eventJson.getLong("id"));
                            event.setTitle(eventJson.optString("eventName", eventJson.optString("title", "Untitled Event")));
                            event.setDescription(eventJson.optString("description", ""));
                            event.setLocation(eventJson.optString("location", ""));
                            
                            // Parse dates
                            String startTimeStr = eventJson.optString("startTime", "");
                            String endTimeStr = eventJson.optString("endTime", "");
                            event.setStartTime(startTimeStr);
                            event.setEndTime(endTimeStr);
                            
                            eventsList.add(event);
                        }
                        
                        android.util.Log.d("OrganizationCalendarActivity", "Parsed " + eventsList.size() + " events");
                        calendarAdapter.notifyDataSetChanged();
                        
                        if (eventsList.isEmpty()) {
                        }
                        
                    } catch (Exception e) {
                        android.util.Log.e("OrganizationCalendarActivity", "Error parsing response", e);
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    android.util.Log.e("OrganizationCalendarActivity", "Network error", error);
                    String errorMessage = "Network error: " + (error.getMessage() != null ? error.getMessage() : "Unknown error");
                    
                    if (error.networkResponse != null) {
                        errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                    }
                    
                    Toast.makeText(OrganizationCalendarActivity.this, "Failed to load events: " + errorMessage, Toast.LENGTH_LONG).show();
                    
                    // Clear list on error
                    eventsList.clear();
                    calendarAdapter.notifyDataSetChanged();
                }
            }
        );

        requestQueue.add(request);
    }

    // Inner class for organization event data model
    public static class OrganizationEvent {
        private Long id;
        private String title;
        private String description;
        private String location;
        private String startTime;
        private String endTime;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
    }
}

