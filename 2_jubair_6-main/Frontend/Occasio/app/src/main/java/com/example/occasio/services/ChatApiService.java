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
import com.android.volley.toolbox.Volley;
import com.example.occasio.api.VolleySingleton;
import com.example.occasio.messaging.Message;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatApiService {
    private static final String TAG = "ChatApiService";
    private static final String BASE_URL = com.example.occasio.utils.ServerConfig.BASE_URL + "/api";
    private RequestQueue requestQueue;
    private Context context;

    public ChatApiService(Context context) {
        this.context = context;
        this.requestQueue = VolleySingleton.getInstance(context).getRequestQueue();
    }

    public interface MessageCallback {
        void onSuccess(Message message);
        void onError(String error);
    }

    public interface MessagesListCallback {
        void onSuccess(List<Message> messages);
        void onError(String error);
    }

    public interface ChatCallback {
        void onSuccess(Long chatId);
        void onError(String error);
    }

    public interface SearchCallback {
        void onSuccess(List<Message> messages);
        void onError(String error);
    }

    public interface UserIdCallback {
        void onSuccess(Long userId);
        void onError(String error);
    }

    public interface VoidCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface LongCallback {
        void onSuccess(Long value);
        void onError(String error);
    }

    public interface StringCallback {
        void onSuccess(String value);
        void onError(String error);
    }

    public interface ChatObjectCallback {
        void onSuccess(JSONObject chat);
        void onError(String error);
    }

    public interface ChatsListCallback {
        void onSuccess(List<JSONObject> chats);
        void onError(String error);
    }

    public interface AttachmentsListCallback {
        void onSuccess(List<JSONObject> attachments);
        void onError(String error);
    }

    public interface BytesCallback {
        void onSuccess(byte[] data);
        void onError(String error);
    }

    public interface MapCallback {
        void onSuccess(Map<String, Object> data);
        void onError(String error);
    }

    public void sendMessage(Long chatId, Long senderId, String content, Long replyToId, MessageCallback callback) {
        String url = BASE_URL + "/messages/send";
        
        Log.d(TAG, "Sending message - URL: " + url + ", chatId: " + chatId + ", senderId: " + senderId + ", content: " + content);
        
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("chatId", chatId);
            requestBody.put("senderId", senderId);
            requestBody.put("content", content);
            if (replyToId != null) {
                requestBody.put("replyToId", replyToId);
            }
            Log.d(TAG, "Request body: " + requestBody.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body: " + e.getMessage());
            callback.onError("Error creating request: " + e.getMessage());
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            requestBody,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Log.d(TAG, "Message sent successfully - Response: " + response.toString());
                        // ✅ Pass senderId to parseMessageFromJson to correctly set isSentByUser
                        Message message = parseMessageFromJson(response, senderId);
                        if (message != null) {
                        callback.onSuccess(message);
                        } else {
                            Log.e(TAG, "Failed to parse message from response");
                            callback.onError("Error parsing response: message is null");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing response: " + e.getMessage(), e);
                        callback.onError("Error parsing response: " + e.getMessage());
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMsg = "Error sending message";
                    if (error.networkResponse != null) {
                        errorMsg += " - Status: " + error.networkResponse.statusCode;
                        try {
                            String responseBody = new String(error.networkResponse.data, "UTF-8");
                            errorMsg += ", Response: " + responseBody;
                            Log.e(TAG, "Error response body: " + responseBody);
                        } catch (Exception e) {
                            Log.e(TAG, "Error reading error response: " + e.getMessage());
                        }
                    } else {
                        errorMsg += " - " + (error.getMessage() != null ? error.getMessage() : "Unknown error");
                    }
                    Log.e(TAG, errorMsg);
                    callback.onError(errorMsg);
                }
            }
        );

        requestQueue.add(request);
    }

    public void sendFileMessage(Long chatId, Long senderId, android.net.Uri fileUri, String caption, Long replyToId, MessageCallback callback) {
        new Thread(() -> {
            try {
                String url = BASE_URL + "/messages/send-file";
                java.net.URL urlObj = new java.net.URL(url);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) urlObj.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setDoInput(true);
                
                String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                
                java.io.OutputStream outputStream = connection.getOutputStream();
                java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(outputStream, "UTF-8"), true);
                
                writer.append("--" + boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"chatId\"").append("\r\n");
                writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n").append("\r\n");
                writer.append(String.valueOf(chatId)).append("\r\n").flush();
                
                writer.append("--" + boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"senderId\"").append("\r\n");
                writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n").append("\r\n");
                writer.append(String.valueOf(senderId)).append("\r\n").flush();
                
                if (caption != null && !caption.isEmpty()) {
                    writer.append("--" + boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"caption\"").append("\r\n");
                    writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n").append("\r\n");
                    writer.append(caption).append("\r\n").flush();
                }
                
                if (replyToId != null) {
                    writer.append("--" + boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"replyToId\"").append("\r\n");
                    writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n").append("\r\n");
                    writer.append(String.valueOf(replyToId)).append("\r\n").flush();
                }
                
                android.content.ContentResolver contentResolver = context.getContentResolver();
                String fileName = "file";
                String mimeType = contentResolver.getType(fileUri);
                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }
                
                // ✅ Extract actual filename from URI - handle both content:// and file:// schemes
                if (fileUri.getScheme() != null && fileUri.getScheme().equals("content")) {
                    try (android.database.Cursor cursor = contentResolver.query(fileUri, null, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            // Try DISPLAY_NAME first
                            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            if (nameIndex >= 0) {
                                String displayName = cursor.getString(nameIndex);
                                if (displayName != null && !displayName.isEmpty()) {
                                    fileName = displayName;
                                }
                            }
                            
                            // If DISPLAY_NAME not found, try MediaStore columns
                            if (fileName == null || fileName.equals("file")) {
                                int dataIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA);
                                if (dataIndex >= 0) {
                                    String dataPath = cursor.getString(dataIndex);
                                    if (dataPath != null && !dataPath.isEmpty()) {
                                        int lastSlash = dataPath.lastIndexOf('/');
                                        if (lastSlash != -1 && lastSlash < dataPath.length() - 1) {
                                            fileName = dataPath.substring(lastSlash + 1);
                                        }
                                    }
                                }
                            }
                            
                            // Also get MIME type from cursor if available
                            if (mimeType == null || mimeType.equals("application/octet-stream")) {
                                int mimeIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE);
                                if (mimeIndex >= 0) {
                                    String cursorMimeType = cursor.getString(mimeIndex);
                                    if (cursorMimeType != null && !cursorMimeType.isEmpty()) {
                                        mimeType = cursorMimeType;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting filename from content URI: " + e.getMessage());
                    }
                } else if (fileUri.getScheme() != null && fileUri.getScheme().equals("file")) {
                    String path = fileUri.getPath();
                    if (path != null) {
                        int lastSlash = path.lastIndexOf('/');
                        if (lastSlash != -1 && lastSlash < path.length() - 1) {
                            fileName = path.substring(lastSlash + 1);
                        }
                        
                        // Determine MIME type from extension if not set
                        if (mimeType == null || mimeType.equals("application/octet-stream")) {
                            String lowerFileName = fileName.toLowerCase();
                            if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
                                mimeType = "image/jpeg";
                            } else if (lowerFileName.endsWith(".png")) {
                                mimeType = "image/png";
                            } else if (lowerFileName.endsWith(".gif")) {
                                mimeType = "image/gif";
                            } else if (lowerFileName.endsWith(".webp")) {
                                mimeType = "image/webp";
                            } else if (lowerFileName.endsWith(".pdf")) {
                                mimeType = "application/pdf";
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Sending file - fileName: " + fileName + ", mimeType: " + mimeType + ", scheme: " + (fileUri.getScheme() != null ? fileUri.getScheme() : "null"));
                
                writer.append("--" + boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"").append("\r\n");
                writer.append("Content-Type: " + mimeType).append("\r\n").append("\r\n").flush();
                
                java.io.InputStream inputStream = contentResolver.openInputStream(fileUri);
                if (inputStream != null) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    inputStream.close();
                }
                
                writer.append("\r\n").flush();
                writer.append("--" + boundary + "--").append("\r\n").flush();
                writer.close();
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    Message message = parseMessageFromJson(jsonResponse);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onSuccess(message);
                    });
                } else {
                    String errorMsg = "Server returned error code: " + responseCode;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onError(errorMsg);
                    });
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error sending file: " + e.getMessage());
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    callback.onError("Error sending file: " + e.getMessage());
                });
            }
        }).start();
    }

    // ✅ Overloaded method without currentUserId (for backward compatibility)
    public void getMessagesByChat(Long chatId, MessagesListCallback callback) {
        getMessagesByChat(chatId, null, callback);
    }
    
    // ✅ Main method with currentUserId parameter to correctly set isSentByUser
    public void getMessagesByChat(Long chatId, Long currentUserId, MessagesListCallback callback) {
        String url = BASE_URL + "/messages/chat/" + chatId;

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<Message> messages = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            // ✅ Pass currentUserId to parseMessageFromJson to correctly set isSentByUser
                            Message message = parseMessageFromJson(response.getJSONObject(i), currentUserId);
                            if (message != null) {
                                messages.add(message);
                            }
                        }
                        callback.onSuccess(messages);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing messages: " + e.getMessage());
                        callback.onError("Error parsing messages");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting messages: " + error.getMessage());
                    callback.onError("Error getting messages: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void searchMessages(Long chatId, String query, SearchCallback callback) {
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, "UTF-8");
            String url = BASE_URL + "/messages/chat/" + chatId + "/search?query=" + encodedQuery;

            JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            List<Message> messages = new ArrayList<>();
                            for (int i = 0; i < response.length(); i++) {
                                Message message = parseMessageFromJson(response.getJSONObject(i));
                                if (message != null) {
                                    messages.add(message);
                                }
                            }
                            callback.onSuccess(messages);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing search results: " + e.getMessage());
                            callback.onError("Error parsing search results");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Error searching messages: " + error.getMessage());
                        callback.onError("Error searching messages: " + error.getMessage());
                    }
                }
            );

            requestQueue.add(request);
        } catch (Exception e) {
            Log.e(TAG, "Error encoding search query: " + e.getMessage());
            callback.onError("Error encoding search query");
        }
    }

    public void createDirectChat(Long user1Id, Long user2Id, ChatCallback callback) {
        String url = BASE_URL + "/chats/direct";
        
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("user1Id", user1Id);
            requestBody.put("user2Id", user2Id);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body: " + e.getMessage());
            callback.onError("Error creating request");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            requestBody,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Long chatId = response.getLong("id");
                        callback.onSuccess(chatId);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing response: " + e.getMessage());
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error creating direct chat: " + error.getMessage());
                    callback.onError("Error creating direct chat: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void createGroupChat(Long groupId, String chatName, ChatCallback callback) {
        String url = BASE_URL + "/chats/group";
        
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("groupId", groupId);
            requestBody.put("chatName", chatName);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body: " + e.getMessage());
            callback.onError("Error creating request");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            requestBody,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Long chatId = response.getLong("id");
                        callback.onSuccess(chatId);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing response: " + e.getMessage());
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error creating group chat: " + error.getMessage());
                    callback.onError("Error creating group chat: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getUserIdFromUsername(String username, UserIdCallback callback) {
        if (username == null || username.trim().isEmpty()) {
            callback.onError("Username cannot be empty");
            return;
        }
        
        // ✅ URL encode the username to handle special characters
        try {
            String encodedUsername = java.net.URLEncoder.encode(username.trim(), "UTF-8");
            String url = com.example.occasio.utils.ServerConfig.USER_INFO_BY_USERNAME + encodedUsername;
            
            Log.d(TAG, "Fetching user ID for username: " + username + " (URL: " + url + ")");
        
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Long userId = response.getLong("id");
                            Log.d(TAG, "Successfully fetched user ID: " + userId + " for username: " + username);
                        callback.onSuccess(userId);
                    } catch (JSONException e) {
                            Log.e(TAG, "Error parsing user ID response: " + e.getMessage());
                            Log.e(TAG, "Response was: " + response.toString());
                            callback.onError("Error parsing user ID: " + e.getMessage());
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMsg = error.getMessage();
                        int statusCode = -1;
                        
                        if (error.networkResponse != null) {
                            statusCode = error.networkResponse.statusCode;
                            try {
                                String responseBody = new String(error.networkResponse.data, "UTF-8");
                                Log.e(TAG, "Error response body: " + responseBody);
                                if (errorMsg == null || errorMsg.isEmpty()) {
                                    errorMsg = "HTTP " + statusCode;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Could not read error response body");
                                if (errorMsg == null || errorMsg.isEmpty()) {
                                    errorMsg = "HTTP " + statusCode;
                                }
                            }
                        } else {
                            if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = "Network error (no response)";
                        }
                    }
                        
                        Log.e(TAG, "Error fetching user ID for username: " + username);
                        Log.e(TAG, "Error message: " + errorMsg);
                        Log.e(TAG, "Status code: " + statusCode);
                    Log.e(TAG, "URL was: " + url);
                    
                        // ✅ Handle different error cases properly
                    if (errorMsg != null && (errorMsg.contains("UnknownHostException") || 
                        errorMsg.contains("Unable to resolve host") || 
                            errorMsg.contains("No address associated") ||
                            errorMsg.contains("Connection refused"))) {
                        callback.onError("NETWORK_ERROR: Backend server unavailable");
                        } else if (statusCode == 404) {
                            callback.onError("USER_NOT_FOUND: User '" + username + "' not found. Please check your username or create an account.");
                        } else if (statusCode == 500) {
                            callback.onError("SERVER_ERROR: Server error occurred. Please try again later.");
                    } else {
                            callback.onError("Error fetching user ID: " + errorMsg + " (Status: " + statusCode + ")");
                    }
                }
            }
        );
            
        requestQueue.add(request);
        } catch (java.io.UnsupportedEncodingException e) {
            Log.e(TAG, "Error encoding username: " + e.getMessage());
            callback.onError("Error encoding username");
        }
    }

    public void getUserChats(Long userId, ChatsListCallback callback) {
        String url = BASE_URL + "/chats/user/" + userId;

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<JSONObject> chats = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            chats.add(response.getJSONObject(i));
                        }
                        callback.onSuccess(chats);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing user chats: " + e.getMessage());
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting user chats: " + error.getMessage());
                    callback.onError("Error getting user chats: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void editMessage(Long messageId, Long userId, String newContent, MessageCallback callback) {
        String url = BASE_URL + "/messages/" + messageId;
        
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("userId", userId);
            requestBody.put("content", newContent);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body: " + e.getMessage());
            callback.onError("Error creating request");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.PUT,
            url,
            requestBody,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Message message = parseMessageFromJson(response);
                        callback.onSuccess(message);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing response: " + e.getMessage());
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error editing message: " + error.getMessage());
                    callback.onError("Error editing message: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void deleteMessage(Long messageId, Long userId, VoidCallback callback) {
        String url = BASE_URL + "/messages/" + messageId + "?userId=" + userId;

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
                    Log.e(TAG, "Error deleting message: " + error.getMessage());
                    callback.onError("Error deleting message: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void addReaction(Long messageId, Long userId, String emoji, VoidCallback callback) {
        String url = BASE_URL + "/messages/" + messageId + "/reactions";
        
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("userId", userId);
            requestBody.put("emoji", emoji);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body: " + e.getMessage());
            callback.onError("Error creating request");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            requestBody,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    callback.onSuccess();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error adding reaction: " + error.getMessage());
                    callback.onError("Error adding reaction: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void removeReaction(Long messageId, Long userId, String emoji, VoidCallback callback) {
        try {
            String encodedEmoji = java.net.URLEncoder.encode(emoji, "UTF-8");
            String url = BASE_URL + "/messages/" + messageId + "/reactions?userId=" + userId + "&emoji=" + encodedEmoji;

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
                        Log.e(TAG, "Error removing reaction: " + error.getMessage());
                        callback.onError("Error removing reaction: " + error.getMessage());
                    }
                }
            );

            requestQueue.add(request);
        } catch (Exception e) {
            Log.e(TAG, "Error encoding emoji: " + e.getMessage());
            callback.onError("Error encoding emoji");
        }
    }

    public void getReactions(Long messageId, AttachmentsListCallback callback) {
        String url = BASE_URL + "/messages/" + messageId + "/reactions";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<JSONObject> reactions = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            reactions.add(response.getJSONObject(i));
                        }
                        callback.onSuccess(reactions);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing reactions: " + e.getMessage());
                        callback.onError("Error parsing reactions");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting reactions: " + error.getMessage());
                    callback.onError("Error getting reactions: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getMessagesSince(Long chatId, String timestamp, MessagesListCallback callback) {
        try {
            String encodedTimestamp = java.net.URLEncoder.encode(timestamp, "UTF-8");
            String url = BASE_URL + "/messages/chat/" + chatId + "/since?timestamp=" + encodedTimestamp;

            JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            List<Message> messages = new ArrayList<>();
                            for (int i = 0; i < response.length(); i++) {
                                Message message = parseMessageFromJson(response.getJSONObject(i));
                                if (message != null) {
                                    messages.add(message);
                                }
                            }
                            callback.onSuccess(messages);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing messages: " + e.getMessage());
                            callback.onError("Error parsing messages");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Error getting messages: " + error.getMessage());
                        callback.onError("Error getting messages: " + error.getMessage());
                    }
                }
            );

            requestQueue.add(request);
        } catch (Exception e) {
            Log.e(TAG, "Error encoding timestamp: " + e.getMessage());
            callback.onError("Error encoding timestamp");
        }
    }

    public void getSharedFiles(Long chatId, MessagesListCallback callback) {
        String url = BASE_URL + "/messages/chat/" + chatId + "/files";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<Message> messages = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            Message message = parseMessageFromJson(response.getJSONObject(i));
                            if (message != null) {
                                messages.add(message);
                            }
                        }
                        callback.onSuccess(messages);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing files: " + e.getMessage());
                        callback.onError("Error parsing files");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting shared files: " + error.getMessage());
                    callback.onError("Error getting shared files: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getMessageCount(Long chatId, LongCallback callback) {
        String url = BASE_URL + "/messages/chat/" + chatId + "/count";

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Long count = response.getLong("count");
                        callback.onSuccess(count);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing count: " + e.getMessage());
                        callback.onError("Error parsing count");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting message count: " + error.getMessage());
                    callback.onError("Error getting message count: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getChatStorageSize(Long chatId, MapCallback callback) {
        String url = BASE_URL + "/messages/chat/" + chatId + "/storage-size";

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Map<String, Object> data = new HashMap<>();
                        data.put("sizeBytes", response.optLong("sizeBytes", 0));
                        data.put("sizeFormatted", response.optString("sizeFormatted", "0 B"));
                        callback.onSuccess(data);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing storage size: " + e.getMessage());
                        callback.onError("Error parsing storage size");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting storage size: " + error.getMessage());
                    callback.onError("Error getting storage size: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getMessagesWithEmojis(Long chatId, MessagesListCallback callback) {
        String url = BASE_URL + "/messages/chat/" + chatId + "/with-emojis";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<Message> messages = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            Message message = parseMessageFromJson(response.getJSONObject(i));
                            if (message != null) {
                                messages.add(message);
                            }
                        }
                        callback.onSuccess(messages);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing messages: " + e.getMessage());
                        callback.onError("Error parsing messages");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting messages with emojis: " + error.getMessage());
                    callback.onError("Error getting messages with emojis: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getPopularEmojis(Long chatId, MapCallback callback) {
        String url = BASE_URL + "/messages/chat/" + chatId + "/popular-emojis";

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Map<String, Object> data = new HashMap<>();
                        if (response.has("emojis")) {
                            JSONArray emojisArray = response.getJSONArray("emojis");
                            List<String> emojis = new ArrayList<>();
                            for (int i = 0; i < emojisArray.length(); i++) {
                                emojis.add(emojisArray.getString(i));
                            }
                            data.put("emojis", emojis);
                        }
                        callback.onSuccess(data);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing popular emojis: " + e.getMessage());
                        callback.onError("Error parsing popular emojis");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting popular emojis: " + error.getMessage());
                    callback.onError("Error getting popular emojis: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getChatAttachments(Long chatId, AttachmentsListCallback callback) {
        String url = BASE_URL + "/messages/chat/" + chatId + "/attachments";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<JSONObject> attachments = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            attachments.add(response.getJSONObject(i));
                        }
                        callback.onSuccess(attachments);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing attachments: " + e.getMessage());
                        callback.onError("Error parsing attachments");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting attachments: " + error.getMessage());
                    callback.onError("Error getting attachments: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getChatImages(Long chatId, AttachmentsListCallback callback) {
        String url = BASE_URL + "/messages/chat/" + chatId + "/images";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<JSONObject> attachments = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            attachments.add(response.getJSONObject(i));
                        }
                        callback.onSuccess(attachments);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing images: " + e.getMessage());
                        callback.onError("Error parsing images");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting images: " + error.getMessage());
                    callback.onError("Error getting images: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getChatVideos(Long chatId, AttachmentsListCallback callback) {
        String url = BASE_URL + "/messages/chat/" + chatId + "/videos";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<JSONObject> attachments = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            attachments.add(response.getJSONObject(i));
                        }
                        callback.onSuccess(attachments);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing videos: " + e.getMessage());
                        callback.onError("Error parsing videos");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting videos: " + error.getMessage());
                    callback.onError("Error getting videos: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getChatPdfs(Long chatId, AttachmentsListCallback callback) {
        String url = BASE_URL + "/messages/chat/" + chatId + "/pdfs";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<JSONObject> attachments = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            attachments.add(response.getJSONObject(i));
                        }
                        callback.onSuccess(attachments);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing PDFs: " + e.getMessage());
                        callback.onError("Error parsing PDFs");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting PDFs: " + error.getMessage());
                    callback.onError("Error getting PDFs: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void downloadAttachment(Long attachmentId, BytesCallback callback) {
        new Thread(() -> {
            try {
                // ✅ Use correct endpoint path - BASE_URL already includes /api
                String url = BASE_URL + "/messages/attachments/" + attachmentId;
                Log.d(TAG, "Downloading attachment from: " + url);
                java.net.URL urlObj = new java.net.URL(url);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) urlObj.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoInput(true);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    java.io.InputStream inputStream = connection.getInputStream();
                    java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    byte[] data = outputStream.toByteArray();
                    inputStream.close();
                    outputStream.close();
                    
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onSuccess(data);
                    });
                } else {
                    String errorMsg = "Server returned error code: " + responseCode;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onError(errorMsg);
                    });
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error downloading attachment: " + e.getMessage());
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    callback.onError("Error downloading attachment: " + e.getMessage());
                });
            }
        }).start();
    }

    public void getAttachmentThumbnail(Long attachmentId, BytesCallback callback) {
        String url = BASE_URL + "/messages/attachments/" + attachmentId + "/thumbnail";

        com.android.volley.toolbox.ImageRequest request = new com.android.volley.toolbox.ImageRequest(
            url,
            new Response.Listener<android.graphics.Bitmap>() {
                @Override
                public void onResponse(android.graphics.Bitmap bitmap) {
                    java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, stream);
                    callback.onSuccess(stream.toByteArray());
                }
            },
            0,
            0,
            null,
            null,
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting thumbnail: " + error.getMessage());
                    callback.onError("Error getting thumbnail: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void deleteAttachment(Long attachmentId, Long userId, VoidCallback callback) {
        String url = BASE_URL + "/messages/attachments/" + attachmentId + "?userId=" + userId;

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
                    Log.e(TAG, "Error deleting attachment: " + error.getMessage());
                    callback.onError("Error deleting attachment: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getDirectChats(Long userId, ChatsListCallback callback) {
        String url = BASE_URL + "/chats/user/" + userId + "/direct";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<JSONObject> chats = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            chats.add(response.getJSONObject(i));
                        }
                        callback.onSuccess(chats);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing chats: " + e.getMessage());
                        callback.onError("Error parsing chats");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting direct chats: " + error.getMessage());
                    callback.onError("Error getting direct chats: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getGroupChats(Long userId, ChatsListCallback callback) {
        String url = BASE_URL + "/chats/user/" + userId + "/groups";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<JSONObject> chats = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            chats.add(response.getJSONObject(i));
                        }
                        callback.onSuccess(chats);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing chats: " + e.getMessage());
                        callback.onError("Error parsing chats");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting group chats: " + error.getMessage());
                    callback.onError("Error getting group chats: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getChatById(Long chatId, ChatObjectCallback callback) {
        String url = BASE_URL + "/chats/" + chatId;

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    callback.onSuccess(response);
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting chat: " + error.getMessage());
                    callback.onError("Error getting chat: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void getGroupChatByGroupId(Long groupId, ChatObjectCallback callback) {
        String url = BASE_URL + "/chats/group/" + groupId;

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    callback.onSuccess(response);
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting group chat: " + error.getMessage());
                    callback.onError("Error getting group chat: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void addParticipant(Long chatId, Long userId, ChatObjectCallback callback) {
        String url = BASE_URL + "/chats/" + chatId + "/participants";
        
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("userId", userId);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body: " + e.getMessage());
            callback.onError("Error creating request");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            requestBody,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    callback.onSuccess(response);
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error adding participant: " + error.getMessage());
                    callback.onError("Error adding participant: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void removeParticipant(Long chatId, Long userId, ChatObjectCallback callback) {
        String url = BASE_URL + "/chats/" + chatId + "/participants/" + userId;

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.DELETE,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    callback.onSuccess(response);
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error removing participant: " + error.getMessage());
                    callback.onError("Error removing participant: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void updateChatName(Long chatId, String newName, ChatObjectCallback callback) {
        String url = BASE_URL + "/chats/" + chatId + "/name";
        
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("name", newName);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body: " + e.getMessage());
            callback.onError("Error creating request");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.PUT,
            url,
            requestBody,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    callback.onSuccess(response);
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error updating chat name: " + error.getMessage());
                    callback.onError("Error updating chat name: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    public void deleteChat(Long chatId, VoidCallback callback) {
        String url = BASE_URL + "/chats/" + chatId;

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
                    Log.e(TAG, "Error deleting chat: " + error.getMessage());
                    callback.onError("Error deleting chat: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    // ✅ Overloaded method without currentUserId (for backward compatibility)
    private Message parseMessageFromJson(JSONObject json) throws JSONException {
        return parseMessageFromJson(json, null);
    }
    
    // ✅ Main method with currentUserId parameter to correctly set isSentByUser
    private Message parseMessageFromJson(JSONObject json, Long currentUserId) throws JSONException {
        try {
            String content = json.optString("content", "");
            // ✅ Handle null values properly - convert "null" string to empty string
            if (content == null || content.equals("null")) {
                content = "";
            }
            
            JSONObject senderJson = json.optJSONObject("sender");
            String senderName = "Unknown";
            Long senderId = null;
            String senderProfilePictureUrl = null;
            if (senderJson != null) {
                senderName = senderJson.optString("username", 
                          senderJson.optString("firstName", "Unknown"));
                if (senderName == null || senderName.isEmpty() || senderName.equals("null")) {
                    senderName = senderJson.optString("lastName", "Unknown");
                }
                if (senderName == null || senderName.isEmpty() || senderName.equals("null")) {
                    senderName = "Unknown";
                }
                try {
                    senderId = senderJson.optLong("id", -1);
                    if (senderId <= 0) {
                        senderId = null;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing sender ID: " + e.getMessage());
                    senderId = null;
                }
                // Extract profile picture path
                senderProfilePictureUrl = senderJson.optString("profilePicturePath", null);
                if (senderProfilePictureUrl == null || senderProfilePictureUrl.isEmpty() || senderProfilePictureUrl.equals("null")) {
                    senderProfilePictureUrl = senderJson.optString("profile_picture_path", null);
                }
            } else {
                // ✅ Log warning if sender is missing
                Log.w(TAG, "Message response missing sender information - messageId: " + json.optLong("id", -1));
            }
            
            String createdAtStr = json.optString("createdAt", "");
            long timestamp = System.currentTimeMillis();
            if (!createdAtStr.isEmpty()) {
                try {
                    // ✅ Use SimpleDateFormat for API 24+ compatibility and proper timezone handling
                    String dateStr = createdAtStr.replace("Z", "").replace("T", " ");
                    // Remove microseconds if present
                    if (dateStr.contains(".")) {
                        int dotIndex = dateStr.indexOf(".");
                        dateStr = dateStr.substring(0, dotIndex);
                    }
                    // Remove timezone offset if present (e.g., "+00:00" or "-06:00")
                    if (dateStr.contains("+") || (dateStr.contains("-") && dateStr.lastIndexOf("-") > 10)) {
                        int tzIndex = dateStr.indexOf("+");
                        if (tzIndex == -1) {
                            // Find the last dash that's part of timezone (not date)
                            tzIndex = dateStr.lastIndexOf("-");
                            if (tzIndex > 10) { // After date part
                                dateStr = dateStr.substring(0, tzIndex);
                            }
                        } else {
                            dateStr = dateStr.substring(0, tzIndex);
                        }
                    }
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                    java.util.Date date = sdf.parse(dateStr);
                    timestamp = date.getTime();
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing timestamp: " + createdAtStr + " - " + e.getMessage());
                    timestamp = System.currentTimeMillis();
                }
            }
            
            // ✅ Correctly determine if sent by current user
            boolean isSentByUser = false;
            if (currentUserId != null && senderId != null) {
                isSentByUser = senderId.equals(currentUserId);
            }
            
            JSONObject attachmentJson = json.optJSONObject("attachment");
            String attachmentPath = null;
            String attachmentType = null;
            String attachmentName = null;
            
            Long attachmentId = null;
            if (attachmentJson != null) {
                attachmentId = attachmentJson.optLong("id", -1);
                if (attachmentId <= 0) {
                    attachmentId = null;
                }
                // ✅ If attachmentId not found in attachment object, try to extract from fileUrl
                if (attachmentId == null) {
                    String fileUrl = attachmentJson.optString("fileUrl", "");
                    if (fileUrl != null && !fileUrl.isEmpty() && fileUrl.contains("/attachments/")) {
                        try {
                            int index = fileUrl.lastIndexOf("/attachments/");
                            if (index >= 0) {
                                String idStr = fileUrl.substring(index + "/attachments/".length());
                                if (idStr.contains("?")) {
                                    idStr = idStr.substring(0, idStr.indexOf("?"));
                                }
                                attachmentId = Long.parseLong(idStr);
                                Log.d(TAG, "Extracted attachmentId from attachment.fileUrl: " + attachmentId);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error extracting attachmentId from attachment.fileUrl: " + e.getMessage());
                        }
                    }
                }
                String fileType = attachmentJson.optString("fileType", "");
                // ✅ Check both "originalFileName" and "fileName" for compatibility
                // ✅ Handle null values properly - optString returns "null" string if value is null
                attachmentName = attachmentJson.optString("originalFileName", "");
                if (attachmentName == null || attachmentName.isEmpty() || attachmentName.equals("null")) {
                    attachmentName = attachmentJson.optString("fileName", "");
                }
                // ✅ If still null or "null", try to get from message fileName field
                if ((attachmentName == null || attachmentName.isEmpty() || attachmentName.equals("null"))) {
                    attachmentName = json.optString("fileName", "");
                }
                // ✅ Final fallback - use a default name
                if (attachmentName == null || attachmentName.isEmpty() || attachmentName.equals("null")) {
                    attachmentName = "Attachment";
                }
                
                if (fileType != null && !fileType.isEmpty()) {
                    if (fileType.toLowerCase().contains("image")) {
                        attachmentType = "image";
                    } else if (fileType.toLowerCase().contains("pdf")) {
                        attachmentType = "pdf";
                    }
                    attachmentPath = attachmentJson.optString("fileUrl", "");
                }
            } else {
                // ✅ Fallback to top-level fields if attachment object doesn't exist
                String fileType = json.optString("fileType", "");
                if (!fileType.isEmpty()) {
                    // ✅ Check both "originalFileName" and "fileName" for compatibility
                    // ✅ Handle null values properly - optString returns "null" string if value is null
                    attachmentName = json.optString("originalFileName", "");
                    if (attachmentName == null || attachmentName.isEmpty() || attachmentName.equals("null")) {
                    attachmentName = json.optString("fileName", "");
                    }
                    // ✅ Final fallback - use a default name based on file type
                    if (attachmentName == null || attachmentName.isEmpty() || attachmentName.equals("null")) {
                        if (fileType.toLowerCase().contains("image")) {
                            attachmentName = "Image";
                        } else if (fileType.toLowerCase().contains("pdf")) {
                            attachmentName = "Document";
                        } else {
                            attachmentName = "Attachment";
                        }
                    }
                    attachmentPath = json.optString("fileUrl", "");
                    // ✅ Try to extract attachmentId from fileUrl if not already set
                    if (attachmentId == null && attachmentPath != null && !attachmentPath.isEmpty() && attachmentPath.contains("/attachments/")) {
                        try {
                            int index = attachmentPath.lastIndexOf("/attachments/");
                            if (index >= 0) {
                                String idStr = attachmentPath.substring(index + "/attachments/".length());
                                if (idStr.contains("?")) {
                                    idStr = idStr.substring(0, idStr.indexOf("?"));
                                }
                                attachmentId = Long.parseLong(idStr);
                                Log.d(TAG, "Extracted attachmentId from top-level fileUrl: " + attachmentId);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error extracting attachmentId from top-level fileUrl: " + e.getMessage());
                        }
                    }
                    if (fileType.toLowerCase().contains("image")) {
                        attachmentType = "image";
                    } else if (fileType.toLowerCase().contains("pdf")) {
                        attachmentType = "pdf";
                    }
                }
            }
            
            Message message;
            // ✅ Only create message with attachment if attachmentPath and attachmentType are valid (not empty/null/"null")
            if (attachmentPath != null && !attachmentPath.isEmpty() && !attachmentPath.equals("null") &&
                attachmentType != null && !attachmentType.isEmpty() && !attachmentType.equals("null")) {
                message = new Message(content, senderName, timestamp, isSentByUser, 
                                     attachmentPath, attachmentType, attachmentName);
                Log.d(TAG, "Created message with attachment - path: " + attachmentPath + 
                          ", type: " + attachmentType + ", name: " + attachmentName);
            } else {
                message = new Message(content, senderName, timestamp, isSentByUser);
                // ✅ Also check top-level fileUrl, fileName, fileType fields as fallback
                // ✅ Only set attachment fields if they're actually valid (not empty/null/"null")
                String fileUrl = json.optString("fileUrl", "");
                String fileName = json.optString("fileName", "");
                String fileType = json.optString("fileType", "");
                // ✅ Check if fileUrl and fileType are valid (not empty, null, or "null")
                if (fileUrl != null && !fileUrl.isEmpty() && !fileUrl.equals("null") &&
                    fileType != null && !fileType.isEmpty() && !fileType.equals("null")) {
                    message.setAttachmentPath(fileUrl);
                    message.setAttachmentType(fileType);
                    // ✅ Handle null values properly - optString returns "null" string if value is null
                    // ✅ Use existing attachmentName variable (already declared above)
                    attachmentName = fileName;
                    if (attachmentName == null || attachmentName.isEmpty() || attachmentName.equals("null")) {
                        if (fileType.toLowerCase().contains("image")) {
                            attachmentName = "Image";
                        } else if (fileType.toLowerCase().contains("pdf")) {
                            attachmentName = "Document";
                        } else {
                            attachmentName = "Attachment";
                        }
                    }
                    message.setAttachmentName(attachmentName);
                    Log.d(TAG, "Set attachment from top-level fields - path: " + fileUrl + 
                              ", type: " + fileType + ", name: " + attachmentName);
                } else {
                    // ✅ Ensure text messages don't have attachment data
                    message.setAttachmentPath(null);
                    message.setAttachmentType(null);
                    message.setAttachmentName(null);
                    message.setAttachmentId(null);
                }
            }
            
            Long messageId = json.optLong("id", -1);
            if (messageId > 0) {
                message.setMessageId(messageId);
            }
            
            // ✅ Always set attachmentId if we found it
            if (attachmentId != null && attachmentId > 0) {
                message.setAttachmentId(attachmentId);
                Log.d(TAG, "Set attachmentId: " + attachmentId + " for message: " + messageId);
            } else {
                Log.w(TAG, "No attachmentId found for message: " + messageId + ", fileUrl: " + attachmentPath);
            }
            
            // Set sender profile picture URL
            if (senderProfilePictureUrl != null && !senderProfilePictureUrl.isEmpty()) {
                message.setSenderProfilePictureUrl(senderProfilePictureUrl);
            }
            
            try {
                if (json.has("reactions")) {
                    Object reactionsObj = json.get("reactions");
                    if (reactionsObj instanceof JSONArray) {
                        JSONArray reactionsArray = (JSONArray) reactionsObj;
                        for (int i = 0; i < reactionsArray.length(); i++) {
                            JSONObject reactionJson = reactionsArray.getJSONObject(i);
                            String emoji = reactionJson.optString("emoji", "");
                            if (!emoji.isEmpty()) {
                                message.addReaction(emoji);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing reactions: " + e.getMessage());
            }
            
            return message;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing message JSON: " + e.getMessage());
            return null;
        }
    }

    // ===================== GROUP MESSAGE ENDPOINTS =====================

    /**
     * Send a text message to a group
     * POST /api/messages/group/{groupId}/send
     */
    public void sendGroupMessage(Long groupId, Long senderId, String content, Long replyToId, MessageCallback callback) {
        String url = BASE_URL + "/messages/group/" + groupId + "/send";
        
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("senderId", senderId);
            requestBody.put("content", content);
            if (replyToId != null) {
                requestBody.put("replyToId", replyToId);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body: " + e.getMessage());
            callback.onError("Error creating request");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            requestBody,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Message message = parseMessageFromJson(response);
                        callback.onSuccess(message);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing response: " + e.getMessage());
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error sending group message: " + error.getMessage());
                    callback.onError("Error sending group message: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    /**
     * Send a file message to a group
     * POST /api/messages/group/{groupId}/send-file
     */
    public void sendGroupFileMessage(Long groupId, Long senderId, android.net.Uri fileUri, String caption, Long replyToId, MessageCallback callback) {
        new Thread(() -> {
            try {
                String url = BASE_URL + "/messages/group/" + groupId + "/send-file";
                java.net.URL urlObj = new java.net.URL(url);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) urlObj.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setDoInput(true);
                
                String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                
                java.io.OutputStream outputStream = connection.getOutputStream();
                java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(outputStream, "UTF-8"), true);
                
                writer.append("--" + boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"senderId\"").append("\r\n");
                writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n").append("\r\n");
                writer.append(String.valueOf(senderId)).append("\r\n").flush();
                
                if (caption != null && !caption.isEmpty()) {
                    writer.append("--" + boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"caption\"").append("\r\n");
                    writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n").append("\r\n");
                    writer.append(caption).append("\r\n").flush();
                }
                
                if (replyToId != null) {
                    writer.append("--" + boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"replyToId\"").append("\r\n");
                    writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n").append("\r\n");
                    writer.append(String.valueOf(replyToId)).append("\r\n").flush();
                }
                
                android.content.ContentResolver contentResolver = context.getContentResolver();
                String fileName = "file";
                String mimeType = contentResolver.getType(fileUri);
                if (mimeType == null) {
                    mimeType = "application/octet-stream";
                }
                
                // ✅ Extract actual filename from URI - handle both content:// and file:// schemes
                if (fileUri.getScheme() != null && fileUri.getScheme().equals("content")) {
                    try (android.database.Cursor cursor = contentResolver.query(fileUri, null, null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            // Try DISPLAY_NAME first
                            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            if (nameIndex >= 0) {
                                String displayName = cursor.getString(nameIndex);
                                if (displayName != null && !displayName.isEmpty()) {
                                    fileName = displayName;
                                }
                            }
                            
                            // If DISPLAY_NAME not found, try MediaStore columns
                            if (fileName == null || fileName.equals("file")) {
                                int dataIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA);
                                if (dataIndex >= 0) {
                                    String dataPath = cursor.getString(dataIndex);
                                    if (dataPath != null && !dataPath.isEmpty()) {
                                        int lastSlash = dataPath.lastIndexOf('/');
                                        if (lastSlash != -1 && lastSlash < dataPath.length() - 1) {
                                            fileName = dataPath.substring(lastSlash + 1);
                                        }
                                    }
                                }
                            }
                            
                            // Also get MIME type from cursor if available
                            if (mimeType == null || mimeType.equals("application/octet-stream")) {
                                int mimeIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE);
                                if (mimeIndex >= 0) {
                                    String cursorMimeType = cursor.getString(mimeIndex);
                                    if (cursorMimeType != null && !cursorMimeType.isEmpty()) {
                                        mimeType = cursorMimeType;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting filename from content URI: " + e.getMessage());
                    }
                } else if (fileUri.getScheme() != null && fileUri.getScheme().equals("file")) {
                    String path = fileUri.getPath();
                    if (path != null) {
                        int lastSlash = path.lastIndexOf('/');
                        if (lastSlash != -1 && lastSlash < path.length() - 1) {
                            fileName = path.substring(lastSlash + 1);
                        }
                        
                        // Determine MIME type from extension if not set
                        if (mimeType == null || mimeType.equals("application/octet-stream")) {
                            String lowerFileName = fileName.toLowerCase();
                            if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
                                mimeType = "image/jpeg";
                            } else if (lowerFileName.endsWith(".png")) {
                                mimeType = "image/png";
                            } else if (lowerFileName.endsWith(".gif")) {
                                mimeType = "image/gif";
                            } else if (lowerFileName.endsWith(".webp")) {
                                mimeType = "image/webp";
                            } else if (lowerFileName.endsWith(".pdf")) {
                                mimeType = "application/pdf";
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Sending file - fileName: " + fileName + ", mimeType: " + mimeType + ", scheme: " + (fileUri.getScheme() != null ? fileUri.getScheme() : "null"));
                
                writer.append("--" + boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"").append("\r\n");
                writer.append("Content-Type: " + mimeType).append("\r\n").append("\r\n").flush();
                
                java.io.InputStream inputStream = contentResolver.openInputStream(fileUri);
                if (inputStream != null) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    inputStream.close();
                }
                
                writer.append("\r\n").flush();
                writer.append("--" + boundary + "--").append("\r\n").flush();
                writer.close();
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    Message message = parseMessageFromJson(jsonResponse);
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onSuccess(message);
                    });
                } else {
                    String errorMsg = "Server returned error code: " + responseCode;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onError(errorMsg);
                    });
                }
                connection.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error sending group file: " + e.getMessage());
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    callback.onError("Error sending group file: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Get all messages for a group
     * GET /api/messages/group/{groupId}
     */
    // ✅ Overloaded method without currentUserId (for backward compatibility)
    public void getMessagesByGroup(Long groupId, MessagesListCallback callback) {
        getMessagesByGroup(groupId, null, callback);
    }
    
    // ✅ Main method with currentUserId parameter to correctly set isSentByUser
    public void getMessagesByGroup(Long groupId, Long currentUserId, MessagesListCallback callback) {
        String url = BASE_URL + "/messages/group/" + groupId;

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<Message> messages = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            // ✅ Pass currentUserId to parseMessageFromJson to correctly set isSentByUser
                            Message message = parseMessageFromJson(response.getJSONObject(i), currentUserId);
                            if (message != null) {
                                messages.add(message);
                            }
                        }
                        callback.onSuccess(messages);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing group messages: " + e.getMessage());
                        callback.onError("Error parsing messages");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting group messages: " + error.getMessage());
                    callback.onError("Error getting group messages: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    /**
     * Get group messages since a specific timestamp
     * GET /api/messages/group/{groupId}/since?timestamp=2024-01-01T00:00:00
     */
    public void getGroupMessagesSince(Long groupId, String timestamp, MessagesListCallback callback) {
        try {
            String encodedTimestamp = java.net.URLEncoder.encode(timestamp, "UTF-8");
            String url = BASE_URL + "/messages/group/" + groupId + "/since?timestamp=" + encodedTimestamp;

            JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        try {
                            List<Message> messages = new ArrayList<>();
                            for (int i = 0; i < response.length(); i++) {
                                Message message = parseMessageFromJson(response.getJSONObject(i));
                                if (message != null) {
                                    messages.add(message);
                                }
                            }
                            callback.onSuccess(messages);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing group messages: " + e.getMessage());
                            callback.onError("Error parsing messages");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Error getting group messages: " + error.getMessage());
                        callback.onError("Error getting group messages: " + error.getMessage());
                    }
                }
            );

            requestQueue.add(request);
        } catch (Exception e) {
            Log.e(TAG, "Error encoding timestamp: " + e.getMessage());
            callback.onError("Error encoding timestamp");
        }
    }

    /**
     * Get files shared in a group
     * GET /api/messages/group/{groupId}/files
     */
    public void getGroupSharedFiles(Long groupId, MessagesListCallback callback) {
        String url = BASE_URL + "/messages/group/" + groupId + "/files";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        List<Message> messages = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            Message message = parseMessageFromJson(response.getJSONObject(i));
                            if (message != null) {
                                messages.add(message);
                            }
                        }
                        callback.onSuccess(messages);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing group files: " + e.getMessage());
                        callback.onError("Error parsing files");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting group shared files: " + error.getMessage());
                    callback.onError("Error getting group shared files: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }

    /**
     * Get message count for a group
     * GET /api/messages/group/{groupId}/count
     */
    public void getGroupMessageCount(Long groupId, LongCallback callback) {
        String url = BASE_URL + "/messages/group/" + groupId + "/count";

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Long count = response.getLong("count");
                        callback.onSuccess(count);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing group message count: " + e.getMessage());
                        callback.onError("Error parsing count");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error getting group message count: " + error.getMessage());
                    callback.onError("Error getting group message count: " + error.getMessage());
                }
            }
        );

        requestQueue.add(request);
    }
}

