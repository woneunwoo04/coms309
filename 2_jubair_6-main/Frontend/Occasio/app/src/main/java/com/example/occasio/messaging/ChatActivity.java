package com.example.occasio.messaging;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.provider.OpenableColumns;
import com.example.occasio.R;
import com.example.occasio.service.ChatWebSocketService;
import com.example.occasio.services.ChatApiService;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView messagesRecyclerView;
    private EditText messageInput;
    private Button sendButton;
    private Button attachButton;
    private Button scrollToBottomButton;
    private TextView contactNameText;
    private MessageAdapter messageAdapter;
    private ChatApiService chatApiService;
    private ChatWebSocketService webSocketService;
    private Long chatId;
    private Long userId;
    private String chatName;
    private List<Message> messages = new ArrayList<>();
    private String pendingAttachmentPath;
    private String pendingAttachmentType;
    private String pendingAttachmentName;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private boolean isLoadingMessages = false; // ✅ Track if messages are currently being loaded from backend
    private boolean isLoadingReactions = false; // ✅ Track if reactions are currently being loaded
    
    // ✅ Track recently sent messages to prevent WebSocket duplicates
    private java.util.Map<String, Long> recentlySentMessages = new java.util.HashMap<>(); // content -> timestamp

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        Intent intent = getIntent();
        chatId = intent.getLongExtra("chatId", -1);
        chatName = intent.getStringExtra("chatName");
        userId = intent.getLongExtra("userId", -1);
        
        android.util.Log.d("ChatActivity", "onCreate - chatId=" + chatId + ", chatName=" + chatName + ", userId=" + userId);

        if (userId == -1) {
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            String username = prefs.getString("username", "");
            if (username.isEmpty()) {
                username = "demo_user";
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("username", username);
                editor.apply();
            }
            
            chatApiService = new ChatApiService(this);
            chatApiService.getUserIdFromUsername(username, new ChatApiService.UserIdCallback() {
                @Override
                public void onSuccess(Long id) {
                    userId = id;
                    initialize();
                }

                @Override
                public void onError(String error) {
                    android.util.Log.e("ChatActivity", "Error fetching user ID: " + error);
                    
                    if (error != null && error.contains("NETWORK_ERROR")) {
                        runOnUiThread(() -> {
                            userId = 1L;
                            initialize();
                        });
                    } else {
                        runOnUiThread(() -> {
                            finish();
                        });
                    }
                }
            });
        } else {
            initialize();
        }
    }

    private void initialize() {
        android.util.Log.d("ChatActivity", "initialize() called - chatId=" + chatId + ", userId=" + userId);
        
        // ✅ Initialize chatApiService if not already initialized
        if (chatApiService == null) {
            android.util.Log.d("ChatActivity", "Initializing chatApiService");
            chatApiService = new ChatApiService(this);
        }
        
        initializeFilePickers();
        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        loadContactInfo();
        loadMessages();
        setupWebSocket();
        
        android.util.Log.d("ChatActivity", "initialize() completed - chatApiService=" + (chatApiService != null ? "initialized" : "null"));
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
            
            File cacheDir = new File(getCacheDir(), "attachments");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            
            File destFile = new File(cacheDir, fileName);
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            FileOutputStream outputStream = new FileOutputStream(destFile);
            
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            
            inputStream.close();
            outputStream.close();
            
            pendingAttachmentPath = destFile.getAbsolutePath();
            pendingAttachmentType = fileType;
            pendingAttachmentName = fileName;
            
            sendMessageWithAttachment();
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Error handling file attachment: " + e.getMessage());
        }
    }
    
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    
    private void sendMessageWithAttachment() {
        if (pendingAttachmentPath == null || pendingAttachmentType == null || chatId == null || userId == null) {
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
        
        File attachmentFile = new File(pendingAttachmentPath);
        if (!attachmentFile.exists()) {
            return;
        }
        
        // ✅ Use file:// URI directly - file is already copied to cache directory
        // Both downloaded images and gallery images are handled the same way after copying to cache
        Uri fileUri = android.net.Uri.fromFile(attachmentFile);
        android.util.Log.d("ChatActivity", "Sending attachment - file: " + attachmentFile.getAbsolutePath() + 
                          ", name: " + pendingAttachmentName + ", type: " + pendingAttachmentType);
        
        if (chatApiService != null) {
            android.util.Log.d("ChatActivity", "Sending file attachment - chatId: " + chatId + ", userId: " + userId + ", path: " + pendingAttachmentPath);
            chatApiService.sendFileMessage(chatId, userId, fileUri, messageText, null, new ChatApiService.MessageCallback() {
                @Override
                public void onSuccess(Message message) {
                    runOnUiThread(() -> {
                        android.util.Log.d("ChatActivity", "File message sent successfully - messageId: " + message.getMessageId() + 
                                          ", hasAttachment: " + message.hasAttachment() +
                                          ", attachmentPath: " + (message.getAttachmentPath() != null ? message.getAttachmentPath() : "null") +
                                          ", attachmentName: " + (message.getAttachmentName() != null ? message.getAttachmentName() : "null") +
                                          ", attachmentType: " + (message.getAttachmentType() != null ? message.getAttachmentType() : "null"));
                        
                        // ✅ Always add/update the message in the list
                        // Check if message with same ID already exists
                        boolean messageExists = false;
                        int existingIndex = -1;
                        for (int i = 0; i < messages.size(); i++) {
                            if (messages.get(i).getMessageId() != null && 
                                message.getMessageId() != null &&
                                messages.get(i).getMessageId().equals(message.getMessageId())) {
                                messageExists = true;
                                existingIndex = i;
                                break;
                            }
                        }
                        
                        if (messageExists && existingIndex >= 0) {
                            // Update existing message
                            // ✅ Ensure isSentByUser is set correctly for sent messages
                            message.setSentByUser(true);
                            messages.set(existingIndex, message);
                            messageAdapter.notifyItemChanged(existingIndex);
                            android.util.Log.d("ChatActivity", "Updated existing message at index " + existingIndex + ", isSentByUser: " + message.isSentByUser());
                        } else {
                            // Add new message
                            // ✅ Ensure isSentByUser is set correctly for sent messages
                            message.setSentByUser(true);
                        messages.add(message);
                        messageAdapter.notifyItemInserted(messages.size() - 1);
                            android.util.Log.d("ChatActivity", "Added new message at index " + (messages.size() - 1) + ", isSentByUser: " + message.isSentByUser());
                            // ✅ Use smooth scroll when user sends a message (better UX)
                            scrollToBottomSmooth();
                        }
                        ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                        
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
                        android.util.Log.e("ChatActivity", "Error sending attachment: " + error);
                        Toast.makeText(ChatActivity.this, "Error sending attachment: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            android.util.Log.e("ChatActivity", "chatApiService is null, cannot send attachment");
            Toast.makeText(this, "Error: Chat service not available", Toast.LENGTH_SHORT).show();
        }
    }

    private Button searchButton;
    
    private void initializeViews() {
        messagesRecyclerView = findViewById(R.id.messages_recycler_view);
        messageInput = findViewById(R.id.message_input);
        sendButton = findViewById(R.id.send_button);
        attachButton = findViewById(R.id.attach_button);
        contactNameText = findViewById(R.id.contact_name_tv);
        searchButton = findViewById(R.id.search_button);
        scrollToBottomButton = findViewById(R.id.scroll_to_bottom_btn);
        
        // ✅ Set click listener for scroll to bottom button
        if (scrollToBottomButton != null) {
            scrollToBottomButton.setOnClickListener(v -> {
                scrollToBottomSmooth();
            });
        }
    }

    private boolean isLoadingOlderMessages = false;
    private LinearLayoutManager layoutManager;
    
    private void setupRecyclerView() {
        messageAdapter = new MessageAdapter(this, messages, userId);
        messageAdapter.setOnReactionClickListener((message, emoji) -> {
            toggleReaction(message, emoji);
        });
        messageAdapter.setOnMessageLongClickListener((message) -> {
            showMessageOptionsDialog(message);
        });
        messageAdapter.setOnAttachmentClickListener((message) -> {
            handleAttachmentClick(message);
        });
        layoutManager = new LinearLayoutManager(this);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(messageAdapter);
        
        messagesRecyclerView.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                if (!isLoadingOlderMessages && layoutManager.findFirstVisibleItemPosition() == 0 && messages.size() > 0) {
                    loadOlderMessages();
                }
                
                // ✅ Show/hide scroll to bottom button based on scroll position
                updateScrollToBottomButtonVisibility();
            }
        });
    }
    
    private void loadOlderMessages() {
        if (chatId == null || chatId == -1 || chatApiService == null || messages.isEmpty()) {
            return;
        }
        
        Message oldestMessage = messages.get(0);
        if (oldestMessage == null || oldestMessage.getTimestamp() == 0) {
            return;
        }
        
        isLoadingOlderMessages = true;
        
        // Use Date for API 24+ compatibility instead of java.time.Instant (requires API 26)
        java.util.Date date = new java.util.Date(oldestMessage.getTimestamp());
        // Format timestamp as ISO 8601 string for API call
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
        String timestamp = sdf.format(date);
        
        chatApiService.getMessagesSince(chatId, timestamp, new ChatApiService.MessagesListCallback() {
            @Override
            public void onSuccess(List<Message> olderMessages) {
                runOnUiThread(() -> {
                    isLoadingOlderMessages = false;
                    if (olderMessages != null && !olderMessages.isEmpty()) {
                        int previousSize = messages.size();
                        messages.addAll(0, olderMessages);
                        messageAdapter.notifyItemRangeInserted(0, olderMessages.size());
                        ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                        
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
        if (message == null || !message.hasAttachment() || message.getAttachmentId() == null || userId == null) {
            return;
        }
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Attachment");
        builder.setMessage("Are you sure you want to delete this attachment?");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            chatApiService.deleteAttachment(message.getAttachmentId(), userId, new ChatApiService.VoidCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        message.setAttachmentPath(null);
                        message.setAttachmentType(null);
                        message.setAttachmentName(null);
                        int position = messages.indexOf(message);
                        if (position >= 0) {
                            messageAdapter.notifyItemChanged(position);
                            ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(ChatActivity.this, "Error deleting attachment: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void editMessage(Message message) {
        if (message == null || userId == null) {
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
                chatApiService.editMessage(message.getMessageId(), userId, newContent, new ChatApiService.MessageCallback() {
                    @Override
                    public void onSuccess(Message updatedMessage) {
                        runOnUiThread(() -> {
                            int position = messages.indexOf(message);
                            if (position >= 0) {
                                message.setText(updatedMessage.getText());
                                messageAdapter.notifyItemChanged(position);
                                ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                            }
                        });
                    }
                    
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(ChatActivity.this, "Error updating message: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    private void deleteMessage(Message message) {
        if (message == null || userId == null) {
            return;
        }
        
        if (message.getMessageId() == null) {
            return;
        }
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Message");
        builder.setMessage("Are you sure you want to delete this message?");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            chatApiService.deleteMessage(message.getMessageId(), userId, new ChatApiService.VoidCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        int position = messages.indexOf(message);
                        if (position >= 0) {
                            messages.remove(position);
                            messageAdapter.notifyItemRemoved(position);
                            ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(ChatActivity.this, "Error deleting message: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void setupClickListeners() {
        android.util.Log.d("ChatActivity", "setupClickListeners() called");
        
        Button backButton = findViewById(R.id.chat_back_btn);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        if (sendButton == null) {
            android.util.Log.e("ChatActivity", "setupClickListeners: sendButton is null!");
        } else {
            android.util.Log.d("ChatActivity", "setupClickListeners: Setting up send button click listener");
            sendButton.setOnClickListener(v -> {
                android.util.Log.d("ChatActivity", "Send button clicked!");
                sendMessage();
            });
        }
        
        if (attachButton != null) {
            attachButton.setOnClickListener(v -> showAttachmentOptions());
        }
        
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> showSearchDialog());
        }
    }
    
    private void showSearchDialog() {
        if (chatId == null || chatId == -1) {
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
        if (chatId == null || chatApiService == null) {
            return;
        }
        
        
        chatApiService.searchMessages(chatId, query, new ChatApiService.SearchCallback() {
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
                    Toast.makeText(ChatActivity.this, "Search error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
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
            int messageIndex = messages.indexOf(selectedMessage);
            if (messageIndex >= 0) {
                messagesRecyclerView.scrollToPosition(messageIndex);
                messagesRecyclerView.smoothScrollToPosition(messageIndex);
            }
        });
        
        builder.setView(listView);
        builder.setPositiveButton("Close", null);
        builder.show();
    }
    
    private void handleAttachmentClick(Message message) {
        if (message == null || !message.hasAttachment()) {
            return;
        }
        
        String attachmentPath = message.getAttachmentPath();
        String attachmentType = message.getAttachmentType();
        // ✅ Get attachmentId from message - try attachmentId field first, then extract from path
        Long attachmentId = message.getAttachmentId();
        
        // ✅ If attachmentId is not set, try to extract it from the fileUrl path
        if (attachmentId == null || attachmentId <= 0) {
            if (attachmentPath != null && !attachmentPath.isEmpty()) {
                // Extract attachmentId from path like "/api/messages/attachments/123"
                try {
                    String path = attachmentPath;
                    if (path.contains("/attachments/")) {
                        int index = path.lastIndexOf("/attachments/");
                        if (index >= 0) {
                            String idStr = path.substring(index + "/attachments/".length());
                            // Remove any query parameters
                            if (idStr.contains("?")) {
                                idStr = idStr.substring(0, idStr.indexOf("?"));
                            }
                            attachmentId = Long.parseLong(idStr);
                            android.util.Log.d("ChatActivity", "Extracted attachmentId from path: " + attachmentId);
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("ChatActivity", "Error extracting attachmentId from path: " + e.getMessage());
                }
            }
        }
        
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
                        // ✅ Save to external storage or cache with proper filename
                        String fileName = message.getAttachmentName();
                        if (fileName == null || fileName.isEmpty() || fileName.equals("null")) {
                            // Generate filename based on type
                            if (attachmentType != null && attachmentType.contains("image")) {
                                fileName = "image_" + System.currentTimeMillis() + ".jpg";
                            } else if (attachmentType != null && attachmentType.contains("pdf")) {
                                fileName = "document_" + System.currentTimeMillis() + ".pdf";
                            } else {
                                fileName = "attachment_" + System.currentTimeMillis();
                            }
                        }
                        
                        // ✅ Save to external files directory (more accessible)
                        java.io.File downloadsDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                        if (downloadsDir == null) {
                            downloadsDir = getCacheDir();
                        }
                        if (!downloadsDir.exists()) {
                            downloadsDir.mkdirs();
                        }
                        
                        java.io.File tempFile = new java.io.File(downloadsDir, fileName);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
                        fos.write(data);
                        fos.close();
                        
                        android.util.Log.d("ChatActivity", "Saved attachment to: " + tempFile.getAbsolutePath());
                        
                        // ✅ Use FileProvider URI for better compatibility
                        android.net.Uri uri = android.net.Uri.fromFile(tempFile);
                        openAttachment(uri, attachmentType, fileName);
                    } catch (Exception e) {
                        android.util.Log.e("ChatActivity", "Error saving attachment: " + e.getMessage());
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "Error downloading: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void openAttachment(android.net.Uri uri, String attachmentType, String fileName) {
        try {
            java.io.File file = null;
            String mimeType = "*/*";
            android.net.Uri contentUri = uri;
            
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
                    
                    // ✅ Convert file:// URI to content:// URI using FileProvider to avoid FileUriExposedException
                    try {
                        contentUri = androidx.core.content.FileProvider.getUriForFile(
                            this,
                            getApplicationContext().getPackageName() + ".fileprovider",
                            file
                        );
                        android.util.Log.d("ChatActivity", "Converted file URI to content URI: " + contentUri);
                    } catch (Exception e) {
                        android.util.Log.w("ChatActivity", "FileProvider not configured, using MediaStore: " + e.getMessage());
                        // Fallback: Use MediaStore to create content:// URI
                        try {
                            android.content.ContentValues values = new android.content.ContentValues();
                            values.put(android.provider.MediaStore.MediaColumns.DATA, file.getAbsolutePath());
                            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType);
                            
                            if (mimeType.startsWith("image/")) {
                                contentUri = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                            } else {
                                contentUri = getContentResolver().insert(android.provider.MediaStore.Files.getContentUri("external"), values);
                            }
                            
                            if (contentUri == null) {
                                android.util.Log.w("ChatActivity", "MediaStore insert returned null, using file URI");
                                contentUri = uri; // Fallback to original file URI
                            }
                        } catch (Exception e2) {
                            android.util.Log.e("ChatActivity", "Error creating content URI: " + e2.getMessage());
                            contentUri = uri; // Fallback to original file URI
                        }
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
            
            android.util.Log.d("ChatActivity", "Opening attachment - URI: " + contentUri + ", MIME type: " + mimeType + ", file exists: " + (file != null ? file.exists() : "N/A"));
            
            // ✅ Create intent with specific MIME type (not wildcard)
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            intent.setDataAndType(contentUri, mimeType);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            
            // ✅ Try to resolve activity
            android.content.pm.ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null) {
                android.content.Intent chooser = android.content.Intent.createChooser(intent, "Open " + (fileName != null ? fileName : "attachment"));
                startActivity(chooser);
            } else {
                // ✅ Fallback: Try with wildcard MIME type
                android.util.Log.w("ChatActivity", "No app found for specific MIME type, trying wildcard");
                intent.setDataAndType(contentUri, "*/*");
                resolveInfo = getPackageManager().resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
                if (resolveInfo != null) {
                    android.content.Intent chooser = android.content.Intent.createChooser(intent, "Open " + (fileName != null ? fileName : "attachment"));
                    startActivity(chooser);
                } else {
                    android.util.Log.e("ChatActivity", "No app available to open file - URI: " + contentUri + ", MIME: " + mimeType);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Error opening attachment: " + e.getMessage(), e);
        }
    }
    
    private void showAttachmentOptions() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
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
    
    private void sendMessage() {
        android.util.Log.d("ChatActivity", "sendMessage() called");
        
        // ✅ Add detailed logging to debug why messages aren't being sent
        if (messageInput == null) {
            android.util.Log.e("ChatActivity", "sendMessage: messageInput is null");
            return;
        }
        
        if (chatId == null || chatId == -1) {
            android.util.Log.e("ChatActivity", "sendMessage: chatId is null or -1, chatId=" + chatId);
            return;
        }
        
        if (userId == null || userId == -1) {
            android.util.Log.e("ChatActivity", "sendMessage: userId is null or -1, userId=" + userId);
            return;
        }
        
        String messageText = messageInput.getText().toString().trim();
        if (messageText.isEmpty()) {
            android.util.Log.d("ChatActivity", "sendMessage: message text is empty");
            return;
        }
        
        android.util.Log.d("ChatActivity", "sendMessage: chatId=" + chatId + ", userId=" + userId + ", messageText=" + messageText);
        android.util.Log.d("ChatActivity", "sendMessage: chatName=" + chatName + ", chatType=DIRECT (this is a direct chat)");
        
        // ✅ Validate chatId is valid before sending
        if (chatId == null || chatId <= 0) {
            android.util.Log.e("ChatActivity", "sendMessage: Invalid chatId: " + chatId);
            return;
        }
        
        // ✅ Ensure pending attachment state is cleared before sending text message
        pendingAttachmentPath = null;
        pendingAttachmentType = null;
        pendingAttachmentName = null;
        
        Message localMessage = new Message(messageText, "You", System.currentTimeMillis(), true);
        // ✅ Ensure text messages don't have attachment data
        localMessage.setAttachmentPath(null);
        localMessage.setAttachmentType(null);
        localMessage.setAttachmentName(null);
        messages.add(localMessage);
        messageAdapter.notifyItemInserted(messages.size() - 1);
        ChatStorage.saveMessages(this, String.valueOf(chatId), messages);
        // ✅ Use smooth scroll when user sends a message (better UX)
        scrollToBottomSmooth();
        
        // ✅ Track this message as recently sent to prevent WebSocket duplicates
        long currentTime = System.currentTimeMillis();
        recentlySentMessages.put(messageText, currentTime);
        // Clean up old entries (older than 30 seconds)
        recentlySentMessages.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > 30000);
        
        messageInput.setText("");
        
        if (chatApiService == null) {
            android.util.Log.e("ChatActivity", "sendMessage: chatApiService is null");
            return;
        }
        
        android.util.Log.d("ChatActivity", "sendMessage: Calling chatApiService.sendMessage()");
            chatApiService.sendMessage(chatId, userId, messageText, null, new ChatApiService.MessageCallback() {
                @Override
                public void onSuccess(Message message) {
                    runOnUiThread(() -> {
                        int position = messages.indexOf(localMessage);
                        if (position >= 0) {
                            // ✅ Preserve isSentByUser flag from local message (which is correct)
                            // The API response should already have it set correctly via parseMessageFromJson(userId)
                            // But ensure it's true for sent messages
                            if (localMessage.isSentByUser()) {
                                message.setSentByUser(true);
                            }
                            
                            // ✅ Ensure text messages don't have attachment data
                            // If local message was a text message (no attachment), clear attachment fields from API response
                            if (!localMessage.hasAttachment()) {
                                message.setAttachmentPath(null);
                                message.setAttachmentType(null);
                                message.setAttachmentName(null);
                                message.setAttachmentId(null);
                                android.util.Log.d("ChatActivity", "Cleared attachment fields from text message - messageId: " + message.getMessageId());
                            }
                            
                            messages.set(position, message);
                            messageAdapter.notifyItemChanged(position);
                            ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                            android.util.Log.d("ChatActivity", "Updated local message with API response, messageId: " + message.getMessageId() + 
                                            ", isSentByUser: " + message.isSentByUser() + 
                                            ", hasAttachment: " + message.hasAttachment());
                            
                            // ✅ Remove from recently sent tracking since API response confirms it was sent
                            // This allows the WebSocket echo check to work, but also allows re-sending same message later
                            recentlySentMessages.remove(messageText);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                    android.util.Log.e("ChatActivity", "Error sending message: " + error);
                            android.util.Log.e("ChatActivity", "Failed to send message: " + error);
                        
                        // Remove the local message if it failed to send
                        int position = messages.indexOf(localMessage);
                        if (position >= 0) {
                            messages.remove(position);
                            messageAdapter.notifyItemRemoved(position);
                            ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                }
            });
        }
            });
    }
    
    private void toggleReaction(Message message, String emoji) {
        if (message == null || emoji == null || emoji.isEmpty() || userId == null) {
            return;
        }
        
        try {
            java.util.Map<String, Integer> reactions = message.getReactions();
            boolean hasReaction = reactions != null && reactions.containsKey(emoji);
            
            // ✅ Update UI immediately (optimistic update)
            if (hasReaction) {
                message.removeReaction(emoji);
            } else {
                message.addReaction(emoji);
            }
            
            // ✅ Update UI instantly - no waiting for API
                                int position = messages.indexOf(message);
                                if (position >= 0) {
                                    messageAdapter.notifyItemChanged(position);
                                    ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                                }
            
            // ✅ Send API request in background (WebSocket will handle real-time updates)
            if (message.getMessageId() != null && chatApiService != null) {
                if (hasReaction) {
                    chatApiService.removeReaction(message.getMessageId(), userId, emoji, new ChatApiService.VoidCallback() {
                        @Override
                        public void onSuccess() {
                            // API call succeeded - WebSocket event will update UI
                            android.util.Log.d("ChatActivity", "Reaction removed successfully");
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Rollback on error
                            runOnUiThread(() -> {
                                message.addReaction(emoji);
                                int position = messages.indexOf(message);
                                if (position >= 0) {
                                    messageAdapter.notifyItemChanged(position);
                                    ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                                }
                                android.util.Log.e("ChatActivity", "Error removing reaction: " + error);
                            });
                        }
                    });
            } else {
                    chatApiService.addReaction(message.getMessageId(), userId, emoji, new ChatApiService.VoidCallback() {
                        @Override
                        public void onSuccess() {
                            // API call succeeded - WebSocket event will update UI
                            android.util.Log.d("ChatActivity", "Reaction added successfully");
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Rollback on error
                            runOnUiThread(() -> {
                                message.removeReaction(emoji);
                                int position = messages.indexOf(message);
                                if (position >= 0) {
                                    messageAdapter.notifyItemChanged(position);
                                    ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                                }
                                android.util.Log.e("ChatActivity", "Error adding reaction: " + error);
                            });
                        }
                    });
                }
            }
        } catch (Exception e) {
            android.util.Log.e("ChatActivity", "Error toggling reaction: " + e.getMessage());
        }
    }
    
    private void loadContactInfo() {
        if (contactNameText != null && chatName != null) {
            contactNameText.setText(chatName);
        }
    }
    
    private void loadMessages() {
        if (chatId == null || chatId == -1) {
            return;
        }
        
        // ✅ Prevent multiple simultaneous loads
        if (isLoadingMessages) {
            android.util.Log.d("ChatActivity", "loadMessages() already in progress, skipping duplicate call");
            return;
        }
        
        // ✅ Set loading flag to prevent WebSocket messages from being added during load
        isLoadingMessages = true;
        
        List<Message> cachedMessages = ChatStorage.loadMessages(this, String.valueOf(chatId));
        boolean hadCachedMessages = cachedMessages != null && !cachedMessages.isEmpty();
        
        if (hadCachedMessages) {
            messages.clear();
            messages.addAll(cachedMessages);
            messageAdapter.notifyDataSetChanged();
            // ✅ Don't auto-scroll when loading cached messages - preserve user's scroll position
            android.util.Log.d("ChatActivity", "Loaded " + cachedMessages.size() + " cached messages, not scrolling");
        }
        
        if (chatApiService != null) {
            // ✅ Pass userId to getMessagesByChat so parseMessageFromJson can correctly set isSentByUser
            // ✅ Always load from backend to ensure messages sent while user wasn't in chat are visible
            android.util.Log.d("ChatActivity", "Loading messages from backend for chatId: " + chatId + ", userId: " + userId);
            chatApiService.getMessagesByChat(chatId, userId, new ChatApiService.MessagesListCallback() {
                @Override
                public void onSuccess(List<Message> backendMessages) {
                    runOnUiThread(() -> {
                        isLoadingMessages = false; // ✅ Clear loading flag
                        android.util.Log.d("ChatActivity", "✅ Loaded " + (backendMessages != null ? backendMessages.size() : 0) + " messages from backend for chatId: " + chatId);
                        if (backendMessages != null) {
                            // ✅ Check if messages changed significantly (new messages added at end)
                            boolean shouldScrollToBottom = false;
                            if (!hadCachedMessages && !backendMessages.isEmpty()) {
                                // ✅ First time loading - scroll to bottom to show latest messages
                                shouldScrollToBottom = true;
                            } else if (!backendMessages.isEmpty() && !messages.isEmpty()) {
                                // ✅ Check if new messages were added at the end
                                Message lastCached = messages.isEmpty() ? null : messages.get(messages.size() - 1);
                                Message lastBackend = backendMessages.get(backendMessages.size() - 1);
                                if (lastCached == null || (lastBackend.getMessageId() != null && 
                                    !lastBackend.getMessageId().equals(lastCached.getMessageId()))) {
                                    // ✅ New messages at the end - only scroll if user was already at bottom
                                    int lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition();
                                    int totalItems = messages.size();
                                    shouldScrollToBottom = (lastVisiblePosition >= totalItems - 3); // Within 3 items of bottom
                                }
                            }
                            
                            // ✅ Always update with backend messages (even if empty) to ensure we have the latest data
                            // ✅ This ensures messages sent while the user wasn't in the chat are visible
                            int previousMessageCount = messages.size();
                            
                            // ✅ Clear existing messages and replace with backend messages
                            // This prevents duplicates from WebSocket messages that arrived during load
                            messages.clear();
                            if (!backendMessages.isEmpty()) {
                                // ✅ isSentByUser is now set correctly by parseMessageFromJson(userId)
                                // No need to manually set it here
                            messages.addAll(backendMessages);
                                // ✅ Only notify adapter once after all messages are added
                            messageAdapter.notifyDataSetChanged();
                            ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                                
                                android.util.Log.d("ChatActivity", "✅ Updated messages from backend - previous: " + previousMessageCount + 
                                            ", backend: " + backendMessages.size() + ", current: " + messages.size() + 
                                            " for chatId: " + chatId);
                                
                                // ✅ Only scroll to bottom if needed (first load or user was at bottom)
                                if (shouldScrollToBottom) {
                            scrollToBottom();
                                }
                            loadReactionsForMessages();
                                // ✅ Update button visibility after loading messages
                                updateScrollToBottomButtonVisibility();
                            } else {
                                // ✅ Backend returned empty list - clear cache and show empty state
                                android.util.Log.d("ChatActivity", "⚠️ Backend returned empty message list for chatId: " + chatId + 
                                            " (previous count: " + previousMessageCount + ")");
                                messageAdapter.notifyDataSetChanged();
                                ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                            }
                        } else {
                            android.util.Log.e("ChatActivity", "❌ Backend returned null message list for chatId: " + chatId);
                            // ✅ If backend returns null, clear cache to force reload next time
                            messages.clear();
                            messageAdapter.notifyDataSetChanged();
                            ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        isLoadingMessages = false; // ✅ Clear loading flag on error
                        android.util.Log.e("ChatActivity", "❌ Error loading messages: " + error);
                        // ✅ Don't clear messages on error - keep cached messages visible
                        // But log the error for debugging
                        android.util.Log.e("ChatActivity", "Error details - chatId: " + chatId + ", userId: " + userId + ", error: " + error);
                    });
                }
            });
        } else {
            android.util.Log.e("ChatActivity", "❌ chatApiService is null, cannot load messages for chatId: " + chatId);
        }
    }
    
    private void loadReactionsForMessages() {
        // ✅ Prevent multiple simultaneous reaction loads
        if (isLoadingReactions) {
            android.util.Log.d("ChatActivity", "loadReactionsForMessages() already in progress, skipping duplicate call");
            return;
        }
        
        if (messages == null || messages.isEmpty() || chatApiService == null) {
            return;
        }
        
        // ✅ Set loading flag to prevent duplicate calls
        isLoadingReactions = true;
        android.util.Log.d("ChatActivity", "Loading reactions for " + messages.size() + " messages");
        
        final int totalMessages = messages.size();
        final java.util.concurrent.atomic.AtomicInteger completedReactions = new java.util.concurrent.atomic.AtomicInteger(0);
        
        for (Message message : messages) {
            Long messageId = message.getMessageId();
            if (messageId != null && messageId > 0) {
                chatApiService.getReactions(messageId, new ChatApiService.AttachmentsListCallback() {
                    @Override
                    public void onSuccess(java.util.List<org.json.JSONObject> reactions) {
                        if (reactions != null) {
                            runOnUiThread(() -> {
                                java.util.Map<String, Integer> reactionMap = new java.util.HashMap<>();
                                for (org.json.JSONObject reactionJson : reactions) {
                                    try {
                                        String emoji = reactionJson.optString("emoji", "");
                                        if (!emoji.isEmpty()) {
                                            reactionMap.put(emoji, reactionMap.getOrDefault(emoji, 0) + 1);
                                        }
                                    } catch (Exception e) {
                                    }
                                }
                                message.setReactions(reactionMap);
                                int position = messages.indexOf(message);
                                if (position >= 0) {
                                    messageAdapter.notifyItemChanged(position);
                                }
                                
                                // ✅ Clear loading flag when all reactions are loaded
                                int completed = completedReactions.incrementAndGet();
                                if (completed >= totalMessages) {
                                    isLoadingReactions = false;
                                    android.util.Log.d("ChatActivity", "Finished loading reactions for all " + totalMessages + " messages");
                                }
                            });
                        } else {
                            // ✅ Clear loading flag on null reactions
                            int completed = completedReactions.incrementAndGet();
                            if (completed >= totalMessages) {
                                isLoadingReactions = false;
                            }
                        }
                    }
                    
                    @Override
                    public void onError(String error) {
                        // ✅ Clear loading flag on error
                        int completed = completedReactions.incrementAndGet();
                        if (completed >= totalMessages) {
                            isLoadingReactions = false;
                        }
                    }
                });
            } else {
                // ✅ Count messages without IDs as completed
                int completed = completedReactions.incrementAndGet();
                if (completed >= totalMessages) {
                    isLoadingReactions = false;
                }
            }
        }
    }
    
    private void setupWebSocket() {
        if (webSocketService == null) {
            webSocketService = ChatWebSocketService.getInstance();
        }
        
        webSocketService.setListener(new ChatWebSocketService.ChatWebSocketListener() {
            @Override
            public void onMessage(Message message) {
                runOnUiThread(() -> {
                    // ✅ Ignore WebSocket messages while loading messages from backend
                    // This prevents duplicates when opening a new chat
                    if (isLoadingMessages) {
                        android.util.Log.d("ChatActivity", "Ignoring WebSocket message (legacy) while loading messages from backend");
                        return;
                    }
                    
                    if (message != null && chatId != null) {
                        android.util.Log.d("ChatActivity", "onMessage received (legacy): " + message.getText());
                        
                        // ✅ Check if message already exists by messageId OR by content + sender + timestamp
                        // This prevents duplicates when legacy handler is called
                        boolean exists = false;
                        int existingIndex = -1;
                        for (int i = 0; i < messages.size(); i++) {
                            Message m = messages.get(i);
                            // Check by messageId first (most reliable)
                            if (message.getMessageId() != null && m.getMessageId() != null && 
                                m.getMessageId().equals(message.getMessageId())) {
                                exists = true;
                                existingIndex = i;
                                android.util.Log.d("ChatActivity", "onMessage (legacy) - Found duplicate by messageId: " + message.getMessageId());
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
                                android.util.Log.d("ChatActivity", "onMessage (legacy) - Found duplicate by content/sender/timestamp - existing: " + 
                                            (m.getMessageId() != null ? m.getMessageId() : "null") + 
                                            ", new: " + (message.getMessageId() != null ? message.getMessageId() : "null"));
                                break;
                            }
                        }
                        
                        if (exists && existingIndex >= 0) {
                            // ✅ Message already exists, skip adding
                            android.util.Log.d("ChatActivity", "onMessage (legacy) - Message already exists at index " + existingIndex + ", skipping duplicate");
                            return;
                        }
                        
                        if (!exists) {
                            // Set isSentByUser based on current user
                            if (userId != null && message.getSender() != null) {
                                // Compare sender with current user - need to get current username
                                SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                                String currentUsername = prefs.getString("username", "");
                                message.setSentByUser(message.getSender().equals(currentUsername));
                            }
                            
                            android.util.Log.d("ChatActivity", "Adding message to UI (legacy): " + message.getText());
                        messages.add(message);
                        messageAdapter.notifyItemInserted(messages.size() - 1);
                        ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                            // ✅ Use smooth scroll when receiving a new message (better UX)
                            scrollToBottomSmooth();
                            
                            // ✅ No notification popup when in chat - messages appear automatically
                        }
                    }
                });
            }
            
            @Override
            public void onChatCreated(org.json.JSONObject chat) {
                // Handle chat created event
                runOnUiThread(() -> {
                    android.util.Log.d("ChatActivity", "Chat created event received");
                });
            }
            
            @Override
            public void onNewMessage(org.json.JSONObject messageData) {
                runOnUiThread(() -> {
                    try {
                        // ✅ Ignore WebSocket messages while loading messages from backend
                        // This prevents duplicates when opening a new chat
                        if (isLoadingMessages) {
                            android.util.Log.d("ChatActivity", "Ignoring WebSocket message while loading messages from backend");
                            return;
                        }
                        
                        android.util.Log.d("ChatActivity", "onNewMessage received: " + messageData.toString());
                        
                        // Parse message from backend format
                        String content = messageData.optString("content", "");
                        
                        // Parse sender info
                        org.json.JSONObject senderObj = messageData.optJSONObject("sender");
                        String sender = "";
                        Long senderId = -1L;
                        if (senderObj != null) {
                            sender = senderObj.optString("username", "");
                            senderId = senderObj.optLong("id", -1);
                        }
                        
                        // ✅ FIRST CHECK: If this is a message sent by current user, check if we just sent it
                        // This prevents the WebSocket echo from adding a duplicate
                        if (userId != null && senderId.equals(userId) && content != null && !content.isEmpty()) {
                            // Check if we recently sent this exact message
                            if (recentlySentMessages.containsKey(content)) {
                                long sentTime = recentlySentMessages.get(content);
                                long currentTime = System.currentTimeMillis();
                                // If sent within last 10 seconds, this is likely our own message echo
                                if ((currentTime - sentTime) < 10000) {
                                    android.util.Log.d("ChatActivity", "onNewMessage - Ignoring WebSocket echo of recently sent message: " + content);
                                    // Remove from tracking since we've handled it
                                    recentlySentMessages.remove(content);
                                    // Still check if we need to update the local message with messageId
                                    for (int i = messages.size() - 1; i >= 0; i--) {
                                        Message m = messages.get(i);
                                        if (m.getMessageId() == null && 
                                            m.getText() != null && m.getText().equals(content) &&
                                            m.isSentByUser() &&
                                            Math.abs(m.getTimestamp() - currentTime) < 10000) {
                                            Long messageId = messageData.optLong("id", -1);
                                            if (messageId > 0) {
                                                m.setMessageId(messageId);
                                                messageAdapter.notifyItemChanged(i);
                                                ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                                                android.util.Log.d("ChatActivity", "onNewMessage - Updated local message with messageId: " + messageId);
                                            }
                                            return; // Don't add duplicate
                                        }
                                    }
                                    return; // Don't add duplicate
                                }
                            }
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
                                android.util.Log.e("ChatActivity", "Error parsing timestamp: " + e.getMessage());
                                timestamp = System.currentTimeMillis();
                            }
                        }
                        
                        // Determine if sent by current user
                        boolean isSentByUser = false;
                        if (userId != null && senderObj != null && senderId > 0) {
                            isSentByUser = senderId.equals(userId);
                        }
                        
                        // ✅ Get attachment info if available
                        org.json.JSONObject attachmentObj = messageData.optJSONObject("attachment");
                        String fileUrl = "";
                        String fileName = "";
                        String fileType = "";
                        Long fileSize = 0L;
                        Long attachmentId = null;
                        if (attachmentObj != null) {
                            fileUrl = attachmentObj.optString("fileUrl", "");
                            // ✅ Extract attachmentId if available
                            attachmentId = attachmentObj.optLong("id", -1);
                            if (attachmentId <= 0) {
                                attachmentId = null;
                            }
                            // ✅ If attachmentId not found, try to extract from fileUrl
                            if (attachmentId == null && fileUrl != null && !fileUrl.isEmpty() && fileUrl.contains("/attachments/")) {
                                try {
                                    int index = fileUrl.lastIndexOf("/attachments/");
                                    if (index >= 0) {
                                        String idStr = fileUrl.substring(index + "/attachments/".length());
                                        if (idStr.contains("?")) {
                                            idStr = idStr.substring(0, idStr.indexOf("?"));
                                        }
                                        attachmentId = Long.parseLong(idStr);
                                        android.util.Log.d("ChatActivity", "Extracted attachmentId from fileUrl: " + attachmentId);
                                    }
                                } catch (Exception e) {
                                    android.util.Log.e("ChatActivity", "Error extracting attachmentId from fileUrl: " + e.getMessage());
                                }
                            }
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
                            fileSize = attachmentObj.optLong("fileSize", 0);
                            android.util.Log.d("ChatActivity", "Received attachment via WebSocket - fileUrl: " + fileUrl + 
                                            ", fileName: " + fileName + ", fileType: " + fileType + ", attachmentId: " + attachmentId);
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
                            // ✅ Set attachmentId if available
                            if (attachmentId != null && attachmentId > 0) {
                                message.setAttachmentId(attachmentId);
                                android.util.Log.d("ChatActivity", "Set attachmentId: " + attachmentId + " for message: " + messageId);
                            }
                            android.util.Log.d("ChatActivity", "Created message with attachment - fileUrl: " + fileUrl + 
                                            ", fileName: " + fileName + ", fileType: " + fileType + ", attachmentId: " + attachmentId);
                        } else {
                            message = new Message(content, sender, timestamp, isSentByUser);
                        }
                        if (messageId > 0) {
                            message.setMessageId(messageId);
                        }
                        
                        // ✅ Check if message already exists by messageId OR by content + sender + timestamp
                        // This prevents duplicates when WebSocket messages arrive after backend load
                        boolean exists = false;
                        int existingIndex = -1;
                        
                        // ✅ FIRST: Check if this is a message sent by current user - if so, look for local message without ID
                        if (isSentByUser && userId != null) {
                            for (int i = messages.size() - 1; i >= 0; i--) { // Check from end (most recent first)
                                Message m = messages.get(i);
                                // Look for local message (no ID) with same content and recent timestamp
                                if (m.getMessageId() == null && 
                                    m.getText() != null && content != null &&
                                    m.getText().equals(content) &&
                                    m.isSentByUser() &&
                                    Math.abs(m.getTimestamp() - timestamp) < 10000) { // Within 10 seconds
                                    exists = true;
                                    existingIndex = i;
                                    android.util.Log.d("ChatActivity", "onNewMessage - Found duplicate by content (sent by user, no ID yet) at index " + i + ", updating with messageId: " + messageId);
                                    // Update the existing local message with messageId and other data from WebSocket
                                    m.setMessageId(messageId);
                                    m.setSentByUser(true);
                                    if (sender != null && !sender.isEmpty()) {
                                        m.setSender(sender);
                                    }
                                    if (message.hasAttachment()) {
                                        m.setAttachmentPath(message.getAttachmentPath());
                                        m.setAttachmentType(message.getAttachmentType());
                                        m.setAttachmentName(message.getAttachmentName());
                                        m.setAttachmentId(message.getAttachmentId());
                                    }
                                    messageAdapter.notifyItemChanged(i);
                                    ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                                    return; // Exit early - don't add duplicate
                                }
                            }
                        }
                        
                        for (int i = 0; i < messages.size(); i++) {
                            Message m = messages.get(i);
                            // Check by messageId first (most reliable)
                            if (messageId > 0 && m.getMessageId() != null && m.getMessageId().equals(messageId)) {
                                exists = true;
                                existingIndex = i;
                                android.util.Log.d("ChatActivity", "onNewMessage - Found duplicate by messageId: " + messageId + " at index " + i);
                                break;
                            }
                            // ✅ Also check by content, sender, and timestamp (within 5 seconds) for local messages without ID
                            // This prevents duplicates when WebSocket message arrives before API response updates local message
                            // ✅ Also check by isSentByUser flag for messages sent by current user (sender might be "You" vs actual username)
                            if (m.getMessageId() == null && messageId > 0 &&
                                m.getText() != null && content != null &&
                                m.getText().equals(content) && 
                                Math.abs(m.getTimestamp() - timestamp) < 5000) { // Within 5 seconds
                                // ✅ Check if both messages are from the same sender (either by sender name OR by isSentByUser flag)
                                boolean sameSender = false;
                                if (m.getSender() != null && sender != null && m.getSender().equals(sender)) {
                                    sameSender = true;
                                } else if (m.isSentByUser() && isSentByUser) {
                                    // ✅ Both messages are from current user (local message has "You" as sender, WebSocket has actual username)
                                    sameSender = true;
                                    android.util.Log.d("ChatActivity", "onNewMessage - Found duplicate by content + isSentByUser (sender mismatch: local='" + m.getSender() + "', websocket='" + sender + "')");
                                }
                                
                                if (sameSender) {
                                    exists = true;
                                    existingIndex = i;
                                    android.util.Log.d("ChatActivity", "onNewMessage - Found duplicate by content/sender/timestamp, updating with messageId: " + messageId + " at index " + i);
                                    // Update the existing message with messageId and other data from WebSocket
                                    m.setMessageId(messageId);
                                    m.setSentByUser(isSentByUser);
                                    // ✅ Update sender to actual username from WebSocket
                                    if (sender != null && !sender.isEmpty()) {
                                        m.setSender(sender);
                                    }
                                    if (message.hasAttachment()) {
                                        m.setAttachmentPath(message.getAttachmentPath());
                                        m.setAttachmentType(message.getAttachmentType());
                                        m.setAttachmentName(message.getAttachmentName());
                                        m.setAttachmentId(message.getAttachmentId());
                                    }
                                    messageAdapter.notifyItemChanged(i);
                                    ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                                    break;
                                }
                            }
                            // ✅ Also check by content, sender, and timestamp even if both have IDs (for safety)
                            // This catches edge cases where messages might have different IDs but same content
                            // ✅ Also check by isSentByUser flag for messages sent by current user (sender might be "You" vs actual username)
                            if (m.getMessageId() != null && messageId > 0 && !m.getMessageId().equals(messageId) &&
                                m.getText() != null && content != null &&
                                m.getText().equals(content) && 
                                Math.abs(m.getTimestamp() - timestamp) < 2000) { // Within 2 seconds (tighter window for messages with IDs)
                                // ✅ Check if both messages are from the same sender (either by sender name OR by isSentByUser flag)
                                boolean sameSender = false;
                                if (m.getSender() != null && sender != null && m.getSender().equals(sender)) {
                                    sameSender = true;
                                } else if (m.isSentByUser() && isSentByUser) {
                                    // ✅ Both messages are from current user (local message has "You" as sender, WebSocket has actual username)
                                    sameSender = true;
                                    android.util.Log.d("ChatActivity", "onNewMessage - Found duplicate by content + isSentByUser (both have IDs, sender mismatch: local='" + m.getSender() + "', websocket='" + sender + "')");
                                }
                                
                                if (sameSender) {
                                    exists = true;
                                    existingIndex = i;
                                    android.util.Log.d("ChatActivity", "onNewMessage - Found duplicate by content/sender/timestamp (both have IDs) - existing: " + m.getMessageId() + ", new: " + messageId + " at index " + i);
                                    break;
                                }
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
                                android.util.Log.d("ChatActivity", "onNewMessage - Found duplicate by content/sender/timestamp (no messageId) at index " + i);
                                break;
                            }
                        }
                        
                        if (exists && existingIndex >= 0) {
                            // ✅ Message already exists, skip adding
                            android.util.Log.d("ChatActivity", "onNewMessage - Message already exists at index " + existingIndex + ", skipping duplicate - messageId: " + messageId + ", content: " + (content != null && content.length() > 20 ? content.substring(0, 20) + "..." : content));
                            return;
                        }
                        
                        if (!exists && chatId != null) {
                            // ✅ Final safety check: verify message doesn't exist before adding
                            boolean finalCheck = false;
                            for (Message m : messages) {
                                if (messageId > 0 && m.getMessageId() != null && m.getMessageId().equals(messageId)) {
                                    finalCheck = true;
                                    android.util.Log.w("ChatActivity", "onNewMessage - Final check found duplicate by messageId: " + messageId + ", skipping add");
                                    break;
                                }
                            }
                            
                            if (!finalCheck) {
                                android.util.Log.d("ChatActivity", "onNewMessage - Adding new message to UI - messageId: " + messageId + ", content: " + (content != null && content.length() > 20 ? content.substring(0, 20) + "..." : content));
                                messages.add(message);
                                messageAdapter.notifyItemInserted(messages.size() - 1);
                                ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                                // ✅ Use smooth scroll when receiving a new message (better UX)
                                scrollToBottomSmooth();
                                
                                // ✅ No notification popup when in chat - messages appear automatically
                            }
                        } else {
                            android.util.Log.d("ChatActivity", "onNewMessage - Message already exists or chatId is null, skipping - exists: " + exists + ", chatId: " + chatId + ", messageId: " + messageId);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("ChatActivity", "Error handling new_message event: " + e.getMessage());
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
                        for (int i = 0; i < messages.size(); i++) {
                            Message m = messages.get(i);
                            if (m.getMessageId() != null && m.getMessageId().equals(messageId)) {
                                m.setText(content);
                                messageAdapter.notifyItemChanged(i);
                                ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                                break;
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("ChatActivity", "Error handling message_edited event: " + e.getMessage());
                    }
                });
            }
            
            @Override
            public void onMessageDeleted(Long messageId) {
                runOnUiThread(() -> {
                    // Remove message from list
                    for (int i = 0; i < messages.size(); i++) {
                        Message m = messages.get(i);
                        if (m.getMessageId() != null && m.getMessageId().equals(messageId)) {
                            messages.remove(i);
                            messageAdapter.notifyItemRemoved(i);
                            ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                            break;
                        }
                    }
                });
            }
            
            @Override
            public void onTyping(Long userId, boolean isTyping) {
                // Handle typing indicator
                runOnUiThread(() -> {
                    android.util.Log.d("ChatActivity", "User " + userId + " is typing: " + isTyping);
                    // TODO: Show typing indicator in UI
                });
            }
            
            @Override
            public void onReaction(Long messageId, String emoji, Long reactionUserId, String action) {
                // ✅ Handle reaction events in real-time - instant update (no API call, no scroll)
                runOnUiThread(() -> {
                    try {
                        android.util.Log.d("ChatActivity", "✅ Reaction event: " + action + " " + emoji + " on message " + messageId + " by user " + reactionUserId);
                        
                        // ✅ Save current scroll position to prevent auto-scroll
                        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
                        boolean wasAtBottom = lastVisiblePosition >= messages.size() - 2; // Within 2 items of bottom
                        
                        // Find the message in the list
                        for (int i = 0; i < messages.size(); i++) {
                            Message m = messages.get(i);
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
                                ChatStorage.saveMessages(ChatActivity.this, String.valueOf(chatId), messages);
                                
                                // ✅ Only scroll to bottom if user was already at bottom
                                if (wasAtBottom && i == messages.size() - 1) {
                        scrollToBottom();
                                }
                                
                                android.util.Log.d("ChatActivity", "✅ Reaction updated instantly in UI");
                                break;
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.e("ChatActivity", "Error handling reaction event: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
            
            @Override
            public void onConnect() {
                android.util.Log.d("ChatActivity", "WebSocket connected");
                // ✅ Auto-join chat after connection is established
                if (chatId != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (webSocketService.isConnected()) {
                            webSocketService.joinChat(String.valueOf(chatId));
                            android.util.Log.d("ChatActivity", "Auto-joined chat after connection: " + chatId);
                        }
                    }, 300);
                }
            }
            
            @Override
            public void onDisconnect() {
                android.util.Log.d("ChatActivity", "WebSocket disconnected");
            }
            
            @Override
            public void onError(Exception e) {
                android.util.Log.e("ChatActivity", "WebSocket error: " + e.getMessage());
            }
        });
        
        // Chat WebSocket requires userId (Long), not username
        if (userId != null && userId > 0) {
            // ✅ Use ServerConfig for WebSocket URL (supports local and production)
            String serverUrl = com.example.occasio.utils.ServerConfig.WS_CHAT_URL;
            
            // ✅ Set chatId before connecting so it auto-joins after connection
        if (chatId != null) {
            webSocketService.joinChat(String.valueOf(chatId));
        }
        
            webSocketService.connect(userId, serverUrl);
            android.util.Log.d("ChatActivity", "Connecting to WebSocket for user: " + userId + " at " + serverUrl + ", chatId: " + chatId);
            
            // ✅ Also join chat after a short delay to ensure connection is established
            if (chatId != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (webSocketService.isConnected()) {
                        webSocketService.joinChat(String.valueOf(chatId));
                        android.util.Log.d("ChatActivity", "Joined chat after connection: " + chatId);
                    }
                }, 500);
            }
        } else {
            android.util.Log.e("ChatActivity", "Cannot connect to WebSocket: userId is invalid");
            // Try to fetch userId from backend
            fetchUserIdForWebSocket();
        }
    }
    
    private void fetchUserIdForWebSocket() {
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");
        if (username.isEmpty()) {
            android.util.Log.e("ChatActivity", "Cannot fetch userId: username is empty");
            return;
        }
        
        String url = com.example.occasio.utils.ServerConfig.USER_INFO_BY_USERNAME + username;
        com.android.volley.toolbox.JsonObjectRequest request = new com.android.volley.toolbox.JsonObjectRequest(
            com.android.volley.Request.Method.GET,
            url,
            null,
            response -> {
                try {
                    Long fetchedUserId = response.getLong("id");
                    userId = fetchedUserId;
                    // ✅ Use ServerConfig for WebSocket URL (supports local and production)
                    String serverUrl = com.example.occasio.utils.ServerConfig.WS_CHAT_URL;
                    
                    // ✅ Set chatId before connecting so it auto-joins after connection
                    if (chatId != null) {
                        webSocketService.joinChat(String.valueOf(chatId));
                    }
                    
                    webSocketService.connect(userId, serverUrl);
                    android.util.Log.d("ChatActivity", "WebSocket connected with fetched userId: " + userId + " at " + serverUrl + ", chatId: " + chatId);
                    
                    // ✅ Also join chat after a short delay to ensure connection is established
                    if (chatId != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            if (webSocketService.isConnected()) {
                                webSocketService.joinChat(String.valueOf(chatId));
                                android.util.Log.d("ChatActivity", "Joined chat after connection: " + chatId);
                            }
                        }, 500);
                    }
                } catch (Exception e) {
                    android.util.Log.e("ChatActivity", "Error parsing userId: " + e.getMessage());
                }
            },
            error -> {
                android.util.Log.e("ChatActivity", "Error fetching userId: " + (error != null ? error.getMessage() : "Unknown error"));
                // ✅ Don't show error toast - just log it silently
                // The userId might already be set from onCreate, so this is just a fallback
                if (userId == null || userId <= 0) {
                    android.util.Log.w("ChatActivity", "userId is still null after fetch attempt - WebSocket may not work");
                }
            }
        );
        com.example.occasio.api.VolleySingleton.getInstance(this).getRequestQueue().add(request);
    }
    
    private void scrollToBottom() {
        messagesRecyclerView.post(() -> {
            if (messages.size() > 0) {
                // ✅ Use scrollToPosition for instant scroll (no animation)
                // This prevents the jarring scroll animation when loading messages
                messagesRecyclerView.scrollToPosition(messages.size() - 1);
            }
        });
    }
    
    private void scrollToBottomSmooth() {
        // ✅ Use smooth scroll when user clicks scroll to bottom button or sends a new message
        messagesRecyclerView.post(() -> {
            if (messages.size() > 0) {
                messagesRecyclerView.smoothScrollToPosition(messages.size() - 1);
                // ✅ Hide button after scrolling
                updateScrollToBottomButtonVisibility();
            }
        });
    }
    
    private void updateScrollToBottomButtonVisibility() {
        if (scrollToBottomButton == null || layoutManager == null || messages.isEmpty()) {
            return;
        }
        
        // ✅ Show button if user is not at the bottom (within 3 items)
        int lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition();
        int totalItems = messages.size();
        boolean isAtBottom = (lastVisiblePosition >= totalItems - 3);
        
        if (isAtBottom) {
            scrollToBottomButton.setVisibility(android.view.View.GONE);
        } else {
            scrollToBottomButton.setVisibility(android.view.View.VISIBLE);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (chatId != null) {
            ChatStorage.saveMessages(this, String.valueOf(chatId), messages);
        }
        if (webSocketService != null && chatId != null) {
            webSocketService.leaveChat(String.valueOf(chatId));
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatId != null) {
            ChatStorage.saveMessages(this, String.valueOf(chatId), messages);
        }
        if (webSocketService != null && chatId != null) {
            webSocketService.leaveChat(String.valueOf(chatId));
        }
    }
}
