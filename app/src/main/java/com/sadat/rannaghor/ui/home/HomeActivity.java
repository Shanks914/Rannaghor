package com.sadat.rannaghor.ui.home;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;

import com.google.firebase.auth.FirebaseAuth;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.CloudBackupManager;
import com.sadat.rannaghor.model.RecipeCategory;
import com.sadat.rannaghor.ui.category.RecipeCategoryActivity;
import com.sadat.rannaghor.ui.favorites.FavoritesActivity;
import com.sadat.rannaghor.ui.planner.PlannerActivity;
import com.sadat.rannaghor.ui.recipes.RecipesActivity;
import com.sadat.rannaghor.ui.shopping.ShoppingListActivity;
import com.sadat.rannaghor.util.ReminderScheduler;

/** Main navigation hub and account entry point. */
public class HomeActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private boolean firebaseConfigured;
    private View accountPanel;
    private TextView accountButton, signupBanner, loginButton, createAccountButton;
    private EditText emailInput, passwordInput;

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
        signupBanner = findViewById(R.id.textSignupBanner);
        emailInput = findViewById(R.id.editEmail);
        passwordInput = findViewById(R.id.editPassword);
        loginButton = findViewById(R.id.buttonLogin);
        createAccountButton = findViewById(R.id.buttonCreateAccount);

        accountButton.setOnClickListener(v -> toggleAccountPanel());
        signupBanner.setOnClickListener(v -> toggleAccountPanel());
        loginButton.setOnClickListener(v -> authenticate(false));
        createAccountButton.setOnClickListener(v -> authenticate(true));

        findViewById(R.id.actionRecipes).setOnClickListener(v -> startActivity(new Intent(this, RecipesActivity.class)));
        findViewById(R.id.actionHome).setOnClickListener(v -> ((NestedScrollView) findViewById(R.id.homeScrollView)).smoothScrollTo(0, 0));
        findViewById(R.id.actionPlanner).setOnClickListener(v -> startActivity(new Intent(this, PlannerActivity.class)));
        findViewById(R.id.actionShopping).setOnClickListener(v -> startActivity(new Intent(this, ShoppingListActivity.class)));
        findViewById(R.id.actionFavorites).setOnClickListener(v -> startActivity(new Intent(this, FavoritesActivity.class)));

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

    private void toggleAccountPanel() {
        if (auth != null && auth.getCurrentUser() != null) {
            Toast.makeText(this, "Signed in as " + auth.getCurrentUser().getEmail(), Toast.LENGTH_SHORT).show();
            return;
        }
        accountPanel.setVisibility(accountPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
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
        accountButton.setText(signedIn ? "Signed in" : "Sign in");
        if (signedIn) {
            String email = auth.getCurrentUser().getEmail();
            signupBanner.setText("Welcome back" + (email == null ? "" : ", " + email) + "  ·  Your recipes are backed up");
            accountPanel.setVisibility(View.GONE);
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
