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
import org.json.JSONException;
import org.json.JSONObject;

public class EditFeedbackActivity extends AppCompatActivity {
    private TextView eventNameTextView;
    private RatingBar ratingBar;
    private EditText commentEditText;
    private Button updateButton;
    private Button cancelButton;
    
    private RequestQueue requestQueue;
    private static final String BASE_URL = com.example.occasio.utils.ServerConfig.BASE_URL;
    private String currentUsername;
    private Long feedbackId;
    private Long eventId;
    private String eventName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_feedback);

        initializeViews();
        
        Intent intent = getIntent();
        if (intent != null) {
            currentUsername = intent.getStringExtra("username");
            feedbackId = intent.getLongExtra("feedbackId", -1L);
            eventId = intent.getLongExtra("eventId", -1L);
            eventName = intent.getStringExtra("eventName");
            int rating = intent.getIntExtra("rating", 5);
            String comment = intent.getStringExtra("comment");
            
            if (eventName != null && eventNameTextView != null) {
                eventNameTextView.setText(eventName);
            }
            
            if (ratingBar != null) {
                ratingBar.setRating(rating);
            }
            
            if (comment != null && commentEditText != null) {
                commentEditText.setText(comment);
            }
        }

        if (currentUsername == null || currentUsername.isEmpty()) {
            finish();
            return;
        }

        if (feedbackId == null || feedbackId <= 0) {
            finish();
            return;
        }

        requestQueue = Volley.newRequestQueue(this);
        
        if (updateButton != null) {
        updateButton.setOnClickListener(v -> updateFeedback());
        }
        if (cancelButton != null) {
        cancelButton.setOnClickListener(v -> finish());
        }
    }

    private void initializeViews() {
        eventNameTextView = findViewById(R.id.edit_feedback_event_name_tv);
        ratingBar = findViewById(R.id.edit_feedback_rating_bar);
        commentEditText = findViewById(R.id.edit_feedback_comment_et);
        updateButton = findViewById(R.id.edit_feedback_update_btn);
        cancelButton = findViewById(R.id.edit_feedback_cancel_btn);
        
        // Null checks
        if (eventNameTextView == null || ratingBar == null || commentEditText == null ||
            updateButton == null || cancelButton == null) {
            finish();
            return;
        }
    }

    private void updateFeedback() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            finish();
            return;
        }

        if (feedbackId == null || feedbackId <= 0) {
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
            requestBody.put("comment", comment);
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

        String url = BASE_URL + "/api/feedback/" + feedbackId + "?username=" + encodedUsername;

        updateButton.setEnabled(false);
        updateButton.setText("Updating...");

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            boolean success = response.getBoolean("success");
                            String message = response.optString("message", "Feedback updated successfully");

                            if (success) {
                                new AlertDialog.Builder(EditFeedbackActivity.this)
                                        .setTitle("✅ Feedback Updated")
                                        .setMessage(message)
                                        .setPositiveButton("OK", (dialog, which) -> {
                                            dialog.dismiss();
                                            finish();
                                        })
                                        .setCancelable(false)
                                        .show();
                            } else {
                                updateButton.setEnabled(true);
                                updateButton.setText("Update Feedback");
                            }
                        } catch (JSONException e) {
                            updateButton.setEnabled(true);
                            updateButton.setText("Update Feedback");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMessage = "Failed to update feedback";
                        
                        if (error.networkResponse != null) {
                            int statusCode = error.networkResponse.statusCode;
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                JSONObject errorJson = new JSONObject(responseBody);
                                errorMessage = errorJson.optString("message", errorMessage);
                            } catch (Exception e) {
                                if (statusCode == 403) {
                                    errorMessage = "You can only update your own feedback";
                                } else if (statusCode == 404) {
                                    errorMessage = "Feedback not found";
                                } else {
                                    errorMessage = "Error (HTTP " + statusCode + ")";
                                }
                            }
                        }

                        updateButton.setEnabled(true);
                        updateButton.setText("Update Feedback");
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

