package com.example.occasio.messaging;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.occasio.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private List<Message> messages;
    private Context context;
    private OnReactionClickListener reactionClickListener;
    private OnMessageLongClickListener messageLongClickListener;
    private OnAttachmentClickListener attachmentClickListener;
    private Long currentUserId;

    public interface OnReactionClickListener {
        void onReactionClick(Message message, String emoji);
    }
    
    public interface OnMessageLongClickListener {
        void onMessageLongClick(Message message);
    }
    
    public interface OnAttachmentClickListener {
        void onAttachmentClick(Message message);
    }

    private static final String[] REACTION_EMOJIS = {"👍", "❤️", "😂", "😮", "😢", "🙏"};

    public MessageAdapter(Context context, List<Message> messages, Long currentUserId) {
        this.context = context;
        this.messages = messages;
        this.currentUserId = currentUserId;
    }

    public void setOnReactionClickListener(OnReactionClickListener listener) {
        this.reactionClickListener = listener;
    }
    
    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.messageLongClickListener = listener;
    }
    
    public void setOnAttachmentClickListener(OnAttachmentClickListener listener) {
        this.attachmentClickListener = listener;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // ✅ Swapped layouts: viewType 0 = received (left), viewType 1 = sent (right)
        // Like iMessage: sender messages on RIGHT, others on LEFT
        int layoutId = viewType == 0 ? R.layout.item_message_received : R.layout.item_message_sent;
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        // ✅ Swapped: sender messages on RIGHT (viewType 1 = item_message_sent), others on LEFT (viewType 0 = item_message_received)
        // Like iMessage: your messages on right, others on left
        return messages.get(position).isSentByUser() ? 1 : 0;
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        private TextView messageText;
        private TextView timeText;
        private ImageView profilePicture;
        private LinearLayout messageBubble;
        private LinearLayout attachmentContainer;
        private ImageView attachmentImage;
        private TextView attachmentName;
        private LinearLayout reactionsContainer;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
            timeText = itemView.findViewById(R.id.message_time);
            profilePicture = itemView.findViewById(R.id.profile_picture);
            messageBubble = itemView.findViewById(R.id.message_bubble);
            attachmentContainer = itemView.findViewById(R.id.attachment_container);
            attachmentImage = itemView.findViewById(R.id.attachment_image);
            attachmentName = itemView.findViewById(R.id.attachment_name);
            reactionsContainer = itemView.findViewById(R.id.reactions_container);
        }

        public void bind(Message message) {
            if (message == null) {
                return;
            }
            
            try {
                if (messageText != null) {
                    // ✅ Handle message text - if null/empty and has attachment, show nothing or default
                    String text = message.getText();
                    if (text == null || text.isEmpty() || text.equals("null")) {
                        if (message.hasAttachment()) {
                            // For attachments without caption, show empty or a default
                            text = ""; // Don't show "null" for attachments
                        } else {
                            text = "";
                        }
                    }
                    messageText.setText(text);
                    // Hide text view if empty and has attachment (attachment will be shown separately)
                    if (messageText != null) {
                        if (text.isEmpty() && message.hasAttachment()) {
                            messageText.setVisibility(View.GONE);
                        } else {
                            messageText.setVisibility(View.VISIBLE);
                        }
                    }
                }
                
                if (timeText != null) {
                    long timestamp = message.getTimestamp();
                    SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
                    timeText.setText(sdf.format(new Date(timestamp)));
                    timeText.setVisibility(View.VISIBLE);
                }

                setProfilePicture(message);

                if (message.hasAttachment()) {
                    displayAttachment(message);
                } else {
                    hideAttachment();
                }

                if (message.hasReactions()) {
                    displayReactions(message);
                } else {
                    if (reactionsContainer != null) {
                        reactionsContainer.setVisibility(View.GONE);
                    }
                }

                if (messageBubble != null) {
                    if (message.isSentByUser() && messageLongClickListener != null) {
                        messageBubble.setOnLongClickListener(v -> {
                            messageLongClickListener.onMessageLongClick(message);
                            return true;
                        });
                    } else {
                        messageBubble.setOnLongClickListener(v -> {
                            showReactionPicker(message);
                            return true;
                        });
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("MessageAdapter", "Error binding message: " + e.getMessage(), e);
            }
        }
        
        private void setProfilePicture(Message message) {
            if (profilePicture == null) return;
            
            String profilePictureUrl = message.getSenderProfilePictureUrl();
            String senderName = message.getSender();
            if (senderName == null || senderName.isEmpty()) {
                senderName = "User";
            }
            
            // If profile picture URL exists, load it with Glide
            if (profilePictureUrl != null && !profilePictureUrl.isEmpty()) {
                // Build full URL if it's a relative path
                String fullUrl = profilePictureUrl;
                if (!profilePictureUrl.startsWith("http://") && !profilePictureUrl.startsWith("https://")) {
                    // If it's a relative path, prepend base URL
                    String baseUrl = com.example.occasio.utils.ServerConfig.BASE_URL;
                    if (profilePictureUrl.startsWith("/")) {
                        fullUrl = baseUrl + profilePictureUrl;
                    } else {
                        fullUrl = baseUrl + "/" + profilePictureUrl;
                    }
                }
                
                // Create placeholder drawable from avatar bitmap
                android.graphics.Bitmap placeholderBitmap = createPlaceholderAvatar(senderName);
                Drawable placeholderDrawable = new BitmapDrawable(context.getResources(), placeholderBitmap);
                
                // Use Glide to load the image with fallback to generated avatar
                RequestOptions requestOptions = new RequestOptions()
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(placeholderDrawable)
                    .error(placeholderDrawable);
                
                Glide.with(context)
                    .load(fullUrl)
                    .apply(requestOptions)
                    .into(profilePicture);
            } else {
                // No profile picture URL, use generated avatar
                android.graphics.Bitmap avatarBitmap = createPlaceholderAvatar(senderName);
                profilePicture.setImageBitmap(avatarBitmap);
            }
        }
        
        private android.graphics.Bitmap createPlaceholderAvatar(String senderName) {
            if (senderName == null || senderName.isEmpty()) {
                senderName = "User";
            }
            
            int colorResId = getColorForName(senderName);
            int color;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                color = context.getResources().getColor(colorResId, null);
            } else {
                color = context.getResources().getColor(colorResId);
            }
            
            String initial = senderName.substring(0, 1).toUpperCase();
            
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            
            android.graphics.Paint backgroundPaint = new android.graphics.Paint();
            backgroundPaint.setColor(color);
            canvas.drawCircle(20, 20, 20, backgroundPaint);
            
            android.graphics.Paint textPaint = new android.graphics.Paint();
            textPaint.setColor(android.graphics.Color.WHITE);
            textPaint.setTextSize(18);
            textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
            textPaint.setAntiAlias(true);
            
            android.graphics.Rect bounds = new android.graphics.Rect();
            textPaint.getTextBounds(initial, 0, initial.length(), bounds);
            float y = canvas.getHeight() / 2f + bounds.height() / 2f;
            
            canvas.drawText(initial, canvas.getWidth() / 2f, y, textPaint);
            return bitmap;
        }
        
        private int getColorForName(String name) {
            int[] colors = {
                R.color.primary_fall,
                R.color.accent_fall,
                android.R.color.holo_blue_dark,
                android.R.color.holo_green_dark,
                android.R.color.holo_orange_dark,
                android.R.color.holo_purple,
                android.R.color.holo_red_dark
            };
            int hash = name.hashCode();
            int index = Math.abs(hash) % colors.length;
            return colors[index];
        }

        private void displayAttachment(Message message) {
            // ✅ Only show attachment if message actually has an attachment
            if (message == null || !message.hasAttachment()) {
                hideAttachment();
                return;
            }
            
            attachmentContainer.setVisibility(View.VISIBLE);
            
            // ✅ Set container width to be wider for horizontal rectangle
            android.view.ViewGroup.LayoutParams containerParams = attachmentContainer.getLayoutParams();
            if (containerParams != null) {
                int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                int desiredWidth = (int) (screenWidth * 0.75f); // 75% of screen width (slightly smaller)
                containerParams.width = desiredWidth;
                attachmentContainer.setLayoutParams(containerParams);
            }
            // ✅ Show attachment name or default to "Attachment" if null/empty
            // ✅ Also handle the string "null" which can come from JSON parsing
            String displayName = message.getAttachmentName();
            if (displayName == null || displayName.isEmpty() || displayName.equals("null")) {
                // Try to determine a better default based on file type
                String fileType = message.getAttachmentType();
                if (fileType != null && fileType.toLowerCase().contains("image")) {
                    displayName = "Image";
                } else if (fileType != null && fileType.toLowerCase().contains("pdf")) {
                    displayName = "Document";
                } else {
                    displayName = "Attachment";
                }
            }
            attachmentName.setText(displayName);
            android.util.Log.d("MessageAdapter", "Displaying attachment - name: " + displayName + ", path: " + message.getAttachmentPath());
            
            String fileType = message.getAttachmentType();
            if (fileType != null && (fileType.startsWith("image/") || fileType.equals("image") || fileType.equals("IMAGE"))) {
                attachmentImage.setVisibility(View.VISIBLE);
                
                // ✅ Set image view dimensions for horizontal rectangle
                android.view.ViewGroup.LayoutParams imageParams = attachmentImage.getLayoutParams();
                if (imageParams != null) {
                    int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                    int desiredWidth = (int) (screenWidth * 0.75f); // 75% of screen width (slightly smaller)
                    int desiredHeight = (int) (desiredWidth * 0.6f); // 60% of width for horizontal rectangle
                    imageParams.width = desiredWidth;
                    imageParams.height = desiredHeight;
                    attachmentImage.setLayoutParams(imageParams);
                    android.util.Log.d("MessageAdapter", "Set image dimensions - width: " + desiredWidth + ", height: " + desiredHeight);
                }
                
                String attachmentPath = message.getAttachmentPath();
                if (attachmentPath != null && !attachmentPath.isEmpty()) {
                    try {
                        // ✅ Build full URL if it's a relative path
                        String fullUrl = attachmentPath;
                        if (!attachmentPath.startsWith("http://") && !attachmentPath.startsWith("https://") && !attachmentPath.startsWith("file://") && !attachmentPath.startsWith("content://")) {
                            // It's a relative path like "/api/messages/attachments/123"
                            // Prepend base URL (BASE_URL already includes /api)
                            String baseUrl = com.example.occasio.utils.ServerConfig.BASE_URL;
                            if (attachmentPath.startsWith("/api/")) {
                                // Path already includes /api, just prepend base URL
                                fullUrl = baseUrl + attachmentPath;
                            } else if (attachmentPath.startsWith("/")) {
                                // Path starts with / but not /api, add /api
                                fullUrl = baseUrl + "/api" + attachmentPath;
                            } else {
                                // Path doesn't start with /, add /api/
                                fullUrl = baseUrl + "/api/" + attachmentPath;
                            }
                        }
                        
                        // ✅ Make final copy for use in inner class
                        final String finalUrl = fullUrl;
                        android.util.Log.d("MessageAdapter", "Loading attachment image from: " + finalUrl + " (original path: " + attachmentPath + ")");
                        
                        // ✅ Use Glide to load image from URL with proper sizing
                        // Calculate dimensions for horizontal rectangle (wider than tall)
                        // Make it bigger than text messages - use 75% of screen width (slightly smaller)
                        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                        int maxWidth = (int) (screenWidth * 0.75f);
                        // For horizontal rectangle, use 16:9 or 2:1 aspect ratio
                        // Height should be about 60% of width for horizontal rectangle
                        int maxHeight = (int) (maxWidth * 0.6f); // 2:1.2 aspect ratio (wider than tall)
                        
                        // Ensure minimum size for visibility
                        if (maxWidth < 350) {
                            maxWidth = 350;
                            maxHeight = 210;
                        }
                        
                        android.util.Log.d("MessageAdapter", "Image dimensions - maxWidth: " + maxWidth + ", maxHeight: " + maxHeight);
                        
                        RequestOptions requestOptions = new RequestOptions()
                            .fitCenter() // Use fitCenter to maintain aspect ratio without cropping
                            .override(maxWidth, maxHeight) // Set max dimensions for horizontal rectangle
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image);
                        
                        // ✅ Add error listener to log failures
                        Glide.with(context)
                            .load(finalUrl)
                            .apply(requestOptions)
                            .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                                @Override
                                public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                                    android.util.Log.e("MessageAdapter", "Glide failed to load image from: " + finalUrl + ", error: " + (e != null ? e.getMessage() : "unknown"));
                                    return false; // Let Glide show the error placeholder
                                }
                                
                                @Override
                                public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                    android.util.Log.d("MessageAdapter", "Glide successfully loaded image from: " + finalUrl);
                                    return false;
                                }
                            })
                            .into(attachmentImage);
                    } catch (Exception e) {
                        android.util.Log.e("MessageAdapter", "Error loading image: " + e.getMessage(), e);
                        attachmentImage.setImageResource(android.R.drawable.ic_menu_report_image);
                    }
                } else {
                    android.util.Log.w("MessageAdapter", "Attachment path is null or empty for message - name: " + displayName);
                    attachmentImage.setImageResource(android.R.drawable.ic_menu_report_image);
                }
            } else {
                attachmentImage.setVisibility(View.GONE);
            }
            
            if (attachmentContainer != null && attachmentClickListener != null) {
                attachmentContainer.setOnClickListener(v -> {
                    attachmentClickListener.onAttachmentClick(message);
                });
            }
        }

        private void hideAttachment() {
            attachmentContainer.setVisibility(View.GONE);
        }

        private void displayReactions(Message message) {
            if (reactionsContainer == null) {
                return;
            }
            
            reactionsContainer.setVisibility(View.VISIBLE);
            reactionsContainer.removeAllViews();

            Map<String, Integer> reactions = message.getReactions();
            if (reactions == null || reactions.isEmpty()) {
                reactionsContainer.setVisibility(View.GONE);
                return;
            }
            
            try {
                for (Map.Entry<String, Integer> entry : reactions.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    
                    TextView reactionView = new TextView(context);
                    reactionView.setText(entry.getKey() + " " + entry.getValue());
                    reactionView.setPadding(8, 4, 8, 4);
                    reactionView.setBackground(context.getResources().getDrawable(R.drawable.card_background_fall));
                    reactionView.setTextSize(12);
                    reactionView.setOnClickListener(v -> {
                        if (reactionClickListener != null && entry.getKey() != null) {
                            reactionClickListener.onReactionClick(message, entry.getKey());
                        }
                    });
                    reactionsContainer.addView(reactionView);
                }
            } catch (Exception e) {
                android.util.Log.e("MessageAdapter", "Error displaying reactions: " + e.getMessage(), e);
                reactionsContainer.setVisibility(View.GONE);
            }
        }

        private void showReactionPicker(Message message) {
            if (reactionClickListener == null || message == null) {
                return;
            }
            
            try {
                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.setPadding(16, 16, 16, 16);

                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
                builder.setTitle("Add Reaction");
                
                final android.app.AlertDialog[] dialogRef = new android.app.AlertDialog[1];
                
                for (String emoji : REACTION_EMOJIS) {
                    if (emoji == null || emoji.isEmpty()) {
                        continue;
                    }
                    
                    TextView emojiView = new TextView(context);
                    emojiView.setText(emoji);
                    emojiView.setTextSize(24);
                    emojiView.setPadding(12, 12, 12, 12);
                    
                    final String emojiFinal = emoji;
                    emojiView.setOnClickListener(v -> {
                        try {
                            if (reactionClickListener != null && message != null) {
                                reactionClickListener.onReactionClick(message, emojiFinal);
                            }
                            if (dialogRef[0] != null && dialogRef[0].isShowing()) {
                                dialogRef[0].dismiss();
                            }
                        } catch (Exception e) {
                            android.util.Log.e("MessageAdapter", "Error handling reaction click: " + e.getMessage(), e);
                        }
                    });
                    layout.addView(emojiView);
                }

                builder.setView(layout);
                dialogRef[0] = builder.create();
                dialogRef[0].show();
            } catch (Exception e) {
                android.util.Log.e("MessageAdapter", "Error showing reaction picker: " + e.getMessage(), e);
            }
        }
    }
}
