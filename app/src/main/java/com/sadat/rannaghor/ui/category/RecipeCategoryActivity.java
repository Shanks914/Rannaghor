package com.sadat.rannaghor.ui.category;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.RecipeRepository;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.model.RecipeCategory;
import com.sadat.rannaghor.ui.detail.RecipeDetailActivity;

public class RecipeCategoryActivity extends AppCompatActivity {
    public static final String EXTRA_CATEGORY = "extra_recipe_category";

    private RecipeCategoryAdapter adapter;
    private TextView message;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_category);

        String category = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (category == null) category = RecipeCategory.QUICK_EASY;
        final String categoryKey = category;

        findViewById(R.id.buttonCategoryBack).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.textCategoryTitle)).setText(RecipeCategory.title(categoryKey));
        ((TextView) findViewById(R.id.textCategorySubtitle)).setText(RecipeCategory.subtitle(categoryKey));
        message = findViewById(R.id.textCategoryMessage);
        RecyclerView list = findViewById(R.id.recyclerCategoryRecipes);
        list.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new RecipeCategoryAdapter(this::openRecipe);
        list.setAdapter(adapter);

        new RecipeRepository(this).searchByCategory(categoryKey, new RecipeRepository.SearchCallback() {
            @Override public void onResult(java.util.List<Recipe> recipes) {
                adapter.submitList(recipes);
                if (recipes.isEmpty()) {
                    message.setText("No recipes found in this collection yet.");
                    message.setVisibility(View.VISIBLE);
                } else {
                    message.setVisibility(View.GONE);
                    ((TextView) findViewById(R.id.textCategoryCount)).setText(recipes.size() + " recipes");
                }
            }
            @Override public void onError(String error) {
                message.setText(error);
                message.setVisibility(View.VISIBLE);
            }
        });
    }

    private void openRecipe(Recipe recipe) {
        Intent intent = new Intent(this, RecipeDetailActivity.class);
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id);
        startActivity(intent);
    }
}
