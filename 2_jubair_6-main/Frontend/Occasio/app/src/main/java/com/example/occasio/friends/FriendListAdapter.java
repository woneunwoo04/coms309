package com.example.occasio.friends;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;

public class FriendListAdapter extends RecyclerView.Adapter<FriendListAdapter.FriendViewHolder> {
    
    private List<JSONObject> friends;
    private OnFriendActionListener listener;
    
    public interface OnFriendActionListener {
        void onRemoveClick(JSONObject friend);
    }
    
    public FriendListAdapter(List<JSONObject> friends, OnFriendActionListener listener) {
        this.friends = friends;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public FriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend, parent, false);
        return new FriendViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull FriendViewHolder holder, int position) {
        JSONObject friend = friends.get(position);
        holder.bind(friend, listener);
    }
    
    @Override
    public int getItemCount() {
        return friends != null ? friends.size() : 0;
    }
    
    static class FriendViewHolder extends RecyclerView.ViewHolder {
        private TextView friendNameText;
        private TextView friendUsernameText;
        private TextView friendEmailText;
        private Button removeButton;
        
        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            friendNameText = itemView.findViewById(R.id.friend_name_tv);
            friendUsernameText = itemView.findViewById(R.id.friend_username_tv);
            friendEmailText = itemView.findViewById(R.id.friend_email_tv);
            removeButton = itemView.findViewById(R.id.friend_remove_btn);
            
            // Hide the add button if it exists
            Button addButton = itemView.findViewById(R.id.add_friend_btn);
            if (addButton != null) {
                addButton.setVisibility(View.GONE);
            }
            
            // Check if critical views are null
            if (friendNameText == null || friendUsernameText == null || 
                friendEmailText == null || removeButton == null) {
                android.util.Log.e("FriendListAdapter", "Some views are null in FriendViewHolder");
            }
        }
        
        public void bind(JSONObject friend, OnFriendActionListener listener) {
            if (friend == null) {
                android.util.Log.e("FriendListAdapter", "Friend JSONObject is null");
                return;
            }
            
            try {
                String username = friend.optString("username", "Unknown");
                String email = friend.optString("email", "");
                
                // Get firstName and lastName, handling null values properly
                String firstName = friend.optString("firstName", null);
                String lastName = friend.optString("lastName", null);
                
                // Handle case where optString might return the string "null"
                if (firstName != null && (firstName.equals("null") || firstName.isEmpty())) {
                    firstName = null;
                }
                if (lastName != null && (lastName.equals("null") || lastName.isEmpty())) {
                    lastName = null;
                }
                
                // Set name
                if (friendNameText != null) {
                    String displayName;
                    if (firstName != null && lastName != null) {
                        // Both names are available
                        displayName = firstName.trim() + " " + lastName.trim();
                    } else if (firstName != null) {
                        // Only first name
                        displayName = firstName.trim();
                    } else if (lastName != null) {
                        // Only last name
                        displayName = lastName.trim();
                    } else {
                        // No name available, use username
                        displayName = username;
                    }
                    friendNameText.setText(displayName);
                } else {
                    android.util.Log.e("FriendListAdapter", "friendNameText is null");
                }
                
                // Set username
                if (friendUsernameText != null) {
                    friendUsernameText.setText("@" + username);
                } else {
                    android.util.Log.e("FriendListAdapter", "friendUsernameText is null");
                }
                
                // Set email
                if (friendEmailText != null) {
                    friendEmailText.setText(email);
                } else {
                    android.util.Log.e("FriendListAdapter", "friendEmailText is null");
                }
                
                // Set remove button
                if (removeButton != null) {
                    removeButton.setVisibility(View.VISIBLE);
                    removeButton.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onRemoveClick(friend);
                        }
                    });
                } else {
                    android.util.Log.e("FriendListAdapter", "removeButton is null");
                }
            } catch (Exception e) {
                android.util.Log.e("FriendListAdapter", "Error binding friend: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

