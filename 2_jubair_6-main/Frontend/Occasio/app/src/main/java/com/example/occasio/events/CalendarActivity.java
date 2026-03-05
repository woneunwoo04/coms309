package com.example.occasio.events;

import com.example.occasio.R;
import com.example.occasio.base.BaseNavigationActivity;
import com.example.occasio.utils.ServerConfig;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast; // Keep for now but replace all usage with InAppNotificationHelper
import com.example.occasio.utils.InAppNotificationHelper;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarActivity extends BaseNavigationActivity {
    
    private RecyclerView calendarGrid;
    private RecyclerView daysHeader;
    private TextView monthYearTextView;
    private TextView selectedDateTextView;
    private RecyclerView eventsRecyclerView;
    private Button backButton;
    private Button refreshButton;
    private Button prevMonthButton;
    private Button nextMonthButton;
    private Button filterAllButton;
    private Button filterRegisteredButton;
    private Button filterAttendedButton;
    private Button filterFavoritedButton;
    
    private String currentUsername;
    private List<CalendarEvent> allCalendarEvents;
    private List<CalendarEvent> filteredEvents;
    private List<CalendarEvent> selectedDateEvents;
    private CalendarAdapter eventsAdapter;
    private CalendarGridAdapter calendarGridAdapter;
    private RequestQueue requestQueue;
    private String currentFilter = "ALL";
    
    private Calendar currentMonth;
    private Calendar selectedDate;
    private Map<String, List<CalendarEvent>> eventsByDate;
    
    private static final String BASE_URL = ServerConfig.BASE_URL;
    private static final String CALENDAR_URL = BASE_URL + "/api/calendar/username/";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        android.util.Log.d("CalendarActivity", "onCreate started");

        // Initialize views
        calendarGrid = findViewById(R.id.calendar_grid);
        daysHeader = findViewById(R.id.calendar_days_header);
        monthYearTextView = findViewById(R.id.calendar_month_year_tv);
        selectedDateTextView = findViewById(R.id.calendar_selected_date_tv);
        eventsRecyclerView = findViewById(R.id.calendar_events_recycler_view);
        backButton = findViewById(R.id.calendar_back_btn);
        refreshButton = findViewById(R.id.calendar_refresh_btn);
        prevMonthButton = findViewById(R.id.calendar_prev_month_btn);
        nextMonthButton = findViewById(R.id.calendar_next_month_btn);
        filterAllButton = findViewById(R.id.calendar_filter_all_btn);
        filterRegisteredButton = findViewById(R.id.calendar_filter_registered_btn);
        filterAttendedButton = findViewById(R.id.calendar_filter_attended_btn);
        filterFavoritedButton = findViewById(R.id.calendar_filter_favorited_btn);

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
        
        android.util.Log.d("CalendarActivity", "Username received: " + currentUsername);

        if (currentUsername == null || currentUsername.isEmpty()) {
            android.util.Log.e("CalendarActivity", "No username provided");
            InAppNotificationHelper.showNotification(this, "⚠️ Error", "No username provided. Please log in again.");
            finish();
            return;
        }

        // Initialize data
        allCalendarEvents = new ArrayList<>();
        filteredEvents = new ArrayList<>();
        selectedDateEvents = new ArrayList<>();
        eventsByDate = new HashMap<>();
        requestQueue = Volley.newRequestQueue(this);
        
        // Initialize calendar
        currentMonth = Calendar.getInstance();
        selectedDate = Calendar.getInstance();
        
        // Setup days header
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        DaysHeaderAdapter daysHeaderAdapter = new DaysHeaderAdapter(dayNames);
        daysHeader.setLayoutManager(new GridLayoutManager(this, 7));
        daysHeader.setAdapter(daysHeaderAdapter);
        
        // Setup calendar grid
        calendarGridAdapter = new CalendarGridAdapter(new ArrayList<>(), this::onDateClick);
        calendarGrid.setLayoutManager(new GridLayoutManager(this, 7));
        calendarGrid.setAdapter(calendarGridAdapter);
        
        // Setup RecyclerView for events list
        eventsAdapter = new CalendarAdapter(selectedDateEvents, this::onEventEdit, this::onEventDelete);
        eventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        eventsRecyclerView.setAdapter(eventsAdapter);

        // Set up click listeners
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(CalendarActivity.this, AllEventsActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
            finish();
        });

        refreshButton.setOnClickListener(v -> {
            android.util.Log.d("CalendarActivity", "Refresh button clicked");
            loadCalendarEvents();
        });

        prevMonthButton.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, -1);
            updateCalendarView();
        });

        nextMonthButton.setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, 1);
            updateCalendarView();
        });

        filterAllButton.setOnClickListener(v -> {
            currentFilter = "ALL";
            updateFilterButtons();
            loadCalendarEvents(); // Reload from backend with new filter
        });

        filterRegisteredButton.setOnClickListener(v -> {
            currentFilter = "REGISTERED";
            updateFilterButtons();
            loadCalendarEvents(); // Reload from backend with new filter
        });

        filterAttendedButton.setOnClickListener(v -> {
            currentFilter = "ATTENDED";
            updateFilterButtons();
            loadCalendarEvents(); // Reload from backend with new filter
        });

        filterFavoritedButton.setOnClickListener(v -> {
            currentFilter = "FAVORITED";
            updateFilterButtons();
            loadCalendarEvents(); // Reload from backend with new filter
        });

        // Initialize filter buttons state
        updateFilterButtons();
        
        // Initialize calendar view
        updateCalendarView();
        updateSelectedDateEvents(selectedDate);

        // Load calendar events (with current filter)
        loadCalendarEvents();
    }

    private void updateFilterButtons() {
        // Reset all buttons
        filterAllButton.setAlpha(1.0f);
        filterRegisteredButton.setAlpha(1.0f);
        filterAttendedButton.setAlpha(1.0f);
        filterFavoritedButton.setAlpha(1.0f);
        
        // Highlight selected filter
        switch (currentFilter) {
            case "ALL":
                filterAllButton.setAlpha(0.7f);
                break;
            case "REGISTERED":
                filterRegisteredButton.setAlpha(0.7f);
                break;
            case "ATTENDED":
                filterAttendedButton.setAlpha(0.7f);
                break;
            case "FAVORITED":
                filterFavoritedButton.setAlpha(0.7f);
                break;
        }
    }

    private void filterEvents() {
        filteredEvents.clear();
        for (CalendarEvent event : allCalendarEvents) {
            if ("ALL".equals(currentFilter) || event.getEventType().equalsIgnoreCase(currentFilter)) {
                filteredEvents.add(event);
            }
        }
        organizeEventsByDate();
    }

    private void organizeEventsByDate() {
        eventsByDate.clear();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        
        for (CalendarEvent event : filteredEvents) {
            try {
                String dateStr = event.getStartTime();
                if (dateStr != null && !dateStr.isEmpty()) {
                    // Parse date from ISO format
                    String dateKey = dateStr.substring(0, 10); // Get YYYY-MM-DD part
                    if (!eventsByDate.containsKey(dateKey)) {
                        eventsByDate.put(dateKey, new ArrayList<>());
                    }
                    eventsByDate.get(dateKey).add(event);
                }
            } catch (Exception e) {
                android.util.Log.e("CalendarActivity", "Error parsing date: " + event.getStartTime(), e);
            }
        }
    }

    private void updateCalendarView() {
        // Update month/year text
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.US);
        monthYearTextView.setText(monthFormat.format(currentMonth.getTime()));
        
        // Get first day of month and number of days
        Calendar firstDay = (Calendar) currentMonth.clone();
        firstDay.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK);
        int daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        
        // Adjust for Sunday = 1 (Calendar.SUNDAY = 1, we want Sunday = 0)
        int startOffset = (firstDayOfWeek - 1) % 7;
        
        // Create list of calendar days
        List<CalendarDay> calendarDays = new ArrayList<>();
        
        // Add empty cells for days before month starts
        for (int i = 0; i < startOffset; i++) {
            calendarDays.add(new CalendarDay(null, false, false, false));
        }
        
        // Add day cells
        Calendar today = Calendar.getInstance();
        Calendar selected = (Calendar) selectedDate.clone();
        
        for (int day = 1; day <= daysInMonth; day++) {
            Calendar dayCalendar = (Calendar) currentMonth.clone();
            dayCalendar.set(Calendar.DAY_OF_MONTH, day);
            
            boolean isToday = isSameDay(dayCalendar, today);
            boolean isSelected = isSameDay(dayCalendar, selected);
            
            // Check if this day has events
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String dateKey = dateFormat.format(dayCalendar.getTime());
            boolean hasEvents = eventsByDate.containsKey(dateKey) && !eventsByDate.get(dateKey).isEmpty();
            
            calendarDays.add(new CalendarDay(dayCalendar, isToday, isSelected, hasEvents));
        }
        
        // Fill remaining cells to make 6 rows (42 cells total)
        int totalCells = calendarDays.size();
        int remainingCells = 42 - totalCells;
        for (int i = 0; i < remainingCells; i++) {
            calendarDays.add(new CalendarDay(null, false, false, false));
        }
        
        // Update adapter (ensure we're on main thread and adapter exists)
        if (calendarGridAdapter != null) {
            runOnUiThread(() -> {
                if (calendarGridAdapter != null && !isFinishing() && !isDestroyed()) {
                    calendarGridAdapter.updateDays(calendarDays);
                }
            });
        }
    }

    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) return false;
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    private void onDateClick(Calendar date) {
        if (date != null) {
            selectedDate = (Calendar) date.clone();
            updateCalendarView();
            updateSelectedDateEvents(selectedDate);
        }
    }

    private void updateSelectedDateEvents(Calendar date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String dateKey = dateFormat.format(date.getTime());
        
        selectedDateEvents.clear();
        List<CalendarEvent> events = eventsByDate.get(dateKey);
        if (events != null) {
            selectedDateEvents.addAll(events);
        }
        
        // Update selected date text
        SimpleDateFormat displayFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US);
        selectedDateTextView.setText(displayFormat.format(date.getTime()) + " (" + selectedDateEvents.size() + " events)");
        
        eventsAdapter.notifyDataSetChanged();
    }

    private void loadCalendarEvents() {
        android.util.Log.d("CalendarActivity", "Loading calendar events for: " + currentUsername + " with filter: " + currentFilter);
        
        String url;
        if ("ALL".equals(currentFilter)) {
            url = CALENDAR_URL + currentUsername;
        } else {
            url = CALENDAR_URL + currentUsername + "/filter?type=" + currentFilter;
        }
        
        android.util.Log.d("CalendarActivity", "Request URL: " + url);

        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    android.util.Log.d("CalendarActivity", "Response received: " + response.length() + " events");
                    android.util.Log.d("CalendarActivity", "Raw JSON: " + response.toString());
                    try {
                        allCalendarEvents.clear();
                        
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject eventJson = response.getJSONObject(i);
                            android.util.Log.d("CalendarActivity", "Event " + i + " JSON: " + eventJson.toString());
                            
                            CalendarEvent calendarEvent = new CalendarEvent();
                            calendarEvent.setId(eventJson.getLong("id"));
                            
                            // Parse title - check both title and eventName
                            String title = eventJson.optString("title", "");
                            if (title.isEmpty()) {
                                title = eventJson.optString("eventName", "Untitled Event");
                            }
                            calendarEvent.setTitle(title);
                            
                            calendarEvent.setDescription(eventJson.optString("description", ""));
                            calendarEvent.setLocation(eventJson.optString("location", ""));
                            calendarEvent.setPersonalNotes(eventJson.optString("personalNotes", ""));
                            
                            // Parse dates - handle both string and array formats
                            String startTimeStr = parseDateTime(eventJson, "startTime");
                            String endTimeStr = parseDateTime(eventJson, "endTime");
                            calendarEvent.setStartTime(startTimeStr);
                            calendarEvent.setEndTime(endTimeStr);
                            
                            // Parse event type
                            String eventTypeStr = eventJson.optString("eventType", "PERSONAL");
                            calendarEvent.setEventType(eventTypeStr);
                            
                            // Parse reminder
                            calendarEvent.setReminderMinutes(eventJson.optInt("reminderMinutes", 15));
                            calendarEvent.setReminderEnabled(eventJson.optBoolean("reminderEnabled", true));
                            
                            // Parse organization info from nested event object
                            String orgName = "Unknown Organization";
                            Long orgId = null;
                            
                            if (eventJson.has("event") && !eventJson.isNull("event")) {
                                try {
                                    JSONObject eventObj = eventJson.getJSONObject("event");
                                    if (eventObj.has("organization") && !eventObj.isNull("organization")) {
                                        JSONObject orgObj = eventObj.getJSONObject("organization");
                                        orgName = orgObj.optString("orgName", "Unknown Organization");
                                        orgId = orgObj.optLong("id", 0);
                                    } else {
                                        // Try direct organization fields
                                        orgName = eventObj.optString("organizationName", "Unknown Organization");
                                        orgId = eventObj.optLong("organizationId", 0);
                                    }
                                } catch (Exception e) {
                                    android.util.Log.e("CalendarActivity", "Error parsing organization from event", e);
                                }
                            } else {
                                // Try direct organization fields on CalendarEvent
                                if (eventJson.has("organization") && !eventJson.isNull("organization")) {
                                    try {
                                        JSONObject orgObj = eventJson.getJSONObject("organization");
                                        orgName = orgObj.optString("orgName", "Unknown Organization");
                                        orgId = orgObj.optLong("id", 0);
                                    } catch (Exception e) {
                                        // Try as string
                                        orgName = eventJson.optString("organization", "Unknown Organization");
                                    }
                                } else {
                                    // Try direct organizationName field
                                    orgName = eventJson.optString("organizationName", "Unknown Organization");
                                    orgId = eventJson.optLong("organizationId", 0);
                                }
                            }
                            
                            calendarEvent.setOrganizationName(orgName);
                            calendarEvent.setOrganizationId(orgId);
                            
                            android.util.Log.d("CalendarActivity", "Parsed event: " + calendarEvent.getTitle() + 
                                " at " + calendarEvent.getStartTime() + " by " + orgName);
                            
                            allCalendarEvents.add(calendarEvent);
                        }
                        
                        android.util.Log.d("CalendarActivity", "Parsed " + allCalendarEvents.size() + " calendar events");
                        // Events are already filtered by backend, so use them directly
                        filteredEvents.clear();
                        filteredEvents.addAll(allCalendarEvents);
                        organizeEventsByDate();
                        updateCalendarView();
                        
                        // Update selected date's events after reloading
                        if (selectedDate != null) {
                            updateSelectedDateEvents(selectedDate);
                        }
                        
                        if (allCalendarEvents.isEmpty()) {
                            String filterMsg = "ALL".equals(currentFilter) ? "" : " (" + currentFilter + " filter)";
                            InAppNotificationHelper.showNotification(CalendarActivity.this, "📅 Calendar", "No calendar events found" + filterMsg + "!");
                        } else {
                            String filterMsg = "ALL".equals(currentFilter) ? "" : " (" + currentFilter + " filter)";
                            android.util.Log.d("CalendarActivity", "Loaded " + allCalendarEvents.size() + " events" + filterMsg);
                        }
                        
                    } catch (Exception e) {
                        android.util.Log.e("CalendarActivity", "Error parsing response", e);
                        showErrorDialog("Error parsing calendar data", e.getMessage());
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    android.util.Log.e("CalendarActivity", "Network error for filter: " + currentFilter, error);
                    String errorMessage = "Network error: " + (error.getMessage() != null ? error.getMessage() : "Unknown error");
                    
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        android.util.Log.e("CalendarActivity", "Error status code: " + statusCode + " for filter: " + currentFilter);
                        
                        // Try to get more specific error message
                        try {
                            String responseBody = new String(error.networkResponse.data, "utf-8");
                            android.util.Log.e("CalendarActivity", "Error response body: " + responseBody);
                            if (!responseBody.isEmpty()) {
                                try {
                                    org.json.JSONObject errorJson = new org.json.JSONObject(responseBody);
                                    String errorMsg = errorJson.optString("message", errorJson.optString("error", ""));
                                    if (!errorMsg.isEmpty()) {
                                        errorMessage = errorMsg;
                                    }
                                } catch (org.json.JSONException e) {
                                    // Use default error message
                                }
                            }
                        } catch (Exception e) {
                            android.util.Log.e("CalendarActivity", "Error parsing error response", e);
                        }
                        
                        // For 400 errors, show a more user-friendly message
                        if (statusCode == 400) {
                            errorMessage = "Invalid filter '" + currentFilter + "'. Please try again.";
                        } else if (statusCode == 404) {
                            errorMessage = "No events found for " + currentFilter + " filter.";
                        } else if (statusCode == 500) {
                            errorMessage = "Server error. Please try again later.";
                        }
                    }
                    
                    InAppNotificationHelper.showNotification(CalendarActivity.this, "❌ Error", errorMessage);
                    
                    // Clear list on error but don't show error dialog
                    allCalendarEvents.clear();
                    filteredEvents.clear();
                    eventsByDate.clear();
                    updateCalendarView();
                    
                    // Update selected date's events to show empty list
                    if (selectedDate != null) {
                        updateSelectedDateEvents(selectedDate);
                    }
                }
            }
        );

        requestQueue.add(request);
    }

    private void onEventEdit(CalendarEvent event) {
        // Show dialog to edit event
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Calendar Event");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_calendar_event, null);
        EditText notesEditText = dialogView.findViewById(R.id.dialog_notes_et);
        EditText reminderEditText = dialogView.findViewById(R.id.dialog_reminder_et);
        
        notesEditText.setText(event.getPersonalNotes());
        reminderEditText.setText(String.valueOf(event.getReminderMinutes()));

        builder.setView(dialogView);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String notes = notesEditText.getText().toString();
            int reminderMinutes = 15;
            try {
                reminderMinutes = Integer.parseInt(reminderEditText.getText().toString());
            } catch (NumberFormatException e) {
                // Use default
            }
            
            updateCalendarEvent(event.getId(), notes, reminderMinutes);
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void onEventDelete(CalendarEvent event) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Calendar Event")
            .setMessage("Are you sure you want to delete \"" + event.getTitle() + "\" from your calendar?")
            .setPositiveButton("Yes, Delete", (dialog, which) -> {
                deleteCalendarEvent(event.getId());
            })
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .show();
    }

    private void updateCalendarEvent(Long eventId, String personalNotes, int reminderMinutes) {
        String url = BASE_URL + "/api/calendar/" + eventId;
        
        try {
            JSONObject requestBody = new JSONObject();
            if (!personalNotes.isEmpty()) {
                requestBody.put("personalNotes", personalNotes);
            }
            requestBody.put("reminderMinutes", reminderMinutes);
            
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.PUT, url, requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        InAppNotificationHelper.showNotification(CalendarActivity.this, "✅ Success", "Calendar event updated!");
                        // Reload calendar events - this will automatically update the view
                        loadCalendarEvents();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        InAppNotificationHelper.showNotification(CalendarActivity.this, "❌ Error", "Failed to update calendar event");
                    }
                }
            );
            
            requestQueue.add(request);
        } catch (Exception e) {
            InAppNotificationHelper.showNotification(this, "❌ Error", "Error updating calendar event");
        }
    }

    private void deleteCalendarEvent(Long eventId) {
        String url = BASE_URL + "/api/calendar/" + eventId;
        
        com.android.volley.toolbox.StringRequest request = new com.android.volley.toolbox.StringRequest(
            Request.Method.DELETE,
            url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    InAppNotificationHelper.showNotification(CalendarActivity.this, "✅ Success", "Calendar event deleted!");
                    // Reload calendar events - this will automatically update the view
                    loadCalendarEvents();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    InAppNotificationHelper.showNotification(CalendarActivity.this, "❌ Error", "Failed to delete calendar event");
                }
            }
        );
        
        requestQueue.add(request);
    }

    private String parseDateTime(JSONObject json, String fieldName) {
        try {
            // Try string format first
            if (json.has(fieldName) && !json.isNull(fieldName)) {
                Object dateValue = json.get(fieldName);
                
                if (dateValue instanceof String) {
                    return (String) dateValue;
                } else if (dateValue instanceof org.json.JSONArray) {
                    // Handle LocalDateTime array format: [year, month, day, hour, minute, second]
                    // Note: LocalDateTime uses 1-based months (1-12)
                    org.json.JSONArray dateArray = (org.json.JSONArray) dateValue;
                    if (dateArray.length() >= 6) {
                        int year = dateArray.getInt(0);
                        int month = dateArray.getInt(1); // Already 1-based from LocalDateTime
                        int day = dateArray.getInt(2);
                        int hour = dateArray.getInt(3);
                        int minute = dateArray.getInt(4);
                        int second = dateArray.length() > 5 ? dateArray.getInt(5) : 0;
                        
                        // Format as ISO 8601 string
                        String formatted = String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d:%02d", 
                            year, month, day, hour, minute, second);
                        android.util.Log.d("CalendarActivity", "Parsed array date: " + formatted);
                        return formatted;
                    }
                } else if (dateValue instanceof org.json.JSONObject) {
                    // Handle object format if needed
                    org.json.JSONObject dateObj = (org.json.JSONObject) dateValue;
                    if (dateObj.has("year") && dateObj.has("monthValue")) {
                        int year = dateObj.getInt("year");
                        int month = dateObj.getInt("monthValue"); // monthValue is 1-based
                        int day = dateObj.getInt("dayOfMonth");
                        int hour = dateObj.optInt("hour", 0);
                        int minute = dateObj.optInt("minute", 0);
                        int second = dateObj.optInt("second", 0);
                        
                        String formatted = String.format(Locale.US, "%04d-%02d-%02dT%02d:%02d:%02d", 
                            year, month, day, hour, minute, second);
                        android.util.Log.d("CalendarActivity", "Parsed object date: " + formatted);
                        return formatted;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("CalendarActivity", "Error parsing date field " + fieldName, e);
        }
        return "";
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .show();
    }

    // Calendar day data model
    private static class CalendarDay {
        Calendar date;
        boolean isToday;
        boolean isSelected;
        boolean hasEvents;
        
        CalendarDay(Calendar date, boolean isToday, boolean isSelected, boolean hasEvents) {
            this.date = date;
            this.isToday = isToday;
            this.isSelected = isSelected;
            this.hasEvents = hasEvents;
        }
    }

    // Days header adapter
    private static class DaysHeaderAdapter extends RecyclerView.Adapter<DaysHeaderAdapter.ViewHolder> {
        private String[] dayNames;
        
        DaysHeaderAdapter(String[] dayNames) {
            this.dayNames = dayNames;
        }
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView textView = new TextView(parent.getContext());
            textView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            textView.setPadding(8, 8, 8, 8);
            textView.setGravity(android.view.Gravity.CENTER);
            textView.setTextSize(12);
            textView.setTextColor(parent.getContext().getResources().getColor(R.color.text_secondary_fall));
            textView.setTypeface(null, android.graphics.Typeface.BOLD);
            return new ViewHolder(textView);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.textView.setText(dayNames[position]);
        }
        
        @Override
        public int getItemCount() {
            return dayNames.length;
        }
        
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            
            ViewHolder(TextView textView) {
                super(textView);
                this.textView = textView;
            }
        }
    }

    // Interface for date click listener
    interface OnDateClickListener {
        void onDateClick(Calendar date);
    }

    // Calendar grid adapter
    private class CalendarGridAdapter extends RecyclerView.Adapter<CalendarGridAdapter.ViewHolder> {
        private List<CalendarDay> days;
        private OnDateClickListener dateClickListener;
        
        CalendarGridAdapter(List<CalendarDay> days, OnDateClickListener listener) {
            this.days = new ArrayList<>(days);
            this.dateClickListener = listener;
        }
        
        void updateDays(List<CalendarDay> newDays) {
            this.days = new ArrayList<>(newDays);
            notifyDataSetChanged();
        }
        
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
            return new ViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            CalendarDay day = days.get(position);
            
            if (day.date == null) {
                // Empty cell
                holder.dayNumberTextView.setText("");
                holder.dayNumberTextView.setVisibility(View.INVISIBLE);
                holder.eventsIndicator.setVisibility(View.GONE);
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                holder.itemView.setClickable(false);
            } else {
                // Day cell
                holder.dayNumberTextView.setText(String.valueOf(day.date.get(Calendar.DAY_OF_MONTH)));
                holder.dayNumberTextView.setVisibility(View.VISIBLE);
                
                // Show event indicator
                if (day.hasEvents) {
                    holder.eventsIndicator.setVisibility(View.VISIBLE);
                    // Show up to 3 dots
                    int eventCount = Math.min(eventsByDate.get(
                        new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day.date.getTime())).size(), 3);
                    for (int i = 0; i < 3; i++) {
                        View dot = holder.itemView.findViewById(
                            i == 0 ? R.id.calendar_day_event_dot1 :
                            i == 1 ? R.id.calendar_day_event_dot2 : R.id.calendar_day_event_dot3);
                        dot.setVisibility(i < eventCount ? View.VISIBLE : View.GONE);
                    }
                } else {
                    holder.eventsIndicator.setVisibility(View.GONE);
                }
                
                // Style based on state
                if (day.isSelected) {
                    holder.itemView.setBackgroundResource(R.drawable.calendar_day_selected);
                    holder.dayNumberTextView.setTextColor(getResources().getColor(R.color.text_on_fall));
                } else if (day.isToday) {
                    holder.itemView.setBackgroundResource(R.drawable.calendar_day_today);
                    holder.dayNumberTextView.setTextColor(getResources().getColor(R.color.text_primary_fall));
                } else {
                    holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                    holder.dayNumberTextView.setTextColor(getResources().getColor(R.color.text_primary_fall));
                }
                
                holder.itemView.setClickable(true);
                holder.itemView.setOnClickListener(v -> {
                    if (dateClickListener != null) {
                        dateClickListener.onDateClick(day.date);
                    }
                });
            }
        }
        
        @Override
        public int getItemCount() {
            return days.size();
        }
        
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView dayNumberTextView;
            LinearLayout eventsIndicator;
            
            ViewHolder(View itemView) {
                super(itemView);
                dayNumberTextView = itemView.findViewById(R.id.calendar_day_number_tv);
                eventsIndicator = itemView.findViewById(R.id.calendar_day_events_indicator);
            }
        }
    }

    // Inner class for calendar event data model
    public static class CalendarEvent {
        private Long id;
        private String title;
        private String description;
        private String location;
        private String startTime;
        private String endTime;
        private String eventType;
        private String personalNotes;
        private int reminderMinutes;
        private boolean reminderEnabled;
        private String organizationName;
        private Long organizationId;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getPersonalNotes() { return personalNotes; }
        public void setPersonalNotes(String personalNotes) { this.personalNotes = personalNotes; }
        public int getReminderMinutes() { return reminderMinutes; }
        public void setReminderMinutes(int reminderMinutes) { this.reminderMinutes = reminderMinutes; }
        public boolean isReminderEnabled() { return reminderEnabled; }
        public void setReminderEnabled(boolean reminderEnabled) { this.reminderEnabled = reminderEnabled; }
        public String getOrganizationName() { return organizationName; }
        public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }
        public Long getOrganizationId() { return organizationId; }
        public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    }
}
