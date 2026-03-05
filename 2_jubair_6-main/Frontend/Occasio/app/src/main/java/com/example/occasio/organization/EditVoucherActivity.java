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

public class EditVoucherActivity extends AppCompatActivity {

    private EditText voucherNameEditText;
    private EditText descriptionEditText;
    private EditText costPointsEditText;
    private CheckBox activeCheckBox;
    private Button updateButton;
    private Button cancelButton;
    
    private RewardApiService rewardApiService;
    private Long voucherId;
    private Long organizationId;
    private String organizationName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_voucher);

        Intent intent = getIntent();
        if (intent != null) {
            // Use getLongExtra with proper default handling
            long voucherIdValue = intent.getLongExtra("voucherId", -1L);
            if (voucherIdValue > 0) {
                voucherId = voucherIdValue;
            }
            long orgIdValue = intent.getLongExtra("orgId", -1L);
            if (orgIdValue > 0) {
                organizationId = orgIdValue;
            }
            organizationName = intent.getStringExtra("orgName");
        }

        if (voucherId == null || voucherId <= 0) {
            finish();
            return;
        }

        initializeViews();
        loadVoucherData();
        setupClickListeners();
        
        rewardApiService = new RewardApiService(this);
    }

    private void initializeViews() {
        voucherNameEditText = findViewById(R.id.edit_voucher_name_et);
        descriptionEditText = findViewById(R.id.edit_voucher_description_et);
        costPointsEditText = findViewById(R.id.edit_voucher_cost_points_et);
        activeCheckBox = findViewById(R.id.edit_voucher_active_cb);
        updateButton = findViewById(R.id.edit_voucher_update_btn);
        cancelButton = findViewById(R.id.edit_voucher_cancel_btn);
        
        // Check if views are null to prevent crashes
        if (voucherNameEditText == null || descriptionEditText == null || 
            costPointsEditText == null || activeCheckBox == null || 
            updateButton == null || cancelButton == null) {
            finish();
            return;
        }
    }

    private void loadVoucherData() {
        Intent intent = getIntent();
        if (intent != null) {
            if (voucherNameEditText != null) {
            voucherNameEditText.setText(intent.getStringExtra("voucherName"));
            }
            if (descriptionEditText != null) {
            descriptionEditText.setText(intent.getStringExtra("description"));
            }
            if (costPointsEditText != null) {
            costPointsEditText.setText(String.valueOf(intent.getIntExtra("costPoints", 0)));
            }
            if (activeCheckBox != null) {
            activeCheckBox.setChecked(intent.getBooleanExtra("active", true));
            }
        }
    }

    private void setupClickListeners() {
        if (updateButton != null) {
        updateButton.setOnClickListener(v -> updateVoucher());
        }
        if (cancelButton != null) {
        cancelButton.setOnClickListener(v -> finish());
        }
    }

    private void updateVoucher() {
        // Validate views are not null
        if (voucherNameEditText == null || descriptionEditText == null || 
            costPointsEditText == null || activeCheckBox == null || updateButton == null) {
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

        // Validate voucherId
        if (voucherId == null || voucherId <= 0) {
            return;
        }
        
        if (rewardApiService == null) {
            return;
        }

        updateButton.setEnabled(false);

        try {
        rewardApiService.updateVoucher(voucherId, voucherName, description, costPoints, active, 
            new RewardApiService.UpdateVoucherCallback() {
                @Override
                public void onSuccess(RewardApiService.RewardVoucher voucher) {
                        try {
                            runOnUiThread(() -> {
                                updateButton.setEnabled(true);
                                finish();
                            });
                        } catch (Exception e) {
                            android.util.Log.e("EditVoucherActivity", "Error in onSuccess: " + e.getMessage());
                            e.printStackTrace();
                    runOnUiThread(() -> {
                        updateButton.setEnabled(true);
                        finish();
                    });
                        }
                }

                @Override
                public void onError(String error) {
                        try {
                    runOnUiThread(() -> {
                        updateButton.setEnabled(true);
                        Toast.makeText(EditVoucherActivity.this, "❌ Error: " + error, Toast.LENGTH_LONG).show();
                    });
                        } catch (Exception e) {
                            android.util.Log.e("EditVoucherActivity", "Error in onError: " + e.getMessage());
                            e.printStackTrace();
                            runOnUiThread(() -> {
                                updateButton.setEnabled(true);
                                Toast.makeText(EditVoucherActivity.this, "❌ Error updating voucher", Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
        } catch (Exception e) {
            android.util.Log.e("EditVoucherActivity", "Error calling updateVoucher: " + e.getMessage());
            e.printStackTrace();
            updateButton.setEnabled(true);
        }
    }
}

