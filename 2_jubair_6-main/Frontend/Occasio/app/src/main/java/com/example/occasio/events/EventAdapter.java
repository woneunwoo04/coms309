package com.example.occasio.events;
import com.example.occasio.R;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {
    private List<ManageEventsActivity.Event> events;
    private OnEventClickListener clickListener;
    private OnEventDeleteListener deleteListener;
    private OnStartAttendanceListener startAttendanceListener;
    private OnViewAttendanceListener viewAttendanceListener;
    private OnViewFeedbackListener viewFeedbackListener;

    public interface OnEventClickListener {
        void onEventClick(ManageEventsActivity.Event event);
    }

    public interface OnEventDeleteListener {
        void onEventDelete(ManageEventsActivity.Event event);
    }
    
    public interface OnStartAttendanceListener {
        void onStartAttendance(ManageEventsActivity.Event event);
    }
    
    public interface OnViewAttendanceListener {
        void onViewAttendance(ManageEventsActivity.Event event);
    }
    
    public interface OnViewFeedbackListener {
        void onViewFeedback(ManageEventsActivity.Event event);
    }

    public EventAdapter(List<ManageEventsActivity.Event> events, OnEventClickListener clickListener, OnEventDeleteListener deleteListener, OnStartAttendanceListener startAttendanceListener, OnViewAttendanceListener viewAttendanceListener, OnViewFeedbackListener viewFeedbackListener) {
        this.events = events;
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
        this.startAttendanceListener = startAttendanceListener;
        this.viewAttendanceListener = viewAttendanceListener;
        this.viewFeedbackListener = viewFeedbackListener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        ManageEventsActivity.Event event = events.get(position);
        holder.bind(event);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    class EventViewHolder extends RecyclerView.ViewHolder {
        private TextView titleTextView;
        private TextView descriptionTextView;
        private TextView locationTextView;
        private TextView startTimeTextView;
        private TextView endTimeTextView;
        private TextView eventTypeTextView;
        private Button editButton;
        private Button deleteButton;
        private Button startAttendanceButton;
        private Button viewAttendanceButton;
        private Button viewFeedbackButton;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.item_event_title_tv);
            descriptionTextView = itemView.findViewById(R.id.item_event_description_tv);
            locationTextView = itemView.findViewById(R.id.item_event_location_tv);
            startTimeTextView = itemView.findViewById(R.id.item_event_start_time_tv);
            endTimeTextView = itemView.findViewById(R.id.item_event_end_time_tv);
            eventTypeTextView = itemView.findViewById(R.id.item_event_type_tv);
            editButton = itemView.findViewById(R.id.item_event_edit_btn);
            deleteButton = itemView.findViewById(R.id.item_event_delete_btn);
            startAttendanceButton = itemView.findViewById(R.id.item_event_start_attendance_btn);
            viewAttendanceButton = itemView.findViewById(R.id.item_event_view_attendance_btn);
            viewFeedbackButton = itemView.findViewById(R.id.item_event_view_feedback_btn);
        }

        public void bind(ManageEventsActivity.Event event) {
            if (event == null) {
                android.util.Log.e("EventAdapter", "Event is null in bind!");
                return;
            }
            
            titleTextView.setText(event.getTitle());
            descriptionTextView.setText(event.getDescription());
            locationTextView.setText("📍 " + event.getLocation());
            startTimeTextView.setText("🕐 Start: " + event.getStartTime());
            endTimeTextView.setText("🕐 End: " + event.getEndTime());
            eventTypeTextView.setText("📋 Type: " + event.getEventType());
            
            android.util.Log.d("EventAdapter", "Binding event: " + event.getTitle() + " (ID: " + event.getId() + ")");

            editButton.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onEventClick(event);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onEventDelete(event);
                }
            });
            
            if (startAttendanceButton != null) {
                // Update button text based on active session status
                if (event.hasActiveSession()) {
                    startAttendanceButton.setText("✅ Active Session");
                } else {
                    startAttendanceButton.setText("📋 Start Attendance");
                }
                
                startAttendanceButton.setOnClickListener(v -> {
                    if (startAttendanceListener != null) {
                        startAttendanceListener.onStartAttendance(event);
                    }
                });
            }
            
            if (viewAttendanceButton != null) {
                viewAttendanceButton.setOnClickListener(v -> {
                    if (viewAttendanceListener != null) {
                        viewAttendanceListener.onViewAttendance(event);
                    }
                });
            }
            
            if (viewFeedbackButton != null) {
                viewFeedbackButton.setVisibility(View.VISIBLE);
                viewFeedbackButton.setOnClickListener(v -> {
                    android.util.Log.d("EventAdapter", "View Feedback button clicked for event: " + (event != null ? event.getTitle() : "null"));
                    if (viewFeedbackListener != null && event != null) {
                        try {
                            viewFeedbackListener.onViewFeedback(event);
                        } catch (Exception e) {
                            android.util.Log.e("EventAdapter", "Error in viewFeedbackListener: " + e.getMessage(), e);
                        }
                    } else {
                        android.util.Log.e("EventAdapter", "viewFeedbackListener is null or event is null!");
                    }
                });
            } else {
                android.util.Log.e("EventAdapter", "View Feedback button is null! Button ID: item_event_view_feedback_btn");
            }
        }
    }
}
