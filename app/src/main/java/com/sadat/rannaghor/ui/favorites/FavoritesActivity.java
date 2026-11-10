package com.sadat.rannaghor.ui.favorites;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.RecipeRepository;
import com.sadat.rannaghor.ui.detail.RecipeDetailActivity;
import com.sadat.rannaghor.ui.home.RecipeAdapter;
import com.sadat.rannaghor.ui.auth.AccountRequiredActivity;

public class FavoritesActivity extends AccountRequiredActivity {

    private RecipeRepository repository;
    private RecipeAdapter adapter;
    private boolean accountDataReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireSignedInAccount()) return;
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
        restoreAccountData(() -> {
            accountDataReady = true;
            loadFavorites();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!accountDataReady) return;
        loadFavorites();
    }

    private void loadFavorites() {
        if (adapter == null || repository == null) return;
        // Reload every time the screen becomes visible, in case a favourite
        // was added or removed on the detail screen.
        TextView emptyText = findViewById(R.id.textEmptyFavorites);
        repository.getFavorites(favorites -> {
            adapter.submitList(favorites);
            emptyText.setVisibility(favorites.isEmpty() ? TextView.VISIBLE : TextView.GONE);
        });
    }
}
