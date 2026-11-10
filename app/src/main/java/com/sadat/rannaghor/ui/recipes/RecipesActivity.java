package com.sadat.rannaghor.ui.recipes;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.RecipeRepository;
import com.sadat.rannaghor.data.repository.RecipeReviewRepository;
import com.sadat.rannaghor.model.RatedRecipe;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.ui.detail.RecipeDetailActivity;
import com.sadat.rannaghor.ui.home.RecipeAdapter;
import com.sadat.rannaghor.util.MealPlanIntents;

import java.util.ArrayList;
import java.util.List;

/** Search by recipe title or by ingredients and browse community top-rated recipes. */
public class RecipesActivity extends AppCompatActivity {
    private static final int REQUEST_PLANNED_RECIPE = 551;
    private final List<String> ingredients = new ArrayList<>();
    private boolean ingredientMode;
    private RecipeRepository repository;
    private RecipeAdapter resultsAdapter;
    private RatedRecipeAdapter popularAdapter;
    private EditText keywordInput, ingredientInput;
    private ChipGroup ingredientChips;
    private TextView status;
    private boolean planningFlow;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipes);
        planningFlow = getIntent().getBooleanExtra(MealPlanIntents.FLOW, false);
        if (planningFlow) {
            ((TextView) findViewById(R.id.textRecipesScreenTitle)).setText("Choose a recipe");
            ((TextView) findViewById(R.id.textRecipesScreenSubtitle)).setText("Find something delicious for your planned meal.");
            View chooseFavorite = findViewById(R.id.buttonChooseFavoriteRecipe);
            chooseFavorite.setVisibility(View.VISIBLE);
            chooseFavorite.setOnClickListener(v -> chooseFavoriteRecipe());
        }
        repository = new RecipeRepository(this);
        findViewById(R.id.buttonRecipesBack).setOnClickListener(v -> finish());
        keywordInput = findViewById(R.id.editRecipeKeyword);
        ingredientInput = findViewById(R.id.editRecipeIngredient);
        ingredientChips = findViewById(R.id.chipRecipeIngredients);
        status = findViewById(R.id.textRecipeSearchStatus);

        resultsAdapter = new RecipeAdapter(this::openRecipe);
        RecyclerView results = findViewById(R.id.recyclerRecipeSearchResults);
        results.setLayoutManager(new LinearLayoutManager(this));
        results.setAdapter(resultsAdapter);

        popularAdapter = new RatedRecipeAdapter(this::openRatedRecipe);
        RecyclerView popular = findViewById(R.id.recyclerMostLoved);
        popular.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        popular.setAdapter(popularAdapter);

        findViewById(R.id.buttonModeRecipeName).setOnClickListener(v -> selectMode(false));
        findViewById(R.id.buttonModeIngredients).setOnClickListener(v -> selectMode(true));
        findViewById(R.id.buttonAddRecipeIngredient).setOnClickListener(v -> addIngredient());
        findViewById(R.id.buttonSearchRecipes).setOnClickListener(v -> search());
        ingredientInput.setOnEditorActionListener((v, actionId, event) -> { addIngredient(); return true; });
        keywordInput.setOnEditorActionListener((v, actionId, event) -> { search(); return true; });
        selectMode(false);
        loadMostLoved();
    }

    private void selectMode(boolean byIngredients) {
        ingredientMode = byIngredients;
        findViewById(R.id.layoutRecipeNameSearch).setVisibility(byIngredients ? View.GONE : View.VISIBLE);
        findViewById(R.id.layoutIngredientSearch).setVisibility(byIngredients ? View.VISIBLE : View.GONE);
        int unselected = ContextCompat.getColor(this, R.color.text_secondary);
        TextView byName = findViewById(R.id.buttonModeRecipeName);
        TextView byIngredient = findViewById(R.id.buttonModeIngredients);
        byName.setBackgroundResource(byIngredients ? R.drawable.bg_rounded_card : R.drawable.bg_rounded_button);
        byName.setTextColor(byIngredients ? unselected : ContextCompat.getColor(this, R.color.white));
        byIngredient.setBackgroundResource(byIngredients ? R.drawable.bg_rounded_button : R.drawable.bg_rounded_card);
        byIngredient.setTextColor(byIngredients ? ContextCompat.getColor(this, R.color.white) : unselected);
    }

    private void addIngredient() {
        String value = ingredientInput.getText().toString().trim();
        if (value.isEmpty()) return;
        for (String current : ingredients) if (current.equalsIgnoreCase(value)) {
            Toast.makeText(this, "Ingredient already added", Toast.LENGTH_SHORT).show();
            return;
        }
        ingredients.add(value);
        Chip chip = new Chip(this);
        chip.setText(value);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> { ingredients.remove(value); ingredientChips.removeView(chip); });
        ingredientChips.addView(chip);
        ingredientInput.setText("");
    }

    private void search() {
        status.setText(R.string.searching);
        status.setVisibility(View.VISIBLE);
        RecipeRepository.SearchCallback callback = new RecipeRepository.SearchCallback() {
            @Override public void onResult(List<Recipe> recipes) {
                resultsAdapter.submitList(recipes);
                status.setVisibility(recipes.isEmpty() ? View.VISIBLE : View.GONE);
                if (recipes.isEmpty()) status.setText(R.string.no_recipes_found);
            }
            @Override public void onError(String message) { status.setText(message); status.setVisibility(View.VISIBLE); }
        };
        if (ingredientMode) {
            if (ingredients.isEmpty()) { status.setText("Add one or more ingredients first."); return; }
            repository.searchByIngredients(new ArrayList<>(ingredients), callback);
        } else {
            String keyword = keywordInput.getText().toString().trim();
            if (keyword.isEmpty()) { status.setText("Enter a recipe name to search."); return; }
            repository.searchByName(keyword, callback);
        }
    }

    private void loadMostLoved() {
        TextView empty = findViewById(R.id.textMostLovedEmpty);
        new RecipeReviewRepository(this).getMostLoved(new RecipeReviewRepository.PopularCallback() {
            @Override public void onLoaded(List<RatedRecipe> recipes) {
                popularAdapter.submitList(recipes);
                empty.setVisibility(recipes.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void onError(Exception error) {
                empty.setText("Could not load community ratings. Check your connection and Firestore rules.");
                empty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void openRecipe(Recipe recipe) {
        Intent intent = new Intent(this, RecipeDetailActivity.class);
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id);
        intent.putExtra("extra_recipe_name", recipe.name);
        intent.putExtra("extra_recipe_image", recipe.imageUrl);
        intent.putExtra("extra_instructions", recipe.instructions);
        intent.putExtra("extra_ingredients_raw", recipe.ingredientsRaw);
        if (planningFlow) {
            MealPlanIntents.copyDraft(getIntent(), intent);
            startActivityForResult(intent, REQUEST_PLANNED_RECIPE);
        } else startActivity(intent);
    }

    private void chooseFavoriteRecipe() {
        findViewById(R.id.buttonChooseFavoriteRecipe).setEnabled(false);
        repository.getFavorites(favorites -> {
            findViewById(R.id.buttonChooseFavoriteRecipe).setEnabled(true);
            if (favorites.isEmpty()) {
                Toast.makeText(this, "You haven’t saved any favourite recipes yet.", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[favorites.size()];
            for (int i = 0; i < favorites.size(); i++) names[i] = favorites.get(i).name;
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Choose a favourite")
                    .setItems(names, (dialog, which) -> openRecipe(favorites.get(which)))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void openRatedRecipe(RatedRecipe recipe) {
        openRecipe(new Recipe(recipe.id, recipe.name, recipe.imageUrl, "", ""));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PLANNED_RECIPE && resultCode == RESULT_OK) {
            setResult(RESULT_OK);
            finish();
        }
    }
}
