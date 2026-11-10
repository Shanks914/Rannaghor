package com.sadat.rannaghor.ui.detail;

import android.content.Intent;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.content.ActivityNotFoundException;
import android.text.InputType;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.bumptech.glide.Glide;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.RecipeRepository;
import com.sadat.rannaghor.data.repository.RecipeReviewRepository;
import com.sadat.rannaghor.data.repository.CloudBackupManager;
import com.sadat.rannaghor.data.repository.MealPlanRepository;
import com.sadat.rannaghor.model.Ingredient;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.model.RecipeReview;
import com.sadat.rannaghor.ui.auth.AuthActivity;
import com.sadat.rannaghor.ui.cooking.CookingActivity;
import com.sadat.rannaghor.ui.planner.PlannerActivity;
import com.sadat.rannaghor.util.MealPlanIntents;
import com.sadat.rannaghor.util.PlanReminderScheduler;

import java.util.List;
import java.util.Calendar;

public class RecipeDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_ID = "extra_recipe_id";

    private RecipeRepository repository;
    private Recipe currentRecipe;
    private ImageButton favoriteButton;
    private ImageButton planButton;
    private RecipeReviewRepository reviewRepository;
    private RecipeReviewAdapter reviewAdapter;
    private int selectedRating;
    private int pendingRating;
    private boolean pendingFavorite;
    private boolean pendingPlan;
    private static final int REQUEST_REVIEW_AUTH = 410;
    private static final int REQUEST_EXACT_ALARM = 411;
    private String pendingPlanType, pendingPlanCustomName, pendingPlanDate;
    private long pendingPlanTime;
    private boolean waitingForAlarmSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        repository = new RecipeRepository(this);
        reviewRepository = new RecipeReviewRepository(this);
        String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        favoriteButton = findViewById(R.id.buttonFavorite);
        planButton = findViewById(R.id.buttonPlanRecipe);
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
                String cachedName = getIntent().getStringExtra("extra_recipe_name");
                String cachedInstructions = getIntent().getStringExtra("extra_instructions");
                if (cachedName != null && cachedInstructions != null) {
                    Recipe cached = new Recipe(recipeId, cachedName,
                            getIntent().getStringExtra("extra_recipe_image"), cachedInstructions,
                            getIntent().getStringExtra("extra_ingredients_raw"));
                    currentRecipe = cached;
                    bindRecipe(cached);
                } else Toast.makeText(RecipeDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bindRecipe(Recipe recipe) {
        boolean planningFlow = getIntent().getBooleanExtra(MealPlanIntents.FLOW, false);
        ((TextView) findViewById(R.id.textRecipeTitle)).setText(recipe.name);
        ((TextView) findViewById(R.id.textInstructions)).setText(recipe.instructions);

        ImageView image = findViewById(R.id.imageRecipeDetail);
        Glide.with(this).load(recipe.imageUrl).centerCrop().into(image);

        favoriteButton.setVisibility(planningFlow ? android.view.View.GONE : android.view.View.VISIBLE);
        planButton.setVisibility(planningFlow ? android.view.View.GONE : android.view.View.VISIBLE);
        favoriteButton.setImageResource(recipe.isFavorite
                ? R.drawable.ic_heart_filled
                : R.drawable.ic_heart_outline);
        favoriteButton.setOnClickListener(v -> toggleFavorite());
        planButton.setOnClickListener(v -> {
            if (!isSignedIn()) { pendingPlan = true; openAuth(); }
            else promptPlanForRecipe();
        });
        findViewById(R.id.buttonAddToPlanner).setVisibility(planningFlow ? android.view.View.VISIBLE : android.view.View.GONE);
        findViewById(R.id.buttonAddToPlanner).setOnClickListener(v -> addRecipeFromPlannerFlow());
        int reviewsVisibility = planningFlow ? android.view.View.GONE : android.view.View.VISIBLE;
        findViewById(R.id.textRatingsHeading).setVisibility(reviewsVisibility);
        findViewById(R.id.textRatingSummary).setVisibility(reviewsVisibility);
        findViewById(R.id.layoutRatingStars).setVisibility(reviewsVisibility);
        findViewById(R.id.editRecipeReview).setVisibility(reviewsVisibility);
        findViewById(R.id.buttonSubmitReview).setVisibility(reviewsVisibility);
        findViewById(R.id.recyclerRecipeReviews).setVisibility(reviewsVisibility);
        findViewById(R.id.buttonStartCooking).setVisibility(planningFlow ? android.view.View.GONE : android.view.View.VISIBLE);
        if (!planningFlow) loadReviews();

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
            long planId = getIntent().getLongExtra(MealPlanIntents.PLAN_ID, -1L);
            if (planId > 0) {
                intent.putExtra(MealPlanIntents.PLAN_ID, planId);
                MealPlanRepository.setStatus(this, planId, "IN_PROGRESS");
            }
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
        if (requestCode == REQUEST_EXACT_ALARM) {
            waitingForAlarmSettings = false;
            continueSavingScheduledRecipe();
            return;
        }
        if (requestCode == REQUEST_REVIEW_AUTH) {
            if (resultCode != RESULT_OK || !isSignedIn()) {
                pendingRating = 0;
                pendingFavorite = false;
                pendingPlan = false;
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
                        if (pendingPlan) {
                            pendingPlan = false;
                            promptPlanForRecipe();
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
                        if (pendingPlan) {
                            pendingPlan = false;
                            promptPlanForRecipe();
                        }
                    }
                });
            });
        }
    }

    private void addRecipeFromPlannerFlow() {
        if (currentRecipe == null) return;
        String date = getIntent().getStringExtra(MealPlanIntents.DATE);
        String type = getIntent().getStringExtra(MealPlanIntents.TYPE);
        String customName = getIntent().getStringExtra(MealPlanIntents.CUSTOM_NAME);
        long scheduledAt = getIntent().getLongExtra(MealPlanIntents.TIME, 0L);
        if (date == null || type == null || scheduledAt <= System.currentTimeMillis()) {
            Toast.makeText(this, "This planned meal time has passed. Start again from the planner.", Toast.LENGTH_LONG).show();
            return;
        }
        saveToPlanner(date, type, customName, scheduledAt, true);
    }

    private void promptPlanForRecipe() {
        if (currentRecipe == null) return;
        String[] types = {"Breakfast", "Lunch", "Dinner", "Anything else"};
        new MaterialAlertDialogBuilder(this).setTitle("Choose a meal")
                .setItems(types, (dialog, which) -> {
                    if (which == 3) promptCustomMealName();
                    else promptPlanDate(types[which], null);
                }).setNegativeButton("Cancel", null).show();
    }

    private void promptCustomMealName() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setHint("e.g. Post workout meal");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new MaterialAlertDialogBuilder(this).setTitle("Name this meal").setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Next", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) Toast.makeText(this, "Enter a name for this meal", Toast.LENGTH_SHORT).show();
                    else promptPlanDate("Anything else", name);
                }).show();
    }

    private void promptPlanDate(String type, String customName) {
        Calendar today = Calendar.getInstance();
        Calendar chosen = (Calendar) today.clone();
        chosen.set(Calendar.HOUR_OF_DAY, 0); chosen.set(Calendar.MINUTE, 0);
        chosen.set(Calendar.SECOND, 0); chosen.set(Calendar.MILLISECOND, 0);
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            chosen.set(year, month, day);
            promptPlanTime(type, customName, chosen);
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMinDate(chosen.getTimeInMillis());
        picker.show();
    }

    private void promptPlanTime(String type, String customName, Calendar chosenDate) {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (view, hour, minute) -> {
            Calendar scheduled = (Calendar) chosenDate.clone();
            scheduled.set(Calendar.HOUR_OF_DAY, hour);
            scheduled.set(Calendar.MINUTE, minute);
            scheduled.set(Calendar.SECOND, 0);
            scheduled.set(Calendar.MILLISECOND, 0);
            if (scheduled.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(this, "Choose a future time", Toast.LENGTH_SHORT).show();
                return;
            }
            pendingPlanDate = MealPlanRepository.dateString(scheduled.getTimeInMillis());
            pendingPlanType = type;
            pendingPlanCustomName = customName;
            pendingPlanTime = scheduled.getTimeInMillis();
            continuePlanAfterAlarmPermissionCheck();
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
    }

    private void continuePlanAfterAlarmPermissionCheck() {
        if (PlanReminderScheduler.hasExactAlarmAccess(this)) {
            continueSavingScheduledRecipe();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Allow on-time meal reminders?")
                .setMessage("Rannaghor can use an exact alarm so the cooking reminder arrives before your planned meal time.")
                .setPositiveButton("Open settings", (dialog, which) -> {
                    try {
                        waitingForAlarmSettings = true;
                        startActivityForResult(PlanReminderScheduler.exactAlarmSettingsIntent(this), REQUEST_EXACT_ALARM);
                    }
                    catch (ActivityNotFoundException unavailable) { continueSavingScheduledRecipe(); }
                })
                .setNegativeButton("Continue", (dialog, which) -> continueSavingScheduledRecipe())
                .show();
    }

    private void continueSavingScheduledRecipe() {
        if (pendingPlanType == null || pendingPlanDate == null || pendingPlanTime <= 0) return;
        if (pendingPlanTime <= System.currentTimeMillis()) {
            Toast.makeText(this, "Choose a future time", Toast.LENGTH_SHORT).show();
            return;
        }
        String type = pendingPlanType;
        String customName = pendingPlanCustomName;
        String date = pendingPlanDate;
        long scheduledAt = pendingPlanTime;
        pendingPlanType = null;
        pendingPlanCustomName = null;
        pendingPlanDate = null;
        pendingPlanTime = 0L;
        saveToPlanner(date, type, customName, scheduledAt, false);
    }

    @Override protected void onResume() {
        super.onResume();
        if (waitingForAlarmSettings) {
            waitingForAlarmSettings = false;
            continueSavingScheduledRecipe();
        }
    }

    private void saveToPlanner(String date, String type, String customName, long scheduledAt, boolean returnToRecipePicker) {
        MealPlanRepository.save(this, currentRecipe, date, type, customName, scheduledAt, (entry, error) -> {
            if (isFinishing()) return;
            if (error != null || entry == null) {
                Toast.makeText(this, error == null ? "Could not add this meal" : error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            int lead = entry.durationMinutes;
            Toast.makeText(this, "Added to planner. We’ll remind you " + lead + " minutes before.", Toast.LENGTH_LONG).show();
            if (returnToRecipePicker) {
                setResult(RESULT_OK);
                finish();
            } else {
                Intent planner = new Intent(this, PlannerActivity.class);
                planner.putExtra(MealPlanIntents.DATE, date);
                startActivity(planner);
            }
        });
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
