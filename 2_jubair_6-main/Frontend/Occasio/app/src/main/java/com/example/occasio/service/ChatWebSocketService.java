package com.example.occasio.service;

import android.util.Log;
import com.example.occasio.messaging.Message;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import java.net.URI;

public class ChatWebSocketService {
    private static final String TAG = "ChatWebSocketService";
    private static WebSocketClient webSocketClient;
    private static ChatWebSocketService instance;
    private ChatWebSocketListener listener;
    private String username;
    private String currentChatId;

    public interface ChatWebSocketListener {
        void onMessage(Message message);
        void onChatCreated(org.json.JSONObject chat);
        void onNewMessage(org.json.JSONObject messageData);
        void onMessageEdited(org.json.JSONObject messageData);
        void onMessageDeleted(Long messageId);
        void onTyping(Long userId, boolean isTyping);
        void onReaction(Long messageId, String emoji, Long userId, String action); // ✅ Added for reactions
        void onConnect();
        void onDisconnect();
        void onError(Exception e);
    }

    private ChatWebSocketService() {}

    public static ChatWebSocketService getInstance() {
        if (instance == null) {
            instance = new ChatWebSocketService();
        }
        return instance;
    }

    public void setListener(ChatWebSocketListener listener) {
        this.listener = listener;
    }

