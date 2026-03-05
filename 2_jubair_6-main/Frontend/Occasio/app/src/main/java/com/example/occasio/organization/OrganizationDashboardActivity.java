package com.example.occasio.organization;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.StringRequest;
import com.example.occasio.R;
import com.example.occasio.base.BaseOrganizationNavigationActivity;
import com.example.occasio.events.CreateEventActivity;
import com.example.occasio.events.EditEventActivity;
import com.example.occasio.events.EventAdapter;
import com.example.occasio.events.ManageEventsActivity;
import com.example.occasio.events.StartEventAttendanceActivity;
import com.example.occasio.events.EventAttendanceRecordsActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class OrganizationDashboardActivity extends BaseOrganizationNavigationActivity {
    private TextView welcomeTextView;
    private Button addEventButton;
    private Button refreshButton;
    private RecyclerView eventsRecyclerView;
    
    private EventAdapter eventAdapter;
    private List<ManageEventsActivity.Event> eventsList;
    
    private static final String DELETE_ORG_URL = com.example.occasio.utils.ServerConfig.ORG_INFO_URL + "/";
    private static final String DELETE_EVENT_URL = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/events/";
    private static final String KEY_REMEMBER_ME = "remember_me";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organization_dashboard);

        welcomeTextView = findViewById(R.id.org_dashboard_welcome_tv);
        addEventButton = findViewById(R.id.org_dashboard_add_event_btn);
        refreshButton = findViewById(R.id.org_dashboard_refresh_btn);
        eventsRecyclerView = findViewById(R.id.org_dashboard_events_recycler_view);

        // Setup bottom navigation
        setupBottomNavigation();

        // Initialize events list and adapter
        eventsList = new ArrayList<>();
        eventAdapter = new EventAdapter(
            eventsList,
            this::onEventClick,
            this::onEventDelete,
            this::onStartAttendance,
            this::onViewAttendance,
            this::onViewFeedback
        );
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        eventsRecyclerView.setAdapter(eventAdapter);

        // Update welcome message
        updateWelcomeMessage();

        // Load events
        loadEvents();
        
        // Set click listeners
        addEventButton.setOnClickListener(v -> {
            Intent createEventIntent = new Intent(OrganizationDashboardActivity.this, CreateEventActivity.class);
            createEventIntent.putExtra("orgName", currentOrgName);
            createEventIntent.putExtra("orgId", currentOrgId != null ? currentOrgId : -1L);
            startActivity(createEventIntent);
        });

        refreshButton.setOnClickListener(v -> loadEvents());

        // Load events on startup
        loadEvents();
    }
    
    @Override
    protected void onResume() {
        super.onResume(); // This will restore orgId and orgName from SharedPreferences
        // Refresh welcome message and events when returning to this activity
        updateWelcomeMessage();
        loadEvents();
    }
    
    private void updateWelcomeMessage() {
        if (currentOrgName != null && !currentOrgName.isEmpty()) {
            welcomeTextView.setText("Welcome, " + currentOrgName + "!");
        } else {
            welcomeTextView.setText("Welcome!");
        }
    }

    private void loadEvents() {
        // Try to restore from SharedPreferences if missing (onResume should handle this, but double-check)
        if ((currentOrgId == null || currentOrgId <= 0) && sharedPreferences != null) {
            long savedOrgId = sharedPreferences.getLong(BaseOrganizationNavigationActivity.KEY_ORG_ID, -1L);
            if (savedOrgId > 0) {
                currentOrgId = savedOrgId;
            }
        }
        
        if (currentOrgId == null || currentOrgId <= 0) {
            // Don't redirect to login, just show a message and return
            android.util.Log.w("OrganizationDashboardActivity", "Organization ID not found, but not redirecting to login");
            return;
        }
        
        // Use the correct endpoint to get events by organization
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/events/organization/" + currentOrgId;
        
        JsonArrayRequest request = new JsonArrayRequest(
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
                        
                    } catch (Exception e) {
                        android.util.Log.e("OrganizationDashboardActivity", "Error parsing events: " + e.getMessage());
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
                    android.util.Log.e("OrganizationDashboardActivity", "Error loading events: " + error.toString());
                    eventsList.clear();
                    eventAdapter.notifyDataSetChanged();
                }
            }
        );
        
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }

    private void onEventClick(ManageEventsActivity.Event event) {
        // Navigate to edit event activity
        Intent editIntent = new Intent(OrganizationDashboardActivity.this, EditEventActivity.class);
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
    
    private void onStartAttendance(ManageEventsActivity.Event event) {
        // Navigate to Start Event Attendance Activity
        Intent startAttendanceIntent = new Intent(OrganizationDashboardActivity.this, StartEventAttendanceActivity.class);
        startAttendanceIntent.putExtra("orgId", currentOrgId);
        startAttendanceIntent.putExtra("eventId", (long) event.getId());
        startAttendanceIntent.putExtra("eventName", event.getTitle());
        startActivity(startAttendanceIntent);
    }
    
    private void onViewAttendance(ManageEventsActivity.Event event) {
        // Navigate to Event Attendance Records Activity
        Intent viewAttendanceIntent = new Intent(OrganizationDashboardActivity.this, EventAttendanceRecordsActivity.class);
        viewAttendanceIntent.putExtra("orgId", currentOrgId);
        viewAttendanceIntent.putExtra("eventId", (long) event.getId());
        viewAttendanceIntent.putExtra("eventName", event.getTitle());
        startActivity(viewAttendanceIntent);
    }
    
    private void checkActiveSessionForEvent(ManageEventsActivity.Event event) {
        if (event == null || event.getId() <= 0) {
            return;
        }
        
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/event-attendance/events/" + event.getId() + "/active-session";
        
        com.android.volley.toolbox.JsonObjectRequest request = new com.android.volley.toolbox.JsonObjectRequest(
            com.android.volley.Request.Method.GET,
            url,
            null,
            new com.android.volley.Response.Listener<org.json.JSONObject>() {
                @Override
                public void onResponse(org.json.JSONObject response) {
                    try {
                        if (response.has("active") && response.getBoolean("active")) {
                            // Active session exists
                            event.setHasActiveSession(true);
                            eventAdapter.notifyItemChanged(eventsList.indexOf(event));
                            android.util.Log.d("OrganizationDashboardActivity", "Active session found for event: " + event.getTitle());
                        } else {
                            event.setHasActiveSession(false);
                            eventAdapter.notifyItemChanged(eventsList.indexOf(event));
                        }
                    } catch (org.json.JSONException e) {
                        android.util.Log.e("OrganizationDashboardActivity", "Error parsing active session response", e);
                        event.setHasActiveSession(false);
                        eventAdapter.notifyItemChanged(eventsList.indexOf(event));
                    }
                }
            },
            new com.android.volley.Response.ErrorListener() {
                @Override
                public void onErrorResponse(com.android.volley.VolleyError error) {
                    // No active session (404) or error - default to false
                    event.setHasActiveSession(false);
                    int index = eventsList.indexOf(event);
                    if (index >= 0) {
                        eventAdapter.notifyItemChanged(index);
                    }
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        android.util.Log.d("OrganizationDashboardActivity", "No active session for event: " + event.getTitle());
                    } else {
                        android.util.Log.e("OrganizationDashboardActivity", "Error checking active session for event: " + event.getTitle());
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
    
    private void onViewFeedback(ManageEventsActivity.Event event) {
        if (event == null) {
            return;
        }
        
        try {
            // Navigate to View Event Feedbacks Activity
            Intent viewFeedbackIntent = new Intent(OrganizationDashboardActivity.this, com.example.occasio.feedback.ViewEventFeedbacksActivity.class);
            long eventIdLong = (long) event.getId();
            viewFeedbackIntent.putExtra("eventId", eventIdLong);
            viewFeedbackIntent.putExtra("eventName", event.getTitle() != null ? event.getTitle() : "Event");
            startActivity(viewFeedbackIntent);
        } catch (Exception e) {
            android.util.Log.e("OrganizationDashboardActivity", "Error navigating to feedback view: " + e.getMessage(), e);
        }
    }

    private void onEventDelete(ManageEventsActivity.Event event) {
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

    private void confirmEventDeletion(ManageEventsActivity.Event event) {
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

    private void performEventDeletion(ManageEventsActivity.Event event) {
        // Show loading dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
            .setTitle("Deleting Event...")
            .setMessage("Please wait while we delete the event.\nThis may take a few moments.")
            .setCancelable(false)
            .create();
        loadingDialog.show();

        // Delete event
        StringRequest deleteRequest = new StringRequest(
            Request.Method.DELETE,
            DELETE_EVENT_URL + event.getId(),
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    loadingDialog.dismiss();
                    // Success - 204 No Content means success
                    new AlertDialog.Builder(OrganizationDashboardActivity.this)
                        .setTitle("✅ Event Deleted")
                        .setMessage("The event '" + event.getTitle() + "' has been successfully deleted.")
                        .setPositiveButton("OK", (dialog, which) -> {
                            // Remove from local list and refresh
                            eventsList.remove(event);
                            eventAdapter.notifyDataSetChanged();
                            
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
                        
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            if (!responseBody.isEmpty()) {
                                JSONObject errorJson = new JSONObject(responseBody);
                                errorMessage = errorJson.optString("message", errorJson.optString("error", errorMessage));
                            }
                        } catch (Exception e) {
                            switch (statusCode) {
                                case 404:
                                    errorMessage = "Event not found or already deleted";
                                    eventsList.remove(event);
                                    eventAdapter.notifyDataSetChanged();
                                    return;
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
                    }
                    
                    showEventDeleteError(errorMessage, event);
                }
            }
        );

        deleteRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(deleteRequest);
    }

    private void showEventDeleteError(String message, ManageEventsActivity.Event event) {
        new AlertDialog.Builder(this)
            .setTitle("❌ Delete Failed")
            .setMessage(message + "\n\nEvent: " + event.getTitle() + "\n\nPlease try again or contact support if the problem persists.")
            .setPositiveButton("Try Again", (dialog, which) -> onEventDelete(event))
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    private void logoutOrganization() {
        // Clear Remember Me data
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_ORG_NAME);
        editor.putBoolean(KEY_REMEMBER_ME, false);
        editor.apply();
        
        // Clear organization data from base class
        currentOrgName = null;
        currentOrgId = null;

        
        // Navigate back to organization login
        Intent intent = new Intent(OrganizationDashboardActivity.this, OrganizationLoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
