package com.example.occasio.auth;
import com.example.occasio.R;
import com.example.occasio.organization.OrganizationLoginActivity;
import com.example.occasio.events.AllEventsActivity;
import com.example.occasio.utils.NetworkUtils;
import com.example.occasio.utils.ServerConfig;

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

/**
 * Activity responsible for user authentication and login functionality.
 * 
 * <p>This activity provides a user interface for logging into the Occasio application.
 * It handles user credential validation, authentication requests to the backend server,
 * and manages the "Remember Me" feature for persistent login sessions.</p>
 * 
 * <p>Key features:
 * <ul>
 *   <li>Username and password validation</li>
 *   <li>Remember Me checkbox for saving credentials</li>
 *   <li>Navigation to signup and organization login</li>
 *   <li>Error handling for network and authentication failures</li>
 * </ul>
 * </p>
 * 
 * @author Team Member 1
 * @version 1.0
 * @since 1.0
 */
public class LoginActivity extends AppCompatActivity {
    private EditText usernameEditText;
    private EditText passwordEditText;
    private CheckBox rememberMeCheckBox;
    private Button loginButton;
    private Button signupButton;

    private RequestQueue requestQueue;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_REMEMBER_ME = "remember_me";

    /**
     * Initializes the activity and sets up the user interface components.
     * 
     * <p>This method is called when the activity is first created. It initializes
     * all UI components, sets up click listeners, loads saved credentials if
     * "Remember Me" was previously checked, and prepares the request queue
     * for network operations.</p>
     * 
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down, this Bundle contains
     *                           the data it most recently supplied in
     *                           onSaveInstanceState(Bundle). Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameEditText = findViewById(R.id.login_username_edt);
        passwordEditText = findViewById(R.id.login_password_edt);
        rememberMeCheckBox = findViewById(R.id.login_remember_me_cb);
        loginButton = findViewById(R.id.login_login_btn);
        signupButton = findViewById(R.id.login_signup_btn);

        requestQueue = Volley.newRequestQueue(this);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load saved credentials if Remember Me was checked
        loadSavedCredentials();

        loginButton.setOnClickListener(v -> loginUser());
        signupButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
            finish();
        });
    }

    /**
     * Loads previously saved user credentials if "Remember Me" was checked.
     * 
     * <p>This method retrieves the saved username from SharedPreferences and
     * populates the username field if the "Remember Me" option was previously
     * enabled. It also sets the checkbox state accordingly.</p>
     * 
     * <p>If no saved credentials are found or "Remember Me" was not checked,
     * the fields remain empty.</p>
     */
    private void loadSavedCredentials() {
        boolean rememberMe = sharedPreferences.getBoolean(KEY_REMEMBER_ME, false);
        String savedUsername = sharedPreferences.getString(KEY_USERNAME, "");

        if (rememberMe && !savedUsername.isEmpty()) {
            usernameEditText.setText(savedUsername);
            rememberMeCheckBox.setChecked(true);
        }
    }

    /**
     * Validates user input and attempts to authenticate the user with the backend server.
     * 
     * <p>This method performs comprehensive validation of the username and password fields,
     * including length requirements and non-empty checks. Upon successful validation,
     * it creates a JSON request and sends it to the login endpoint.</p>
     * 
     * <p>Validation rules:
     * <ul>
     *   <li>Username must be at least 3 characters long</li>
     *   <li>Password must be at least 6 characters long</li>
     *   <li>Both fields must be non-empty</li>
     * </ul>
     * </p>
     * 
     * <p>On successful authentication:
     * <ul>
     *   <li>Saves username to SharedPreferences for session management</li>
     *   <li>Optionally saves "Remember Me" preference</li>
     *   <li>Navigates to AllEventsActivity</li>
     * </ul>
     * </p>
     * 
     * <p>On authentication failure:
     * <ul>
     *   <li>Displays appropriate error messages based on HTTP status codes</li>
     *   <li>Handles network errors gracefully</li>
     *   <li>Re-enables the login button for retry</li>
     * </ul>
     * </p>
     */
    private void loginUser() {
        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        boolean rememberMe = rememberMeCheckBox.isChecked();

        // Enhanced validation
        if (username.isEmpty()) {
            usernameEditText.setError("Username is required");
            usernameEditText.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }

        if (username.length() < 3) {
            usernameEditText.setError("Username must be at least 3 characters");
            usernameEditText.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters");
            passwordEditText.requestFocus();
            return;
        }

        // Clear any previous errors
        usernameEditText.setError(null);
        passwordEditText.setError(null);

        // Disable login button to prevent multiple requests
        loginButton.setEnabled(false);
        loginButton.setText("Signing in...");

        // Create login request
        JSONObject loginData = new JSONObject();
        try {
            loginData.put("username", username);
            loginData.put("password", password);
        } catch (JSONException e) {
            resetLoginButton();
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
            Request.Method.POST,
            ServerConfig.LOGIN_URL,
            loginData,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    resetLoginButton();
                    try {
                        // Backend returns user object directly on success
                        String responseUsername = response.getString("username");
                        String responseEmail = response.getString("email");
                        
                        // ✅ Always save username for current session (required for messaging, etc.)
                        // Remember Me only controls whether to persist after logout
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(KEY_USERNAME, responseUsername);
                        if (rememberMe) {
                            editor.putBoolean(KEY_REMEMBER_ME, true);
                        } else {
                            editor.putBoolean(KEY_REMEMBER_ME, false);
                        }
                        editor.apply();

                        
                        // Navigate to Events page (main page)
                        Intent intent = new Intent(LoginActivity.this, com.example.occasio.events.AllEventsActivity.class);
                        intent.putExtra("username", responseUsername);
                        startActivity(intent);
                        finish();
                        
                    } catch (JSONException e) {
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    resetLoginButton();
                    
                    String errorMessage = NetworkUtils.getErrorMessage(error);
                    
                    // Special handling for authentication errors
                    if (error.networkResponse != null && error.networkResponse.statusCode == 401) {
                        errorMessage = "Invalid username or password";
                    } else if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        errorMessage = "User not found. Please sign up first.";
                    }
                    
                    NetworkUtils.logError("LoginActivity", "Login failed", error);
                }
            }
        );

        // Apply standard retry policy
        NetworkUtils.applyRetryPolicy(jsonObjectRequest);
        requestQueue.add(jsonObjectRequest);
    }

    /**
     * Resets the login button to its default enabled state.
     * 
     * <p>This method is called after a login attempt completes (either success or failure)
     * to restore the button's enabled state and text, allowing the user to retry
     * if necessary.</p>
     */
    private void resetLoginButton() {
        loginButton.setEnabled(true);
        loginButton.setText("Sign In");
    }

    /**
     * Saves user credentials to SharedPreferences for future sessions.
     * 
     * <p>This method stores the username and sets the "Remember Me" flag to true
     * in SharedPreferences, allowing the credentials to be automatically loaded
     * on subsequent app launches.</p>
     * 
     * @param username The username to save for future login attempts
     */
    private void saveCredentials(String username) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_USERNAME, username);
        editor.putBoolean(KEY_REMEMBER_ME, true);
        editor.apply();
    }

    /**
     * Clears all saved user credentials from SharedPreferences.
     * 
     * <p>This method removes the stored username and sets the "Remember Me"
     * flag to false, effectively logging out the user from persistent storage
     * while maintaining the current session if active.</p>
     */
    private void clearSavedCredentials() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_USERNAME);
        editor.putBoolean(KEY_REMEMBER_ME, false);
        editor.apply();
    }
}