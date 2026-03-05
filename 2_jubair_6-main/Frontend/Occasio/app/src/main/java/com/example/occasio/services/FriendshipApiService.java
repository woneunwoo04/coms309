package com.example.occasio.services;

import android.content.Context;
import android.util.Log;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.example.occasio.api.VolleySingleton;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendshipApiService {
    private static final String TAG = "FriendshipApiService";
    private static final String BASE_URL = com.example.occasio.utils.ServerConfig.BASE_URL + "/api";
    private RequestQueue requestQueue;
    private Context context;

    public FriendshipApiService(Context context) {
        this.context = context;
        this.requestQueue = VolleySingleton.getInstance(context).getRequestQueue();
    }

    public interface FriendshipCallback {
        void onSuccess(JSONObject friendship);
        void onError(String error);
    }

    public interface FriendshipsListCallback {
        void onSuccess(List<JSONObject> friendships);
        void onError(String error);
    }

    public interface FriendsListCallback {
        void onSuccess(List<JSONObject> friends);
        void onError(String error);
    }

    public interface BooleanCallback {
        void onSuccess(boolean result);
        void onError(String error);
    }

    public interface VoidCallback {
        void onSuccess();
        void onError(String error);
    }

    /**
     * Accept a friend request
     * POST /api/friends/accept/{friendshipId}
     */
    public void acceptFriendRequest(Long friendshipId, FriendshipCallback callback) {
        String url = BASE_URL + "/friends/accept/" + friendshipId;

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        callback.onSuccess(response);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in accept callback: " + e.getMessage());
                        callback.onError("Error processing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMsg = "Error accepting friend request";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                    }
                    Log.e(TAG, errorMsg + ": " + error.getMessage());
                    callback.onError(errorMsg);
                }
            }
        );

        requestQueue.add(request);
    }

    /**
     * Reject a friend request
     * DELETE /api/friends/reject/{friendshipId}
     */
    public void rejectFriendRequest(Long friendshipId, VoidCallback callback) {
        String url = BASE_URL + "/friends/reject/" + friendshipId;

        StringRequest request = new StringRequest(
            Request.Method.DELETE,
            url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    callback.onSuccess();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMsg = "Error rejecting friend request";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                    }
                    Log.e(TAG, errorMsg + ": " + error.getMessage());
                    callback.onError(errorMsg);
                }
            }
        );

        requestQueue.add(request);
    }

    /**
     * Get pending friend requests for a user
     * GET /api/friends/pending/{username}
     */
    public void getPendingRequests(String username, FriendshipsListCallback callback) {
        String url = BASE_URL + "/friends/pending/" + username;

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<JSONObject> friendships = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            friendships.add(response.getJSONObject(i));
                        }
                        callback.onSuccess(friendships);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing pending requests: " + e.getMessage());
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMsg = "Error fetching pending requests";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                    }
                    Log.e(TAG, errorMsg + ": " + error.getMessage());
                    callback.onError(errorMsg);
                }
            }
        );

        requestQueue.add(request);
    }

    /**
     * Get sent friend requests by a user
     * GET /api/friends/sent/{username}
     */
    public void getSentRequests(String username, FriendshipsListCallback callback) {
        String url = BASE_URL + "/friends/sent/" + username;

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<JSONObject> friendships = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            friendships.add(response.getJSONObject(i));
                        }
                        callback.onSuccess(friendships);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing sent requests: " + e.getMessage());
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMsg = "Error fetching sent requests";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                    }
                    Log.e(TAG, errorMsg + ": " + error.getMessage());
                    callback.onError(errorMsg);
                }
            }
        );

        requestQueue.add(request);
    }

    /**
     * Check if two users are friends
     * GET /api/friends/check?username1={username1}&username2={username2}
     */
    public void checkFriendship(String username1, String username2, BooleanCallback callback) {
        try {
            String encodedUsername1 = java.net.URLEncoder.encode(username1, "UTF-8");
            String encodedUsername2 = java.net.URLEncoder.encode(username2, "UTF-8");
            String url = BASE_URL + "/friends/check?username1=" + encodedUsername1 + "&username2=" + encodedUsername2;

            JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            boolean areFriends = response.getBoolean("areFriends");
                            callback.onSuccess(areFriends);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing friendship check: " + e.getMessage());
                            callback.onError("Error parsing response");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMsg = "Error checking friendship";
                        if (error.networkResponse != null) {
                            errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                        }
                        Log.e(TAG, errorMsg + ": " + error.getMessage());
                        callback.onError(errorMsg);
                    }
                }
            );

            requestQueue.add(request);
        } catch (Exception e) {
            Log.e(TAG, "Error encoding usernames: " + e.getMessage());
            callback.onError("Error encoding request");
        }
    }

    /**
     * Get all friends of a user
     * GET /api/friends/{username}
     */
    public void getFriends(String username, FriendsListCallback callback) {
        String url = com.example.occasio.utils.ServerConfig.FRIENDS_URL + "/" + username;

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<JSONObject> friends = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            friends.add(response.getJSONObject(i));
                        }
                        callback.onSuccess(friends);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing friends: " + e.getMessage());
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMsg = "Error fetching friends";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                    }
                    Log.e(TAG, errorMsg + ": " + error.getMessage());
                    callback.onError(errorMsg);
                }
            }
        );

        requestQueue.add(request);
    }
}

