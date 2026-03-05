package com.example.occasio.organization;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;

import java.util.List;

public class EmailTemplateAdapter extends RecyclerView.Adapter<EmailTemplateAdapter.TemplateViewHolder> {

    private List<EmailTemplateManagementActivity.EmailTemplate> templates;
    private TemplateActionListener listener;

    public interface TemplateActionListener {
        void onEditClick(EmailTemplateManagementActivity.EmailTemplate template);
        void onDeleteClick(EmailTemplateManagementActivity.EmailTemplate template);
    }

    public EmailTemplateAdapter(List<EmailTemplateManagementActivity.EmailTemplate> templates, TemplateActionListener listener) {
        this.templates = templates;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TemplateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_email_template, parent, false);
        return new TemplateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TemplateViewHolder holder, int position) {
        EmailTemplateManagementActivity.EmailTemplate template = templates.get(position);
        holder.bind(template, listener);
    }

    @Override
    public int getItemCount() {
        return templates.size();
    }

    static class TemplateViewHolder extends RecyclerView.ViewHolder {
        private TextView nameText;
        private TextView subjectText;
        private TextView descriptionText;
        private Button editButton;
        private Button deleteButton;

        public TemplateViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.item_template_name);
            subjectText = itemView.findViewById(R.id.item_template_subject);
            descriptionText = itemView.findViewById(R.id.item_template_description);
            editButton = itemView.findViewById(R.id.item_template_edit_btn);
            deleteButton = itemView.findViewById(R.id.item_template_delete_btn);
        }

        public void bind(EmailTemplateManagementActivity.EmailTemplate template, TemplateActionListener listener) {
            if (template == null) return;
            
            nameText.setText(template.getName() != null ? template.getName() : "Unnamed Template");
            subjectText.setText(template.getSubject() != null ? template.getSubject() : "No subject");
            descriptionText.setText(template.getDescription() != null && !template.getDescription().isEmpty() 
                    ? template.getDescription() : "No description");
            
            if (editButton != null) {
                editButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onEditClick(template);
                    }
                });
            }
            
            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onDeleteClick(template);
                    }
                });
            }
        }
    }
}
