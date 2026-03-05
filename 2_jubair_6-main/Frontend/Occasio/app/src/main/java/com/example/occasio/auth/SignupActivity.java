package com.example.occasio.auth;
import com.example.occasio.R;
import com.example.occasio.events.AllEventsActivity;
import com.example.occasio.utils.NetworkUtils;
import com.example.occasio.utils.ServerConfig;

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
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONException;
import org.json.JSONObject;
import com.google.gson.Gson;

public class SignupActivity extends AppCompatActivity {
    private EditText usernameEditText;
    private EditText emailEditText;
    private EditText passwordEditText;
    private EditText confirmPasswordEditText;
    private EditText fullNameEditText;
    private Button signupButton;
    
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize views
        usernameEditText = findViewById(R.id.signup_username_edt);
        emailEditText = findViewById(R.id.signup_email_edt);
        passwordEditText = findViewById(R.id.signup_password_edt);
        confirmPasswordEditText = findViewById(R.id.signup_confirm_password_edt);
        fullNameEditText = findViewById(R.id.signup_fullname_edt);
        signupButton = findViewById(R.id.signup_signup_btn);

        requestQueue = Volley.newRequestQueue(this);


        // Set click listeners
        signupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signupUser();
            }
        });
    }

    private void signupUser() {
        String username = usernameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();
        String fullName = fullNameEditText.getText().toString().trim();

        // Validation
        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || 
            confirmPassword.isEmpty()) {
            return;
        }

        if (username.length() < 3) {
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return;
        }

        if (password.length() < 6) {
            return;
        }

        if (!password.equals(confirmPassword)) {
            return;
        }

        // Disable signup button to prevent multiple requests
        signupButton.setEnabled(false);
        signupButton.setText("Creating account...");
        
        // Create JSON object that matches the mock server example
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("username", username);
            jsonObject.put("email", email);
            jsonObject.put("password", password);
            
            // Make POST request
            android.util.Log.d("SignupActivity", "Signup URL: " + ServerConfig.SIGNUP_URL);
            android.util.Log.d("SignupActivity", "Request Body: " + jsonObject.toString());
            
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                ServerConfig.SIGNUP_URL,
                jsonObject,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        signupButton.setEnabled(true);
                        signupButton.setText("Sign Up");
                        
                        try {
                            // Backend returns UserInfo object on success (with id, username, email, etc.)
                            // Check if response has username field (means success)
                            if (response.has("username") && response.has("id")) {
                                String createdUsername = response.getString("username");
                                // Navigate to Events page (main page) with actual username
                                Intent intent = new Intent(SignupActivity.this, AllEventsActivity.class);
                                intent.putExtra("username", createdUsername);
                                startActivity(intent);
                                finish();
                            } else if (response.has("error")) {
                                // Backend returned error object
                                String errorMsg = response.optString("message", response.optString("error", "Unknown error"));
                            } else {
                                // Fallback: try to get username directly
                                String responseUsername = response.optString("username", username);
                                Intent intent = new Intent(SignupActivity.this, AllEventsActivity.class);
                                intent.putExtra("username", responseUsername);
                                startActivity(intent);
                                finish();
                            }
                        } catch (JSONException e) {
                            android.util.Log.e("SignupActivity", "Error parsing response: " + e.getMessage());
                            android.util.Log.e("SignupActivity", "Response was: " + response.toString());
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
                            
                            // Log detailed error information
                            android.util.Log.e("SignupActivity", "HTTP Status Code: " + statusCode);
                            
                            // Try to parse error response body
                            try {
                                String responseBody = new String(error.networkResponse.data, "utf-8");
                                android.util.Log.e("SignupActivity", "Error Response Body: " + responseBody);
                                
                                JSONObject errorJson = new JSONObject(responseBody);
                                errorMessage = errorJson.optString("message", errorJson.optString("error", errorMessage));
                                android.util.Log.e("SignupActivity", "Parsed Error Message: " + errorMessage);
                            } catch (Exception e) {
                                android.util.Log.e("SignupActivity", "Error parsing response: " + e.getMessage());
                                // If parsing fails, use status code
                                switch (statusCode) {
                                    case 400:
                                        errorMessage = "Invalid signup data. Please check your information.";
                                        break;
                                    case 401:
                                        errorMessage = "Unauthorized. Please check your credentials.";
                                        break;
                                    case 404:
                                        errorMessage = "Signup endpoint not found. Please check server configuration.";
                                        break;
                                    case 409:
                                        errorMessage = "Username or email already exists. Please try different credentials.";
                                        break;
                                    case 415:
                                        errorMessage = "Unsupported media type. Please check request format.";
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
                            android.util.Log.e("SignupActivity", "Error Message: " + error.getMessage());
                            if (error.getMessage().contains("Duplicate") || error.getMessage().contains("duplicate")) {
                                errorMessage = "Username or email already exists. Please try different credentials.";
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
                        
                        
                        // Log the full error for debugging
                        android.util.Log.e("SignupActivity", "Full error details: " + error.toString());
                        if (error.networkResponse != null) {
                            android.util.Log.e("SignupActivity", "Network Response: " + error.networkResponse.toString());
                        }
                    }
                }
            );

            jsonObjectRequest.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            requestQueue.add(jsonObjectRequest);

        } catch (JSONException e) {
        }
    }
    

}

