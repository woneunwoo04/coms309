package com.example.occasio.email;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import com.example.occasio.base.BaseNavigationActivity;
import com.example.occasio.utils.ServerConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmailInboxActivity extends BaseNavigationActivity {

    private RecyclerView emailsRecyclerView;
    private TextView emptyView;
    private Button refreshButton;
    private Button backButton;
    
    private EmailAdapter emailAdapter;
    private RequestQueue requestQueue;
    private String currentUsername;
    private List<EmailItem> emailList = new ArrayList<>();
    private static final String BASE_URL = ServerConfig.BASE_URL + "/api/email/logs";
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_USERNAME = "username";

    public static class EmailItem {
        private Long id;
        private String subject;
        private String body;
        private String sentAt;
        private boolean readFlag;
        private String templateName;

        public EmailItem(Long id, String subject, String body, String sentAt, boolean readFlag, String templateName) {
            this.id = id;
            this.subject = subject;
            this.body = body;
            this.sentAt = sentAt;
            this.readFlag = readFlag;
            this.templateName = templateName;
        }

        public Long getId() { return id; }
        public String getSubject() { return subject; }
        public String getBody() { return body; }
        public String getSentAt() { return sentAt; }
        public boolean isReadFlag() { return readFlag; }
        public String getTemplateName() { return templateName; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_inbox);

        // Get username from Intent or SharedPreferences
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("username")) {
            currentUsername = intent.getStringExtra("username");
        }
        
        if (currentUsername == null || currentUsername.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            currentUsername = prefs.getString(KEY_USERNAME, "");
        }

        if (currentUsername == null || currentUsername.isEmpty()) {
            finish();
            return;
        }

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        setupBottomNavigation();
        
        requestQueue = Volley.newRequestQueue(this);
        loadEmails();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEmails();
    }

    private void initializeViews() {
        emailsRecyclerView = findViewById(R.id.email_inbox_recycler_view);
        emptyView = findViewById(R.id.email_inbox_empty_view);
        refreshButton = findViewById(R.id.email_inbox_refresh_btn);
        backButton = findViewById(R.id.email_inbox_back_btn);
    }

    private void setupRecyclerView() {
        if (emailsRecyclerView == null) {
            android.util.Log.e("EmailInbox", "RecyclerView is null, cannot setup");
            return;
        }
        
        emailAdapter = new EmailAdapter(emailList, email -> {
            // Open email detail
            Intent intent = new Intent(EmailInboxActivity.this, EmailDetailActivity.class);
            intent.putExtra("emailId", email.getId());
            intent.putExtra("subject", email.getSubject());
            intent.putExtra("body", email.getBody());
            intent.putExtra("sentAt", email.getSentAt());
            intent.putExtra("templateName", email.getTemplateName());
            intent.putExtra("username", currentUsername);
            startActivity(intent);
        });
        
        emailsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        emailsRecyclerView.setAdapter(emailAdapter);
    }

    private void setupClickListeners() {
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> loadEmails());
        }
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }

    private void loadEmails() {
        if (requestQueue == null) {
            android.util.Log.e("EmailInbox", "RequestQueue is null, cannot load emails");
            requestQueue = Volley.newRequestQueue(this);
        }
        
        if (currentUsername == null || currentUsername.isEmpty()) {
            android.util.Log.e("EmailInbox", "Username is null or empty, cannot load emails");
            return;
        }
        
        String url = BASE_URL + "/user/" + currentUsername + "?unreadOnly=false";
        
        JsonArrayRequest request = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        runOnUiThread(() -> {
                            emailList.clear();
                            try {
                                for (int i = 0; i < response.length(); i++) {
                                    JSONObject obj = response.getJSONObject(i);
                                    EmailItem email = new EmailItem(
                                            obj.getLong("id"),
                                            obj.optString("subject", "No Subject"),
                                            obj.optString("body", ""),
                                            obj.optString("sentAt", ""),
                                            obj.optBoolean("readFlag", false),
                                            obj.optString("templateName", null)
                                    );
                                    emailList.add(email);
                                }
                                if (emailAdapter != null) {
                                    emailAdapter.notifyDataSetChanged();
                                }
                                updateEmptyView();
                            } catch (JSONException e) {
                                android.util.Log.e("EmailInbox", "Error parsing emails: " + e.getMessage());
                            }
                        });
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        runOnUiThread(() -> {
                            android.util.Log.e("EmailInbox", "Error loading emails: " + error.getMessage());
                            emailList.clear();
                            if (emailAdapter != null) {
                                emailAdapter.notifyDataSetChanged();
                            }
                            updateEmptyView();
                            Toast.makeText(EmailInboxActivity.this, "Failed to load emails: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                }
        );
        
        requestQueue.add(request);
    }

    private void updateEmptyView() {
        if (emailsRecyclerView == null || emptyView == null) {
            return;
        }
        if (emailList.isEmpty()) {
            emailsRecyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emailsRecyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }
}
