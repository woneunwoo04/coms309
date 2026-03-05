package com.example.occasio.messaging;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import java.io.File;
import java.util.List;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {
    
    private List<ChatItem> chatList;
    private OnChatItemClickListener clickListener;

    public interface OnChatItemClickListener {
        void onChatItemClick(ChatItem chatItem);
    }

    public ChatListAdapter(List<ChatItem> chatList, OnChatItemClickListener clickListener) {
        this.chatList = chatList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_list, parent, false);
        return new ChatViewHolder(view, parent.getContext());
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatItem chatItem = chatList.get(position);
        holder.bind(chatItem, clickListener);
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private TextView chatName;
        private TextView lastMessage;
        private TextView timestamp;
        private TextView groupIndicator;
        private ImageView chatAvatar;
        private Context context;

        public ChatViewHolder(@NonNull View itemView, Context context) {
            super(itemView);
            this.context = context;
            chatName = itemView.findViewById(R.id.chat_name);
            lastMessage = itemView.findViewById(R.id.last_message);
            timestamp = itemView.findViewById(R.id.timestamp);
            groupIndicator = itemView.findViewById(R.id.group_indicator);
            
            View avatarView = itemView.findViewById(R.id.chat_avatar);
            if (avatarView instanceof ImageView) {
                chatAvatar = (ImageView) avatarView;
            }
        }

        public void bind(ChatItem chatItem, OnChatItemClickListener clickListener) {
            chatName.setText(chatItem.getName());
            lastMessage.setText(chatItem.getLastMessage());
            timestamp.setText(chatItem.getTimestamp());
            
            if (chatItem.isGroup()) {
                groupIndicator.setVisibility(View.VISIBLE);
                groupIndicator.setText("👥");
            } else {
                groupIndicator.setVisibility(View.GONE);
            }
            
            loadChatAvatar(chatItem);

            itemView.setOnClickListener(v -> {
                android.util.Log.d("ChatListAdapter", "Item clicked: " + chatItem.getName());
                if (clickListener != null) {
                    clickListener.onChatItemClick(chatItem);
                } else {
                    android.util.Log.e("ChatListAdapter", "Click listener is null!");
                }
            });
        }
        
        private void loadChatAvatar(ChatItem chatItem) {
            if (chatAvatar == null || context == null) return;
            
            String avatarPath = chatItem.getAvatar();
            if (avatarPath != null && !avatarPath.isEmpty()) {
                try {
                    if (avatarPath.startsWith("/")) {
                        File photoFile = new File(avatarPath);
                        if (photoFile.exists()) {
                            Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                            if (bitmap != null) {
                                chatAvatar.setImageBitmap(bitmap);
                                return;
                            }
                        }
                    }
                    
                    File photosDir = new File(context.getFilesDir(), "profile_photos");
                    if (photosDir.exists()) {
                        File[] files = photosDir.listFiles();
                        if (files != null && files.length > 0) {
                            File photoFile = files[0];
                            if (photoFile.exists()) {
                                Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                                if (bitmap != null) {
                                    chatAvatar.setImageBitmap(bitmap);
                                    return;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("ChatListAdapter", "Error loading chat avatar: " + e.getMessage());
                }
            }
            
            chatAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
        }
    }
}

