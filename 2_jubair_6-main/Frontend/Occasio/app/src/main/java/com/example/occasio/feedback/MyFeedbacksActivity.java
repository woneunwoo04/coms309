package com.example.occasio.feedback;

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
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MyFeedbacksActivity extends AppCompatActivity {
    private RecyclerView feedbacksRecyclerView;
    private TextView emptyStateTextView;
    private Button backButton;
    private Button refreshButton;
    private Button addFeedbackButton;
    
    private RequestQueue requestQueue;
    private FeedbackAdapter feedbackAdapter;
    private List<FeedbackItem> feedbacksList;
    private static final String BASE_URL = com.example.occasio.utils.ServerConfig.BASE_URL;
    private String currentUsername;
    private String userRole; // "STUDENT", "PROFESSOR", or "ORGANIZATION"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_feedbacks);

        initializeViews();
        
        Intent intent = getIntent();
        if (intent != null) {
            currentUsername = intent.getStringExtra("username");
        }

        if (currentUsername == null || currentUsername.isEmpty()) {
            finish();
            return;
        }

        requestQueue = Volley.newRequestQueue(this);
        feedbacksList = new ArrayList<>();

        try {
        feedbackAdapter = new FeedbackAdapter(feedbacksList, this::onFeedbackClick, this::onFeedbackDelete);
        feedbacksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        feedbacksRecyclerView.setAdapter(feedbackAdapter);

            if (backButton != null) {
        backButton.setOnClickListener(v -> finish());
            }
            if (refreshButton != null) {
        refreshButton.setOnClickListener(v -> loadFeedbacks());
            }
            if (addFeedbackButton != null) {
        addFeedbackButton.setOnClickListener(v -> showAddFeedbackDialog());
            }

            // Check user role first, then load feedbacks
            checkUserRole();
        } catch (Exception e) {
            android.util.Log.e("MyFeedbacksActivity", "Error in onCreate", e);
        }
    }

    private void initializeViews() {
        feedbacksRecyclerView = findViewById(R.id.my_feedbacks_recycler_view);
        emptyStateTextView = findViewById(R.id.my_feedbacks_empty_tv);
        backButton = findViewById(R.id.my_feedbacks_back_btn);
        refreshButton = findViewById(R.id.my_feedbacks_refresh_btn);
        addFeedbackButton = findViewById(R.id.my_feedbacks_add_btn);
        
        // Null checks
        if (feedbacksRecyclerView == null || emptyStateTextView == null || 
            backButton == null || refreshButton == null || addFeedbackButton == null) {
            finish();
            return;
        }
    }

    private void checkUserRole() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            return;
        }

        String encodedUsername = "";
        try {
            encodedUsername = java.net.URLEncoder.encode(currentUsername.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedUsername = currentUsername.trim();
        }

        String url = BASE_URL + "/user_info/username/" + encodedUsername;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            if (response.has("role")) {
                                userRole = response.getString("role");
                                
                                // Organizations can't submit feedback, only view
                                if ("ORGANIZATION".equals(userRole) && addFeedbackButton != null) {
                                    addFeedbackButton.setVisibility(View.GONE);
                                }
                            }
                            
                            // Load feedbacks after role is checked
                            loadFeedbacks();
                        } catch (JSONException e) {
                            android.util.Log.e("MyFeedbacksActivity", "Error parsing user role", e);
                            // Still try to load feedbacks
                            loadFeedbacks();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        android.util.Log.e("MyFeedbacksActivity", "Error fetching user role", error);
                        // Still try to load feedbacks
                        loadFeedbacks();
                    }
                }
        );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(10000, 1, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }

    private void loadFeedbacks() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            return;
        }

        String encodedUsername = "";
        try {
            encodedUsername = java.net.URLEncoder.encode(currentUsername.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedUsername = currentUsername.trim();
        }

        String url = BASE_URL + "/api/feedback/user?username=" + encodedUsername;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            boolean success = response.getBoolean("success");
                            if (success) {
                                if (feedbacksList != null) {
                                feedbacksList.clear();
                                } else {
                                    feedbacksList = new ArrayList<>();
                                }
                                
                                if (response.has("feedbacks")) {
                                JSONArray feedbacksArray = response.getJSONArray("feedbacks");
                                    if (feedbacksArray != null) {
                                for (int i = 0; i < feedbacksArray.length(); i++) {
                                            try {
                                    JSONObject feedbackObj = feedbacksArray.getJSONObject(i);
                                                if (feedbackObj == null) continue;
                                                
                                                Long id = feedbackObj.has("id") && !feedbackObj.isNull("id") 
                                                    ? feedbackObj.getLong("id") : null;
                                                Long eventId = feedbackObj.has("eventId") && !feedbackObj.isNull("eventId")
                                                    ? feedbackObj.getLong("eventId") : null;
                                                String eventName = feedbackObj.optString("eventName", "Unknown Event");
                                                int rating = feedbackObj.optInt("rating", 5);
                                                String comment = feedbackObj.optString("comment", "");
                                                String createdAt = feedbackObj.optString("createdAt", "");
                                                String updatedAt = feedbackObj.optString("updatedAt", "");
                                                
                                                if (id != null && eventId != null) {
                                    FeedbackItem item = new FeedbackItem(
                                                            id,
                                                            eventId,
                                                            eventName != null ? eventName : "Unknown Event",
                                                            rating,
                                                            comment != null ? comment : "",
                                                            createdAt != null ? createdAt : "",
                                                            updatedAt != null ? updatedAt : ""
                                    );
                                    
                                    feedbacksList.add(item);
                                }
                                            } catch (JSONException e) {
                                                android.util.Log.e("MyFeedbacksActivity", "Error parsing feedback " + i, e);
                                                continue; // Skip this feedback and continue
                                            }
                                        }
                                    }
                                }
                                
                                if (feedbackAdapter != null) {
                                feedbackAdapter.notifyDataSetChanged();
                                }
                                
                                if (feedbacksList == null || feedbacksList.isEmpty()) {
                                    if (emptyStateTextView != null) {
                                    emptyStateTextView.setVisibility(View.VISIBLE);
                                    }
                                    if (feedbacksRecyclerView != null) {
                                    feedbacksRecyclerView.setVisibility(View.GONE);
                                    }
                                } else {
                                    if (emptyStateTextView != null) {
                                    emptyStateTextView.setVisibility(View.GONE);
                                    }
                                    if (feedbacksRecyclerView != null) {
                                    feedbacksRecyclerView.setVisibility(View.VISIBLE);
                                    }
                                }
                            } else {
                                String message = response.optString("message", "Failed to load feedbacks");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMessage = "Failed to load feedbacks";
                        if (error.networkResponse != null) {
                            errorMessage += " (HTTP " + error.networkResponse.statusCode + ")";
                        }
                    }
                }
        );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }
    
    private void showAddFeedbackDialog() {
        // Organizations can't submit feedback
        if ("ORGANIZATION".equals(userRole)) {
            return;
        }
        
        // Load attended events and show them in a dialog
        String encodedUsername = "";
        try {
            encodedUsername = java.net.URLEncoder.encode(currentUsername.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedUsername = currentUsername.trim();
        }

        String attendanceUrl = BASE_URL + "/api/event-attendance/my-records?username=" + encodedUsername;
        JsonArrayRequest attendanceRequest = new JsonArrayRequest(
                Request.Method.GET,
                attendanceUrl,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            // Get existing feedbacks to check which events already have feedback
                            java.util.Map<Long, FeedbackItem> feedbackMap = new java.util.HashMap<>();
                            if (feedbacksList != null) {
                            for (FeedbackItem item : feedbacksList) {
                                    if (item != null && item.getEventId() != null) {
                                feedbackMap.put(item.getEventId(), item);
                                    }
                                }
                            }
                            
                            // Collect unique attended events
                            java.util.List<AttendedEvent> attendedEvents = new ArrayList<>();
                            java.util.Set<Long> addedEventIds = new java.util.HashSet<>();
                            
                            if (response != null && response.length() > 0) {
                                for (int i = 0; i < response.length(); i++) {
                                    try {
                                    JSONObject recordObj = response.getJSONObject(i);
                                        if (recordObj == null) continue;
                                    
                                    // Only process VERIFIED attendance records
                                    String status = recordObj.optString("status", "");
                                    if (!"VERIFIED".equals(status)) {
                                        continue;
                                    }
                                    
                                    // Get event ID
                                    Long eventId = null;
                                    String eventName = "Unknown Event";
                                    
                                    if (recordObj.has("event") && !recordObj.isNull("event")) {
                                        JSONObject eventObj = recordObj.getJSONObject("event");
                                            if (eventObj != null) {
                                                if (eventObj.has("id") && !eventObj.isNull("id")) {
                                            eventId = eventObj.getLong("id");
                                        }
                                        eventName = eventObj.optString("eventName", 
                                            eventObj.optString("title", "Unknown Event"));
                                                if (eventName == null || eventName.isEmpty()) {
                                                    eventName = "Unknown Event";
                                                }
                                            }
                                    }
                                    
                                    // Only add if event ID exists and not already added
                                        if (eventId != null && eventId > 0 && !addedEventIds.contains(eventId)) {
                                        FeedbackItem existingFeedback = feedbackMap.get(eventId);
                                        attendedEvents.add(new AttendedEvent(eventId, eventName, existingFeedback));
                                        addedEventIds.add(eventId);
                                        }
                                    } catch (JSONException e) {
                                        android.util.Log.e("MyFeedbacksActivity", "Error parsing record " + i, e);
                                        continue; // Skip this record and continue
                                    }
                                }
                            }
                            
                            if (attendedEvents.isEmpty()) {
                                return;
                            }
                            
                            // Show dialog with attended events
                            showEventSelectionDialog(attendedEvents);
                        } catch (Exception e) {
                            android.util.Log.e("MyFeedbacksActivity", "Error parsing attendance records", e);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMessage = "Failed to load attended events";
                        if (error.networkResponse != null) {
                            errorMessage += " (HTTP " + error.networkResponse.statusCode + ")";
                        }
                    }
                }
        );

        attendanceRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(attendanceRequest);
    }
    
    private void showEventSelectionDialog(java.util.List<AttendedEvent> events) {
        String[] eventNames = new String[events.size()];
        for (int i = 0; i < events.size(); i++) {
            AttendedEvent event = events.get(i);
            String prefix = event.hasFeedback() ? "✏️ " : "💬 ";
            eventNames[i] = prefix + event.getEventName();
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Select Event to Add/Edit Feedback")
                .setItems(eventNames, (dialog, which) -> {
                    AttendedEvent selectedEvent = events.get(which);
                    if (selectedEvent.hasFeedback()) {
                        // Navigate to edit feedback
                        FeedbackItem feedback = selectedEvent.getFeedback();
                        Intent editIntent = new Intent(MyFeedbacksActivity.this, EditFeedbackActivity.class);
                        editIntent.putExtra("username", currentUsername);
                        editIntent.putExtra("feedbackId", feedback.getId());
                        editIntent.putExtra("eventId", feedback.getEventId());
                        editIntent.putExtra("eventName", feedback.getEventName());
                        editIntent.putExtra("rating", feedback.getRating());
                        editIntent.putExtra("comment", feedback.getComment());
                        startActivity(editIntent);
                    } else {
                        // Navigate to create feedback
                        Intent feedbackIntent = new Intent(MyFeedbacksActivity.this, FeedbackActivity.class);
                        feedbackIntent.putExtra("username", currentUsername);
                        feedbackIntent.putExtra("eventId", selectedEvent.getEventId());
                        feedbackIntent.putExtra("eventName", selectedEvent.getEventName());
                        startActivity(feedbackIntent);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void onFeedbackClick(FeedbackItem feedback) {
        if (feedback == null || currentUsername == null || currentUsername.isEmpty()) {
            return;
        }
        
        try {
        // Navigate to edit feedback activity
        Intent editIntent = new Intent(MyFeedbacksActivity.this, EditFeedbackActivity.class);
        editIntent.putExtra("username", currentUsername);
            if (feedback.getId() != null) {
        editIntent.putExtra("feedbackId", feedback.getId());
            }
            if (feedback.getEventId() != null) {
        editIntent.putExtra("eventId", feedback.getEventId());
            }
            editIntent.putExtra("eventName", feedback.getEventName() != null ? feedback.getEventName() : "Unknown Event");
        editIntent.putExtra("rating", feedback.getRating());
            editIntent.putExtra("comment", feedback.getComment() != null ? feedback.getComment() : "");
        startActivity(editIntent);
        } catch (Exception e) {
            android.util.Log.e("MyFeedbacksActivity", "Error navigating to edit feedback", e);
        }
    }

    private void onFeedbackDelete(FeedbackItem feedback) {
        if (feedback == null) {
            return;
        }
        
        try {
            String eventName = feedback.getEventName() != null ? feedback.getEventName() : "this event";
        new AlertDialog.Builder(this)
                .setTitle("Delete Feedback")
                    .setMessage("Are you sure you want to delete your feedback for " + eventName + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteFeedback(feedback))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
        } catch (Exception e) {
            android.util.Log.e("MyFeedbacksActivity", "Error showing delete dialog", e);
        }
    }

    private void deleteFeedback(FeedbackItem feedback) {
        if (feedback == null || feedback.getId() == null || currentUsername == null || currentUsername.isEmpty()) {
            return;
        }
        
        String encodedUsername = "";
        try {
            encodedUsername = java.net.URLEncoder.encode(currentUsername.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedUsername = currentUsername.trim();
        }

        String url = BASE_URL + "/api/feedback/" + feedback.getId() + "?username=" + encodedUsername;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.DELETE,
                url,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            boolean success = response.getBoolean("success");
                            if (success) {
                                loadFeedbacks(); // Refresh list
                            } else {
                                String message = response.optString("message", "Failed to delete feedback");
                            }
                        } catch (JSONException e) {
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMessage = "Failed to delete feedback";
                        if (error.networkResponse != null) {
                            errorMessage += " (HTTP " + error.networkResponse.statusCode + ")";
                        }
                    }
                }
        );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh feedbacks when returning to this activity
        loadFeedbacks();
    }

    // FeedbackItem data class
    public static class FeedbackItem {
        private Long id; // null if no feedback exists yet
        private Long eventId;
        private String eventName;
        private int rating;
        private String comment;
        private String createdAt;
        private String updatedAt;
        private boolean hasFeedback; // Whether feedback exists

        // Constructor for events with feedback
        public FeedbackItem(Long id, Long eventId, String eventName, int rating, String comment, String createdAt, String updatedAt) {
            this.id = id;
            this.eventId = eventId;
            this.eventName = eventName;
            this.rating = rating;
            this.comment = comment;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.hasFeedback = true;
        }
        
        // Constructor for events without feedback (attended but no feedback yet)
        public FeedbackItem(Long eventId, String eventName) {
            this.id = null;
            this.eventId = eventId;
            this.eventName = eventName;
            this.rating = 0;
            this.comment = "";
            this.createdAt = "";
            this.updatedAt = "";
            this.hasFeedback = false;
        }

        public Long getId() { return id; }
        public Long getEventId() { return eventId; }
        public String getEventName() { return eventName; }
        public int getRating() { return rating; }
        public String getComment() { return comment; }
        public String getCreatedAt() { return createdAt; }
        public String getUpdatedAt() { return updatedAt; }
        public boolean hasFeedback() { return hasFeedback; }
    }
    
    // Helper class for attended events in dialog
    private static class AttendedEvent {
        private Long eventId;
        private String eventName;
        private FeedbackItem feedback; // null if no feedback exists
        
        public AttendedEvent(Long eventId, String eventName, FeedbackItem feedback) {
            this.eventId = eventId;
            this.eventName = eventName;
            this.feedback = feedback;
        }
        
        public Long getEventId() { return eventId; }
        public String getEventName() { return eventName; }
        public FeedbackItem getFeedback() { return feedback; }
        public boolean hasFeedback() { return feedback != null; }
    }
}

