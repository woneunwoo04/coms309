package com.example.occasio.events;
import com.example.occasio.R;
import com.example.occasio.organization.OrganizationDashboardActivity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast; // Keep for now but replace all usage with InAppNotificationHelper
import com.example.occasio.utils.InAppNotificationHelper;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

public class CreateEventActivity extends AppCompatActivity {
    private EditText titleEditText;
    private EditText descriptionEditText;
    private EditText locationEditText;
    private EditText startTimeEditText;
    private EditText endTimeEditText;
    private EditText rewardPointsEditText;
    private EditText registrationFeeEditText;
    private Spinner eventTypeSpinner;
    private Button createEventButton;
    private Button backButton;
    
    private RequestQueue requestQueue;
    private static final String CREATE_EVENT_URL = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/events/organization/";
    
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
        setContentView(R.layout.activity_create_event);

        // Initialize views
        titleEditText = findViewById(R.id.create_event_title_edt);
        descriptionEditText = findViewById(R.id.create_event_description_edt);
        locationEditText = findViewById(R.id.create_event_location_edt);
        startTimeEditText = findViewById(R.id.create_event_start_time_edt);
        endTimeEditText = findViewById(R.id.create_event_end_time_edt);
        rewardPointsEditText = findViewById(R.id.create_event_reward_points_edt);
        registrationFeeEditText = findViewById(R.id.create_event_registration_fee_edt);
        eventTypeSpinner = findViewById(R.id.create_event_type_spinner);
        createEventButton = findViewById(R.id.create_event_create_btn);
        backButton = findViewById(R.id.create_event_back_btn);

        requestQueue = Volley.newRequestQueue(this);

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
        
        if (currentOrgId == null || currentOrgId <= 0) {
            InAppNotificationHelper.showNotification(this, "⚠️ Error", "Organization ID not found. Please login again.");
            finish();
            return;
        }

        // Initialize date/time with current values
        Calendar now = Calendar.getInstance();
        selectedStartYear = now.get(Calendar.YEAR);
        selectedStartMonth = now.get(Calendar.MONTH);
        selectedStartDay = now.get(Calendar.DAY_OF_MONTH);
        selectedStartHour = now.get(Calendar.HOUR_OF_DAY);
        selectedStartMinute = now.get(Calendar.MINUTE);
        
        selectedEndYear = selectedStartYear;
        selectedEndMonth = selectedStartMonth;
        selectedEndDay = selectedStartDay;
        selectedEndHour = selectedStartHour + 2; // Default 2 hours later
        selectedEndMinute = selectedStartMinute;

