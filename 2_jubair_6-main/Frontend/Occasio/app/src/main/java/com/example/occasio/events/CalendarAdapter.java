package com.example.occasio.events;

import com.example.occasio.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.CalendarEventViewHolder> {
    private List<CalendarActivity.CalendarEvent> calendarEvents;
    private OnEventEditListener editListener;
    private OnEventDeleteListener deleteListener;

    public interface OnEventEditListener {
        void onEventEdit(CalendarActivity.CalendarEvent event);
    }

    public interface OnEventDeleteListener {
        void onEventDelete(CalendarActivity.CalendarEvent event);
    }

    public CalendarAdapter(List<CalendarActivity.CalendarEvent> calendarEvents, 
                          OnEventEditListener editListener, 
                          OnEventDeleteListener deleteListener) {
        this.calendarEvents = calendarEvents;
        this.editListener = editListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public CalendarEventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_event, parent, false);
        return new CalendarEventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CalendarEventViewHolder holder, int position) {
        CalendarActivity.CalendarEvent event = calendarEvents.get(position);
        holder.bind(event);
    }

    @Override
    public int getItemCount() {
        return calendarEvents.size();
    }

    class CalendarEventViewHolder extends RecyclerView.ViewHolder {
        private TextView titleTextView;
        private TextView timeTextView;
        private TextView dateTextView;
        private TextView locationTextView;
        private TextView organizationTextView;
        private TextView descriptionTextView;
        private TextView notesTextView;
        private TextView reminderTextView;
        private Button editButton;
        private Button deleteButton;

        public CalendarEventViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.calendar_event_title_tv);
            timeTextView = itemView.findViewById(R.id.calendar_event_time_tv);
            dateTextView = itemView.findViewById(R.id.calendar_event_date_tv);
            locationTextView = itemView.findViewById(R.id.calendar_event_location_tv);
            organizationTextView = itemView.findViewById(R.id.calendar_event_organization_tv);
            descriptionTextView = itemView.findViewById(R.id.calendar_event_description_tv);
            notesTextView = itemView.findViewById(R.id.calendar_event_notes_tv);
            reminderTextView = itemView.findViewById(R.id.calendar_event_reminder_tv);
            editButton = itemView.findViewById(R.id.calendar_event_edit_btn);
            deleteButton = itemView.findViewById(R.id.calendar_event_delete_btn);
        }

        public void bind(CalendarActivity.CalendarEvent event) {
            // Title
            String title = event.getTitle();
            if (title == null || title.isEmpty()) {
                title = "Untitled Event";
            }
            titleTextView.setText(title);
            
            // Format time
            String timeStr = formatTime(event.getStartTime());
            if (timeStr.isEmpty()) {
                timeStr = "Time TBD";
            }
            timeTextView.setText(timeStr);
            
            // Format date
            String dateStr = formatDate(event.getStartTime());
            if (dateStr.isEmpty()) {
                dateStr = "Date TBD";
            }
            dateTextView.setText(dateStr);
            
            // Location
            if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                locationTextView.setText("📍 " + event.getLocation());
                locationTextView.setVisibility(View.VISIBLE);
            } else {
                locationTextView.setVisibility(View.GONE);
            }
            
            // Organization
            if (event.getOrganizationName() != null && !event.getOrganizationName().isEmpty() 
                    && !event.getOrganizationName().equals("Unknown Organization")) {
                organizationTextView.setText("🏢 " + event.getOrganizationName());
                organizationTextView.setVisibility(View.VISIBLE);
            } else {
                organizationTextView.setVisibility(View.GONE);
            }
            
            // Description
            if (event.getDescription() != null && !event.getDescription().isEmpty()) {
                descriptionTextView.setText(event.getDescription());
                descriptionTextView.setVisibility(View.VISIBLE);
            } else {
                descriptionTextView.setVisibility(View.GONE);
            }
            
            // Personal notes
            if (event.getPersonalNotes() != null && !event.getPersonalNotes().isEmpty()) {
                notesTextView.setText("📝 " + event.getPersonalNotes());
                notesTextView.setVisibility(View.VISIBLE);
            } else {
                notesTextView.setVisibility(View.GONE);
            }
            
            // Reminder
            if (event.isReminderEnabled()) {
                reminderTextView.setText("⏰ Reminder: " + event.getReminderMinutes() + " min before");
                reminderTextView.setVisibility(View.VISIBLE);
            } else {
                reminderTextView.setVisibility(View.GONE);
            }

            editButton.setOnClickListener(v -> {
                if (editListener != null) {
                    editListener.onEventEdit(event);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onEventDelete(event);
                }
            });
        }

        private String formatTime(String dateTimeStr) {
            if (dateTimeStr == null || dateTimeStr.isEmpty()) {
                return "";
            }
            try {
                // Parse ISO 8601 format: "2025-01-15T10:00:00"
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("h:mm a", Locale.US);
                Date date = inputFormat.parse(dateTimeStr);
                return outputFormat.format(date);
            } catch (ParseException e) {
                // Try alternative format
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
                    SimpleDateFormat outputFormat = new SimpleDateFormat("h:mm a", Locale.US);
                    Date date = inputFormat.parse(dateTimeStr);
                    return outputFormat.format(date);
                } catch (ParseException e2) {
                    return dateTimeStr; // Return original if parsing fails
                }
            }
        }

        private String formatDate(String dateTimeStr) {
            if (dateTimeStr == null || dateTimeStr.isEmpty()) {
                return "";
            }
            try {
                // Parse ISO 8601 format
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                Date date = inputFormat.parse(dateTimeStr);
                return outputFormat.format(date);
            } catch (ParseException e) {
                // Try alternative format
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
                    SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                    Date date = inputFormat.parse(dateTimeStr);
                    return outputFormat.format(date);
                } catch (ParseException e2) {
                    return dateTimeStr; // Return original if parsing fails
                }
            }
        }
    }
}

