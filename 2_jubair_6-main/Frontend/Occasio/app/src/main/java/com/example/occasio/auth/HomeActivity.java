package com.example.occasio.auth;
import com.example.occasio.R;
import com.example.occasio.events.AllEventsActivity;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class HomeActivity extends AppCompatActivity {
    private Button userLoginButton;
    private Button orgLoginButton;
    private TextView signupLink;
    private TextView welcomeTextView;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_REMEMBER_ME = "remember_me";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        userLoginButton = findViewById(R.id.home_user_login_btn);
        orgLoginButton = findViewById(R.id.home_org_login_btn);
        signupLink = findViewById(R.id.home_signup_link);
        welcomeTextView = findViewById(R.id.home_welcome_txt);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Check if user is already logged in (Remember Me)
        checkRememberMe();
        
        // Test server connectivity
        testServerConnection();

        userLoginButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        orgLoginButton.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, com.example.occasio.organization.OrganizationLoginActivity.class);
            startActivity(intent);
        });

        signupLink.setOnClickListener(v -> showSignupOptions());
    }
    
    private void showSignupOptions() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choose Account Type")
            .setMessage("Select the type of account you want to create:")
            .setPositiveButton("User Account", (dialog, which) -> {
                Intent intent = new Intent(HomeActivity.this, SignupActivity.class);
                startActivity(intent);
            })
            .setNegativeButton("Organization Account", (dialog, which) -> {
                Intent intent = new Intent(HomeActivity.this, com.example.occasio.organization.OrganizationSignupActivity.class);
                startActivity(intent);
            })
            .setNeutralButton("Cancel", null)
            .show();
    }

    private void testServerConnection() {
        // Server connectivity will be tested when making actual API calls
        // No need to show offline mode message here
    }

    private void checkRememberMe() {
        boolean rememberMe = sharedPreferences.getBoolean(KEY_REMEMBER_ME, false);
        String savedUsername = sharedPreferences.getString(KEY_USERNAME, "");

        if (rememberMe && !savedUsername.isEmpty()) {
            // User is remembered, go directly to Events page
            Intent intent = new Intent(HomeActivity.this, AllEventsActivity.class);
            intent.putExtra("username", savedUsername);
            startActivity(intent);
            finish();
        } else {
            // Show welcome message
            welcomeTextView.setText("Welcome to Occasio!\nPlease login or signup to continue.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check again when returning to this activity
        checkRememberMe();
    }
}