    public void connect(Long userId, String serverUrl) {
        this.username = String.valueOf(userId); // Store as string for compatibility
        
        if (webSocketClient != null && webSocketClient.isOpen()) {
            Log.d(TAG, "Chat WebSocket already connected");
            return;
        }

        try {
            URI serverUri = URI.create(serverUrl + userId);
            Log.d(TAG, "Connecting to Chat WebSocket: " + serverUri);

            webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.d(TAG, "Chat WebSocket connection opened");
                    if (listener != null) {
                        listener.onConnect();
                    }
                    
                    // ✅ Auto-join chat if chatId was set before connection
                    if (currentChatId != null && !currentChatId.isEmpty()) {
                        joinChat(currentChatId);
                    }
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "📨 Chat WebSocket message received: " + message);
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(message);
                        String type = json.optString("type", "");
                        Log.d(TAG, "📨 Message type: " + type);
                        
                        if (listener == null) {
                            Log.w(TAG, "⚠️ No listener set, ignoring message. Type: " + type);
                            return;
                        }
                        
                        switch (type) {
                            case "chat_created":
                            case "group_chat_created":
                                // ✅ Handle both direct and group chat creation
                                org.json.JSONObject chatData = json.optJSONObject("data");
                                if (chatData != null) {
                                    listener.onChatCreated(chatData);
                                }
                                break;
                                
                            case "new_message":
                                // ✅ Handle both formats: "message" (from broadcast) or "data" (from sendToUser)
                                org.json.JSONObject messageObj = json.optJSONObject("message");
                                if (messageObj == null) {
                                    // Try "data" format (from sendToUser)
                                    messageObj = json.optJSONObject("data");
                                }
                                if (messageObj != null) {
                                    Log.d(TAG, "✅ Received new_message event, forwarding to listener");
                                    // ✅ Only call onNewMessage - it handles the message properly
                                    // ✅ Don't call onMessage (legacy) to avoid duplicates
                                    // The onNewMessage handler has proper duplicate detection
                                    listener.onNewMessage(messageObj);
                                } else {
                                    Log.w(TAG, "⚠️ new_message event received but message/data object is null. JSON: " + json.toString());
                                }
                                break;
                                
                            case "message_edited":
                                org.json.JSONObject editedMessage = json.optJSONObject("message");
                                if (editedMessage != null) {
                                    listener.onMessageEdited(editedMessage);
                                }
                                break;
                                
                            case "message_deleted":
                                Long messageId = json.optLong("messageId", -1);
                                if (messageId > 0) {
                                    listener.onMessageDeleted(messageId);
                                }
                                break;
                                
                            case "typing":
                                Long userId = json.optLong("userId", -1);
                                boolean isTyping = json.optBoolean("isTyping", false);
                                if (userId > 0) {
                                    listener.onTyping(userId, isTyping);
                                }
                                break;
                                
                            case "reaction_added":
                            case "reaction_removed":
                                // ✅ Handle reaction events (from broadcast or sendToUser)
                                Long reactionMessageId = json.optLong("messageId", -1);
                                String emoji = json.optString("emoji", "");
                                Long reactionUserId = json.optLong("userId", -1);
                                String action = "reaction_added".equals(type) ? "add" : "remove";
                                
                                // ✅ Handle both formats: direct fields or nested in data
                                if (reactionMessageId <= 0 || emoji.isEmpty() || reactionUserId <= 0) {
                                    org.json.JSONObject data = json.optJSONObject("data");
                                    if (data != null) {
                                        if (reactionMessageId <= 0) {
                                            reactionMessageId = data.optLong("messageId", -1);
                                        }
                                        if (emoji.isEmpty()) {
                                            emoji = data.optString("emoji", "");
                                        }
                                        if (reactionUserId <= 0) {
                                            reactionUserId = data.optLong("userId", -1);
                                        }
                                    }
                                }
                                
                                if (reactionMessageId > 0 && !emoji.isEmpty() && reactionUserId > 0) {
                                    Log.d(TAG, "✅ Received reaction event: " + action + " " + emoji + " on message " + reactionMessageId);
                                    listener.onReaction(reactionMessageId, emoji, reactionUserId, action);
                                } else {
                                    Log.w(TAG, "⚠️ Reaction event received but missing data. messageId: " + reactionMessageId + ", emoji: " + emoji + ", userId: " + reactionUserId);
                                }
                                break;
                                
                            case "new_message_notification":
                                // ✅ Notification for users not in chat - show popup notification
                                org.json.JSONObject notificationData = json.optJSONObject("data");
                                if (notificationData != null) {
                                    Log.d(TAG, "New message notification: " + notificationData.toString());
                                    // Create a message-like object from notification for onNewMessage handler
                                    org.json.JSONObject notificationMessage = new org.json.JSONObject();
                                    notificationMessage.put("content", notificationData.optString("messagePreview", ""));
                                    org.json.JSONObject senderObj = new org.json.JSONObject();
                                    senderObj.put("username", notificationData.optString("from", ""));
                                    notificationMessage.put("sender", senderObj);
                                    notificationMessage.put("chatId", notificationData.optLong("chatId", -1));
                                    listener.onNewMessage(notificationMessage);
                                }
                                break;
                                
                            default:
                                // Try to parse as legacy message format
                                Message legacyMessage = parseMessage(message);
                                if (legacyMessage != null) {
                                    listener.onMessage(legacyMessage);
                                } else {
                                    Log.w(TAG, "Unknown message type: " + type);
                                }
                                break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing message: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "Chat WebSocket connection closed: " + reason);
                    if (listener != null) {
                        listener.onDisconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "Chat WebSocket error: " + ex.getMessage());
                    if (listener != null) {
                        listener.onError(ex);
                    }
                }
            };

            webSocketClient.connect();

        } catch (Exception e) {
            Log.e(TAG, "Error creating Chat WebSocket: " + e.getMessage());
            if (listener != null) {
                listener.onError(e);
            }
        }
    }

    public void sendMessage(Message message) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.e(TAG, "Cannot send message: WebSocket not connected");
            return;
        }

        try {
            JSONObject messageJson = new JSONObject();
            messageJson.put("chatId", currentChatId);
            messageJson.put("text", message.getText());
            messageJson.put("sender", message.getSender());
            messageJson.put("timestamp", message.getTimestamp());
            messageJson.put("isSentByUser", message.isSentByUser());
            
            if (message.hasAttachment()) {
                messageJson.put("attachmentPath", message.getAttachmentPath());
                messageJson.put("attachmentType", message.getAttachmentType());
                messageJson.put("attachmentName", message.getAttachmentName());
            }
            
            if (message.hasReactions()) {
                JSONObject reactionsJson = new JSONObject();
                for (java.util.Map.Entry<String, Integer> entry : message.getReactions().entrySet()) {
                    reactionsJson.put(entry.getKey(), entry.getValue());
                }
                messageJson.put("reactions", reactionsJson);
            }

            webSocketClient.send(messageJson.toString());
            Log.d(TAG, "Message sent via WebSocket");
        } catch (Exception e) {
            Log.e(TAG, "Error sending message: " + e.getMessage());
        }
    }

    public void joinChat(String chatId) {
        this.currentChatId = chatId;
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject joinJson = new JSONObject();
                joinJson.put("action", "join_chat");  // Fixed: backend expects "join_chat"
                joinJson.put("chatId", Long.parseLong(chatId));
                webSocketClient.send(joinJson.toString());
                Log.d(TAG, "Joined chat: " + chatId);
            } catch (Exception e) {
                Log.e(TAG, "Error joining chat: " + e.getMessage());
            }
        }
    }

    public void leaveChat(String chatId) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject leaveJson = new JSONObject();
                leaveJson.put("action", "leave_chat");  // Fixed: backend expects "leave_chat"
                leaveJson.put("chatId", Long.parseLong(chatId));
                webSocketClient.send(leaveJson.toString());
                Log.d(TAG, "Left chat: " + chatId);
            } catch (Exception e) {
                Log.e(TAG, "Error leaving chat: " + e.getMessage());
            }
        }
        if (chatId != null && chatId.equals(currentChatId)) {
            currentChatId = null;
        }
    }

    public void disconnect() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
            webSocketClient = null;
            currentChatId = null;
            Log.d(TAG, "Chat WebSocket disconnected");
        }
    }

    public boolean isConnected() {
        return webSocketClient != null && webSocketClient.isOpen();
    }

    private Message parseMessage(String messageJson) {
        try {
            JSONObject json = new JSONObject(messageJson);
            return parseMessageFromBackend(json);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing message JSON: " + e.getMessage());
            return null;
        }
    }
    
    private Message parseMessageFromBackend(org.json.JSONObject json) {
        try {
            // Handle backend message format
            String content = json.optString("content", "");
            if (content.isEmpty()) {
                content = json.optString("text", "");
            }
            
            // Get sender info
            org.json.JSONObject senderObj = json.optJSONObject("sender");
            String sender = "";
            if (senderObj != null) {
                sender = senderObj.optString("username", "");
            }
            if (sender.isEmpty()) {
                sender = json.optString("sender", "");
            }
            
            // Get timestamp - use SimpleDateFormat for API 24+ compatibility
            String createdAtStr = json.optString("createdAt", "");
            long timestamp = System.currentTimeMillis();
            if (!createdAtStr.isEmpty()) {
                try {
                    // Try ISO 8601 format: "2024-01-01T00:00:00" or "2024-01-01T00:00:00.000000"
                    String dateStr = createdAtStr.replace("Z", "").replace("T", " ");
                    // Remove microseconds if present
                    if (dateStr.contains(".")) {
                        int dotIndex = dateStr.indexOf(".");
                        dateStr = dateStr.substring(0, dotIndex);
                    }
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                    java.util.Date date = sdf.parse(dateStr);
                    timestamp = date.getTime();
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing timestamp: " + e.getMessage());
                    timestamp = json.optLong("timestamp", System.currentTimeMillis());
                }
            } else {
                timestamp = json.optLong("timestamp", System.currentTimeMillis());
            }
            
            // Determine if sent by user (need to compare with current user)
            boolean isSentByUser = false; // Will be set by caller if needed
            
            // ✅ Get attachment info - handle both old format (top-level) and new format (nested)
            String fileUrl = "";
            String fileName = "";
            String fileType = "";
            
            // Check for nested attachment object first (new format)
            org.json.JSONObject attachmentObj = json.optJSONObject("attachment");
            if (attachmentObj != null) {
                fileUrl = attachmentObj.optString("fileUrl", "");
                fileName = attachmentObj.optString("fileName", "");
                fileType = attachmentObj.optString("fileType", "");
            } else {
                // Fall back to top-level fields (old format)
                fileUrl = json.optString("fileUrl", "");
                fileName = json.optString("fileName", "");
                fileType = json.optString("fileType", "");
            }
            
            Message message;
            if (!fileUrl.isEmpty() && !fileType.isEmpty()) {
                message = new Message(content, sender, timestamp, isSentByUser, fileUrl, fileType, fileName);
            } else {
                message = new Message(content, sender, timestamp, isSentByUser);
            }
            
            // Get message ID if available
            if (json.has("id")) {
                Long messageId = json.optLong("id", -1);
                if (messageId > 0) {
                    message.setMessageId(messageId);
                }
            }
            
            // Get reactions if available
            if (json.has("reactions")) {
                org.json.JSONArray reactionsArray = json.optJSONArray("reactions");
                if (reactionsArray != null && reactionsArray.length() > 0) {
                    java.util.Map<String, Integer> reactions = new java.util.HashMap<>();
                    for (int i = 0; i < reactionsArray.length(); i++) {
                        org.json.JSONObject reaction = reactionsArray.optJSONObject(i);
                        if (reaction != null) {
                            String emoji = reaction.optString("emoji", "");
                            if (!emoji.isEmpty()) {
                                reactions.put(emoji, reactions.getOrDefault(emoji, 0) + 1);
                            }
                        }
                    }
                    message.setReactions(reactions);
                }
            }
            
            return message;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing message from backend: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}

