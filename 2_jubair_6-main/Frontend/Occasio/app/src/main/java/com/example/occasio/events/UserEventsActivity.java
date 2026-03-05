package com.example.occasio.events;
import com.example.occasio.R;
import com.example.occasio.events.AllEventsActivity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
import java.util.ArrayList;
import java.util.List;
import com.example.occasio.model.Event;

public class UserEventsActivity extends AppCompatActivity {
    private RecyclerView eventsRecyclerView;
    private Button backButton;
    private Button refreshButton;
    private Button attendanceButton;
    
    private String currentUsername;
    private List<Event> eventsList;
    private UserEventAdapter eventAdapter;
    private RequestQueue requestQueue;
    
    private static final String USER_EVENTS_URL = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/events/user/";
    private static final String ACTIVE_SESSION_URL = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/event-attendance/events/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_events);

        android.util.Log.d("UserEventsActivity", "onCreate started");

        eventsRecyclerView = findViewById(R.id.user_events_recycler_view);
        backButton = findViewById(R.id.user_events_back_btn);
        refreshButton = findViewById(R.id.user_events_refresh_btn);
        attendanceButton = findViewById(R.id.user_events_attendance_btn);

        // Get username from intent
        Intent receivedIntent = getIntent();
        if (receivedIntent != null && receivedIntent.hasExtra("username")) {
            currentUsername = receivedIntent.getStringExtra("username");
        }
        
        // If not in Intent, try SharedPreferences
        if (currentUsername == null || currentUsername.isEmpty()) {
            android.content.SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            currentUsername = sharedPreferences.getString("username", "");
        }
        
        android.util.Log.d("UserEventsActivity", "Username received: " + currentUsername);

        if (currentUsername == null || currentUsername.isEmpty()) {
            android.util.Log.e("UserEventsActivity", "No username provided");
            finish();
            return;
        }

        // Initialize RecyclerView
        eventsList = new ArrayList<>();
        eventAdapter = new UserEventAdapter(eventsList, this::onEventClick, this::onUnregisterClick, this::onCheckInClick);
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        eventsRecyclerView.setAdapter(eventAdapter);

        // Initialize Volley
        requestQueue = Volley.newRequestQueue(this);

        // Set up click listeners
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(UserEventsActivity.this, AllEventsActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
            finish();
        });

        refreshButton.setOnClickListener(v -> {
            android.util.Log.d("UserEventsActivity", "Refresh button clicked");
            loadUserEvents();
        });

        attendanceButton.setOnClickListener(v -> {
            Intent intent = new Intent(UserEventsActivity.this, AttendanceActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
        });

        // Load user events
        loadUserEvents();
    }

    private void loadUserEvents() {
        // Validate username
        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            finish();
            return;
        }
        
        android.util.Log.d("UserEventsActivity", "Loading user events for: " + currentUsername);
        
        // URL encode username to handle special characters
        String encodedUsername = "";
        try {
            encodedUsername = java.net.URLEncoder.encode(currentUsername, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            android.util.Log.e("UserEventsActivity", "Error encoding username", e);
            encodedUsername = currentUsername; // Fallback to original
        }
        
        String url = USER_EVENTS_URL + encodedUsername;
        android.util.Log.d("UserEventsActivity", "Request URL: " + url);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    android.util.Log.d("UserEventsActivity", "Response received: " + response.length() + " events");
                    try {
                        eventsList.clear();
                        
                        // Handle null or empty response
                        if (response == null || response.length() == 0) {
                            android.util.Log.d("UserEventsActivity", "No events found");
                            eventAdapter.notifyDataSetChanged();
                            return;
                        }
                        
                        for (int i = 0; i < response.length(); i++) {
                            try {
                                JSONObject eventJson = response.getJSONObject(i);
                                
                                // Validate required fields
                                if (!eventJson.has("id")) {
                                    android.util.Log.w("UserEventsActivity", "Event missing ID at index " + i);
                                    continue;
                                }
                                
                                Event event = new Event();
                                event.setId((int) eventJson.getLong("id"));
                                
                                // Safely get event name
                                if (eventJson.has("eventName")) {
                                    event.setTitle(eventJson.getString("eventName"));
                                } else if (eventJson.has("title")) {
                                    event.setTitle(eventJson.getString("title"));
                                } else {
                                    event.setTitle("Untitled Event");
                                }
                                
                                event.setDescription(eventJson.optString("description", ""));
                                event.setLocation(eventJson.optString("location", ""));
                                
                                // Parse date - backend uses startTime as LocalDateTime
                                String dateStr = eventJson.optString("startTime", eventJson.optString("date", ""));
                                if (!dateStr.isEmpty()) {
                                    event.setStartTime(dateStr);
                                    event.setEndTime(eventJson.optString("endTime", dateStr));
                                }
                                
                                // Parse organization info
                                JSONObject orgJson = null;
                                if (eventJson.has("organization") && !eventJson.isNull("organization")) {
                                    orgJson = eventJson.getJSONObject("organization");
                                }
                                
                                String orgName = "Unknown Organization";
                                String orgId = "0";
                                if (orgJson != null) {
                                    orgName = orgJson.optString("orgName", "Unknown Organization");
                                    orgId = String.valueOf(orgJson.optLong("id", 0));
                                } else {
                                    orgName = eventJson.optString("organizationName", "Unknown Organization");
                                    orgId = eventJson.optString("organizationId", "0");
                                }
                                event.setOrganizerName(orgName);
                                event.setOrganizerId(orgId);
                                
                                // Check if there's an active attendance session for this event
                                event.setHasActiveSession(false); // Default to false, will be updated
                                eventsList.add(event);
                            } catch (org.json.JSONException e) {
                                android.util.Log.e("UserEventsActivity", "Error parsing event at index " + i, e);
                                // Continue with next event instead of failing completely
                            }
                        }
                        
                        android.util.Log.d("UserEventsActivity", "Parsed " + eventsList.size() + " events");
                        
                        // Check for active sessions for each event
                        checkActiveSessionsForEvents();
                        
                        if (eventsList.isEmpty()) {
                        }
                        
                    } catch (Exception e) {
                        android.util.Log.e("UserEventsActivity", "Unexpected error", e);
                        showErrorDialog("Unexpected error", e.getMessage());
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    android.util.Log.e("UserEventsActivity", "Network error", error);
                    String errorMessage = "Failed to load events";
                    
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            org.json.JSONObject errorJson = new org.json.JSONObject(responseBody);
                            errorMessage = errorJson.optString("message", errorMessage);
                        } catch (Exception e) {
                            // If we can't parse the error, use status code
                            if (statusCode == 404) {
                                errorMessage = "User not found";
                            } else if (statusCode == 400) {
                                errorMessage = "Invalid request";
                            } else if (statusCode == 500) {
                                errorMessage = "Server error. Please try again later";
                            } else {
                                errorMessage = "Error (HTTP " + statusCode + ")";
                            }
                        }
                    } else if (error.getMessage() != null) {
                        errorMessage = error.getMessage();
                    }
                    
                    showErrorDialog("Failed to load your events", errorMessage);
                    
                    // Clear list on error
                    eventsList.clear();
                    eventAdapter.notifyDataSetChanged();
                }
            }
        );
        
        // Add retry policy
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
            15000, // 15 seconds timeout
            2, // 2 retries
            com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }


    private void onEventClick(Event event) {
        android.util.Log.d("UserEventsActivity", "Event clicked: " + event.getTitle());
        // Navigate to Event Check-In Activity
        Intent checkInIntent = new Intent(UserEventsActivity.this, EventCheckInActivity.class);
        checkInIntent.putExtra("username", currentUsername);
        checkInIntent.putExtra("eventId", event.getId());
        checkInIntent.putExtra("eventName", event.getTitle());
        startActivity(checkInIntent);
    }

    private void onUnregisterClick(Event event) {
        android.util.Log.d("UserEventsActivity", "Unregister clicked for: " + event.getTitle());
        
        new AlertDialog.Builder(this)
            .setTitle("Unregister from Event")
            .setMessage("Are you sure you want to unregister from \"" + event.getTitle() + "\"?")
            .setPositiveButton("Yes, Unregister", (dialog, which) -> {
                unregisterFromEvent(event);
            })
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void onCheckInClick(Event event) {
        android.util.Log.d("UserEventsActivity", "Check-in clicked for: " + event.getTitle());
        
        // Navigate to Event Check-In Activity
        Intent checkInIntent = new Intent(UserEventsActivity.this, EventCheckInActivity.class);
        checkInIntent.putExtra("username", currentUsername);
        checkInIntent.putExtra("eventId", (long) event.getId());
        checkInIntent.putExtra("eventName", event.getTitle());
        startActivity(checkInIntent);
    }

    private void unregisterFromEvent(Event event) {
        // Validate inputs
        if (event == null || event.getId() <= 0) {
            return;
        }
        
        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            return;
        }
        
        // URL encode username
        String encodedUsername = "";
        try {
            encodedUsername = java.net.URLEncoder.encode(currentUsername, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            android.util.Log.e("UserEventsActivity", "Error encoding username", e);
            encodedUsername = currentUsername;
        }
        
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/events/" + event.getId() + "/unregister?username=" + encodedUsername;
        android.util.Log.d("UserEventsActivity", "Unregistering from event: " + url);
        
        com.android.volley.toolbox.StringRequest request = new com.android.volley.toolbox.StringRequest(
            Request.Method.DELETE,
            url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    // Reload events
                    loadUserEvents();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    android.util.Log.e("UserEventsActivity", "Error unregistering", error);
                    String errorMessage = "Failed to unregister from event";
                    
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            JSONObject errorJson = new JSONObject(responseBody);
                            errorMessage = errorJson.optString("message", errorMessage);
                        } catch (Exception e) {
                            // Use status code based error messages
                            if (statusCode == 404) {
                                errorMessage = "Event or user not found";
                            } else if (statusCode == 400) {
                                errorMessage = "User is not registered for this event";
                            } else if (statusCode == 500) {
                                errorMessage = "Server error. Please try again later";
                            } else {
                                errorMessage = "Error (HTTP " + statusCode + ")";
                            }
                        }
                    } else if (error.getMessage() != null) {
                        errorMessage = error.getMessage();
                    }
                    
                }
            }
        );
        
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
            15000, // 15 seconds timeout
            2, // 2 retries
            com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));
        requestQueue.add(request);
    }

    private void checkActiveSessionsForEvents() {
        // Check active session for each event
        for (Event event : eventsList) {
            checkActiveSessionForEvent(event);
        }
    }

    private void checkActiveSessionForEvent(Event event) {
        String url = ACTIVE_SESSION_URL + event.getId() + "/active-session";
        
        com.android.volley.toolbox.JsonObjectRequest request = new com.android.volley.toolbox.JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    // Active session exists
                    event.setHasActiveSession(true);
                    eventAdapter.notifyDataSetChanged();
                    android.util.Log.d("UserEventsActivity", "Active session found for event: " + event.getTitle());
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    // No active session (404) or error
                    event.setHasActiveSession(false);
                    eventAdapter.notifyDataSetChanged();
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        android.util.Log.d("UserEventsActivity", "No active session for event: " + event.getTitle());
                    } else {
                        android.util.Log.e("UserEventsActivity", "Error checking active session for event: " + event.getTitle());
                    }
                }
            }
        );
        
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
            5000, // 5 seconds timeout
            1, // 1 retry
            com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));
        
        requestQueue.add(request);
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Copy Error", (dialog, which) -> {
                // Copy error to clipboard
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Error", message);
                clipboard.setPrimaryClip(clip);
            })
            .setNegativeButton("OK", (dialog, which) -> dialog.dismiss())
            .show();
    }
}
