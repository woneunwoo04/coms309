package com.example.occasio.organization;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import com.example.occasio.base.BaseOrganizationNavigationActivity;
import com.example.occasio.utils.ServerConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class EmailTemplateManagementActivity extends BaseOrganizationNavigationActivity {

    private RecyclerView templatesRecyclerView;
    private View templatesEmptyState;
    private TextView statusText;
    private Button createTemplateButton;
    private Button refreshButton;
    private Button backButton;
    private Button sendEmailButton;
    
    private EmailTemplateAdapter templateAdapter;
    private List<EmailTemplate> templates = new ArrayList<>();
    private static final String BASE_URL = ServerConfig.BASE_URL + "/api/email/templates";

    public static class EmailTemplate {
        private Long id;
        private String name;
        private String subject;
        private String body;
        private String description;

        public EmailTemplate(Long id, String name, String subject, String body, String description) {
            this.id = id;
            this.name = name;
            this.subject = subject;
            this.body = body;
            this.description = description;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public String getSubject() { return subject; }
        public String getBody() { return body; }
        public String getDescription() { return description; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_template_management);

        // Setup bottom navigation
        setupBottomNavigation();

        if (currentOrgName == null || currentOrgName.isEmpty()) {
            finish();
            return;
        }

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        
        loadTemplates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTemplates();
    }

    private void initializeViews() {
        templatesRecyclerView = findViewById(R.id.email_templates_recycler_view);
        templatesEmptyState = findViewById(R.id.email_templates_empty_state);
        statusText = findViewById(R.id.email_templates_status_tv);
        createTemplateButton = findViewById(R.id.email_templates_create_btn);
        refreshButton = findViewById(R.id.email_templates_refresh_btn);
        backButton = findViewById(R.id.email_templates_back_btn);
        sendEmailButton = findViewById(R.id.email_templates_send_email_btn);
    }

    private void setupRecyclerView() {
        templateAdapter = new EmailTemplateAdapter(templates, new EmailTemplateAdapter.TemplateActionListener() {
            @Override
            public void onEditClick(EmailTemplate template) {
                editTemplate(template);
            }

            @Override
            public void onDeleteClick(EmailTemplate template) {
                showDeleteConfirmation(template);
            }
        });
        
        templatesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        templatesRecyclerView.setAdapter(templateAdapter);
    }

    private void setupClickListeners() {
        createTemplateButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateEmailTemplateActivity.class);
            intent.putExtra("orgName", currentOrgName);
            startActivity(intent);
        });

        sendEmailButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SendEmailActivity.class);
            intent.putExtra("orgName", currentOrgName);
            startActivity(intent);
        });

        refreshButton.setOnClickListener(v -> {
            loadTemplates();
        });

        if (backButton != null) {
            backButton.setVisibility(android.view.View.GONE); // Hide back button since we have bottom nav
        }
    }

    private void loadTemplates() {
        if (currentOrgName == null || currentOrgName.isEmpty()) {
            statusText.setText("Error: Organization name not found");
            return;
        }
        
        statusText.setText("Loading templates...");
        android.util.Log.d("EmailTemplateManagement", "Loading templates from: " + BASE_URL);
        android.util.Log.d("EmailTemplateManagement", "Using username: " + currentOrgName);
        
        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                BASE_URL,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        runOnUiThread(() -> {
                            templates.clear();
                            try {
                                for (int i = 0; i < response.length(); i++) {
                                    JSONObject obj = response.getJSONObject(i);
                                    EmailTemplate template = new EmailTemplate(
                                            obj.getLong("id"),
                                            obj.getString("name"),
                                            obj.getString("subject"),
                                            obj.getString("body"),
                                            obj.optString("description", "")
                                    );
                                    templates.add(template);
                                }
                                templateAdapter.notifyDataSetChanged();
                                
                                if (templates.isEmpty()) {
                                    templatesRecyclerView.setVisibility(View.GONE);
                                    templatesEmptyState.setVisibility(View.VISIBLE);
                                    statusText.setText("No templates found. Create your first template!");
                                } else {
                                    templatesRecyclerView.setVisibility(View.VISIBLE);
                                    templatesEmptyState.setVisibility(View.GONE);
                                    statusText.setText("You have " + templates.size() + " template(s)");
                                }
                            } catch (JSONException e) {
                                android.util.Log.e("EmailTemplateManagement", "Error parsing templates: " + e.getMessage());
                            }
                        });
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        runOnUiThread(() -> {
                            String errorMessage = "Failed to load templates";
                            if (error.networkResponse != null) {
                                try {
                                    String responseBody = new String(error.networkResponse.data, java.nio.charset.StandardCharsets.UTF_8);
                                    android.util.Log.e("EmailTemplateManagement", "Error response body: " + responseBody);
                                    android.util.Log.e("EmailTemplateManagement", "Error status code: " + error.networkResponse.statusCode);
                                    
                                    try {
                                        JSONObject errorJson = new JSONObject(responseBody);
                                        errorMessage = errorJson.optString("error", errorJson.optString("message", errorMessage));
                                    } catch (JSONException e) {
                                        errorMessage = "HTTP " + error.networkResponse.statusCode + ": " + responseBody;
                                    }
                                } catch (Exception e) {
                                    errorMessage = "HTTP " + error.networkResponse.statusCode;
                                }
                            } else if (error.getMessage() != null) {
                                errorMessage = error.getMessage();
                            }
                            
                            android.util.Log.e("EmailTemplateManagement", "Error loading templates: " + errorMessage);
                            templatesRecyclerView.setVisibility(View.GONE);
                            templatesEmptyState.setVisibility(View.VISIBLE);
                            statusText.setText("Error: " + errorMessage);
                        });
                    }
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("username", currentOrgName);
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "application/json");
                android.util.Log.d("EmailTemplateManagement", "Request headers: username=" + currentOrgName);
                return headers;
            }
        };
        
        requestQueue.add(request);
    }

    private void editTemplate(EmailTemplate template) {
        Intent intent = new Intent(this, EditEmailTemplateActivity.class);
        intent.putExtra("templateId", template.getId());
        intent.putExtra("templateName", template.getName());
        intent.putExtra("templateSubject", template.getSubject());
        intent.putExtra("templateBody", template.getBody());
        intent.putExtra("templateDescription", template.getDescription());
        intent.putExtra("orgName", currentOrgName);
        startActivity(intent);
    }

    private void showDeleteConfirmation(EmailTemplate template) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Template")
                .setMessage("Are you sure you want to delete template \"" + template.getName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteTemplate(template))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteTemplate(EmailTemplate template) {
        String url = BASE_URL + "/" + template.getId();
        
        StringRequest request = new StringRequest(
                Request.Method.DELETE,
                url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        runOnUiThread(() -> {
                            loadTemplates();
                        });
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        runOnUiThread(() -> {
                            android.util.Log.e("EmailTemplateManagement", "Error deleting template: " + error.getMessage());
                            Toast.makeText(EmailTemplateManagementActivity.this, "Failed to delete template: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                }
        ) {
            @Override
            public java.util.Map<String, String> getHeaders() {
                java.util.Map<String, String> headers = new java.util.HashMap<>();
                headers.put("username", currentOrgName);
                return headers;
            }
        };
        
        requestQueue.add(request);
    }
}
