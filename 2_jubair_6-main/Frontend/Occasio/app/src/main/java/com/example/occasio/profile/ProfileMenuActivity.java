package com.example.occasio.profile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.occasio.R;
import com.example.occasio.base.BaseNavigationActivity;
import com.example.occasio.email.EmailInboxActivity;
import com.example.occasio.feedback.MyFeedbacksActivity;
import com.example.occasio.friends.MyFriendsActivity;
import com.example.occasio.payment.PaymentHistoryActivity;
import com.example.occasio.rewards.RewardsActivity;

import androidx.appcompat.app.AlertDialog;

/**
 * ProfileMenuActivity - Menu-style profile page
 * Displays various menu options for user account management
 */
public class ProfileMenuActivity extends BaseNavigationActivity {

    private Button editProfileButton;
    private Button paymentHistoryButton;
    private Button calendarButton;
    private Button favoritesButton;
    private Button registeredEventsButton;
    private Button feedbackButton;
    private Button emailButton;
    private Button friendsButton;
    private Button rewardsButton;
    private Button logoutButton;
    private Button deleteAccountButton;
    
    private EditText searchInput;
    private LinearLayout menuContentContainer;
    private Button selectedButton;
    private String selectedButtonText;

    private SharedPreferences sharedPreferences;
    private String currentUsername;
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_USERNAME = "username";
    
    // Store all buttons for filtering
    private java.util.List<ButtonInfo> allButtons = new java.util.ArrayList<>();
    
    private static class ButtonInfo {
        Button button;
        String text;
        String searchableText;
        View.OnClickListener listener;
        View parentView; // The CardView or section containing this button
        
        ButtonInfo(Button button, String text, View.OnClickListener listener, View parentView) {
            this.button = button;
            this.text = text;
            this.searchableText = text.toLowerCase().replaceAll("[^a-z0-9\\s]", ""); // Remove emojis and special chars for search
            this.listener = listener;
            this.parentView = parentView;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_menu);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("username")) {
            currentUsername = intent.getStringExtra("username");
        } else {
            currentUsername = sharedPreferences.getString(KEY_USERNAME, "");
        }

        if (currentUsername == null || currentUsername.isEmpty()) {
            finish();
            return;
        }

        initializeViews();
        setupClickListeners();
        setupSearchFunctionality();
        
