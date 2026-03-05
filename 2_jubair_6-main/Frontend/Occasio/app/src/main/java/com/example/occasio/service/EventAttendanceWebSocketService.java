package com.example.occasio.service;

import android.util.Log;
import com.example.occasio.utils.ServerConfig;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket service for real-time event attendance code receiving
 * Users subscribe to specific event IDs to receive attendance codes
 */
public class EventAttendanceWebSocketService {
    private static final String TAG = "EventAttendanceWebSocket";
    private static final String BASE_WS_URL = ServerConfig.WS_ATTENDANCE_URL;
    
    private static EventAttendanceWebSocketService instance;
    private Map<Long, WebSocketClient> eventClients; // eventId -> WebSocketClient
    private Map<Long, AttendanceListener> listeners; // eventId -> listener
    
    public interface AttendanceListener {
        void onAttendanceCodeReceived(String code, Long sessionId, String eventName);
        void onSessionEnded(String message);
        void onConnect(Long eventId);
        void onDisconnect(Long eventId);
        void onError(Long eventId, Exception error);
    }
    
    private EventAttendanceWebSocketService() {
        eventClients = new ConcurrentHashMap<>();
        listeners = new ConcurrentHashMap<>();
    }
    
    public static EventAttendanceWebSocketService getInstance() {
        if (instance == null) {
            instance = new EventAttendanceWebSocketService();
        }
        return instance;
    }
    
    /**
     * Subscribe to an event's attendance WebSocket
     */
    public void subscribeToEvent(Long eventId, AttendanceListener listener) {
        if (eventClients.containsKey(eventId)) {
            Log.d(TAG, "Already subscribed to event " + eventId);
            listeners.put(eventId, listener); // Update listener
            return;
        }
        
        listeners.put(eventId, listener);
        
        try {
            URI serverUri = URI.create(BASE_WS_URL + eventId);
            Log.d(TAG, "Connecting to Event Attendance WebSocket: " + serverUri);
            
            WebSocketClient client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.d(TAG, "Event Attendance WebSocket connected for event " + eventId);
                    AttendanceListener l = listeners.get(eventId);
                    if (l != null) {
                        l.onConnect(eventId);
                    }
                }
                
                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "Event Attendance WebSocket message received for event " + eventId + ": " + message);
                    handleMessage(eventId, message);
                }
                
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "Event Attendance WebSocket closed for event " + eventId + ": " + reason);
                    eventClients.remove(eventId);
                    AttendanceListener l = listeners.get(eventId);
                    if (l != null) {
                        l.onDisconnect(eventId);
                    }
                }
                
                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "Event Attendance WebSocket error for event " + eventId + ": " + ex.getMessage());
                    eventClients.remove(eventId);
                    AttendanceListener l = listeners.get(eventId);
                    if (l != null) {
                        l.onError(eventId, ex);
                    }
                }
            };
            
            eventClients.put(eventId, client);
            client.connect();
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating Event Attendance WebSocket for event " + eventId + ": " + e.getMessage());
            AttendanceListener l = listeners.get(eventId);
            if (l != null) {
                l.onError(eventId, e);
            }
        }
    }
    
    /**
     * Unsubscribe from an event's attendance WebSocket
     */
    public void unsubscribeFromEvent(Long eventId) {
        WebSocketClient client = eventClients.remove(eventId);
        if (client != null && client.isOpen()) {
            client.close();
            Log.d(TAG, "Unsubscribed from event " + eventId);
        }
        listeners.remove(eventId);
    }
    
    private void handleMessage(Long eventId, String message) {
        try {
            JSONObject json = new JSONObject(message);
            String type = json.optString("type", "");
            JSONObject data = json.optJSONObject("data");
            
            AttendanceListener listener = listeners.get(eventId);
            if (listener == null) {
                return;
            }
            
            if ("ATTENDANCE_SESSION".equals(type) && data != null) {
                // Parse attendance session data
                String code = data.optString("code", "");
                Long sessionId = data.optLong("sessionId", -1);
                String eventName = data.optString("eventName", "");
                
                if (!code.isEmpty() && sessionId > 0) {
                    listener.onAttendanceCodeReceived(code, sessionId, eventName);
                }
            } else if ("SESSION_ENDED".equals(type)) {
                String messageText = data != null ? data.optString("message", "") : json.optString("data", "");
                listener.onSessionEnded(messageText);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Event Attendance WebSocket message: " + e.getMessage());
        }
    }
    
    /**
     * Check if subscribed to an event
     */
    public boolean isSubscribed(Long eventId) {
        WebSocketClient client = eventClients.get(eventId);
        return client != null && client.isOpen();
    }
    
    /**
     * Disconnect all WebSocket connections
     */
    public void disconnectAll() {
        for (Map.Entry<Long, WebSocketClient> entry : eventClients.entrySet()) {
            WebSocketClient client = entry.getValue();
            if (client != null && client.isOpen()) {
                client.close();
            }
        }
        eventClients.clear();
        listeners.clear();
        Log.d(TAG, "Disconnected all Event Attendance WebSocket connections");
    }
}

