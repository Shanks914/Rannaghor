package com.sadat.rannaghor.ui.favorites;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.RecipeRepository;
import com.sadat.rannaghor.ui.detail.RecipeDetailActivity;
import com.sadat.rannaghor.ui.home.RecipeAdapter;

public class FavoritesActivity extends AppCompatActivity {

    private RecipeRepository repository;
    private RecipeAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        repository = new RecipeRepository(this);
        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recyclerFavorites);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecipeAdapter(recipe -> {
            Intent intent = new Intent(this, RecipeDetailActivity.class);
            intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload every time the screen becomes visible, in case a favourite
        // was added or removed on the detail screen.
        TextView emptyText = findViewById(R.id.textEmptyFavorites);
        repository.getFavorites(favorites -> {
            adapter.submitList(favorites);
            emptyText.setVisibility(favorites.isEmpty() ? TextView.VISIBLE : TextView.GONE);
        });
    }
}
