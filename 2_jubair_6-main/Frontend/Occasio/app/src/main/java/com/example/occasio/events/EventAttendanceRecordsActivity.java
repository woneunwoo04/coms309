package com.example.occasio.events;

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
import com.example.occasio.R;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class EventAttendanceRecordsActivity extends AppCompatActivity {
    private RecyclerView recordsRecyclerView;
    private Button backButton;
    private Button refreshButton;
    private TextView eventNameTextView;
    private TextView statusTextView;
    private RequestQueue requestQueue;
    private Long currentOrgId;
    private Long currentEventId;
    private String currentEventName;
    private List<AttendanceActivity.AttendanceRecord> attendanceRecords;
    private AttendanceAdapter attendanceAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_records);

        Intent intent = getIntent();
        if (intent != null) {
            currentOrgId = intent.getLongExtra("orgId", -1L);
            currentEventId = intent.getLongExtra("eventId", -1L);
            currentEventName = intent.getStringExtra("eventName");
        }

        if (currentOrgId == null || currentOrgId <= 0 || currentEventId == null || currentEventId <= 0) {
            finish();
            return;
        }

        initializeViews();
        setupRecyclerView();
        loadAttendanceRecords();
    }

    private void initializeViews() {
        recordsRecyclerView = findViewById(R.id.attendance_records_recycler_view);
        backButton = findViewById(R.id.attendance_records_back_btn);
        refreshButton = findViewById(R.id.attendance_records_refresh_btn);
        eventNameTextView = findViewById(R.id.attendance_records_event_name_tv);
        statusTextView = findViewById(R.id.attendance_records_status_tv);
        
        requestQueue = Volley.newRequestQueue(this);
        attendanceRecords = new ArrayList<>();

        if (currentEventName != null) {
            eventNameTextView.setText(currentEventName);
        }

        backButton.setOnClickListener(v -> {
            Intent backIntent = new Intent(EventAttendanceRecordsActivity.this, ManageEventsActivity.class);
            backIntent.putExtra("orgId", currentOrgId);
            startActivity(backIntent);
            finish();
        });

        refreshButton.setOnClickListener(v -> loadAttendanceRecords());
    }

    private void setupRecyclerView() {
        attendanceAdapter = new AttendanceAdapter(attendanceRecords);
        recordsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        recordsRecyclerView.setAdapter(attendanceAdapter);
    }

    private void loadAttendanceRecords() {
        // Validate inputs
        if (currentEventId == null || currentEventId <= 0) {
            finish();
            return;
        }
        
        if (currentOrgId == null || currentOrgId <= 0) {
            finish();
            return;
        }
        
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/event-attendance/events/" + currentEventId + "/records?orgId=" + currentOrgId;
        android.util.Log.d("EventAttendanceRecordsActivity", "🔍 Loading attendance records from: " + url);
        android.util.Log.d("EventAttendanceRecordsActivity", "📝 Event ID: " + currentEventId + ", Organization ID: " + currentOrgId);
        
        statusTextView.setText("Loading attendance records...");
        
        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    attendanceRecords.clear();
                    try {
                        // Handle null or empty response
                        if (response == null || response.length() == 0) {
                            android.util.Log.d("EventAttendanceRecordsActivity", "No attendance records found");
                            attendanceAdapter.notifyDataSetChanged();
                            statusTextView.setText("No attendance records found for this event");
                            return;
                        }
                        
                        for (int i = 0; i < response.length(); i++) {
                            try {
                                JSONObject recordObj = response.getJSONObject(i);
                                
                                // Validate required fields
                                if (!recordObj.has("id")) {
                                    android.util.Log.w("EventAttendanceRecordsActivity", 
                                        "Record missing ID at index " + i);
                                    continue;
                                }
                                
                                // Parse user info
                                String userName = "Unknown User";
                                if (recordObj.has("user") && !recordObj.isNull("user")) {
                                    JSONObject userObj = recordObj.getJSONObject("user");
                                    userName = userObj.optString("username", 
                                        userObj.optString("email", "Unknown User"));
                                }
                                
                                // Parse check-in time
                                String checkInTime = recordObj.optString("checkInTime", "N/A");
                                if (checkInTime != null && !checkInTime.equals("N/A") && checkInTime.contains("T")) {
                                    try {
                                        checkInTime = checkInTime.replace("T", " ").substring(0, 16);
                                    } catch (Exception e) {
                                        android.util.Log.w("EventAttendanceRecordsActivity", 
                                            "Error parsing check-in time", e);
                                    }
                                }
                                
                                // Determine check-in method
                                String checkInMethod = "Code";
                                if (recordObj.has("latitude") && !recordObj.isNull("latitude")) {
                                    checkInMethod = "GPS Location";
                                } else if (recordObj.has("wifiSSID") && !recordObj.isNull("wifiSSID")) {
                                    String wifiSSID = recordObj.optString("wifiSSID", "");
                                    checkInMethod = wifiSSID.isEmpty() ? "WiFi" : "WiFi: " + wifiSSID;
                                }
                                
                                // Get status
                                String status = recordObj.optString("status", "VERIFIED");
                                
                                // Get points (from event if available)
                                int points = 0;
                                if (recordObj.has("event") && !recordObj.isNull("event")) {
                                    JSONObject eventObj = recordObj.getJSONObject("event");
                                    points = eventObj.optInt("rewardPoints", 0);
                                }
                                
                                Long id = recordObj.getLong("id");
                                String eventTitle = (currentEventName != null) ? 
                                    userName + " - " + currentEventName : userName;
                                
                                AttendanceActivity.AttendanceRecord record = new AttendanceActivity.AttendanceRecord(
                                    id.intValue(),
                                    eventTitle,
                                    checkInTime,
                                    checkInMethod,
                                    status,
                                    points
                                );
                                attendanceRecords.add(record);
                            } catch (JSONException e) {
                                android.util.Log.e("EventAttendanceRecordsActivity", 
                                    "Error parsing attendance record at index " + i, e);
                                // Continue with next record instead of failing completely
                            }
                        }
                        
                        attendanceAdapter.notifyDataSetChanged();
                        if (attendanceRecords.isEmpty()) {
                            statusTextView.setText("No attendance records found for this event");
                        } else {
                            statusTextView.setText("Found " + attendanceRecords.size() + " attendance record(s)");
                        }
                    } catch (Exception e) {
                        android.util.Log.e("EventAttendanceRecordsActivity", "Unexpected error", e);
                        statusTextView.setText("Unexpected error occurred");
                        attendanceRecords.clear();
                        attendanceAdapter.notifyDataSetChanged();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    android.util.Log.e("EventAttendanceRecordsActivity", 
                        "Error loading attendance records", error);
                    String errorMessage = "Failed to load attendance records";
                    
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            JSONObject errorJson = new JSONObject(responseBody);
                            errorMessage = errorJson.optString("message", errorMessage);
                        } catch (Exception e) {
                            // Use status code based error messages
                            if (statusCode == 403) {
                                errorMessage = "You don't have permission to view these records";
                            } else if (statusCode == 404) {
                                errorMessage = "Event not found";
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
                    
                    statusTextView.setText(errorMessage);
                    attendanceRecords.clear();
                    attendanceAdapter.notifyDataSetChanged();
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
}

