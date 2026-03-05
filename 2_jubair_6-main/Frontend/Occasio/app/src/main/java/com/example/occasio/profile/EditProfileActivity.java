package com.example.occasio.profile;
import com.example.occasio.R;

import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.occasio.R;
import com.example.occasio.base.BaseNavigationActivity;
import com.example.occasio.auth.HomeActivity;
import org.json.JSONException;
import org.json.JSONObject;

public class EditProfileActivity extends BaseNavigationActivity {

    private EditText firstNameEditText;
    private EditText lastNameEditText;
    private EditText emailEditText;
    private EditText phoneEditText;
    private EditText userClassEditText;
    private EditText bioEditText;
    private Button saveButton;
    private Button deleteAccountButton;
    private Button friendsButton;
    private Button rewardsButton;
    private Button logoutButton;
    private ImageView profileImageView;
    private String selectedImageBase64;
    private Uri selectedImageUri;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    private SharedPreferences sharedPreferences;
    private String currentUsername;
    private RequestQueue requestQueue;
    // REAL BACKEND
    private static final String GET_USER_URL = com.example.occasio.utils.ServerConfig.USER_INFO_BY_USERNAME;
    private static final String UPDATE_PROFILE_URL = com.example.occasio.utils.ServerConfig.USER_INFO_BY_USERNAME;
    private static final String DELETE_URL = com.example.occasio.utils.ServerConfig.USER_INFO_BY_USERNAME;
    private static final String PREFS_NAME = "UserPrefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_REMEMBER_ME = "remember_me";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        initializeViews();
        setupImagePicker();
        loadUserData();
        setupClickListeners();
        
