package com.example.occasio.organization;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import com.example.occasio.utils.ServerConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SendEmailActivity extends AppCompatActivity {

    private RadioGroup emailTypeRadioGroup;
    private RadioButton customEmailRadio;
    private RadioButton templateEmailRadio;
    
    // Custom email fields
    private View customEmailLayout;
    private EditText customToEditText;
    private EditText customSubjectEditText;
    private EditText customMessageEditText;
    
    // Template email fields
    private View templateEmailLayout;
    private Spinner templateSpinner;
    private EditText templateToEditText;
    private EditText templateVariablesEditText;
    private TextView templateVariablesHint;
    
    private Button sendButton;
    private Button cancelButton;
    
    private RequestQueue requestQueue;
    private String organizationUsername;
    private List<String> templateNames = new ArrayList<>();
    private static final String TEMPLATES_URL = ServerConfig.BASE_URL + "/api/email/templates";
    private static final String SEND_CUSTOM_URL = ServerConfig.BASE_URL + "/api/email/send";
    private static final String SEND_TEMPLATE_URL = ServerConfig.BASE_URL + "/api/email/send-template";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_email);

        organizationUsername = getIntent().getStringExtra("orgName");
        if (organizationUsername == null || organizationUsername.isEmpty()) {
            finish();
            return;
        }

        requestQueue = Volley.newRequestQueue(this);
        
        initializeViews();
        setupClickListeners();
        loadTemplates();
    }

    private void initializeViews() {
        emailTypeRadioGroup = findViewById(R.id.send_email_type_radio_group);
        customEmailRadio = findViewById(R.id.send_email_custom_radio);
        templateEmailRadio = findViewById(R.id.send_email_template_radio);
        
        customEmailLayout = findViewById(R.id.send_email_custom_layout);
        customToEditText = findViewById(R.id.send_email_custom_to_et);
        customSubjectEditText = findViewById(R.id.send_email_custom_subject_et);
        customMessageEditText = findViewById(R.id.send_email_custom_message_et);
        
        templateEmailLayout = findViewById(R.id.send_email_template_layout);
        templateSpinner = findViewById(R.id.send_email_template_spinner);
        templateToEditText = findViewById(R.id.send_email_template_to_et);
        templateVariablesEditText = findViewById(R.id.send_email_template_variables_et);
        templateVariablesHint = findViewById(R.id.send_email_template_variables_hint);
        
        sendButton = findViewById(R.id.send_email_send_btn);
        cancelButton = findViewById(R.id.send_email_cancel_btn);
        
        // Set default to custom email
        customEmailRadio.setChecked(true);
        updateEmailTypeLayout();
    }

    private void setupClickListeners() {
        emailTypeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            updateEmailTypeLayout();
        });
        
        sendButton.setOnClickListener(v -> sendEmail());
        cancelButton.setOnClickListener(v -> finish());
    }

    private void updateEmailTypeLayout() {
        if (customEmailRadio.isChecked()) {
            customEmailLayout.setVisibility(View.VISIBLE);
            templateEmailLayout.setVisibility(View.GONE);
        } else {
            customEmailLayout.setVisibility(View.GONE);
            templateEmailLayout.setVisibility(View.VISIBLE);
        }
    }

    private void loadTemplates() {
        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                TEMPLATES_URL,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        runOnUiThread(() -> {
                            templateNames.clear();
                            try {
                                for (int i = 0; i < response.length(); i++) {
                                    JSONObject obj = response.getJSONObject(i);
                                    templateNames.add(obj.getString("name"));
                                }
                                
                                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                        SendEmailActivity.this,
                                        android.R.layout.simple_spinner_item,
                                        templateNames
                                );
                                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                templateSpinner.setAdapter(adapter);
                                
                                if (templateNames.isEmpty()) {
                                    if (templateVariablesHint != null) {
                                        templateVariablesHint.setText("No templates available. Create templates first.");
                                    }
                                } else {
                                    if (templateVariablesHint != null) {
                                        templateVariablesHint.setText("Enter variables as JSON: {\"username\": \"John\", \"date\": \"2025-01-15\"}");
                                    }
                                }
                            } catch (JSONException e) {
                                android.util.Log.e("SendEmail", "Error parsing templates: " + e.getMessage());
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
                                    android.util.Log.e("SendEmail", "Error response body: " + responseBody);
                                    android.util.Log.e("SendEmail", "Error status code: " + error.networkResponse.statusCode);
                                    
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
                            
                            android.util.Log.e("SendEmail", "Error loading templates: " + errorMessage);
                            if (templateVariablesHint != null) {
                                templateVariablesHint.setText("Error: " + errorMessage);
                            }
                        });
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("username", organizationUsername);
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "application/json");
                android.util.Log.d("SendEmail", "Request headers: username=" + organizationUsername);
                return headers;
            }
        };
        
        requestQueue.add(request);
    }

    private void sendEmail() {
        if (customEmailRadio.isChecked()) {
            sendCustomEmail();
        } else {
            sendTemplateEmail();
        }
    }

    private void sendCustomEmail() {
        String to = customToEditText.getText().toString().trim();
        String subject = customSubjectEditText.getText().toString().trim();
        String message = customMessageEditText.getText().toString().trim();

        if (to.isEmpty()) {
            return;
        }
        if (subject.isEmpty()) {
            return;
        }
        if (message.isEmpty()) {
            return;
        }

        try {
            // Automatically apply pink & cute styling to the message
            String styledMessage = com.example.occasio.utils.EmailStylingHelper.applyPinkCuteStyling(message, subject);
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("to", to);
            requestBody.put("subject", subject);
            requestBody.put("message", styledMessage);

            StringRequest request = new StringRequest(
                    Request.Method.POST,
                    SEND_CUSTOM_URL,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            clearCustomForm();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            android.util.Log.e("SendEmail", "Error sending custom email: " + error.getMessage());
                            Toast.makeText(SendEmailActivity.this, "Failed to send email: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
            ) {
                @Override
                public byte[] getBody() {
                    return requestBody.toString().getBytes();
                }

                @Override
                public String getBodyContentType() {
                    return "application/json";
                }

                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("username", organizationUsername);
                    headers.put("Content-Type", "application/json");
                    return headers;
                }
            };

            requestQueue.add(request);
        } catch (JSONException e) {
            android.util.Log.e("SendEmail", "Error creating JSON: " + e.getMessage());
        }
    }

    private void sendTemplateEmail() {
        String to = templateToEditText.getText().toString().trim();
        String variablesJson = templateVariablesEditText.getText().toString().trim();

        if (to.isEmpty()) {
            return;
        }
        if (templateSpinner.getSelectedItem() == null) {
            return;
        }

        String templateName = templateSpinner.getSelectedItem().toString();
        Map<String, String> variables = new HashMap<>();
        
        if (!variablesJson.isEmpty()) {
            try {
                JSONObject variablesObj = new JSONObject(variablesJson);
                JSONArray keys = variablesObj.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String key = keys.getString(i);
                        variables.put(key, variablesObj.getString(key));
                    }
                }
            } catch (JSONException e) {
                return;
            }
        }

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("to", to);
            requestBody.put("templateName", templateName);
            if (!variables.isEmpty()) {
                JSONObject variablesObj = new JSONObject();
                for (Map.Entry<String, String> entry : variables.entrySet()) {
                    variablesObj.put(entry.getKey(), entry.getValue());
                }
                requestBody.put("variables", variablesObj);
            }

            StringRequest request = new StringRequest(
                    Request.Method.POST,
                    SEND_TEMPLATE_URL,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            clearTemplateForm();
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            android.util.Log.e("SendEmail", "Error sending template email: " + error.getMessage());
                            Toast.makeText(SendEmailActivity.this, "Failed to send email: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
            ) {
                @Override
                public byte[] getBody() {
                    return requestBody.toString().getBytes();
                }

                @Override
                public String getBodyContentType() {
                    return "application/json";
                }

                @Override
                public Map<String, String> getHeaders() {
                    Map<String, String> headers = new HashMap<>();
                    headers.put("username", organizationUsername);
                    headers.put("Content-Type", "application/json");
                    return headers;
                }
            };

            requestQueue.add(request);
        } catch (JSONException e) {
            android.util.Log.e("SendEmail", "Error creating JSON: " + e.getMessage());
        }
    }

    private void clearCustomForm() {
        customToEditText.setText("");
        customSubjectEditText.setText("");
        customMessageEditText.setText("");
    }

    private void clearTemplateForm() {
        templateToEditText.setText("");
        templateVariablesEditText.setText("");
    }
}
