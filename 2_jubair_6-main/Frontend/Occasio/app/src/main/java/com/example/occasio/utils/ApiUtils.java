package com.example.occasio.utils;
import com.example.occasio.R;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

public class ApiUtils {
    
    /**
     * Check if network is available
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    
    /**
     * Validate email format
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches();
    }
    
    // Removed isValidUsername - never used in codebase
    // Removed isValidPassword - never used in codebase
    
    // Removed empty showToast and showLongToast methods - they had no implementation
    
    /**
     * Get error message from network response
     */
    public static String getErrorMessage(int statusCode) {
        switch (statusCode) {
            case 400:
                return "Bad request. Please check your input.";
            case 401:
                return "Unauthorized. Please login again.";
            case 403:
                return "Forbidden. You don't have permission to perform this action.";
            case 404:
                return "Not found. The requested resource was not found.";
            case 500:
                return "Server error. Please try again later.";
            case 503:
                return "Service unavailable. Please try again later.";
            default:
                return "Network error occurred. Please check your connection.";
        }
    }
}
