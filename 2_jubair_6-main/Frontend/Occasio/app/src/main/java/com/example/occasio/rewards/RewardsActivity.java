package com.example.occasio.rewards;

import android.content.Intent;
import android.content.SharedPreferences;
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
import com.example.occasio.service.WebSocketService;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity for managing user rewards, points, vouchers, and transactions.
 * 
 * <p>This activity provides functionality to:
 * <ul>
 *   <li>Display current reward points balance</li>
 *   <li>Browse available vouchers from organizations</li>
 *   <li>Redeem vouchers using reward points</li>
 *   <li>View transaction history</li>
 *   <li>Receive real-time point updates via WebSocket</li>
 * </ul>
 * </p>
 * 
 * <p>The activity uses RewardApiService for backend communication and
 * WebSocketService for real-time notifications about point changes.
 * It displays vouchers and transactions in RecyclerViews with appropriate
 * adapters.</p>
 * 
 * @author Team Member 2
 * @version 1.0
 * @since 1.0
 */
public class RewardsActivity extends AppCompatActivity {

    private TextView statusText;
    private TextView totalPointsText;
    private RecyclerView vouchersRecyclerView;
    private View vouchersEmptyState;
    private RecyclerView transactionsRecyclerView;
    private View transactionsEmptyState;
    private VoucherAdapter voucherAdapter;
    private TransactionAdapter transactionAdapter;
    private RewardApiService rewardApiService;
    private WebSocketService webSocketService;
    private String currentUsername;
    private Long userInfoId;
    private int userPoints = 0;
    private List<RewardApiService.RewardTransaction> currentTransactions = new ArrayList<>();

    /**
     * Initializes the activity and sets up the rewards interface.
     * 
     * <p>This method initializes all UI components, sets up RecyclerViews for
     * vouchers and transactions, configures adapters, and fetches the user ID
     * to load rewards data. It also sets up WebSocket connection for real-time
     * point updates.</p>
     * 
     * <p>If username is not found, it shows a warning and uses default values
     * for testing purposes.</p>
     * 
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down, this Bundle contains
     *                           the data it most recently supplied in
     *                           onSaveInstanceState(Bundle). Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("username")) {
            currentUsername = intent.getStringExtra("username");
        }
        
        if (currentUsername == null || currentUsername.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
            currentUsername = prefs.getString("username", "");
            android.util.Log.d("RewardsActivity", "Username from SharedPreferences: " + currentUsername);
        } else {
            android.util.Log.d("RewardsActivity", "Username from Intent: " + currentUsername);
        }

        if (currentUsername == null || currentUsername.isEmpty()) {
            currentUsername = "demo_user";
            android.util.Log.d("RewardsActivity", "Using default test username: " + currentUsername);
        }

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        
        rewardApiService = new RewardApiService(this);
        
        if (currentUsername != null && !currentUsername.isEmpty()) {
            android.util.Log.d("RewardsActivity", "Fetching user ID for username: " + currentUsername);
            fetchUserIdAndLoadData();
            setupWebSocket();
        } else {
            android.util.Log.w("RewardsActivity", "Username not found in Intent or SharedPreferences");
            setUserPoints(0);
            loadVouchers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userInfoId != null) {
            loadUserPoints();
            loadTransactions();
        } else if (currentUsername != null && !currentUsername.isEmpty()) {
            fetchUserIdAndLoadData();
        }
        if (currentUsername != null && !currentUsername.isEmpty() && webSocketService == null) {
            setupWebSocket();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webSocketService != null) {
            webSocketService.setListener(null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocketService != null) {
            webSocketService.setListener(null);
        }
    }

    /**
     * Initializes all UI components from the layout file.
     * 
     * <p>This method finds all TextViews, RecyclerViews, and empty state views
     * from the layout and stores references for later use.</p>
     */
    private void initializeViews() {
        statusText = findViewById(R.id.rewards_status_tv);
        totalPointsText = findViewById(R.id.total_points_tv);
        vouchersRecyclerView = findViewById(R.id.vouchers_recycler_view);
        vouchersEmptyState = findViewById(R.id.vouchers_empty_state);
        transactionsRecyclerView = findViewById(R.id.transactions_recycler_view);
        transactionsEmptyState = findViewById(R.id.transactions_empty_state);
    }
    
