package com.example.occasio.organization;
import com.example.occasio.R;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONException;
import org.json.JSONObject;

public class OrganizationSignupActivity extends AppCompatActivity {
    private EditText orgNameEditText;
    private EditText passwordEditText;
    private EditText confirmPasswordEditText;
    private EditText descriptionEditText;
    private Button signupButton;
    
    private RequestQueue requestQueue;
    private static final String ORG_SIGNUP_URL = com.example.occasio.utils.ServerConfig.ORG_SIGNUP_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organization_signup);

        // Initialize views
        orgNameEditText = findViewById(R.id.org_signup_name_edt);
        passwordEditText = findViewById(R.id.org_signup_password_edt);
        confirmPasswordEditText = findViewById(R.id.org_signup_confirm_password_edt);
        descriptionEditText = findViewById(R.id.org_signup_description_edt);
        signupButton = findViewById(R.id.org_signup_signup_btn);

        requestQueue = Volley.newRequestQueue(this);

        // Set click listeners
        signupButton.setOnClickListener(v -> signupOrganization());
    }

    private void signupOrganization() {
        String orgName = orgNameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();
        String description = descriptionEditText.getText().toString().trim();

        // Validation
        if (orgName.isEmpty() || password.isEmpty() || 
            confirmPassword.isEmpty() || description.isEmpty()) {
            return;
        }

        if (orgName.length() < 3) {
            return;
        }

        if (password.length() < 6) {
            return;
        }

        if (!password.equals(confirmPassword)) {
            return;
        }

        if (description.length() < 10) {
            return;
        }

        // Create JSON object for organization signup
        // Note: Backend generates email automatically from orgName, but we accept email for future use
        try {
            JSONObject orgData = new JSONObject();
            orgData.put("orgName", orgName);
            orgData.put("password", password);
            orgData.put("description", description);
            // Note: email is not used by backend but we keep it for consistency

            // Make POST request
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                ORG_SIGNUP_URL,
                orgData,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        signupButton.setEnabled(true);
                        signupButton.setText("Sign Up");
                        
                        try {
                            // Backend returns Organization object with id, orgName, description
                            if (response.has("orgName") && response.has("id")) {
                                String responseOrgName = response.getString("orgName");
                                Long responseOrgId = response.getLong("id");
                                
                                    android.util.Log.d("OrganizationSignupActivity", "Organization created successfully!");
                                
                                // Navigate to organization dashboard with orgName and orgId
                                Intent intent = new Intent(OrganizationSignupActivity.this, OrganizationDashboardActivity.class);
                                intent.putExtra("orgName", responseOrgName);
                                intent.putExtra("orgId", responseOrgId);
                                startActivity(intent);
                                finish();
                            } else if (response.has("error")) {
                                // Backend returned error object
                                String errorMsg = response.optString("message", response.optString("error", "Unknown error"));
                                    android.util.Log.e("OrganizationSignupActivity", "Signup failed: " + errorMsg);
                            } else {
                                // Fallback
                                String responseOrgName = response.optString("orgName", orgName);
                                    android.util.Log.d("OrganizationSignupActivity", "Organization created successfully!");
                                
                                Intent intent = new Intent(OrganizationSignupActivity.this, OrganizationDashboardActivity.class);
                                intent.putExtra("orgName", responseOrgName);
                                intent.putExtra("orgId", 1L); // Fallback ID
                                startActivity(intent);
                                finish();
                            }
                        } catch (JSONException e) {
                            android.util.Log.e("OrganizationSignupActivity", "Error parsing response: " + e.getMessage());
                            android.util.Log.e("OrganizationSignupActivity", "Response was: " + response.toString());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        signupButton.setEnabled(true);
                        signupButton.setText("Sign Up");
                        
                        String errorMessage = "Signup failed";
                        
                        if (error.networkResponse != null) {
                            int statusCode = error.networkResponse.statusCode;
                            
                            // Try to parse error response body
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                if (!responseBody.isEmpty()) {
                                    JSONObject errorJson = new JSONObject(responseBody);
                                    errorMessage = errorJson.optString("message", errorJson.optString("error", errorMessage));
                                }
                            } catch (Exception e) {
                                // If parsing fails, use status code
                                switch (statusCode) {
                                    case 400:
                                        errorMessage = "Invalid signup data. Please check your information.";
                                        break;
                                    case 409:
                                        errorMessage = "Organization name already exists. Please try a different name.";
                                        break;
                                    case 500:
                                        errorMessage = "Server error. Please try again later.";
                                        break;
                                    default:
                                        errorMessage = "Signup failed (HTTP " + statusCode + ")";
                                        break;
                                }
                            }
                        } else if (error.getMessage() != null) {
                            if (error.getMessage().contains("Duplicate") || error.getMessage().contains("duplicate")) {
                                errorMessage = "Organization name already exists. Please try a different name.";
                            } else if (error.getMessage().contains("timeout") || error.getMessage().contains("Timeout")) {
                                errorMessage = "Connection timeout. Please check your internet connection.";
                            } else if (error.getMessage().contains("No address") || error.getMessage().contains("Unable to resolve")) {
                                errorMessage = "Cannot reach server. Please check your connection.";
                            } else {
                                errorMessage = "Connection error: " + error.getMessage();
                            }
                        } else {
                            errorMessage = "Network error. Please check your connection.";
                        }
                        
                        android.util.Log.e("OrganizationSignupActivity", "Signup error: " + error.toString());
                    }
                }
            );

            jsonObjectRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            requestQueue.add(jsonObjectRequest);
        } catch (JSONException e) {
        }
    }
}
