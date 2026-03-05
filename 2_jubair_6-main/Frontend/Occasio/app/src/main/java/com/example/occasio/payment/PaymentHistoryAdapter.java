package com.example.occasio.payment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.occasio.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PaymentHistoryAdapter extends RecyclerView.Adapter<PaymentHistoryAdapter.PaymentViewHolder> {

    private List<PaymentHistoryActivity.PaymentHistoryItem> paymentList;

    public PaymentHistoryAdapter(List<PaymentHistoryActivity.PaymentHistoryItem> paymentList) {
        this.paymentList = paymentList;
    }

    @NonNull
    @Override
    public PaymentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_payment_history, parent, false);
        return new PaymentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PaymentViewHolder holder, int position) {
        PaymentHistoryActivity.PaymentHistoryItem payment = paymentList.get(position);
        holder.bind(payment);
    }

    @Override
    public int getItemCount() {
        return paymentList.size();
    }

    class PaymentViewHolder extends RecyclerView.ViewHolder {
        private TextView eventNameTextView;
        private TextView amountTextView;
        private TextView statusTextView;
        private TextView dateTextView;
        private TextView paymentMethodTextView;

        public PaymentViewHolder(@NonNull View itemView) {
            super(itemView);
            eventNameTextView = itemView.findViewById(R.id.payment_history_item_event_name_tv);
            amountTextView = itemView.findViewById(R.id.payment_history_item_amount_tv);
            statusTextView = itemView.findViewById(R.id.payment_history_item_status_tv);
            dateTextView = itemView.findViewById(R.id.payment_history_item_date_tv);
            paymentMethodTextView = itemView.findViewById(R.id.payment_history_item_method_tv);
        }

        public void bind(PaymentHistoryActivity.PaymentHistoryItem payment) {
            eventNameTextView.setText(payment.getEventName());
            amountTextView.setText("$" + String.format("%.2f", payment.getAmount()));
            
            // Status with color
            String status = payment.getStatus();
            statusTextView.setText(status);
            if ("SUCCEEDED".equals(status)) {
                statusTextView.setTextColor(itemView.getContext().getResources().getColor(R.color.button_primary_fall));
            } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
                statusTextView.setTextColor(itemView.getContext().getResources().getColor(R.color.error_color));
            } else {
                statusTextView.setTextColor(itemView.getContext().getResources().getColor(R.color.text_secondary_fall));
            }
            
            // Format date
            String dateStr = formatDate(payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt());
            dateTextView.setText(dateStr);
            
            // Payment method
            String method = payment.getPaymentMethod();
            String last4 = payment.getLast4Digits();
            if (method != null && !method.isEmpty()) {
                String methodText = method.toUpperCase();
                if (last4 != null && !last4.isEmpty()) {
                    methodText += " •••• " + last4;
                }
                paymentMethodTextView.setText(methodText);
                paymentMethodTextView.setVisibility(View.VISIBLE);
            } else {
                paymentMethodTextView.setVisibility(View.GONE);
            }
        }

        private String formatDate(String dateStr) {
            if (dateStr == null || dateStr.isEmpty()) {
                return "Date unknown";
            }
            try {
                // Try ISO 8601 format
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.US);
                Date date = inputFormat.parse(dateStr);
                return outputFormat.format(date);
            } catch (ParseException e) {
                // Return original if parsing fails
                return dateStr;
            }
        }
    }
}

