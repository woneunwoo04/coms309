package com.example.occasio.friends;
import com.example.occasio.R;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.FriendViewHolder> {
    
    private List<Friend> friends = new ArrayList<>();
    private OnAddFriendClickListener addFriendClickListener;
    private OnDeleteFriendClickListener deleteFriendClickListener;
    private boolean isMyFriendsMode = false;
    
    public interface OnAddFriendClickListener {
        void onAddFriendClick(Friend friend);
    }
    
    public interface OnDeleteFriendClickListener {
        void onDeleteFriendClick(Friend friend);
    }
    
    public FriendAdapter(OnAddFriendClickListener listener) {
        this.addFriendClickListener = listener;
        this.isMyFriendsMode = false;
    }
    
    public FriendAdapter(OnAddFriendClickListener addListener, OnDeleteFriendClickListener deleteListener) {
        this.addFriendClickListener = addListener;
        this.deleteFriendClickListener = deleteListener;
        this.isMyFriendsMode = true;
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
        Friend friend = friends.get(position);
        holder.bind(friend, addFriendClickListener, deleteFriendClickListener, isMyFriendsMode);
    }
    
    @Override
    public int getItemCount() {
        return friends.size();
    }
    
    public void updateFriends(List<Friend> newFriends) {
        this.friends.clear();
        this.friends.addAll(newFriends);
        notifyDataSetChanged();
    }
    
    public static class FriendViewHolder extends RecyclerView.ViewHolder {
        private TextView nameTextView;
        private TextView usernameTextView;
        private TextView emailTextView;
        private ImageView profileImageView;
        private Button actionButton;
        
        public FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.friend_name_tv);
            usernameTextView = itemView.findViewById(R.id.friend_username_tv);
            emailTextView = itemView.findViewById(R.id.friend_email_tv);
            profileImageView = itemView.findViewById(R.id.friend_profile_iv);
            actionButton = itemView.findViewById(R.id.add_friend_btn);
        }
        
        public void bind(Friend friend, OnAddFriendClickListener addListener, OnDeleteFriendClickListener deleteListener, boolean isMyFriendsMode) {
            nameTextView.setText(friend.getFullName());
            usernameTextView.setText("@" + friend.getUsername());
            emailTextView.setText(friend.getEmail());
            
            profileImageView.setImageResource(android.R.drawable.ic_menu_myplaces);
            
            actionButton.setOnClickListener(null);
            
            if (isMyFriendsMode) {
                actionButton.setText("Remove");
                actionButton.setEnabled(true);
                actionButton.setOnClickListener(v -> {
                    if (deleteListener != null) {
                        deleteListener.onDeleteFriendClick(friend);
                    }
                });
            } else {
                if (friend.isFriend()) {
                    actionButton.setText("Friends");
                    actionButton.setEnabled(false);
                } else {
                    actionButton.setText("Add Friend");
                    actionButton.setEnabled(true);
                    actionButton.setOnClickListener(v -> {
                        if (addListener != null) {
                            addListener.onAddFriendClick(friend);
                        }
                    });
                }
            }
        }
    }
}
