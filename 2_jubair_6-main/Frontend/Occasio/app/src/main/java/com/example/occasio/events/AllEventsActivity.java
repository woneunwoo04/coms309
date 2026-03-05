package com.example.occasio.events;

import com.example.occasio.R;
import com.example.occasio.base.BaseNavigationActivity;
import com.example.occasio.utils.InAppNotificationHelper;

import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast; // Keep for now but replace all usage with InAppNotificationHelper
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

/**
 * Activity for displaying and managing all available events in the Occasio application.
 * 
 * <p>This activity serves as the main events browsing interface, allowing users to:
 * <ul>
 *   <li>View all available events from the backend</li>
 *   <li>Search and filter events by title, description, or location</li>
 *   <li>Register for events</li>
 *   <li>Add events to favorites</li>
 *   <li>Navigate to related activities (My Events, Favorites, Rewards)</li>
 * </ul>
 * </p>
 * 
 * <p>The activity extends BaseNavigationActivity to inherit bottom navigation
 * functionality and WebSocket support for real-time notifications.</p>
 * 
 * @author Team Member 1
 * @version 1.0
 * @since 1.0
 */
public class AllEventsActivity extends BaseNavigationActivity {
    
    private ScrollView scrollView;
    private LinearLayout eventsContainer;
    private Button backButton;
    private Button refreshButton;
    private Button filterAllButton;
    private Button filterRegisteredButton;
    private Button filterFavoritesButton;
    private Button calendarViewButton;
    private EditText searchEditText;
    private Button searchButton;
    
    private String currentUsername;
    private List<Event> eventsList;
    private List<Event> filteredEventsList;
    private RequestQueue requestQueue;
    private String currentFilter = "ALL"; // ALL, REGISTERED, FAVORITES
    
    private static final String ALL_EVENTS_URL = com.example.occasio.utils.ServerConfig.EVENTS_URL;

