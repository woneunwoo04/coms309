package com.example.occasio.organization;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
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
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import com.example.occasio.base.BaseOrganizationNavigationActivity;
import com.example.occasio.utils.ServerConfig;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * OrganizationProfileActivity - Edit organization profile and Stripe information
 */
public class OrganizationProfileActivity extends BaseOrganizationNavigationActivity {

    private static final String TAG = "OrganizationProfileActivity";

    private EditText nameEditText;
    private EditText descriptionEditText;
    private EditText contactEmailEditText;
    private EditText contactPhoneEditText;
    private EditText websiteEditText;
    private EditText bankLast4EditText;
    private EditText bankTypeEditText;
    private TextView stripeStatusTextView;
    private Button saveButton;
    private Button backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organization_profile);

        // Setup bottom navigation
        setupBottomNavigation();

        if (currentOrgName == null || currentOrgName.isEmpty()) {
            finish();
            return;
        }

        initializeViews();
        setupClickListeners();
        loadOrganizationData();
    }

    private void initializeViews() {
        nameEditText = findViewById(R.id.org_profile_name_et);
        descriptionEditText = findViewById(R.id.org_profile_description_et);
        contactEmailEditText = findViewById(R.id.org_profile_contact_email_et);
        contactPhoneEditText = findViewById(R.id.org_profile_contact_phone_et);
        websiteEditText = findViewById(R.id.org_profile_website_et);
        bankLast4EditText = findViewById(R.id.org_profile_bank_last4_et);
        bankTypeEditText = findViewById(R.id.org_profile_bank_type_et);
        stripeStatusTextView = findViewById(R.id.org_profile_stripe_status_tv);
        saveButton = findViewById(R.id.org_profile_save_btn);
        backButton = findViewById(R.id.org_profile_back_btn);
    }

    private void setupClickListeners() {
        saveButton.setOnClickListener(v -> saveOrganizationProfile());
        if (backButton != null) {
            backButton.setVisibility(android.view.View.GONE); // Hide back button since we have bottom nav
        }
    }

    private void loadOrganizationData() {
        String url = ServerConfig.ORG_INFO_URL + "/name/" + currentOrgName;

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        // Load organization data
                        nameEditText.setText(response.optString("orgName", ""));
                        descriptionEditText.setText(response.optString("description", ""));
                        contactEmailEditText.setText(response.optString("contactEmail", ""));
                        contactPhoneEditText.setText(response.optString("contactPhone", ""));
                        websiteEditText.setText(response.optString("website", ""));

                        // Load Stripe information
                        boolean stripeConnected = response.optBoolean("stripeConnectEnabled", false);
                        String bankLast4 = response.optString("bankAccountLast4", "");
                        String bankType = response.optString("bankAccountType", "");

                        if (stripeConnected) {
                            stripeStatusTextView.setText("Stripe Connect: Connected ✓");
                            stripeStatusTextView.setTextColor(getResources().getColor(R.color.button_primary_fall));
                        } else {
                            stripeStatusTextView.setText("Stripe Connect: Not Connected");
                            stripeStatusTextView.setTextColor(getResources().getColor(R.color.text_secondary_fall));
                        }

                        if (!bankLast4.isEmpty()) {
                            bankLast4EditText.setText(bankLast4);
                        }
                        if (!bankType.isEmpty()) {
                            bankTypeEditText.setText(bankType);
                        }

                        // Get org ID if not already set
                        if (currentOrgId == null || currentOrgId <= 0) {
                            currentOrgId = response.optLong("id", -1L);
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing organization data", e);
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error loading organization data", error);
                    Toast.makeText(OrganizationProfileActivity.this, "Failed to load organization data", Toast.LENGTH_SHORT).show();
                }
            }
        );

        requestQueue.add(request);
    }

    private void saveOrganizationProfile() {
        if (currentOrgId == null || currentOrgId <= 0) {
            return;
        }

        String url = ServerConfig.ORG_INFO_URL + "/" + currentOrgId;

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("orgName", nameEditText.getText().toString().trim());
            requestBody.put("description", descriptionEditText.getText().toString().trim());
            requestBody.put("contactEmail", contactEmailEditText.getText().toString().trim());
            requestBody.put("contactPhone", contactPhoneEditText.getText().toString().trim());
            requestBody.put("website", websiteEditText.getText().toString().trim());
            requestBody.put("bankAccountLast4", bankLast4EditText.getText().toString().trim());
            requestBody.put("bankAccountType", bankTypeEditText.getText().toString().trim());
            
            // For demo: allow setting stripeConnectEnabled to true
            // In production, this would require actual Stripe Connect setup
            requestBody.put("stripeConnectEnabled", true); // Can be set to true for demo

            JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        
                        // Update stored org name if it changed
                        String newOrgName = nameEditText.getText().toString().trim();
                        if (!newOrgName.isEmpty() && !newOrgName.equals(currentOrgName)) {
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("org_name", newOrgName);
                            editor.apply();
                            currentOrgName = newOrgName;
                        }
                        
                        finish();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG, "Error updating organization profile", error);
                        String errorMessage = "Failed to update profile";
                        if (error.networkResponse != null) {
                            errorMessage += " (Status: " + error.networkResponse.statusCode + ")";
                        }
                    }
                }
            );

            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            Toast.makeText(this, "Error creating update request", Toast.LENGTH_SHORT).show();
        }
    }
}

