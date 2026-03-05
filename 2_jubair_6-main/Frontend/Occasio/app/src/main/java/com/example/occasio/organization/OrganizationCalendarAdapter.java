package com.example.occasio.organization;

import com.example.occasio.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OrganizationCalendarAdapter extends RecyclerView.Adapter<OrganizationCalendarAdapter.OrganizationEventViewHolder> {
    private List<OrganizationCalendarActivity.OrganizationEvent> events;

    public OrganizationCalendarAdapter(List<OrganizationCalendarActivity.OrganizationEvent> events) {
        this.events = events;
    }

    @NonNull
    @Override
    public OrganizationEventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_event, parent, false);
        return new OrganizationEventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrganizationEventViewHolder holder, int position) {
        OrganizationCalendarActivity.OrganizationEvent event = events.get(position);
        holder.bind(event);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    class OrganizationEventViewHolder extends RecyclerView.ViewHolder {
        private TextView titleTextView;
        private TextView timeTextView;
        private TextView dateTextView;
        private TextView locationTextView;
        private TextView descriptionTextView;
        private TextView notesTextView;
        private TextView reminderTextView;
        private View editButton;
        private View deleteButton;

        public OrganizationEventViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.calendar_event_title_tv);
            timeTextView = itemView.findViewById(R.id.calendar_event_time_tv);
            dateTextView = itemView.findViewById(R.id.calendar_event_date_tv);
            locationTextView = itemView.findViewById(R.id.calendar_event_location_tv);
            descriptionTextView = itemView.findViewById(R.id.calendar_event_description_tv);
            notesTextView = itemView.findViewById(R.id.calendar_event_notes_tv);
            reminderTextView = itemView.findViewById(R.id.calendar_event_reminder_tv);
            editButton = itemView.findViewById(R.id.calendar_event_edit_btn);
            deleteButton = itemView.findViewById(R.id.calendar_event_delete_btn);
            
            // Hide edit and delete buttons for organization view
            if (editButton != null) editButton.setVisibility(View.GONE);
            if (deleteButton != null) deleteButton.setVisibility(View.GONE);
        }

        public void bind(OrganizationCalendarActivity.OrganizationEvent event) {
            titleTextView.setText(event.getTitle());
            
            // Format time
            String timeStr = formatTime(event.getStartTime());
            timeTextView.setText(timeStr);
            
            // Format date
            String dateStr = formatDate(event.getStartTime());
            dateTextView.setText(dateStr);
            
            // Location
            if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                locationTextView.setText("📍 " + event.getLocation());
                locationTextView.setVisibility(View.VISIBLE);
            } else {
                locationTextView.setVisibility(View.GONE);
            }
            
            // Description
            if (event.getDescription() != null && !event.getDescription().isEmpty()) {
                descriptionTextView.setText(event.getDescription());
                descriptionTextView.setVisibility(View.VISIBLE);
            } else {
                descriptionTextView.setVisibility(View.GONE);
            }
            
            // Hide notes and reminder for organization view
            notesTextView.setVisibility(View.GONE);
            reminderTextView.setVisibility(View.GONE);
        }

        private String formatTime(String dateTimeStr) {
            if (dateTimeStr == null || dateTimeStr.isEmpty()) {
                return "";
            }
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("h:mm a", Locale.US);
                Date date = inputFormat.parse(dateTimeStr);
                return outputFormat.format(date);
            } catch (ParseException e) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
                    SimpleDateFormat outputFormat = new SimpleDateFormat("h:mm a", Locale.US);
                    Date date = inputFormat.parse(dateTimeStr);
                    return outputFormat.format(date);
                } catch (ParseException e2) {
                    return dateTimeStr;
                }
            }
        }

        private String formatDate(String dateTimeStr) {
            if (dateTimeStr == null || dateTimeStr.isEmpty()) {
                return "";
            }
            try {
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                Date date = inputFormat.parse(dateTimeStr);
                return outputFormat.format(date);
            } catch (ParseException e) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
                    SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                    Date date = inputFormat.parse(dateTimeStr);
                    return outputFormat.format(date);
                } catch (ParseException e2) {
                    return dateTimeStr;
                }
            }
        }
    }
}

