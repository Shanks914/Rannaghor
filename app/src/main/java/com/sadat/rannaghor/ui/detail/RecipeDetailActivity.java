package com.sadat.rannaghor.ui.detail;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestoreException;

import com.bumptech.glide.Glide;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.RecipeRepository;
import com.sadat.rannaghor.data.repository.RecipeReviewRepository;
import com.sadat.rannaghor.data.repository.CloudBackupManager;
import com.sadat.rannaghor.model.Ingredient;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.model.RecipeReview;
import com.sadat.rannaghor.ui.auth.AuthActivity;
import com.sadat.rannaghor.ui.cooking.CookingActivity;

import java.util.List;

public class RecipeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_ID = "extra_recipe_id";

    private RecipeRepository repository;
    private Recipe currentRecipe;
    private ImageButton favoriteButton;
    private RecipeReviewRepository reviewRepository;
    private RecipeReviewAdapter reviewAdapter;
    private int selectedRating;
    private int pendingRating;
    private boolean pendingFavorite;
    private static final int REQUEST_REVIEW_AUTH = 410;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        repository = new RecipeRepository(this);
        reviewRepository = new RecipeReviewRepository(this);
        String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        favoriteButton = findViewById(R.id.buttonFavorite);
        RecyclerView reviews = findViewById(R.id.recyclerRecipeReviews);
        reviews.setLayoutManager(new LinearLayoutManager(this));
        reviewAdapter = new RecipeReviewAdapter();
        reviews.setAdapter(reviewAdapter);

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
        loadReviews();

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
        int[] stars = {R.id.ratingStar1, R.id.ratingStar2, R.id.ratingStar3, R.id.ratingStar4, R.id.ratingStar5};
        for (int i = 0; i < stars.length; i++) {
            final int rating = i + 1;
            findViewById(stars[i]).setOnClickListener(v -> {
                if (!isSignedIn()) { pendingRating = rating; openAuth(); return; }
                selectedRating = rating;
                renderStars();
            });
        }
        findViewById(R.id.buttonSubmitReview).setOnClickListener(v -> submitReview());
    }

    private void loadReviews() {
        if (currentRecipe == null) return;
        reviewRepository.getReviews(currentRecipe.id, new RecipeReviewRepository.ReviewsCallback() {
            @Override public void onLoaded(List<RecipeReview> reviews, double average) {
                String summary = reviews.isEmpty() ? "No ratings yet. Be the first to rate this recipe."
                        : String.format(java.util.Locale.getDefault(), "★ %.1f out of 5 · %d %s", average, reviews.size(), reviews.size() == 1 ? "rating" : "ratings");
                ((TextView) findViewById(R.id.textRatingSummary)).setText(summary);
                reviewAdapter.submitList(reviews);
                if (CloudBackupManager.isConfigured(RecipeDetailActivity.this) && FirebaseAuth.getInstance().getCurrentUser() != null) {
                    String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    for (RecipeReview review : reviews) if (uid.equals(review.userId)) selectedRating = review.rating;
                    renderStars();
                }
            }
            @Override public void onError(Exception error) {
                ((TextView) findViewById(R.id.textRatingSummary)).setText(reviewErrorMessage(error));
            }
        });
    }

    private void renderStars() {
        int[] ids = {R.id.ratingStar1, R.id.ratingStar2, R.id.ratingStar3, R.id.ratingStar4, R.id.ratingStar5};
        for (int i = 0; i < ids.length; i++) ((TextView) findViewById(ids[i])).setText(i < selectedRating ? "★" : "☆");
    }

    private void submitReview() {
        if (!isSignedIn()) { openAuth(); return; }
        if (selectedRating < 1 || selectedRating > 5) {
            Toast.makeText(this, "Tap 1 to 5 stars first", Toast.LENGTH_SHORT).show();
            return;
        }
        TextView submit = findViewById(R.id.buttonSubmitReview);
        submit.setEnabled(false);
        String reviewText = ((android.widget.EditText) findViewById(R.id.editRecipeReview)).getText().toString();
        reviewRepository.saveReview(currentRecipe.id, currentRecipe.name, currentRecipe.imageUrl, selectedRating, reviewText, error -> {
            submit.setEnabled(true);
            if (error != null) Toast.makeText(this, reviewErrorMessage(error), Toast.LENGTH_LONG).show();
            else {
                Toast.makeText(this, "Your rating was saved", Toast.LENGTH_SHORT).show();
                loadReviews();
            }
        });
    }

    private String reviewErrorMessage(Exception error) {
        if (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "Firebase denied review access. Publish the updated firestore.rules in Firebase Console → Firestore Database → Rules.";
        }
        String detail = error == null ? null : error.getLocalizedMessage();
        return detail == null || detail.trim().isEmpty() ? "Could not access recipe ratings. Check your connection and Firestore setup." : "Could not access recipe ratings: " + detail;
    }

    private boolean isSignedIn() {
        return CloudBackupManager.isConfigured(this) && FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    private void openAuth() {
        startActivityForResult(new Intent(this, AuthActivity.class), REQUEST_REVIEW_AUTH);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_REVIEW_AUTH) {
            if (resultCode != RESULT_OK || !isSignedIn()) {
                pendingRating = 0;
                pendingFavorite = false;
                return;
            }
            CloudBackupManager.restoreAndBackup(this, error -> {
                if (isFinishing() || currentRecipe == null) return;
                repository = new RecipeRepository(this);
                if (error != null) Toast.makeText(this, "Could not sync cloud backup. Using this account's saved data.", Toast.LENGTH_LONG).show();
                repository.getRecipeDetail(currentRecipe.id, new RecipeRepository.SingleRecipeCallback() {
                    @Override public void onResult(Recipe accountRecipe) {
                        currentRecipe = accountRecipe;
                        bindRecipe(accountRecipe);
                        if (pendingRating > 0) selectedRating = pendingRating;
                        pendingRating = 0;
                        renderStars();
                        if (pendingFavorite) {
                            pendingFavorite = false;
                            toggleFavorite();
                        }
                    }
                    @Override public void onError(String message) {
                        Toast.makeText(RecipeDetailActivity.this, message, Toast.LENGTH_LONG).show();
                        if (pendingFavorite) {
                            pendingFavorite = false;
                            toggleFavorite();
                        }
                        if (pendingRating > 0) selectedRating = pendingRating;
                        pendingRating = 0;
                        renderStars();
                        loadReviews();
                    }
                });
            });
        }
    }

    private void toggleFavorite() {
        if (currentRecipe == null) return;
        if (!isSignedIn()) {
            pendingFavorite = true;
            openAuth();
            return;
        }
        repository.toggleFavorite(currentRecipe, () -> {
            favoriteButton.setImageResource(currentRecipe.isFavorite
                    ? R.drawable.ic_heart_filled
                    : R.drawable.ic_heart_outline);
            String msg = currentRecipe.isFavorite ? "Saved to favourites" : "Removed from favourites";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }
}
