package com.example.occasio.messaging;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatStorage {
    private static final String TAG = "ChatStorage";
    private static final String CHAT_DATA_DIR = "chat_history";
    
    public static void saveMessages(Context context, String chatId, List<Message> messages) {
        if (context == null || chatId == null || messages == null) {
            return;
        }
        
        try {
            File chatDir = new File(context.getFilesDir(), CHAT_DATA_DIR);
            if (!chatDir.exists()) {
                chatDir.mkdirs();
            }
            
            JSONArray messagesArray = new JSONArray();
            for (Message message : messages) {
                JSONObject messageObj = new JSONObject();
                messageObj.put("text", message.getText() != null ? message.getText() : "");
                messageObj.put("sender", message.getSender() != null ? message.getSender() : "");
                messageObj.put("timestamp", message.getTimestamp());
                messageObj.put("isSentByUser", message.isSentByUser());
                
                if (message.getMessageId() != null) {
                    messageObj.put("messageId", message.getMessageId());
                } else {
                    messageObj.put("messageId", JSONObject.NULL);
                }
                
                if (message.getAttachmentId() != null) {
                    messageObj.put("attachmentId", message.getAttachmentId());
                } else {
                    messageObj.put("attachmentId", JSONObject.NULL);
                }
                
                if (message.hasAttachment()) {
                    messageObj.put("attachmentPath", message.getAttachmentPath() != null ? message.getAttachmentPath() : "");
                    messageObj.put("attachmentType", message.getAttachmentType() != null ? message.getAttachmentType() : "");
                    messageObj.put("attachmentName", message.getAttachmentName() != null ? message.getAttachmentName() : "");
                } else {
                    messageObj.put("attachmentPath", "");
                    messageObj.put("attachmentType", "");
                    messageObj.put("attachmentName", "");
                }
                
                if (message.hasReactions()) {
                    JSONObject reactionsObj = new JSONObject();
                    for (Map.Entry<String, Integer> entry : message.getReactions().entrySet()) {
                        reactionsObj.put(entry.getKey(), entry.getValue());
                    }
                    messageObj.put("reactions", reactionsObj);
                } else {
                    messageObj.put("reactions", new JSONObject());
                }
                
                messagesArray.put(messageObj);
            }
            
            File chatFile = new File(chatDir, "chat_" + chatId + ".json");
            FileOutputStream fos = new FileOutputStream(chatFile);
            fos.write(messagesArray.toString().getBytes());
            fos.close();
            
            Log.d(TAG, "Saved " + messages.size() + " messages for chat: " + chatId);
        } catch (Exception e) {
            Log.e(TAG, "Error saving messages: " + e.getMessage(), e);
        }
    }
    
    public static List<Message> loadMessages(Context context, String chatId) {
        List<Message> messages = new ArrayList<>();
        
        if (context == null || chatId == null) {
            return messages;
        }
        
        try {
            File chatDir = new File(context.getFilesDir(), CHAT_DATA_DIR);
            File chatFile = new File(chatDir, "chat_" + chatId + ".json");
            
            if (!chatFile.exists()) {
                Log.d(TAG, "No saved messages found for chat: " + chatId);
                return messages;
            }
            
            FileInputStream fis = new FileInputStream(chatFile);
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);
            fis.close();
            
            String jsonString = new String(buffer);
            JSONArray messagesArray = new JSONArray(jsonString);
            
            for (int i = 0; i < messagesArray.length(); i++) {
                JSONObject messageObj = messagesArray.getJSONObject(i);
                
                String text = messageObj.optString("text", "");
                String sender = messageObj.optString("sender", "");
                long timestamp = messageObj.optLong("timestamp", System.currentTimeMillis());
                boolean isSentByUser = messageObj.optBoolean("isSentByUser", false);
                
                String attachmentPath = messageObj.optString("attachmentPath", "");
                String attachmentType = messageObj.optString("attachmentType", "");
                String attachmentName = messageObj.optString("attachmentName", "");
                
                Message message;
                if (!attachmentPath.isEmpty() && !attachmentType.isEmpty()) {
                    message = new Message(text, sender, timestamp, isSentByUser, attachmentPath, attachmentType, attachmentName);
                } else {
                    message = new Message(text, sender, timestamp, isSentByUser);
                }
                
                if (messageObj.has("messageId") && !messageObj.isNull("messageId")) {
                    try {
                        Long messageId = messageObj.getLong("messageId");
                        message.setMessageId(messageId);
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading messageId: " + e.getMessage());
                    }
                }
                
                if (messageObj.has("attachmentId") && !messageObj.isNull("attachmentId")) {
                    try {
                        Long attachmentId = messageObj.getLong("attachmentId");
                        message.setAttachmentId(attachmentId);
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading attachmentId: " + e.getMessage());
                    }
                }
                
                if (messageObj.has("reactions")) {
                    try {
                        JSONObject reactionsObj = messageObj.getJSONObject("reactions");
                        if (reactionsObj.length() > 0) {
                            Map<String, Integer> reactions = new HashMap<>();
                            for (int j = 0; j < reactionsObj.length(); j++) {
                                String key = reactionsObj.names().getString(j);
                                int value = reactionsObj.getInt(key);
                                reactions.put(key, value);
                            }
                            message.setReactions(reactions);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading reactions: " + e.getMessage());
                    }
                }
                
                messages.add(message);
            }
            
            Log.d(TAG, "Loaded " + messages.size() + " messages for chat: " + chatId);
        } catch (Exception e) {
            Log.e(TAG, "Error loading messages: " + e.getMessage(), e);
        }
        
        return messages;
    }
    
    public static void deleteChatHistory(Context context, String chatId) {
        if (context == null || chatId == null) {
            return;
        }
        
        try {
            File chatDir = new File(context.getFilesDir(), CHAT_DATA_DIR);
            File chatFile = new File(chatDir, "chat_" + chatId + ".json");
            
            if (chatFile.exists()) {
                chatFile.delete();
                Log.d(TAG, "Deleted chat history for: " + chatId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting chat history: " + e.getMessage(), e);
        }
    }
    
    public static void clearAllChatHistory(Context context) {
        if (context == null) {
            return;
        }
        
        try {
            File chatDir = new File(context.getFilesDir(), CHAT_DATA_DIR);
            if (chatDir.exists()) {
                File[] files = chatDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
            }
            Log.d(TAG, "Cleared all chat history");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing chat history: " + e.getMessage(), e);
        }
    }
}