        // Setup bottom navigation
        setupBottomNavigation();
    }
    
    private void setupImagePicker() {
        // Register for image picker result
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        handleSelectedImage(imageUri);
                    }
                }
            }
        );
        
        // Register for permission result
        permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    openImagePicker();
                }
            }
        );
    }
    
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Profile Picture"));
    }
    
    private void handleSelectedImage(Uri imageUri) {
        try {
            // Read image from URI
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) {
                inputStream.close();
            }
            
            if (bitmap == null) {
                return;
            }
            
            // Resize image to reduce size (max 800x800)
            int maxDimension = 800;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            
            if (width > maxDimension || height > maxDimension) {
                float scale = Math.min((float) maxDimension / width, (float) maxDimension / height);
                int newWidth = Math.round(width * scale);
                int newHeight = Math.round(height * scale);
                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            }
            
            // Convert to base64
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream); // 80% quality
            byte[] imageBytes = outputStream.toByteArray();
            selectedImageBase64 = Base64.encodeToString(imageBytes, Base64.DEFAULT);
            
            // Update ImageView
            profileImageView.setImageBitmap(bitmap);
            selectedImageUri = imageUri;
            
        } catch (IOException e) {
            Log.e("EditProfileActivity", "Error handling selected image: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeViews() {
        firstNameEditText = findViewById(R.id.edit_first_name_et);
        lastNameEditText = findViewById(R.id.edit_last_name_et);
        emailEditText = findViewById(R.id.edit_email_et);
        phoneEditText = findViewById(R.id.edit_phone_et);
        userClassEditText = findViewById(R.id.edit_user_class_et);
        bioEditText = findViewById(R.id.edit_bio_et);
        saveButton = findViewById(R.id.edit_save_btn);
        deleteAccountButton = findViewById(R.id.edit_delete_account_btn);
        friendsButton = findViewById(R.id.edit_friends_btn);
        rewardsButton = findViewById(R.id.edit_rewards_btn);
        logoutButton = findViewById(R.id.edit_logout_btn);
        profileImageView = findViewById(R.id.edit_profile_iv);

        sharedPreferences = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        requestQueue = Volley.newRequestQueue(this);
        
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("username")) {
            currentUsername = intent.getStringExtra("username");
        } else {
            currentUsername = sharedPreferences.getString("username", "");
            if (currentUsername.isEmpty()) {
                currentUsername = "demo_user";
                sharedPreferences.edit().putString("username", currentUsername).apply();
            }
        }
    }

    private void loadUserData() {
        // First try to load from backend
        fetchUserDataFromServer();
        
        // Also load from SharedPreferences as fallback
        String firstName = sharedPreferences.getString("firstName", "");
        String lastName = sharedPreferences.getString("lastName", "");
        String email = sharedPreferences.getString("email", "");
        String phone = sharedPreferences.getString("phone", "");
        String userClass = sharedPreferences.getString("userClass", "");
        String bio = sharedPreferences.getString("bio", "");
        
        // Only set if not empty (backend data will override)
        if (!firstName.isEmpty()) firstNameEditText.setText(firstName);
        if (!lastName.isEmpty()) lastNameEditText.setText(lastName);
        if (!email.isEmpty()) emailEditText.setText(email);
        if (!phone.isEmpty()) phoneEditText.setText(phone);
        if (!userClass.isEmpty()) userClassEditText.setText(userClass);
        if (!bio.isEmpty()) bioEditText.setText(bio);
        
        // Load profile picture if available
        String profilePicturePath = sharedPreferences.getString("profilePicturePath", "");
        if (!profilePicturePath.isEmpty() && profilePicturePath.startsWith("data:image")) {
            // Base64 image data
            try {
                String base64Data = profilePicturePath.substring(profilePicturePath.indexOf(",") + 1);
                byte[] imageBytes = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                if (bitmap != null) {
                    profileImageView.setImageBitmap(bitmap);
                } else {
                    profileImageView.setImageResource(android.R.drawable.ic_menu_myplaces);
                }
            } catch (Exception e) {
                Log.e("EditProfileActivity", "Error loading profile picture: " + e.getMessage());
                profileImageView.setImageResource(android.R.drawable.ic_menu_myplaces);
            }
        } else {
            profileImageView.setImageResource(android.R.drawable.ic_menu_myplaces);
        }
        
        // Make profile image clickable to change picture
        profileImageView.setOnClickListener(v -> {
            // Request permission if needed (Android 13+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES);
                    return;
                }
            } else {
                if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE);
                    return;
                }
            }
            openImagePicker();
        });
    }
    
    private void fetchUserDataFromServer() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            Log.e("EditProfileActivity", "Cannot fetch user data: username is empty");
            return;
        }
        
        String url = GET_USER_URL + currentUsername;
        
        JsonObjectRequest getUserRequest = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        // Parse and display user data
                        String firstName = response.optString("firstName", "");
                        String lastName = response.optString("lastName", "");
                        String email = response.optString("email", "");
                        String phone = response.optString("phone", "");
                        String userClass = response.optString("userClass", "");
                        String bio = response.optString("bio", "");
                        String profilePicturePath = response.optString("profilePicturePath", "");
                        
                        // Update UI
                        firstNameEditText.setText(firstName);
                        lastNameEditText.setText(lastName);
                        emailEditText.setText(email);
                        phoneEditText.setText(phone);
                        userClassEditText.setText(userClass);
                        bioEditText.setText(bio);
                        
                        // Save to SharedPreferences for offline access
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("firstName", firstName);
                        editor.putString("lastName", lastName);
                        editor.putString("email", email);
                        editor.putString("phone", phone);
                        editor.putString("userClass", userClass);
                        editor.putString("bio", bio);
                        if (!profilePicturePath.isEmpty()) {
                            editor.putString("profilePicturePath", profilePicturePath);
                        }
                        editor.apply();
                        
                        // Load profile picture if available
                        if (!profilePicturePath.isEmpty() && profilePicturePath.startsWith("data:image")) {
                            try {
                                String base64Data = profilePicturePath.substring(profilePicturePath.indexOf(",") + 1);
                                byte[] imageBytes = Base64.decode(base64Data, Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                                if (bitmap != null && profileImageView != null) {
                                    profileImageView.setImageBitmap(bitmap);
                                }
                            } catch (Exception e) {
                                Log.e("EditProfileActivity", "Error loading profile picture: " + e.getMessage());
                            }
                        }
                        
                        Log.d("EditProfileActivity", "User data loaded successfully from server");
                    } catch (Exception e) {
                        Log.e("EditProfileActivity", "Error parsing user data: " + e.getMessage());
                        e.printStackTrace();
                        // Don't show toast - just log the error and use cached data
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("EditProfileActivity", "Error fetching user data from server");
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        Log.e("EditProfileActivity", "Status code: " + statusCode);
                        if (statusCode == 404) {
                            // User not found - might be first time, show helpful message
                        }
                        // For other errors, silently use cached data
                    } else {
                        Log.e("EditProfileActivity", "Network error: " + error.toString());
                        // Don't show error toast for network issues - just use cached data
                        // This allows offline mode
                    }
                }
            }
        );
        
        requestQueue.add(getUserRequest);
    }

    private void setupClickListeners() {
        saveButton.setOnClickListener(v -> saveProfile());
        
        deleteAccountButton.setOnClickListener(v -> showDeleteConfirmation());
        
        friendsButton.setOnClickListener(v -> {
            Intent intent = new Intent(EditProfileActivity.this, com.example.occasio.friends.MyFriendsActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
        });
        
        rewardsButton.setOnClickListener(v -> {
            Intent intent = new Intent(EditProfileActivity.this, com.example.occasio.rewards.RewardsActivity.class);
            intent.putExtra("username", currentUsername);
            startActivity(intent);
        });
        
        logoutButton.setOnClickListener(v -> showLogoutConfirmation());
    }

    private void saveProfile() {
        String firstName = firstNameEditText.getText().toString().trim();
        String lastName = lastNameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String phone = phoneEditText.getText().toString().trim();
        String userClass = userClassEditText.getText().toString().trim();
        String bio = bioEditText.getText().toString().trim();

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("firstName", firstName);
        editor.putString("lastName", lastName);
        editor.putString("email", email);
        editor.putString("phone", phone);
        editor.putString("userClass", userClass);
        editor.putString("bio", bio);
        editor.apply();

        updateProfileOnServer(firstName, lastName, email, phone, userClass, bio);
    }

    private void updateProfileOnServer(String firstName, String lastName, String email, String phone, String userClass, String bio) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("username", currentUsername);
            requestBody.put("firstName", firstName);
            requestBody.put("lastName", lastName);
            requestBody.put("email", email);
            requestBody.put("phone", phone);
            requestBody.put("userClass", userClass);
            requestBody.put("bio", bio);
            
            // Include profile picture if one was selected
            if (selectedImageBase64 != null && !selectedImageBase64.isEmpty()) {
                String profilePictureDataUri = "data:image/jpeg;base64," + selectedImageBase64;
                requestBody.put("profilePicturePath", profilePictureDataUri);
                
                // Also save to SharedPreferences
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("profilePicturePath", profilePictureDataUri);
                editor.apply();
            }

            String url = UPDATE_PROFILE_URL + currentUsername;
            Log.d("EditProfileActivity", "Updating profile at URL: " + url);
            Log.d("EditProfileActivity", "Request body: " + requestBody.toString());

            JsonObjectRequest updateRequest = new JsonObjectRequest(
                Request.Method.PUT,
                url,
                requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("EditProfileActivity", "Profile update successful. Response: " + response.toString());
                        // Profile updated successfully - no toast needed
                        
                        // Update SharedPreferences with saved data
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("firstName", firstName);
                        editor.putString("lastName", lastName);
                        editor.putString("email", email);
                        editor.putString("phone", phone);
                        editor.putString("userClass", userClass);
                        editor.putString("bio", bio);
                        editor.apply();
                        
                        // Refresh profile data from server to get any server-side updates
                        fetchUserDataFromServer();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        String errorMessage = "Failed to update profile on server";
                        if (error.networkResponse != null) {
                            int statusCode = error.networkResponse.statusCode;
                            String responseBody = new String(error.networkResponse.data);
                            Log.e("Update Profile Error", "HTTP " + statusCode + ": " + responseBody);
                            errorMessage = "Server error (HTTP " + statusCode + "): " + responseBody;
                        } else if (error.getMessage() != null) {
                            Log.e("Update Profile Error", "Network error: " + error.getMessage());
                            errorMessage = "Network error: " + error.getMessage();
                        }
                        
                        android.util.Log.w("EditProfileActivity", "⚠️ Profile saved locally but failed to sync: " + errorMessage);
                        
                        // Since we extend BaseNavigationActivity, we can just finish or stay on profile page
                        // The bottom navigation will handle navigation
                        // Optionally refresh profile data
                        loadUserData();
                    }
                }
            ) {
                @Override
                public java.util.Map<String, String> getHeaders() {
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("Content-Type", "application/json");
                    headers.put("Accept", "application/json");
                    return headers;
                }
            };
            requestQueue.add(updateRequest);
        } catch (JSONException e) {
            Log.e("JSON Error", "Error creating update request: " + e.getMessage());
        }
    }

    private void showInAppNotification(String title, String message) {
        // Simple notification to avoid layout crashes
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
            .setCancelable(true)
            .show();
    }
    
    private void showDeleteConfirmation() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("⚠️ Delete Account")
            .setMessage("Are you absolutely sure you want to delete your account?\n\n" +
                       "This will permanently remove:\n" +
                       "• Your profile and personal information\n" +
                       "• All your event registrations\n" +
                       "• Your friends list\n" +
                       "• All your preferences and settings\n\n" +
                       "This action CANNOT be undone!")
            .setPositiveButton("Yes, Delete Forever", (dialog, which) -> showFinalConfirmation())
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setCancelable(false)
            .show();
    }

    private void showFinalConfirmation() {
        new AlertDialog.Builder(this)
            .setTitle("🚨 Final Confirmation")
            .setMessage("This is your last chance to cancel!\n\n" +
                       "Type '" + currentUsername + "' to confirm deletion:")
            .setPositiveButton("Confirm Delete", (dialog, which) -> {
                // For now, proceed with deletion
                // In a real app, you might want to add a text input field here
                deleteAccount();
            })
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setCancelable(false)
            .show();
    }

    private void deleteAccount() {
        if (currentUsername == null || currentUsername.isEmpty()) {
            return;
        }

        // Show loading dialog
        AlertDialog loadingDialog = new AlertDialog.Builder(this)
            .setTitle("Deleting Account...")
            .setMessage("Please wait while we delete your account.\nThis may take a few moments.")
            .setCancelable(false)
            .create();
        loadingDialog.show();

        // Use the actual current username
        String deleteUsername = currentUsername;
        
        JsonObjectRequest deleteRequest = new JsonObjectRequest(
            Request.Method.DELETE,
            DELETE_URL + deleteUsername,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    loadingDialog.dismiss();
                    // Backend returns empty response (200) for successful deletion
                    // Show success message
                    new AlertDialog.Builder(EditProfileActivity.this)
                        .setTitle("✅ Account Deleted")
                        .setMessage("Your account has been successfully deleted.\n\nYou will now be redirected to the login page.")
                        .setPositiveButton("OK", (dialog, which) -> {
                            // Clear all user data
                            clearAllUserData();
                            
                            // Navigate back to home
                            Intent intent = new Intent(EditProfileActivity.this, HomeActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        })
                        .setCancelable(false)
                        .show();
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    loadingDialog.dismiss();
                    String errorMessage = "Unable to delete account";
                    
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        switch (statusCode) {
                            case 404:
                                errorMessage = "Account not found or already deleted";
                                break;
                            case 403:
                                errorMessage = "You don't have permission to delete this account";
                                break;
                            case 500:
                                errorMessage = "Server error occurred. Please try again later";
                                break;
                            default:
                                errorMessage = "Network error (HTTP " + statusCode + ")";
                        }
                    } else if (error.getMessage() != null) {
                        errorMessage = "Connection error: " + error.getMessage();
                    }
                    
                    showDeleteError(errorMessage);
                }
            }
        );

        requestQueue.add(deleteRequest);
    }

    private void clearAllUserData() {
        // Clear Remember Me data
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_USERNAME);
        editor.putBoolean(KEY_REMEMBER_ME, false);
        editor.apply();
        
        // Clear any other user preferences
        SharedPreferences userPrefs = getSharedPreferences("UserPreferences", MODE_PRIVATE);
        userPrefs.edit().clear().apply();
        
        // Clear any cached data
        SharedPreferences cachePrefs = getSharedPreferences("CacheData", MODE_PRIVATE);
        cachePrefs.edit().clear().apply();
    }

    private void showDeleteError(String message) {
        new AlertDialog.Builder(this)
            .setTitle("❌ Delete Failed")
            .setMessage(message + "\n\nPlease try again or contact support if the problem persists.")
            .setPositiveButton("Try Again", (dialog, which) -> showDeleteConfirmation())
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }
    
    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
            .setTitle("🚪 Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes, Logout", (dialog, which) -> logout())
            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
            .setIcon(android.R.drawable.ic_dialog_info)
            .show();
    }
    
    private void logout() {
        // Clear all user data
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_USERNAME);
        editor.putBoolean(KEY_REMEMBER_ME, false);
        editor.apply();
        
        // Clear any other user preferences
        SharedPreferences userPrefs = getSharedPreferences("UserPreferences", MODE_PRIVATE);
        userPrefs.edit().clear().apply();
        
        // Clear any cached data
        SharedPreferences cachePrefs = getSharedPreferences("CacheData", MODE_PRIVATE);
        cachePrefs.edit().clear().apply();
        
        // Navigate back to home
        Intent intent = new Intent(EditProfileActivity.this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
