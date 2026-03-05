package com.example.occasio.organization;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import com.example.occasio.services.RewardApiService;
import java.util.List;

public class VoucherManagementAdapter extends RecyclerView.Adapter<VoucherManagementAdapter.VoucherViewHolder> {

    private List<RewardApiService.RewardVoucher> vouchers;
    private VoucherActionListener actionListener;

    public interface VoucherActionListener {
        void onEditClick(RewardApiService.RewardVoucher voucher);
        void onDeleteClick(RewardApiService.RewardVoucher voucher);
        void onToggleActiveClick(RewardApiService.RewardVoucher voucher);
    }

    public VoucherManagementAdapter(List<RewardApiService.RewardVoucher> vouchers, VoucherActionListener listener) {
        this.vouchers = vouchers;
        this.actionListener = listener;
    }

    @NonNull
    @Override
    public VoucherViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_voucher_management, parent, false);
        return new VoucherViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VoucherViewHolder holder, int position) {
        RewardApiService.RewardVoucher voucher = vouchers.get(position);
        holder.bind(voucher);
    }

    @Override
    public int getItemCount() {
        return vouchers != null ? vouchers.size() : 0;
    }

    class VoucherViewHolder extends RecyclerView.ViewHolder {
        private TextView voucherNameText;
        private TextView descriptionText;
        private TextView costPointsText;
        private TextView activeStatusText;
        private Button editButton;
        private Button deleteButton;
        private Button toggleActiveButton;

        public VoucherViewHolder(@NonNull View itemView) {
            super(itemView);
            voucherNameText = itemView.findViewById(R.id.voucher_mgmt_name);
            descriptionText = itemView.findViewById(R.id.voucher_mgmt_description);
            costPointsText = itemView.findViewById(R.id.voucher_mgmt_cost_points);
            activeStatusText = itemView.findViewById(R.id.voucher_mgmt_active_status);
            editButton = itemView.findViewById(R.id.voucher_mgmt_edit_btn);
            deleteButton = itemView.findViewById(R.id.voucher_mgmt_delete_btn);
            toggleActiveButton = itemView.findViewById(R.id.voucher_mgmt_toggle_active_btn);
        }

        public void bind(RewardApiService.RewardVoucher voucher) {
            voucherNameText.setText(voucher.getVoucherName());
            descriptionText.setText(voucher.getDescription());
            costPointsText.setText(voucher.getCostPoints() + " points");

            if (voucher.isActive()) {
                activeStatusText.setText("✅ Active");
                activeStatusText.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
                toggleActiveButton.setText("Deactivate");
            } else {
                activeStatusText.setText("❌ Inactive");
                activeStatusText.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
                toggleActiveButton.setText("Activate");
            }

            editButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onEditClick(voucher);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onDeleteClick(voucher);
                }
            });

            toggleActiveButton.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onToggleActiveClick(voucher);
                }
            });
        }
    }
}

