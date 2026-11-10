package com.sadat.rannaghor.ui.home;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.Manifest;
import android.content.pm.PackageManager;

import com.google.firebase.auth.FirebaseAuth;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.CloudBackupManager;
import com.sadat.rannaghor.data.repository.RecipeReviewRepository;
import com.sadat.rannaghor.model.RecipeCategory;
import com.sadat.rannaghor.ui.category.RecipeCategoryActivity;
import com.sadat.rannaghor.ui.favorites.FavoritesActivity;
import com.sadat.rannaghor.ui.planner.PlannerActivity;
import com.sadat.rannaghor.ui.auth.AuthActivity;
import com.sadat.rannaghor.ui.recipes.RecipesActivity;
import com.sadat.rannaghor.ui.shopping.ShoppingListActivity;
import com.sadat.rannaghor.util.ReminderScheduler;

/** Main navigation hub and account entry point. */
public class HomeActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private boolean firebaseConfigured;
    private View accountPanel;
    private TextView accountButton, loginButton, createAccountButton;
    private EditText emailInput, passwordInput;
    private static final int REQUEST_ACCOUNT_AUTH = 411;
    private static final int REQUEST_PRIVATE_FEATURE_AUTH = 412;
    private Class<?> pendingDestination;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        firebaseConfigured = CloudBackupManager.isConfigured(this);
        if (firebaseConfigured) {
            auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) syncSignedInUser();
        }
        ReminderScheduler.scheduleDailyReminder(this);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        accountPanel = findViewById(R.id.accountPanel);
        accountButton = findViewById(R.id.buttonAccount);
        emailInput = findViewById(R.id.editEmail);
        passwordInput = findViewById(R.id.editPassword);
        loginButton = findViewById(R.id.buttonLogin);
        createAccountButton = findViewById(R.id.buttonCreateAccount);

        accountButton.setOnClickListener(v -> toggleAccountPanel());
        loginButton.setOnClickListener(v -> authenticate(false));
        createAccountButton.setOnClickListener(v -> authenticate(true));

        findViewById(R.id.actionRecipes).setOnClickListener(v -> startActivity(new Intent(this, RecipesActivity.class)));
        findViewById(R.id.actionPlanner).setOnClickListener(v -> openPersonalDestination(PlannerActivity.class));
        findViewById(R.id.actionShopping).setOnClickListener(v -> openPersonalDestination(ShoppingListActivity.class));
        findViewById(R.id.actionFavorites).setOnClickListener(v -> openPersonalDestination(FavoritesActivity.class));

        findViewById(R.id.categoryQuickEasy).setOnClickListener(v -> openCategory(RecipeCategory.QUICK_EASY));
        findViewById(R.id.categoryDinner).setOnClickListener(v -> openCategory(RecipeCategory.DINNER));
        findViewById(R.id.categoryVegetarian).setOnClickListener(v -> openCategory(RecipeCategory.VEGETARIAN));
        findViewById(R.id.categoryHealthy).setOnClickListener(v -> openCategory(RecipeCategory.HEALTHY));
        findViewById(R.id.categoryInstantPot).setOnClickListener(v -> openCategory(RecipeCategory.INSTANT_POT));
        findViewById(R.id.categoryVegan).setOnClickListener(v -> openCategory(RecipeCategory.VEGAN));
        findViewById(R.id.categorySoups).setOnClickListener(v -> openCategory(RecipeCategory.SOUPS));
        findViewById(R.id.categorySalads).setOnClickListener(v -> openCategory(RecipeCategory.SALADS));
        findViewById(R.id.categoryBangladeshi).setOnClickListener(v -> openCategory(RecipeCategory.BANGLADESHI));
        updateAccountSummary();
    }

    private void openCategory(String categoryId) {
        Intent intent = new Intent(this, RecipeCategoryActivity.class);
        intent.putExtra(RecipeCategoryActivity.EXTRA_CATEGORY, categoryId);
        startActivity(intent);
    }

    private void openPersonalDestination(Class<?> destination) {
        if (auth != null && auth.getCurrentUser() != null) {
            startActivity(new Intent(this, destination));
            return;
        }
        if (!firebaseConfigured) {
            Toast.makeText(this, R.string.firebase_setup_needed, Toast.LENGTH_LONG).show();
            return;
        }
        pendingDestination = destination;
        startActivityForResult(new Intent(this, AuthActivity.class), REQUEST_PRIVATE_FEATURE_AUTH);
    }

    private void toggleAccountPanel() {
        if (auth != null && auth.getCurrentUser() != null) {
            showAccountDialog();
            return;
        }
        accountPanel.setVisibility(accountPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void showAccountDialog() {
        if (auth == null || auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();
        View content = getLayoutInflater().inflate(R.layout.dialog_account, null, false);
        TextView email = content.findViewById(R.id.textAccountEmail);
        TextView ratingCount = content.findViewById(R.id.textAccountRatingCount);
        TextView reviewCount = content.findViewById(R.id.textAccountReviewCount);
        TextView countError = content.findViewById(R.id.textAccountCountError);
        email.setText(auth.getCurrentUser().getEmail() == null ? "Signed-in account" : auth.getCurrentUser().getEmail());
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .create();
        content.findViewById(R.id.buttonSwitchAccount).setOnClickListener(v -> {
            dialog.dismiss();
            startActivityForResult(new Intent(this, AuthActivity.class), REQUEST_ACCOUNT_AUTH);
        });
        content.findViewById(R.id.buttonAccountLogout).setOnClickListener(v -> {
            dialog.dismiss();
            auth.signOut();
            updateAccountSummary();
            Toast.makeText(this, "You are signed out", Toast.LENGTH_SHORT).show();
        });
        new RecipeReviewRepository(this).getUserReviewCounts(userId, new RecipeReviewRepository.UserReviewCountsCallback() {
            @Override public void onLoaded(int ratings, int writtenReviews) {
                if (dialog.isShowing()) {
                    ratingCount.setText(String.valueOf(ratings));
                    reviewCount.setText(String.valueOf(writtenReviews));
                }
            }
            @Override public void onError(Exception error) {
                if (dialog.isShowing()) {
                    ratingCount.setText("—");
                    reviewCount.setText("—");
                    countError.setVisibility(View.VISIBLE);
                }
            }
        });
        dialog.show();
    }

    private void authenticate(boolean createAccount) {
        if (!firebaseConfigured || auth == null) {
            Toast.makeText(this, R.string.firebase_setup_needed, Toast.LENGTH_LONG).show();
            return;
        }
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email address");
            return;
        }
        if (password.length() < 6) {
            passwordInput.setError("Use at least 6 characters");
            return;
        }
        setAuthButtonsEnabled(false);
        if (createAccount) {
            auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
                setAuthButtonsEnabled(true);
                if (task.isSuccessful() && task.getResult().getUser() != null) syncSignedInUser();
                else showAuthError(R.string.firebase_signup_failed, task.getException());
            });
        } else {
            auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
                setAuthButtonsEnabled(true);
                if (task.isSuccessful() && task.getResult().getUser() != null) syncSignedInUser();
                else showAuthError(R.string.firebase_login_failed, task.getException());
            });
        }
    }

    private void syncSignedInUser() {
        CloudBackupManager.restoreAndBackup(this, error -> {
            if (isFinishing()) return;
            updateAccountSummary();
            if (error != null) {
                String reason = error.getLocalizedMessage() == null ? "Check your internet connection and Firestore rules." : error.getLocalizedMessage();
                Toast.makeText(this, getString(R.string.cloud_sync_failed, reason), Toast.LENGTH_LONG).show();
            } else Toast.makeText(this, R.string.cloud_sync_succeeded, Toast.LENGTH_SHORT).show();
        });
    }

    private void updateAccountSummary() {
        boolean signedIn = auth != null && auth.getCurrentUser() != null;
        String email = signedIn ? auth.getCurrentUser().getEmail() : null;
        accountButton.setText(signedIn ? (email == null ? "My account" : email) : "Sign in");
        accountButton.setContentDescription(signedIn ? "Account for " + (email == null ? "signed-in user" : email) : "Sign in");
        if (signedIn) {
            accountPanel.setVisibility(View.GONE);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (accountButton != null) updateAccountSummary();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ACCOUNT_AUTH) {
            updateAccountSummary();
            if (resultCode == RESULT_OK && auth != null && auth.getCurrentUser() != null) syncSignedInUser();
        } else if (requestCode == REQUEST_PRIVATE_FEATURE_AUTH) {
            Class<?> destination = pendingDestination;
            pendingDestination = null;
            if (resultCode == RESULT_OK && auth != null && auth.getCurrentUser() != null && destination != null) {
                CloudBackupManager.restoreAndBackup(this, error -> {
                    if (isFinishing()) return;
                    updateAccountSummary();
                    if (error != null) Toast.makeText(this, "Could not sync cloud backup. Showing this account's saved data.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, destination));
                });
            } else {
                updateAccountSummary();
            }
        }
    }

    private void setAuthButtonsEnabled(boolean enabled) {
        loginButton.setEnabled(enabled);
        createAccountButton.setEnabled(enabled);
        loginButton.setAlpha(enabled ? 1f : 0.5f);
        createAccountButton.setAlpha(enabled ? 1f : 0.5f);
    }

    private void showAuthError(int messageId, Exception error) {
        String details = error == null || error.getLocalizedMessage() == null ? "Please check your details and try again." : error.getLocalizedMessage();
        Toast.makeText(this, getString(messageId, details), Toast.LENGTH_LONG).show();
    }
}
