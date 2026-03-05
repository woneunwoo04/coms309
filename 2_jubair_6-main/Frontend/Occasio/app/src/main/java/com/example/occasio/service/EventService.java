package com.example.occasio.service;
import com.example.occasio.R;

import android.content.Context;
import android.util.Log;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.api.VolleySingleton;
import com.example.occasio.model.Event;
import com.example.occasio.utils.ApiUtils;
import com.example.occasio.utils.ServerConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class EventService {
    private static final String TAG = "EventService";
    private static final String BASE_URL = ServerConfig.BASE_URL;
    
    // API Endpoints
    private static final String GET_ALL_EVENTS_URL = BASE_URL + "/api/events";
    private static final String GET_EVENT_BY_ID_URL = BASE_URL + "/api/events/";
    private static final String CREATE_EVENT_URL = BASE_URL + "/api/events";
    private static final String UPDATE_EVENT_URL = BASE_URL + "/api/events/";
    private static final String DELETE_EVENT_URL = BASE_URL + "/api/events/";
    private static final String GET_ORGANIZATION_EVENTS_URL = BASE_URL + "/api/events/organization/";
    private static final String GET_USER_EVENTS_URL = BASE_URL + "/api/events/user/";
    private static final String ATTEND_EVENT_URL = BASE_URL + "/api/attendance/register";
    private static final String UNATTEND_EVENT_URL = BASE_URL + "/api/attendance/unregister";
    private static final String FAVORITE_EVENT_URL = BASE_URL + "/api/attendance/favorites/";
    private static final String UNFAVORITE_EVENT_URL = BASE_URL + "/api/events/unfavorite/";
    private static final String SEARCH_EVENTS_URL = BASE_URL + "/api/events/search";

    private RequestQueue requestQueue;
    private Context context;

    public EventService(Context context) {
        this.context = context;
        this.requestQueue = VolleySingleton.getInstance(context).getRequestQueue();
    }

    // Callback interfaces
    public interface EventListCallback {
        void onSuccess(List<Event> events);
        void onError(String error);
    }

    public interface EventCallback {
        void onSuccess(Event event);
        void onError(String error);
    }

    public interface BooleanCallback {
        void onSuccess(boolean success, String message);
        void onError(String error);
    }

    /**
     * Get all public events
     */
    public void getAllEvents(EventListCallback callback) {
        if (!ApiUtils.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            GET_ALL_EVENTS_URL,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        List<Event> events = parseEventsFromResponse(response);
                        callback.onSuccess(events);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing events response", e);
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching events", error);
                    callback.onError(ApiUtils.getErrorMessage(error.networkResponse != null ? 
                        error.networkResponse.statusCode : 0));
                }
            }
        );

        requestQueue.add(request);
    }

    /**
     * Get events by organization
     */
    public void getEventsByOrganization(String organizationId, EventListCallback callback) {
        if (!ApiUtils.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        String url = GET_ORGANIZATION_EVENTS_URL + organizationId;
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        List<Event> events = parseEventsFromResponse(response);
                        callback.onSuccess(events);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing organization events response", e);
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching organization events", error);
                    callback.onError(ApiUtils.getErrorMessage(error.networkResponse != null ? 
                        error.networkResponse.statusCode : 0));
                }
            }
        );

        requestQueue.add(request);
    }

    /**
     * Create a new event
     */
    public void createEvent(Event event, EventCallback callback) {
        if (!ApiUtils.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        try {
            JSONObject eventJson = eventToJson(event);
            JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                CREATE_EVENT_URL,
                eventJson,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            boolean success = response.getBoolean("success");
                            if (success) {
                                Event createdEvent = parseEventFromResponse(response.getJSONObject("data"));
                                callback.onSuccess(createdEvent);
                            } else {
                                callback.onError(response.getString("message"));
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing create event response", e);
                            callback.onError("Error parsing response");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Error creating event", error);
                        callback.onError(ApiUtils.getErrorMessage(error.networkResponse != null ? 
                            error.networkResponse.statusCode : 0));
                    }
                }
            );

            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating event JSON", e);
            callback.onError("Error creating event data");
        }
    }

    /**
     * Update an existing event
     */
    public void updateEvent(int eventId, Event event, EventCallback callback) {
        if (!ApiUtils.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        try {
            JSONObject eventJson = eventToJson(event);
            String url = UPDATE_EVENT_URL + eventId;
            JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                eventJson,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            boolean success = response.getBoolean("success");
                            if (success) {
                                Event updatedEvent = parseEventFromResponse(response.getJSONObject("data"));
                                callback.onSuccess(updatedEvent);
                            } else {
                                callback.onError(response.getString("message"));
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing update event response", e);
                            callback.onError("Error parsing response");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Error updating event", error);
                        callback.onError(ApiUtils.getErrorMessage(error.networkResponse != null ? 
                            error.networkResponse.statusCode : 0));
                    }
                }
            );

            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating update event JSON", e);
            callback.onError("Error creating event data");
        }
    }

    /**
     * Delete an event
     */
    public void deleteEvent(int eventId, BooleanCallback callback) {
        if (!ApiUtils.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        String url = DELETE_EVENT_URL + eventId;
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.DELETE,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        boolean success = response.getBoolean("success");
                        String message = response.optString("message", "Event deleted successfully");
                        callback.onSuccess(success, message);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing delete event response", e);
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error deleting event", error);
                    callback.onError(ApiUtils.getErrorMessage(error.networkResponse != null ? 
                        error.networkResponse.statusCode : 0));
                }
            }
        );

        requestQueue.add(request);
    }

    /**
     * Search events by query
     */
    public void searchEvents(String query, EventListCallback callback) {
        if (!ApiUtils.isNetworkAvailable(context)) {
            callback.onError("No internet connection");
            return;
        }

        String url = SEARCH_EVENTS_URL + "?q=" + query;
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        List<Event> events = parseEventsFromResponse(response);
                        callback.onSuccess(events);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing search events response", e);
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error searching events", error);
                    callback.onError(ApiUtils.getErrorMessage(error.networkResponse != null ? 
                        error.networkResponse.statusCode : 0));
                }
            }
        );

        requestQueue.add(request);
    }

    // Helper methods
    private List<Event> parseEventsFromResponse(JSONObject response) throws JSONException {
        List<Event> events = new ArrayList<>();
        
        if (response.has("data")) {
            JSONArray eventsArray = response.getJSONArray("data");
            for (int i = 0; i < eventsArray.length(); i++) {
                JSONObject eventJson = eventsArray.getJSONObject(i);
                Event event = parseEventFromResponse(eventJson);
                events.add(event);
            }
        }
        
        return events;
    }

    private Event parseEventFromResponse(JSONObject eventJson) throws JSONException {
        Event event = new Event();
        event.setId(eventJson.optInt("id", 0));
        event.setTitle(eventJson.optString("title", ""));
        event.setDescription(eventJson.optString("description", ""));
        event.setLocation(eventJson.optString("location", ""));
        event.setStartTime(eventJson.optString("startTime", ""));
        event.setEndTime(eventJson.optString("endTime", ""));
        event.setEventType(eventJson.optString("eventType", ""));
        event.setOrganizerId(eventJson.optString("organizerId", ""));
        event.setOrganizerName(eventJson.optString("organizerName", ""));
        event.setMaxAttendees(eventJson.optInt("maxAttendees", 0));
        event.setCurrentAttendees(eventJson.optInt("currentAttendees", 0));
        event.setPublic(eventJson.optBoolean("isPublic", true));
        event.setImageUrl(eventJson.optString("imageUrl", ""));
        event.setCategory(eventJson.optString("category", ""));
        event.setTags(eventJson.optString("tags", ""));
        event.setLatitude(eventJson.optDouble("latitude", 0.0));
        event.setLongitude(eventJson.optDouble("longitude", 0.0));
        
        return event;
    }

    private JSONObject eventToJson(Event event) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("title", event.getTitle());
        json.put("description", event.getDescription());
        json.put("location", event.getLocation());
        json.put("startTime", event.getStartTime());
        json.put("endTime", event.getEndTime());
        json.put("eventType", event.getEventType());
        json.put("organizerId", event.getOrganizerId());
        json.put("organizerName", event.getOrganizerName());
        json.put("maxAttendees", event.getMaxAttendees());
        json.put("isPublic", event.isPublic());
        json.put("imageUrl", event.getImageUrl());
        json.put("category", event.getCategory());
        json.put("tags", event.getTags());
        json.put("latitude", event.getLatitude());
        json.put("longitude", event.getLongitude());
        
        return json;
    }
}
