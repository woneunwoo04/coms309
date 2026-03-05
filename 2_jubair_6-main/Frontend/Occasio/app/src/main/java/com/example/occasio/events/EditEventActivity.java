package com.example.occasio.events;

import androidx.appcompat.app.AppCompatActivity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import com.example.occasio.organization.OrganizationDashboardActivity;
import com.example.occasio.utils.ServerConfig;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

public class EditEventActivity extends AppCompatActivity {
    private EditText titleEditText;
    private EditText descriptionEditText;
    private EditText locationEditText;
    private EditText startTimeEditText;
    private EditText endTimeEditText;
    private EditText rewardPointsEditText;
    private EditText registrationFeeEditText;
    private Spinner eventTypeSpinner;
    private Button updateEventButton;
    private Button backButton;
    
    private RequestQueue requestQueue;
    private static final String BASE_URL = ServerConfig.BASE_URL;
    private static final String UPDATE_EVENT_URL = BASE_URL + "/api/events/";
    
    private int eventId;
    private String currentOrgName;
    private Long currentOrgId;
    
    // Date and time storage
    private int selectedStartYear, selectedStartMonth, selectedStartDay;
    private int selectedStartHour, selectedStartMinute;
    private int selectedEndYear, selectedEndMonth, selectedEndDay;
    private int selectedEndHour, selectedEndMinute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_event);

        // Initialize views
        titleEditText = findViewById(R.id.edit_event_title_edt);
        descriptionEditText = findViewById(R.id.edit_event_description_edt);
        locationEditText = findViewById(R.id.edit_event_location_edt);
        startTimeEditText = findViewById(R.id.edit_event_start_time_edt);
        endTimeEditText = findViewById(R.id.edit_event_end_time_edt);
        rewardPointsEditText = findViewById(R.id.edit_event_reward_points_edt);
        registrationFeeEditText = findViewById(R.id.edit_event_registration_fee_edt);
        eventTypeSpinner = findViewById(R.id.edit_event_type_spinner);
        updateEventButton = findViewById(R.id.edit_event_update_btn);
        backButton = findViewById(R.id.edit_event_back_btn);

        // Validate views are not null
        if (titleEditText == null || descriptionEditText == null || locationEditText == null ||
            startTimeEditText == null || endTimeEditText == null || rewardPointsEditText == null ||
            registrationFeeEditText == null || eventTypeSpinner == null || 
            updateEventButton == null || backButton == null) {
            finish();
            return;
        }

        requestQueue = Volley.newRequestQueue(this);

        // Get event data from intent
        Intent intent = getIntent();
        if (intent != null) {
            eventId = intent.getIntExtra("eventId", -1);
            currentOrgName = intent.getStringExtra("orgName");
            currentOrgId = intent.getLongExtra("orgId", -1L);
            
            // Populate fields with existing data
            titleEditText.setText(intent.getStringExtra("eventTitle"));
            descriptionEditText.setText(intent.getStringExtra("eventDescription"));
            locationEditText.setText(intent.getStringExtra("eventLocation"));
            startTimeEditText.setText(intent.getStringExtra("eventStartTime"));
            endTimeEditText.setText(intent.getStringExtra("eventEndTime"));
            
            // Populate reward points (default to 0 if not provided)
            int rewardPoints = intent.getIntExtra("eventRewardPoints", 0);
            rewardPointsEditText.setText(String.valueOf(rewardPoints));
            
            // Populate registration fee (if provided)
            if (intent.hasExtra("eventRegistrationFee")) {
                double registrationFee = intent.getDoubleExtra("eventRegistrationFee", 0.0);
                if (registrationFee > 0) {
                    registrationFeeEditText.setText(String.format("%.2f", registrationFee));
                }
            }
            
            // Populate event type spinner
            String[] eventTypes = {"Workshop", "Seminar", "Conference", "Social Event", "Sports", "Cultural", "Academic", "Networking", "Other"};
            android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                eventTypes
            );
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            eventTypeSpinner.setAdapter(spinnerAdapter);
            
            // Set spinner selection based on event type
            String eventType = intent.getStringExtra("eventType");
            if (eventType != null && !eventType.isEmpty()) {
                for (int i = 0; i < eventTypes.length; i++) {
                    if (eventTypes[i].equalsIgnoreCase(eventType)) {
                        eventTypeSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }
        
        if (eventId == -1) {
            finish();
            return;
        }

        // Initialize date/time from existing values or current time
        Calendar now = Calendar.getInstance();
        selectedStartYear = now.get(Calendar.YEAR);
        selectedStartMonth = now.get(Calendar.MONTH);
        selectedStartDay = now.get(Calendar.DAY_OF_MONTH);
        selectedStartHour = now.get(Calendar.HOUR_OF_DAY);
        selectedStartMinute = now.get(Calendar.MINUTE);
        
        selectedEndYear = selectedStartYear;
        selectedEndMonth = selectedStartMonth;
        selectedEndDay = selectedStartDay;
        selectedEndHour = selectedStartHour + 2;
        selectedEndMinute = selectedStartMinute;

        // Set click listeners for date/time pickers
        if (startTimeEditText != null) {
        startTimeEditText.setFocusable(false);
        startTimeEditText.setClickable(true);
        startTimeEditText.setOnClickListener(v -> showStartDateTimePicker());
        }
        
        if (endTimeEditText != null) {
        endTimeEditText.setFocusable(false);
        endTimeEditText.setClickable(true);
        endTimeEditText.setOnClickListener(v -> showEndDateTimePicker());
        }
        
        // Set click listeners
        if (updateEventButton != null) {
        updateEventButton.setOnClickListener(v -> updateEvent());
        }
        if (backButton != null) {
        backButton.setOnClickListener(v -> {
            // Simply finish to return to the previous activity (OrganizationDashboardActivity)
            // This avoids creating a duplicate instance
            finish();
        });
        }
    }
    
    private void showStartDateTimePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                selectedStartYear = year;
                selectedStartMonth = month;
                selectedStartDay = dayOfMonth;
                
                // After date is selected, show time picker
                TimePickerDialog timePickerDialog = new TimePickerDialog(
                    this,
                    (timeView, hourOfDay, minute) -> {
                        selectedStartHour = hourOfDay;
                        selectedStartMinute = minute;
                        
                        // Format and display
                        String dateTime = String.format("%04d-%02d-%02d %02d:%02d",
                            year, month + 1, dayOfMonth, hourOfDay, minute);
                        startTimeEditText.setText(dateTime);
                    },
                    selectedStartHour,
                    selectedStartMinute,
                    true // 24-hour format
                );
                timePickerDialog.show();
            },
            selectedStartYear,
            selectedStartMonth,
            selectedStartDay
        );
        datePickerDialog.show();
    }
    
    private void showEndDateTimePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
            this,
            (view, year, month, dayOfMonth) -> {
                selectedEndYear = year;
                selectedEndMonth = month;
                selectedEndDay = dayOfMonth;
                
                // After date is selected, show time picker
                TimePickerDialog timePickerDialog = new TimePickerDialog(
                    this,
                    (timeView, hourOfDay, minute) -> {
                        selectedEndHour = hourOfDay;
                        selectedEndMinute = minute;
                        
                        // Format and display
                        String dateTime = String.format("%04d-%02d-%02d %02d:%02d",
                            year, month + 1, dayOfMonth, hourOfDay, minute);
                        endTimeEditText.setText(dateTime);
                    },
                    selectedEndHour,
                    selectedEndMinute,
                    true // 24-hour format
                );
                timePickerDialog.show();
            },
            selectedEndYear,
            selectedEndMonth,
            selectedEndDay
        );
        datePickerDialog.show();
    }

    private void updateEvent() {
        // Validate views are not null
        if (titleEditText == null || descriptionEditText == null || locationEditText == null ||
            startTimeEditText == null || endTimeEditText == null || rewardPointsEditText == null ||
            registrationFeeEditText == null || eventTypeSpinner == null || updateEventButton == null) {
            return;
        }
        
        String title = titleEditText.getText().toString().trim();
        String description = descriptionEditText.getText().toString().trim();
        String location = locationEditText.getText().toString().trim();
        String startTime = startTimeEditText.getText().toString().trim();
        String endTime = endTimeEditText.getText().toString().trim();
        String rewardPointsStr = rewardPointsEditText.getText().toString().trim();
        String registrationFeeStr = registrationFeeEditText.getText().toString().trim();
        
        // Get event type from spinner with null check
        String eventType = null;
        if (eventTypeSpinner != null) {
        Object selectedItem = eventTypeSpinner.getSelectedItem();
        if (selectedItem != null) {
            eventType = selectedItem.toString();
            }
        }
        
        // Parse reward points (default to 0 if empty or invalid)
        int rewardPoints = 0;
        if (!rewardPointsStr.isEmpty()) {
            try {
                rewardPoints = Integer.parseInt(rewardPointsStr);
                if (rewardPoints < 0) {
                    return;
                }
            } catch (NumberFormatException e) {
                return;
            }
        }
        
        // Parse registration fee (0 or empty = null, meaning free event)
        Double registrationFee = null;
        if (!registrationFeeStr.isEmpty()) {
            try {
                double fee = Double.parseDouble(registrationFeeStr);
                if (fee < 0) {
                    return;
                }
                if (fee > 0 && fee < 0.50) {
                    return;
                }
                if (fee > 0) {
                    registrationFee = fee;
                }
                // If fee is 0, leave it as null (free event)
            } catch (NumberFormatException e) {
                return;
            }
        }

        // Validation
        if (title.isEmpty() || description.isEmpty() || location.isEmpty() || 
            startTime.isEmpty() || endTime.isEmpty()) {
            return;
        }

        if (title.length() < 3) {
            return;
        }

        if (description.length() < 10) {
            return;
        }

        if (eventId == -1) {
            return;
        }

        // Disable button during request
        updateEventButton.setEnabled(false);
        updateEventButton.setText("Updating...");
        
        // Create JSON object for event update
        try {
            JSONObject eventData = new JSONObject();
            eventData.put("eventName", title);
            eventData.put("title", title); // Also set title field
            eventData.put("description", description);
            eventData.put("location", location);
            
            // Backend expects startTime as LocalDateTime - format: "YYYY-MM-DDTHH:MM:SS"
            String formattedStartTime = formatDateTimeForBackend(startTime);
            eventData.put("startTime", formattedStartTime);
            
            // Format and add end time
            String formattedEndTime = formatDateTimeForBackend(endTime);
            eventData.put("endTime", formattedEndTime);
            
            // Add event type if available
            if (eventType != null && !eventType.isEmpty()) {
                eventData.put("eventType", eventType);
            }
            
            // Add reward points
            eventData.put("rewardPoints", rewardPoints);
            
            // Add registration fee if provided
            if (registrationFee != null && registrationFee > 0) {
                eventData.put("registrationFee", registrationFee);
            } else {
                // Explicitly set to null for free events
                eventData.put("registrationFee", JSONObject.NULL);
            }
            
            String updateUrl = UPDATE_EVENT_URL + eventId;
            
            // Debug logging
            android.util.Log.d("EditEventActivity", "Updating event ID: " + eventId + " with URL: " + updateUrl);
            android.util.Log.d("EditEventActivity", "Event data: " + eventData.toString(2));
            
            // Make PUT request
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.PUT,
                updateUrl,
                eventData,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        updateEventButton.setEnabled(true);
                        updateEventButton.setText("Update Event");
                        
                        android.util.Log.d("EditEventActivity", "✅ SUCCESS! Response: " + response.toString());
                        
                        try {
                            // Backend returns the updated event directly
                            String eventName = response.optString("eventName", title); // Use title as fallback
                            Long updatedEventId = response.optLong("id", eventId); // Use eventId as fallback
                            
                            android.util.Log.d("EditEventActivity", "Event updated: " + eventName + " (ID: " + updatedEventId + ")");
                            
                            // Show success message - user can navigate back themselves
                            
                            // Don't redirect - let user navigate back themselves
                        } catch (Exception e) {
                            android.util.Log.e("EditEventActivity", "Error parsing response: " + e.getMessage());
                            e.printStackTrace();
                            // Still show success even if parsing fails
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        updateEventButton.setEnabled(true);
                        updateEventButton.setText("Update Event");
                        
                        String errorMessage = "Failed to update event";
                        String debugInfo = "";
                        
                        if (error.networkResponse != null) {
                            int statusCode = error.networkResponse.statusCode;
                            debugInfo = "Status Code: " + statusCode;
                            
                            // Try to parse error response body
                            try {
                                String responseBody = new String(error.networkResponse.data, java.nio.charset.StandardCharsets.UTF_8);
                                android.util.Log.e("EditEventActivity", "Error response body: " + responseBody);
                                debugInfo += "\nResponse: " + responseBody;
                                
                                if (!responseBody.isEmpty()) {
                                    JSONObject errorJson = new JSONObject(responseBody);
                                    errorMessage = errorJson.optString("message", errorJson.optString("error", errorMessage));
                                }
                            } catch (Exception e) {
                                android.util.Log.e("EditEventActivity", "Error parsing error response: " + e.getMessage());
                                // If parsing fails, use status code
                                switch (statusCode) {
                                    case 400:
                                        errorMessage = "Invalid event data. Please check your information.";
                                        break;
                                    case 404:
                                        errorMessage = "Event not found. It may have been deleted.";
                                        break;
                                    case 500:
                                        errorMessage = "Server error. Please try again later.";
                                        break;
                                    default:
                                        errorMessage = "Update failed (HTTP " + statusCode + ")";
                                        break;
                                }
                            }
                        } else if (error.getMessage() != null) {
                            android.util.Log.e("EditEventActivity", "Error message: " + error.getMessage());
                            debugInfo = "Error: " + error.getMessage();
                            
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
                        
                        android.util.Log.e("EditEventActivity", "Update event error: " + error.toString());
                        android.util.Log.e("EditEventActivity", debugInfo);
                        
                    }
                }
            );

            jsonObjectRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            
            if (requestQueue != null) {
            requestQueue.add(jsonObjectRequest);
            } else {
                updateEventButton.setEnabled(true);
                updateEventButton.setText("Update Event");
            }

        } catch (JSONException e) {
            updateEventButton.setEnabled(true);
            updateEventButton.setText("Update Event");
            android.util.Log.e("EditEventActivity", "Error creating event data: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            updateEventButton.setEnabled(true);
            updateEventButton.setText("Update Event");
            android.util.Log.e("EditEventActivity", "Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showInAppNotification(String title, String message) {
        try {
        // Create a custom in-app notification view
        android.view.LayoutInflater inflater = LayoutInflater.from(this);
        View notificationView = inflater.inflate(R.layout.in_app_notification, null);
            
            if (notificationView == null) {
                // Fallback to simple toast if layout inflation fails
                return;
            }
        
        TextView titleView = notificationView.findViewById(R.id.notification_title);
        TextView messageView = notificationView.findViewById(R.id.notification_message);
        Button closeButton = notificationView.findViewById(R.id.notification_close);
        
            // Check if views exist before using them
            if (titleView != null) {
        titleView.setText(title);
            }
            if (messageView != null) {
        messageView.setText(message);
            }
        
        // Create a custom dialog for the notification
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setView(notificationView);
        builder.setCancelable(true);
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            
            if (closeButton != null) {
                closeButton.setOnClickListener(v -> {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                });
            }
        
        // Auto-dismiss after 5 seconds
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }, 5000);
        
        dialog.show();
        } catch (Exception e) {
            // If anything goes wrong with the custom notification, fall back to toast
            android.util.Log.e("EditEventActivity", "Error showing in-app notification: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Format date/time string for backend LocalDateTime
     * Converts "YYYY-MM-DD HH:MM" to "YYYY-MM-DDTHH:MM:SS"
     */
    private String formatDateTimeForBackend(String dateTime) {
        if (dateTime == null || dateTime.isEmpty()) {
            return null;
        }
        
        // If already in ISO format, return as is
        if (dateTime.contains("T")) {
            // Ensure seconds are included
            if (dateTime.length() == 16) { // "YYYY-MM-DDTHH:MM"
                return dateTime + ":00"; // Add seconds
            }
            return dateTime;
        }
        
        // Convert "YYYY-MM-DD HH:MM" to "YYYY-MM-DDTHH:MM:00"
        if (dateTime.length() == 16) { // "YYYY-MM-DD HH:MM"
            return dateTime.replace(" ", "T") + ":00";
        }
        
        // If format is different, try to parse and convert
        try {
            // Simple conversion: replace space with T and add seconds
            return dateTime.replace(" ", "T") + ":00";
        } catch (Exception e) {
            android.util.Log.e("EditEventActivity", "Error formatting date: " + e.getMessage());
            return dateTime; // Return original if conversion fails
        }
    }
}
