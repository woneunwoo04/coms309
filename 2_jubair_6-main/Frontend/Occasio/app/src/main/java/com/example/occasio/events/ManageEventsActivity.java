package com.example.occasio.events;
import com.example.occasio.R;
import com.example.occasio.organization.OrganizationDashboardActivity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ManageEventsActivity extends AppCompatActivity {
    private RecyclerView eventsRecyclerView;
    private Button backButton;
    private Button refreshButton;
    
    private RequestQueue requestQueue;
    private EventAdapter eventAdapter;
    private List<ManageEventsActivity.Event> eventsList;
    private String currentOrgName;
    private Long currentOrgId;
    
    private static final String DELETE_EVENT_URL = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/events/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_events);

        eventsRecyclerView = findViewById(R.id.manage_events_recycler_view);
        backButton = findViewById(R.id.manage_events_back_btn);
        refreshButton = findViewById(R.id.manage_events_refresh_btn);

        requestQueue = Volley.newRequestQueue(this);
        eventsList = new ArrayList<>();

        // Get organization name and ID from intent
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra("orgName")) {
                currentOrgName = intent.getStringExtra("orgName");
            }
            if (intent.hasExtra("orgId")) {
                currentOrgId = intent.getLongExtra("orgId", -1L);
            }
        }

        // Setup RecyclerView with Start Attendance and View Feedback listeners
        eventAdapter = new EventAdapter(eventsList, this::onEventClick, this::onEventDelete, this::onStartAttendance, this::onViewAttendance, this::onViewFeedback);
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        eventsRecyclerView.setAdapter(eventAdapter);

        // Set click listeners
        backButton.setOnClickListener(v -> {
            Intent backIntent = new Intent(ManageEventsActivity.this, OrganizationDashboardActivity.class);
            backIntent.putExtra("orgName", currentOrgName);
            startActivity(backIntent);
            finish();
        });

        refreshButton.setOnClickListener(v -> loadEvents());

        // Load events on startup
        loadEvents();
    }

    private void loadEvents() {
        if (currentOrgId == null || currentOrgId <= 0) {
            finish();
            return;
        }
        
        // Show loading indicator
        
        // Use the correct endpoint to get events by organization
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/events/organization/" + currentOrgId;
        
        com.android.volley.toolbox.JsonArrayRequest request = new com.android.volley.toolbox.JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<org.json.JSONArray>() {
                @Override
                public void onResponse(org.json.JSONArray response) {
                    try {
                        eventsList.clear();
                        
                        // Parse events from JSON array
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject eventObj = response.getJSONObject(i);
                            
                            Long eventId = eventObj.getLong("id");
                            String eventName = eventObj.getString("eventName");
                            String description = eventObj.optString("description", "");
                            String location = eventObj.optString("location", "");
                            String startTime = eventObj.optString("startTime", "");
                            String endTime = eventObj.optString("endTime", "");
                            String title = eventObj.optString("title", eventName);
                            int rewardPoints = eventObj.optInt("rewardPoints", 0);
                            String eventType = eventObj.optString("eventType", "Other");
                            
                            // Parse registration fee if available
                            Double registrationFee = null;
                            if (eventObj.has("registrationFee") && !eventObj.isNull("registrationFee")) {
                                try {
                                    registrationFee = eventObj.getDouble("registrationFee");
                                } catch (JSONException e) {
                                    // If not a number, leave as null
                                }
                            }
                            
                            // Create Event object
                            ManageEventsActivity.Event event = new ManageEventsActivity.Event(
                                eventId.intValue(),
                                title != null && !title.isEmpty() ? title : eventName,
                                description,
                                location,
                                startTime,
                                endTime,
                                eventType,
                                currentOrgName,
                                rewardPoints
                            );
                            
                            // Set registration fee
                            if (registrationFee != null) {
                                event.setRegistrationFee(registrationFee);
                            }
                            
                            eventsList.add(event);
                            
                            // Check for active session for this event
                            checkActiveSessionForEvent(event);
                        }
                        
                        eventAdapter.notifyDataSetChanged();
                        android.util.Log.d("ManageEventsActivity", "Loaded " + eventsList.size() + " events");
                            
                    } catch (Exception e) {
                        android.util.Log.e("ManageEventsActivity", "Error parsing events: " + e.getMessage());
                        eventsList.clear();
                        eventAdapter.notifyDataSetChanged();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMessage = "Failed to load events";
                    if (error.networkResponse != null) {
                        errorMessage += " (HTTP " + error.networkResponse.statusCode + ")";
                    }
                    android.util.Log.e("ManageEventsActivity", "Error loading events: " + error.toString());
                    eventsList.clear();
                    eventAdapter.notifyDataSetChanged();
                }
            }
        );
        
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }

    private void onEventClick(Event event) {
        // Navigate to edit event activity
        Intent editIntent = new Intent(ManageEventsActivity.this, EditEventActivity.class);
        editIntent.putExtra("eventId", event.getId());
        editIntent.putExtra("eventTitle", event.getTitle());
        editIntent.putExtra("eventDescription", event.getDescription());
        editIntent.putExtra("eventLocation", event.getLocation());
        editIntent.putExtra("eventStartTime", event.getStartTime());
        editIntent.putExtra("eventEndTime", event.getEndTime());
        editIntent.putExtra("eventType", event.getEventType());
        editIntent.putExtra("eventRewardPoints", event.getRewardPoints());
        if (event.getRegistrationFee() != null) {
            editIntent.putExtra("eventRegistrationFee", event.getRegistrationFee());
        }
        editIntent.putExtra("orgName", currentOrgName);
        if (currentOrgId != null && currentOrgId > 0) {
            editIntent.putExtra("orgId", currentOrgId);
        }
        startActivity(editIntent);
    }
    
    private void onStartAttendance(Event event) {
        // Navigate to Start Event Attendance Activity
        Intent startAttendanceIntent = new Intent(ManageEventsActivity.this, StartEventAttendanceActivity.class);
        startAttendanceIntent.putExtra("orgId", currentOrgId);
        startAttendanceIntent.putExtra("eventId", (long) event.getId()); // Convert int to long
        startAttendanceIntent.putExtra("eventName", event.getTitle());
        startActivity(startAttendanceIntent);
    }
    
    private void onViewAttendance(Event event) {
        // Navigate to Event Attendance Records Activity
        Intent viewAttendanceIntent = new Intent(ManageEventsActivity.this, EventAttendanceRecordsActivity.class);
        viewAttendanceIntent.putExtra("orgId", currentOrgId);
        viewAttendanceIntent.putExtra("eventId", (long) event.getId()); // Convert int to long
        viewAttendanceIntent.putExtra("eventName", event.getTitle());
        startActivity(viewAttendanceIntent);
    }
    
    private void checkActiveSessionForEvent(Event event) {
        if (event == null || event.getId() <= 0) {
            return;
        }
        
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/event-attendance/events/" + event.getId() + "/active-session";
        
        com.android.volley.toolbox.JsonObjectRequest request = new com.android.volley.toolbox.JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        if (response.has("active") && response.getBoolean("active")) {
                            // Active session exists
                            event.setHasActiveSession(true);
                            eventAdapter.notifyItemChanged(eventsList.indexOf(event));
                            android.util.Log.d("ManageEventsActivity", "Active session found for event: " + event.getTitle());
                        } else {
                            event.setHasActiveSession(false);
                            eventAdapter.notifyItemChanged(eventsList.indexOf(event));
                        }
                    } catch (JSONException e) {
                        android.util.Log.e("ManageEventsActivity", "Error parsing active session response", e);
                        event.setHasActiveSession(false);
                        eventAdapter.notifyItemChanged(eventsList.indexOf(event));
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    // No active session (404) or error - default to false
                    event.setHasActiveSession(false);
                    int index = eventsList.indexOf(event);
                    if (index >= 0) {
                        eventAdapter.notifyItemChanged(index);
                    }
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        android.util.Log.d("ManageEventsActivity", "No active session for event: " + event.getTitle());
                    } else {
                        android.util.Log.e("ManageEventsActivity", "Error checking active session for event: " + event.getTitle());
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
    
    private void onViewFeedback(Event event) {
        if (event == null) {
            return;
        }
        
        try {
            // Navigate to View Event Feedbacks Activity
            Intent viewFeedbackIntent = new Intent(ManageEventsActivity.this, com.example.occasio.feedback.ViewEventFeedbacksActivity.class);
            long eventIdLong = (long) event.getId();
            viewFeedbackIntent.putExtra("eventId", eventIdLong);
            viewFeedbackIntent.putExtra("eventName", event.getTitle() != null ? event.getTitle() : "Event");
            startActivity(viewFeedbackIntent);
        } catch (Exception e) {
            android.util.Log.e("ManageEventsActivity", "Error navigating to feedback view: " + e.getMessage(), e);
        }
    }

    private void onEventDelete(Event event) {
        // Show confirmation dialog
        new AlertDialog.Builder(this)
            .setTitle("🗑️ Delete Event")
            .setMessage("Are you sure you want to delete this event?\n\n" +
                       "Event: " + event.getTitle() + "\n" +
                       "Date: " + event.getStartTime() + "\n\n" +
                       "This will permanently remove:\n" +
                       "• All event details\n" +
                       "• All attendee registrations\n" +
                       "• Event history and analytics\n\n" +
                       "This action CANNOT be undone!")
            .setPositiveButton("Yes, Delete Event", (dialog, which) -> confirmEventDeletion(event))
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setCancelable(false)
            .show();
    }

    private void confirmEventDeletion(Event event) {
        // Show final confirmation
        new AlertDialog.Builder(this)
            .setTitle("🚨 Final Confirmation")
            .setMessage("This is your last chance to cancel!\n\n" +
                       "Event: " + event.getTitle() + "\n" +
                       "Are you absolutely sure you want to delete this event?")
            .setPositiveButton("Confirm Delete", (dialog, which) -> performEventDeletion(event))
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setCancelable(false)
            .show();
    }

    private void performEventDeletion(Event event) {
        // Show loading dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
            .setTitle("Deleting Event...")
            .setMessage("Please wait while we delete the event.\nThis may take a few moments.")
            .setCancelable(false)
            .create();
        loadingDialog.show();

        // Delete event
        // Backend returns 204 No Content on success, not JSON
        com.android.volley.toolbox.StringRequest deleteRequest = new com.android.volley.toolbox.StringRequest(
            Request.Method.DELETE,
            DELETE_EVENT_URL + event.getId(),
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    loadingDialog.dismiss();
                    // Success - 204 No Content means success
                    // Show success message
                    new AlertDialog.Builder(ManageEventsActivity.this)
                        .setTitle("✅ Event Deleted")
                        .setMessage("The event '" + event.getTitle() + "' has been successfully deleted.")
                        .setPositiveButton("OK", (dialog, which) -> {
                            // Remove from local list and refresh
                            eventsList.remove(event);
                            eventAdapter.notifyDataSetChanged();
                            
                            // Show updated count
                            android.util.Log.d("ManageEventsActivity", "Event deleted. " + eventsList.size() + " events remaining.");
                        })
                        .setCancelable(false)
                        .show();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    loadingDialog.dismiss();
                    String errorMessage = "Unable to delete event";
                    
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        
                        // Try to parse error response body
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            if (!responseBody.isEmpty()) {
                                JSONObject errorJson = new JSONObject(responseBody);
                                errorMessage = errorJson.optString("message", errorJson.optString("error", errorMessage));
                            }
                        } catch (Exception e) {
                            // If parsing fails, use status code
                            switch (statusCode) {
                                case 404:
                                    errorMessage = "Event not found or already deleted";
                                    // If already deleted, treat as success
                                    eventsList.remove(event);
                                    eventAdapter.notifyDataSetChanged();
                                    android.util.Log.d("ManageEventsActivity", "Event already deleted. " + eventsList.size() + " events remaining.");
                                    return; // Don't show error dialog
                                case 403:
                                    errorMessage = "You don't have permission to delete this event";
                                    break;
                                case 500:
                                    errorMessage = "Server error occurred. Please try again later";
                                    break;
                                default:
                                    errorMessage = "Network error (HTTP " + statusCode + ")";
                                    break;
                            }
                        }
                    } else if (error.getMessage() != null) {
                        if (error.getMessage().contains("timeout") || error.getMessage().contains("Timeout")) {
                            errorMessage = "Connection timeout. Please check your internet connection.";
                        } else if (error.getMessage().contains("No address") || error.getMessage().contains("Unable to resolve")) {
                            errorMessage = "Cannot reach server. Please check your connection.";
                        } else {
                            errorMessage = "Connection error: " + error.getMessage();
                        }
                    } else {
                        errorMessage = "Network error. Please check your connection.";
                    }
                    
                    showEventDeleteError(errorMessage, event);
                }
            }
        );

        deleteRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(deleteRequest);
    }

    private void showEventDeleteError(String message, Event event) {
        new AlertDialog.Builder(this)
            .setTitle("❌ Delete Failed")
            .setMessage(message + "\n\nEvent: " + event.getTitle() + "\n\nPlease try again or contact support if the problem persists.")
            .setPositiveButton("Try Again", (dialog, which) -> onEventDelete(event))
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    // Event data class
    public static class Event {
        private int id;
        private String title;
        private String description;
        private String location;
        private String startTime;
        private String endTime;
        private String eventType;
        private String organizerId;
        private int rewardPoints;
        private Double registrationFee;
        private boolean hasActiveSession = false;

        public Event(int id, String title, String description, String location, 
                    String startTime, String endTime, String eventType, String organizerId, int rewardPoints) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.location = location;
            this.startTime = startTime;
            this.endTime = endTime;
            this.eventType = eventType;
            this.organizerId = organizerId;
            this.rewardPoints = rewardPoints;
        }

        // Getters
        public int getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getLocation() { return location; }
        public String getStartTime() { return startTime; }
        public String getEndTime() { return endTime; }
        public String getEventType() { return eventType; }
        public String getOrganizerId() { return organizerId; }
        public int getRewardPoints() { return rewardPoints; }
        public Double getRegistrationFee() { return registrationFee; }
        public void setRegistrationFee(Double registrationFee) { this.registrationFee = registrationFee; }
        public boolean hasActiveSession() { return hasActiveSession; }
        public void setHasActiveSession(boolean hasActiveSession) { this.hasActiveSession = hasActiveSession; }
    }
}