    /**
     * Configures the RecyclerViews with adapters and layout managers.
     * 
     * <p>This method sets up both the vouchers and transactions RecyclerViews
     * with LinearLayoutManager and their respective adapters. It also configures
     * the redeem click listener for vouchers.</p>
     */
    private void setupRecyclerView() {
        voucherAdapter = new VoucherAdapter(new ArrayList<>(), userPoints);
        voucherAdapter.setOnRedeemClickListener(new VoucherAdapter.OnRedeemClickListener() {
            @Override
            public void onRedeemClick(RewardApiService.RewardVoucher voucher) {
                showRedeemConfirmation(voucher);
            }
        });
        vouchersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        vouchersRecyclerView.setAdapter(voucherAdapter);

        transactionAdapter = new TransactionAdapter(new ArrayList<>());
        transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        transactionsRecyclerView.setAdapter(transactionAdapter);
    }
    
    /**
     * Fetches the user ID from username and loads all rewards-related data.
     * 
     * <p>This method uses RewardApiService to get the user ID from the username.
     * Once the ID is obtained, it loads user points, vouchers, and transactions.
     * On error, it sets points to 0 and still loads vouchers.</p>
     */
    private void fetchUserIdAndLoadData() {
        rewardApiService.getUserIdFromUsername(currentUsername, new RewardApiService.UserIdCallback() {
            @Override
            public void onSuccess(Long userId) {
                userInfoId = userId;
                android.util.Log.d("RewardsActivity", "User ID found: " + userId);
                loadUserPoints();
                loadVouchers();
                loadTransactions();
            }
            
            @Override
            public void onError(String error) {
                android.util.Log.e("RewardsActivity", "Error fetching user ID: " + error);
                setUserPoints(0);
                loadVouchers();
            }
        });
    }
    
    /**
     * Fetches and displays the current user's reward points balance.
     * 
     * <p>This method retrieves the user's current points from the backend
     * and updates the UI. It also updates the voucher adapter with the new
     * points value to enable/disable redeem buttons appropriately.</p>
     * 
     * <p>If userInfoId is null, it sets points to 0.</p>
     */
    private void loadUserPoints() {
        if (userInfoId == null) {
            setUserPoints(0);
            return;
        }
        
        rewardApiService.getUserPoints(userInfoId, new RewardApiService.PointsCallback() {
            @Override
            public void onSuccess(int points) {
                userPoints = points;
                setUserPoints(points);
                android.util.Log.d("RewardsActivity", "Loaded points: " + points);
                
                if (voucherAdapter != null) {
                    voucherAdapter.setUserPoints(userPoints);
                }
            }
            
            @Override
            public void onError(String error) {
                android.util.Log.e("RewardsActivity", "Error fetching points: " + error);
                userPoints = 0;
                setUserPoints(0);
                if (voucherAdapter != null) {
                    voucherAdapter.setUserPoints(userPoints);
                }
            }
        });
    }
    