        // Populate event type spinner
        String[] eventTypes = {"Workshop", "Seminar", "Conference", "Social Event", "Sports", "Cultural", "Academic", "Networking", "Other"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            eventTypes
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        eventTypeSpinner.setAdapter(spinnerAdapter);

        // Set click listeners for date/time pickers
        startTimeEditText.setFocusable(false);
        startTimeEditText.setClickable(true);
        startTimeEditText.setOnClickListener(v -> showStartDateTimePicker());
        
        endTimeEditText.setFocusable(false);
        endTimeEditText.setClickable(true);
        endTimeEditText.setOnClickListener(v -> showEndDateTimePicker());
        
        // Set click listeners
        createEventButton.setOnClickListener(v -> createEvent());
        backButton.setOnClickListener(v -> {
            Intent backIntent = new Intent(CreateEventActivity.this, OrganizationDashboardActivity.class);
            backIntent.putExtra("orgName", currentOrgName);
            backIntent.putExtra("orgId", currentOrgId);
            startActivity(backIntent);
            finish();
        });
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

    private void createEvent() {
        String title = titleEditText.getText().toString().trim();
        String description = descriptionEditText.getText().toString().trim();
        String location = locationEditText.getText().toString().trim();
        String startTime = startTimeEditText.getText().toString().trim();
        String endTime = endTimeEditText.getText().toString().trim();
        String rewardPointsStr = rewardPointsEditText.getText().toString().trim();
        String registrationFeeStr = registrationFeeEditText.getText().toString().trim();
        
        // Get event type from spinner with null check
        String eventType = null;
        Object selectedItem = eventTypeSpinner.getSelectedItem();
        if (selectedItem != null) {
            eventType = selectedItem.toString();
        }
        
        // Parse reward points (default to 0 if empty or invalid)
        int rewardPoints = 0;
        if (!rewardPointsStr.isEmpty()) {
            try {
                rewardPoints = Integer.parseInt(rewardPointsStr);
                if (rewardPoints < 0) {
                    InAppNotificationHelper.showNotification(this, "⚠️ Validation", "Reward points cannot be negative");
                    return;
                }
            } catch (NumberFormatException e) {
                InAppNotificationHelper.showNotification(this, "⚠️ Validation", "Invalid reward points value. Please enter a number.");
                return;
            }
        }
        
        // Parse registration fee (0 or empty = null, meaning free event)
        Double registrationFee = null;
        if (!registrationFeeStr.isEmpty()) {
            try {
                double fee = Double.parseDouble(registrationFeeStr);
                if (fee < 0) {
                    InAppNotificationHelper.showNotification(this, "⚠️ Validation", "Registration fee cannot be negative");
                    return;
                }
                if (fee > 0) {
                    registrationFee = fee;
                }
                // If fee is 0, leave it as null (free event)
            } catch (NumberFormatException e) {
                InAppNotificationHelper.showNotification(this, "⚠️ Validation", "Invalid registration fee. Please enter a valid number.");
                return;
            }
        }

        // Validation
        if (title.isEmpty() || description.isEmpty() || location.isEmpty() || 
            startTime.isEmpty() || endTime.isEmpty()) {
            InAppNotificationHelper.showNotification(this, "⚠️ Validation", "Please fill in all fields");
            return;
        }

        if (title.length() < 3) {
            InAppNotificationHelper.showNotification(this, "⚠️ Validation", "Event title must be at least 3 characters");
            return;
        }

        if (description.length() < 10) {
            InAppNotificationHelper.showNotification(this, "⚠️ Validation", "Event description must be at least 10 characters");
            return;
        }

        // Disable button during request
        createEventButton.setEnabled(false);
        createEventButton.setText("Creating...");
        
        // Create JSON object for event creation
        try {
            JSONObject eventData = new JSONObject();
            eventData.put("eventName", title);
            eventData.put("title", title); // Also set title field
            eventData.put("description", description);
            eventData.put("location", location);
            // Backend expects startTime as LocalDateTime - format: "YYYY-MM-DDTHH:MM:SS"
            // If startTime is in "YYYY-MM-DD HH:MM" format, convert it
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
            
            // Add registration fee (only if > 0, otherwise null = free event)
            if (registrationFee != null && registrationFee > 0) {
                eventData.put("registrationFee", registrationFee);
            }
            
            // Use the actual organization ID from intent
            String createUrl = CREATE_EVENT_URL + currentOrgId;
            
            // Debug logging
            android.util.Log.d("CreateEventActivity", "🔍 Creating event with URL: " + createUrl);
            android.util.Log.d("CreateEventActivity", "📝 Organization ID: " + currentOrgId);
            android.util.Log.d("CreateEventActivity", "📦 Event data: " + eventData.toString(2));
            
            // Make POST request
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                createUrl,
                eventData,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        createEventButton.setEnabled(true);
                        createEventButton.setText("Create Event");
                        
                        android.util.Log.d("CreateEventActivity", "✅ SUCCESS! Response: " + response.toString());
                        
                        try {
                            // Backend returns the created event directly
                            String eventName = response.getString("eventName");
                            Long eventId = response.getLong("id");
                            
                            android.util.Log.d("CreateEventActivity", "Event created: " + eventName + " (ID: " + eventId + ")");
                            InAppNotificationHelper.showNotification(CreateEventActivity.this, "✅ Success", "Event created successfully!");
                            
                            // Show in-app notification for event creation
                            showInAppNotification("🎉 Event Created!", 
                                eventName + " has been successfully created and is now available for users to attend!");
                            
                            clearForm();
                            
                            // Navigate back to dashboard
                            Intent intent = new Intent(CreateEventActivity.this, OrganizationDashboardActivity.class);
                            intent.putExtra("orgName", currentOrgName);
                            intent.putExtra("orgId", currentOrgId);
                            startActivity(intent);
                            finish();
                        } catch (JSONException e) {
                            android.util.Log.e("CreateEventActivity", "Error parsing response: " + e.getMessage());
                            InAppNotificationHelper.showNotification(CreateEventActivity.this, "✅ Success", "Event created successfully! (Response parsing error)");
                            clearForm();
                            
                            // Navigate back to dashboard
                            Intent intent = new Intent(CreateEventActivity.this, OrganizationDashboardActivity.class);
                            intent.putExtra("orgName", currentOrgName);
                            intent.putExtra("orgId", currentOrgId);
                            startActivity(intent);
                            finish();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        createEventButton.setEnabled(true);
                        createEventButton.setText("Create Event");
                        
                        String errorMessage = "Failed to create event";
                        String debugInfo = "";
                        
                        if (error.networkResponse != null) {
                            int statusCode = error.networkResponse.statusCode;
                            debugInfo = "Status Code: " + statusCode;
                            
                            // Try to parse error response body
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                android.util.Log.e("CreateEventActivity", "Error response body: " + responseBody);
                                debugInfo += "\nResponse: " + responseBody;
                                
                                if (!responseBody.isEmpty()) {
                                    JSONObject errorJson = new JSONObject(responseBody);
                                    errorMessage = errorJson.optString("message", errorJson.optString("error", errorMessage));
                                }
                            } catch (Exception e) {
                                android.util.Log.e("CreateEventActivity", "Error parsing error response: " + e.getMessage());
                                // If parsing fails, use status code
                                switch (statusCode) {
                                    case 400:
                                        errorMessage = "Invalid event data. Please check your information.";
                                        break;
                                    case 404:
                                        errorMessage = "Organization not found (ID: " + currentOrgId + "). Please login again.";
                                        break;
                                    case 500:
                                        errorMessage = "Server error. Please try again later.";
                                        break;
                                    default:
                                        errorMessage = "Create failed (HTTP " + statusCode + ")";
                                        break;
                                }
                            }
                        } else if (error.getMessage() != null) {
                            android.util.Log.e("CreateEventActivity", "Error message: " + error.getMessage());
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
                        
                        android.util.Log.e("CreateEventActivity", "❌ Create event error: " + error.toString());
                        android.util.Log.e("CreateEventActivity", "📋 Debug info: " + debugInfo);
                        android.util.Log.e("CreateEventActivity", "🔗 URL was: " + CREATE_EVENT_URL + currentOrgId);
                        android.util.Log.e("CreateEventActivity", "🆔 Organization ID: " + currentOrgId);
                        
                        InAppNotificationHelper.showNotification(CreateEventActivity.this, "❌ Error", errorMessage);
                    }
                }
            );

            jsonObjectRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            requestQueue.add(jsonObjectRequest);

        } catch (JSONException e) {
            InAppNotificationHelper.showNotification(this, "❌ Error", "Error creating event data");
        }
    }

    private AlertDialog notificationDialog; // Store reference to prevent leaks
    
    private void showInAppNotification(String title, String message) {
        // Dismiss any existing dialog first
        if (notificationDialog != null && notificationDialog.isShowing()) {
            notificationDialog.dismiss();
        }
        
        // Create a custom in-app notification view
        LayoutInflater inflater = LayoutInflater.from(this);
        View notificationView = inflater.inflate(R.layout.in_app_notification, null);
        
        TextView titleView = notificationView.findViewById(R.id.notification_title);
        TextView messageView = notificationView.findViewById(R.id.notification_message);
        Button closeButton = notificationView.findViewById(R.id.notification_close);
        
        titleView.setText(title);
        messageView.setText(message);
        
        // Create a custom dialog for the notification
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(notificationView);
        builder.setCancelable(true);
        
        notificationDialog = builder.create();
        if (notificationDialog.getWindow() != null) {
            notificationDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        closeButton.setOnClickListener(v -> {
            if (notificationDialog != null && notificationDialog.isShowing()) {
                notificationDialog.dismiss();
            }
        });
        
        // Auto-dismiss after 5 seconds
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (notificationDialog != null && notificationDialog.isShowing() && !isFinishing() && !isDestroyed()) {
                notificationDialog.dismiss();
            }
        }, 5000);
        
        // Only show if activity is still valid
        if (!isFinishing() && !isDestroyed()) {
            notificationDialog.show();
        }
    }

    private void clearForm() {
        titleEditText.setText("");
        descriptionEditText.setText("");
        locationEditText.setText("");
        startTimeEditText.setText("");
        endTimeEditText.setText("");
        rewardPointsEditText.setText("");
        eventTypeSpinner.setSelection(0);
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
            android.util.Log.e("CreateEventActivity", "Error formatting date: " + e.getMessage());
            return dateTime; // Return original if conversion fails
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up dialog to prevent window leak
        if (notificationDialog != null && notificationDialog.isShowing()) {
            notificationDialog.dismiss();
        }
        notificationDialog = null;
    }
}
