package com.example.occasio.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Network utility class for handling network operations and errors
 */
public class NetworkUtils {
    private static final String TAG = "NetworkUtils";

    /**
     * Check if the device has an active internet connection
     */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }

    /**
     * Apply standard retry policy to a request
     */
    public static void applyRetryPolicy(Request<?> request) {
        request.setRetryPolicy(new DefaultRetryPolicy(
            ServerConfig.DEFAULT_TIMEOUT_MS,
            ServerConfig.DEFAULT_MAX_RETRIES,
            ServerConfig.DEFAULT_BACKOFF_MULT
        ));
    }

    /**
     * Parse error message from VolleyError
     */
    public static String getErrorMessage(VolleyError error) {
        if (error == null) {
            return "Unknown error occurred";
        }

        // Check for network errors
        if (error instanceof NetworkError) {
            return "Network error. Please check your internet connection.";
        } else if (error instanceof NoConnectionError) {
            return "No internet connection available.";
        } else if (error instanceof TimeoutError) {
            return "Connection timeout. Please try again.";
        } else if (error instanceof ServerError) {
            return parseServerError(error);
        }

        // Check for specific error messages
        if (error.getMessage() != null) {
            String message = error.getMessage();
            if (message.contains("UnknownHostException") || 
                message.contains("Unable to resolve host") ||
                message.contains("No address associated")) {
                return "Cannot connect to server. Please check your network.";
            }
            if (message.contains("timeout") || message.contains("Timeout")) {
                return "Connection timeout. Please try again.";
            }
        }

        // Check network response
        if (error.networkResponse != null) {
            int statusCode = error.networkResponse.statusCode;
            switch (statusCode) {
                case 400:
                    return "Bad request. Please check your input.";
                case 401:
                    return "Invalid credentials.";
                case 403:
                    return "Access denied.";
                case 404:
                    return "Resource not found.";
                case 409:
                    return "Conflict. The resource already exists.";
                case 500:
                    return "Server error. Please try again later.";
                case 503:
                    return "Service temporarily unavailable.";
                default:
                    return "Server error (Code: " + statusCode + ")";
            }
        }

        return "An error occurred. Please try again.";
    }

    /**
     * Parse server error response
     */
    private static String parseServerError(VolleyError error) {
        if (error.networkResponse != null && error.networkResponse.data != null) {
            try {
                String errorJson = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                JSONObject errorObject = new JSONObject(errorJson);
                
                // Try different common error message fields
                if (errorObject.has("message")) {
                    return errorObject.getString("message");
                }
                if (errorObject.has("error")) {
                    return errorObject.getString("error");
                }
                if (errorObject.has("detail")) {
                    return errorObject.getString("detail");
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing server error response", e);
            }
        }
        
        return "Server error occurred. Please try again later.";
    }

    /**
     * Check if error is due to network connectivity
     */
    public static boolean isNetworkError(VolleyError error) {
        if (error instanceof NetworkError || 
            error instanceof NoConnectionError || 
            error instanceof TimeoutError) {
            return true;
        }
        
        if (error.getMessage() != null) {
            String message = error.getMessage();
            return message.contains("UnknownHostException") || 
                   message.contains("Unable to resolve host") ||
                   message.contains("No address associated") ||
                   message.contains("timeout") ||
                   message.contains("Timeout");
        }
        
        return false;
    }

    /**
     * Check if the error indicates server is unreachable
     */
    public static boolean isServerUnreachable(VolleyError error) {
        if (error.networkResponse != null) {
            int statusCode = error.networkResponse.statusCode;
            return statusCode == 503 || statusCode >= 500;
        }
        
        return isNetworkError(error);
    }

    /**
     * Get appropriate log level for error
     */
    public static void logError(String tag, String message, VolleyError error) {
        if (isNetworkError(error)) {
            Log.w(tag, message + ": " + getErrorMessage(error));
        } else {
            Log.e(tag, message + ": " + getErrorMessage(error), error);
        }
    }
}
