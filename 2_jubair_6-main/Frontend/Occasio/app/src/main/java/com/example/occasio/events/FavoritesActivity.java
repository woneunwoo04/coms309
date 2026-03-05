package com.example.occasio.events;

import com.example.occasio.R;
import com.example.occasio.events.AllEventsActivity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import com.example.occasio.model.Event;
import com.example.occasio.utils.ServerConfig;

public class FavoritesActivity extends AppCompatActivity {
    
    private ScrollView scrollView;
    private LinearLayout favoritesContainer;
    private Button backButton;
    private Button refreshButton;
    private EditText searchEditText;
    private Button searchButton;
    
    private String currentUsername;
    private List<Event> favoritesList;
    private RequestQueue requestQueue;
    
    private static final String BASE_URL = ServerConfig.BASE_URL;
    private static final String FAVORITES_URL = BASE_URL + "/api/favorites/username/";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        android.util.Log.d("FavoritesActivity", "onCreate started");

        // Initialize views
        scrollView = findViewById(R.id.favorites_scroll_view);
        favoritesContainer = findViewById(R.id.favorites_container);
        backButton = findViewById(R.id.favorites_back_btn);
        refreshButton = findViewById(R.id.favorites_refresh_btn);
        searchEditText = findViewById(R.id.favorites_search_et);
        searchButton = findViewById(R.id.favorites_search_btn);

        // Get username from intent
        Intent receivedIntent = getIntent();
        if (receivedIntent != null && receivedIntent.hasExtra("username")) {
            currentUsername = receivedIntent.getStringExtra("username");
        }
        
        // If not in Intent, try SharedPreferences
        if (currentUsername == null || currentUsername.isEmpty()) {
            android.content.SharedPreferences sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            currentUsername = sharedPreferences.getString("username", "");
        }
        
        android.util.Log.d("FavoritesActivity", "Username received: " + currentUsername);

        if (currentUsername == null || currentUsername.isEmpty()) {
            android.util.Log.e("FavoritesActivity", "No username provided");
            finish();
            return;
        }

        // Initialize data
        favoritesList = new ArrayList<>();
        requestQueue = Volley.newRequestQueue(this);

