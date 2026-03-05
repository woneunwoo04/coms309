package com.example.occasio.organization;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import com.example.occasio.services.RewardApiService;
import java.util.ArrayList;
import java.util.List;

public class ManageVouchersActivity extends AppCompatActivity {

    private RecyclerView vouchersRecyclerView;
    private View vouchersEmptyState;
    private TextView statusText;
    private Button createVoucherButton;
    private Button refreshButton;
    private Button backButton;
    
    private VoucherManagementAdapter voucherAdapter;
    private RewardApiService rewardApiService;
    private Long organizationId;
    private String organizationName;
    private List<RewardApiService.RewardVoucher> vouchers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_vouchers);

        Intent intent = getIntent();
        if (intent != null) {
            organizationId = intent.getLongExtra("orgId", -1);
            organizationName = intent.getStringExtra("orgName");
        }

        if (organizationId == null || organizationId == -1) {
            finish();
            return;
        }

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        
        rewardApiService = new RewardApiService(this);
        loadVouchers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadVouchers();
    }

    private void initializeViews() {
        vouchersRecyclerView = findViewById(R.id.manage_vouchers_recycler_view);
        vouchersEmptyState = findViewById(R.id.manage_vouchers_empty_state);
        statusText = findViewById(R.id.manage_vouchers_status_tv);
        createVoucherButton = findViewById(R.id.manage_vouchers_create_btn);
        refreshButton = findViewById(R.id.manage_vouchers_refresh_btn);
        backButton = findViewById(R.id.manage_vouchers_back_btn);
    }

    private void setupRecyclerView() {
        voucherAdapter = new VoucherManagementAdapter(vouchers, new VoucherManagementAdapter.VoucherActionListener() {
            @Override
            public void onEditClick(RewardApiService.RewardVoucher voucher) {
                editVoucher(voucher);
            }

            @Override
            public void onDeleteClick(RewardApiService.RewardVoucher voucher) {
                showDeleteConfirmation(voucher);
            }

            @Override
            public void onToggleActiveClick(RewardApiService.RewardVoucher voucher) {
                toggleVoucherActive(voucher);
            }
        });
        
        vouchersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        vouchersRecyclerView.setAdapter(voucherAdapter);
    }

    private void setupClickListeners() {
        createVoucherButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateVoucherActivity.class);
            intent.putExtra("orgId", organizationId);
            intent.putExtra("orgName", organizationName);
            startActivity(intent);
        });

        refreshButton.setOnClickListener(v -> {
            loadVouchers();
        });

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrganizationRewardsActivity.class);
            intent.putExtra("orgId", organizationId);
            intent.putExtra("orgName", organizationName);
            startActivity(intent);
            finish();
        });
    }

    private void loadVouchers() {
        if (organizationId == null || organizationId == -1) {
            return;
        }

        statusText.setText("Loading vouchers...");
        rewardApiService.getOrganizationVouchers(organizationId, new RewardApiService.VouchersCallback() {
            @Override
            public void onSuccess(List<RewardApiService.RewardVoucher> voucherList) {
                runOnUiThread(() -> {
                    vouchers.clear();
                    vouchers.addAll(voucherList);
                    voucherAdapter.notifyDataSetChanged();
                    
                    if (vouchers.isEmpty()) {
                        vouchersRecyclerView.setVisibility(View.GONE);
                        vouchersEmptyState.setVisibility(View.VISIBLE);
                        statusText.setText("No vouchers found. Create your first voucher!");
                    } else {
                        vouchersRecyclerView.setVisibility(View.VISIBLE);
                        vouchersEmptyState.setVisibility(View.GONE);
                        statusText.setText("You have " + vouchers.size() + " voucher(s)");
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    android.util.Log.e("ManageVouchersActivity", "Error loading vouchers: " + error);
                    vouchersRecyclerView.setVisibility(View.GONE);
                    vouchersEmptyState.setVisibility(View.VISIBLE);
                    statusText.setText("Error loading vouchers: " + error);
                    Toast.makeText(ManageVouchersActivity.this, "Failed to load vouchers: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void editVoucher(RewardApiService.RewardVoucher voucher) {
        Intent intent = new Intent(this, EditVoucherActivity.class);
        intent.putExtra("voucherId", voucher.getId());
        intent.putExtra("voucherName", voucher.getVoucherName());
        intent.putExtra("description", voucher.getDescription());
        intent.putExtra("costPoints", voucher.getCostPoints());
        intent.putExtra("active", voucher.isActive());
        intent.putExtra("orgId", organizationId);
        intent.putExtra("orgName", organizationName);
        startActivity(intent);
    }

    private void showDeleteConfirmation(RewardApiService.RewardVoucher voucher) {
        new AlertDialog.Builder(this)
            .setTitle("🗑️ Delete Voucher")
            .setMessage("Are you sure you want to delete \"" + voucher.getVoucherName() + "\"?\n\nThis action cannot be undone.")
            .setPositiveButton("Yes, Delete", (dialog, which) -> deleteVoucher(voucher))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void deleteVoucher(RewardApiService.RewardVoucher voucher) {
        rewardApiService.deleteVoucher(voucher.getId(), new RewardApiService.DeleteVoucherCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    loadVouchers();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ManageVouchersActivity.this, "❌ Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void toggleVoucherActive(RewardApiService.RewardVoucher voucher) {
        boolean newActiveState = !voucher.isActive();
        
        rewardApiService.updateVoucher(
            voucher.getId(),
            voucher.getVoucherName(),
            voucher.getDescription(),
            voucher.getCostPoints(),
            newActiveState,
            new RewardApiService.UpdateVoucherCallback() {
                @Override
                public void onSuccess(RewardApiService.RewardVoucher updatedVoucher) {
                    runOnUiThread(() -> {
                            android.util.Log.d("ManageVouchersActivity", "✅ Voucher " + (newActiveState ? "activated" : "deactivated"));
                        loadVouchers();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(ManageVouchersActivity.this, "❌ Error: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            }
        );
    }
}

