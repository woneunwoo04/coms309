package com.example.occasio.organization;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.example.occasio.R;
import com.example.occasio.base.BaseOrganizationNavigationActivity;

public class OrganizationRewardsActivity extends BaseOrganizationNavigationActivity {

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organization_rewards);

        // Setup bottom navigation
        setupBottomNavigation();

        initializeViews();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume(); // This will restore orgId and orgName from SharedPreferences
        // Organization data should now be available
    }

    private void initializeViews() {
        statusText = findViewById(R.id.org_rewards_status_tv);
    }

    private void setupClickListeners() {
        Button backButton = findViewById(R.id.org_rewards_back_btn);
        if (backButton != null) {
            backButton.setVisibility(View.GONE); // Hide back button since we have bottom nav
        }

        Button manageVouchersButton = findViewById(R.id.org_rewards_manage_vouchers_btn);
        if (manageVouchersButton != null) {
            manageVouchersButton.setOnClickListener(v -> {
                Intent manageIntent = new Intent(this, ManageVouchersActivity.class);
                manageIntent.putExtra("orgId", currentOrgId);
                manageIntent.putExtra("orgName", currentOrgName);
                startActivity(manageIntent);
            });
        }
    }
}
