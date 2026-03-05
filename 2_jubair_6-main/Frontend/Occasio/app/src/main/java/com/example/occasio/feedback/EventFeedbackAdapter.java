package com.example.occasio.feedback;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import java.util.List;

public class EventFeedbackAdapter extends RecyclerView.Adapter<EventFeedbackAdapter.FeedbackViewHolder> {
    
    private List<ViewEventFeedbacksActivity.EventFeedbackItem> feedbacks;
    
    public EventFeedbackAdapter(List<ViewEventFeedbacksActivity.EventFeedbackItem> feedbacks) {
        this.feedbacks = feedbacks;
    }
    
    @NonNull
    @Override
    public FeedbackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_feedback, parent, false);
        return new FeedbackViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull FeedbackViewHolder holder, int position) {
        ViewEventFeedbacksActivity.EventFeedbackItem feedback = feedbacks.get(position);
        holder.bind(feedback);
    }
    
    @Override
    public int getItemCount() {
        return feedbacks.size();
    }
    
    public static class FeedbackViewHolder extends RecyclerView.ViewHolder {
        private TextView usernameTextView;
        private RatingBar ratingBar;
        private TextView commentTextView;
        private TextView dateTextView;
        
        public FeedbackViewHolder(@NonNull View itemView) {
            super(itemView);
            usernameTextView = itemView.findViewById(R.id.event_feedback_item_username_tv);
            ratingBar = itemView.findViewById(R.id.event_feedback_item_rating_bar);
            commentTextView = itemView.findViewById(R.id.event_feedback_item_comment_tv);
            dateTextView = itemView.findViewById(R.id.event_feedback_item_date_tv);
        }
        
        public void bind(ViewEventFeedbacksActivity.EventFeedbackItem feedback) {
            if (feedback == null) {
                return;
            }
            
            if (usernameTextView != null) {
                String username = feedback.getUsername() != null ? feedback.getUsername() : "Unknown User";
                usernameTextView.setText("@" + username);
            }
            
            if (ratingBar != null) {
            ratingBar.setRating(feedback.getRating());
            }
            
            if (commentTextView != null) {
            if (feedback.getComment() != null && !feedback.getComment().isEmpty()) {
                commentTextView.setText(feedback.getComment());
                commentTextView.setVisibility(View.VISIBLE);
            } else {
                commentTextView.setVisibility(View.GONE);
                }
            }
            
            if (dateTextView != null) {
                String dateText = feedback.getCreatedAt() != null ? feedback.getCreatedAt() : "Unknown date";
                dateTextView.setText("Submitted: " + dateText);
            }
        }
    }
}