        // Set up click listeners
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(FavoritesActivity.this, AllEventsActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
            finish();
        });

        refreshButton.setOnClickListener(v -> {
            android.util.Log.d("FavoritesActivity", "Refresh button clicked");
            loadFavorites();
        });

        searchButton.setOnClickListener(v -> performSearch());

        // Load favorites
        loadFavorites();
    }

    private void loadFavorites() {
        android.util.Log.d("FavoritesActivity", "Loading favorites for: " + currentUsername);
        
        String url = FAVORITES_URL + currentUsername;
        android.util.Log.d("FavoritesActivity", "Request URL: " + url);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    android.util.Log.d("FavoritesActivity", "Response received: " + response.length() + " favorites");
                    try {
                        favoritesList.clear();
                        
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject favoriteJson = response.getJSONObject(i);
                            
                            // FavoriteEvent has a nested "event" field
                            JSONObject eventJson;
                            if (favoriteJson.has("event") && !favoriteJson.isNull("event")) {
                                // Nested structure: FavoriteEvent.event
                                eventJson = favoriteJson.getJSONObject("event");
                            } else {
                                // Direct structure: Events object (fallback)
                                eventJson = favoriteJson;
                            }
                            
                            Event event = new Event();
                            event.setId((int) eventJson.getLong("id"));
                            event.setTitle(eventJson.optString("eventName", eventJson.optString("title", "Unknown Event")));
                            event.setDescription(eventJson.optString("description", ""));
                            event.setLocation(eventJson.optString("location", ""));
                            
                            // Parse date - backend uses startTime as LocalDateTime
                            String dateStr = eventJson.optString("startTime", eventJson.optString("date", ""));
                            if (!dateStr.isEmpty()) {
                                event.setStartTime(dateStr);
                                event.setEndTime(eventJson.optString("endTime", dateStr));
                            }
                            
                            // Parse organization info
                            JSONObject orgJson = null;
                            if (eventJson.has("organization") && !eventJson.isNull("organization")) {
                                orgJson = eventJson.getJSONObject("organization");
                            }
                            
                            String orgName = "Unknown Organization";
                            String orgId = "0";
                            if (orgJson != null) {
                                orgName = orgJson.optString("orgName", "Unknown Organization");
                                orgId = String.valueOf(orgJson.optLong("id", 0));
                            } else {
                                orgName = eventJson.optString("organizationName", "Unknown Organization");
                                orgId = eventJson.optString("organizationId", "0");
                            }
                            event.setOrganizerName(orgName);
                            event.setOrganizerId(orgId);
                            
                            favoritesList.add(event);
                        }
                        
                        android.util.Log.d("FavoritesActivity", "Parsed " + favoritesList.size() + " favorites");
                        displayFavorites();
                        
                        if (favoritesList.isEmpty()) {
                        }
                        
                    } catch (Exception e) {
                        android.util.Log.e("FavoritesActivity", "Error parsing response", e);
                        showErrorDialog("Error parsing favorites data", e.getMessage());
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    android.util.Log.e("FavoritesActivity", "Network error", error);
                    String errorMessage = "Network error: " + (error.getMessage() != null ? error.getMessage() : "Unknown error");
                    
                    if (error.networkResponse != null) {
                        errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                    }
                    
                    showErrorDialog("Failed to load favorites", errorMessage);
                    
                    // Clear list on error
                    favoritesList.clear();
                    displayFavorites();
                }
            }
        );

        requestQueue.add(request);
    }


    private void performSearch() {
        String searchQuery = searchEditText.getText().toString().trim().toLowerCase();
        android.util.Log.d("FavoritesActivity", "Searching for: " + searchQuery);
        
        List<Event> filteredFavorites = new ArrayList<>();
        
        if (favoritesList == null || favoritesList.isEmpty()) {
            displayFavorites(filteredFavorites);
            return;
        }
        
        for (Event event : favoritesList) {
            if (event == null) continue;
            
            String title = event.getTitle() != null ? event.getTitle().toLowerCase() : "";
            String description = event.getDescription() != null ? event.getDescription().toLowerCase() : "";
            String location = event.getLocation() != null ? event.getLocation().toLowerCase() : "";
            
            boolean matchesSearch = searchQuery.isEmpty() ||
                title.contains(searchQuery) ||
                description.contains(searchQuery) ||
                location.contains(searchQuery);
            
            if (matchesSearch) {
                filteredFavorites.add(event);
            }
        }
        
        displayFavorites(filteredFavorites);
    }

    private void displayFavorites() {
        displayFavorites(favoritesList);
    }

    private void displayFavorites(List<Event> eventsToDisplay) {
        favoritesContainer.removeAllViews();
        
        if (eventsToDisplay.isEmpty()) {
            TextView noFavoritesText = new TextView(this);
            noFavoritesText.setText("No favorite events found");
            noFavoritesText.setTextSize(16);
            noFavoritesText.setTextColor(getResources().getColor(R.color.text_secondary_fall));
            noFavoritesText.setPadding(32, 32, 32, 32);
            noFavoritesText.setGravity(android.view.Gravity.CENTER);
            favoritesContainer.addView(noFavoritesText);
            return;
        }
        
        for (Event event : eventsToDisplay) {
            View eventView = createFavoriteEventView(event);
            favoritesContainer.addView(eventView);
        }
    }

    private View createFavoriteEventView(Event event) {
        LinearLayout eventLayout = new LinearLayout(this);
        eventLayout.setOrientation(LinearLayout.VERTICAL);
        eventLayout.setPadding(16, 16, 16, 16);
        eventLayout.setBackgroundColor(getResources().getColor(R.color.card_background_fall));
        
        // Add margin between events
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 8, 8, 8);
        eventLayout.setLayoutParams(params);
        
        // Event title
        TextView titleText = new TextView(this);
        titleText.setText(event.getTitle());
        titleText.setTextSize(18);
        titleText.setTextColor(getResources().getColor(R.color.text_primary_fall));
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        eventLayout.addView(titleText);
        
        // Event description
        TextView descText = new TextView(this);
        descText.setText(event.getDescription());
        descText.setTextSize(14);
        descText.setTextColor(getResources().getColor(R.color.text_secondary_fall));
        descText.setPadding(0, 8, 0, 8);
        eventLayout.addView(descText);
        
        // Event location
        TextView locationText = new TextView(this);
        locationText.setText("📍 " + event.getLocation());
        locationText.setTextSize(12);
        locationText.setTextColor(getResources().getColor(R.color.text_secondary_fall));
        eventLayout.addView(locationText);
        
        // Event date/time
        TextView dateText = new TextView(this);
        dateText.setText("📅 " + event.getStartTime());
        dateText.setTextSize(12);
        dateText.setTextColor(getResources().getColor(R.color.text_secondary_fall));
        eventLayout.addView(dateText);
        
        // Event organizer
        TextView organizerText = new TextView(this);
        organizerText.setText("👤 " + event.getOrganizerName());
        organizerText.setTextSize(12);
        organizerText.setTextColor(getResources().getColor(R.color.text_secondary_fall));
        eventLayout.addView(organizerText);
        
        // Action buttons
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setPadding(0, 16, 0, 0);
        
        Button removeButton = new Button(this);
        removeButton.setText("💔 Remove");
        removeButton.setBackgroundColor(getResources().getColor(R.color.error_color));
        removeButton.setTextColor(getResources().getColor(R.color.text_on_fall));
        removeButton.setOnClickListener(v -> removeFromFavorites(event));
        
        Button attendButton = new Button(this);
        attendButton.setText("📝 Register");
        attendButton.setBackgroundColor(getResources().getColor(R.color.primary_fall));
        attendButton.setTextColor(getResources().getColor(R.color.text_on_fall));
        attendButton.setOnClickListener(v -> registerForEvent(event));
        
        buttonLayout.addView(removeButton);
        buttonLayout.addView(attendButton);
        eventLayout.addView(buttonLayout);
        
        return eventLayout;
    }

    private void removeFromFavorites(Event event) {
        android.util.Log.d("FavoritesActivity", "Removing from favorites: " + event.getTitle());
        
        new AlertDialog.Builder(this)
            .setTitle("Remove from Favorites")
            .setMessage("Are you sure you want to remove \"" + event.getTitle() + "\" from your favorites?")
            .setPositiveButton("Yes, Remove", (dialog, which) -> {
                String url = BASE_URL + "/api/favorites/username/" + currentUsername + "/" + event.getId();
                
                // Backend returns 204 No Content on success
                com.android.volley.toolbox.StringRequest request = new com.android.volley.toolbox.StringRequest(
                    Request.Method.DELETE,
                    url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            android.util.Log.d("FavoritesActivity", "Successfully removed from favorites");
                            // Remove from local list
                            favoritesList.remove(event);
                            displayFavorites();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            android.util.Log.e("FavoritesActivity", "Error removing from favorites", error);
                            String errorMessage = "Failed to remove from favorites";
                            
                            if (error.networkResponse != null) {
                                int statusCode = error.networkResponse.statusCode;
                                
                                // Try to parse error response body
                                try {
                                    String responseBody = new String(error.networkResponse.data, "utf-8");
                                    if (!responseBody.isEmpty()) {
                                        try {
                                            JSONObject errorJson = new JSONObject(responseBody);
                                            errorMessage = errorJson.optString("message", errorJson.optString("error", errorMessage));
                                        } catch (org.json.JSONException e) {
                                            // If not JSON, check if it's a plain error message
                                            if (!responseBody.isEmpty() && responseBody.length() < 200) {
                                                errorMessage = responseBody;
                                            } else {
                                                errorMessage += " (Status: " + statusCode + ")";
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    switch (statusCode) {
                                        case 404:
                                            errorMessage = "Favorite not found or already removed";
                                            // If already removed, treat as success
                                            favoritesList.remove(event);
                                            displayFavorites();
                                            return; // Don't show error dialog
                                        case 500:
                                            errorMessage = "Server error. Please try again later.";
                                            break;
                                        default:
                                            errorMessage += " (Status: " + statusCode + ")";
                                            break;
                                    }
                                }
                            } else if (error.getMessage() != null) {
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
                            
                        }
                    }
                );
                
                request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                requestQueue.add(request);
            })
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void registerForEvent(Event event) {
        android.util.Log.d("FavoritesActivity", "Registering for event: " + event.getTitle());
        
        new AlertDialog.Builder(this)
            .setTitle("Register for Event")
            .setMessage("Are you sure you want to register for \"" + event.getTitle() + "\"?")
            .setPositiveButton("Yes, Register", (dialog, which) -> {
                String url = BASE_URL + "/api/events/" + event.getId() + "/register?username=" + currentUsername;
                
                // Use StringRequest instead of JsonObjectRequest since we're not sending JSON body
                com.android.volley.toolbox.StringRequest request = new com.android.volley.toolbox.StringRequest(
                    Request.Method.POST,
                    url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            android.util.Log.d("FavoritesActivity", "Successfully registered for event");
                            // Note: Reward points are awarded on check-in, not registration
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            android.util.Log.e("FavoritesActivity", "Error registering", error);
                            String errorMessage = "Failed to register for event";
                            if (error.networkResponse != null) {
                                try {
                                    String responseBody = new String(error.networkResponse.data, "utf-8");
                                    android.util.Log.e("FavoritesActivity", "Error response body: " + responseBody);
                                    // Try to parse as JSON first
                                    try {
                                        JSONObject errorJson = new JSONObject(responseBody);
                                        errorMessage = errorJson.optString("message", errorMessage);
                                    } catch (org.json.JSONException e) {
                                        // If not JSON, check if it's a plain error message
                                        if (!responseBody.isEmpty() && responseBody.length() < 200) {
                                            errorMessage = responseBody;
                                        } else {
                                            errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                                        }
                                    }
                                } catch (Exception e) {
                                    android.util.Log.e("FavoritesActivity", "Error parsing error response", e);
                                    errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                                }
                            }
                        }
                    }
                );
                
                request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                requestQueue.add(request);
            })
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .show();
    }

    // Note: Reward points are now awarded on event check-in (attendance), not registration
    // This method is kept for reference but not used
    // Points are awarded automatically by EventAttendanceService when user checks in

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Copy Error", (dialog, which) -> {
                // Copy error to clipboard
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Error", message);
                clipboard.setPrimaryClip(clip);
            })
            .setNegativeButton("OK", (dialog, which) -> dialog.dismiss())
            .show();
    }
}