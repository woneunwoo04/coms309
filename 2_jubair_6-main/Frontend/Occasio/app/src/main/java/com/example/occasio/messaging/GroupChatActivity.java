package com.example.occasio.messaging;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import com.example.occasio.service.ChatWebSocketService;
import com.example.occasio.services.ChatApiService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroupChatActivity extends AppCompatActivity {
    
    private RecyclerView recyclerView;
    private MessageAdapter messageAdapter;
    private EditText messageInput;
    private View sendButton;
    private Button attachButton;
    private Button scrollToBottomButton;
    private TextView chatTitle;
    private TextView groupMembers;
    private LinearLayout headerLayout;
    private List<Message> messageList;
    private List<String> memberList;
    private String currentChatId;
    private String currentChatName;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private String pendingAttachmentPath;
    private String pendingAttachmentType;
    private String pendingAttachmentName;
    private boolean isLoadingMessages = false; // ✅ Track if messages are currently being loaded from backend
    private ChatWebSocketService chatWebSocketService;
    private ChatApiService chatApiService;
    private String currentUsername;
    private Long currentUserId;
    private Long currentGroupId; // Store groupId for group-specific endpoints

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        try {
            android.content.SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            currentUsername = prefs.getString("username", "");
            if (currentUsername.isEmpty()) {
                currentUsername = "demo_user";
                android.content.SharedPreferences.Editor editor = prefs.edit();
                editor.putString("username", currentUsername);
                editor.apply();
            }
            
            initializeFilePickers();
            initializeViews();
            setupRecyclerView();
            setupClickListeners();
            
            Intent intent = getIntent();
            if (intent != null) {
                currentChatId = intent.getStringExtra("chat_id");
                currentChatName = intent.getStringExtra("chat_name");
                String intentUsername = intent.getStringExtra("username");
                
                // Try to get groupId from intent
                if (intent.hasExtra("groupId")) {
                    currentGroupId = intent.getLongExtra("groupId", -1);
                    if (currentGroupId == -1) {
                        currentGroupId = null;
                    }
                }
                
                if (intentUsername != null && !intentUsername.isEmpty()) {
                    currentUsername = intentUsername;
                }
                
                if (currentChatName == null || currentChatName.isEmpty()) {
                    currentChatName = "Group Chat";
                }
                
                if (chatTitle != null) {
                    chatTitle.setText(currentChatName);
                }
                if (groupMembers != null && memberList != null) {
                    groupMembers.setText(memberList.size() + " members");
                }
                
                // Fetch chat to get groupId if not provided
                if (currentGroupId == null && currentChatId != null && !currentChatId.isEmpty()) {
                    fetchChatAndGroupId();
                } else {
                    loadMessages();
                }
            } else {
                currentChatName = "Group Chat";
                if (chatTitle != null) {
                    chatTitle.setText(currentChatName);
                }
            }
            
            setupChatWebSocket();
        } catch (Exception e) {
            android.util.Log.e("GroupChatActivity", "Error in onCreate: " + e.getMessage(), e);
        }
    }

    private void initializeFilePickers() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri fileUri = result.getData().getData();
                    if (fileUri != null) {
                        handleFileAttachment(fileUri, "pdf");
                    }
                }
            }
        );
        
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        handleFileAttachment(imageUri, "image");
                    }
                }
            }
        );
    }
    
    private void handleFileAttachment(Uri fileUri, String fileType) {
        try {
            String fileName = getFileName(fileUri);
            if (fileName == null) {
                fileName = fileType.equals("image") ? "image.jpg" : "document.pdf";
            }
            
            String savedPath = saveAttachmentToStorage(fileUri, fileName);
            if (savedPath != null) {
                pendingAttachmentPath = savedPath;
                pendingAttachmentType = fileType;
                pendingAttachmentName = fileName;
                
                sendMessageWithAttachment();
            }
        } catch (Exception e) {
            Log.e("GroupChatActivity", "Error handling attachment: " + e.getMessage());
        }
    }
    
    private void sendMessageWithAttachment() {
        if (pendingAttachmentPath == null || pendingAttachmentType == null || currentChatId == null || currentUserId == null) {
            return;
        }
        
        // ✅ Get caption from input - if empty, use empty string (not null)
        String messageText = "";
        if (messageInput != null) {
            String inputText = messageInput.getText().toString();
            if (inputText != null) {
                messageText = inputText.trim();
            }
        }
        // Ensure it's never null
        if (messageText == null) {
            messageText = "";
        }
        
        java.io.File attachmentFile = new java.io.File(pendingAttachmentPath);
        if (!attachmentFile.exists()) {
            return;
        }
        
        // ✅ Use file:// URI directly - file is already copied to cache directory
        // Both downloaded images and gallery images are handled the same way after copying to cache
        Uri fileUri = android.net.Uri.fromFile(attachmentFile);
        android.util.Log.d("GroupChatActivity", "Sending attachment - file: " + attachmentFile.getAbsolutePath() + 
                          ", name: " + pendingAttachmentName + ", type: " + pendingAttachmentType);
        
        try {
            Long chatIdLong = Long.parseLong(currentChatId);
            // Use group-specific endpoint if groupId is available
            if (currentGroupId != null && chatApiService != null) {
                android.util.Log.d("GroupChatActivity", "Sending file attachment - groupId: " + currentGroupId + ", userId: " + currentUserId + ", path: " + pendingAttachmentPath);
                chatApiService.sendGroupFileMessage(currentGroupId, currentUserId, fileUri, messageText, null, new ChatApiService.MessageCallback() {
                    @Override
                    public void onSuccess(Message message) {
                        runOnUiThread(() -> {
                            android.util.Log.d("GroupChatActivity", "File message sent successfully - messageId: " + message.getMessageId() + 
                                          ", hasAttachment: " + message.hasAttachment() +
                                          ", attachmentPath: " + (message.getAttachmentPath() != null ? message.getAttachmentPath() : "null") +
                                          ", attachmentName: " + (message.getAttachmentName() != null ? message.getAttachmentName() : "null") +
                                          ", attachmentType: " + (message.getAttachmentType() != null ? message.getAttachmentType() : "null"));
                            
                            // ✅ Always add/update the message in the list
                            // Check if message with same ID already exists
                            boolean messageExists = false;
                            int existingIndex = -1;
                            for (int i = 0; i < messageList.size(); i++) {
                                if (messageList.get(i).getMessageId() != null && 
                                    message.getMessageId() != null &&
                                    messageList.get(i).getMessageId().equals(message.getMessageId())) {
                                    messageExists = true;
                                    existingIndex = i;
                                    break;
                                }
                            }
                            
                            if (messageExists && existingIndex >= 0) {
                                // Update existing message
                                // ✅ Ensure isSentByUser is set correctly for sent messages
                                message.setSentByUser(true);
                                messageList.set(existingIndex, message);
                                messageAdapter.notifyItemChanged(existingIndex);
                                android.util.Log.d("GroupChatActivity", "Updated existing message at index " + existingIndex + ", isSentByUser: " + message.isSentByUser());
                            } else {
                                // Add new message
                                // ✅ Ensure isSentByUser is set correctly for sent messages
                                message.setSentByUser(true);
                            messageList.add(message);
                            messageAdapter.notifyItemInserted(messageList.size() - 1);
                                android.util.Log.d("GroupChatActivity", "Added new message at index " + (messageList.size() - 1) + ", isSentByUser: " + message.isSentByUser());
                                // ✅ Use smooth scroll when user sends a message (better UX)
                                scrollToBottomSmooth();
                            }
                            saveMessages();
                            
                            if (messageInput != null) {
                                messageInput.setText("");
                            }
                            
                            pendingAttachmentPath = null;
                            pendingAttachmentType = null;
                            pendingAttachmentName = null;
                            
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            android.util.Log.e("GroupChatActivity", "Error sending attachment: " + error);
                            Toast.makeText(GroupChatActivity.this, "Error sending attachment: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } else if (chatApiService != null) {
                // Fallback to chat endpoint
                android.util.Log.d("GroupChatActivity", "Sending file attachment - chatId: " + chatIdLong + ", userId: " + currentUserId + ", path: " + pendingAttachmentPath);
                chatApiService.sendFileMessage(chatIdLong, currentUserId, fileUri, messageText, null, new ChatApiService.MessageCallback() {
                    @Override
                    public void onSuccess(Message message) {
                        runOnUiThread(() -> {
                            android.util.Log.d("GroupChatActivity", "File message sent successfully - messageId: " + message.getMessageId() + 
                                          ", hasAttachment: " + message.hasAttachment() +
                                          ", attachmentPath: " + (message.getAttachmentPath() != null ? message.getAttachmentPath() : "null") +
                                          ", attachmentName: " + (message.getAttachmentName() != null ? message.getAttachmentName() : "null") +
                                          ", attachmentType: " + (message.getAttachmentType() != null ? message.getAttachmentType() : "null"));
                            
                            // ✅ Always add/update the message in the list
                            // Check if message with same ID already exists
                            boolean messageExists = false;
                            int existingIndex = -1;
                            for (int i = 0; i < messageList.size(); i++) {
                                if (messageList.get(i).getMessageId() != null && 
                                    message.getMessageId() != null &&
                                    messageList.get(i).getMessageId().equals(message.getMessageId())) {
                                    messageExists = true;
                                    existingIndex = i;
                                    break;
                                }
                            }
                            
                            if (messageExists && existingIndex >= 0) {
                                // Update existing message
                                // ✅ Ensure isSentByUser is set correctly for sent messages
                                message.setSentByUser(true);
                                messageList.set(existingIndex, message);
                                messageAdapter.notifyItemChanged(existingIndex);
                                android.util.Log.d("GroupChatActivity", "Updated existing message at index " + existingIndex + ", isSentByUser: " + message.isSentByUser());
                            } else {
                                // Add new message
                                // ✅ Ensure isSentByUser is set correctly for sent messages
                                message.setSentByUser(true);
                            messageList.add(message);
                            messageAdapter.notifyItemInserted(messageList.size() - 1);
                                android.util.Log.d("GroupChatActivity", "Added new message at index " + (messageList.size() - 1) + ", isSentByUser: " + message.isSentByUser());
                                // ✅ Use smooth scroll when user sends a message (better UX)
                                scrollToBottomSmooth();
                            }
                            saveMessages();
                            
                            if (messageInput != null) {
                                messageInput.setText("");
                            }
                            
                            pendingAttachmentPath = null;
                            pendingAttachmentType = null;
                            pendingAttachmentName = null;
                            
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            android.util.Log.e("GroupChatActivity", "Error sending attachment: " + error);
                            Toast.makeText(GroupChatActivity.this, "Error sending attachment: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } else {
                android.util.Log.e("GroupChatActivity", "chatApiService is null, cannot send attachment");
                Toast.makeText(this, "Error: Chat service not available", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            android.util.Log.e("GroupChatActivity", "Error parsing chat ID: " + e.getMessage());
            Toast.makeText(this, "Error sending attachment", Toast.LENGTH_SHORT).show();
        }
    }
    
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result;
    }
    
    private String saveAttachmentToStorage(Uri fileUri, String fileName) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            if (inputStream == null) return null;
            
            File attachmentsDir = new File(getFilesDir(), "chat_attachments");
            if (!attachmentsDir.exists()) {
                attachmentsDir.mkdirs();
            }
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            String safeFileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            File attachmentFile = new File(attachmentsDir, timestamp + "_" + safeFileName);
            
            FileOutputStream outputStream = new FileOutputStream(attachmentFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outputStream.close();
            
            return attachmentFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("GroupChatActivity", "Error saving attachment: " + e.getMessage());
            return null;
        }
    }

    private Button searchButton;
    private Button groupSettingsButton;
    
    private void initializeViews() {
        recyclerView = findViewById(R.id.messages_recycler_view);
        messageInput = findViewById(R.id.message_input);
        sendButton = findViewById(R.id.send_button);
        attachButton = findViewById(R.id.attach_button);
        chatTitle = findViewById(R.id.chat_title);
        groupMembers = findViewById(R.id.group_members);
        headerLayout = findViewById(R.id.header_container);
        searchButton = findViewById(R.id.search_button);
        groupSettingsButton = findViewById(R.id.group_settings_button);
        scrollToBottomButton = findViewById(R.id.scroll_to_bottom_btn);
        
        // ✅ Set click listener for scroll to bottom button
        if (scrollToBottomButton != null) {
            scrollToBottomButton.setOnClickListener(v -> {
                scrollToBottomSmooth();
            });
        }
        messageList = new ArrayList<>();
        
        chatApiService = new ChatApiService(this);
        
        fetchUserId();
        
        // ✅ Initialize empty member list - will be loaded from backend
        memberList = new ArrayList<>();
    }
    
    private void fetchUserId() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            currentUsername = prefs.getString("username", "");
            if (currentUsername.isEmpty()) {
                currentUsername = "demo_user";
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("username", currentUsername);
                editor.apply();
            }
        }
        
        if (chatApiService != null) {
            chatApiService.getUserIdFromUsername(currentUsername, new ChatApiService.UserIdCallback() {
                @Override
                public void onSuccess(Long userId) {
                    currentUserId = userId;
                }
                
                @Override
                public void onError(String error) {
                    android.util.Log.e("GroupChatActivity", "Error fetching user ID: " + error);
                    currentUserId = 1L;
                }
            });
        } else {
            currentUserId = 1L;
        }
    }

    private void setupRecyclerView() {
        if (recyclerView == null) {
            android.util.Log.e("GroupChatActivity", "RecyclerView is null!");
            return;
        }
        
        if (currentUserId == null) {
            currentUserId = 1L;
        }
        
        messageAdapter = new MessageAdapter(this, messageList, currentUserId);
        messageAdapter.setOnReactionClickListener((message, emoji) -> {
            try {
                if (message == null || emoji == null || emoji.isEmpty() || currentUserId == null) {
                    return;
                }
                
                java.util.Map<String, Integer> reactions = message.getReactions();
                boolean hasReaction = reactions != null && reactions.containsKey(emoji);
                
                // ✅ Update UI immediately (optimistic update)
                if (hasReaction) {
                    message.removeReaction(emoji);
                } else {
                    message.addReaction(emoji);
                }
                
                // ✅ Update UI instantly - no waiting for API
                int position = messageList.indexOf(message);
                if (position >= 0) {
                    messageAdapter.notifyItemChanged(position);
                    saveMessages();
                }
                
                // ✅ Send API request in background (WebSocket will handle real-time updates)
                if (message.getMessageId() != null && chatApiService != null) {
                    if (hasReaction) {
                        chatApiService.removeReaction(message.getMessageId(), currentUserId, emoji, new ChatApiService.VoidCallback() {
                            @Override
                            public void onSuccess() {
                                // API call succeeded - WebSocket event will update UI
                                android.util.Log.d("GroupChatActivity", "Reaction removed successfully");
                            }
                            
                            @Override
                            public void onError(String error) {
                                // Rollback on error
                                runOnUiThread(() -> {
                                    message.addReaction(emoji);
                                    int position = messageList.indexOf(message);
                                    if (position >= 0) {
                                        messageAdapter.notifyItemChanged(position);
                saveMessages();
                                    }
                                    android.util.Log.e("GroupChatActivity", "Error removing reaction: " + error);
                                });
                            }
                        });
                    } else {
                        chatApiService.addReaction(message.getMessageId(), currentUserId, emoji, new ChatApiService.VoidCallback() {
                            @Override
                            public void onSuccess() {
                                // API call succeeded - WebSocket event will update UI
                                android.util.Log.d("GroupChatActivity", "Reaction added successfully");
                            }
                            
                            @Override
                            public void onError(String error) {
                                // Rollback on error
                                runOnUiThread(() -> {
                                    message.removeReaction(emoji);
                                    int position = messageList.indexOf(message);
                                    if (position >= 0) {
                                        messageAdapter.notifyItemChanged(position);
                                        saveMessages();
                                    }
                                    android.util.Log.e("GroupChatActivity", "Error adding reaction: " + error);
                                });
                            }
                        });
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("GroupChatActivity", "Error toggling reaction: " + e.getMessage(), e);
            }
        });
        messageAdapter.setOnMessageLongClickListener((message) -> {
            showMessageOptionsDialog(message);
        });
        messageAdapter.setOnAttachmentClickListener((message) -> {
            handleAttachmentClick(message);
        });
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(messageAdapter);
        
        recyclerView.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                if (layoutManager.findFirstVisibleItemPosition() == 0 && messageList.size() > 0) {
                    loadOlderMessages(layoutManager);
                }
                
                // ✅ Show/hide scroll to bottom button based on scroll position
                updateScrollToBottomButtonVisibility();
            }
        });
    }
    
    private boolean isLoadingOlderMessages = false;
    
    private void loadOlderMessages(LinearLayoutManager layoutManager) {
        if (currentChatId == null || currentChatId.isEmpty() || chatApiService == null || messageList.isEmpty() || isLoadingOlderMessages) {
            return;
        }
        
        try {
            Long chatIdLong = Long.parseLong(currentChatId);
            Message oldestMessage = messageList.get(0);
            if (oldestMessage == null || oldestMessage.getTimestamp() == 0) {
                return;
            }
            
            isLoadingOlderMessages = true;
            
        // Use Date for API 24+ compatibility instead of java.time.Instant (requires API 26)
        java.util.Date date = new java.util.Date(oldestMessage.getTimestamp());
        // Format timestamp as ISO 8601 string for API call
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
        String timestamp = sdf.format(date);
            
            chatApiService.getMessagesSince(chatIdLong, timestamp, new ChatApiService.MessagesListCallback() {
                @Override
                public void onSuccess(List<Message> olderMessages) {
                    runOnUiThread(() -> {
                        isLoadingOlderMessages = false;
                        if (olderMessages != null && !olderMessages.isEmpty()) {
                            messageList.addAll(0, olderMessages);
                            messageAdapter.notifyItemRangeInserted(0, olderMessages.size());
                            saveMessages();
                            
                            layoutManager.scrollToPositionWithOffset(olderMessages.size(), 0);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        isLoadingOlderMessages = false;
                    });
                }
            });
        } catch (Exception e) {
            isLoadingOlderMessages = false;
        }
    }
    
    private void showMessageOptionsDialog(Message message) {
        if (message == null || !message.isSentByUser()) {
            return;
        }
        
        java.util.List<String> options = new java.util.ArrayList<>();
        options.add("✏️ Edit");
        options.add("🗑️ Delete");
        
        if (message.hasAttachment() && message.getAttachmentId() != null) {
            options.add("📎 Delete Attachment");
        }
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Message Options");
        builder.setItems(options.toArray(new String[0]), (dialog, which) -> {
            if (which == 0) {
                editMessage(message);
            } else if (which == 1) {
                deleteMessage(message);
            } else if (which == 2 && message.hasAttachment()) {
                deleteAttachment(message);
            }
        });
        builder.show();
    }
    
    private void deleteAttachment(Message message) {
        if (message == null || !message.hasAttachment() || message.getAttachmentId() == null || currentUserId == null) {
            return;
        }
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Attachment");
        builder.setMessage("Are you sure you want to delete this attachment?");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            if (chatApiService != null) {
                chatApiService.deleteAttachment(message.getAttachmentId(), currentUserId, new ChatApiService.VoidCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            message.setAttachmentPath(null);
                            message.setAttachmentType(null);
                            message.setAttachmentName(null);
                            int position = messageList.indexOf(message);
                            if (position >= 0) {
                                messageAdapter.notifyItemChanged(position);
                                saveMessages();
                            }
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(GroupChatActivity.this, "Error deleting attachment: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void editMessage(Message message) {
        if (message == null || currentUserId == null) {
            return;
        }
        
        if (message.getMessageId() == null) {
            return;
        }
        
        android.widget.EditText editText = new android.widget.EditText(this);
        editText.setText(message.getText());
        editText.setSelection(editText.getText().length());
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Edit Message");
        builder.setView(editText);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String newContent = editText.getText().toString().trim();
            if (!newContent.isEmpty() && !newContent.equals(message.getText())) {
                if (chatApiService != null) {
                    chatApiService.editMessage(message.getMessageId(), currentUserId, newContent, new ChatApiService.MessageCallback() {
                        @Override
                        public void onSuccess(Message updatedMessage) {
                            runOnUiThread(() -> {
                                int position = messageList.indexOf(message);
                                if (position >= 0) {
                                    message.setText(updatedMessage.getText());
                                    messageAdapter.notifyItemChanged(position);
                                    saveMessages();
                                }
                            });
                        }
                        
                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                Toast.makeText(GroupChatActivity.this, "Error updating message: " + error, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void deleteMessage(Message message) {
        if (message == null || currentUserId == null) {
            return;
        }
        
        if (message.getMessageId() == null) {
            return;
        }
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Message");
        builder.setMessage("Are you sure you want to delete this message?");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            if (chatApiService != null) {
                chatApiService.deleteMessage(message.getMessageId(), currentUserId, new ChatApiService.VoidCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            int position = messageList.indexOf(message);
                            if (position >= 0) {
                                messageList.remove(position);
                                messageAdapter.notifyItemRemoved(position);
                                saveMessages();
                            }
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(GroupChatActivity.this, "Error deleting message: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void setupClickListeners() {
        if (sendButton != null) {
            sendButton.setOnClickListener(v -> sendMessage());
        }
        
        if (attachButton != null) {
            attachButton.setOnClickListener(v -> showAttachmentOptions());
        }
        
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> showSearchDialog());
        }
        
        if (groupSettingsButton != null) {
            groupSettingsButton.setOnClickListener(v -> showGroupSettingsMenu());
        }
        
        Button backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        if (messageInput != null) {
            messageInput.setOnEditorActionListener((v, actionId, event) -> {
                sendMessage();
                return true;
            });
        }
        
        if (headerLayout != null) {
            headerLayout.setOnClickListener(v -> showGroupMembersDialog());
        }
        
        if (chatTitle != null) {
            chatTitle.setOnClickListener(v -> showGroupMembersDialog());
        }
        if (groupMembers != null) {
            groupMembers.setOnClickListener(v -> showGroupMembersDialog());
        }
    }
    
    private void sendMessage() {
        if (messageInput == null || currentUserId == null) {
            return;
        }
        
        // Need groupId for group messages
        if (currentGroupId == null) {
            // Fallback to chat endpoint if groupId not available
            sendMessageViaChat();
            return;
        }
        
        String messageText = messageInput.getText().toString().trim();
        if (messageText.isEmpty()) {
            return;
        }
        
        Message localMessage = new Message(messageText, currentUsername != null ? currentUsername : "You", System.currentTimeMillis(), true);
        // ✅ Ensure text messages don't have attachment data
        localMessage.setAttachmentPath(null);
        localMessage.setAttachmentType(null);
        localMessage.setAttachmentName(null);
        messageList.add(localMessage);
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        saveMessages();
        // ✅ Scroll to bottom when user sends a message
        scrollToBottomSmooth();
        
        messageInput.setText("");
        
        // Use group-specific endpoint
        if (chatApiService != null) {
            chatApiService.sendGroupMessage(currentGroupId, currentUserId, messageText, null, new ChatApiService.MessageCallback() {
                @Override
                public void onSuccess(Message message) {
                    runOnUiThread(() -> {
                        int position = messageList.indexOf(localMessage);
                        if (position >= 0) {
                            // ✅ Ensure text messages don't have attachment data
                            // If local message was a text message (no attachment), clear attachment fields from API response
                            if (!localMessage.hasAttachment()) {
                                message.setAttachmentPath(null);
                                message.setAttachmentType(null);
                                message.setAttachmentName(null);
                                message.setAttachmentId(null);
                                android.util.Log.d("GroupChatActivity", "Cleared attachment fields from text message - messageId: " + message.getMessageId());
                            }
                            
                            // ✅ Check if this is the same message by content/timestamp to avoid duplicates
                            // The WebSocket might have already added it, so we should update instead of replace
                            // ✅ Preserve isSentByUser from local message (which is correct - user sent it)
                            if (localMessage.isSentByUser()) {
                                message.setSentByUser(true);
                            }
                            messageList.set(position, message);
                            messageAdapter.notifyItemChanged(position);
                            saveMessages();
                            // ✅ Don't scroll here - let WebSocket handler scroll when it receives the message
                            // This prevents double scrolling
                            android.util.Log.d("GroupChatActivity", "Updated local message with API response, messageId: " + message.getMessageId() + 
                                            ", hasAttachment: " + message.hasAttachment() + ", isSentByUser: " + message.isSentByUser());
                        } else {
                            // ✅ Message not found by indexOf - might have been added by WebSocket already
                            // Check if it exists by messageId
                            boolean foundById = false;
                            for (int i = 0; i < messageList.size(); i++) {
                                Message m = messageList.get(i);
                                if (m.getMessageId() != null && message.getMessageId() != null &&
                                    m.getMessageId().equals(message.getMessageId())) {
                                    // Update existing message
                                    if (!localMessage.hasAttachment()) {
                                        message.setAttachmentPath(null);
                                        message.setAttachmentType(null);
                                        message.setAttachmentName(null);
                                        message.setAttachmentId(null);
                                    }
                                    // ✅ Preserve isSentByUser from local message (which is correct - user sent it)
                                    if (localMessage.isSentByUser()) {
                                        message.setSentByUser(true);
                                    }
                                    messageList.set(i, message);
                                    messageAdapter.notifyItemChanged(i);
                                    saveMessages();
                                    foundById = true;
                                    android.util.Log.d("GroupChatActivity", "Updated message by ID (was added by WebSocket) - messageId: " + message.getMessageId() + ", isSentByUser: " + message.isSentByUser());
                                    break;
                                }
                            }
                            if (!foundById) {
                                // Message doesn't exist, add it
                                if (!localMessage.hasAttachment()) {
                                    message.setAttachmentPath(null);
                                    message.setAttachmentType(null);
                                    message.setAttachmentName(null);
                                    message.setAttachmentId(null);
                                }
                                // ✅ Preserve isSentByUser from local message (which is correct - user sent it)
                                if (localMessage.isSentByUser()) {
                                    message.setSentByUser(true);
                                }
                                messageList.add(message);
                                messageAdapter.notifyItemInserted(messageList.size() - 1);
                                saveMessages();
                                scrollToBottomSmooth();
                                android.util.Log.d("GroupChatActivity", "Added message from API response (not found in list) - messageId: " + message.getMessageId() + ", isSentByUser: " + message.isSentByUser());
                            }
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    android.util.Log.e("GroupChatActivity", "Error sending group message: " + error);
                    runOnUiThread(() -> {
                        Toast.makeText(GroupChatActivity.this, "Error sending message", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }
    
    private void sendMessageViaChat() {
        if (messageInput == null || currentChatId == null || currentUserId == null) {
            return;
        }
        
        String messageText = messageInput.getText().toString().trim();
        if (messageText.isEmpty()) {
            return;
        }
        
        Message localMessage = new Message(messageText, currentUsername != null ? currentUsername : "You", System.currentTimeMillis(), true);
        // ✅ Ensure text messages don't have attachment data
        localMessage.setAttachmentPath(null);
        localMessage.setAttachmentType(null);
        localMessage.setAttachmentName(null);
        messageList.add(localMessage);
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        saveMessages();
        
        messageInput.setText("");
        
        try {
            Long chatIdLong = Long.parseLong(currentChatId);
            if (chatApiService != null) {
                chatApiService.sendMessage(chatIdLong, currentUserId, messageText, null, new ChatApiService.MessageCallback() {
                    @Override
                    public void onSuccess(Message message) {
                        runOnUiThread(() -> {
                            int position = messageList.indexOf(localMessage);
                            if (position >= 0) {
                                messageList.set(position, message);
                                messageAdapter.notifyItemChanged(position);
                                saveMessages();
                            }
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        android.util.Log.e("GroupChatActivity", "Error sending message: " + error);
                    }
                });
            }
        } catch (Exception e) {
            android.util.Log.e("GroupChatActivity", "Error parsing chat ID: " + e.getMessage());
        }
    }
    
    private void showSearchDialog() {
        if (currentChatId == null || currentChatId.isEmpty()) {
            return;
        }
        
        android.widget.EditText searchInput = new android.widget.EditText(this);
        searchInput.setHint("Search messages...");
        searchInput.setPadding(32, 16, 32, 16);
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Search Messages");
        builder.setView(searchInput);
        builder.setPositiveButton("Search", (dialog, which) -> {
            String query = searchInput.getText().toString().trim();
            if (!query.isEmpty()) {
                searchMessages(query);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void searchMessages(String query) {
        if (currentChatId == null || currentChatId.isEmpty() || chatApiService == null) {
            return;
        }
        
        
        try {
            Long chatIdLong = Long.parseLong(currentChatId);
            chatApiService.searchMessages(chatIdLong, query, new ChatApiService.SearchCallback() {
                @Override
                public void onSuccess(List<Message> searchResults) {
                    runOnUiThread(() -> {
                        if (searchResults == null || searchResults.isEmpty()) {
                            return;
                        }
                        
                        showSearchResults(searchResults, query);
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(GroupChatActivity.this, "Search error: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            android.util.Log.e("GroupChatActivity", "Error parsing chat ID: " + e.getMessage());
        }
    }
    
    private void showSearchResults(List<Message> searchResults, String query) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Search Results (" + searchResults.size() + " found)");
        
        android.widget.ListView listView = new android.widget.ListView(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, 
            android.R.layout.simple_list_item_1);
        
        for (Message message : searchResults) {
            String preview = message.getText();
            if (preview.length() > 50) {
                preview = preview.substring(0, 50) + "...";
            }
            adapter.add(message.getSender() + ": " + preview);
        }
        
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Message selectedMessage = searchResults.get(position);
            int messageIndex = messageList.indexOf(selectedMessage);
            if (messageIndex >= 0) {
                recyclerView.scrollToPosition(messageIndex);
                recyclerView.smoothScrollToPosition(messageIndex);
            }
        });
        
        builder.setView(listView);
        builder.setPositiveButton("Close", null);
        builder.show();
    }
    
    private void showAttachmentOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Attach File");
        builder.setItems(new String[]{"📷 Photo", "📄 PDF Document"}, (dialog, which) -> {
            if (which == 0) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                imagePickerLauncher.launch(intent);
            } else if (which == 1) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("application/pdf");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                filePickerLauncher.launch(intent);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void handleAttachmentClick(Message message) {
        if (message == null || !message.hasAttachment()) {
            return;
        }
        
        String attachmentPath = message.getAttachmentPath();
        String attachmentType = message.getAttachmentType();
        Long attachmentId = message.getMessageId();
        
        if (attachmentId == null || attachmentId <= 0) {
            if (attachmentPath != null && !attachmentPath.isEmpty()) {
                try {
                    android.net.Uri uri = android.net.Uri.parse(attachmentPath);
                    openAttachment(uri, attachmentType, message.getAttachmentName());
                } catch (Exception e) {
                }
            } else {
            }
            return;
        }
        
        if (chatApiService == null) {
            return;
        }
        
        
        chatApiService.downloadAttachment(attachmentId, new ChatApiService.BytesCallback() {
            @Override
            public void onSuccess(byte[] data) {
                runOnUiThread(() -> {
                    try {
                        java.io.File tempFile = new java.io.File(getCacheDir(), message.getAttachmentName() != null ? 
                            message.getAttachmentName() : "attachment");
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                        fos.write(data);
                        fos.close();
                        
                        android.net.Uri uri = android.net.Uri.fromFile(tempFile);
                        openAttachment(uri, attachmentType, message.getAttachmentName());
                    } catch (Exception e) {
                        android.util.Log.e("GroupChatActivity", "Error saving attachment: " + e.getMessage());
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(GroupChatActivity.this, "Error downloading: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void openAttachment(android.net.Uri uri, String attachmentType, String fileName) {
        try {
            java.io.File file = null;
            String mimeType = "*/*";
            
            // ✅ Determine MIME type from file extension or attachment type
            if (uri.getScheme() != null && uri.getScheme().equals("file")) {
                file = new java.io.File(uri.getPath());
                if (file.exists()) {
                    String filePath = file.getAbsolutePath();
                    String extension = "";
                    int lastDot = filePath.lastIndexOf('.');
                    if (lastDot > 0 && lastDot < filePath.length() - 1) {
                        extension = filePath.substring(lastDot + 1).toLowerCase();
                    }
                    
                    // Set specific MIME type based on extension
                    if (extension.equals("jpg") || extension.equals("jpeg")) {
                        mimeType = "image/jpeg";
                    } else if (extension.equals("png")) {
                        mimeType = "image/png";
                    } else if (extension.equals("gif")) {
                        mimeType = "image/gif";
                    } else if (extension.equals("webp")) {
                        mimeType = "image/webp";
                    } else if (extension.equals("pdf")) {
                        mimeType = "application/pdf";
                    } else if (attachmentType != null && attachmentType.contains("image")) {
                        mimeType = "image/jpeg"; // Default to JPEG for images
                    } else if (attachmentType != null && attachmentType.contains("pdf")) {
                        mimeType = "application/pdf";
                    }
                }
            } else if (uri.getScheme() != null && uri.getScheme().equals("content")) {
                // For content:// URIs, get MIME type from ContentResolver
                String contentMimeType = getContentResolver().getType(uri);
                if (contentMimeType != null && !contentMimeType.isEmpty()) {
                    mimeType = contentMimeType;
            } else {
                    // Fallback to attachment type
                    if (attachmentType != null && attachmentType.contains("image")) {
                        mimeType = "image/jpeg";
                    } else if (attachmentType != null && attachmentType.contains("pdf")) {
                        mimeType = "application/pdf";
                    }
                }
            } else {
                // Fallback to attachment type
                if (attachmentType != null && attachmentType.contains("image")) {
                    mimeType = "image/jpeg";
                } else if (attachmentType != null && attachmentType.contains("pdf")) {
                    mimeType = "application/pdf";
                }
            }
            
            android.util.Log.d("GroupChatActivity", "Opening attachment - URI: " + uri + ", MIME type: " + mimeType + ", file exists: " + (file != null ? file.exists() : "N/A"));
            
            // ✅ Create intent with specific MIME type (not wildcard)
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // ✅ Try to resolve activity
            android.content.pm.ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null) {
                android.content.Intent chooser = android.content.Intent.createChooser(intent, "Open " + (fileName != null ? fileName : "attachment"));
                startActivity(chooser);
            } else {
                // ✅ Fallback: Try with wildcard MIME type
                android.util.Log.w("GroupChatActivity", "No app found for specific MIME type, trying wildcard");
                intent.setDataAndType(uri, "*/*");
                resolveInfo = getPackageManager().resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
                if (resolveInfo != null) {
                    android.content.Intent chooser = android.content.Intent.createChooser(intent, "Open " + (fileName != null ? fileName : "attachment"));
                    startActivity(chooser);
                } else {
                    android.util.Log.e("GroupChatActivity", "No app available to open file - URI: " + uri + ", MIME: " + mimeType);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("GroupChatActivity", "Error opening attachment: " + e.getMessage(), e);
        }
    }

    private void loadMessages() {
        if (currentChatId == null || currentChatId.isEmpty()) {
            return;
        }
        
        // ✅ Prevent multiple simultaneous loads
        if (isLoadingMessages) {
            android.util.Log.d("GroupChatActivity", "loadMessages() already in progress, skipping duplicate call");
            return;
        }
        
        // ✅ Set loading flag to prevent WebSocket messages from being added during load
        isLoadingMessages = true;
        
        List<Message> cachedMessages = ChatStorage.loadMessages(this, currentChatId);
        if (cachedMessages != null && !cachedMessages.isEmpty()) {
            messageList.clear();
            messageList.addAll(cachedMessages);
            messageAdapter.notifyDataSetChanged();
            // ✅ Update button visibility after loading cached messages
            updateScrollToBottomButtonVisibility();
        }
        
        // Use group-specific endpoint if groupId is available
        if (currentGroupId != null && chatApiService != null) {
            // ✅ Pass currentUserId to getMessagesByGroup so parseMessageFromJson can correctly set isSentByUser
            android.util.Log.d("GroupChatActivity", "Loading messages from backend for groupId: " + currentGroupId + ", chatId: " + currentChatId);
            chatApiService.getMessagesByGroup(currentGroupId, currentUserId, new ChatApiService.MessagesListCallback() {
                @Override
                public void onSuccess(List<Message> backendMessages) {
                    runOnUiThread(() -> {
                        isLoadingMessages = false; // ✅ Clear loading flag
                        android.util.Log.d("GroupChatActivity", "✅ Loaded " + (backendMessages != null ? backendMessages.size() : 0) + " messages from backend for groupId: " + currentGroupId);
                        if (backendMessages != null && !backendMessages.isEmpty()) {
                            // ✅ Always replace messages with backend messages to ensure we have the latest data
                            // This prevents duplicates when WebSocket messages arrive after backend load
                            int previousCount = messageList.size();
                            messageList.clear();
                            // ✅ isSentByUser is now set correctly by parseMessageFromJson(currentUserId)
                            // No need to manually set it here
                            messageList.addAll(backendMessages);
                            messageAdapter.notifyDataSetChanged();
                            saveMessages();
                            // ✅ Update button visibility after loading messages
                            updateScrollToBottomButtonVisibility();
                            android.util.Log.d("GroupChatActivity", "Updated messages from backend - previous: " + previousCount + 
                                        ", backend: " + backendMessages.size() + ", current: " + messageList.size());
                        } else {
                            // ✅ Backend returned empty list - clear cache and show empty state
                            android.util.Log.d("GroupChatActivity", "⚠️ Backend returned empty message list for groupId: " + currentGroupId + 
                                        " (previous count: " + messageList.size() + ")");
                            messageList.clear();
                            messageAdapter.notifyDataSetChanged();
                            saveMessages();
                            updateScrollToBottomButtonVisibility();
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        isLoadingMessages = false; // ✅ Clear loading flag on error
                    android.util.Log.e("GroupChatActivity", "Error loading group messages: " + error);
                    // Fallback to chat endpoint
                    loadMessagesViaChat();
                    });
                }
            });
        } else if (currentChatId != null && !currentChatId.isEmpty() && chatApiService != null) {
            // Fallback to chat endpoint if groupId not available
            loadMessagesViaChat();
        }
    }
    
    private void loadMessagesViaChat() {
        try {
            Long chatIdLong = Long.parseLong(currentChatId);
            
            // ✅ Prevent multiple simultaneous loads
            if (isLoadingMessages) {
                android.util.Log.d("GroupChatActivity", "loadMessagesViaChat() already in progress, skipping duplicate call");
                return;
            }
            
            // ✅ Set loading flag to prevent WebSocket messages from being added during load
            isLoadingMessages = true;
            // ✅ Pass currentUserId to getMessagesByChat so parseMessageFromJson can correctly set isSentByUser
            android.util.Log.d("GroupChatActivity", "Loading messages from backend for chatId: " + chatIdLong);
            chatApiService.getMessagesByChat(chatIdLong, currentUserId, new ChatApiService.MessagesListCallback() {
                @Override
                public void onSuccess(List<Message> backendMessages) {
                    runOnUiThread(() -> {
                        isLoadingMessages = false; // ✅ Clear loading flag
                        android.util.Log.d("GroupChatActivity", "✅ Loaded " + (backendMessages != null ? backendMessages.size() : 0) + " messages from backend for chatId: " + chatIdLong);
                        if (backendMessages != null && !backendMessages.isEmpty()) {
                            // ✅ Always replace messages with backend messages to ensure we have the latest data
                            // This prevents duplicates when WebSocket messages arrive after backend load
                            int previousCount = messageList.size();
                            messageList.clear();
                            // ✅ isSentByUser is now set correctly by parseMessageFromJson(currentUserId)
                            // No need to manually set it here
                            messageList.addAll(backendMessages);
                            messageAdapter.notifyDataSetChanged();
                            saveMessages();
                            // ✅ Update button visibility after loading messages
                            updateScrollToBottomButtonVisibility();
                            android.util.Log.d("GroupChatActivity", "Updated messages from backend - previous: " + previousCount + 
                                        ", backend: " + backendMessages.size() + ", current: " + messageList.size());
                        } else {
                            // ✅ Backend returned empty list - clear cache and show empty state
                            android.util.Log.d("GroupChatActivity", "⚠️ Backend returned empty message list for chatId: " + chatIdLong + 
                                        " (previous count: " + messageList.size() + ")");
                            messageList.clear();
                            messageAdapter.notifyDataSetChanged();
                            saveMessages();
                            updateScrollToBottomButtonVisibility();
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        isLoadingMessages = false; // ✅ Clear loading flag on error
                    android.util.Log.e("GroupChatActivity", "Error loading messages: " + error);
                    });
                }
            });
        } catch (Exception e) {
            isLoadingMessages = false; // ✅ Clear loading flag on exception
            android.util.Log.e("GroupChatActivity", "Error parsing chat ID: " + e.getMessage());
        }
    }
    
    private void fetchChatAndGroupId() {
        if (currentChatId == null || currentChatId.isEmpty() || chatApiService == null) {
            loadMessages();
            return;
        }
        
        try {
            Long chatIdLong = Long.parseLong(currentChatId);
            chatApiService.getChatById(chatIdLong, new ChatApiService.ChatObjectCallback() {
                @Override
                public void onSuccess(org.json.JSONObject chat) {
                    try {
                        // Try to get groupId from chat object
                        if (chat.has("group")) {
                            org.json.JSONObject group = chat.getJSONObject("group");
                            if (group.has("id")) {
                                currentGroupId = group.getLong("id");
                                android.util.Log.d("GroupChatActivity", "Fetched groupId: " + currentGroupId);
                            }
                        } else if (chat.has("groupId")) {
                            currentGroupId = chat.getLong("groupId");
                            android.util.Log.d("GroupChatActivity", "Fetched groupId: " + currentGroupId);
                        }
                        
                        // ✅ Load participants from chat object
                        if (chat.has("participants")) {
                            org.json.JSONArray participants = chat.getJSONArray("participants");
                            memberList.clear();
                            for (int i = 0; i < participants.length(); i++) {
                                org.json.JSONObject participant = participants.getJSONObject(i);
                                String username = participant.optString("username", "");
                                if (!username.isEmpty()) {
                                    memberList.add(username);
                                }
                            }
                            
                            // ✅ Update member count display
                            runOnUiThread(() -> {
                                if (groupMembers != null && memberList != null) {
                                    groupMembers.setText(memberList.size() + " members");
                                }
                            });
                        }
                        
                        runOnUiThread(() -> {
                            loadMessages();
                        });
                    } catch (Exception e) {
                        android.util.Log.e("GroupChatActivity", "Error parsing group from chat: " + e.getMessage());
                        runOnUiThread(() -> {
                            loadMessages();
                        });
                    }
                }
                
                @Override
                public void onError(String error) {
                    android.util.Log.e("GroupChatActivity", "Error fetching chat: " + error);
                    runOnUiThread(() -> {
                        loadMessages();
                    });
                }
            });
        } catch (Exception e) {
            android.util.Log.e("GroupChatActivity", "Error parsing chat ID: " + e.getMessage());
            loadMessages();
        }
    }
    
    private void saveMessages() {
        if (currentChatId != null && !currentChatId.isEmpty()) {
            ChatStorage.saveMessages(this, currentChatId, messageList);
        }
    }
    
    private void setupChatWebSocket() {
        if (chatWebSocketService == null) {
            chatWebSocketService = ChatWebSocketService.getInstance();
        }
        
        chatWebSocketService.setListener(new ChatWebSocketService.ChatWebSocketListener() {
            @Override
            public void onMessage(Message message) {
                runOnUiThread(() -> {
                    // ✅ Ignore WebSocket messages while loading messages from backend
                    // This prevents duplicates when opening a new chat
                    if (isLoadingMessages) {
                        android.util.Log.d("GroupChatActivity", "Ignoring WebSocket message (legacy) while loading messages from backend");
                        return;
                    }
                    
                    if (message != null && currentChatId != null) {
                        // ✅ Check if message already exists by messageId OR by content/timestamp/sender
                        // This prevents duplicates when WebSocket message arrives after backend messages are loaded
                        boolean exists = false;
                        int existingIndex = -1;
                        for (int i = 0; i < messageList.size(); i++) {
                            Message m = messageList.get(i);
                            // Check by messageId first (most reliable)
                            if (m.getMessageId() != null && message.getMessageId() != null && 
                                m.getMessageId().equals(message.getMessageId())) {
                                exists = true;
                                existingIndex = i;
                                android.util.Log.d("GroupChatActivity", "onMessage - Found duplicate by messageId: " + message.getMessageId());
                                break;
                            }
                            // ✅ Also check by content, sender, and timestamp (within 2 seconds) for safety
                            // This catches edge cases where messages might have different IDs but same content
                            if (m.getText() != null && message.getText() != null &&
                                m.getText().equals(message.getText()) && 
                                m.getSender() != null && message.getSender() != null &&
                                m.getSender().equals(message.getSender()) &&
                                Math.abs(m.getTimestamp() - message.getTimestamp()) < 2000) { // Within 2 seconds
                                exists = true;
                                existingIndex = i;
                                android.util.Log.d("GroupChatActivity", "onMessage - Found duplicate by content/sender/timestamp - existing: " + 
                                            (m.getMessageId() != null ? m.getMessageId() : "null") + 
                                            ", new: " + (message.getMessageId() != null ? message.getMessageId() : "null"));
                                break;
                            }
                        }
                        
                        if (exists && existingIndex >= 0) {
                            // ✅ Update existing message instead of adding duplicate
                            Message existingMessage = messageList.get(existingIndex);
                            // Update messageId if not set
                            if (existingMessage.getMessageId() == null && message.getMessageId() != null) {
                                existingMessage.setMessageId(message.getMessageId());
                            }
                            // Update other fields if needed
                            if (message.hasAttachment() && !existingMessage.hasAttachment()) {
                                existingMessage.setAttachmentPath(message.getAttachmentPath());
                                existingMessage.setAttachmentType(message.getAttachmentType());
                                existingMessage.setAttachmentName(message.getAttachmentName());
                                existingMessage.setAttachmentId(message.getAttachmentId());
                            }
                            messageAdapter.notifyItemChanged(existingIndex);
                            saveMessages();
                            android.util.Log.d("GroupChatActivity", "onMessage - Updated existing message instead of adding duplicate");
                        } else if (!exists) {
                            // ✅ Only add if it doesn't exist
                            // ✅ isSentByUser is already set correctly in onNewMessage based on senderId comparison
                            // No need to manually set it here
                        messageList.add(message);
                        messageAdapter.notifyItemInserted(messageList.size() - 1);
                        saveMessages();
                            // ✅ Scroll to bottom when receiving new message via WebSocket (legacy onMessage)
                            scrollToBottomSmooth();
                            android.util.Log.d("GroupChatActivity", "onMessage - Added new message - messageId: " + 
                                        (message.getMessageId() != null ? message.getMessageId() : "null"));
                        }
                    }
                });
            }
            
            @Override
            public void onChatCreated(org.json.JSONObject chat) {
                runOnUiThread(() -> {
                    android.util.Log.d("GroupChatActivity", "Chat created event received");
                });
            }
            
            @Override
            public void onNewMessage(org.json.JSONObject messageData) {
                runOnUiThread(() -> {
                    try {
                        // ✅ Ignore WebSocket messages while loading messages from backend
                        // This prevents duplicates when opening a new chat
                        if (isLoadingMessages) {
                            android.util.Log.d("GroupChatActivity", "Ignoring WebSocket message while loading messages from backend");
                            return;
                        }
                        
                        // Parse message from backend format
                        String content = messageData.optString("content", "");
                        org.json.JSONObject senderObj = messageData.optJSONObject("sender");
                        String sender = "";
                        if (senderObj != null) {
                            sender = senderObj.optString("username", "");
                        }
                        
                        Long messageId = messageData.optLong("id", -1);
                        String createdAt = messageData.optString("createdAt", "");
                        
                        // Parse timestamp - use SimpleDateFormat for API 24+ compatibility
                        long timestamp = System.currentTimeMillis();
                        if (!createdAt.isEmpty()) {
                            try {
                                // Try ISO 8601 format: "2024-01-01T00:00:00" or "2024-01-01T00:00:00.000000"
                                String dateStr = createdAt.replace("Z", "").replace("T", " ");
                                // Remove microseconds if present
                                if (dateStr.contains(".")) {
                                    int dotIndex = dateStr.indexOf(".");
                                    dateStr = dateStr.substring(0, dotIndex);
                                }
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
                                java.util.Date date = sdf.parse(dateStr);
                                timestamp = date.getTime();
                            } catch (Exception e) {
                                android.util.Log.e("GroupChatActivity", "Error parsing timestamp: " + e.getMessage());
                                timestamp = System.currentTimeMillis();
                            }
                        }
                        
                        // Determine if sent by current user
                        boolean isSentByUser = false;
                        if (currentUserId != null && senderObj != null) {
                            Long senderId = senderObj.optLong("id", -1);
                            if (senderId > 0) {
                                isSentByUser = senderId.equals(currentUserId);
                                android.util.Log.d("GroupChatActivity", "onNewMessage - senderId: " + senderId + ", currentUserId: " + currentUserId + ", isSentByUser: " + isSentByUser);
                            } else {
                                android.util.Log.w("GroupChatActivity", "onNewMessage - senderId is invalid: " + senderId);
                            }
                        } else {
                            android.util.Log.w("GroupChatActivity", "onNewMessage - currentUserId or senderObj is null");
                        }
                        
                        // ✅ Get attachment info if available
                        org.json.JSONObject attachmentObj = messageData.optJSONObject("attachment");
                        String fileUrl = "";
                        String fileName = "";
                        String fileType = "";
                        if (attachmentObj != null) {
                            fileUrl = attachmentObj.optString("fileUrl", "");
                            // ✅ Check both "originalFileName" and "fileName" for compatibility
                            // ✅ Handle null values properly - optString returns "null" string if value is null
                            fileName = attachmentObj.optString("originalFileName", "");
                            if (fileName == null || fileName.isEmpty() || fileName.equals("null")) {
                                fileName = attachmentObj.optString("fileName", "");
                            }
                            // ✅ Final fallback - use a default name
                            if (fileName == null || fileName.isEmpty() || fileName.equals("null")) {
                                String fileTypeCheck = attachmentObj.optString("fileType", "");
                                if (fileTypeCheck != null && fileTypeCheck.toLowerCase().contains("image")) {
                                    fileName = "Image";
                                } else if (fileTypeCheck != null && fileTypeCheck.toLowerCase().contains("pdf")) {
                                    fileName = "Document";
                                } else {
                                    fileName = "Attachment";
                                }
                            }
                            fileType = attachmentObj.optString("fileType", "");
                        }
                        
                        // Also check top-level fields for attachment info (backward compatibility)
                        if (fileUrl.isEmpty()) {
                            fileUrl = messageData.optString("fileUrl", "");
                            // ✅ Check both "originalFileName" and "fileName" for compatibility
                            // ✅ Handle null values properly
                            fileName = messageData.optString("originalFileName", "");
                            if (fileName == null || fileName.isEmpty() || fileName.equals("null")) {
                                fileName = messageData.optString("fileName", "");
                            }
                            // ✅ Final fallback - use a default name
                            if (fileName == null || fileName.isEmpty() || fileName.equals("null")) {
                                String fileTypeCheck = messageData.optString("fileType", "");
                                if (fileTypeCheck != null && fileTypeCheck.toLowerCase().contains("image")) {
                                    fileName = "Image";
                                } else if (fileTypeCheck != null && fileTypeCheck.toLowerCase().contains("pdf")) {
                                    fileName = "Document";
                                } else {
                                    fileName = "Attachment";
                                }
                            }
                            fileType = messageData.optString("fileType", "");
                        }
                        
                        // Create message object with attachment if available
                        Message message;
                        if (!fileUrl.isEmpty() && !fileType.isEmpty()) {
                            message = new Message(content, sender, timestamp, isSentByUser, fileUrl, fileType, fileName);
                            android.util.Log.d("GroupChatActivity", "Created message with attachment - fileUrl: " + fileUrl + ", fileName: " + fileName);
                        } else {
                            message = new Message(content, sender, timestamp, isSentByUser);
                        }
                        if (messageId > 0) {
                            message.setMessageId(messageId);
                        }
                        
                        // ✅ Check if message already exists by messageId OR by content/timestamp/sender
                        // This prevents duplicates when WebSocket message arrives after backend messages are loaded
                        boolean exists = false;
                        int existingIndex = -1;
                        for (int i = 0; i < messageList.size(); i++) {
                            Message m = messageList.get(i);
                            // Check by messageId first (most reliable)
                            if (m.getMessageId() != null && messageId > 0 && m.getMessageId().equals(messageId)) {
                                exists = true;
                                existingIndex = i;
                                android.util.Log.d("GroupChatActivity", "Found duplicate message by messageId: " + messageId);
                                break;
                            }
                            // ✅ Also check by content, sender, and timestamp (within 5 seconds) for messages without ID
                            // This prevents duplicates when WebSocket message arrives before API response updates local message
                            if (m.getMessageId() == null && messageId > 0 &&
                                m.getText() != null && content != null &&
                                m.getText().equals(content) && 
                                m.getSender() != null && sender != null &&
                                m.getSender().equals(sender) &&
                                Math.abs(m.getTimestamp() - timestamp) < 5000) { // Within 5 seconds
                                exists = true;
                                existingIndex = i;
                                android.util.Log.d("GroupChatActivity", "onNewMessage - Found duplicate by content/sender/timestamp, updating with messageId: " + messageId + " at index " + i);
                                break;
                            }
                            // ✅ Also check by content, sender, and timestamp even if both have IDs (for safety)
                            // This catches edge cases where messages might have different IDs but same content
                            if (m.getMessageId() != null && messageId > 0 && !m.getMessageId().equals(messageId) &&
                                m.getText() != null && content != null &&
                                m.getText().equals(content) && 
                                m.getSender() != null && sender != null &&
                                m.getSender().equals(sender) &&
                                Math.abs(m.getTimestamp() - timestamp) < 2000) { // Within 2 seconds (tighter window for messages with IDs)
                                exists = true;
                                existingIndex = i;
                                android.util.Log.d("GroupChatActivity", "onNewMessage - Found duplicate by content/sender/timestamp (both have IDs) - existing: " + m.getMessageId() + ", new: " + messageId + " at index " + i);
                                break;
                            }
                            // ✅ Also check by content, sender, and timestamp even if messageId is 0 or negative (for safety)
                            // This catches edge cases where messageId might not be set correctly
                            if (messageId <= 0 &&
                                m.getText() != null && content != null &&
                                m.getText().equals(content) && 
                                m.getSender() != null && sender != null &&
                                m.getSender().equals(sender) &&
                                Math.abs(m.getTimestamp() - timestamp) < 2000) { // Within 2 seconds
                                exists = true;
                                existingIndex = i;
                                android.util.Log.d("GroupChatActivity", "onNewMessage - Found duplicate by content/sender/timestamp (no messageId) at index " + i);
                                break;
                            }
                        }
                        
                        if (exists && existingIndex >= 0) {
                            // ✅ Message already exists, update it instead of adding duplicate
                            Message existingMessage = messageList.get(existingIndex);
                            // ✅ Only update messageId if it's not already set
                            if (existingMessage.getMessageId() == null && messageId > 0) {
                                existingMessage.setMessageId(messageId);
                            }
                            // ✅ Preserve isSentByUser from existing message if it was already set to true
                            // This ensures messages sent by the user stay on the right side
                            // Only update if the existing message doesn't have isSentByUser set to true
                            if (!existingMessage.isSentByUser()) {
                                existingMessage.setSentByUser(isSentByUser);
                            } else {
                                // ✅ Keep it as true - the user sent this message
                                existingMessage.setSentByUser(true);
                                android.util.Log.d("GroupChatActivity", "Preserved isSentByUser=true for sent message - messageId: " + messageId);
                            }
                            // Update attachment data if present
                            if (message.hasAttachment()) {
                                existingMessage.setAttachmentPath(message.getAttachmentPath());
                                existingMessage.setAttachmentType(message.getAttachmentType());
                                existingMessage.setAttachmentName(message.getAttachmentName());
                                existingMessage.setAttachmentId(message.getAttachmentId());
                            }
                            messageAdapter.notifyItemChanged(existingIndex);
                            saveMessages();
                            android.util.Log.d("GroupChatActivity", "onNewMessage - Updated existing message at index " + existingIndex + " - messageId: " + messageId + ", isSentByUser: " + existingMessage.isSentByUser());
                        } else if (!exists && currentChatId != null) {
                            // ✅ Final safety check: verify message doesn't exist before adding
                            boolean finalCheck = false;
                            for (Message m : messageList) {
                                if (messageId > 0 && m.getMessageId() != null && m.getMessageId().equals(messageId)) {
                                    finalCheck = true;
                                    android.util.Log.w("GroupChatActivity", "onNewMessage - Final check found duplicate by messageId: " + messageId + ", skipping add");
                                    break;
                                }
                            }
                            
                            if (!finalCheck) {
                                android.util.Log.d("GroupChatActivity", "onNewMessage - Adding new message - messageId: " + messageId + ", content: " + (content != null && content.length() > 20 ? content.substring(0, 20) + "..." : content));
                                messageList.add(message);
                                messageAdapter.notifyItemInserted(messageList.size() - 1);
                                saveMessages();
                                // ✅ Scroll to bottom when receiving new message via WebSocket
                                scrollToBottomSmooth();
                                android.util.Log.d("GroupChatActivity", "onNewMessage - Added new message from WebSocket - messageId: " + messageId);
                            }
                        } else {
                            android.util.Log.d("GroupChatActivity", "onNewMessage - Skipping message - exists: " + exists + ", currentChatId: " + currentChatId + ", messageId: " + messageId);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("GroupChatActivity", "Error handling new_message event: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
            
            @Override
            public void onMessageEdited(org.json.JSONObject messageData) {
                runOnUiThread(() -> {
                    try {
                        Long messageId = messageData.optLong("id", -1);
                        String content = messageData.optString("content", "");
                        
                        // Update message in list
                        for (int i = 0; i < messageList.size(); i++) {
                            Message m = messageList.get(i);
                            if (m.getMessageId() != null && m.getMessageId().equals(messageId)) {
                                m.setText(content);
                                messageAdapter.notifyItemChanged(i);
                                saveMessages();
                                break;
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("GroupChatActivity", "Error handling message_edited event: " + e.getMessage());
                    }
                });
            }
            
            @Override
            public void onMessageDeleted(Long messageId) {
                runOnUiThread(() -> {
                    // Remove message from list
                    for (int i = 0; i < messageList.size(); i++) {
                        Message m = messageList.get(i);
                        if (m.getMessageId() != null && m.getMessageId().equals(messageId)) {
                            messageList.remove(i);
                            messageAdapter.notifyItemRemoved(i);
                            saveMessages();
                            break;
                        }
                    }
                });
            }
            
            @Override
            public void onTyping(Long userId, boolean isTyping) {
                runOnUiThread(() -> {
                    android.util.Log.d("GroupChatActivity", "User " + userId + " is typing: " + isTyping);
                    // TODO: Show typing indicator in UI
                });
            }
            
            @Override
            public void onReaction(Long messageId, String emoji, Long reactionUserId, String action) {
                // ✅ Handle reaction events in real-time - instant update (no API call, no scroll)
                runOnUiThread(() -> {
                    try {
                        android.util.Log.d("GroupChatActivity", "✅ Reaction event: " + action + " " + emoji + " on message " + messageId + " by user " + reactionUserId);
                        
                        // ✅ Save current scroll position to prevent auto-scroll
                        androidx.recyclerview.widget.LinearLayoutManager layoutManager = 
                            (androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager();
                        int lastVisiblePosition = layoutManager != null ? layoutManager.findLastVisibleItemPosition() : -1;
                        boolean wasAtBottom = lastVisiblePosition >= messageList.size() - 2; // Within 2 items of bottom
                        
                        // Find the message in the list
                        for (int i = 0; i < messageList.size(); i++) {
                            Message m = messageList.get(i);
                            if (m.getMessageId() != null && m.getMessageId().equals(messageId)) {
                                // ✅ Update reactions instantly based on WebSocket event
                                if ("add".equals(action)) {
                                    m.addReaction(emoji);
                                } else if ("remove".equals(action)) {
                                    m.removeReaction(emoji);
                                }
                                
                                // ✅ Update UI instantly - WebSocket event is the source of truth
                                // Use notifyItemChanged with payload to prevent scroll
                                messageAdapter.notifyItemChanged(i, "reaction_update");
                                saveMessages();
                                
                                // ✅ Only scroll to bottom if user was already at bottom
                                if (wasAtBottom && i == messageList.size() - 1 && layoutManager != null) {
                                    recyclerView.post(() -> {
                                        if (messageList.size() > 0) {
                                            recyclerView.smoothScrollToPosition(messageList.size() - 1);
                                        }
                                    });
                                }
                                
                                android.util.Log.d("GroupChatActivity", "✅ Reaction updated instantly in UI");
                                break;
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("GroupChatActivity", "Error handling reaction event: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
            
            @Override
            public void onConnect() {
                android.util.Log.d("GroupChatActivity", "WebSocket connected");
            }
            
            @Override
            public void onDisconnect() {
                android.util.Log.d("GroupChatActivity", "WebSocket disconnected");
            }
            
            @Override
            public void onError(Exception e) {
                android.util.Log.e("GroupChatActivity", "WebSocket error: " + e.getMessage());
            }
        });
        
        if (currentChatId != null) {
            chatWebSocketService.joinChat(currentChatId);
        }
        
        // Chat WebSocket requires userId (Long), not username
        if (currentUserId != null && currentUserId > 0) {
            // ✅ Use ServerConfig for WebSocket URL (supports local and production)
            String wsUrl = com.example.occasio.utils.ServerConfig.WS_CHAT_URL;
            chatWebSocketService.connect(currentUserId, wsUrl);
            android.util.Log.d("GroupChatActivity", "Connecting to WebSocket for user: " + currentUserId + " at " + wsUrl);
        } else {
            android.util.Log.e("GroupChatActivity", "Cannot connect to WebSocket: userId is invalid, fetching...");
            fetchUserIdForWebSocket();
        }
    }
    
    private void fetchUserIdForWebSocket() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            android.util.Log.e("GroupChatActivity", "Cannot fetch userId: username is empty");
            return;
        }
        
        String url = com.example.occasio.utils.ServerConfig.USER_INFO_BY_USERNAME + currentUsername;
        com.android.volley.toolbox.JsonObjectRequest request = new com.android.volley.toolbox.JsonObjectRequest(
            com.android.volley.Request.Method.GET,
            url,
            null,
            response -> {
                try {
                    Long fetchedUserId = response.getLong("id");
                    currentUserId = fetchedUserId;
                    // ✅ Use ServerConfig for WebSocket URL (supports local and production)
                    String wsUrl = com.example.occasio.utils.ServerConfig.WS_CHAT_URL;
                    chatWebSocketService.connect(currentUserId, wsUrl);
                    android.util.Log.d("GroupChatActivity", "WebSocket connected with fetched userId: " + currentUserId + " at " + wsUrl);
                } catch (Exception e) {
                    android.util.Log.e("GroupChatActivity", "Error parsing userId: " + e.getMessage());
                }
            },
            error -> {
                android.util.Log.e("GroupChatActivity", "Error fetching userId: " + error.getMessage());
            }
        );
        com.example.occasio.api.VolleySingleton.getInstance(this).addToRequestQueue(request);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        saveMessages();
        if (chatWebSocketService != null && currentChatId != null) {
            chatWebSocketService.leaveChat(currentChatId);
        }
    }
    
    private void showGroupSettingsMenu() {
        if (currentChatId == null || currentChatId.isEmpty()) {
            return;
        }
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Group Settings");
        builder.setItems(new String[]{
            "➕ Add Member",
            "➖ Remove Member",
            "✏️ Rename Chat",
            "🗑️ Delete Chat"
        }, (dialog, which) -> {
            try {
                Long chatIdLong = Long.parseLong(currentChatId);
                switch (which) {
                    case 0:
                        showAddParticipantDialog(chatIdLong);
                        break;
                    case 1:
                        showRemoveParticipantDialog(chatIdLong);
                        break;
                    case 2:
                        showUpdateChatNameDialog(chatIdLong);
                        break;
                    case 3:
                        showDeleteChatDialog(chatIdLong);
                        break;
                }
            } catch (Exception e) {
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void showAddParticipantDialog(Long chatId) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter user ID to add");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Add Member");
        builder.setView(input);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String userIdStr = input.getText().toString().trim();
            if (!userIdStr.isEmpty()) {
                try {
                    Long userId = Long.parseLong(userIdStr);
                    addParticipant(chatId, userId);
                } catch (Exception e) {
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void addParticipant(Long chatId, Long userId) {
        if (chatApiService == null) {
            return;
        }
        
        chatApiService.addParticipant(chatId, userId, new ChatApiService.ChatObjectCallback() {
            @Override
            public void onSuccess(org.json.JSONObject chat) {
                runOnUiThread(() -> {
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(GroupChatActivity.this, "Error adding member: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void showRemoveParticipantDialog(Long chatId) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter user ID to remove");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Remove Member");
        builder.setView(input);
        builder.setPositiveButton("Remove", (dialog, which) -> {
            String userIdStr = input.getText().toString().trim();
            if (!userIdStr.isEmpty()) {
                try {
                    Long userId = Long.parseLong(userIdStr);
                    removeParticipant(chatId, userId);
                } catch (Exception e) {
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void removeParticipant(Long chatId, Long userId) {
        if (chatApiService == null) {
            return;
        }
        
        chatApiService.removeParticipant(chatId, userId, new ChatApiService.ChatObjectCallback() {
            @Override
            public void onSuccess(org.json.JSONObject chat) {
                runOnUiThread(() -> {
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(GroupChatActivity.this, "Error removing member: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void showUpdateChatNameDialog(Long chatId) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter new chat name");
        input.setText(currentChatName);
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Rename Chat");
        builder.setView(input);
        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                updateChatName(chatId, newName);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void updateChatName(Long chatId, String newName) {
        if (chatApiService == null) {
            return;
        }
        
        chatApiService.updateChatName(chatId, newName, new ChatApiService.ChatObjectCallback() {
            @Override
            public void onSuccess(org.json.JSONObject chat) {
                runOnUiThread(() -> {
                    currentChatName = newName;
                    if (chatTitle != null) {
                        chatTitle.setText(newName);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(GroupChatActivity.this, "Error renaming chat: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void showDeleteChatDialog(Long chatId) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Chat");
        builder.setMessage("Are you sure you want to delete this chat? This action cannot be undone.");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            deleteChat(chatId);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void deleteChat(Long chatId) {
        if (chatApiService == null) {
            return;
        }
        
        chatApiService.deleteChat(chatId, new ChatApiService.VoidCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    finish();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(GroupChatActivity.this, "Error deleting chat: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void showGroupMembersDialog() {
        if (memberList == null || memberList.isEmpty()) {
            return;
        }
        
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this,
            android.R.layout.simple_list_item_1, memberList);
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Group Members (" + memberList.size() + ")");
        builder.setAdapter(adapter, null);
        builder.setPositiveButton("Close", null);
        builder.show();
    }
    
    private void scrollToBottomSmooth() {
        // ✅ Use smooth scroll when user clicks scroll to bottom button
        recyclerView.post(() -> {
            if (messageList.size() > 0) {
                recyclerView.smoothScrollToPosition(messageList.size() - 1);
                // ✅ Hide button after scrolling
                updateScrollToBottomButtonVisibility();
            }
        });
    }
    
    private void updateScrollToBottomButtonVisibility() {
        if (scrollToBottomButton == null || recyclerView == null || messageList.isEmpty()) {
            return;
        }
        
        androidx.recyclerview.widget.LinearLayoutManager layoutManager = 
            (androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        
        // ✅ Show button if user is not at the bottom (within 3 items)
        int lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition();
        int totalItems = messageList.size();
        boolean isAtBottom = (lastVisiblePosition >= totalItems - 3);
        
        if (isAtBottom) {
            scrollToBottomButton.setVisibility(android.view.View.GONE);
        } else {
            scrollToBottomButton.setVisibility(android.view.View.VISIBLE);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveMessages();
        if (chatWebSocketService != null && currentChatId != null) {
            chatWebSocketService.leaveChat(currentChatId);
        }
    }
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}

