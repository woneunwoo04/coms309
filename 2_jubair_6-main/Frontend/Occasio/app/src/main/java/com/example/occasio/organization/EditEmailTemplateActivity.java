package com.example.occasio.organization;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import com.example.occasio.utils.ServerConfig;
import org.json.JSONException;
import org.json.JSONObject;

public class EditEmailTemplateActivity extends AppCompatActivity {

    private TextView nameTextView;
    private EditText subjectEditText;
    private EditText bodyEditText;
    private EditText descriptionEditText;
    private Button saveButton;
    private Button cancelButton;
    
    private RequestQueue requestQueue;
    private String organizationUsername;
    private Long templateId;
    private String templateName;
    private static final String BASE_URL = ServerConfig.BASE_URL + "/api/email/templates";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_email_template);

        organizationUsername = getIntent().getStringExtra("orgName");
        templateId = getIntent().getLongExtra("templateId", -1);
        templateName = getIntent().getStringExtra("templateName");
        
        if (organizationUsername == null || organizationUsername.isEmpty() || templateId == -1) {
            finish();
            return;
        }

        initializeViews();
        loadTemplateData();
        setupClickListeners();
        
        requestQueue = Volley.newRequestQueue(this);
    }

    private void initializeViews() {
        nameTextView = findViewById(R.id.edit_template_name_tv);
        subjectEditText = findViewById(R.id.edit_template_subject_et);
        bodyEditText = findViewById(R.id.edit_template_body_et);
        descriptionEditText = findViewById(R.id.edit_template_description_et);
        saveButton = findViewById(R.id.edit_template_save_btn);
        cancelButton = findViewById(R.id.edit_template_cancel_btn);
    }

    private void loadTemplateData() {
        nameTextView.setText(templateName != null ? templateName : "Template");
        subjectEditText.setText(getIntent().getStringExtra("templateSubject"));
        
        // Extract plain text from HTML body for editing (remove HTML tags)
        String bodyHtml = getIntent().getStringExtra("templateBody");
        if (bodyHtml != null && !bodyHtml.trim().isEmpty()) {
            // Remove HTML tags to show plain text for editing
            String plainText = bodyHtml.replaceAll("<[^>]+>", "").trim();
            // If removing tags left nothing, use original (might be plain text already)
            if (plainText.isEmpty()) {
                plainText = bodyHtml.trim();
            }
            bodyEditText.setText(plainText);
        }
        
        descriptionEditText.setText(getIntent().getStringExtra("templateDescription"));
    }

    private void setupClickListeners() {
        saveButton.setOnClickListener(v -> updateTemplate());
        cancelButton.setOnClickListener(v -> finish());
    }

    private void updateTemplate() {
        String subject = subjectEditText.getText().toString().trim();
        String body = bodyEditText.getText().toString().trim();
        String description = descriptionEditText.getText().toString().trim();

        if (subject.isEmpty()) {
            return;
        }
        if (body.isEmpty()) {
            return;
        }

        // Automatically apply pink & cute styling to the body
        String styledBody = com.example.occasio.utils.EmailStylingHelper.applyPinkCuteStyling(body, subject);

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("subject", subject);
            requestBody.put("body", styledBody);
            requestBody.put("description", description);

            String url = BASE_URL + "/" + templateId;
            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.PUT,
                    url,
                    requestBody,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            finish();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            android.util.Log.e("EditEmailTemplate", "Error updating template: " + error.getMessage());
                            Toast.makeText(EditEmailTemplateActivity.this, "Failed to update template: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
            ) {
                @Override
                public java.util.Map<String, String> getHeaders() {
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("username", organizationUsername);
                    return headers;
                }
            };

            requestQueue.add(request);
        } catch (JSONException e) {
            android.util.Log.e("EditEmailTemplate", "Error creating JSON: " + e.getMessage());
        }
    }
}
