package com.example.occasio.notifications;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.occasio.R;
import com.example.occasio.model.Notification;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<Notification> notifications;
    private NotificationInboxActivity activity;
    private boolean isSelectionMode = false;
    private Set<Long> selectedNotifications = new HashSet<>();
    private OnSelectionChangeListener selectionChangeListener;

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int count);
    }

    public void setSelectionChangeListener(OnSelectionChangeListener listener) {
        this.selectionChangeListener = listener;
    }

    public NotificationAdapter(List<Notification> notifications, NotificationInboxActivity activity) {
        this.notifications = notifications;
        this.activity = activity;
    }

    public void setSelectionMode(boolean enabled) {
        isSelectionMode = enabled;
        if (!enabled) {
            selectedNotifications.clear();
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedNotifications.size());
        }
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public Set<Long> getSelectedNotifications() {
        return new HashSet<>(selectedNotifications);
    }

    public void selectAll() {
        selectedNotifications.clear();
        for (Notification notification : notifications) {
            selectedNotifications.add(notification.getId());
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedNotifications.size());
        }
    }

    public void clearSelection() {
        selectedNotifications.clear();
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(0);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Notification notification = notifications.get(position);
        
        // Set message
        holder.messageText.setText(notification.getMessage());
        
        // Set event name if available
        if (notification.getEventName() != null && !notification.getEventName().isEmpty()) {
            holder.eventNameText.setText(notification.getEventName());
            holder.eventContainer.setVisibility(View.VISIBLE);
        } else {
            holder.eventContainer.setVisibility(View.GONE);
        }

        // Set notification type badge
        String typeToDisplay = "NOTIFICATION";
        if (notification.getNotificationType() != null) {
            typeToDisplay = notification.getNotificationType()
                    .replace("_", " ")
                    .toUpperCase();
        }
        holder.typeBadge.setText(typeToDisplay);
        
        // Color code by type
        int bgColor;
        String typeUpper = typeToDisplay.toUpperCase();
        if (typeUpper.contains("EVENT") || typeUpper.contains("ATTENDANCE")) {
            bgColor = android.graphics.Color.parseColor("#FF8C42"); // Orange
        } else if (typeUpper.contains("CHAT") || typeUpper.contains("MESSAGE")) {
            bgColor = android.graphics.Color.parseColor("#2196F3"); // Blue
        } else if (typeUpper.contains("REWARD") || typeUpper.contains("POINT")) {
            bgColor = android.graphics.Color.parseColor("#FFD700"); // Gold
        } else if (typeUpper.contains("SOCIAL") || typeUpper.contains("FRIEND")) {
            bgColor = android.graphics.Color.parseColor("#9C27B0"); // Purple
        } else {
            bgColor = android.graphics.Color.parseColor("#757575"); // Gray
        }
        holder.typeBadge.setBackgroundColor(bgColor);

        // Set read status badge
        if (notification.isRead()) {
            holder.readStatusBadge.setText("READ");
            holder.readStatusBadge.setBackgroundResource(R.drawable.notification_status_badge_read);
            holder.itemView.setAlpha(0.7f);
        } else {
            holder.readStatusBadge.setText("NEW");
            holder.readStatusBadge.setBackgroundResource(R.drawable.notification_status_badge_new);
            holder.itemView.setAlpha(1.0f);
        }

        // Set timestamp (if available)
        if (notification.getCreatedAt() != null) {
            holder.timestampText.setText(formatTimestamp(notification.getCreatedAt()));
        } else {
            holder.timestampText.setText("Just now");
        }

        // Selection mode handling
        if (isSelectionMode) {
            holder.checkbox.setVisibility(View.VISIBLE);
            holder.checkbox.setChecked(selectedNotifications.contains(notification.getId()));
            
            holder.itemView.setOnClickListener(v -> {
                if (selectedNotifications.contains(notification.getId())) {
                    selectedNotifications.remove(notification.getId());
                } else {
                    selectedNotifications.add(notification.getId());
                }
                holder.checkbox.setChecked(selectedNotifications.contains(notification.getId()));
                updateCardSelection(holder.cardView, selectedNotifications.contains(notification.getId()));
                if (selectionChangeListener != null) {
                    selectionChangeListener.onSelectionChanged(selectedNotifications.size());
                }
            });
            holder.itemView.setOnLongClickListener(null); // Disable long press in selection mode
        } else {
            holder.checkbox.setVisibility(View.GONE);
            
            // Click to mark as read (if not already read)
            holder.itemView.setOnClickListener(v -> {
                if (notification != null && notification.getId() != null && !notification.isRead()) {
                    // Mark as read when clicked
                    activity.markNotificationAsRead(notification.getId());
                }
            });
            
            // Long press to enter selection mode
            holder.itemView.setOnLongClickListener(v -> {
                activity.enterSelectionMode();
                selectedNotifications.add(notification.getId());
                holder.checkbox.setChecked(true);
                updateCardSelection(holder.cardView, true);
                if (selectionChangeListener != null) {
                    selectionChangeListener.onSelectionChanged(selectedNotifications.size());
                }
                return true;
            });
            updateCardSelection(holder.cardView, false);
        }
    }

    private void updateCardSelection(CardView cardView, boolean selected) {
        if (selected) {
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"));
            cardView.setCardElevation(8f);
        } else {
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));
            cardView.setCardElevation(6f);
        }
    }

    private String formatTimestamp(String timestamp) {
        try {
            if (timestamp == null || timestamp.isEmpty()) {
                return "Just now";
            }
            // Try to parse ISO format
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timestamp);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.time.Duration duration = java.time.Duration.between(dateTime, now);
            
            long minutes = duration.toMinutes();
            if (minutes < 1) {
                return "Just now";
            } else if (minutes < 60) {
                return minutes + "m ago";
            } else if (minutes < 1440) {
                return (minutes / 60) + "h ago";
            } else {
                return (minutes / 1440) + "d ago";
            }
        } catch (Exception e) {
            return "Just now";
        }
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        CheckBox checkbox;
        TextView typeBadge;
        TextView readStatusBadge;
        TextView messageText;
        TextView eventNameText;
        View eventContainer;
        TextView timestampText;

        public ViewHolder(View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.notification_card);
            checkbox = itemView.findViewById(R.id.notification_checkbox);
            typeBadge = itemView.findViewById(R.id.notification_type_badge);
            readStatusBadge = itemView.findViewById(R.id.notification_read_status_badge);
            messageText = itemView.findViewById(R.id.notification_message);
            eventNameText = itemView.findViewById(R.id.notification_event_name);
            eventContainer = itemView.findViewById(R.id.notification_event_container);
            timestampText = itemView.findViewById(R.id.notification_timestamp);
        }
    }
}

