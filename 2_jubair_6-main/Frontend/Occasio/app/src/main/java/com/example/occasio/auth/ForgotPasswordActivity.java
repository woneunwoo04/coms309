package com.example.occasio.auth;
import com.example.occasio.R;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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

import org.json.JSONException;
import org.json.JSONObject;

public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPasswordActivity";
    private EditText etEmail;
    private Button btnSendRequest;
    private Button btnBackToLogin;
    private TextView tvInstructions;
    private RequestQueue requestQueue;

    // Backend server URL
    private static final String FORGOT_PASSWORD_URL = com.example.occasio.utils.ServerConfig.BASE_URL + "/user_info/forgot-password";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        initViews();
        setupClickListeners();
        
        requestQueue = Volley.newRequestQueue(this);
    }

    private void initViews() {
        etEmail = findViewById(R.id.et_email_forgot);
        btnSendRequest = findViewById(R.id.btn_send_request);
        btnBackToLogin = findViewById(R.id.btn_back_to_login);
        tvInstructions = findViewById(R.id.tv_forgot_password_instructions);
    }

    private void setupClickListeners() {
        btnSendRequest.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();

            if (email.isEmpty()) {
                etEmail.requestFocus();
                return;
            }

                if (!com.example.occasio.utils.ApiUtils.isValidEmail(email)) {
                etEmail.requestFocus();
                return;
            }

            // Send password reset request
            sendPasswordResetRequest(email);
        });

        btnBackToLogin.setOnClickListener(v -> {
            Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void sendPasswordResetRequest(String email) {
        // Disable button to prevent multiple requests
        btnSendRequest.setEnabled(false);
        btnSendRequest.setText("Sending...");

        JSONObject jsonRequest = new JSONObject();
        try {
            jsonRequest.put("email", email);
        } catch (JSONException e) {
            e.printStackTrace();
            resetButton();
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                FORGOT_PASSWORD_URL,
                jsonRequest,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        resetButton();
                        Log.d(TAG, "Response: " + response.toString());
                        
                        try {
                            boolean success = response.getBoolean("success");
                            String message = response.optString("message", "Password reset request sent");
                            
                            if (success) {
                                    android.util.Log.d("ForgotPasswordActivity", "Password reset instructions sent to your email!");
                                
                                // Update instructions
                                tvInstructions.setText("Check your email for password reset instructions.\n" +
                                    "If you don't see the email, check your spam folder.");
                                
                                // Clear email field
                                etEmail.setText("");
                                
                                // Navigate back to login after a delay
                                new android.os.Handler().postDelayed(() -> {
                                    Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
                                    startActivity(intent);
                                    finish();
                                }, 3000);
                            } else {
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        resetButton();
                        Log.e(TAG, "Error: " + error.toString());
                        
                        String errorMessage = "Failed to send request. Please try again.";
                        if (error.networkResponse != null) {
                            switch (error.networkResponse.statusCode) {
                                case 404:
                                    errorMessage = "Email not found. Please check your email address.";
                                    break;
                                case 500:
                                    errorMessage = "Server error. Please try again later.";
                                    break;
                                default:
                                    errorMessage = "Network error. Please check your connection.";
                                    break;
                            }
                        }
                        
                    }
                }
        );

        requestQueue.add(jsonObjectRequest);
    }

    private void resetButton() {
        btnSendRequest.setEnabled(true);
        btnSendRequest.setText("Send Reset Link");
    }

    // Using ApiUtils.isValidEmail instead of local method
}
