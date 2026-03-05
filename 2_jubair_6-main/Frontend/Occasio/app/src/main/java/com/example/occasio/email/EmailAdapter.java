package com.example.occasio.email;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmailAdapter extends RecyclerView.Adapter<EmailAdapter.EmailViewHolder> {

    private List<EmailInboxActivity.EmailItem> emails;
    private EmailClickListener listener;

    public interface EmailClickListener {
        void onEmailClick(EmailInboxActivity.EmailItem email);
    }

    public EmailAdapter(List<EmailInboxActivity.EmailItem> emails, EmailClickListener listener) {
        this.emails = emails != null ? emails : new java.util.ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public EmailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_email, parent, false);
        return new EmailViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EmailViewHolder holder, int position) {
        if (emails == null || position < 0 || position >= emails.size()) {
            return;
        }
        EmailInboxActivity.EmailItem email = emails.get(position);
        holder.bind(email, listener);
    }

    @Override
    public int getItemCount() {
        return emails == null ? 0 : emails.size();
    }

    static class EmailViewHolder extends RecyclerView.ViewHolder {
        private CardView cardView;
        private TextView subjectText;
        private TextView sentAtText;
        private TextView templateNameText;
        private View unreadIndicator;

        public EmailViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.item_email_card);
            subjectText = itemView.findViewById(R.id.item_email_subject);
            sentAtText = itemView.findViewById(R.id.item_email_sent_at);
            templateNameText = itemView.findViewById(R.id.item_email_template_name);
            unreadIndicator = itemView.findViewById(R.id.item_email_unread_indicator);
        }

        public void bind(EmailInboxActivity.EmailItem email, EmailClickListener listener) {
            if (email == null) return;
            
            if (subjectText != null) {
                subjectText.setText(email.getSubject() != null ? email.getSubject() : "No Subject");
            }
            
            // Format sent date
            if (sentAtText != null) {
                String formattedDate = formatDate(email.getSentAt());
                sentAtText.setText(formattedDate);
            }
            
            // Show template name if available
            if (templateNameText != null) {
                if (email.getTemplateName() != null && !email.getTemplateName().isEmpty()) {
                    templateNameText.setText("Template: " + email.getTemplateName());
                    templateNameText.setVisibility(View.VISIBLE);
                } else {
                    templateNameText.setVisibility(View.GONE);
                }
            }
            
            // Show unread indicator
            if (unreadIndicator != null) {
                unreadIndicator.setVisibility(email.isReadFlag() ? View.GONE : View.VISIBLE);
            }
            
            // Set card alpha based on read status
            if (cardView != null) {
                cardView.setAlpha(email.isReadFlag() ? 0.7f : 1.0f);
            }
            
            // Set click listener
            if (cardView != null && listener != null) {
                cardView.setOnClickListener(v -> listener.onEmailClick(email));
            }
        }

        private String formatDate(String dateString) {
            if (dateString == null || dateString.isEmpty()) {
                return "Unknown date";
            }
            
            try {
                // Try to parse ISO format
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                Date date = inputFormat.parse(dateString);
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
                return outputFormat.format(date);
            } catch (ParseException e) {
                // If parsing fails, return original string
                return dateString;
            }
        }
    }
}
