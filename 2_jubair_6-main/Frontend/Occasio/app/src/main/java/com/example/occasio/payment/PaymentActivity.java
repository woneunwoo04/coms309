package com.example.occasio.payment;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast; // Keep for now but replace all usage with InAppNotificationHelper
import com.example.occasio.utils.InAppNotificationHelper;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import com.example.occasio.utils.ServerConfig;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * PaymentActivity handles Stripe payment processing for event registration
 * 
 * Flow:
 * 1. User clicks "Register" on an event with registration fee
 * 2. This activity is launched with eventId and username
 * 3. Backend creates PaymentIntent and returns clientSecret
 * 4. Stripe PaymentSheet is presented to user
 * 5. User completes payment
 * 6. Payment is confirmed with backend
 * 7. User is registered for event
 */
public class PaymentActivity extends AppCompatActivity {

    private static final String TAG = "PaymentActivity";
    
    private TextView eventTitleTextView;
    private TextView amountTextView;
    private Button payButton;
    private Button cancelButton;
    
    private RequestQueue requestQueue;
    private String currentUsername;
    private Long eventId;
    private String eventTitle;
    private Double amount;
    private String clientSecret;
    private String paymentIntentId;
    
    private PaymentSheet paymentSheet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        // Initialize Stripe with publishable key
        // TODO: Replace with your actual Stripe publishable key in ServerConfig
        PaymentConfiguration.init(this, ServerConfig.STRIPE_PUBLISHABLE_KEY);

        // Initialize views
        eventTitleTextView = findViewById(R.id.payment_event_title_tv);
        amountTextView = findViewById(R.id.payment_amount_tv);
        payButton = findViewById(R.id.payment_pay_btn);
        cancelButton = findViewById(R.id.payment_cancel_btn);

        requestQueue = Volley.newRequestQueue(this);

        // Get data from intent
        Intent intent = getIntent();
        if (intent != null) {
            currentUsername = intent.getStringExtra("username");
            eventId = intent.getLongExtra("eventId", -1L);
            eventTitle = intent.getStringExtra("eventTitle");
            amount = intent.getDoubleExtra("amount", 0.0);
        }

        if (currentUsername == null || currentUsername.isEmpty() || eventId == null || eventId <= 0) {
            InAppNotificationHelper.showNotification(this, "❌ Error", "Invalid payment information");
            finish();
            return;
        }

        // Display event info
        if (eventTitle != null) {
            eventTitleTextView.setText(eventTitle);
        }
        amountTextView.setText("$" + String.format("%.2f", amount));

        // Initialize PaymentSheet with result callback
        paymentSheet = new PaymentSheet(
            this,
            this::onPaymentSheetResult
        );

        // Set up click listeners
        payButton.setOnClickListener(v -> createPaymentIntent());
        cancelButton.setOnClickListener(v -> finish());

