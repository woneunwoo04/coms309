package com.example.occasio.services;

import android.content.Context;
import android.util.Log;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.example.occasio.api.VolleySingleton;
import com.example.occasio.utils.ServerConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class RewardApiService {
    private static final String TAG = "RewardApiService";
    private static final String BASE_URL = ServerConfig.BASE_URL;
    private RequestQueue requestQueue;
    private Context context;

    public RewardApiService(Context context) {
        this.context = context;
        this.requestQueue = VolleySingleton.getInstance(context).getRequestQueue();
    }

    public interface PointsCallback {
        void onSuccess(int points);
        void onError(String error);
    }

    public interface VouchersCallback {
        void onSuccess(List<RewardVoucher> vouchers);
        void onError(String error);
    }

    public interface RedeemCallback {
        void onSuccess(RewardTransaction transaction);
        void onError(String error);
    }

    public interface UserIdCallback {
        void onSuccess(Long userId);
        void onError(String error);
    }

    public interface TransactionsCallback {
        void onSuccess(List<RewardTransaction> transactions);
        void onError(String error);
    }

    public interface CreateVoucherCallback {
        void onSuccess(RewardVoucher voucher);
        void onError(String error);
    }

    public interface EarnAttendanceCallback {
        void onSuccess(RewardTransaction transaction);
        void onError(String error);
    }

    public interface UpdateVoucherCallback {
        void onSuccess(RewardVoucher voucher);
        void onError(String error);
    }

    public interface DeleteVoucherCallback {
        void onSuccess();
        void onError(String error);
    }

    public static class RewardVoucher {
        private Long id;
        private String voucherName;
        private String description;
        private int costPoints;
        private boolean active;

        public RewardVoucher(JSONObject json) throws JSONException {
            // Use optLong/getLong with fallback to handle missing id (for newly created vouchers)
            if (json.has("id") && !json.isNull("id")) {
            this.id = json.getLong("id");
            } else {
                this.id = null; // New voucher might not have ID yet
            }
            this.voucherName = json.optString("voucherName", "");
            this.description = json.optString("description", "");
            this.costPoints = json.optInt("costPoints", 0);
            this.active = json.optBoolean("active", true);
        }

        public Long getId() { return id; }
        public String getVoucherName() { return voucherName; }
        public String getDescription() { return description; }
        public int getCostPoints() { return costPoints; }
        public boolean isActive() { return active; }
    }

    public static class RewardTransaction {
        private Long id;
        private int points;
        private String description;
        private String timestamp;

        public RewardTransaction(JSONObject json) throws JSONException {
            this.id = json.getLong("id");
            this.points = json.getInt("points");
            this.description = json.optString("description", "");
            this.timestamp = json.optString("timestamp", "");
        }

        public Long getId() { return id; }
        public int getPoints() { return points; }
        public String getDescription() { return description; }
        public String getTimestamp() { return timestamp; }
    }

    public void getUserIdFromUsername(String username, UserIdCallback callback) {
        if (username == null || username.isEmpty()) {
            Log.e(TAG, "Username is null or empty");
            callback.onError("Username is required");
            return;
        }
        
        String url = ServerConfig.BASE_URL + "/user_info/username/" + username;
        Log.d(TAG, "Fetching user ID from: " + url);

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Log.d(TAG, "User ID response: " + response.toString());
                        Long userId = response.getLong("id");
                        Log.d(TAG, "User ID found: " + userId);
                        callback.onSuccess(userId);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing user ID: " + e.getMessage(), e);
                        Log.e(TAG, "Response was: " + response.toString());
                        callback.onError("Error parsing user ID: " + e.getMessage());
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMsg = "Error fetching user ID";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                        try {
                            String responseBody = new String(error.networkResponse.data);
                            Log.e(TAG, "Error response body: " + responseBody);
                        } catch (Exception e) {
                        }
                    }
                    Log.e(TAG, errorMsg + ": " + error.getMessage());
                    if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                        callback.onError("User not found. Please check your username.");
                    } else {
                        callback.onError(errorMsg);
                    }
                }
            }
        );

        if (requestQueue != null) {
        requestQueue.add(request);
        } else {
            Log.e(TAG, "RequestQueue is null, cannot create organization voucher");
            callback.onError("Network service not available");
        }
    }

    public void getUserPoints(Long userInfoId, PointsCallback callback) {
        String url = BASE_URL + "/api/rewards/" + userInfoId + "/points";

        StringRequest request = new StringRequest(
            Request.Method.GET,
            url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    try {
                        int points = Integer.parseInt(response.trim());
                        callback.onSuccess(points);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing points: " + e.getMessage());
                        callback.onError("Error parsing points");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching points: " + error.getMessage());
                    callback.onError("Error fetching points: " + error.getMessage());
                }
            }
        );

        if (requestQueue != null) {
        requestQueue.add(request);
        } else {
            Log.e(TAG, "RequestQueue is null, cannot create organization voucher");
            callback.onError("Network service not available");
        }
    }

    public void getAllVouchers(VouchersCallback callback) {
        String url = BASE_URL + "/api/vouchers";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        Log.d(TAG, "Vouchers API response: " + response.toString());
                        Log.d(TAG, "Number of vouchers received: " + response.length());
                        
                        List<RewardVoucher> vouchers = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject voucherJson = response.getJSONObject(i);
                            RewardVoucher voucher = new RewardVoucher(voucherJson);
                            Log.d(TAG, "Voucher " + i + ": " + voucher.getVoucherName() + ", active: " + voucher.isActive());
                            if (voucher.isActive()) {
                                vouchers.add(voucher);
                            }
                        }
                        
                        Log.d(TAG, "Total active vouchers: " + vouchers.size());
                        callback.onSuccess(vouchers);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing vouchers: " + e.getMessage(), e);
                        callback.onError("Error parsing vouchers: " + e.getMessage());
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMsg = "Error fetching vouchers";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                        try {
                            String responseBody = new String(error.networkResponse.data);
                            Log.e(TAG, "Error response body: " + responseBody);
                        } catch (Exception e) {
                        }
                    }
                    Log.e(TAG, errorMsg + ": " + error.getMessage());
                    callback.onError(errorMsg);
                }
            }
        );

        if (requestQueue != null) {
        requestQueue.add(request);
        } else {
            Log.e(TAG, "RequestQueue is null, cannot create organization voucher");
            callback.onError("Network service not available");
        }
    }

    public void redeemVoucher(Long userInfoId, Long voucherId, RedeemCallback callback) {
        String url = BASE_URL + "/api/rewards/redeem/" + voucherId + "?userInfoId=" + userInfoId;

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        RewardTransaction transaction = new RewardTransaction(response);
                        callback.onSuccess(transaction);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing redeem response: " + e.getMessage());
                        callback.onError("Error parsing transaction data");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error redeeming voucher: " + error.getMessage());
                    String errorMsg = "Error redeeming voucher";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            errorMsg = new String(error.networkResponse.data);
                        } catch (Exception e) {
                        }
                    }
                    callback.onError(errorMsg);
                }
            }
        );

        if (requestQueue != null) {
        requestQueue.add(request);
        } else {
            Log.e(TAG, "RequestQueue is null, cannot create organization voucher");
            callback.onError("Network service not available");
        }
    }

    public void getUserTransactions(Long userInfoId, TransactionsCallback callback) {
        String url = BASE_URL + "/api/rewards/" + userInfoId + "/transactions";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        Log.d(TAG, "Transactions API response: " + response.toString());
                        List<RewardTransaction> transactions = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject transactionJson = response.getJSONObject(i);
                            RewardTransaction transaction = new RewardTransaction(transactionJson);
                            transactions.add(transaction);
                        }
                        Log.d(TAG, "Total transactions: " + transactions.size());
                        callback.onSuccess(transactions);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing transactions: " + e.getMessage(), e);
                        callback.onError("Error parsing transactions: " + e.getMessage());
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching transactions: " + error.getMessage());
                    callback.onError("Error fetching transactions: " + error.getMessage());
                }
            }
        );

        if (requestQueue != null) {
        requestQueue.add(request);
        } else {
            Log.e(TAG, "RequestQueue is null, cannot create organization voucher");
            callback.onError("Network service not available");
        }
    }

    public void createVoucher(String voucherName, String description, int costPoints, boolean active, CreateVoucherCallback callback) {
        String url = BASE_URL + "/api/vouchers";

        JSONObject voucherData = new JSONObject();
        try {
            voucherData.put("voucherName", voucherName);
            voucherData.put("description", description);
            voucherData.put("costPoints", costPoints);
            voucherData.put("active", active);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating voucher JSON: " + e.getMessage());
            callback.onError("Error creating voucher data");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            voucherData,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Log.d(TAG, "Create voucher response: " + response.toString());
                        RewardVoucher voucher = new RewardVoucher(response);
                        callback.onSuccess(voucher);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing created voucher: " + e.getMessage());
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error creating voucher: " + error.getMessage());
                    String errorMsg = "Error creating voucher";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                        try {
                            String responseBody = new String(error.networkResponse.data);
                            Log.e(TAG, "Error response body: " + responseBody);
                        } catch (Exception e) {
                        }
                    }
                    callback.onError(errorMsg);
                }
            }
        );

        if (requestQueue != null) {
        requestQueue.add(request);
        } else {
            Log.e(TAG, "RequestQueue is null, cannot create organization voucher");
            callback.onError("Network service not available");
        }
    }

    public void earnEventAttendancePoints(Long userInfoId, Long eventId, int points, EarnAttendanceCallback callback) {
        String url = BASE_URL + "/api/rewards/earn/attendance?userInfoId=" + userInfoId + "&eventId=" + eventId + "&points=" + points;

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        RewardTransaction transaction = new RewardTransaction(response);
                        callback.onSuccess(transaction);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing earn attendance response: " + e.getMessage());
                        callback.onError("Error parsing transaction data");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error earning attendance points: " + error.getMessage());
                    String errorMsg = "Error earning attendance points";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            errorMsg = new String(error.networkResponse.data);
                        } catch (Exception e) {
                        }
                    }
                    callback.onError(errorMsg);
                }
            }
        );

        if (requestQueue != null) {
        requestQueue.add(request);
        } else {
            Log.e(TAG, "RequestQueue is null, cannot create organization voucher");
            callback.onError("Network service not available");
        }
    }

    // Organization Voucher Management Methods

    public void getOrganizationVouchers(Long orgId, VouchersCallback callback) {
        String url = BASE_URL + "/api/organizations/" + orgId + "/vouchers";

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    try {
                        Log.d(TAG, "Organization vouchers API response: " + response.toString());
                        Log.d(TAG, "Number of vouchers received: " + response.length());
                        
                        List<RewardVoucher> vouchers = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject voucherJson = response.getJSONObject(i);
                            RewardVoucher voucher = new RewardVoucher(voucherJson);
                            vouchers.add(voucher);
                        }
                        
                        Log.d(TAG, "Total organization vouchers: " + vouchers.size());
                        callback.onSuccess(vouchers);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing organization vouchers: " + e.getMessage(), e);
                        callback.onError("Error parsing vouchers: " + e.getMessage());
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    String errorMsg = "Error fetching organization vouchers";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                        try {
                            String responseBody = new String(error.networkResponse.data);
                            Log.e(TAG, "Error response body: " + responseBody);
                        } catch (Exception e) {
                        }
                    }
                    Log.e(TAG, errorMsg + ": " + error.getMessage());
                    callback.onError(errorMsg);
                }
            }
        );

        if (requestQueue != null) {
        requestQueue.add(request);
        } else {
            Log.e(TAG, "RequestQueue is null, cannot create voucher");
            callback.onError("Network service not available");
        }
    }

    public void createOrganizationVoucher(Long orgId, String voucherName, String description, int costPoints, boolean active, CreateVoucherCallback callback) {
        // Validate orgId before constructing URL
        if (orgId == null || orgId <= 0) {
            Log.e(TAG, "Invalid organization ID: " + orgId);
            callback.onError("Invalid organization ID");
            return;
        }
        
        // Validate input parameters
        if (voucherName == null || voucherName.trim().isEmpty()) {
            callback.onError("Voucher name cannot be empty");
            return;
        }
        if (description == null || description.trim().isEmpty()) {
            callback.onError("Description cannot be empty");
            return;
        }
        if (costPoints <= 0) {
            callback.onError("Cost points must be greater than 0");
            return;
        }
        
        String url = BASE_URL + "/api/organizations/" + orgId + "/vouchers";

        JSONObject voucherData = new JSONObject();
        try {
            voucherData.put("voucherName", voucherName);
            voucherData.put("description", description);
            voucherData.put("costPoints", costPoints);
            voucherData.put("active", active);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating voucher JSON: " + e.getMessage());
            callback.onError("Error creating voucher data");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            voucherData,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Log.d(TAG, "Create organization voucher response: " + response.toString());
                        if (response == null || response.length() == 0) {
                            callback.onError("Empty response from server");
                            return;
                        }
                        RewardVoucher voucher = new RewardVoucher(response);
                        callback.onSuccess(voucher);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing created voucher: " + e.getMessage());
                        e.printStackTrace();
                        callback.onError("Error parsing response: " + e.getMessage());
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected error parsing voucher: " + e.getMessage());
                        e.printStackTrace();
                        callback.onError("Unexpected error: " + e.getMessage());
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error creating organization voucher: " + error.getMessage());
                    String errorMsg = "Error creating voucher";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                        try {
                            String responseBody = new String(error.networkResponse.data);
                            Log.e(TAG, "Error response body: " + responseBody);
                        } catch (Exception e) {
                        }
                    }
                    callback.onError(errorMsg);
                }
            }
        );

        if (requestQueue != null) {
        requestQueue.add(request);
        } else {
            Log.e(TAG, "RequestQueue is null, cannot create organization voucher");
            callback.onError("Network service not available");
        }
    }

    public void updateVoucher(Long voucherId, String voucherName, String description, int costPoints, boolean active, UpdateVoucherCallback callback) {
        String url = BASE_URL + "/api/organizations/vouchers/" + voucherId;

        JSONObject voucherData = new JSONObject();
        try {
            voucherData.put("voucherName", voucherName);
            voucherData.put("description", description);
            voucherData.put("costPoints", costPoints);
            voucherData.put("active", active);
        } catch (JSONException e) {
            Log.e(TAG, "Error creating voucher JSON: " + e.getMessage());
            callback.onError("Error creating voucher data");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.PUT,
            url,
            voucherData,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        Log.d(TAG, "Update voucher response: " + response.toString());
                        RewardVoucher voucher = new RewardVoucher(response);
                        callback.onSuccess(voucher);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing updated voucher: " + e.getMessage());
                        callback.onError("Error parsing response");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error updating voucher: " + error.getMessage());
                    String errorMsg = "Error updating voucher";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                        try {
                            String responseBody = new String(error.networkResponse.data);
                            Log.e(TAG, "Error response body: " + responseBody);
                        } catch (Exception e) {
                        }
                    }
                    callback.onError(errorMsg);
                }
            }
        );

        if (requestQueue != null) {
        requestQueue.add(request);
        } else {
            Log.e(TAG, "RequestQueue is null, cannot create organization voucher");
            callback.onError("Network service not available");
        }
    }

    public void deleteVoucher(Long voucherId, DeleteVoucherCallback callback) {
        String url = BASE_URL + "/api/organizations/vouchers/" + voucherId;

        StringRequest request = new StringRequest(
            Request.Method.DELETE,
            url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.d(TAG, "Voucher deleted successfully");
                    callback.onSuccess();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error deleting voucher: " + error.getMessage());
                    String errorMsg = "Error deleting voucher";
                    if (error.networkResponse != null) {
                        errorMsg += " (Status: " + error.networkResponse.statusCode + ")";
                        try {
                            String responseBody = new String(error.networkResponse.data);
                            Log.e(TAG, "Error response body: " + responseBody);
                        } catch (Exception e) {
                        }
                    }
                    callback.onError(errorMsg);
                }
            }
        );

        if (requestQueue != null) {
        requestQueue.add(request);
        } else {
            Log.e(TAG, "RequestQueue is null, cannot create organization voucher");
            callback.onError("Network service not available");
        }
    }
}

