package com.example.occasio.events;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import java.util.List;

public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder> {
    private List<AttendanceActivity.AttendanceRecord> attendanceRecords;

    public AttendanceAdapter(List<AttendanceActivity.AttendanceRecord> attendanceRecords) {
        this.attendanceRecords = attendanceRecords;
    }

    @NonNull
    @Override
    public AttendanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance, parent, false);
        return new AttendanceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttendanceViewHolder holder, int position) {
        AttendanceActivity.AttendanceRecord record = attendanceRecords.get(position);
        holder.bind(record);
    }

    @Override
    public int getItemCount() {
        return attendanceRecords.size();
    }

    class AttendanceViewHolder extends RecyclerView.ViewHolder {
        private TextView eventTitleTextView;
        private TextView checkInTimeTextView;
        private TextView checkInMethodTextView;
        private TextView statusTextView;
        private TextView pointsTextView;

        public AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);
            eventTitleTextView = itemView.findViewById(R.id.item_attendance_event_title_tv);
            checkInTimeTextView = itemView.findViewById(R.id.item_attendance_checkin_time_tv);
            checkInMethodTextView = itemView.findViewById(R.id.item_attendance_checkin_method_tv);
            statusTextView = itemView.findViewById(R.id.item_attendance_status_tv);
            pointsTextView = itemView.findViewById(R.id.item_attendance_points_tv);
        }

        public void bind(AttendanceActivity.AttendanceRecord record) {
            eventTitleTextView.setText("📅 " + record.getEventTitle());
            checkInTimeTextView.setText("🕐 Check-in: " + record.getCheckInTime());
            checkInMethodTextView.setText("📍 Method: " + record.getCheckInMethod());
            statusTextView.setText("✅ Status: " + record.getStatus());
            pointsTextView.setText("⭐ Points: " + record.getPointsEarned());
        }
    }
}
