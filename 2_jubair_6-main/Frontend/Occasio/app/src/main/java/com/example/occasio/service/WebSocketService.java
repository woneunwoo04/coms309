package com.example.occasio.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class WebSocketService {
    private static final String TAG = "WebSocketService";
    private static WebSocketClient webSocketClient;
    private static WebSocketService instance;
    private WebSocketListener listener;
    private String username;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface WebSocketListener {
        void onMessage(String message);
        void onConnect();
        void onDisconnect();
        void onError(Exception e);
    }

    private WebSocketService() {}

    public static WebSocketService getInstance() {
        if (instance == null) {
            instance = new WebSocketService();
        }
        return instance;
    }

    public void setListener(WebSocketListener listener) {
        this.listener = listener;
    }

    public void connect(String username, String serverUrl) {
        this.username = username;
        
        // Disconnect existing connection if any
        if (webSocketClient != null) {
            if (webSocketClient.isOpen()) {
                Log.d(TAG, "Disconnecting existing WebSocket connection");
                webSocketClient.close();
            }
            webSocketClient = null;
        }

        try {
            // Construct full URL
            String fullUrl = serverUrl + username;
            URI serverUri = URI.create(fullUrl);
            Log.d(TAG, "🔌 Connecting to WebSocket: " + serverUri);
            Log.d(TAG, "   Username: " + username);
            Log.d(TAG, "   Server URL: " + serverUrl);

            webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.d(TAG, "✅ WebSocket connection opened successfully");
                    Log.d(TAG, "   Status: " + handshake.getHttpStatus());
                    // Post to UI thread since onOpen is called from background thread
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onConnect();
                        } else {
                            Log.w(TAG, "⚠️ WebSocket connected but listener is null!");
                        }
                    });
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "📨 WebSocket message received: " + message);
                    // Post to UI thread since onMessage is called from background thread
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onMessage(message);
                        } else {
                            Log.w(TAG, "⚠️ WebSocket message received but listener is null!");
                        }
                    });
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "⚠️ WebSocket connection closed: " + reason + " (code: " + code + ", remote: " + remote + ")");
                    // Post to UI thread since onClose is called from background thread
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onDisconnect();
                        }
                    });
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "❌ WebSocket error: " + ex.getMessage());
                    ex.printStackTrace();
                    // Post to UI thread since onError is called from background thread
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onError(ex);
                        }
                    });
                }
            };

            Log.d(TAG, "🔌 Attempting to connect...");
            webSocketClient.connect();
            Log.d(TAG, "🔌 Connection attempt initiated");

        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating WebSocket: " + e.getMessage());
            e.printStackTrace();
            if (listener != null) {
                listener.onError(e);
            }
        }
    }

    public void disconnect() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
            webSocketClient = null;
            Log.d(TAG, "WebSocket disconnected");
        }
    }

    public boolean isConnected() {
        return webSocketClient != null && webSocketClient.isOpen();
    }

    public void reconnect() {
        if (username != null) {
            disconnect();
            // Reconnect with the stored username using ServerConfig
            String wsUrl = com.example.occasio.utils.ServerConfig.getWebSocketBase() + "/ws/notifications/";
            connect(username, wsUrl);
        }
    }
}

