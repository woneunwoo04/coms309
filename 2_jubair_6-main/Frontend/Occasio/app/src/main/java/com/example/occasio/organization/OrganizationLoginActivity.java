package com.example.occasio.organization;
import com.example.occasio.R;
import com.example.occasio.auth.LoginActivity;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
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

public class OrganizationLoginActivity extends AppCompatActivity {
    private EditText orgNameEditText;
    private EditText passwordEditText;
    private CheckBox rememberMeCheckBox;
    private Button loginButton;
    private Button signupButton;

    private RequestQueue requestQueue;
    private SharedPreferences sharedPreferences;
    private static final String ORG_LOGIN_URL = com.example.occasio.utils.ServerConfig.ORG_LOGIN_URL;
    private static final String PREFS_NAME = "OrgPrefs";
    private static final String KEY_ORG_NAME = "org_name";
    private static final String KEY_REMEMBER_ME = "remember_me";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organization_login);

        orgNameEditText = findViewById(R.id.org_login_name_edt);
        passwordEditText = findViewById(R.id.org_login_password_edt);
        rememberMeCheckBox = findViewById(R.id.org_login_remember_me_cb);
        loginButton = findViewById(R.id.org_login_login_btn);
        signupButton = findViewById(R.id.org_login_signup_btn);

        requestQueue = Volley.newRequestQueue(this);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load saved credentials if Remember Me was checked
        loadSavedCredentials();

        loginButton.setOnClickListener(v -> loginOrganization());
        signupButton.setOnClickListener(v -> {
            startActivity(new Intent(OrganizationLoginActivity.this, OrganizationSignupActivity.class));
            finish();
        });
    }

    private void loadSavedCredentials() {
        boolean rememberMe = sharedPreferences.getBoolean(KEY_REMEMBER_ME, false);
        String savedOrgName = sharedPreferences.getString(KEY_ORG_NAME, "");

        if (rememberMe && !savedOrgName.isEmpty()) {
            orgNameEditText.setText(savedOrgName);
            rememberMeCheckBox.setChecked(true);
        }
    }

    private void loginOrganization() {
        String orgName = orgNameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        boolean rememberMe = rememberMeCheckBox.isChecked();

        if (orgName.isEmpty() || password.isEmpty()) {
            return;
        }

        // Create JSON object for login
        try {
            JSONObject loginData = new JSONObject();
            loginData.put("orgName", orgName);
            loginData.put("password", password);

            // Make POST request
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                ORG_LOGIN_URL,
                loginData,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            // Backend returns: {"success": true, "message": "Login successful", "organization": {...}}
                            if (response.has("organization") && !response.isNull("organization")) {
                                JSONObject organizationObj = response.getJSONObject("organization");
                                String responseOrgName = organizationObj.getString("orgName");
                                Long responseOrgId = organizationObj.getLong("id");
                                
                                // Save credentials if Remember Me is checked
                                if (rememberMe) {
                                    saveCredentials(responseOrgName);
                                } else {
                                    clearSavedCredentials();
                                }

                                    android.util.Log.d("OrganizationLoginActivity", "Organization login successful!");
                                
                                // Navigate to organization dashboard with orgName and orgId
                                Intent intent = new Intent(OrganizationLoginActivity.this, OrganizationDashboardActivity.class);
                                intent.putExtra("orgName", responseOrgName);
                                intent.putExtra("orgId", responseOrgId);
                                startActivity(intent);
                                finish();
                            } else if (response.has("success") && response.getBoolean("success")) {
                                // Fallback: try to get orgName directly if organization object is missing
                                String responseOrgName = response.optString("orgName", orgName);
                                    android.util.Log.d("OrganizationLoginActivity", "Organization login successful!");
                                
                                Intent intent = new Intent(OrganizationLoginActivity.this, OrganizationDashboardActivity.class);
                                intent.putExtra("orgName", responseOrgName);
                                intent.putExtra("orgId", 1L); // Fallback ID
                                startActivity(intent);
                                finish();
                            } else {
                                    android.util.Log.w("OrganizationLoginActivity", "Login response missing organization data");
                            }
                        } catch (JSONException e) {
                            android.util.Log.e("OrganizationLoginActivity", "Error parsing login response: " + e.getMessage());
                            android.util.Log.e("OrganizationLoginActivity", "Response was: " + response.toString());
                                android.util.Log.e("OrganizationLoginActivity", "Error parsing login response");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        loginButton.setEnabled(true);
                        loginButton.setText("Sign In");
                        
                        String errorMessage = "Login failed: Invalid credentials";
                        
                        if (error.networkResponse != null) {
                            int statusCode = error.networkResponse.statusCode;
                            
                            // Try to parse error response body
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                if (!responseBody.isEmpty()) {
                                    JSONObject errorJson = new JSONObject(responseBody);
                                    errorMessage = errorJson.optString("error", errorMessage);
                                }
                            } catch (Exception e) {
                                // If parsing fails, use status code
                                switch (statusCode) {
                                    case 401:
                                        errorMessage = "Invalid organization name or password";
                                        break;
                                    case 404:
                                        errorMessage = "Organization not found";
                                        break;
                                    case 500:
                                        errorMessage = "Server error. Please try again later";
                                        break;
                                    default:
                                        errorMessage = "Login failed (HTTP " + statusCode + ")";
                                        break;
                                }
                            }
                        } else if (error.getMessage() != null) {
                            if (error.getMessage().contains("timeout") || error.getMessage().contains("Timeout")) {
                                errorMessage = "Connection timeout. Please check your internet connection.";
                            } else if (error.getMessage().contains("No address") || error.getMessage().contains("Unable to resolve")) {
                                errorMessage = "Cannot reach server. Please check your connection.";
                            }
                        }
                        
                        android.util.Log.e("OrganizationLoginActivity", "Login error: " + error.toString());
                    }
                }
            );

            jsonObjectRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            requestQueue.add(jsonObjectRequest);
        } catch (JSONException e) {
        }
    }

    private void saveCredentials(String orgName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_ORG_NAME, orgName);
        editor.putBoolean(KEY_REMEMBER_ME, true);
        editor.apply();
    }

    private void clearSavedCredentials() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_ORG_NAME);
        editor.putBoolean(KEY_REMEMBER_ME, false);
        editor.apply();
    }
}
