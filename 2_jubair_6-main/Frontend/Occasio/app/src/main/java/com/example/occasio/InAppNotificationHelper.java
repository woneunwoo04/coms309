package com.example.occasio.utils;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.occasio.R;
import android.os.Handler;
import android.os.Looper;

public class InAppNotificationHelper {
    
    public static void showNotification(AppCompatActivity activity, String title, String message) {
        if (activity == null || activity.isFinishing()) {
            android.util.Log.w("InAppNotificationHelper", "Cannot show notification - activity is null or finishing");
            return;
        }
        
        try {
            android.util.Log.d("InAppNotificationHelper", "🎯 Attempting to show popup: " + title);
            
            // Create a custom in-app notification view
            LayoutInflater inflater = LayoutInflater.from(activity);
            View notificationView = inflater.inflate(R.layout.in_app_notification, null);
            
            TextView titleView = notificationView.findViewById(R.id.notification_title);
            TextView messageView = notificationView.findViewById(R.id.notification_message);
            Button closeButton = notificationView.findViewById(R.id.notification_close);
            
            if (titleView == null || messageView == null || closeButton == null) {
                android.util.Log.e("InAppNotificationHelper", "❌ Layout views not found - check in_app_notification.xml");
                return;
            }
            
            titleView.setText(title);
            messageView.setText(message);
            
            // Create a custom dialog for the notification
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setView(notificationView);
            builder.setCancelable(true);
            
            AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
            
            closeButton.setOnClickListener(v -> {
                android.util.Log.d("InAppNotificationHelper", "✅ User closed notification");
                dialog.dismiss();
            });
            
            // Auto-dismiss after 8 seconds (increased from 5)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (dialog.isShowing() && !activity.isFinishing() && activity.getWindow() != null) {
                        android.util.Log.d("InAppNotificationHelper", "⏰ Auto-dismissing notification");
                        dialog.dismiss();
                    }
                } catch (Exception e) {
                    android.util.Log.w("InAppNotificationHelper", "Could not auto-dismiss notification: " + e.getMessage());
                }
            }, 8000);
            
            dialog.show();
            android.util.Log.d("InAppNotificationHelper", "✅ Popup notification shown successfully");
        } catch (Exception e) {
            // Log error but don't fallback to Toast - always use XML
            android.util.Log.e("InAppNotificationHelper", "❌ Error showing notification: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

