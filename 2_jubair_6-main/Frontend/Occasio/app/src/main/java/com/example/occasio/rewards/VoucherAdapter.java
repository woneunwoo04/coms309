package com.example.occasio.rewards;

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

public class VoucherAdapter extends RecyclerView.Adapter<VoucherAdapter.VoucherViewHolder> {

    private List<RewardApiService.RewardVoucher> vouchers;
    private int userPoints;
    private OnRedeemClickListener redeemClickListener;

    public interface OnRedeemClickListener {
        void onRedeemClick(RewardApiService.RewardVoucher voucher);
    }

    public VoucherAdapter(List<RewardApiService.RewardVoucher> vouchers, int userPoints) {
        this.vouchers = vouchers;
        this.userPoints = userPoints;
    }

    public void setOnRedeemClickListener(OnRedeemClickListener listener) {
        this.redeemClickListener = listener;
    }

    public void setUserPoints(int points) {
        this.userPoints = points;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VoucherViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_voucher, parent, false);
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
        private Button redeemButton;

        public VoucherViewHolder(@NonNull View itemView) {
            super(itemView);
            voucherNameText = itemView.findViewById(R.id.voucher_name);
            descriptionText = itemView.findViewById(R.id.voucher_description);
            costPointsText = itemView.findViewById(R.id.voucher_cost_points);
            redeemButton = itemView.findViewById(R.id.redeem_button);
        }

        public void bind(RewardApiService.RewardVoucher voucher) {
            voucherNameText.setText(voucher.getVoucherName());
            descriptionText.setText(voucher.getDescription());
            costPointsText.setText(voucher.getCostPoints() + " points");

            boolean canRedeem = userPoints >= voucher.getCostPoints();
            redeemButton.setEnabled(canRedeem);
            redeemButton.setAlpha(canRedeem ? 1.0f : 0.5f);
            redeemButton.setText(canRedeem ? "Redeem" : "Insufficient Points");

            redeemButton.setOnClickListener(v -> {
                if (canRedeem && redeemClickListener != null) {
                    redeemClickListener.onRedeemClick(voucher);
                }
            });
        }
    }
}


