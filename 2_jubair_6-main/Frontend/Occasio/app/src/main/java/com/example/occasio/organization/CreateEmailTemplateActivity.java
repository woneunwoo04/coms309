package com.example.occasio.organization;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
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

public class CreateEmailTemplateActivity extends AppCompatActivity {

    private EditText nameEditText;
    private EditText subjectEditText;
    private EditText bodyEditText;
    private EditText descriptionEditText;
    private Button saveButton;
    private Button cancelButton;
    
    private RequestQueue requestQueue;
    private String organizationUsername;
    private static final String BASE_URL = ServerConfig.BASE_URL + "/api/email/templates";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_email_template);

        organizationUsername = getIntent().getStringExtra("orgName");
        if (organizationUsername == null || organizationUsername.isEmpty()) {
            finish();
            return;
        }

        initializeViews();
        setupClickListeners();
        
        requestQueue = Volley.newRequestQueue(this);
    }

    private void initializeViews() {
        nameEditText = findViewById(R.id.create_template_name_et);
        subjectEditText = findViewById(R.id.create_template_subject_et);
        bodyEditText = findViewById(R.id.create_template_body_et);
        descriptionEditText = findViewById(R.id.create_template_description_et);
        saveButton = findViewById(R.id.create_template_save_btn);
        cancelButton = findViewById(R.id.create_template_cancel_btn);
    }

    private void setupClickListeners() {
        saveButton.setOnClickListener(v -> saveTemplate());
        cancelButton.setOnClickListener(v -> finish());
    }

    private void saveTemplate() {
        String name = nameEditText.getText().toString().trim();
        String subject = subjectEditText.getText().toString().trim();
        String body = bodyEditText.getText().toString().trim();
        String description = descriptionEditText.getText().toString().trim();

        if (name.isEmpty()) {
            return;
        }
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
            requestBody.put("name", name);
            requestBody.put("subject", subject);
            requestBody.put("body", styledBody);
            requestBody.put("description", description);

            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    BASE_URL,
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
                            android.util.Log.e("CreateEmailTemplate", "Error creating template: " + error.getMessage());
                            Toast.makeText(CreateEmailTemplateActivity.this, "Failed to create template: " + error.getMessage(), Toast.LENGTH_LONG).show();
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
            android.util.Log.e("CreateEmailTemplate", "Error creating JSON: " + e.getMessage());
        }
    }
}