    /**
     * Initializes the activity and sets up the user interface for event browsing.
     * 
     * <p>This method initializes all UI components, retrieves the current username
     * from Intent or SharedPreferences, sets up click listeners for navigation buttons,
     * and loads all events from the backend server.</p>
     * 
     * <p>If no username is found, the activity finishes and prompts the user to log in again.</p>
     * 
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down, this Bundle contains
     *                           the data it most recently supplied in
     *                           onSaveInstanceState(Bundle). Otherwise it is null.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 1001) { // Payment request code
            if (resultCode == RESULT_OK && data != null) {
                boolean paymentSuccess = data.getBooleanExtra("paymentSuccess", false);
                if (paymentSuccess) {
                    // Payment succeeded, now register for event
                    Long eventId = data.getLongExtra("eventId", -1L);
                    if (eventId > 0) {
                        // Find the event and register
                        Event event = eventsList.stream()
                                .filter(e -> e.getId() == eventId.intValue())
                                .findFirst()
                                .orElse(null);
                        if (event != null) {
                            // Register for event after successful payment
                            registerForEventAfterPayment(event);
                        }
                    }
                } else {
                    InAppNotificationHelper.showNotification(this, "❌ Payment Required", "Could not register. Payment was not completed.");
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_events);

        android.util.Log.d("AllEventsActivity", "onCreate started");

        // Initialize views
        scrollView = findViewById(R.id.all_events_scroll_view);
        eventsContainer = findViewById(R.id.all_events_container);
        backButton = findViewById(R.id.all_events_back_btn);
        refreshButton = findViewById(R.id.all_events_refresh_btn);
        filterAllButton = findViewById(R.id.all_events_filter_all_btn);
        filterRegisteredButton = findViewById(R.id.all_events_filter_registered_btn);
        filterFavoritesButton = findViewById(R.id.all_events_filter_favorites_btn);
        calendarViewButton = findViewById(R.id.all_events_calendar_btn);
        searchEditText = findViewById(R.id.all_events_search_et);
        searchButton = findViewById(R.id.all_events_search_btn);

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
        
        android.util.Log.d("AllEventsActivity", "Username received: " + currentUsername);

        if (currentUsername == null || currentUsername.isEmpty()) {
            android.util.Log.e("AllEventsActivity", "No username provided");
            InAppNotificationHelper.showNotification(this, "⚠️ Error", "No username provided. Please log in again.");
            finish();
            return;
        }

        // Initialize data
        eventsList = new ArrayList<>();
        filteredEventsList = new ArrayList<>();
        requestQueue = Volley.newRequestQueue(this);

        // Setup bottom navigation
        setupBottomNavigation();

        // Set up click listeners
        if (backButton != null) {
            backButton.setVisibility(View.GONE); // Hide back button since we have bottom nav
        }

        // Filter chip listeners
        if (filterAllButton != null) {
            filterAllButton.setOnClickListener(v -> {
                currentFilter = "ALL";
                updateFilterButtons();
                performFilter();
            });
        }

        if (filterRegisteredButton != null) {
            filterRegisteredButton.setOnClickListener(v -> {
                currentFilter = "REGISTERED";
                updateFilterButtons();
                performFilter();
            });
        }

        if (filterFavoritesButton != null) {
            filterFavoritesButton.setOnClickListener(v -> {
                currentFilter = "FAVORITES";
                updateFilterButtons();
                performFilter();
            });
        }

        if (calendarViewButton != null) {
            calendarViewButton.setOnClickListener(v -> {
                Intent intent = new Intent(AllEventsActivity.this, CalendarActivity.class);
                intent.putExtra("username", currentUsername);
                startActivity(intent);
            });
        }

        // Initialize filter button states
        updateFilterButtons();

        refreshButton.setOnClickListener(v -> {
            android.util.Log.d("AllEventsActivity", "Refresh button clicked");
            loadAllEvents();
        });

        searchButton.setOnClickListener(v -> performSearch());

        // Load events
        loadAllEvents();
    }

    /**
     * Fetches all available events from the backend server.
     * 
     * <p>This method makes a GET request to the events endpoint and parses the
     * JSON response into Event objects. It handles both successful responses
     * and network errors, updating the UI accordingly.</p>
     * 
     * <p>On success:
     * <ul>
     *   <li>Parses JSON array into Event objects</li>
     *   <li>Extracts event details (title, description, location, dates, organizer)</li>
     *   <li>Updates the filtered events list</li>
     *   <li>Displays events in the UI</li>
     * </ul>
     * </p>
     * 
     * <p>On error:
     * <ul>
     *   <li>Shows error dialog with details</li>
     *   <li>Clears the events list</li>
     *   <li>Displays empty state message</li>
     * </ul>
     * </p>
     */
    private void loadAllEvents() {
        android.util.Log.d("AllEventsActivity", "Loading all events");
        
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, ALL_EVENTS_URL, null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    android.util.Log.d("AllEventsActivity", "Response received: " + response.length() + " events");
                    try {
                            eventsList.clear();
                        
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject eventJson = response.getJSONObject(i);
                            try {
                                Event event = parseEventFromJson(eventJson);
                                if (event != null) {
                                    eventsList.add(event);
                                }
                            } catch (Exception e) {
                                android.util.Log.e("AllEventsActivity", "Error parsing event at index " + i, e);
                            }
                        }
                        
                        android.util.Log.d("AllEventsActivity", "Parsed " + eventsList.size() + " events");
                        
                        // Apply current filter after loading
                        if ("ALL".equals(currentFilter)) {
                            filteredEventsList = new ArrayList<>(eventsList);
                            displayEvents();
                        } else {
                            // If a filter is active, re-apply it
                            performFilter();
                        }
                        
                        if (eventsList.isEmpty()) {
                            InAppNotificationHelper.showNotification(AllEventsActivity.this, "📋 Events", "No events found!");
                        }
                        
                    } catch (Exception e) {
                        android.util.Log.e("AllEventsActivity", "Error parsing response", e);
                        showErrorDialog("Error parsing events data", e.getMessage());
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    android.util.Log.e("AllEventsActivity", "Network error", error);
                    String errorMessage = "Network error: " + (error.getMessage() != null ? error.getMessage() : "Unknown error");
                    
                    if (error.networkResponse != null) {
                        errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                    }
                    
                    showErrorDialog("Failed to load events", errorMessage);
                    
                    // Clear list on error
                    eventsList.clear();
                    filteredEventsList.clear();
                    displayEvents();
                }
            }
        );

        requestQueue.add(request);
    }


    /**
     * Performs a search/filter operation on the events list based on user input.
     * 
     * <p>This method filters the events list by matching the search query against
     * event titles, descriptions, and locations. The search is case-insensitive
     * and matches partial strings.</p>
     * 
     * <p>Search behavior:
     * <ul>
     *   <li>If search query is empty, all events are shown</li>
     *   <li>Matches are found in title, description, or location fields</li>
     *   <li>Results are displayed immediately after filtering</li>
     *   <li>Shows a toast message with the number of results found</li>
     * </ul>
     * </p>
     */
    private void performSearch() {
        String searchQuery = searchEditText.getText().toString().trim().toLowerCase();
        android.util.Log.d("AllEventsActivity", "Searching for: " + searchQuery);
        
        filteredEventsList.clear();
        
        if (eventsList == null || eventsList.isEmpty()) {
            displayEvents();
            InAppNotificationHelper.showNotification(this, "🔍 Search", "No events to search");
            return;
        }
        
        for (Event event : eventsList) {
            if (event == null) continue;
            
            String title = event.getTitle() != null ? event.getTitle().toLowerCase() : "";
            String description = event.getDescription() != null ? event.getDescription().toLowerCase() : "";
            String location = event.getLocation() != null ? event.getLocation().toLowerCase() : "";
            
            boolean matchesSearch = searchQuery.isEmpty() ||
                title.contains(searchQuery) ||
                description.contains(searchQuery) ||
                location.contains(searchQuery);
            
            if (matchesSearch) {
                filteredEventsList.add(event);
            }
        }
        
        displayEvents();
        InAppNotificationHelper.showNotification(this, "🔍 Search Results", "Found " + filteredEventsList.size() + " events");
    }


    /**
     * Displays the filtered events list in the UI container.
     * 
     * <p>This method clears the events container and dynamically creates views
     * for each event in the filtered list. If no events are found, it displays
     * an empty state message.</p>
     * 
     * <p>For each event, a view is created using the createEventView() method,
     * which includes event details and action buttons.</p>
     */
    private void displayEvents() {
        eventsContainer.removeAllViews();
        
        if (filteredEventsList.isEmpty()) {
            TextView noEventsText = new TextView(this);
            noEventsText.setText("No events found");
            noEventsText.setTextSize(16);
            noEventsText.setTextColor(getResources().getColor(R.color.text_secondary_fall));
            noEventsText.setPadding(32, 32, 32, 32);
            noEventsText.setGravity(android.view.Gravity.CENTER);
            eventsContainer.addView(noEventsText);
            return;
        }
        
        for (Event event : filteredEventsList) {
            View eventView = createEventView(event);
            eventsContainer.addView(eventView);
        }
    }

    /**
     * Creates a dynamically generated view for displaying a single event.
     * 
     * <p>This method creates a LinearLayout containing all event information
     * and action buttons. The view includes:
     * <ul>
     *   <li>Event title (bold, large text)</li>
     *   <li>Event description (truncated to 3 lines)</li>
     *   <li>Location with icon</li>
     *   <li>Date/time with icon</li>
     *   <li>Organizer name with icon</li>
     *   <li>Register and Favorite buttons</li>
     * </ul>
     * </p>
     * 
     * @param event The Event object containing all event details
     * @return A View containing the formatted event display with all information
     *         and interactive buttons
     */
    private View createEventView(Event event) {
        // Use CardView for better visual hierarchy
        androidx.cardview.widget.CardView cardView = new androidx.cardview.widget.CardView(this);
        cardView.setCardElevation(4);
        cardView.setRadius(12);
        cardView.setCardBackgroundColor(getResources().getColor(R.color.card_background_fall));
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(12, 12, 12, 12);
        cardView.setLayoutParams(cardParams);
        
        LinearLayout eventLayout = new LinearLayout(this);
        eventLayout.setOrientation(LinearLayout.VERTICAL);
        eventLayout.setPadding(20, 20, 20, 20);
        
        cardView.addView(eventLayout);
        
        // Event title
        TextView titleText = new TextView(this);
        titleText.setText(event.getTitle());
        titleText.setTextSize(18);
        titleText.setTextColor(getResources().getColor(R.color.text_primary_fall));
        titleText.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, 12);
        titleText.setLayoutParams(titleParams);
        eventLayout.addView(titleText);
        
        // Event description
        TextView descText = new TextView(this);
        descText.setText(event.getDescription());
        descText.setTextSize(14);
        descText.setTextColor(getResources().getColor(R.color.text_secondary_fall));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descParams.setMargins(0, 0, 0, 12);
        descText.setLayoutParams(descParams);
        descText.setMaxLines(3);
        descText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        eventLayout.addView(descText);
        
        // Event location
        TextView locationText = new TextView(this);
        locationText.setText("📍 " + event.getLocation());
        locationText.setTextSize(13);
        locationText.setTextColor(getResources().getColor(R.color.text_secondary_fall));
        LinearLayout.LayoutParams locationParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        locationParams.setMargins(0, 0, 0, 8);
        locationText.setLayoutParams(locationParams);
        eventLayout.addView(locationText);
        
        // Event date/time
        TextView dateText = new TextView(this);
        dateText.setText("📅 " + event.getStartTime());
        dateText.setTextSize(13);
        dateText.setTextColor(getResources().getColor(R.color.text_secondary_fall));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        dateParams.setMargins(0, 0, 0, 8);
        dateText.setLayoutParams(dateParams);
        eventLayout.addView(dateText);
        
        // Event organizer
        TextView organizerText = new TextView(this);
        organizerText.setText("👤 " + event.getOrganizerName());
        organizerText.setTextSize(13);
        organizerText.setTextColor(getResources().getColor(R.color.text_secondary_fall));
        LinearLayout.LayoutParams organizerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        organizerParams.setMargins(0, 0, 0, 8);
        organizerText.setLayoutParams(organizerParams);
        eventLayout.addView(organizerText);
        
        // Registration fee (if applicable)
        if (event.requiresPayment()) {
            TextView feeText = new TextView(this);
            feeText.setText("💰 Registration Fee: $" + String.format("%.2f", event.getRegistrationFee()));
            feeText.setTextSize(14);
            feeText.setTextColor(getResources().getColor(R.color.button_primary_fall));
            feeText.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams feeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            feeParams.setMargins(0, 0, 0, 16);
            feeText.setLayoutParams(feeParams);
            eventLayout.addView(feeText);
        } else {
            TextView freeText = new TextView(this);
            freeText.setText("🆓 Free Event");
            freeText.setTextSize(13);
            freeText.setTextColor(getResources().getColor(R.color.text_secondary_fall));
            LinearLayout.LayoutParams freeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            freeParams.setMargins(0, 0, 0, 16);
            freeText.setLayoutParams(freeParams);
            eventLayout.addView(freeText);
        }
        
        // Action buttons
        LinearLayout buttonLayout = new LinearLayout(this);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonLayoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonLayoutParams.setMargins(0, 4, 0, 0);
        buttonLayout.setLayoutParams(buttonLayoutParams);
        
        Button attendButton = new Button(this);
        attendButton.setText("📝 Register");
        attendButton.setBackgroundColor(getResources().getColor(R.color.primary_fall));
        attendButton.setTextColor(getResources().getColor(R.color.text_on_fall));
        attendButton.setOnClickListener(v -> registerForEvent(event));
        LinearLayout.LayoutParams attendButtonParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        attendButtonParams.setMargins(0, 0, 8, 0);
        attendButton.setLayoutParams(attendButtonParams);
        attendButton.setPadding(16, 12, 16, 12);
        
        Button favoriteButton = new Button(this);
        favoriteButton.setText("❤️ Favorite");
        favoriteButton.setBackgroundColor(getResources().getColor(R.color.button_secondary_fall));
        favoriteButton.setTextColor(getResources().getColor(R.color.text_on_fall));
        favoriteButton.setOnClickListener(v -> addToFavorites(event));
        LinearLayout.LayoutParams favoriteButtonParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        favoriteButtonParams.setMargins(8, 0, 8, 0);
        favoriteButton.setLayoutParams(favoriteButtonParams);
        favoriteButton.setPadding(16, 12, 16, 12);
        
        Button calendarButton = new Button(this);
        calendarButton.setText("📅 Calendar");
        calendarButton.setBackgroundColor(getResources().getColor(R.color.button_secondary_fall));
        calendarButton.setTextColor(getResources().getColor(R.color.text_on_fall));
        calendarButton.setOnClickListener(v -> addToCalendar(event));
        LinearLayout.LayoutParams calendarButtonParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        calendarButtonParams.setMargins(8, 0, 0, 0);
        calendarButton.setLayoutParams(calendarButtonParams);
        calendarButton.setPadding(16, 12, 16, 12);
        
        buttonLayout.addView(attendButton);
        buttonLayout.addView(favoriteButton);
        buttonLayout.addView(calendarButton);
        eventLayout.addView(buttonLayout);
        
        // If showing registered events, add a Check-In button for attendance
        if ("REGISTERED".equals(currentFilter)) {
            Button checkInButton = new Button(this);
            checkInButton.setText("✅ Check In");
            checkInButton.setBackgroundColor(getResources().getColor(R.color.primary_fall));
            checkInButton.setTextColor(getResources().getColor(R.color.text_on_fall));
            checkInButton.setOnClickListener(v -> navigateToCheckIn(event));
            LinearLayout.LayoutParams checkInParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            checkInParams.setMargins(12, 8, 12, 0);
            checkInButton.setLayoutParams(checkInParams);
            checkInButton.setPadding(16, 12, 16, 12);
            eventLayout.addView(checkInButton);
        }
        
        return cardView;
    }

    /**
     * Registers the current user for a specific event.
     * 
     * <p>This method shows a confirmation dialog before making the registration
     * request. Upon confirmation, it sends a POST request to the backend endpoint
     * to register the user for the event.</p>
     * 
     * <p>Registration process:
     * <ul>
     *   <li>Shows confirmation dialog with event title</li>
     *   <li>Sends POST request to /api/events/{eventId}/register</li>
     *   <li>Includes username as query parameter</li>
     *   <li>Handles success and error responses</li>
     * </ul>
     * </p>
     * 
     * <p>Error handling includes specific messages for:
     * <ul>
     *   <li>400: Invalid request</li>
     *   <li>404: Event not found</li>
     *   <li>409: Already registered</li>
     *   <li>500: Server error</li>
     *   <li>Network timeouts and connection errors</li>
     * </ul>
     * </p>
     * 
     * @param event The Event object for which the user wants to register
     */
    private void registerForEvent(Event event) {
        android.util.Log.d("AllEventsActivity", "Registering for event: " + event.getTitle());
        
        // Check if payment is required
        if (event.requiresPayment()) {
            // Launch PaymentActivity
            Intent paymentIntent = new Intent(AllEventsActivity.this, com.example.occasio.payment.PaymentActivity.class);
            paymentIntent.putExtra("username", currentUsername);
            paymentIntent.putExtra("eventId", (long) event.getId());
            paymentIntent.putExtra("eventTitle", event.getTitle());
            paymentIntent.putExtra("amount", event.getRegistrationFee());
            startActivityForResult(paymentIntent, 1001); // Request code for payment
            return;
        }
        
        // Free event - proceed with normal registration
        new AlertDialog.Builder(this)
            .setTitle("Register for Event")
            .setMessage("Are you sure you want to register for \"" + event.getTitle() + "\"?")
            .setPositiveButton("Yes, Register", (dialog, which) -> {
                String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/events/" + event.getId() + "/register?username=" + currentUsername;
                
                // Use StringRequest instead of JsonObjectRequest since we're not sending JSON body
                com.android.volley.toolbox.StringRequest request = new com.android.volley.toolbox.StringRequest(
                    Request.Method.POST,
                    url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            android.util.Log.d("AllEventsActivity", "Successfully registered for event");
                            // Note: Reward points are awarded on check-in, not registration
                            InAppNotificationHelper.showNotification(AllEventsActivity.this, "✅ Success", "Successfully registered for event!");
                            // Refresh events list to update registration status
                            loadAllEvents();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            android.util.Log.e("AllEventsActivity", "Error registering", error);
                            String errorMessage = "Failed to register for event";
                            if (error.networkResponse != null) {
                                try {
                                    String responseBody = new String(error.networkResponse.data, "utf-8");
                                    android.util.Log.e("AllEventsActivity", "Error response body: " + responseBody);
                                    // Try to parse as JSON first
                                    try {
                                        JSONObject errorJson = new JSONObject(responseBody);
                                        errorMessage = errorJson.optString("message", errorJson.optString("error", errorMessage));
                                    } catch (org.json.JSONException e) {
                                        // If not JSON, check if it's a plain error message
                                        if (!responseBody.isEmpty() && responseBody.length() < 200) {
                                            errorMessage = responseBody;
                                        } else {
                                            errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                                        }
                                    }
                                } catch (Exception e) {
                                    android.util.Log.e("AllEventsActivity", "Error parsing error response", e);
                                    // Use status code for error message
                                    int statusCode = error.networkResponse.statusCode;
                                    switch (statusCode) {
                                        case 400:
                                            errorMessage = "Invalid request. Please check your information.";
                                            break;
                                        case 404:
                                            errorMessage = "Event not found.";
                                            break;
                                        case 409:
                                            errorMessage = "You are already registered for this event.";
                                            break;
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
                            InAppNotificationHelper.showNotification(AllEventsActivity.this, "❌ Error", errorMessage);
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

    /**
     * Adds an event to the user's favorites list.
     * 
     * <p>This method sends a POST request to the backend to add the specified
     * event to the current user's favorites. It handles both successful additions
     * and various error scenarios.</p>
     * 
     * <p>Favorite addition process:
     * <ul>
     *   <li>Sends POST request to /api/favorites/username/{username}/{eventId}</li>
     *   <li>Shows success toast on completion</li>
     *   <li>Handles duplicate favorite attempts (409 status)</li>
     * </ul>
     * </p>
     * 
     * <p>Error handling includes specific messages for:
     * <ul>
     *   <li>400: Invalid request</li>
     *   <li>404: Event or user not found</li>
     *   <li>409: Event already in favorites</li>
     *   <li>500: Server error</li>
     *   <li>Network connection issues</li>
     * </ul>
     * </p>
     * 
     * @param event The Event object to add to favorites
     */
    private void addToFavorites(Event event) {
        android.util.Log.d("AllEventsActivity", "Adding to favorites: " + event.getTitle());
        
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/favorites/username/" + currentUsername + "/" + event.getId();
        
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    android.util.Log.d("AllEventsActivity", "Successfully added to favorites");
                    InAppNotificationHelper.showNotification(AllEventsActivity.this, "❤️ Favorites", "Added \"" + event.getTitle() + "\" to favorites!");
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    android.util.Log.e("AllEventsActivity", "Error adding to favorites", error);
                    String errorMessage = "Failed to add to favorites";
                    
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
                                    // If not JSON, use status code
                                    switch (statusCode) {
                                        case 400:
                                            errorMessage = "Invalid request. Please check your information.";
                                            break;
                                        case 404:
                                            errorMessage = "Event or user not found.";
                                            break;
                                        case 409:
                                            errorMessage = "Event is already in your favorites.";
                                            break;
                                        case 500:
                                            errorMessage = "Server error. Please try again later.";
                                            break;
                                        default:
                                            errorMessage += " (Status: " + statusCode + ")";
                                            break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            android.util.Log.e("AllEventsActivity", "Error parsing error response", e);
                            switch (statusCode) {
                                case 400:
                                    errorMessage = "Invalid request. Please check your information.";
                                    break;
                                case 404:
                                    errorMessage = "Event or user not found.";
                                    break;
                                case 409:
                                    errorMessage = "Event is already in your favorites.";
                                    break;
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
                    
                    InAppNotificationHelper.showNotification(AllEventsActivity.this, "❌ Error", errorMessage);
                }
            }
        );
        
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }

    /**
     * Adds an event to the user's calendar.
     * 
     * <p>This method shows a dialog to optionally add personal notes and set reminder,
     * then sends a POST request to add the event to the calendar.</p>
     * 
     * @param event The Event object to add to calendar
     */
    private void addToCalendar(Event event) {
        android.util.Log.d("AllEventsActivity", "Adding to calendar: " + event.getTitle());
        
        // Show dialog for personal notes and reminder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add to Calendar");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_calendar_event, null);
        EditText notesEditText = dialogView.findViewById(R.id.dialog_notes_et);
        EditText reminderEditText = dialogView.findViewById(R.id.dialog_reminder_et);
        
        notesEditText.setHint("Add personal notes (optional)");
        reminderEditText.setText("15");

        builder.setView(dialogView);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String notes = notesEditText.getText().toString();
            int reminderMinutes = 15;
            try {
                reminderMinutes = Integer.parseInt(reminderEditText.getText().toString());
            } catch (NumberFormatException e) {
                // Use default
            }
            
            String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/calendar/username/" + currentUsername + "/add-event/" + event.getId();
            
            try {
                JSONObject requestBody = new JSONObject();
                if (!notes.isEmpty()) {
                    requestBody.put("personalNotes", notes);
                }
                requestBody.put("reminderMinutes", reminderMinutes);
                
                JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, requestBody,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            android.util.Log.d("AllEventsActivity", "Response from add to calendar: " + response.toString());
                            
                            // Check if event was already in calendar
                            if (response.has("alreadyAdded") && response.optBoolean("alreadyAdded", false)) {
                                InAppNotificationHelper.showNotification(AllEventsActivity.this, 
                                    "📅 Calendar", 
                                    "Already there");
                            } else if (response.has("message") && response.optString("message", "").toLowerCase().contains("already")) {
                                InAppNotificationHelper.showNotification(AllEventsActivity.this, 
                                    "📅 Calendar", 
                                    "Already there");
                            } else {
                                InAppNotificationHelper.showNotification(AllEventsActivity.this, 
                                    "✅ Success", 
                                    "Added \"" + event.getTitle() + "\" to calendar!");
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            android.util.Log.e("AllEventsActivity", "Error adding to calendar", error);
                            String errorMessage = "Failed to add to calendar";
                            
                            if (error.networkResponse != null) {
                                int statusCode = error.networkResponse.statusCode;
                                try {
                                    String responseBody = new String(error.networkResponse.data, "utf-8");
                                    android.util.Log.d("AllEventsActivity", "Error response body: " + responseBody);
                                    
                                    // Check if event is already in calendar
                                    if (statusCode == 400) {
                                        if (responseBody.contains("already in calendar") || 
                                            responseBody.contains("already") ||
                                            responseBody.toLowerCase().contains("already")) {
                                            InAppNotificationHelper.showNotification(AllEventsActivity.this, 
                                                "📅 Calendar", 
                                                "Already there");
                                            return;
                                        }
                                    }
                                    
                                    if (!responseBody.isEmpty()) {
                                        try {
                                            JSONObject errorJson = new JSONObject(responseBody);
                                            String errorMsg = errorJson.optString("message", errorJson.optString("error", ""));
                                            if (errorMsg.toLowerCase().contains("already")) {
                                                InAppNotificationHelper.showNotification(AllEventsActivity.this, 
                                                    "📅 Calendar", 
                                                    "Already there");
                                                return;
                                            }
                                            errorMessage = errorMsg.isEmpty() ? errorMessage : errorMsg;
                                        } catch (org.json.JSONException e) {
                                            switch (statusCode) {
                                                case 400:
                                                    errorMessage = "Event already in calendar or invalid request.";
                                                    break;
                                                case 404:
                                                    errorMessage = "User or event not found. Please make sure you're logged in correctly.";
                                                    break;
                                                case 500:
                                                    errorMessage = "Server error. Please try again later.";
                                                    break;
                                                default:
                                                    errorMessage += " (Status: " + statusCode + ")";
                                                    break;
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    errorMessage += " (Status: " + statusCode + ")";
                                }
                            }
                            
                            // Only show error toast if it's not an "already added" case
                            if (!errorMessage.toLowerCase().contains("already")) {
                                InAppNotificationHelper.showNotification(AllEventsActivity.this, "❌ Error", errorMessage);
                            }
                        }
                    }
                );
                
                request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
                requestQueue.add(request);
            } catch (Exception e) {
                InAppNotificationHelper.showNotification(AllEventsActivity.this, "❌ Error", "Error adding to calendar");
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    /**
     * Displays an error dialog with the option to copy the error message.
     * 
     * <p>This method creates an AlertDialog showing the error title and message.
     * It provides a "Copy Error" button that copies the error message to the
     * clipboard for easy sharing or debugging.</p>
     * 
     * @param title The title text to display in the error dialog
     * @param message The error message to display and optionally copy
     */
    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Copy Error", (dialog, which) -> {
                // Copy error to clipboard
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Error", message);
                clipboard.setPrimaryClip(clip);
                InAppNotificationHelper.showNotification(this, "📋 Clipboard", "Error copied to clipboard");
            })
            .setNegativeButton("OK", (dialog, which) -> dialog.dismiss())
            .show();
    }
    
    /**
     * Register for event after payment is completed
     */
    private void registerForEventAfterPayment(Event event) {
        android.util.Log.d("AllEventsActivity", "Registering for event after payment: " + event.getTitle());
        
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/events/" + event.getId() + "/register?username=" + currentUsername;
        
        com.android.volley.toolbox.StringRequest request = new com.android.volley.toolbox.StringRequest(
            Request.Method.POST,
            url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    android.util.Log.d("AllEventsActivity", "Successfully registered for event after payment");
                    InAppNotificationHelper.showNotification(AllEventsActivity.this, "✅ Success", "Successfully registered for event!");
                    // Refresh events list
                    loadAllEvents();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    android.util.Log.e("AllEventsActivity", "Error registering for event after payment", error);
                    String errorMessage = "Could not register for event";
                    if (error.networkResponse != null) {
                        errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                    }
                    InAppNotificationHelper.showNotification(AllEventsActivity.this, "❌ Error", errorMessage);
                }
            }
        );
        
        requestQueue.add(request);
    }
    
    /**
     * Updates the visual state of filter buttons based on current filter
     */
    private void updateFilterButtons() {
        if (filterAllButton != null) {
            if ("ALL".equals(currentFilter)) {
                filterAllButton.setBackgroundColor(getResources().getColor(R.color.primary_fall));
                filterAllButton.setTextColor(getResources().getColor(R.color.text_on_fall));
            } else {
                filterAllButton.setBackgroundColor(getResources().getColor(R.color.card_background_fall));
                filterAllButton.setTextColor(getResources().getColor(R.color.text_primary_fall));
            }
        }
        
        if (filterRegisteredButton != null) {
            if ("REGISTERED".equals(currentFilter)) {
                filterRegisteredButton.setBackgroundColor(getResources().getColor(R.color.primary_fall));
                filterRegisteredButton.setTextColor(getResources().getColor(R.color.text_on_fall));
            } else {
                filterRegisteredButton.setBackgroundColor(getResources().getColor(R.color.card_background_fall));
                filterRegisteredButton.setTextColor(getResources().getColor(R.color.text_primary_fall));
            }
        }
        
        if (filterFavoritesButton != null) {
            if ("FAVORITES".equals(currentFilter)) {
                filterFavoritesButton.setBackgroundColor(getResources().getColor(R.color.primary_fall));
                filterFavoritesButton.setTextColor(getResources().getColor(R.color.text_on_fall));
            } else {
                filterFavoritesButton.setBackgroundColor(getResources().getColor(R.color.card_background_fall));
                filterFavoritesButton.setTextColor(getResources().getColor(R.color.text_primary_fall));
            }
        }
    }
    
    /**
     * Filters events based on current filter selection
     */
    private void performFilter() {
        android.util.Log.d("AllEventsActivity", "Performing filter: " + currentFilter);
        
        if ("ALL".equals(currentFilter)) {
            // For ALL filter, use the already loaded eventsList
            if (eventsList == null || eventsList.isEmpty()) {
                // If events haven't been loaded yet, load them first
                android.util.Log.d("AllEventsActivity", "Events list is empty, loading all events first");
                loadAllEvents();
                return;
            }
            filteredEventsList.clear();
            filteredEventsList.addAll(eventsList);
            displayEvents();
        } else if ("REGISTERED".equals(currentFilter)) {
            // Load registered events from backend (these are events where user can check in for attendance)
            android.util.Log.d("AllEventsActivity", "Loading registered events from backend");
            loadRegisteredEvents();
        } else if ("FAVORITES".equals(currentFilter)) {
            // Load favorite events from backend
            android.util.Log.d("AllEventsActivity", "Loading favorite events from backend");
            loadFavoriteEvents();
        } else {
            // Unknown filter, default to ALL
            android.util.Log.w("AllEventsActivity", "Unknown filter: " + currentFilter + ", defaulting to ALL");
            currentFilter = "ALL";
            updateFilterButtons();
            performFilter();
        }
    }
    
    /**
     * Loads registered events from backend
     * These are events where the user is registered and can check in for attendance
     */
    private void loadRegisteredEvents() {
        String url = com.example.occasio.utils.ServerConfig.EVENTS_URL + "/user/" + currentUsername;
        android.util.Log.d("AllEventsActivity", "Loading registered events from: " + url);
        
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    android.util.Log.d("AllEventsActivity", "Registered events response received: " + response.length() + " events");
                    try {
                        filteredEventsList.clear();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject eventJson = response.getJSONObject(i);
                            Event event = parseEventFromJson(eventJson);
                            if (event != null) {
                                filteredEventsList.add(event);
                            }
                        }
                        android.util.Log.d("AllEventsActivity", "Parsed " + filteredEventsList.size() + " registered events");
                        displayEvents();
                        if (filteredEventsList.isEmpty()) {
                            InAppNotificationHelper.showNotification(AllEventsActivity.this, "📋 Events", "No registered events found");
                        }
                    } catch (Exception e) {
                        android.util.Log.e("AllEventsActivity", "Error parsing registered events", e);
                        e.printStackTrace();
                        filteredEventsList.clear();
                        displayEvents();
                        InAppNotificationHelper.showNotification(AllEventsActivity.this, "❌ Error", "Error loading registered events");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    android.util.Log.e("AllEventsActivity", "Error loading registered events", error);
                    if (error.networkResponse != null) {
                        android.util.Log.e("AllEventsActivity", "Status code: " + error.networkResponse.statusCode);
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            android.util.Log.e("AllEventsActivity", "Error response: " + responseBody);
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    filteredEventsList.clear();
                    displayEvents();
                    Toast.makeText(AllEventsActivity.this, "Error loading registered events", Toast.LENGTH_SHORT).show();
                }
            }
        );
        requestQueue.add(request);
    }
    
    /**
     * Loads favorite events from backend
     * Note: Favorites endpoint returns FavoriteEvent objects with nested "event" field
     */
    private void loadFavoriteEvents() {
        // Use the same endpoint as FavoritesActivity
        String url = com.example.occasio.utils.ServerConfig.BASE_URL + "/api/favorites/username/" + currentUsername;
        android.util.Log.d("AllEventsActivity", "Loading favorites from: " + url);
        
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    android.util.Log.d("AllEventsActivity", "Favorites response received: " + response.length() + " items");
                    try {
                        filteredEventsList.clear();
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
                            
                            Event event = parseEventFromJson(eventJson);
                            if (event != null) {
                                filteredEventsList.add(event);
                            }
                        }
                        android.util.Log.d("AllEventsActivity", "Parsed " + filteredEventsList.size() + " favorite events");
                        displayEvents();
                    } catch (Exception e) {
                        android.util.Log.e("AllEventsActivity", "Error parsing favorite events", e);
                        e.printStackTrace();
                        filteredEventsList.clear();
                        displayEvents();
                        InAppNotificationHelper.showNotification(AllEventsActivity.this, "❌ Error", "Error loading favorites");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    android.util.Log.e("AllEventsActivity", "Error loading favorite events", error);
                    if (error.networkResponse != null) {
                        android.util.Log.e("AllEventsActivity", "Status code: " + error.networkResponse.statusCode);
                    }
                    filteredEventsList.clear();
                    displayEvents();
                    Toast.makeText(AllEventsActivity.this, "Error loading favorites", Toast.LENGTH_SHORT).show();
                }
            }
        );
        requestQueue.add(request);
    }
    
    /**
     * Helper method to parse Event from JSON
     */
    private Event parseEventFromJson(JSONObject eventJson) throws Exception {
        Event event = new Event();
        event.setId((int) eventJson.getLong("id"));
        // Handle both "eventName" and "title" fields
        String eventName = eventJson.optString("eventName", "");
        if (eventName.isEmpty()) {
            eventName = eventJson.optString("title", "Unknown Event");
        }
        event.setTitle(eventName);
        event.setDescription(eventJson.optString("description", ""));
        event.setLocation(eventJson.optString("location", ""));
        
        String dateStr = eventJson.optString("startTime", eventJson.optString("date", ""));
        if (!dateStr.isEmpty()) {
            event.setStartTime(dateStr);
            event.setEndTime(eventJson.optString("endTime", dateStr));
        }
        
        if (eventJson.has("registrationFee") && !eventJson.isNull("registrationFee")) {
            double fee = eventJson.getDouble("registrationFee");
            if (fee > 0) {
                event.setRegistrationFee(fee);
            }
        }
        
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
        
        return event;
    }
    
    /**
     * Navigate to check-in activity for a registered event
     */
    private void navigateToCheckIn(Event event) {
        Intent checkInIntent = new Intent(AllEventsActivity.this, EventCheckInActivity.class);
        checkInIntent.putExtra("username", currentUsername);
        checkInIntent.putExtra("eventId", (long) event.getId());
        checkInIntent.putExtra("eventName", event.getTitle());
        startActivity(checkInIntent);
    }
    
    // Note: Chat WebSocket setup is now handled by BaseNavigationActivity
    // All pages that extend BaseNavigationActivity (except ChatActivity/GroupChatActivity) 
    // will automatically receive chat notifications
}