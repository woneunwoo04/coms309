package com.example.occasio.feedback;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import java.util.List;

public class FeedbackAdapter extends RecyclerView.Adapter<FeedbackAdapter.FeedbackViewHolder> {
    
    private List<MyFeedbacksActivity.FeedbackItem> feedbacks;
    private OnFeedbackClickListener clickListener;
    private OnFeedbackDeleteListener deleteListener;
    
    public interface OnFeedbackClickListener {
        void onFeedbackClick(MyFeedbacksActivity.FeedbackItem feedback);
    }
    
    public interface OnFeedbackDeleteListener {
        void onFeedbackDelete(MyFeedbacksActivity.FeedbackItem feedback);
    }
    
    public FeedbackAdapter(List<MyFeedbacksActivity.FeedbackItem> feedbacks, 
                           OnFeedbackClickListener clickListener,
                           OnFeedbackDeleteListener deleteListener) {
        this.feedbacks = feedbacks;
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }
    
    @NonNull
    @Override
    public FeedbackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feedback, parent, false);
        return new FeedbackViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull FeedbackViewHolder holder, int position) {
        MyFeedbacksActivity.FeedbackItem feedback = feedbacks.get(position);
        holder.bind(feedback, clickListener, deleteListener);
    }
    
    @Override
    public int getItemCount() {
        return feedbacks.size();
    }
    
    public static class FeedbackViewHolder extends RecyclerView.ViewHolder {
        private TextView eventNameTextView;
        private RatingBar ratingBar;
        private TextView commentTextView;
        private TextView dateTextView;
        private Button editButton;
        private Button deleteButton;
        
        public FeedbackViewHolder(@NonNull View itemView) {
            super(itemView);
            eventNameTextView = itemView.findViewById(R.id.feedback_item_event_name_tv);
            ratingBar = itemView.findViewById(R.id.feedback_item_rating_bar);
            commentTextView = itemView.findViewById(R.id.feedback_item_comment_tv);
            dateTextView = itemView.findViewById(R.id.feedback_item_date_tv);
            editButton = itemView.findViewById(R.id.feedback_item_edit_btn);
            deleteButton = itemView.findViewById(R.id.feedback_item_delete_btn);
        }
        
        public void bind(MyFeedbacksActivity.FeedbackItem feedback, 
                        OnFeedbackClickListener clickListener,
                        OnFeedbackDeleteListener deleteListener) {
            if (feedback == null) {
                return;
            }
            
            if (eventNameTextView != null) {
                eventNameTextView.setText(feedback.getEventName() != null ? feedback.getEventName() : "Unknown Event");
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
            
            if (editButton != null) {
            editButton.setOnClickListener(v -> {
                    if (clickListener != null && feedback != null) {
                    clickListener.onFeedbackClick(feedback);
                }
            });
            }
            
            if (deleteButton != null) {
            deleteButton.setOnClickListener(v -> {
                    if (deleteListener != null && feedback != null) {
                    deleteListener.onFeedbackDelete(feedback);
                }
            });
            }
        }
    }
}

