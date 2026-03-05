package com.example.occasio.events;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import com.example.occasio.service.EventAttendanceWebSocketService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EventCheckInActivity extends AppCompatActivity {
    private TextView eventNameTextView;
    private TextView[] codeDigits;
    private Button[] numberButtons;
    private Button deleteButton;
    private Button submitButton;
    private Button backButton;
    private TextView locationStatusTextView;
    private TextView wifiStatusTextView;
    private TextView validationStatusTextView;
    private String currentCode = "";
    private RequestQueue requestQueue;
    private static final String BASE_URL = com.example.occasio.utils.ServerConfig.BASE_URL;
    private String currentUsername;
    private Long eventId;
    private String eventName;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private FusedLocationProviderClient fusedLocationClient;
    private EventAttendanceWebSocketService attendanceWebSocketService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_check_in);

        initializeViews();
        setupCodeInput();
        // Don't request permissions immediately - only check status, don't request
        updateLocationStatus();

        Intent intent = getIntent();
        if (intent != null) {
            currentUsername = intent.getStringExtra("username");
            eventId = intent.getLongExtra("eventId", -1L);
            eventName = intent.getStringExtra("eventName");
            if (eventName != null && eventNameTextView != null) {
                eventNameTextView.setText(eventName);
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Initialize code as empty - user must enter it manually
        currentCode = "";
        updateCodeDisplay();
        
        // Setup WebSocket to receive attendance codes (for notifications only, not auto-fill)
        try {
            setupAttendanceWebSocket();
        } catch (Exception e) {
            android.util.Log.e("EventCheckInActivity", "Error setting up WebSocket", e);
            e.printStackTrace();
            // Continue without WebSocket
        }
        
        // Check for active session on load (for notification only, not auto-fill)
        try {
            checkActiveSession();
        } catch (Exception e) {
            android.util.Log.e("EventCheckInActivity", "Error checking active session", e);
            e.printStackTrace();
            // Continue without checking active session
        }
        
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        if (submitButton != null) {
            submitButton.setOnClickListener(v -> {
                try {
                    performCheckIn();
                } catch (Exception e) {
                    android.util.Log.e("EventCheckInActivity", "Error in performCheckIn", e);
                    e.printStackTrace();
                }
            });
        }
    }
    
    private void checkActiveSession() {
        if (eventId == null || eventId <= 0) {
            return;
        }
        
        String url = BASE_URL + "/api/event-attendance/events/" + eventId + "/active-session";
        
        com.android.volley.toolbox.JsonObjectRequest request = new com.android.volley.toolbox.JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        if (response.has("code") && response.getBoolean("active")) {
                            String code = response.getString("code");
                            if (code != null && code.length() == 4) {
                                // Don't auto-fill the code - user must enter it manually
                                // Just notify that a session is active
                                validationStatusTextView.setText("✅ Active session found! Please enter the attendance code.");
                                validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_green_dark));
                                validationStatusTextView.setVisibility(View.VISIBLE);
                                // Keep currentCode empty - user must enter it
                            }
                        }
                    } catch (JSONException e) {
                        android.util.Log.e("EventCheckInActivity", "Error parsing active session response", e);
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    // No active session is fine - user can still enter code manually
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        validationStatusTextView.setText("No active session. Waiting for attendance code...");
                        validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_orange_dark));
                        validationStatusTextView.setVisibility(View.VISIBLE);
                    }
                }
            }
        );
        
        request.setRetryPolicy(new DefaultRetryPolicy(5000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }
    
    private void setupAttendanceWebSocket() {
        if (eventId == null || eventId <= 0) {
            android.util.Log.e("EventCheckInActivity", "Cannot setup WebSocket: eventId is invalid");
            return;
        }
        
        attendanceWebSocketService = EventAttendanceWebSocketService.getInstance();
        
        attendanceWebSocketService.subscribeToEvent(eventId, new EventAttendanceWebSocketService.AttendanceListener() {
            @Override
            public void onAttendanceCodeReceived(String code, Long sessionId, String eventName) {
                runOnUiThread(() -> {
                    // Notify user that code is available, but don't auto-fill
                    // User must enter the code manually
                    if (code.length() == 4) {
                        validationStatusTextView.setText("✅ Attendance session started! Please enter the code: " + code);
                        validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_green_dark));
                        validationStatusTextView.setVisibility(View.VISIBLE);
                        // Don't auto-fill - user must enter it manually
                    }
                });
            }
            
            @Override
            public void onSessionEnded(String message) {
                runOnUiThread(() -> {
                    validationStatusTextView.setText("⏰ Session ended");
                    validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                });
            }
            
            @Override
            public void onConnect(Long eventId) {
                android.util.Log.d("EventCheckInActivity", "Connected to attendance WebSocket for event " + eventId);
            }
            
            @Override
            public void onDisconnect(Long eventId) {
                android.util.Log.d("EventCheckInActivity", "Disconnected from attendance WebSocket for event " + eventId);
            }
            
            @Override
            public void onError(Long eventId, Exception error) {
                android.util.Log.e("EventCheckInActivity", "WebSocket error for event " + eventId + ": " + error.getMessage());
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up WebSocket connection
        if (attendanceWebSocketService != null && eventId != null) {
            attendanceWebSocketService.unsubscribeFromEvent(eventId);
        }
    }

    private void initializeViews() {
        try {
            eventNameTextView = findViewById(R.id.check_in_course_name_tv);
            codeDigits = new TextView[]{
                findViewById(R.id.check_in_digit_1),
                findViewById(R.id.check_in_digit_2),
                findViewById(R.id.check_in_digit_3),
                findViewById(R.id.check_in_digit_4)
            };
            deleteButton = findViewById(R.id.check_in_delete_btn);
            submitButton = findViewById(R.id.check_in_submit_btn);
            backButton = findViewById(R.id.check_in_back_btn);
            locationStatusTextView = findViewById(R.id.check_in_location_status_tv);
            wifiStatusTextView = findViewById(R.id.check_in_wifi_status_tv);
            validationStatusTextView = findViewById(R.id.check_in_validation_status_tv);

            // Validate all views are found
            if (eventNameTextView == null || codeDigits == null || deleteButton == null || 
                submitButton == null || backButton == null || locationStatusTextView == null ||
                wifiStatusTextView == null || validationStatusTextView == null) {
                android.util.Log.e("EventCheckInActivity", "One or more views are null");
                finish();
                return;
            }
            
            // Check codeDigits array
            for (int i = 0; i < codeDigits.length; i++) {
                if (codeDigits[i] == null) {
                    android.util.Log.e("EventCheckInActivity", "Code digit " + i + " is null");
                    finish();
                    return;
                }
            }

            requestQueue = Volley.newRequestQueue(this);
        } catch (Exception e) {
            android.util.Log.e("EventCheckInActivity", "Error in initializeViews", e);
            e.printStackTrace();
            finish();
        }
    }

    private void setupCodeInput() {
        try {
            numberButtons = new Button[]{
                findViewById(R.id.check_in_btn_0),
                findViewById(R.id.check_in_btn_1),
                findViewById(R.id.check_in_btn_2),
                findViewById(R.id.check_in_btn_3),
                findViewById(R.id.check_in_btn_4),
                findViewById(R.id.check_in_btn_5),
                findViewById(R.id.check_in_btn_6),
                findViewById(R.id.check_in_btn_7),
                findViewById(R.id.check_in_btn_8),
                findViewById(R.id.check_in_btn_9)
            };

            // Validate all buttons are found
            for (int i = 0; i < numberButtons.length; i++) {
                if (numberButtons[i] == null) {
                    android.util.Log.e("EventCheckInActivity", "Number button " + i + " is null");
                    finish();
                    return;
                }
            }

            for (int i = 0; i < numberButtons.length; i++) {
                final int digit = i;
                numberButtons[i].setOnClickListener(v -> addDigit(String.valueOf(digit)));
            }

            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> removeDigit());
            } else {
                android.util.Log.e("EventCheckInActivity", "Delete button is null");
            }
        } catch (Exception e) {
            android.util.Log.e("EventCheckInActivity", "Error in setupCodeInput", e);
            e.printStackTrace();
        }
    }

    private void addDigit(String digit) {
        try {
            if (currentCode == null) {
                currentCode = "";
            }
            if (currentCode.length() < 4) {
                currentCode += digit;
                updateCodeDisplay();
                if (currentCode.length() == 4 && submitButton != null) {
                    submitButton.setEnabled(true);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("EventCheckInActivity", "Error in addDigit", e);
            e.printStackTrace();
        }
    }

    private void removeDigit() {
        try {
            if (currentCode == null) {
                currentCode = "";
            }
            if (currentCode.length() > 0) {
                currentCode = currentCode.substring(0, currentCode.length() - 1);
                updateCodeDisplay();
                if (submitButton != null) {
                    submitButton.setEnabled(false);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("EventCheckInActivity", "Error in removeDigit", e);
            e.printStackTrace();
        }
    }

    private void updateCodeDisplay() {
        try {
            if (codeDigits == null || currentCode == null) {
                return;
            }
            for (int i = 0; i < 4 && i < codeDigits.length; i++) {
                if (codeDigits[i] != null) {
                    if (i < currentCode.length()) {
                        codeDigits[i].setText(String.valueOf(currentCode.charAt(i)));
                    } else {
                        codeDigits[i].setText("_");
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("EventCheckInActivity", "Error in updateCodeDisplay", e);
            e.printStackTrace();
        }
    }

    private void updateLocationStatus() {
        boolean fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (fineLocationGranted && coarseLocationGranted) {
            if (locationStatusTextView != null) {
                locationStatusTextView.setText("✅ Location Enabled");
                locationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_green_dark));
            }
            updateWifiStatus();
        } else {
            if (locationStatusTextView != null) {
                locationStatusTextView.setText("⚠️ Location Optional (code-based check-in works without it)");
                locationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_orange_dark));
            }
            // Don't request permissions automatically - user can still check in with code
        }
    }

    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            updateLocationStatus();
        }
    }

    private void updateWifiStatus() {
        try {
            if (wifiStatusTextView == null) {
                return; // Can't update if view is null
            }
            
            // Check if we have WiFi state permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                wifiStatusTextView.setText("📶 WiFi: Permission needed");
                wifiStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                return;
            }
            
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifiManager == null) {
                wifiStatusTextView.setText("📶 WiFi: Unavailable");
                wifiStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                return;
            }
            
            // Check if WiFi is enabled (requires ACCESS_WIFI_STATE permission)
            boolean wifiEnabled = false;
            try {
                wifiEnabled = wifiManager.isWifiEnabled();
            } catch (SecurityException e) {
                android.util.Log.e("EventCheckInActivity", "SecurityException checking WiFi state", e);
                wifiStatusTextView.setText("📶 WiFi: Permission denied");
                wifiStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                return;
            }
            
            if (!wifiEnabled) {
                wifiStatusTextView.setText("📶 WiFi: Disabled");
                wifiStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                return;
            }
            
            // Get SSID (requires ACCESS_FINE_LOCATION permission on Android 10+)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                wifiStatusTextView.setText("📶 WiFi: Location permission needed for SSID");
                wifiStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                return;
            }
            
            try {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ssid = wifiInfo.getSSID();
                if (ssid != null && !ssid.equals("<unknown ssid>") && !ssid.equals("0x")) {
                    wifiStatusTextView.setText("📶 WiFi: " + ssid.replace("\"", ""));
                    wifiStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_green_dark));
                } else {
                    wifiStatusTextView.setText("📶 WiFi: Not connected");
                    wifiStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                }
            } catch (SecurityException e) {
                android.util.Log.e("EventCheckInActivity", "SecurityException getting WiFi SSID", e);
                wifiStatusTextView.setText("📶 WiFi: Connected (SSID unavailable)");
                wifiStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_green_dark));
            }
        } catch (Exception e) {
            android.util.Log.e("EventCheckInActivity", "Error updating WiFi status", e);
            e.printStackTrace();
            wifiStatusTextView.setText("📶 WiFi: Error");
            wifiStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
        }
    }

    private void performCheckIn() {
        android.util.Log.d("EventCheckInActivity", "performCheckIn() called");
        try {
            // Validate views are initialized
            android.util.Log.d("EventCheckInActivity", "Checking views...");
            if (validationStatusTextView == null || submitButton == null) {
                android.util.Log.e("EventCheckInActivity", "Views not initialized - validationStatusTextView: " + (validationStatusTextView == null) + ", submitButton: " + (submitButton == null));
                return;
            }
            android.util.Log.d("EventCheckInActivity", "Views are initialized");
            
            // Validate code
            android.util.Log.d("EventCheckInActivity", "Validating code - currentCode: " + currentCode);
            if (currentCode == null || currentCode.length() != 4) {
                android.util.Log.w("EventCheckInActivity", "Code validation failed - currentCode: " + currentCode);
                if (validationStatusTextView != null) {
                    validationStatusTextView.setText("❌ Please enter a 4-digit code");
                    validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                }
                return;
            }
            
            // Validate code contains only digits
            if (!currentCode.matches("\\d{4}")) {
                android.util.Log.w("EventCheckInActivity", "Code format validation failed - currentCode: " + currentCode);
                if (validationStatusTextView != null) {
                    validationStatusTextView.setText("❌ Code must contain only digits");
                    validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                }
                return;
            }

            // Validate username
            android.util.Log.d("EventCheckInActivity", "Validating username - currentUsername: " + currentUsername);
            if (currentUsername == null || currentUsername.trim().isEmpty()) {
                android.util.Log.e("EventCheckInActivity", "Username validation failed - currentUsername: " + currentUsername);
                finish();
                return;
            }
            
            // Validate event ID
            android.util.Log.d("EventCheckInActivity", "Validating event ID - eventId: " + eventId);
            if (eventId == null || eventId <= 0) {
                android.util.Log.e("EventCheckInActivity", "Event ID validation failed - eventId: " + eventId);
                return;
            }

            // Check location permission - but don't require it if geofence is optional
            android.util.Log.d("EventCheckInActivity", "Checking location permission...");
            boolean hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                    == PackageManager.PERMISSION_GRANTED;
            android.util.Log.d("EventCheckInActivity", "Location permission granted: " + hasLocationPermission);
            
            // Don't request permission here - just proceed without it if not available
            // Location is optional for code-based check-in

            // Try to get location, but don't fail if it's not available
            android.util.Log.d("EventCheckInActivity", "Getting location...");
            if (!hasLocationPermission) {
                // No permission - proceed without location
                android.util.Log.d("EventCheckInActivity", "No location permission, proceeding without location");
                proceedWithCheckIn(null, null);
                return;
            }
            
            try {
                if (fusedLocationClient == null) {
                    android.util.Log.w("EventCheckInActivity", "FusedLocationClient is null, proceeding without location");
                    proceedWithCheckIn(null, null);
                    return;
                }
                
                fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    try {
                        // Handle null location - proceed without location if geofence is optional
                        if (location == null) {
                            android.util.Log.w("EventCheckInActivity", "Location not available, proceeding without location");
                            proceedWithCheckIn(null, null);
                            return;
                        }
                        
                        // Validate location coordinates
                        if (location.getLatitude() == 0.0 && location.getLongitude() == 0.0) {
                            android.util.Log.w("EventCheckInActivity", "Invalid location coordinates, proceeding without location");
                            proceedWithCheckIn(null, null);
                            return;
                        }

                        // Get WiFi SSID
                        String ssid = getCurrentWifiSSID();
                        
                        // Proceed with check-in using location
                        proceedWithCheckIn(location, ssid);
                    } catch (Exception e) {
                        android.util.Log.e("EventCheckInActivity", "Error in location success listener", e);
                        e.printStackTrace();
                        // Proceed without location if there's an error
                        proceedWithCheckIn(null, null);
                    }
                })
                .addOnFailureListener(this, e -> {
                    android.util.Log.e("EventCheckInActivity", "Error getting location", e);
                    e.printStackTrace();
                    // Proceed without location if location retrieval fails
                    proceedWithCheckIn(null, null);
                });
            } catch (SecurityException e) {
                android.util.Log.e("EventCheckInActivity", "SecurityException requesting location (permission may have been revoked)", e);
                e.printStackTrace();
                // Proceed without location if permission was revoked
                proceedWithCheckIn(null, null);
            } catch (Exception e) {
                android.util.Log.e("EventCheckInActivity", "Error requesting location", e);
                e.printStackTrace();
                // Proceed without location if there's an exception
                proceedWithCheckIn(null, null);
            }
        } catch (Exception e) {
            android.util.Log.e("EventCheckInActivity", "FATAL ERROR in performCheckIn", e);
            android.util.Log.e("EventCheckInActivity", "Exception type: " + e.getClass().getName());
            android.util.Log.e("EventCheckInActivity", "Exception message: " + e.getMessage());
            e.printStackTrace();
            if (submitButton != null) {
                submitButton.setEnabled(true);
            }
        }
    }
    
    private void proceedWithCheckIn(Location location, String ssid) {
        try {
            // Validate views are initialized
            if (validationStatusTextView == null || submitButton == null || requestQueue == null) {
                android.util.Log.e("EventCheckInActivity", "Views or requestQueue not initialized in proceedWithCheckIn");
                return;
            }
            
            // Show validation status
            validationStatusTextView.setVisibility(View.VISIBLE);
            validationStatusTextView.setText("⏳ Validating check-in...");
            validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_orange_dark));
            submitButton.setEnabled(false);

            // Prepare request body
            JSONObject checkInData = new JSONObject();
            try {
                checkInData.put("code", currentCode.trim());
                
                // Add location only if available
                if (location != null) {
                    checkInData.put("latitude", location.getLatitude());
                    checkInData.put("longitude", location.getLongitude());
                }
                
                // Add WiFi SSID if available
                if (ssid != null && !ssid.trim().isEmpty()) {
                    checkInData.put("ssid", ssid.trim());
                }
            } catch (JSONException e) {
                android.util.Log.e("EventCheckInActivity", "Error creating check-in data", e);
                e.printStackTrace();
                validationStatusTextView.setText("❌ Error preparing request");
                validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                submitButton.setEnabled(true);
                return;
            }

            // URL encode username
            String encodedUsername = "";
            try {
                encodedUsername = java.net.URLEncoder.encode(currentUsername.trim(), "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                android.util.Log.e("EventCheckInActivity", "Error encoding username", e);
                e.printStackTrace();
                encodedUsername = currentUsername.trim();
            }
            
            String url = BASE_URL + "/api/event-attendance/check-in?username=" + encodedUsername;
            android.util.Log.d("EventCheckInActivity", "Check-in URL: " + url);
            android.util.Log.d("EventCheckInActivity", "Check-in data: " + checkInData.toString());

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    url,
                    checkInData,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                boolean success = response.getBoolean("success");
                                String message = response.optString("message", "Check-in successful");
                                
                                if (success) {
                                    validationStatusTextView.setText("✅ " + message);
                                    validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_green_dark));
                                    showCheckInSuccess(response);
                                } else {
                                    validationStatusTextView.setText("❌ " + message);
                                    validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                                    submitButton.setEnabled(true);
                                }
                            } catch (JSONException e) {
                                android.util.Log.e("EventCheckInActivity", "Error parsing response", e);
                                e.printStackTrace();
                                submitButton.setEnabled(true);
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            android.util.Log.e("EventCheckInActivity", "Check-in error", error);
                            String errorMessage = "Check-in failed";
                            boolean isLocationError = false;
                            
                            if (error.networkResponse != null) {
                                int statusCode = error.networkResponse.statusCode;
                                try {
                                    String responseBody = new String(error.networkResponse.data, "utf-8");
                                    android.util.Log.e("EventCheckInActivity", "Error response body: " + responseBody);
                                    JSONObject errorJson = new JSONObject(responseBody);
                                    errorMessage = errorJson.optString("message", errorMessage);
                                    
                                    // Check if error is related to location/geofence
                                    String lowerMessage = errorMessage.toLowerCase();
                                    if (lowerMessage.contains("location") || 
                                        lowerMessage.contains("geofence") || 
                                        lowerMessage.contains("within") ||
                                        lowerMessage.contains("area") ||
                                        statusCode == 403) {
                                        isLocationError = true;
                                    }
                                } catch (Exception e) {
                                    android.util.Log.e("EventCheckInActivity", "Error parsing error response", e);
                                    // Use status code based error messages
                                    if (statusCode == 404) {
                                        errorMessage = "Invalid or expired attendance code";
                                    } else if (statusCode == 400) {
                                        errorMessage = "Invalid check-in request. You may have already checked in.";
                                    } else if (statusCode == 403) {
                                        errorMessage = "Check-in failed: Location or WiFi validation failed";
                                        isLocationError = true;
                                    } else if (statusCode == 500) {
                                        errorMessage = "Server error. Please try again later";
                                    } else {
                                        errorMessage = "Error (HTTP " + statusCode + ")";
                                    }
                                }
                            } else if (error.getMessage() != null) {
                                errorMessage = error.getMessage();
                                String lowerMessage = errorMessage.toLowerCase();
                                if (lowerMessage.contains("location") || lowerMessage.contains("geofence")) {
                                    isLocationError = true;
                                }
                            }
                            
                            validationStatusTextView.setText("❌ " + errorMessage);
                            validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
                            submitButton.setEnabled(true);
                            
                            // Show specific toast for location errors
                            if (isLocationError) {
                            } else {
                            }
                        }
                    }
            );

            request.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            ));

            requestQueue.add(request);
        } catch (Exception e) {
            android.util.Log.e("EventCheckInActivity", "Error in proceedWithCheckIn", e);
            e.printStackTrace();
            validationStatusTextView.setText("❌ Error processing check-in");
            validationStatusTextView.setTextColor(ContextCompat.getColor(EventCheckInActivity.this, android.R.color.holo_red_dark));
            submitButton.setEnabled(true);
        }
    }

    private String getCurrentWifiSSID() {
        try {
            // Check if we have WiFi state permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                return null; // Cannot check WiFi without permission
            }
            
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifiManager == null) {
                return null;
            }
            
            // Check if WiFi is enabled
            boolean wifiEnabled = false;
            try {
                wifiEnabled = wifiManager.isWifiEnabled();
            } catch (SecurityException e) {
                android.util.Log.e("EventCheckInActivity", "SecurityException checking WiFi state", e);
                return null;
            }
            
            if (!wifiEnabled) {
                return null;
            }
            
            // Get SSID (requires ACCESS_FINE_LOCATION permission on Android 10+)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null; // Cannot get SSID without location permission
            }
            
            try {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ssid = wifiInfo.getSSID();
                if (ssid != null && !ssid.equals("<unknown ssid>") && !ssid.equals("0x")) {
                    return ssid.replace("\"", "");
                }
            } catch (SecurityException e) {
                android.util.Log.e("EventCheckInActivity", "SecurityException getting WiFi SSID", e);
                return null;
            }
        } catch (Exception e) {
            android.util.Log.e("EventCheckInActivity", "Error getting WiFi SSID", e);
            e.printStackTrace();
        }
        return null;
    }

    private void showCheckInSuccess(JSONObject response) {
        try {
            String message = response.optString("message", "Check-in successful");
            String checkInTime = response.optString("checkInTime", new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));

            new AlertDialog.Builder(this)
                    .setTitle("✅ Check-in Successful")
                    .setMessage(message + "\n\nEvent: " + eventName + "\nTime: " + checkInTime)
                    .setPositiveButton("OK", (dialog, which) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .setCancelable(false)
                    .show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

