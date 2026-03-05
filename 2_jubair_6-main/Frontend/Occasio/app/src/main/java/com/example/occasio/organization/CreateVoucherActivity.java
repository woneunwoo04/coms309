package com.example.occasio.organization;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.occasio.R;
import com.example.occasio.services.RewardApiService;

public class CreateVoucherActivity extends AppCompatActivity {

    private EditText voucherNameEditText;
    private EditText descriptionEditText;
    private EditText costPointsEditText;
    private CheckBox activeCheckBox;
    private Button createButton;
    private Button cancelButton;
    private RewardApiService rewardApiService;
    private Long organizationId;
    private String organizationName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_voucher);

        Intent intent = getIntent();
        if (intent != null) {
            // Use getLongExtra with proper default handling
            long orgIdValue = intent.getLongExtra("orgId", -1L);
            if (orgIdValue > 0) {
                organizationId = orgIdValue;
            }
            organizationName = intent.getStringExtra("orgName");
        }

        // Validate organizationId
        if (organizationId == null || organizationId <= 0) {
            finish();
            return;
        }

        initializeViews();
        setupClickListeners();
        
        rewardApiService = new RewardApiService(this);
    }

    private void initializeViews() {
        voucherNameEditText = findViewById(R.id.create_voucher_name_et);
        descriptionEditText = findViewById(R.id.create_voucher_description_et);
        costPointsEditText = findViewById(R.id.create_voucher_cost_points_et);
        activeCheckBox = findViewById(R.id.create_voucher_active_cb);
        createButton = findViewById(R.id.create_voucher_submit_btn);
        cancelButton = findViewById(R.id.create_voucher_cancel_btn);
        
        // Check if views are null to prevent crashes
        if (voucherNameEditText == null || descriptionEditText == null || 
            costPointsEditText == null || activeCheckBox == null || 
            createButton == null || cancelButton == null) {
            finish();
            return;
        }
    }

    private void setupClickListeners() {
        if (createButton != null) {
        createButton.setOnClickListener(v -> createVoucher());
        }
        if (cancelButton != null) {
        cancelButton.setOnClickListener(v -> finish());
        }
    }

    private void createVoucher() {
        // Validate views are not null
        if (voucherNameEditText == null || descriptionEditText == null || 
            costPointsEditText == null || activeCheckBox == null || createButton == null) {
            return;
        }
        
        String voucherName = voucherNameEditText.getText().toString().trim();
        String description = descriptionEditText.getText().toString().trim();
        String costPointsStr = costPointsEditText.getText().toString().trim();

        if (voucherName.isEmpty()) {
            voucherNameEditText.requestFocus();
            return;
        }

        if (description.isEmpty()) {
            descriptionEditText.requestFocus();
            return;
        }

        if (costPointsStr.isEmpty()) {
            costPointsEditText.requestFocus();
            return;
        }

        int costPoints;
        try {
            costPoints = Integer.parseInt(costPointsStr);
            if (costPoints <= 0) {
                costPointsEditText.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            costPointsEditText.requestFocus();
            return;
        }

        boolean active = activeCheckBox.isChecked();

        createButton.setEnabled(false);

        // Validate organizationId again before making API call
        if (organizationId == null || organizationId == -1) {
            createButton.setEnabled(true);
            return;
        }
        
        if (rewardApiService == null) {
            createButton.setEnabled(true);
            return;
        }

            // Use organization-specific endpoint
        try {
            rewardApiService.createOrganizationVoucher(organizationId, voucherName, description, costPoints, active, 
                new RewardApiService.CreateVoucherCallback() {
                    @Override
                    public void onSuccess(com.example.occasio.services.RewardApiService.RewardVoucher voucher) {
                        try {
                        runOnUiThread(() -> {
                            createButton.setEnabled(true);
                            finish();
                        });
                        } catch (Exception e) {
                            android.util.Log.e("CreateVoucherActivity", "Error in onSuccess: " + e.getMessage());
                            e.printStackTrace();
                    runOnUiThread(() -> {
                        createButton.setEnabled(true);
                        finish();
                    });
                        }
                }

                @Override
                public void onError(String error) {
                        try {
                    runOnUiThread(() -> {
                        createButton.setEnabled(true);
                        Toast.makeText(CreateVoucherActivity.this, "❌ Error: " + error, Toast.LENGTH_LONG).show();
                    });
                        } catch (Exception e) {
                            android.util.Log.e("CreateVoucherActivity", "Error in onError: " + e.getMessage());
                            e.printStackTrace();
                            runOnUiThread(() -> {
                                createButton.setEnabled(true);
                                Toast.makeText(CreateVoucherActivity.this, "❌ Error creating voucher", Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
        } catch (Exception e) {
            android.util.Log.e("CreateVoucherActivity", "Error calling createOrganizationVoucher: " + e.getMessage());
            e.printStackTrace();
            createButton.setEnabled(true);
        }
    }
}

