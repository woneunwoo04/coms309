package com.example.occasio.feedback;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import com.example.occasio.utils.InAppNotificationHelper;
import org.json.JSONException;
import org.json.JSONObject;

public class FeedbackActivity extends AppCompatActivity {
    private TextView eventNameTextView;
    private RatingBar ratingBar;
    private EditText commentEditText;
    private Button submitButton;
    private Button cancelButton;
    
    private RequestQueue requestQueue;
    private static final String BASE_URL = com.example.occasio.utils.ServerConfig.BASE_URL;
    private String currentUsername;
    private Long eventId;
    private String eventName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        initializeViews();
        
        Intent intent = getIntent();
        if (intent != null) {
            currentUsername = intent.getStringExtra("username");
            eventId = intent.getLongExtra("eventId", -1L);
            eventName = intent.getStringExtra("eventName");
            
            if (eventName != null && eventNameTextView != null) {
                eventNameTextView.setText(eventName);
            }
        }

        if (currentUsername == null || currentUsername.isEmpty()) {
            finish();
            return;
        }

        if (eventId == null || eventId <= 0) {
            finish();
            return;
        }

        requestQueue = Volley.newRequestQueue(this);
        
        if (submitButton != null) {
        submitButton.setOnClickListener(v -> submitFeedback());
        }
        if (cancelButton != null) {
        cancelButton.setOnClickListener(v -> finish());
        }
    }

    private void initializeViews() {
        eventNameTextView = findViewById(R.id.feedback_event_name_tv);
        ratingBar = findViewById(R.id.feedback_rating_bar);
        commentEditText = findViewById(R.id.feedback_comment_et);
        submitButton = findViewById(R.id.feedback_submit_btn);
        cancelButton = findViewById(R.id.feedback_cancel_btn);
        
        // Null checks
        if (eventNameTextView == null || ratingBar == null || commentEditText == null ||
            submitButton == null || cancelButton == null) {
            finish();
            return;
        }
        
        // Set default rating to 5
            ratingBar.setRating(5);
    }

    private void submitFeedback() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            finish();
            return;
        }

        if (eventId == null || eventId <= 0) {
            return;
        }

        int rating = (int) ratingBar.getRating();
        if (rating < 1 || rating > 5) {
            return;
        }

        String comment = commentEditText.getText().toString().trim();

        // Prepare request body
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("rating", rating);
            if (!comment.isEmpty()) {
                requestBody.put("comment", comment);
            }
        } catch (JSONException e) {
            return;
        }

        // URL encode username
        String encodedUsername = "";
        try {
            encodedUsername = java.net.URLEncoder.encode(currentUsername.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedUsername = currentUsername.trim();
        }

        String url = BASE_URL + "/api/feedback?username=" + encodedUsername + "&eventId=" + eventId;

        submitButton.setEnabled(false);
        submitButton.setText("Submitting...");

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            boolean success = response.getBoolean("success");
                            String message = response.optString("message", "Feedback submitted successfully");

                            if (success) {
                                // Show notification for successful feedback submission
                                InAppNotificationHelper.showNotification(
                                    FeedbackActivity.this,
                                    "✅ Feedback Submitted",
                                    "Your feedback has been submitted successfully!"
                                );
                                
                                // Also show dialog for immediate confirmation
                                new AlertDialog.Builder(FeedbackActivity.this)
                                        .setTitle("✅ Feedback Submitted")
                                        .setMessage(message)
                                        .setPositiveButton("OK", (dialog, which) -> {
                                            dialog.dismiss();
                                            finish();
                                        })
                                        .setCancelable(false)
                                        .show();
                            } else {
                                submitButton.setEnabled(true);
                                submitButton.setText("Submit Feedback");
                            }
                        } catch (JSONException e) {
                            submitButton.setEnabled(true);
                            submitButton.setText("Submit Feedback");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMessage = "Failed to submit feedback";
                        
                        if (error.networkResponse != null) {
                            int statusCode = error.networkResponse.statusCode;
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                JSONObject errorJson = new JSONObject(responseBody);
                                errorMessage = errorJson.optString("message", errorMessage);
                            } catch (Exception e) {
                                if (statusCode == 409) {
                                    errorMessage = "You have already submitted feedback for this event";
                                } else if (statusCode == 400) {
                                    errorMessage = "Invalid feedback data";
                                } else if (statusCode == 404) {
                                    errorMessage = "Event or user not found";
                                } else {
                                    errorMessage = "Error (HTTP " + statusCode + ")";
                                }
                            }
                        }

                        submitButton.setEnabled(true);
                        submitButton.setText("Submit Feedback");
                    }
                }
        );

        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
                15000,
                2,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));

        requestQueue.add(request);
    }
}

