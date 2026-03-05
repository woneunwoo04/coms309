package com.example.occasio.payment;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
import com.example.occasio.utils.ServerConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * PaymentHistoryActivity displays user's payment history
 */
public class PaymentHistoryActivity extends AppCompatActivity {

    private static final String TAG = "PaymentHistoryActivity";
    
    private RecyclerView paymentRecyclerView;
    private TextView emptyTextView;
    private RequestQueue requestQueue;
    private String currentUsername;
    private PaymentHistoryAdapter adapter;
    private List<PaymentHistoryItem> paymentList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_history);

        // Get username from intent
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("username")) {
            currentUsername = intent.getStringExtra("username");
        }

        if (currentUsername == null || currentUsername.isEmpty()) {
            finish();
            return;
        }

        // Initialize views
        paymentRecyclerView = findViewById(R.id.payment_history_recycler_view);
        emptyTextView = findViewById(R.id.payment_history_empty_tv);

        requestQueue = Volley.newRequestQueue(this);
        paymentList = new ArrayList<>();
        adapter = new PaymentHistoryAdapter(paymentList);
        
        paymentRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        paymentRecyclerView.setAdapter(adapter);

        // Load payment history
        loadPaymentHistory();
    }

    private void loadPaymentHistory() {
        String url = ServerConfig.PAYMENT_HISTORY_URL + currentUsername;

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        paymentList.clear();
                        
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject paymentJson = response.getJSONObject(i);
                            
                            PaymentHistoryItem item = new PaymentHistoryItem();
                            item.setId(paymentJson.getLong("id"));
                            item.setAmount(paymentJson.getDouble("amount"));
                            item.setCurrency(paymentJson.optString("currency", "USD"));
                            item.setStatus(paymentJson.getString("status"));
                            item.setEventId(paymentJson.getLong("eventId"));
                            item.setEventName(paymentJson.optString("eventName", "Unknown Event"));
                            
                            // Parse dates
                            String createdAtStr = paymentJson.optString("createdAt", "");
                            String paidAtStr = paymentJson.optString("paidAt", "");
                            
                            if (!createdAtStr.isEmpty()) {
                                item.setCreatedAt(createdAtStr);
                            }
                            if (!paidAtStr.isEmpty()) {
                                item.setPaidAt(paidAtStr);
                            }
                            
                            item.setPaymentMethod(paymentJson.optString("paymentMethod", ""));
                            item.setLast4Digits(paymentJson.optString("last4Digits", ""));
                            
                            paymentList.add(item);
                        }
                        
                        adapter.notifyDataSetChanged();
                        
                        // Show/hide empty state
                        if (paymentList.isEmpty()) {
                            emptyTextView.setVisibility(android.view.View.VISIBLE);
                            paymentRecyclerView.setVisibility(android.view.View.GONE);
                        } else {
                            emptyTextView.setVisibility(android.view.View.GONE);
                            paymentRecyclerView.setVisibility(android.view.View.VISIBLE);
                        }
                        
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing payment history", e);
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error loading payment history", error);
                    String errorMessage = "Failed to load payment history";
                    if (error.networkResponse != null) {
                        errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                    }
                }
            }
        );

        requestQueue.add(request);
    }

    // Payment history item model
    public static class PaymentHistoryItem {
        private Long id;
        private Double amount;
        private String currency;
        private String status;
        private Long eventId;
        private String eventName;
        private String createdAt;
        private String paidAt;
        private String paymentMethod;
        private String last4Digits;

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getEventId() { return eventId; }
        public void setEventId(Long eventId) { this.eventId = eventId; }
        public String getEventName() { return eventName; }
        public void setEventName(String eventName) { this.eventName = eventName; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
        public String getPaidAt() { return paidAt; }
        public void setPaidAt(String paidAt) { this.paidAt = paidAt; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getLast4Digits() { return last4Digits; }
        public void setLast4Digits(String last4Digits) { this.last4Digits = last4Digits; }
    }
}

