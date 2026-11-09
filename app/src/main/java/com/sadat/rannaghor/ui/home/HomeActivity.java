package com.sadat.rannaghor.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.RecipeRepository;
import com.sadat.rannaghor.data.repository.CloudBackupManager;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.ui.detail.RecipeDetailActivity;
import com.sadat.rannaghor.ui.favorites.FavoritesActivity;
import com.sadat.rannaghor.ui.planner.PlannerActivity;
import com.sadat.rannaghor.ui.shopping.ShoppingListActivity;
import com.sadat.rannaghor.util.ReminderScheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * Home screen: the user adds ingredients as chips, taps "Find recipes",
 * and gets a ranked list of matching dishes from RecipeRepository.
 */
public class HomeActivity extends AppCompatActivity {

    private final List<String> ingredients = new ArrayList<>();
    private ChipGroup chipGroup;
    private EditText ingredientInput;
    private TextView emptyStateText;
    private RecipeAdapter adapter;
    private RecipeRepository repository;
    private FirebaseAuth auth;
    private boolean firebaseConfigured;
    private View accountPanel;
    private TextView accountButton, signupBanner;
    private EditText emailInput, passwordInput;
    private TextView loginButton, createAccountButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        repository = new RecipeRepository(this);
        firebaseConfigured = CloudBackupManager.isConfigured(this);
        if (firebaseConfigured) {
            auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) syncSignedInUser();
        }
        ReminderScheduler.scheduleDailyReminder(this);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        chipGroup = findViewById(R.id.chipGroupIngredients);
        ingredientInput = findViewById(R.id.editIngredient);
        emptyStateText = findViewById(R.id.textEmptyState);
        accountPanel = findViewById(R.id.accountPanel);
        accountButton = findViewById(R.id.buttonAccount);
        signupBanner = findViewById(R.id.textSignupBanner);
        emailInput = findViewById(R.id.editEmail);
        passwordInput = findViewById(R.id.editPassword);
        loginButton = findViewById(R.id.buttonLogin);
        createAccountButton = findViewById(R.id.buttonCreateAccount);
        ImageButton addButton = findViewById(R.id.buttonAddIngredient);
        TextView searchButton = findViewById(R.id.buttonFindRecipes);

        RecyclerView recyclerView = findViewById(R.id.recyclerRecipes);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecipeAdapter(this::openDetail);
        recyclerView.setAdapter(adapter);

        addButton.setOnClickListener(v -> addIngredientChip());
        searchButton.setOnClickListener(v -> searchRecipes());
        accountButton.setOnClickListener(v -> toggleAccountPanel());
        signupBanner.setOnClickListener(v -> toggleAccountPanel());
        loginButton.setOnClickListener(v -> authenticate(false));
        createAccountButton.setOnClickListener(v -> authenticate(true));

        findViewById(R.id.cardQuickEasy).setOnClickListener(v -> searchFromFeatured("Pasta"));
        findViewById(R.id.cardDinner).setOnClickListener(v -> searchFromFeatured("Chicken"));
        findViewById(R.id.cardVegetarian).setOnClickListener(v -> searchFromFeatured("Potato"));
        findViewById(R.id.cardBangladeshi).setOnClickListener(v -> searchFromFeatured("Rice"));
        findViewById(R.id.categoryQuick).setOnClickListener(v -> searchFromFeatured("Pasta"));
        findViewById(R.id.categoryDinner).setOnClickListener(v -> searchFromFeatured("Chicken"));
        findViewById(R.id.categoryVegetarian).setOnClickListener(v -> searchFromFeatured("Potato"));
        findViewById(R.id.categoryBangladeshi).setOnClickListener(v -> searchFromFeatured("Rice"));
        findViewById(R.id.ingredientSearchSection).setOnClickListener(v -> ingredientInput.requestFocus());

        updateAccountSummary();

        setupBottomNav();
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
                String reason = error.getLocalizedMessage() == null
                        ? "Check your internet connection and Firestore rules."
                        : error.getLocalizedMessage();
                Toast.makeText(this, getString(R.string.cloud_sync_failed, reason), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.cloud_sync_succeeded, Toast.LENGTH_SHORT).show();
            }
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
        String details = error == null || error.getLocalizedMessage() == null
                ? "Please check your details and try again."
                : error.getLocalizedMessage();
        Toast.makeText(this, getString(messageId, details), Toast.LENGTH_LONG).show();
    }

    private void addIngredientChip() {
        String text = ingredientInput.getText().toString().trim();
        if (text.isEmpty()) return;
        addIngredientChip(text);
    }

    private void addIngredientChip(String text) {
        for (String ingredient : ingredients) {
            if (ingredient.equalsIgnoreCase(text)) {
                Toast.makeText(this, "Ingredient already added", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        ingredients.add(text);
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            ingredients.remove(text);
            chipGroup.removeView(chip);
        });
        chipGroup.addView(chip);
        ingredientInput.setText("");
    }

    private void searchFromFeatured(String ingredient) {
        ingredients.clear();
        chipGroup.removeAllViews();
        addIngredientChip(ingredient);
        searchRecipes();
        NestedScrollView scrollView = findViewById(R.id.homeScrollView);
        scrollView.post(() -> scrollView.smoothScrollTo(0, findViewById(R.id.buttonFindRecipes).getBottom()));
    }

    private void searchRecipes() {
        if (ingredients.isEmpty()) {
            Toast.makeText(this, "Add at least one ingredient first", Toast.LENGTH_SHORT).show();
            return;
        }
        emptyStateText.setText(R.string.searching);
        emptyStateText.setVisibility(TextView.VISIBLE);

        repository.searchByIngredients(ingredients, new RecipeRepository.SearchCallback() {
            @Override
            public void onResult(List<Recipe> rankedRecipes) {
                adapter.submitList(rankedRecipes);
                emptyStateText.setVisibility(rankedRecipes.isEmpty() ? TextView.VISIBLE : TextView.GONE);
                if (rankedRecipes.isEmpty()) {
                    emptyStateText.setText(R.string.no_recipes_found);
                }
            }

            @Override
            public void onError(String message) {
                emptyStateText.setText(message);
                emptyStateText.setVisibility(TextView.VISIBLE);
            }
        });
    }

    private void openDetail(Recipe recipe) {
        Intent intent = new Intent(this, RecipeDetailActivity.class);
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id);
        startActivity(intent);
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_home);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_planner) {
                startActivity(new Intent(this, PlannerActivity.class));
                return true;
            } else if (id == R.id.nav_shopping) {
                startActivity(new Intent(this, ShoppingListActivity.class));
                return true;
            } else if (id == R.id.nav_favorites) {
                startActivity(new Intent(this, FavoritesActivity.class));
                return true;
            }
            return false;
        });
    }
}
