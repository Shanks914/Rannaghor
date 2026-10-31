package com.sadat.rannaghor.ui.detail;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.RecipeRepository;
import com.sadat.rannaghor.model.Ingredient;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.ui.cooking.CookingActivity;

import java.util.List;

public class RecipeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_ID = "extra_recipe_id";

    private RecipeRepository repository;
    private Recipe currentRecipe;
    private ImageButton favoriteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        repository = new RecipeRepository(this);
        String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        favoriteButton = findViewById(R.id.buttonFavorite);

        if (recipeId == null) {
            Toast.makeText(this, "No recipe selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadRecipe(recipeId);
    }

    private void loadRecipe(String recipeId) {
        repository.getRecipeDetail(recipeId, new RecipeRepository.SingleRecipeCallback() {
            @Override
            public void onResult(Recipe recipe) {
                currentRecipe = recipe;
                bindRecipe(recipe);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(RecipeDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bindRecipe(Recipe recipe) {
        ((TextView) findViewById(R.id.textRecipeTitle)).setText(recipe.name);
        ((TextView) findViewById(R.id.textInstructions)).setText(recipe.instructions);

        ImageView image = findViewById(R.id.imageRecipeDetail);
        Glide.with(this).load(recipe.imageUrl).centerCrop().into(image);

        favoriteButton.setImageResource(recipe.isFavorite
                ? R.drawable.ic_heart_filled
                : R.drawable.ic_heart_outline);
        favoriteButton.setOnClickListener(v -> toggleFavorite());

        LinearLayout ingredientList = findViewById(R.id.layoutIngredients);
        ingredientList.removeAllViews();
        List<Ingredient> ingredients = Ingredient.parseList(recipe.ingredientsRaw);
        for (Ingredient ing : ingredients) {
            TextView row = new TextView(this);
            row.setPadding(0, 16, 0, 16);
            row.setText(getString(R.string.ingredient_row_format, ing.name, ing.measure));
            ingredientList.addView(row);
        }

        findViewById(R.id.buttonStartCooking).setOnClickListener(v -> {
            Intent intent = new Intent(this, CookingActivity.class);
            intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id);
            intent.putExtra("extra_recipe_name", recipe.name);
            intent.putExtra("extra_instructions", recipe.instructions);
            startActivity(intent);
        });
    }

    private void toggleFavorite() {
        if (currentRecipe == null) return;
        repository.toggleFavorite(currentRecipe, () -> {
            favoriteButton.setImageResource(currentRecipe.isFavorite
                    ? R.drawable.ic_heart_filled
                    : R.drawable.ic_heart_outline);
            String msg = currentRecipe.isFavorite ? "Saved to favourites" : "Removed from favourites";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }
}