        // Setup bottom navigation
        setupBottomNavigation();
    }

    private void initializeViews() {
        searchInput = findViewById(R.id.profile_menu_search_input);
        menuContentContainer = findViewById(R.id.profile_menu_content_container);
        
        editProfileButton = findViewById(R.id.profile_menu_edit_profile_btn);
        paymentHistoryButton = findViewById(R.id.profile_menu_payment_history_btn);
        calendarButton = findViewById(R.id.profile_menu_calendar_btn);
        favoritesButton = findViewById(R.id.profile_menu_favorites_btn);
        registeredEventsButton = findViewById(R.id.profile_menu_registered_btn);
        // Feedback button may not exist in the current layout
        feedbackButton = null; // findViewById(R.id.profile_menu_feedback_btn);
        emailButton = findViewById(R.id.profile_menu_email_btn);
        friendsButton = findViewById(R.id.profile_menu_friends_btn);
        rewardsButton = findViewById(R.id.profile_menu_rewards_btn);
        logoutButton = findViewById(R.id.profile_menu_logout_btn);
        deleteAccountButton = findViewById(R.id.profile_menu_delete_account_btn);
        
        // Store all buttons with their info for search
        storeAllButtons();
    }
    
    private void storeAllButtons() {
        allButtons.clear();
        
        // Find parent views (CardViews) for each button
        View accountCard = editProfileButton != null ? (View) editProfileButton.getParent().getParent() : null;
        View eventsCard = calendarButton != null ? (View) calendarButton.getParent().getParent() : null;
        View socialCard = friendsButton != null ? (View) friendsButton.getParent().getParent() : null;
        View rewardsCard = rewardsButton != null ? (View) rewardsButton.getParent().getParent() : null;
        View actionsCard = logoutButton != null ? (View) logoutButton.getParent().getParent() : null;
        
        if (editProfileButton != null) {
            allButtons.add(new ButtonInfo(editProfileButton, editProfileButton.getText().toString(), 
                v -> editProfileButton.performClick(), accountCard));
        }
        if (paymentHistoryButton != null) {
            allButtons.add(new ButtonInfo(paymentHistoryButton, paymentHistoryButton.getText().toString(), 
                v -> paymentHistoryButton.performClick(), accountCard));
        }
        if (emailButton != null) {
            allButtons.add(new ButtonInfo(emailButton, emailButton.getText().toString(), 
                v -> emailButton.performClick(), accountCard));
        }
        if (calendarButton != null) {
            allButtons.add(new ButtonInfo(calendarButton, calendarButton.getText().toString(), 
                v -> calendarButton.performClick(), eventsCard));
        }
        if (favoritesButton != null) {
            allButtons.add(new ButtonInfo(favoritesButton, favoritesButton.getText().toString(), 
                v -> favoritesButton.performClick(), eventsCard));
        }
        if (registeredEventsButton != null) {
            allButtons.add(new ButtonInfo(registeredEventsButton, registeredEventsButton.getText().toString(), 
                v -> registeredEventsButton.performClick(), eventsCard));
        }
        if (friendsButton != null) {
            allButtons.add(new ButtonInfo(friendsButton, friendsButton.getText().toString(), 
                v -> friendsButton.performClick(), socialCard));
        }
        if (rewardsButton != null) {
            allButtons.add(new ButtonInfo(rewardsButton, rewardsButton.getText().toString(), 
                v -> rewardsButton.performClick(), rewardsCard));
        }
        if (logoutButton != null) {
            allButtons.add(new ButtonInfo(logoutButton, logoutButton.getText().toString(), 
                v -> logoutButton.performClick(), actionsCard));
        }
        if (deleteAccountButton != null) {
            allButtons.add(new ButtonInfo(deleteAccountButton, deleteAccountButton.getText().toString(), 
                v -> deleteAccountButton.performClick(), actionsCard));
        }
    }

    private void setupClickListeners() {
        editProfileButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileMenuActivity.this, EditProfileActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
        });

        paymentHistoryButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileMenuActivity.this, PaymentHistoryActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
        });

        calendarButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileMenuActivity.this, com.example.occasio.events.CalendarActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
        });

        favoritesButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileMenuActivity.this, com.example.occasio.events.FavoritesActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
        });

        registeredEventsButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileMenuActivity.this, com.example.occasio.events.UserEventsActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
        });

        if (feedbackButton != null) {
            feedbackButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileMenuActivity.this, com.example.occasio.feedback.MyFeedbacksActivity.class);
                intent.putExtra("username", currentUsername);
                startActivity(intent);
            });
        }

        if (emailButton != null) {
            emailButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileMenuActivity.this, com.example.occasio.email.EmailInboxActivity.class);
                intent.putExtra("username", currentUsername);
                startActivity(intent);
            });
        }

        friendsButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileMenuActivity.this, MyFriendsActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
        });

        rewardsButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileMenuActivity.this, RewardsActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
        });

        logoutButton.setOnClickListener(v -> showLogoutConfirmation());

        deleteAccountButton.setOnClickListener(v -> showDeleteConfirmation());
    }
    
    private void setupSearchFunctionality() {
        if (searchInput == null) {
            return;
        }
        
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterMenuItems(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Handle search button/enter key
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            String query = searchInput.getText().toString().trim();
            if (!query.isEmpty()) {
                filterMenuItems(query);
                // Hide keyboard
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                    getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });
    }
    
    private void filterMenuItems(String query) {
        if (query == null || query.trim().isEmpty()) {
            // Show all buttons and sections
            showAllMenuItems();
            selectedButton = null;
            selectedButtonText = null;
            return;
        }
        
        String lowerQuery = query.toLowerCase().trim();
        java.util.List<ButtonInfo> matchingButtons = new java.util.ArrayList<>();
        
        // Find matching buttons
        for (ButtonInfo buttonInfo : allButtons) {
            if (buttonInfo.searchableText.contains(lowerQuery)) {
                matchingButtons.add(buttonInfo);
            }
        }
        
        if (matchingButtons.isEmpty()) {
            // No matches - show message
            showNoResults();
        } else if (matchingButtons.size() == 1) {
            // Single match - show only that button
            showSingleButton(matchingButtons.get(0));
        } else {
            // Multiple matches - show all matching buttons
            showMatchingButtons(matchingButtons);
        }
    }
    
    private void showAllMenuItems() {
        // Show all sections and buttons
        for (ButtonInfo buttonInfo : allButtons) {
            if (buttonInfo.button != null) {
                buttonInfo.button.setVisibility(View.VISIBLE);
            }
            if (buttonInfo.parentView != null) {
                buttonInfo.parentView.setVisibility(View.VISIBLE);
            }
        }
        
        // Show all section headers
        View rootView = menuContentContainer;
        if (rootView != null) {
            for (int i = 0; i < ((ViewGroup) rootView).getChildCount(); i++) {
                View child = ((ViewGroup) rootView).getChildAt(i);
                if (child instanceof TextView && child.getVisibility() != View.GONE) {
                    child.setVisibility(View.VISIBLE);
                }
            }
        }
    }
    
    private void showNoResults() {
        // Hide all buttons and sections
        for (ButtonInfo buttonInfo : allButtons) {
            if (buttonInfo.button != null) {
                buttonInfo.button.setVisibility(View.GONE);
            }
            if (buttonInfo.parentView != null) {
                buttonInfo.parentView.setVisibility(View.GONE);
            }
        }
        
        // Hide all section headers
        View rootView = menuContentContainer;
        if (rootView != null) {
            for (int i = 0; i < ((ViewGroup) rootView).getChildCount(); i++) {
                View child = ((ViewGroup) rootView).getChildAt(i);
                if (child instanceof TextView) {
                    child.setVisibility(View.GONE);
                }
            }
        }
        
        // Show "No results" message
        TextView noResultsText = null;
        // Try to find existing no results text
        if (menuContentContainer != null) {
            for (int i = 0; i < menuContentContainer.getChildCount(); i++) {
                View child = menuContentContainer.getChildAt(i);
                if (child instanceof TextView && child.getTag() != null && 
                    child.getTag().equals("no_results")) {
                    noResultsText = (TextView) child;
                    break;
                }
            }
        }
        
        if (noResultsText == null) {
            // Create and add no results TextView if it doesn't exist
            noResultsText = new TextView(this);
            noResultsText.setTag("no_results");
            noResultsText.setText("No menu items found");
            noResultsText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
            noResultsText.setTextColor(getResources().getColor(R.color.text_secondary_fall, getTheme()));
            noResultsText.setGravity(android.view.Gravity.CENTER);
            noResultsText.setPadding(0, 32, 0, 32);
            if (menuContentContainer != null) {
                menuContentContainer.addView(noResultsText);
            }
        }
        if (noResultsText != null) {
            noResultsText.setVisibility(View.VISIBLE);
        }
    }
    
    private void showSingleButton(ButtonInfo buttonInfo) {
        // Hide all buttons and sections
        for (ButtonInfo info : allButtons) {
            if (info.button != null) {
                info.button.setVisibility(View.GONE);
            }
            if (info.parentView != null) {
                info.parentView.setVisibility(View.GONE);
            }
        }
        
        // Hide all section headers
        View rootView = menuContentContainer;
        if (rootView != null) {
            for (int i = 0; i < ((ViewGroup) rootView).getChildCount(); i++) {
                View child = ((ViewGroup) rootView).getChildAt(i);
                if (child instanceof TextView) {
                    child.setVisibility(View.GONE);
                }
            }
        }
        
        // Hide no results message if it exists
        if (menuContentContainer != null) {
            for (int i = 0; i < menuContentContainer.getChildCount(); i++) {
                View child = menuContentContainer.getChildAt(i);
                if (child instanceof TextView && child.getTag() != null && 
                    child.getTag().equals("no_results")) {
                    child.setVisibility(View.GONE);
                    break;
                }
            }
        }
        
        // Show only the matching button
        if (buttonInfo.button != null) {
            buttonInfo.button.setVisibility(View.VISIBLE);
        }
        if (buttonInfo.parentView != null) {
            buttonInfo.parentView.setVisibility(View.VISIBLE);
        }
        
        selectedButton = buttonInfo.button;
        selectedButtonText = buttonInfo.text;
    }
    
    private void showMatchingButtons(java.util.List<ButtonInfo> matchingButtons) {
        // Hide no results message if it exists
        if (menuContentContainer != null) {
            for (int i = 0; i < menuContentContainer.getChildCount(); i++) {
                View child = menuContentContainer.getChildAt(i);
                if (child instanceof TextView && child.getTag() != null && 
                    child.getTag().equals("no_results")) {
                    child.setVisibility(View.GONE);
                    break;
                }
            }
        }
        
        // Hide all buttons and sections first
        for (ButtonInfo buttonInfo : allButtons) {
            if (buttonInfo.button != null) {
                buttonInfo.button.setVisibility(View.GONE);
            }
            if (buttonInfo.parentView != null) {
                buttonInfo.parentView.setVisibility(View.GONE);
            }
        }
        
        // Hide all section headers
        View rootView = menuContentContainer;
        if (rootView != null) {
            for (int i = 0; i < ((ViewGroup) rootView).getChildCount(); i++) {
                View child = ((ViewGroup) rootView).getChildAt(i);
                if (child instanceof TextView) {
                    child.setVisibility(View.GONE);
                }
            }
        }
        
        // Show only matching buttons and their parent containers
        for (ButtonInfo buttonInfo : matchingButtons) {
            if (buttonInfo.button != null) {
                buttonInfo.button.setVisibility(View.VISIBLE);
            }
            if (buttonInfo.parentView != null) {
                buttonInfo.parentView.setVisibility(View.VISIBLE);
            }
        }
        
        selectedButton = null;
        selectedButtonText = null;
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes", (dialog, which) -> logout())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void logout() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_USERNAME);
        editor.remove("remember_me");
        editor.apply();

        Intent intent = new Intent(ProfileMenuActivity.this, com.example.occasio.auth.LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
            .setPositiveButton("Delete", (dialog, which) -> {
                // Navigate to EditProfileActivity for deletion
                Intent intent = new Intent(ProfileMenuActivity.this, EditProfileActivity.class);
                intent.putExtra("username", currentUsername);
                intent.putExtra("showDeleteDialog", true);
                startActivity(intent);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
}