    /**
     * Fetches and displays all available vouchers from organizations.
     * 
     * <p>This method retrieves all vouchers from the backend and updates
     * the RecyclerView. If vouchers are available, it shows them; otherwise,
     * it displays an empty state. The vouchers are displayed with redeem
     * buttons that are enabled/disabled based on user's available points.</p>
     */
    private void loadVouchers() {
        android.util.Log.d("RewardsActivity", "Loading vouchers from API...");
        rewardApiService.getAllVouchers(new RewardApiService.VouchersCallback() {
            @Override
            public void onSuccess(List<RewardApiService.RewardVoucher> vouchers) {
                android.util.Log.d("RewardsActivity", "Vouchers received: " + (vouchers != null ? vouchers.size() : 0));
                runOnUiThread(() -> {
                    if (vouchers != null && !vouchers.isEmpty()) {
                        android.util.Log.d("RewardsActivity", "Displaying " + vouchers.size() + " vouchers");
                        voucherAdapter = new VoucherAdapter(vouchers, userPoints);
                        voucherAdapter.setOnRedeemClickListener(new VoucherAdapter.OnRedeemClickListener() {
                            @Override
                            public void onRedeemClick(RewardApiService.RewardVoucher voucher) {
                                showRedeemConfirmation(voucher);
                            }
                        });
                        vouchersRecyclerView.setAdapter(voucherAdapter);
                        
                        vouchersRecyclerView.setVisibility(View.VISIBLE);
                        vouchersEmptyState.setVisibility(View.GONE);
                    } else {
                        android.util.Log.d("RewardsActivity", "No vouchers available - showing empty state");
                        vouchersRecyclerView.setVisibility(View.GONE);
                        vouchersEmptyState.setVisibility(View.VISIBLE);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                android.util.Log.e("RewardsActivity", "Error fetching vouchers: " + error);
                runOnUiThread(() -> {
                    vouchersRecyclerView.setVisibility(View.GONE);
                    vouchersEmptyState.setVisibility(View.VISIBLE);
                    Toast.makeText(RewardsActivity.this, "Failed to load vouchers: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    /**
     * Shows a confirmation dialog before redeeming a voucher.
     * 
     * <p>This method checks if the user has sufficient points to redeem the
     * voucher. If not, it shows an error message. Otherwise, it displays a
     * confirmation dialog with voucher details and cost.</p>
     * 
     * @param voucher The RewardVoucher object to be redeemed
     */
    private void showRedeemConfirmation(RewardApiService.RewardVoucher voucher) {
        if (userPoints < voucher.getCostPoints()) {
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Redeem Voucher");
        builder.setMessage("Redeem \"" + voucher.getVoucherName() + "\" for " + voucher.getCostPoints() + " points?");
        
        builder.setPositiveButton("Yes", (dialog, which) -> {
            redeemVoucher(voucher);
        });
        
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
        });
        
        builder.show();
    }
    
    /**
     * Redeems a voucher using the user's reward points.
     * 
     * <p>This method sends a request to the backend to redeem the voucher.
     * On success, it adds the transaction to the local list, updates the
     * points display, refreshes vouchers and transactions, and shows a
     * success message.</p>
     * 
     * <p>On error, it displays an error toast with details.</p>
     * 
     * @param voucher The RewardVoucher object to redeem
     */
    private void redeemVoucher(RewardApiService.RewardVoucher voucher) {
        if (userInfoId == null) {
            return;
        }
        
        rewardApiService.redeemVoucher(userInfoId, voucher.getId(), new RewardApiService.RedeemCallback() {
            @Override
            public void onSuccess(RewardApiService.RewardTransaction transaction) {
                runOnUiThread(() -> {
                    String message = "✅ Voucher redeemed! Points: " + transaction.getPoints();
                    android.util.Log.d("RewardsActivity", "Adding transaction: " + transaction.getDescription() + ", Points: " + transaction.getPoints());
                    currentTransactions.add(0, transaction);
                    android.util.Log.d("RewardsActivity", "Current transactions count: " + currentTransactions.size());
                    updateTransactionsUI();
                    loadUserPoints();
                    loadVouchers();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(RewardsActivity.this, "❌ Error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    /**
     * Updates the displayed user points value in the UI.
     * 
     * <p>This method updates both the internal points variable and the
     * TextView displaying the total points to the user.</p>
     * 
     * @param points The new points value to display
     */
    private void setUserPoints(int points) {
        userPoints = points;
        if (totalPointsText != null) {
            totalPointsText.setText(String.valueOf(points));
        }
    }

    /**
     * Sets up click listeners for navigation and action buttons.
     * 
     * <p>This method configures click handlers for:
     * <ul>
     *   <li>Back button: Finishes the activity</li>
     *   <li>Refresh button: Reloads points, transactions, and vouchers</li>
     * </ul>
     * </p>
     */
    private void setupClickListeners() {
        Button backButton = findViewById(R.id.rewards_back_btn);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        
        Button refreshButton = findViewById(R.id.refresh_button);
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> {
                if (userInfoId != null) {
                    loadUserPoints();
                    loadTransactions();
                }
                loadVouchers();
            });
        }
    }

    /**
     * Fetches and displays the user's reward transaction history.
     * 
     * <p>This method retrieves all transactions from the backend and merges
     * them with any local transactions (from recent redemptions). It updates
     * the RecyclerView with the combined list or shows an empty state if no
     * transactions exist.</p>
     */
    private void loadTransactions() {
        if (userInfoId == null) {
            return;
        }

        rewardApiService.getUserTransactions(userInfoId, new RewardApiService.TransactionsCallback() {
            @Override
            public void onSuccess(List<RewardApiService.RewardTransaction> transactions) {
                runOnUiThread(() -> {
                    if (transactions != null && !transactions.isEmpty()) {
                        List<RewardApiService.RewardTransaction> merged = new ArrayList<>(transactions);
                        for (RewardApiService.RewardTransaction localTx : currentTransactions) {
                            boolean exists = false;
                            for (RewardApiService.RewardTransaction serverTx : transactions) {
                                if (serverTx.getId() != null && localTx.getId() != null && 
                                    serverTx.getId().equals(localTx.getId())) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                merged.add(0, localTx);
                            }
                        }
                        currentTransactions = merged;
                        updateTransactionsUI();
                    } else if (!currentTransactions.isEmpty()) {
                        updateTransactionsUI();
                    } else {
                        transactionsRecyclerView.setVisibility(View.GONE);
                        transactionsEmptyState.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("RewardsActivity", "Error fetching transactions: " + error);
                runOnUiThread(() -> {
                    transactionsRecyclerView.setVisibility(View.GONE);
                    transactionsEmptyState.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    /**
     * Updates the transactions RecyclerView with the current transaction list.
     * 
     * <p>This method creates a new TransactionAdapter with the current
     * transactions list and updates the RecyclerView. If transactions exist,
     * it shows the RecyclerView; otherwise, it shows the empty state.</p>
     */
    private void updateTransactionsUI() {
        android.util.Log.d("RewardsActivity", "updateTransactionsUI called, count: " + (currentTransactions != null ? currentTransactions.size() : 0));
        if (currentTransactions != null && !currentTransactions.isEmpty()) {
            transactionAdapter = new TransactionAdapter(new ArrayList<>(currentTransactions));
            transactionsRecyclerView.setAdapter(transactionAdapter);
            transactionAdapter.notifyDataSetChanged();
            transactionsRecyclerView.setVisibility(View.VISIBLE);
            transactionsEmptyState.setVisibility(View.GONE);
            android.util.Log.d("RewardsActivity", "UI updated, adapter item count: " + transactionAdapter.getItemCount());
        } else {
            transactionsRecyclerView.setVisibility(View.GONE);
            transactionsEmptyState.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Sets up WebSocket connection for real-time reward point updates.
     * 
     * <p>This method connects to the WebSocket notification service and sets
     * up a listener to receive real-time updates about point changes. When
     * a message containing "points" is received, it automatically refreshes
     * the user's points and transactions, and shows a toast notification.</p>
     * 
     * <p>The WebSocket connection is established using the current username
     * and the notification WebSocket URL from ServerConfig.</p>
     */
    private void setupWebSocket() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            return;
        }

        webSocketService = WebSocketService.getInstance();
        webSocketService.setListener(new WebSocketService.WebSocketListener() {
            @Override
            public void onMessage(String message) {
                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(message);
                        String type = json.optString("type", "");
                        String data = json.optString("data", "");

                        if (data.contains("points") || data.contains("Points")) {
                            if (userInfoId != null) {
                                loadUserPoints();
                                loadTransactions();
                            }
                        }
                    } catch (Exception e) {
                        if (message.contains("points") || message.contains("Points")) {
                            if (userInfoId != null) {
                                loadUserPoints();
                                loadTransactions();
                            }
                        }
                    }
                });
            }

            @Override
            public void onConnect() {
                android.util.Log.d("RewardsActivity", "WebSocket connected");
            }

            @Override
            public void onDisconnect() {
                android.util.Log.d("RewardsActivity", "WebSocket disconnected");
            }

            @Override
            public void onError(Exception e) {
                android.util.Log.e("RewardsActivity", "WebSocket error: " + e.getMessage());
            }
        });

        String wsUrl = com.example.occasio.utils.ServerConfig.WS_NOTIFICATION_URL;
        webSocketService.connect(currentUsername, wsUrl);
    }
}

