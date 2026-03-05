package com.example.occasio.rewards;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.occasio.R;
import com.example.occasio.services.RewardApiService;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<RewardApiService.RewardTransaction> transactions;

    public TransactionAdapter(List<RewardApiService.RewardTransaction> transactions) {
        this.transactions = transactions;
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        RewardApiService.RewardTransaction transaction = transactions.get(position);
        holder.bind(transaction);
    }

    @Override
    public int getItemCount() {
        return transactions != null ? transactions.size() : 0;
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private TextView descriptionText;
        private TextView pointsText;
        private TextView timestampText;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            descriptionText = itemView.findViewById(R.id.transaction_description);
            pointsText = itemView.findViewById(R.id.transaction_points);
            timestampText = itemView.findViewById(R.id.transaction_timestamp);
        }

        public void bind(RewardApiService.RewardTransaction transaction) {
            descriptionText.setText(transaction.getDescription());
            
            int points = transaction.getPoints();
            if (points > 0) {
                pointsText.setText("+" + points + " pts");
                pointsText.setTextColor(itemView.getContext().getResources().getColor(R.color.accent_fall));
            } else {
                pointsText.setText(String.valueOf(points) + " pts");
                pointsText.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
            }

            String timestamp = transaction.getTimestamp();
            if (timestamp != null && !timestamp.isEmpty()) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                    Date date = inputFormat.parse(timestamp);
                    if (date != null) {
                        timestampText.setText(outputFormat.format(date));
                    } else {
                        timestampText.setText(timestamp);
                    }
                } catch (ParseException e) {
                    timestampText.setText(timestamp);
                }
            } else {
                timestampText.setText("");
            }
        }
    }
}

