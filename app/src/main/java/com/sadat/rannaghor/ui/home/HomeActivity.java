package com.sadat.rannaghor.ui.home;

import android.content.Intent;
import android.os.Bundle;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.RecipeRepository;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        repository = new RecipeRepository(this);
        ReminderScheduler.scheduleDailyReminder(this);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        chipGroup = findViewById(R.id.chipGroupIngredients);
        ingredientInput = findViewById(R.id.editIngredient);
        emptyStateText = findViewById(R.id.textEmptyState);
        ImageButton addButton = findViewById(R.id.buttonAddIngredient);
        TextView searchButton = findViewById(R.id.buttonFindRecipes);

        RecyclerView recyclerView = findViewById(R.id.recyclerRecipes);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecipeAdapter(this::openDetail);
        recyclerView.setAdapter(adapter);

        addButton.setOnClickListener(v -> addIngredientChip());
        searchButton.setOnClickListener(v -> searchRecipes());

        setupBottomNav();
    }

    private void addIngredientChip() {
        String text = ingredientInput.getText().toString().trim();
        if (text.isEmpty()) return;
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
