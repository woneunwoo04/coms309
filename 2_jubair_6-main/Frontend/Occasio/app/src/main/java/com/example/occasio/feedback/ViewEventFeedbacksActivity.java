package com.example.occasio.feedback;

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
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ViewEventFeedbacksActivity extends AppCompatActivity {
    private RecyclerView feedbacksRecyclerView;
    private TextView emptyStateTextView;
    private TextView eventNameTextView;
    private TextView averageRatingTextView;
    private Button backButton;
    private Button refreshButton;
    
    private RequestQueue requestQueue;
    private EventFeedbackAdapter feedbackAdapter;
    private List<EventFeedbackItem> feedbacksList;
    private static final String BASE_URL = com.example.occasio.utils.ServerConfig.BASE_URL;
    private Long eventId;
    private String eventName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_event_feedbacks);

        initializeViews();
        
        Intent intent = getIntent();
        if (intent != null) {
            eventId = intent.getLongExtra("eventId", -1L);
            eventName = intent.getStringExtra("eventName");
            
            android.util.Log.d("ViewEventFeedbacks", "Received eventId: " + eventId + ", eventName: " + eventName);
            
            if (eventName != null && eventNameTextView != null) {
                eventNameTextView.setText(eventName);
            } else if (eventNameTextView != null) {
                eventNameTextView.setText("Event Feedback");
            }
        } else {
            android.util.Log.e("ViewEventFeedbacks", "Intent is null!");
            finish();
            return;
        }

        if (eventId == null || eventId <= 0) {
            android.util.Log.e("ViewEventFeedbacks", "Invalid event ID: " + eventId);
            finish();
            return;
        }

        requestQueue = Volley.newRequestQueue(this);
        feedbacksList = new ArrayList<>();

        feedbackAdapter = new EventFeedbackAdapter(feedbacksList);
        feedbacksRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        feedbacksRecyclerView.setAdapter(feedbackAdapter);

        backButton.setOnClickListener(v -> finish());
        refreshButton.setOnClickListener(v -> {
            loadFeedbacks();
            loadAverageRating();
        });

        loadFeedbacks();
        loadAverageRating();
    }

    private void initializeViews() {
        feedbacksRecyclerView = findViewById(R.id.view_event_feedbacks_recycler_view);
        emptyStateTextView = findViewById(R.id.view_event_feedbacks_empty_tv);
        eventNameTextView = findViewById(R.id.view_event_feedbacks_event_name_tv);
        averageRatingTextView = findViewById(R.id.view_event_feedbacks_rating_tv);
        backButton = findViewById(R.id.view_event_feedbacks_back_btn);
        refreshButton = findViewById(R.id.view_event_feedbacks_refresh_btn);
        
        // Null checks
        if (feedbacksRecyclerView == null || emptyStateTextView == null || 
            eventNameTextView == null || averageRatingTextView == null ||
            backButton == null || refreshButton == null) {
            finish();
            return;
        }
    }

    private void loadFeedbacks() {
        String url = BASE_URL + "/api/feedback/event/" + eventId;

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
                                                String username = feedbackObj.optString("username", "Unknown User");
                                                int rating = feedbackObj.optInt("rating", 5);
                                                String comment = feedbackObj.optString("comment", "");
                                                String createdAt = feedbackObj.optString("createdAt", "");
                                                
                                                if (id != null) {
                                                    EventFeedbackItem item = new EventFeedbackItem(
                                                            id,
                                                            username != null ? username : "Unknown User",
                                                            rating,
                                                            comment != null ? comment : "",
                                                            createdAt != null ? createdAt : ""
                                                    );
                                                    
                                                    feedbacksList.add(item);
                                                }
                                            } catch (JSONException e) {
                                                android.util.Log.e("ViewEventFeedbacks", "Error parsing feedback " + i, e);
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

    private void loadAverageRating() {
        String url = BASE_URL + "/api/feedback/event/" + eventId + "/average-rating";

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            if (response.getBoolean("success")) {
                                double avgRating = response.optDouble("averageRating", 0.0);
                                long count = response.optLong("count", 0);
                                
                                if (averageRatingTextView != null) {
                                    if (count > 0) {
                                        averageRatingTextView.setText(String.format("⭐ Average Rating: %.1f/5.0 (%d feedbacks)", avgRating, count));
                                    } else {
                                        averageRatingTextView.setText("⭐ No feedbacks yet");
                                    }
                                    averageRatingTextView.setVisibility(View.VISIBLE);
                                }
                            }
                        } catch (JSONException e) {
                            // Ignore
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Ignore
                    }
                }
        );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(5000, 1, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }

    // EventFeedbackItem data class
    public static class EventFeedbackItem {
        private Long id;
        private String username;
        private int rating;
        private String comment;
        private String createdAt;

        public EventFeedbackItem(Long id, String username, int rating, String comment, String createdAt) {
            this.id = id;
            this.username = username;
            this.rating = rating;
            this.comment = comment;
            this.createdAt = createdAt;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public int getRating() { return rating; }
        public String getComment() { return comment; }
        public String getCreatedAt() { return createdAt; }
    }
}

