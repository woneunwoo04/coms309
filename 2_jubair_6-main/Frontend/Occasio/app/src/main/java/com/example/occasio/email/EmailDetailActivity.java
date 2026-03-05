package com.example.occasio.email;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import com.example.occasio.utils.ServerConfig;

public class EmailDetailActivity extends AppCompatActivity {

    private TextView subjectText;
    private TextView sentAtText;
    private TextView templateNameText;
    private WebView bodyWebView;
    private RequestQueue requestQueue;
    private Long emailId;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_detail);

        Intent intent = getIntent();
        emailId = intent.getLongExtra("emailId", -1);
        username = intent.getStringExtra("username");
        
        if (emailId == -1 || username == null || username.isEmpty()) {
            finish();
            return;
        }

        requestQueue = Volley.newRequestQueue(this);
        
        initializeViews();
        loadEmailData();
        markAsRead();
    }

    private void initializeViews() {
        subjectText = findViewById(R.id.email_detail_subject);
        sentAtText = findViewById(R.id.email_detail_sent_at);
        templateNameText = findViewById(R.id.email_detail_template_name);
        bodyWebView = findViewById(R.id.email_detail_body_webview);
        
        // Configure WebView
        if (bodyWebView != null) {
            bodyWebView.getSettings().setJavaScriptEnabled(true);
            bodyWebView.getSettings().setLoadWithOverviewMode(true);
            bodyWebView.getSettings().setUseWideViewPort(true);
        }
    }

    private void loadEmailData() {
        Intent intent = getIntent();
        String subject = intent.getStringExtra("subject");
        String body = intent.getStringExtra("body");
        String sentAt = intent.getStringExtra("sentAt");
        String templateName = intent.getStringExtra("templateName");

        if (subjectText != null) {
            subjectText.setText(subject != null ? subject : "No Subject");
        }
        
        if (sentAtText != null) {
            sentAtText.setText(sentAt != null ? "Sent: " + sentAt : "Unknown date");
        }
        
        if (templateNameText != null) {
            if (templateName != null && !templateName.isEmpty()) {
                templateNameText.setText("Template: " + templateName);
                templateNameText.setVisibility(android.view.View.VISIBLE);
            } else {
                templateNameText.setVisibility(android.view.View.GONE);
            }
        }
        
        if (bodyWebView != null && body != null) {
            // Load HTML content in WebView
            bodyWebView.loadDataWithBaseURL(null, body, "text/html", "UTF-8", null);
        }
    }

    private void markAsRead() {
        if (requestQueue == null) {
            android.util.Log.e("EmailDetail", "RequestQueue is null, cannot mark email as read");
            return;
        }
        
        if (username == null || username.isEmpty() || emailId == null || emailId == -1) {
            android.util.Log.e("EmailDetail", "Invalid email data, cannot mark as read");
            return;
        }
        
        String url = ServerConfig.BASE_URL + "/api/email/logs/" + username + "/" + emailId + "/read?read=true";
        
        StringRequest request = new StringRequest(
                Request.Method.PATCH,
                url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        android.util.Log.d("EmailDetail", "Email marked as read");
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        android.util.Log.e("EmailDetail", "Error marking email as read: " + error.getMessage());
                    }
                }
        );
        
        requestQueue.add(request);
    }
}
