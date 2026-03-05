package com.example.occasio.events;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.occasio.R;
import java.util.ArrayList;
import java.util.List;

public class AttendanceActivity extends AppCompatActivity {
    private RecyclerView attendanceRecyclerView;
    private Button backButton;
    private Button refreshButton;
    private Button enableLocationButton;
    private TextView locationStatusTextView;
    
    private com.android.volley.RequestQueue requestQueue;
    
    private String currentUsername;
    private List<AttendanceRecord> attendanceList;
    private AttendanceAdapter attendanceAdapter;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        attendanceRecyclerView = findViewById(R.id.attendance_recycler_view);
        backButton = findViewById(R.id.attendance_back_btn);
        refreshButton = findViewById(R.id.attendance_refresh_btn);
        enableLocationButton = findViewById(R.id.attendance_enable_location_btn);
        locationStatusTextView = findViewById(R.id.attendance_location_status_tv);

        attendanceList = new ArrayList<>();
        requestQueue = com.android.volley.toolbox.Volley.newRequestQueue(this);

        // Get username from intent or SharedPreferences
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("username")) {
            currentUsername = intent.getStringExtra("username");
        }
        
        if (currentUsername == null || currentUsername.isEmpty()) {
            android.content.SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            currentUsername = prefs.getString("username", "");
        }
        
        if (currentUsername == null || currentUsername.isEmpty()) {
            finish();
            return;
        }

        // Setup RecyclerView
        attendanceAdapter = new AttendanceAdapter(attendanceList);
        attendanceRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        attendanceRecyclerView.setAdapter(attendanceAdapter);

        // Set click listeners
        backButton.setOnClickListener(v -> {
            Intent backIntent = new Intent(AttendanceActivity.this, UserEventsActivity.class);
            backIntent.putExtra("username", currentUsername);
            startActivity(backIntent);
            finish();
        });

        refreshButton.setOnClickListener(v -> loadAttendanceRecords());
        
        enableLocationButton.setOnClickListener(v -> requestLocationPermission());

        // Check location permission status
        updateLocationStatus();
        
        // Load attendance records on startup
        loadAttendanceRecords();
    }

    private void requestLocationPermission() {
        // Check both fine and coarse location permissions
        boolean fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
        
        if (!fineLocationGranted || !coarseLocationGranted) {
            // Request both permissions
            ActivityCompat.requestPermissions(this, 
                new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                }, 
                LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            updateLocationStatus();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            
            if (allPermissionsGranted) {
            } else {
            }
            updateLocationStatus();
        }
    }

    private void updateLocationStatus() {
        boolean fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
        
        if (fineLocationGranted && coarseLocationGranted) {
            locationStatusTextView.setText("✅ Location Enabled");
            locationStatusTextView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            enableLocationButton.setVisibility(View.GONE);
        } else {
            locationStatusTextView.setText("❌ Location Disabled - Required for check-in");
            locationStatusTextView.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            enableLocationButton.setVisibility(View.VISIBLE);
        }
    }

    private void loadAttendanceRecords() {
        // Validate username
        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            finish();
            return;
        }
        
        // URL encode username
        String encodedUsername = "";
        try {
            encodedUsername = java.net.URLEncoder.encode(currentUsername.trim(), "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            android.util.Log.e("AttendanceActivity", "Error encoding username", e);
            encodedUsername = currentUsername.trim();
        }
        
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/event-attendance/my-records?username=" + encodedUsername;
        android.util.Log.d("AttendanceActivity", "🔍 Loading attendance records from: " + url);
        
        com.android.volley.toolbox.JsonArrayRequest request = new com.android.volley.toolbox.JsonArrayRequest(
            com.android.volley.Request.Method.GET,
            url,
            null,
            new com.android.volley.Response.Listener<org.json.JSONArray>() {
                @Override
                public void onResponse(org.json.JSONArray response) {
                    attendanceList.clear();
                    try {
                        // Handle null or empty response
                        if (response == null || response.length() == 0) {
                            android.util.Log.d("AttendanceActivity", "No attendance records found");
                            attendanceAdapter.notifyDataSetChanged();
                            return;
                        }
                        
                        for (int i = 0; i < response.length(); i++) {
                            try {
                                org.json.JSONObject recordObj = response.getJSONObject(i);
                                
                                // Validate required fields
                                if (!recordObj.has("id")) {
                                    android.util.Log.w("AttendanceActivity", "Record missing ID at index " + i);
                                    continue;
                                }
                                
                                // Parse event name
                                String eventName = "Unknown Event";
                                if (recordObj.has("event") && !recordObj.isNull("event")) {
                                    org.json.JSONObject eventObj = recordObj.getJSONObject("event");
                                    eventName = eventObj.optString("eventName", 
                                        eventObj.optString("title", "Unknown Event"));
                                }
                                
                                // Parse check-in time
                                String checkInTime = recordObj.optString("checkInTime", "N/A");
                                if (checkInTime != null && !checkInTime.equals("N/A") && checkInTime.contains("T")) {
                                    try {
                                        checkInTime = checkInTime.replace("T", " ").substring(0, 16);
                                    } catch (Exception e) {
                                        android.util.Log.w("AttendanceActivity", "Error parsing check-in time", e);
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
                                
                                // Get status - only show VERIFIED records (successfully attended)
                                String status = recordObj.optString("status", "VERIFIED");
                                
                                // Filter: Only add VERIFIED records (successfully attended events)
                                if (!"VERIFIED".equals(status)) {
                                    android.util.Log.d("AttendanceActivity", "Skipping non-VERIFIED record: " + status);
                                    continue; // Skip PENDING or REJECTED records
                                }
                                
                                // Get points (from event if available, or from reward_points_awarded)
                                int points = 0;
                                if (recordObj.has("rewardPointsAwarded") && !recordObj.isNull("rewardPointsAwarded")) {
                                    points = recordObj.optInt("rewardPointsAwarded", 0);
                                } else if (recordObj.has("event") && !recordObj.isNull("event")) {
                                    org.json.JSONObject eventObj = recordObj.getJSONObject("event");
                                    points = eventObj.optInt("rewardPoints", 0);
                                }
                                
                                Long id = recordObj.getLong("id");
                                AttendanceRecord record = new AttendanceRecord(
                                    id.intValue(),
                                    eventName,
                                    checkInTime,
                                    checkInMethod,
                                    status,
                                    points
                                );
                                attendanceList.add(record);
                                android.util.Log.d("AttendanceActivity", "✅ Added attendance record: " + eventName);
                            } catch (org.json.JSONException e) {
                                android.util.Log.e("AttendanceActivity", 
                                    "Error parsing attendance record at index " + i, e);
                                // Continue with next record instead of failing completely
                            }
                        }
                        
                        attendanceAdapter.notifyDataSetChanged();
                        if (attendanceList.isEmpty()) {
                            android.util.Log.d("AttendanceActivity", "No attendance records found");
                        } else {
                            android.util.Log.d("AttendanceActivity", "Loaded " + attendanceList.size() + " attendance record(s)");
                        }
                    } catch (Exception e) {
                        android.util.Log.e("AttendanceActivity", "Unexpected error", e);
                        attendanceList.clear();
                        attendanceAdapter.notifyDataSetChanged();
                    }
                }
            },
            new com.android.volley.Response.ErrorListener() {
                @Override
                public void onErrorResponse(com.android.volley.VolleyError error) {
                    android.util.Log.e("AttendanceActivity", "Error loading attendance records", error);
                    String errorMessage = "Failed to load attendance records";
                    
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            org.json.JSONObject errorJson = new org.json.JSONObject(responseBody);
                            errorMessage = errorJson.optString("message", errorMessage);
                        } catch (Exception e) {
                            // Use status code based error messages
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
                    
                    attendanceList.clear();
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

    /**
     * Example method showing proper permission checking before using location services
     * This method demonstrates how to safely check permissions before accessing location
     */
    private void performLocationBasedCheckIn() {
        try {
            // Check permissions before using location services
            boolean fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                    == PackageManager.PERMISSION_GRANTED;
            boolean coarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) 
                    == PackageManager.PERMISSION_GRANTED;
            
            if (!fineLocationGranted || !coarseLocationGranted) {
                requestLocationPermission();
                return;
            }
            
            // TODO: Implement actual location-based check-in here
            // This would involve:
            // 1. Getting current location using LocationManager or FusedLocationProviderClient
            // 2. Checking if user is within event location radius
            // 3. Sending check-in request to server with location data
            
            
        } catch (SecurityException e) {
            // Handle SecurityException if permissions are revoked at runtime
            updateLocationStatus();
        } catch (Exception e) {
            // Handle any other location-related errors
        }
    }

    // AttendanceRecord data class
    public static class AttendanceRecord {
        private int id;
        private String eventTitle;
        private String checkInTime;
        private String checkInMethod;
        private String status;
        private int pointsEarned;

        public AttendanceRecord(int id, String eventTitle, String checkInTime, 
                              String checkInMethod, String status, int pointsEarned) {
            this.id = id;
            this.eventTitle = eventTitle;
            this.checkInTime = checkInTime;
            this.checkInMethod = checkInMethod;
            this.status = status;
            this.pointsEarned = pointsEarned;
        }

        // Getters
        public int getId() { return id; }
        public String getEventTitle() { return eventTitle; }
        public String getCheckInTime() { return checkInTime; }
        public String getCheckInMethod() { return checkInMethod; }
        public String getStatus() { return status; }
        public int getPointsEarned() { return pointsEarned; }
    }
}
