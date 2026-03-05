package com.example.occasio.base;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.occasio.R;
import com.example.occasio.organization.OrganizationDashboardActivity;
import com.example.occasio.organization.OrganizationRewardsActivity;
import com.example.occasio.organization.EmailTemplateManagementActivity;
import com.example.occasio.organization.OrganizationProfileActivity;
import com.android.volley.RequestQueue;

public abstract class BaseOrganizationNavigationActivity extends AppCompatActivity {
    
    protected LinearLayout navEvents;
    protected LinearLayout navRewards;
    protected LinearLayout navEmailManagement;
    protected LinearLayout navProfile;
    protected String currentOrgName;
    protected Long currentOrgId;
    protected SharedPreferences sharedPreferences;
    protected RequestQueue requestQueue;
    
    protected static final String PREFS_NAME = "OrgPrefs";
    protected static final String KEY_ORG_NAME = "org_name";
    protected static final String KEY_ORG_ID = "org_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentOrgName = sharedPreferences.getString(KEY_ORG_NAME, "");
        // Restore orgId from SharedPreferences
        long savedOrgId = sharedPreferences.getLong(KEY_ORG_ID, -1L);
        if (savedOrgId > 0) {
            currentOrgId = savedOrgId;
        }
        
        // Get from Intent if available, and save to SharedPreferences
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("orgName")) {
            String orgNameFromIntent = intent.getStringExtra("orgName");
            if (orgNameFromIntent != null && !orgNameFromIntent.isEmpty()) {
                currentOrgName = orgNameFromIntent;
                // Save to SharedPreferences for persistence
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(KEY_ORG_NAME, currentOrgName);
                editor.apply();
            }
            
            if (intent.hasExtra("orgId")) {
                long orgIdFromIntent = intent.getLongExtra("orgId", -1L);
                if (orgIdFromIntent > 0) {
                    currentOrgId = orgIdFromIntent;
                    // Save orgId to SharedPreferences for persistence
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putLong(KEY_ORG_ID, currentOrgId);
                    editor.apply();
                }
            }
        }
        
        // If still empty, try to get from SharedPreferences one more time
        if ((currentOrgName == null || currentOrgName.isEmpty()) && sharedPreferences != null) {
            currentOrgName = sharedPreferences.getString(KEY_ORG_NAME, "");
        }
        
        // Restore orgId from SharedPreferences if still not set
        if ((currentOrgId == null || currentOrgId <= 0) && sharedPreferences != null) {
            long savedOrgId2 = sharedPreferences.getLong(KEY_ORG_ID, -1L);
            if (savedOrgId2 > 0) {
                currentOrgId = savedOrgId2;
            }
        }
        
        requestQueue = com.android.volley.toolbox.Volley.newRequestQueue(this);
        
        // Fetch orgId if not set
        if ((currentOrgId == null || currentOrgId <= 0) && currentOrgName != null && !currentOrgName.isEmpty()) {
            fetchOrganizationId(currentOrgName);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Restore orgId from SharedPreferences if it was lost
        if ((currentOrgId == null || currentOrgId <= 0) && sharedPreferences != null) {
            long savedOrgId = sharedPreferences.getLong(KEY_ORG_ID, -1L);
            if (savedOrgId > 0) {
                currentOrgId = savedOrgId;
            }
        }
        // Restore orgName from SharedPreferences if it was lost
        if ((currentOrgName == null || currentOrgName.isEmpty()) && sharedPreferences != null) {
            String savedOrgName = sharedPreferences.getString(KEY_ORG_NAME, "");
            if (!savedOrgName.isEmpty()) {
                currentOrgName = savedOrgName;
            }
        }
    }
    
    protected void setupBottomNavigation() {
        navEvents = findViewById(R.id.nav_org_events);
        navRewards = findViewById(R.id.nav_org_rewards);
        navEmailManagement = findViewById(R.id.nav_org_email_management);
        navProfile = findViewById(R.id.nav_org_profile);
        
        if (navEvents != null) {
            navEvents.setOnClickListener(v -> navigateToEvents());
        }
        
        if (navRewards != null) {
            navRewards.setOnClickListener(v -> navigateToRewards());
        }
        
        if (navEmailManagement != null) {
            navEmailManagement.setOnClickListener(v -> navigateToEmailManagement());
        }
        
        if (navProfile != null) {
            navProfile.setOnClickListener(v -> navigateToProfile());
        }
        
        // Update selected state
        updateSelectedState();
    }
    
    protected void updateSelectedState() {
        // Reset all nav items
        resetNavItem(navEvents);
        resetNavItem(navRewards);
        resetNavItem(navEmailManagement);
        resetNavItem(navProfile);
        
        // Highlight current page
        if (this instanceof OrganizationDashboardActivity) {
            highlightNavItem(navEvents);
        } else if (this instanceof OrganizationRewardsActivity) {
            highlightNavItem(navRewards);
        } else if (this instanceof EmailTemplateManagementActivity) {
            highlightNavItem(navEmailManagement);
        } else if (this instanceof OrganizationProfileActivity) {
            highlightNavItem(navProfile);
        }
    }
    
    private void resetNavItem(LinearLayout navItem) {
        if (navItem == null) return;
        navItem.setAlpha(0.6f);
        // Find ImageView and TextView in the layout
        for (int i = 0; i < navItem.getChildCount(); i++) {
            View child = navItem.getChildAt(i);
            if (child instanceof ImageView) {
                child.setAlpha(0.6f);
            } else if (child instanceof TextView) {
                child.setAlpha(0.6f);
            }
        }
    }
    
    private void highlightNavItem(LinearLayout navItem) {
        if (navItem == null) return;
        navItem.setAlpha(1.0f);
        // Find ImageView and TextView in the layout
        for (int i = 0; i < navItem.getChildCount(); i++) {
            View child = navItem.getChildAt(i);
            if (child instanceof ImageView) {
                child.setAlpha(1.0f);
            } else if (child instanceof TextView) {
                child.setAlpha(1.0f);
            }
        }
    }
    
    protected void navigateToEvents() {
        if (this instanceof OrganizationDashboardActivity) {
            return; // Already on events page
        }
        Intent intent = new Intent(this, OrganizationDashboardActivity.class);
        intent.putExtra("orgName", currentOrgName);
        if (currentOrgId != null && currentOrgId > 0) {
            intent.putExtra("orgId", currentOrgId);
        }
        startActivity(intent);
        finish();
    }
    
    protected void navigateToRewards() {
        if (this instanceof OrganizationRewardsActivity) {
            return; // Already on rewards page
        }
        Intent intent = new Intent(this, OrganizationRewardsActivity.class);
        intent.putExtra("orgName", currentOrgName);
        if (currentOrgId != null && currentOrgId > 0) {
            intent.putExtra("orgId", currentOrgId);
        }
        startActivity(intent);
        finish();
    }
    
    protected void navigateToEmailManagement() {
        if (this instanceof EmailTemplateManagementActivity) {
            return; // Already on email management page
        }
        Intent intent = new Intent(this, EmailTemplateManagementActivity.class);
        intent.putExtra("orgName", currentOrgName);
        startActivity(intent);
        finish();
    }
    
    protected void navigateToProfile() {
        if (this instanceof OrganizationProfileActivity) {
            return; // Already on profile page
        }
        Intent intent = new Intent(this, OrganizationProfileActivity.class);
        intent.putExtra("orgName", currentOrgName);
        if (currentOrgId != null && currentOrgId > 0) {
            intent.putExtra("orgId", currentOrgId);
        }
        startActivity(intent);
        finish();
    }
    
    private void fetchOrganizationId(String orgName) {
        String url = com.example.occasio.utils.ServerConfig.ORG_INFO_URL + "/name/" + orgName;
        
        com.android.volley.toolbox.JsonObjectRequest request = new com.android.volley.toolbox.JsonObjectRequest(
            com.android.volley.Request.Method.GET,
            url,
            null,
            new com.android.volley.Response.Listener<org.json.JSONObject>() {
                @Override
                public void onResponse(org.json.JSONObject response) {
                    try {
                        if (response.has("id")) {
                            currentOrgId = response.getLong("id");
                            // Save orgId to SharedPreferences for persistence
                            if (currentOrgId > 0 && sharedPreferences != null) {
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putLong(KEY_ORG_ID, currentOrgId);
                                editor.apply();
                            }
                        } else {
                            if (currentOrgId == null || currentOrgId <= 0) {
                                currentOrgId = 1L;
                            }
                        }
                    } catch (org.json.JSONException e) {
                        android.util.Log.e("BaseOrganizationNavigationActivity", "Error parsing organization: " + e.getMessage());
                        if (currentOrgId == null || currentOrgId <= 0) {
                            currentOrgId = 1L;
                        }
                    }
                }
            },
            new com.android.volley.Response.ErrorListener() {
                @Override
                public void onErrorResponse(com.android.volley.VolleyError error) {
                    if (currentOrgId == null || currentOrgId <= 0) {
                        currentOrgId = 1L;
                    }
                    android.util.Log.e("BaseOrganizationNavigationActivity", "Error fetching organization: " + error.getMessage());
                }
            }
        );
        
        request.setRetryPolicy(new com.android.volley.DefaultRetryPolicy(15000, 2, com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        requestQueue.add(request);
    }
}