        // Automatically create payment intent when activity starts
        createPaymentIntent();
    }

    /**
     * Create PaymentIntent with backend
     */
    private void createPaymentIntent() {
        payButton.setEnabled(false);
        payButton.setText("Processing...");

        String url = ServerConfig.PAYMENT_CREATE_INTENT_URL;
        Log.d(TAG, "Creating payment intent - URL: " + url);
        Log.d(TAG, "Request params - username: " + currentUsername + ", eventId: " + eventId);

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("username", currentUsername);
            requestBody.put("eventId", eventId);

            JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            Log.d(TAG, "Payment intent created successfully");
                            clientSecret = response.getString("clientSecret");
                            paymentIntentId = response.getString("paymentIntentId");
                            
                            // Present PaymentSheet
                            presentPaymentSheet();
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing payment intent response", e);
                            showError("Failed to process payment. Please try again.");
                            payButton.setEnabled(true);
                            payButton.setText("Pay");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Error creating payment intent", error);
                        String errorMessage = "Failed to create payment";
                        if (error.networkResponse != null) {
                            try {
                                String responseBody = new String(error.networkResponse.data, java.nio.charset.StandardCharsets.UTF_8);
                                Log.e(TAG, "Error response body: " + responseBody);
                                Log.e(TAG, "Error status code: " + error.networkResponse.statusCode);
                                
                                JSONObject errorJson = new JSONObject(responseBody);
                                // Extract error message, handling both "error" field and quoted strings
                                String extractedError = errorJson.optString("error", "");
                                if (!extractedError.isEmpty()) {
                                    // Remove quotes if present at start/end
                                    extractedError = extractedError.replaceAll("^\"|\"$", "");
                                    // Remove status code prefix if present (e.g., "500 INTERNAL_SERVER_ERROR \"message\"")
                                    // Find the last quoted string in the error message
                                    if (extractedError.contains("\"")) {
                                        int firstQuote = extractedError.indexOf("\"");
                                        int lastQuote = extractedError.lastIndexOf("\"");
                                        if (firstQuote >= 0 && lastQuote > firstQuote) {
                                            extractedError = extractedError.substring(firstQuote + 1, lastQuote);
                                        } else if (firstQuote >= 0) {
                                            // Only one quote found, extract from quote to end
                                            extractedError = extractedError.substring(firstQuote + 1);
                                        }
                                    }
                                    // If still empty or just whitespace, use the original
                                    if (extractedError.trim().isEmpty()) {
                                        extractedError = errorJson.optString("error", errorMessage);
                                    }
                                    errorMessage = extractedError.trim().isEmpty() ? errorMessage : extractedError.trim();
                                } else {
                                    errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing error response", e);
                                if (error.networkResponse != null) {
                                    String responseBody = new String(error.networkResponse.data, java.nio.charset.StandardCharsets.UTF_8);
                                    Log.e(TAG, "Raw error response: " + responseBody);
                                    errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                                }
                            }
                        } else {
                            Log.e(TAG, "No network response - possible connection error");
                            errorMessage = "Unable to connect to server. Please check your connection.";
                        }
                        showError(errorMessage);
                        payButton.setEnabled(true);
                        payButton.setText("Pay");
                    }
                }
            );

            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request", e);
            showError("Error creating payment request");
            payButton.setEnabled(true);
            payButton.setText("Pay");
        }
    }

    /**
     * Present Stripe PaymentSheet
     */
    private void presentPaymentSheet() {
        if (clientSecret == null) {
            showError("Payment not ready. Please try again.");
            return;
        }

        PaymentSheet.Configuration configuration = new PaymentSheet.Configuration.Builder("Occasio Events")
                .build();

        paymentSheet.presentWithPaymentIntent(
            clientSecret,
            configuration
        );
    }

    /**
     * Handle PaymentSheet result
     * Verifies payment status with Stripe before confirming
     */
    private void onPaymentSheetResult(PaymentSheetResult paymentSheetResult) {
        if (paymentSheetResult instanceof PaymentSheetResult.Completed) {
            // Payment succeeded in PaymentSheet
            // Verify with backend before confirming
            verifyAndConfirmPayment();
        } else if (paymentSheetResult instanceof PaymentSheetResult.Canceled) {
            // User canceled
            InAppNotificationHelper.showNotification(this, "💳 Payment", "Payment canceled");
            payButton.setEnabled(true);
            payButton.setText("Pay");
        } else if (paymentSheetResult instanceof PaymentSheetResult.Failed) {
            // Payment failed
            PaymentSheetResult.Failed failed = (PaymentSheetResult.Failed) paymentSheetResult;
            Log.e(TAG, "Payment failed", failed.getError());
            showError("Payment failed: " + failed.getError().getMessage());
            payButton.setEnabled(true);
            payButton.setText("Pay");
        }
    }

    /**
     * Verify payment status with backend before confirming
     * This ensures the payment actually succeeded on Stripe's side
     */
    private void verifyAndConfirmPayment() {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            showError("Payment intent ID not found");
            payButton.setEnabled(true);
            payButton.setText("Pay");
            return;
        }

        // The backend confirmPayment() method already verifies with Stripe
        // So we can proceed with confirmation
        confirmPayment();
    }

    /**
     * Confirm payment with backend after Stripe payment succeeds
     */
    private void confirmPayment() {
        payButton.setEnabled(false);
        payButton.setText("Confirming...");

        String url = ServerConfig.PAYMENT_CONFIRM_URL;

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("username", currentUsername);
            requestBody.put("paymentIntentId", paymentIntentId);

            JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            String status = response.getString("status");
                            if ("SUCCEEDED".equals(status)) {
                                // Payment confirmed, return success with eventId
                                Intent resultIntent = new Intent();
                                resultIntent.putExtra("paymentSuccess", true);
                                resultIntent.putExtra("paymentId", response.getLong("id"));
                                resultIntent.putExtra("eventId", eventId);
                                setResult(RESULT_OK, resultIntent);
                                finish();
                            } else {
                                showError("Payment confirmation failed. Status: " + status);
                                payButton.setEnabled(true);
                                payButton.setText("Pay");
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing confirmation response", e);
                            showError("Failed to confirm payment");
                            payButton.setEnabled(true);
                            payButton.setText("Pay");
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Error confirming payment", error);
                        String errorMessage = "Failed to confirm payment";
                        if (error.networkResponse != null) {
                            try {
                                String responseBody = new String(error.networkResponse.data, java.nio.charset.StandardCharsets.UTF_8);
                                JSONObject errorJson = new JSONObject(responseBody);
                                errorMessage = errorJson.optString("error", errorMessage);
                            } catch (Exception e) {
                                errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                            }
                        }
                        showError(errorMessage);
                        payButton.setEnabled(true);
                        payButton.setText("Pay");
                    }
                }
            );

            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating confirmation request", e);
            showError("Error confirming payment");
            payButton.setEnabled(true);
            payButton.setText("Pay");
        }
    }

    private void showError(String message) {
        InAppNotificationHelper.showNotification(this, "❌ Payment Error", message);
    }
}


