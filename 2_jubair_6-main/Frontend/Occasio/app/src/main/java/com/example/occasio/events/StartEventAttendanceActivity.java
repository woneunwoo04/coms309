package com.example.occasio.events;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Timer;
import java.util.TimerTask;

public class StartEventAttendanceActivity extends AppCompatActivity implements OnMapReadyCallback {
    private TextView eventNameTextView;
    private TextView sessionStatusTextView;
    private TextView sessionCodeTextView;
    private TextView timeRemainingTextView;
    private TextView activeSessionInfoTextView;
    private EditText radiusEditText;
    private EditText wifiSSIDEditText;
    private Button enableGeofenceButton;
    private Button disableGeofenceButton;
    private Button useCurrentLocationButton;
    private Button startSessionButton;
    private Button endSessionButton;
    private Button backButton;
    private android.view.View geofenceSection;
    private RequestQueue requestQueue;
    private Long currentOrgId;
    private Long currentEventId;
    private String currentEventName;
    private String currentSessionCode;
    private Long currentSessionId;
    private Timer countdownTimer;
    private long sessionEndTime;
    private boolean sessionActive = false;
    private boolean geofencingEnabled = false;
    private boolean mapInitialized = false;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    
    // Google Maps
    private MapView mapView;
    private GoogleMap googleMap;
    private Marker locationMarker;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng selectedLocation;
    private static final LatLng DEFAULT_LOCATION = new LatLng(42.0267, -93.6465); // ISU coordinates

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_event_attendance);

        initializeViews();
        // Don't request permissions here - only request when geofencing is enabled

        Intent intent = getIntent();
        if (intent != null) {
            currentOrgId = intent.getLongExtra("orgId", -1L);
            currentEventId = intent.getLongExtra("eventId", -1L);
            currentEventName = intent.getStringExtra("eventName");
            if (currentEventName != null && eventNameTextView != null) {
                eventNameTextView.setText(currentEventName);
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // Set click listeners first
        enableGeofenceButton.setOnClickListener(v -> enableGeofencing(savedInstanceState));
        if (disableGeofenceButton != null) {
            disableGeofenceButton.setOnClickListener(v -> disableGeofencing());
        }
        if (useCurrentLocationButton != null) {
            useCurrentLocationButton.setOnClickListener(v -> {
                try {
                    centerOnCurrentLocation();
                } catch (Exception e) {
                    android.util.Log.e("StartEventAttendanceActivity", "Error in centerOnCurrentLocation: " + e.getMessage(), e);
                }
            });
        }
        if (startSessionButton != null) {
            startSessionButton.setOnClickListener(v -> {
                try {
                    startSession();
                } catch (Exception e) {
                    android.util.Log.e("StartEventAttendanceActivity", "Error in startSession: " + e.getMessage(), e);
                    e.printStackTrace();
                    if (startSessionButton != null) {
                        startSessionButton.setEnabled(true);
                        startSessionButton.setText("Start Session");
                    }
                }
            });
        }
        if (endSessionButton != null) {
            endSessionButton.setOnClickListener(v -> {
                try {
                    endSession();
                } catch (Exception e) {
                    android.util.Log.e("StartEventAttendanceActivity", "Error in endSession: " + e.getMessage(), e);
                }
            });
        }
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        // Geofencing is disabled by default - attendance works with code only
        geofencingEnabled = false;
    }
    
    private void enableGeofencing(Bundle savedInstanceState) {
        if (geofencingEnabled) {
            return; // Already enabled
        }
        
        // Show geofencing section
        if (geofenceSection != null) {
            geofenceSection.setVisibility(View.VISIBLE);
        }
        if (enableGeofenceButton != null) {
            enableGeofenceButton.setVisibility(View.GONE);
        }
        
        // Try to initialize map, but don't crash if it fails
        if (!mapInitialized) {
            try {
                // Check permissions first before initializing map
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    // Request permission, then initialize map after permission is granted
                    requestLocationPermissions();
                    // Map will be initialized in onRequestPermissionsResult if permission is granted
                    return;
                }
                
                initializeMap(savedInstanceState);
                mapInitialized = true;
                geofencingEnabled = true;
            } catch (Exception e) {
                android.util.Log.e("StartEventAttendanceActivity", "Error initializing map: " + e.getMessage());
                e.printStackTrace();
                // Hide map-related UI if map fails to initialize
                if (geofenceSection != null) {
                    geofenceSection.setVisibility(View.GONE);
                }
                if (enableGeofenceButton != null) {
                    enableGeofenceButton.setVisibility(View.VISIBLE);
                }
                if (mapView != null) {
                    mapView.setVisibility(View.GONE);
                }
                if (useCurrentLocationButton != null) {
                    useCurrentLocationButton.setVisibility(View.GONE);
                }
                // Allow user to disable geofencing
                geofencingEnabled = false;
            }
        } else {
            geofencingEnabled = true;
        }
    }
    
    private void disableGeofencing() {
        geofencingEnabled = false;
        if (geofenceSection != null) {
            geofenceSection.setVisibility(View.GONE);
        }
        if (enableGeofenceButton != null) {
            enableGeofenceButton.setVisibility(View.VISIBLE);
        }
        selectedLocation = null; // Clear selected location
    }
    
    private void initializeMap(Bundle savedInstanceState) {
        try {
            if (mapView == null) {
                mapView = findViewById(R.id.start_event_attendance_map_view);
            }
            if (mapView != null) {
                try {
                    mapView.onCreate(savedInstanceState != null ? savedInstanceState : new Bundle());
                    // Don't call onResume here - it will be called in onResume() lifecycle method
                    mapView.getMapAsync(this);
                    android.util.Log.d("StartEventAttendanceActivity", "Map initialization started");
                } catch (Exception e) {
                    android.util.Log.e("StartEventAttendanceActivity", "Error in MapView.onCreate: " + e.getMessage());
                    e.printStackTrace();
                    throw e;
                }
            } else {
                android.util.Log.e("StartEventAttendanceActivity", "MapView not found in layout");
                throw new IllegalStateException("MapView not found in layout");
            }
        } catch (Exception e) {
            android.util.Log.e("StartEventAttendanceActivity", "Error in initializeMap: " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-throw to be caught by caller
        }
    }
    
    @Override
    public void onMapReady(GoogleMap map) {
        try {
            android.util.Log.d("StartEventAttendanceActivity", "onMapReady called - map received");
            googleMap = map;
            
            if (googleMap == null) {
                android.util.Log.e("StartEventAttendanceActivity", "GoogleMap is null");
                return;
            }
            
            android.util.Log.d("StartEventAttendanceActivity", "GoogleMap initialized successfully");
            
            // Enable map interactivity
            googleMap.getUiSettings().setZoomControlsEnabled(true);
            googleMap.getUiSettings().setZoomGesturesEnabled(true);
            googleMap.getUiSettings().setScrollGesturesEnabled(true);
            googleMap.getUiSettings().setTiltGesturesEnabled(true);
            googleMap.getUiSettings().setRotateGesturesEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
            googleMap.getUiSettings().setCompassEnabled(true);
            googleMap.getUiSettings().setMapToolbarEnabled(true);
            
            // Set map type
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            
            // Show map centered on ISU with initial marker
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, 15));
            
            // Add initial marker at default location
            selectedLocation = DEFAULT_LOCATION;
            addMarkerAtLocation(DEFAULT_LOCATION);
            
            android.util.Log.d("StartEventAttendanceActivity", "Map configured and marker added");
            
            // Set up marker drag listener
            googleMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
                @Override
                public void onMarkerDragStart(Marker marker) {
                }

                @Override
                public void onMarkerDrag(Marker marker) {
                }

                @Override
                public void onMarkerDragEnd(Marker marker) {
                    if (marker != null) {
                        selectedLocation = marker.getPosition();
                    }
                }
            });
            
            // Set up map click listener - allows user to tap anywhere on map to select location
            googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
                @Override
                public void onMapClick(LatLng latLng) {
                    selectedLocation = latLng;
                    addMarkerAtLocation(latLng);
                    // Animate camera slightly to show the marker
                    googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
                    android.util.Log.d("StartEventAttendanceActivity", "Location selected: " + String.format("%.6f, %.6f", latLng.latitude, latLng.longitude));
                }
            });
            
            // Allow user to long-press to select location as well
            googleMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                @Override
                public void onMapLongClick(LatLng latLng) {
                    selectedLocation = latLng;
                    addMarkerAtLocation(latLng);
                    googleMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
                    android.util.Log.d("StartEventAttendanceActivity", "Location selected (long press): " + String.format("%.6f, %.6f", latLng.latitude, latLng.longitude));
                }
            });
        } catch (Exception e) {
            android.util.Log.e("StartEventAttendanceActivity", "Error in onMapReady: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void addMarkerAtLocation(LatLng location) {
        if (googleMap == null) {
            return;
        }
        try {
            if (locationMarker != null) {
                locationMarker.remove();
            }
            locationMarker = googleMap.addMarker(new MarkerOptions()
                .position(location)
                .title("Attendance Location")
                .snippet("Drag to move or tap map to select")
                .draggable(true)
                .anchor(0.5f, 1.0f)); // Center bottom of marker on location
            
            // Show info window briefly
            if (locationMarker != null) {
                locationMarker.showInfoWindow();
            }
        } catch (Exception e) {
            android.util.Log.e("StartEventAttendanceActivity", "Error adding marker: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void centerOnCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions();
            return;
        }
        
        if (googleMap == null) {
            return;
        }
        
        // Show loading message
        
        fusedLocationClient.getLastLocation()
            .addOnSuccessListener(this, location -> {
                if (location != null && googleMap != null) {
                    try {
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        // Animate camera to current location
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                        selectedLocation = currentLatLng;
                        addMarkerAtLocation(currentLatLng);
                    } catch (Exception e) {
                        android.util.Log.e("StartEventAttendanceActivity", "Error centering on location: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    // Try to get current location with request
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.getCurrentLocation(
                            com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY,
                            null
                        ).addOnSuccessListener(this, currentLocation -> {
                            if (currentLocation != null && googleMap != null) {
                                try {
                                    LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                                    selectedLocation = currentLatLng;
                                    addMarkerAtLocation(currentLatLng);
                                } catch (Exception e) {
                                    android.util.Log.e("StartEventAttendanceActivity", "Error centering on current location: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            } else {
                            }
                        });
                    } else {
                    }
                }
            })
            .addOnFailureListener(this, e -> {
                android.util.Log.e("StartEventAttendanceActivity", "Error getting location: " + e.getMessage());
                e.printStackTrace();
            });
    }
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions();
        }
    }
    
    private void requestLocationPermissions() {
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
            LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted - now initialize map if geofencing was being enabled
                if (!mapInitialized && geofenceSection != null && geofenceSection.getVisibility() == View.VISIBLE) {
                    try {
                        initializeMap(null); // savedInstanceState might be null here, that's okay
                        mapInitialized = true;
                        geofencingEnabled = true;
                    } catch (Exception e) {
                        android.util.Log.e("StartEventAttendanceActivity", "Error initializing map after permission grant: " + e.getMessage());
                        e.printStackTrace();
                        if (geofenceSection != null) {
                            geofenceSection.setVisibility(View.GONE);
                        }
                        if (enableGeofenceButton != null) {
                            enableGeofenceButton.setVisibility(View.VISIBLE);
                        }
                        geofencingEnabled = false;
                    }
                }
            } else {
                // Permission denied
                if (geofenceSection != null) {
                    geofenceSection.setVisibility(View.GONE);
                }
                if (enableGeofenceButton != null) {
                    enableGeofenceButton.setVisibility(View.VISIBLE);
                }
                geofencingEnabled = false;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            if (mapView != null && mapInitialized) {
                try {
                    mapView.onResume();
                    android.util.Log.d("StartEventAttendanceActivity", "MapView onResume called");
                } catch (Exception e) {
                    android.util.Log.e("StartEventAttendanceActivity", "Error in MapView.onResume: " + e.getMessage());
                    e.printStackTrace();
                    // Don't crash - just log the error
                }
            }
            
            // Check for active session when returning to this activity
            if (currentEventId != null && currentEventId > 0 && requestQueue != null) {
                android.util.Log.d("StartEventAttendanceActivity", "onResume: Checking for active session");
                checkForActiveSession();
            }
        } catch (Exception e) {
            android.util.Log.e("StartEventAttendanceActivity", "Error in onResume: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            if (mapView != null && mapInitialized) {
                mapView.onPause();
            }
        } catch (Exception e) {
            android.util.Log.e("StartEventAttendanceActivity", "Error in onPause: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mapView != null && mapInitialized) {
                mapView.onDestroy();
            }
        } catch (Exception e) {
            android.util.Log.e("StartEventAttendanceActivity", "Error in onDestroy: " + e.getMessage());
            e.printStackTrace();
        }
        stopCountdown();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        try {
            if (mapView != null && mapInitialized) {
                mapView.onLowMemory();
            }
        } catch (Exception e) {
            android.util.Log.e("StartEventAttendanceActivity", "Error in onLowMemory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeViews() {
        try {
            eventNameTextView = findViewById(R.id.start_event_attendance_event_name_tv);
            sessionStatusTextView = findViewById(R.id.start_event_attendance_status_tv);
            sessionCodeTextView = findViewById(R.id.start_event_attendance_code_tv);
            timeRemainingTextView = findViewById(R.id.start_event_attendance_time_tv);
            activeSessionInfoTextView = findViewById(R.id.start_event_attendance_info_tv);
            
            // These views are inside the geofence section, but findViewById should still work
            // We'll initialize them even if the section is hidden
            radiusEditText = findViewById(R.id.start_event_attendance_radius_edt);
            wifiSSIDEditText = findViewById(R.id.start_event_attendance_wifi_edt);
            
            enableGeofenceButton = findViewById(R.id.start_event_attendance_enable_geofence_btn);
            disableGeofenceButton = findViewById(R.id.start_event_attendance_disable_geofence_btn);
            geofenceSection = findViewById(R.id.start_event_attendance_geofence_section);
            useCurrentLocationButton = findViewById(R.id.start_event_attendance_use_location_btn);
            startSessionButton = findViewById(R.id.start_event_attendance_start_btn);
            endSessionButton = findViewById(R.id.start_event_attendance_end_btn);
            backButton = findViewById(R.id.start_event_attendance_back_btn);

            // Validate critical views
            if (startSessionButton == null) {
                android.util.Log.e("StartEventAttendanceActivity", "startSessionButton is null!");
                finish();
                return;
            }

            requestQueue = Volley.newRequestQueue(this);
            
            android.util.Log.d("StartEventAttendanceActivity", "Views initialized - radiusEditText: " + (radiusEditText != null) + ", wifiSSIDEditText: " + (wifiSSIDEditText != null));
            
            // Check for existing active session after requestQueue is initialized
            // Only check if we have a valid eventId
            if (currentEventId != null && currentEventId > 0) {
                android.util.Log.d("StartEventAttendanceActivity", "initializeViews: Checking for active session for eventId: " + currentEventId);
                checkForActiveSession();
            } else {
                android.util.Log.w("StartEventAttendanceActivity", "initializeViews: Cannot check for active session - eventId is null or invalid: " + currentEventId);
            }
        } catch (Exception e) {
            android.util.Log.e("StartEventAttendanceActivity", "Error in initializeViews: " + e.getMessage(), e);
            e.printStackTrace();
            finish();
        }

        // Initially hide session info
        if (sessionCodeTextView != null) {
            sessionCodeTextView.setVisibility(View.GONE);
        }
        if (timeRemainingTextView != null) {
            timeRemainingTextView.setVisibility(View.GONE);
        }
        if (activeSessionInfoTextView != null) {
            activeSessionInfoTextView.setVisibility(View.GONE);
        }
        if (endSessionButton != null) {
            endSessionButton.setVisibility(View.GONE);
        }
        if (sessionStatusTextView != null) {
            sessionStatusTextView.setText("No active session");
        }
        
        // Geofencing is disabled by default
        if (geofenceSection != null) {
            geofenceSection.setVisibility(View.GONE);
        }
        if (enableGeofenceButton != null) {
            enableGeofenceButton.setVisibility(View.VISIBLE);
        }
        
        // Set default radius (only if EditText exists)
        if (radiusEditText != null) {
            radiusEditText.setText("50");
        }
    }

    private void checkForActiveSession() {
        if (currentEventId == null || currentEventId <= 0) {
            android.util.Log.w("StartEventAttendanceActivity", "checkForActiveSession: Invalid eventId: " + currentEventId);
            return;
        }
        
        if (requestQueue == null) {
            android.util.Log.w("StartEventAttendanceActivity", "checkForActiveSession: RequestQueue is null, initializing...");
            requestQueue = Volley.newRequestQueue(this);
            if (requestQueue == null) {
                android.util.Log.e("StartEventAttendanceActivity", "checkForActiveSession: Failed to initialize RequestQueue");
                return;
            }
        }
        
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/event-attendance/events/" + currentEventId + "/active-session";
        android.util.Log.d("StartEventAttendanceActivity", "checkForActiveSession: Checking URL: " + url);
        
        com.android.volley.toolbox.JsonObjectRequest request = new com.android.volley.toolbox.JsonObjectRequest(
            com.android.volley.Request.Method.GET,
            url,
            null,
            new com.android.volley.Response.Listener<org.json.JSONObject>() {
                @Override
                public void onResponse(org.json.JSONObject response) {
                    try {
                        android.util.Log.d("StartEventAttendanceActivity", "Active session response: " + response.toString());
                        // Log response keys for debugging
                        java.util.Iterator<String> keys = response.keys();
                        StringBuilder keysStr = new StringBuilder();
                        while (keys.hasNext()) {
                            if (keysStr.length() > 0) keysStr.append(", ");
                            keysStr.append(keys.next());
                        }
                        android.util.Log.d("StartEventAttendanceActivity", "Response keys: " + keysStr.toString());
                        
                        // Check if active session exists - try multiple ways to check
                        boolean isActive = false;
                        if (response.has("active")) {
                            isActive = response.getBoolean("active");
                        } else if (response.has("isActive")) {
                            isActive = response.getBoolean("isActive");
                        } else {
                            // If no active field, check if we have a code and sessionId - that means it's active
                            if (response.has("code") && response.has("sessionId")) {
                                isActive = true;
                            }
                        }
                        
                        android.util.Log.d("StartEventAttendanceActivity", "Is active session: " + isActive);
                        
                        if (isActive) {
                            // Active session exists - restore UI
                            currentSessionCode = response.optString("code", "");
                            
                            // Make sure sessionId is actually present and valid
                            // Try both "sessionId" and "session_id" field names
                            if (response.has("sessionId")) {
                                currentSessionId = response.getLong("sessionId");
                            } else if (response.has("session_id")) {
                                currentSessionId = response.getLong("session_id");
                            } else {
                                // Try to get it from the response, or use -1 if not found
                                currentSessionId = response.optLong("sessionId", -1L);
                                if (currentSessionId <= 0) {
                                    currentSessionId = response.optLong("session_id", -1L);
                                }
                                if (currentSessionId <= 0) {
                                    android.util.Log.w("StartEventAttendanceActivity", "Active session found but sessionId is missing or invalid. Response keys: " + response.keys());
                                    // If sessionId is missing, we can't end the session properly, but we can still show the code
                                    android.util.Log.w("StartEventAttendanceActivity", "Warning: sessionId not found in response. Ending session may not work.");
                                }
                            }
                            
                            android.util.Log.d("StartEventAttendanceActivity", "Restored active session - Code: " + currentSessionCode + ", SessionId: " + currentSessionId);
                            
                            // Get expiry time
                            String expiryTimeStr = response.optString("expiryTime", "");
                            if (!expiryTimeStr.isEmpty()) {
                                try {
                                    java.time.LocalDateTime expiryTime = java.time.LocalDateTime.parse(expiryTimeStr);
                                    sessionEndTime = expiryTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                                } catch (Exception e) {
                                    android.util.Log.w("StartEventAttendanceActivity", "Error parsing expiry time: " + e.getMessage());
                                    // Default to 5 minutes from now
                                    sessionEndTime = System.currentTimeMillis() + (5 * 60 * 1000);
                                }
                            } else {
                                sessionEndTime = System.currentTimeMillis() + (5 * 60 * 1000);
                            }
                            
                            // Update UI to show active session
                            updateSessionUI(true);
                            startCountdown();
                            android.util.Log.d("StartEventAttendanceActivity", "Successfully restored active session UI");
                        } else {
                            // No active session
                            android.util.Log.d("StartEventAttendanceActivity", "No active session found");
                            updateSessionUI(false);
                        }
                    } catch (org.json.JSONException e) {
                        android.util.Log.e("StartEventAttendanceActivity", "Error parsing active session response", e);
                        e.printStackTrace();
                        updateSessionUI(false);
                    }
                }
            },
            new com.android.volley.Response.ErrorListener() {
                @Override
                public void onErrorResponse(com.android.volley.VolleyError error) {
                    // No active session (404) or error - default to no session
                    updateSessionUI(false);
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        android.util.Log.d("StartEventAttendanceActivity", "No active session found");
                    } else {
                        android.util.Log.e("StartEventAttendanceActivity", "Error checking active session: " + error.getMessage());
                    }
                }
            }
        );
        
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(
            5000, // 5 seconds timeout
            1, // 1 retry
            com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        ));
        
        if (requestQueue != null) {
            requestQueue.add(request);
        } else {
            android.util.Log.e("StartEventAttendanceActivity", "RequestQueue is null in checkForActiveSession!");
            requestQueue = com.android.volley.toolbox.Volley.newRequestQueue(this);
            if (requestQueue != null) {
                requestQueue.add(request);
            }
        }
    }
    
    private void startSession() {
        try {
            // Validate required data
            if (currentOrgId == null || currentOrgId < 0) {
                return;
            }

            if (currentEventId == null || currentEventId < 0) {
                return;
            }
            
            // Validate request queue
            if (requestQueue == null) {
                android.util.Log.e("StartEventAttendanceActivity", "RequestQueue is null in startSession!");
                requestQueue = Volley.newRequestQueue(this);
                if (requestQueue == null) {
                    return;
                }
            }

            // Prepare request body
            JSONObject sessionData = new JSONObject();
            try {
                sessionData.put("eventId", currentEventId);
                
                // Optional geofence - only if geofencing is enabled AND location is selected AND radius is provided
                // Backend requires ALL geofence parameters (latitude, longitude, radius) together or NONE
                if (geofencingEnabled && selectedLocation != null && radiusEditText != null) {
                String radiusStr = "";
                try {
                    radiusStr = radiusEditText.getText().toString().trim();
                } catch (Exception e) {
                    android.util.Log.e("StartEventAttendanceActivity", "Error getting radius text: " + e.getMessage());
                    radiusStr = "";
                }
                if (!radiusStr.isEmpty()) {
                    try {
                        double radius = Double.parseDouble(radiusStr);
                        if (radius > 0) {
                            // Only add geofence if radius is valid and > 0
                            sessionData.put("latitude", selectedLocation.latitude);
                            sessionData.put("longitude", selectedLocation.longitude);
                            sessionData.put("radius", radius);
                            
                            // WiFi SSID is optional fallback
                            if (wifiSSIDEditText != null) {
                                try {
                                    String wifiSSID = wifiSSIDEditText.getText().toString().trim();
                                    if (!wifiSSID.isEmpty()) {
                                        sessionData.put("wifiSSID", wifiSSID);
                                    }
                                } catch (Exception e) {
                                    android.util.Log.e("StartEventAttendanceActivity", "Error getting WiFi SSID text: " + e.getMessage());
                                    // Continue without WiFi SSID
                                }
                            }
                            
                            android.util.Log.d("StartEventAttendanceActivity", "Geofence enabled - Lat: " + selectedLocation.latitude + ", Lon: " + selectedLocation.longitude + ", Radius: " + radius);
                        } else {
                            android.util.Log.w("StartEventAttendanceActivity", "Radius must be greater than 0, skipping geofence");
                            // Don't add geofence if radius is 0 or negative
                        }
                    } catch (NumberFormatException e) {
                        android.util.Log.e("StartEventAttendanceActivity", "Invalid radius format: " + radiusStr, e);
                        if (startSessionButton != null) {
                            startSessionButton.setEnabled(true);
                            startSessionButton.setText("Start Session");
                        }
                        return;
                    }
                } else {
                    android.util.Log.d("StartEventAttendanceActivity", "No radius provided, starting session without geofence");
                    // Location selected but no radius - start without geofence
                }
                } else {
                    android.util.Log.d("StartEventAttendanceActivity", "Geofencing disabled or not configured, starting session with code only");
                    // Geofencing disabled or not configured - start without geofence (code-based attendance)
                }
            } catch (JSONException e) {
                android.util.Log.e("StartEventAttendanceActivity", "Error preparing session data: " + e.getMessage(), e);
                e.printStackTrace();
                if (startSessionButton != null) {
                    startSessionButton.setEnabled(true);
                    startSessionButton.setText("Start Session");
                }
                return;
            }

            String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/event-attendance/sessions/start?orgId=" + currentOrgId;
            
            android.util.Log.d("StartEventAttendanceActivity", "Starting session - URL: " + url);
            android.util.Log.d("StartEventAttendanceActivity", "Session data: " + sessionData.toString());
            android.util.Log.d("StartEventAttendanceActivity", "Event ID: " + currentEventId);
            android.util.Log.d("StartEventAttendanceActivity", "Org ID: " + currentOrgId);
            android.util.Log.d("StartEventAttendanceActivity", "Selected location: " + (selectedLocation != null ? selectedLocation.toString() : "null"));
            android.util.Log.d("StartEventAttendanceActivity", "Geofencing enabled: " + geofencingEnabled);

            if (startSessionButton != null) {
                startSessionButton.setEnabled(false);
                startSessionButton.setText("Starting...");
            }

            JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                sessionData,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            currentSessionCode = response.getString("code");
                            currentSessionId = response.getLong("sessionId");
                            String message = "Session started successfully!";
                            
                            // Get expiry time
                            String expiryTimeStr = response.optString("expiryTime", "");
                            if (!expiryTimeStr.isEmpty()) {
                                try {
                                    // Parse ISO format
                                    java.time.LocalDateTime expiryTime = java.time.LocalDateTime.parse(expiryTimeStr);
                                    sessionEndTime = expiryTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                                } catch (Exception e) {
                                    // Default to 5 minutes from now
                                    sessionEndTime = System.currentTimeMillis() + (5 * 60 * 1000);
                                }
                            } else {
                                sessionEndTime = System.currentTimeMillis() + (5 * 60 * 1000);
                            }

                            updateSessionUI(true);
                            startCountdown();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            if (startSessionButton != null) {
                                startSessionButton.setEnabled(true);
                                startSessionButton.setText("Start Session");
                            }
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        android.util.Log.e("StartEventAttendanceActivity", "Error starting session", error);
                        error.printStackTrace();
                        String errorMessage = "Failed to start session";
                        
                        if (error.networkResponse != null) {
                            int statusCode = error.networkResponse.statusCode;
                            android.util.Log.e("StartEventAttendanceActivity", "HTTP Status Code: " + statusCode);
                            
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                android.util.Log.e("StartEventAttendanceActivity", "Response body: " + responseBody);
                                JSONObject errorJson = new JSONObject(responseBody);
                                errorMessage = errorJson.optString("message", errorJson.optString("error", errorMessage));
                            } catch (Exception e) {
                                android.util.Log.e("StartEventAttendanceActivity", "Error parsing error response", e);
                                // Use status code based error messages
                                if (statusCode == 400) {
                                    errorMessage = "Invalid request. Please check event ID and geofence parameters.";
                                } else if (statusCode == 403) {
                                    errorMessage = "You don't have permission to start this session.";
                                } else if (statusCode == 404) {
                                    errorMessage = "Event not found.";
                                } else if (statusCode == 500) {
                                    errorMessage = "Server error. Please try again later.";
                                } else {
                                    errorMessage = "Error (HTTP " + statusCode + "): " + (error.getMessage() != null ? error.getMessage() : "Unknown error");
                                }
                            }
                        } else if (error.getMessage() != null) {
                            android.util.Log.e("StartEventAttendanceActivity", "Error message: " + error.getMessage());
                            errorMessage = "Network error: " + error.getMessage();
                        }
                        
                        android.util.Log.e("StartEventAttendanceActivity", "Final error message: " + errorMessage);
                        if (startSessionButton != null) {
                            startSessionButton.setEnabled(true);
                            startSessionButton.setText("Start Session");
                        }
                    }
                }
        );

            request.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            ));

            if (requestQueue != null) {
                requestQueue.add(request);
            } else {
                android.util.Log.e("StartEventAttendanceActivity", "RequestQueue is null!");
                if (startSessionButton != null) {
                    startSessionButton.setEnabled(true);
                    startSessionButton.setText("Start Session");
                }
            }
        } catch (Exception e) {
            android.util.Log.e("StartEventAttendanceActivity", "Unexpected error in startSession: " + e.getMessage(), e);
            e.printStackTrace();
            if (startSessionButton != null) {
                startSessionButton.setEnabled(true);
                startSessionButton.setText("Start Session");
            }
        }
    }

    private void endSession() {
        if (currentSessionId == null || currentSessionId <= 0) {
            android.util.Log.e("StartEventAttendanceActivity", "Cannot end session - currentSessionId is null or invalid: " + currentSessionId);
            return;
        }

        if (currentOrgId == null || currentOrgId <= 0) {
            android.util.Log.e("StartEventAttendanceActivity", "Cannot end session - currentOrgId is null or invalid: " + currentOrgId);
            return;
        }
        
        android.util.Log.d("StartEventAttendanceActivity", "Ending session - SessionId: " + currentSessionId + ", OrgId: " + currentOrgId);

        new android.app.AlertDialog.Builder(this)
                .setTitle("End Session")
                .setMessage("Are you sure you want to end the active attendance session?")
                .setPositiveButton("End Session", (dialog, which) -> {
                    String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/event-attendance/sessions/" + currentSessionId + "/end?orgId=" + currentOrgId;

                    if (endSessionButton != null) {
                        endSessionButton.setEnabled(false);
                        endSessionButton.setText("Ending...");
                    }

                    com.android.volley.toolbox.StringRequest request = new com.android.volley.toolbox.StringRequest(
                            Request.Method.POST,
                            url,
                            response -> {
                                android.util.Log.d("StartEventAttendanceActivity", "End session response: " + response);
                                stopCountdown();
                                updateSessionUI(false);
                                currentSessionCode = null;
                                currentSessionId = null;
                                if (endSessionButton != null) {
                                    endSessionButton.setEnabled(true);
                                    endSessionButton.setText("End Session");
                                }
                            },
                            error -> {
                                error.printStackTrace();
                                String errorMessage = "Error ending session";
                                
                                // Check for timeout error specifically
                                if (error instanceof com.android.volley.TimeoutError) {
                                    errorMessage = "Connection timeout. Please check your internet connection and try again.";
                                    android.util.Log.e("StartEventAttendanceActivity", "Timeout error ending session");
                                } else if (error.networkResponse != null) {
                                    int statusCode = error.networkResponse.statusCode;
                                    try {
                                        String responseBody = new String(error.networkResponse.data, "utf-8");
                                        android.util.Log.e("StartEventAttendanceActivity", "End session error response: " + responseBody);
                                        org.json.JSONObject errorJson = new org.json.JSONObject(responseBody);
                                        errorMessage = errorJson.optString("message", errorJson.optString("error", errorMessage));
                                    } catch (Exception e) {
                                        android.util.Log.e("StartEventAttendanceActivity", "Error parsing error response", e);
                                        if (statusCode == 404) {
                                            errorMessage = "Session not found or already ended";
                                        } else if (statusCode == 403) {
                                            errorMessage = "You don't have permission to end this session";
                                        } else if (statusCode == 500) {
                                            errorMessage = "Server error. Please try again later";
                                        } else {
                                            errorMessage = "Error ending session (HTTP " + statusCode + ")";
                                        }
                                    }
                                } else if (error.getMessage() != null) {
                                    String msg = error.getMessage();
                                    if (msg.contains("timeout") || msg.contains("Timeout")) {
                                        errorMessage = "Connection timeout. Please check your internet connection.";
                                    } else if (msg.contains("No address") || msg.contains("Unable to resolve")) {
                                        errorMessage = "Cannot reach server. Please check your connection.";
                                    } else {
                                        errorMessage = "Error ending session: " + msg;
                                    }
                                } else {
                                    errorMessage = "Network error. Please check your connection and try again.";
                                }
                                
                                android.util.Log.e("StartEventAttendanceActivity", "End session error: " + errorMessage);
                                if (endSessionButton != null) {
                                    endSessionButton.setEnabled(true);
                                    endSessionButton.setText("End Session");
                                }
                            }
                    );
                    
                    // Add retry policy with longer timeout
                    request.setRetryPolicy(new DefaultRetryPolicy(
                        20000, // 20 seconds timeout (longer than default)
                        2, // 2 retries
                        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
                    ));

                    if (requestQueue != null) {
                        requestQueue.add(request);
                    } else {
                        android.util.Log.e("StartEventAttendanceActivity", "RequestQueue is null in endSession!");
                        requestQueue = Volley.newRequestQueue(this);
                        if (requestQueue != null) {
                            requestQueue.add(request);
                        } else {
                            if (endSessionButton != null) {
                                endSessionButton.setEnabled(true);
                                endSessionButton.setText("End Session");
                            }
                        }
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void updateSessionUI(boolean active) {
        sessionActive = active;
        if (active) {
            if (sessionStatusTextView != null) {
                try {
                    sessionStatusTextView.setText("✅ Session Active");
                } catch (Exception e) {
                    android.util.Log.e("StartEventAttendanceActivity", "Error setting session status text: " + e.getMessage());
                }
            }
            if (sessionCodeTextView != null && currentSessionCode != null) {
                sessionCodeTextView.setText("Code: " + currentSessionCode);
                sessionCodeTextView.setVisibility(View.VISIBLE);
            }
            if (timeRemainingTextView != null) {
                timeRemainingTextView.setVisibility(View.VISIBLE);
            }
            if (activeSessionInfoTextView != null) {
                activeSessionInfoTextView.setVisibility(View.VISIBLE);
                activeSessionInfoTextView.setText("Share this code with users to check in");
            }
            if (startSessionButton != null) {
                startSessionButton.setVisibility(View.GONE);
            }
            if (endSessionButton != null) {
                endSessionButton.setVisibility(View.VISIBLE);
            }
        } else {
            if (sessionStatusTextView != null) {
                sessionStatusTextView.setText("No active session");
            }
            if (sessionCodeTextView != null) {
                sessionCodeTextView.setVisibility(View.GONE);
            }
            if (timeRemainingTextView != null) {
                timeRemainingTextView.setVisibility(View.GONE);
            }
            if (activeSessionInfoTextView != null) {
                activeSessionInfoTextView.setVisibility(View.GONE);
            }
            if (startSessionButton != null) {
                startSessionButton.setVisibility(View.VISIBLE);
                startSessionButton.setEnabled(true);
                startSessionButton.setText("Start Session");
            }
            if (endSessionButton != null) {
                endSessionButton.setVisibility(View.GONE);
            }
        }
    }

    private void startCountdown() {
        stopCountdown();
        countdownTimer = new Timer();
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    long remaining = sessionEndTime - System.currentTimeMillis();
                    if (remaining > 0) {
                        long minutes = remaining / 60000;
                        long seconds = (remaining % 60000) / 1000;
                        timeRemainingTextView.setText("Time Remaining: " + String.format("%02d:%02d", minutes, seconds));
                    } else {
                        timeRemainingTextView.setText("Session Expired");
                        stopCountdown();
                        updateSessionUI(false);
                    }
                });
            }
        }, 0, 1000);
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
    }
}

