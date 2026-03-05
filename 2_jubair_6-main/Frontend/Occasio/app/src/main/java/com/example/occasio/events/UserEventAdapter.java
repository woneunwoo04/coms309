package com.example.occasio.events;
import com.example.occasio.R;
import com.example.occasio.model.Event;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class UserEventAdapter extends RecyclerView.Adapter<UserEventAdapter.UserEventViewHolder> {
    private List<Event> events;
    private OnEventClickListener eventClickListener;
    private OnUnregisterClickListener unregisterClickListener;
    private OnCheckInClickListener checkInClickListener;

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    public interface OnUnregisterClickListener {
        void onUnregisterClick(Event event);
    }

    public interface OnCheckInClickListener {
        void onCheckInClick(Event event);
    }

    public UserEventAdapter(List<Event> events, OnEventClickListener eventClickListener, OnUnregisterClickListener unregisterClickListener, OnCheckInClickListener checkInClickListener) {
        this.events = events;
        this.eventClickListener = eventClickListener;
        this.unregisterClickListener = unregisterClickListener;
        this.checkInClickListener = checkInClickListener;
    }

    @NonNull
    @Override
    public UserEventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_event, parent, false);
        return new UserEventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserEventViewHolder holder, int position) {
        Event event = events.get(position);
        holder.bind(event, eventClickListener, unregisterClickListener, checkInClickListener);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    class UserEventViewHolder extends RecyclerView.ViewHolder {
        private TextView titleTextView;
        private TextView descriptionTextView;
        private TextView locationTextView;
        private TextView startTimeTextView;
        private TextView endTimeTextView;
        private TextView eventTypeTextView;
        private TextView organizerTextView;
        private Button attendButton;
        private Button checkInButton;

        public UserEventViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.item_user_event_title_tv);
            descriptionTextView = itemView.findViewById(R.id.item_user_event_description_tv);
            locationTextView = itemView.findViewById(R.id.item_user_event_location_tv);
            startTimeTextView = itemView.findViewById(R.id.item_user_event_start_time_tv);
            endTimeTextView = itemView.findViewById(R.id.item_user_event_end_time_tv);
            eventTypeTextView = itemView.findViewById(R.id.item_user_event_type_tv);
            organizerTextView = itemView.findViewById(R.id.item_user_event_organizer_tv);
            attendButton = itemView.findViewById(R.id.item_user_event_attend_btn);
            checkInButton = itemView.findViewById(R.id.item_user_event_checkin_btn);
        }

        public void bind(Event event, OnEventClickListener eventClickListener, OnUnregisterClickListener unregisterClickListener, OnCheckInClickListener checkInClickListener) {
            titleTextView.setText(event.getTitle());
            descriptionTextView.setText(event.getDescription());
            locationTextView.setText("📍 " + event.getLocation());
            startTimeTextView.setText("🕐 Start: " + event.getStartTime());
            endTimeTextView.setText("🕐 End: " + event.getEndTime());
            eventTypeTextView.setText("📋 Type: " + event.getEventType());
            organizerTextView.setText("🏢 Organizer: " + event.getOrganizerName());

            // Always show check-in button, but enable/disable based on active session
            checkInButton.setVisibility(View.VISIBLE);
            if (event.hasActiveSession()) {
                checkInButton.setEnabled(true);
                checkInButton.setAlpha(1.0f);
                checkInButton.setText("✅ Check In");
                checkInButton.setOnClickListener(v -> {
                    if (checkInClickListener != null) {
                        checkInClickListener.onCheckInClick(event);
                    }
                });
            } else {
                checkInButton.setEnabled(false);
                checkInButton.setAlpha(0.5f);
                checkInButton.setText("⏳ Check In (Waiting for session)");
                checkInButton.setOnClickListener(null);
            }

            attendButton.setText("❌ Unregister");
            attendButton.setOnClickListener(v -> {
                if (unregisterClickListener != null) {
                    unregisterClickListener.onUnregisterClick(event);
                }
            });

            // Click on event to view details
            itemView.setOnClickListener(v -> {
                if (eventClickListener != null) {
                    eventClickListener.onEventClick(event);
                }
            });
        }
    }
}
