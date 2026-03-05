package com.example.occasio.service;

import android.util.Log;
import com.example.occasio.utils.ServerConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.disposables.Disposable;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.LifecycleEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * STOMP WebSocket service for attendance sessions
 * Handles real-time attendance code broadcasting and session notifications
 */
public class AttendanceWebSocketService {
    private static final String TAG = "AttendanceWebSocket";
    private static final String BASE_WS_URL = ServerConfig.WS_STOMP_URL;
    
    private static AttendanceWebSocketService instance;
    private StompClient stompClient;
    private Map<String, Disposable> subscriptions; // courseId -> subscription
    private Map<String, AttendanceListener> listeners; // courseId -> listener
    
    public interface AttendanceListener {
        void onSessionStarted(String code, Long sessionId, String courseName);
        void onSessionEnded(Long sessionId, String message);
        void onError(Exception error);
    }
    
    private Gson gson;
    
    private AttendanceWebSocketService() {
        subscriptions = new ConcurrentHashMap<>();
        listeners = new ConcurrentHashMap<>();
        gson = new Gson();
    }
    
    public static AttendanceWebSocketService getInstance() {
        if (instance == null) {
            instance = new AttendanceWebSocketService();
        }
        return instance;
    }
    
    /**
     * Connect to the WebSocket server
     */
    public void connect() {
        if (stompClient != null && stompClient.isConnected()) {
            Log.d(TAG, "WebSocket already connected");
            return;
        }
        
        try {
            stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, BASE_WS_URL);
            
            stompClient.lifecycle().subscribe(lifecycleEvent -> {
                switch (lifecycleEvent.getType()) {
                    case OPENED:
                        Log.d(TAG, "✅ WebSocket connection opened successfully");
                        // Connection is ready, but subscriptions happen in subscribeToCourse
                        break;
                    case CLOSED:
                        Log.d(TAG, "WebSocket connection closed");
                        break;
                    case ERROR:
                        Log.e(TAG, "❌ WebSocket error: " + lifecycleEvent.getException().getMessage());
                        lifecycleEvent.getException().printStackTrace();
                        notifyAllListenersError(lifecycleEvent.getException());
                        break;
                }
            });
            
            stompClient.connect();
            Log.d(TAG, "Connecting to WebSocket: " + BASE_WS_URL);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating WebSocket: " + e.getMessage());
            e.printStackTrace();
            notifyAllListenersError(e);
        }
    }
    
    /**
     * Disconnect from the WebSocket server
     */
    public void disconnect() {
        // Unsubscribe from all topics
        for (Map.Entry<String, Disposable> entry : subscriptions.entrySet()) {
            entry.getValue().dispose();
        }
        subscriptions.clear();
        listeners.clear();
        
        if (stompClient != null) {
            stompClient.disconnect();
            stompClient = null;
            Log.d(TAG, "WebSocket disconnected");
        }
    }
    
    /**
     * Subscribe to a course topic to receive attendance session updates
     * @param courseId The course ID to subscribe to
     * @param listener The listener to receive updates
     */
    public void subscribeToCourse(Long courseId, AttendanceListener listener) {
        String topic = "/topic/course/" + courseId;
        String courseKey = String.valueOf(courseId);
        
        // Ensure WebSocket is connected
        if (stompClient == null) {
            Log.d(TAG, "WebSocket client is null, creating connection...");
            connect();
        }
        
        if (!stompClient.isConnected()) {
            Log.w(TAG, "WebSocket not connected yet, waiting for connection...");
            // Use Handler to wait for connection (non-blocking)
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (stompClient != null && stompClient.isConnected()) {
                    doSubscribe(courseId, courseKey, topic, listener);
                } else {
                    Log.e(TAG, "❌ WebSocket still not connected after delay");
                    listener.onError(new Exception("WebSocket connection failed"));
                }
            }, 1500); // Wait 1.5 seconds for connection
            return;
        }
        
        doSubscribe(courseId, courseKey, topic, listener);
    }
    
    private void doSubscribe(Long courseId, String courseKey, String topic, AttendanceListener listener) {
        // Check if already subscribed
        if (subscriptions.containsKey(courseKey)) {
            Log.d(TAG, "Already subscribed to " + topic + ", updating listener");
            listeners.put(courseKey, listener); // Update listener
            return;
        }
        
        listeners.put(courseKey, listener);
        
        Log.d(TAG, "🔌 Subscribing to topic: " + topic + " for course ID: " + courseId);
        
        Disposable subscription = stompClient.topic(topic).subscribe(
            stompMessage -> {
                Log.d(TAG, "📨 Received message on topic " + topic);
                Log.d(TAG, "📦 Payload: " + stompMessage.getPayload());
                
                try {
                    String payload = stompMessage.getPayload();
                    
                    // Check if it's a session end notification
                    if (payload.contains("\"action\"") && payload.contains("SESSION_ENDED")) {
                        Log.d(TAG, "🔴 Session ended message detected");
                        handleSessionEnded(payload, listener);
                    } else {
                        // It's a session start with attendance code
                        Log.d(TAG, "🟢 Session started message detected");
                        handleSessionStarted(payload, listener);
                    }
                    
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error processing WebSocket message: " + e.getMessage());
                    e.printStackTrace();
                    listener.onError(e);
                }
            },
            error -> {
                Log.e(TAG, "❌ Error in subscription to " + topic + ": " + error.getMessage());
                error.printStackTrace();
                listener.onError(new Exception(error));
            }
        );
        
        subscriptions.put(courseKey, subscription);
        Log.d(TAG, "✅ Successfully subscribed to topic: " + topic);
    }
    
    /**
     * Unsubscribe from a course topic
     */
    public void unsubscribeFromCourse(Long courseId) {
        String courseKey = String.valueOf(courseId);
        
        Disposable subscription = subscriptions.remove(courseKey);
        if (subscription != null) {
            subscription.dispose();
            Log.d(TAG, "Unsubscribed from course: " + courseId);
        }
        
        listeners.remove(courseKey);
    }
    
    /**
     * Send a message to start an attendance session
     * This is typically done via REST API, but can be sent via WebSocket too
     */
    public void sendStartSession(Long courseId, String username) {
        if (stompClient == null || !stompClient.isConnected()) {
            Log.e(TAG, "WebSocket not connected");
            return;
        }
        
        String destination = "/app/attendance/start/" + courseId;
        Map<String, String> payload = new HashMap<>();
        payload.put("username", username);
        
        // Note: This library expects JSON string
        String jsonPayload = "{\"username\":\"" + username + "\"}";
        
        stompClient.send(destination, jsonPayload).subscribe(
            () -> Log.d(TAG, "Start session message sent"),
            error -> Log.e(TAG, "Error sending start session message: " + error.getMessage())
        );
    }
    
    /**
     * Send a message to end an attendance session
     */
    public void sendEndSession(Long courseId, Long sessionId, String username) {
        if (stompClient == null || !stompClient.isConnected()) {
            Log.e(TAG, "WebSocket not connected");
            return;
        }
        
        String destination = "/app/attendance/end/" + courseId;
        String jsonPayload = "{\"username\":\"" + username + "\",\"sessionId\":" + sessionId + "}";
        
        stompClient.send(destination, jsonPayload).subscribe(
            () -> Log.d(TAG, "End session message sent"),
            error -> Log.e(TAG, "Error sending end session message: " + error.getMessage())
        );
    }
    
    private void handleSessionStarted(String payload, AttendanceListener listener) {
        try {
            // Parse JSON payload using Gson
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            
            if (json.has("code") && json.has("sessionId")) {
                String code = json.get("code").getAsString();
                Long sessionId = json.get("sessionId").getAsLong();
                String courseName = json.has("courseName") ? json.get("courseName").getAsString() : "";
                
                listener.onSessionStarted(code, sessionId, courseName);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing session started message: " + e.getMessage());
            e.printStackTrace();
            listener.onError(e);
        }
    }
    
    private void handleSessionEnded(String payload, AttendanceListener listener) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            
            if (json.has("sessionId")) {
                Long sessionId = json.get("sessionId").getAsLong();
                String message = json.has("message") ? json.get("message").getAsString() : "Session ended";
                
                listener.onSessionEnded(sessionId, message);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing session ended message: " + e.getMessage());
            e.printStackTrace();
            listener.onError(e);
        }
    }
    
    private void notifyAllListenersError(Exception error) {
        for (AttendanceListener listener : listeners.values()) {
            listener.onError(error);
        }
    }
    
    public boolean isConnected() {
        return stompClient != null && stompClient.isConnected();
    }
}

