package com.sadat.rannaghor.ui.planner;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.data.repository.CloudBackupManager;
import com.sadat.rannaghor.model.MealPlanEntry;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.util.LocalRecipeLoader;
import com.sadat.rannaghor.ui.auth.AccountRequiredActivity;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Daily meal planner with date navigation and a picker for saved and bundled recipes. */
public class PlannerActivity extends AccountRequiredActivity {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private MealPlanAdapter adapter;
    private String selectedDate;
    private Calendar selectedDay;
    private TextView selectedDateLabel;
    private AppDatabase accountDatabase;
    private String accountUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireSignedInAccount()) return;
        accountUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        accountDatabase = AppDatabase.getInstanceForUser(getApplicationContext(), accountUid);
        setContentView(R.layout.activity_planner);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        selectedDay = Calendar.getInstance();
        selectedDateLabel = findViewById(R.id.textSelectedDate);
        findViewById(R.id.buttonPreviousDay).setOnClickListener(v -> changeDay(-1));
        findViewById(R.id.buttonNextDay).setOnClickListener(v -> changeDay(1));
        updateSelectedDate();

        RecyclerView recyclerView = findViewById(R.id.recyclerMealPlan);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MealPlanAdapter(entry -> confirmDelete(entry));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.buttonAddMeal).setOnClickListener(v -> showRecipePicker());
        restoreAccountData(this::loadPlanForDate);
    }

    private void showRecipePicker() {
        executor.execute(() -> {
            Map<String, Recipe> recipesById = new LinkedHashMap<>();
            for (Recipe recipe : LocalRecipeLoader.loadAll(getApplicationContext())) {
                recipesById.put(recipe.id, recipe);
            }
            for (Recipe recipe : accountDatabase.recipeDao().getFavorites()) {
                recipesById.put(recipe.id, recipe);
            }
            List<Recipe> recipes = new ArrayList<>(recipesById.values());
            runOnUiThread(() -> showRecipeChoices(recipes));
        });
    }

    private void showRecipeChoices(List<Recipe> recipes) {
        if (isFinishing()) return;
        if (recipes.isEmpty()) {
            Toast.makeText(this, "No recipes available. Save a favourite first.", Toast.LENGTH_LONG).show();
            return;
        }

        String[] recipeNames = new String[recipes.size()];
        for (int i = 0; i < recipes.size(); i++) recipeNames[i] = recipes.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("Choose a recipe")
                .setItems(recipeNames, (dialog, which) -> showMealTypeChoices(recipes.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMealTypeChoices(Recipe recipe) {
        String[] mealTypes = {"Breakfast", "Lunch", "Dinner"};
        new AlertDialog.Builder(this)
                .setTitle("Add " + recipe.name + " to")
                .setItems(mealTypes, (dialog, which) -> saveEntry(mealTypes[which], recipe))
                .show();
    }

    private void saveEntry(String mealType, Recipe recipe) {
        MealPlanEntry entry = new MealPlanEntry();
        entry.date = selectedDate;
        entry.mealType = mealType;
        entry.recipeName = recipe.name;
        entry.recipeId = recipe.id;

        executor.execute(() -> {
            accountDatabase.mealPlanDao().insert(entry);
            CloudBackupManager.backupForUser(getApplicationContext(), accountUid);
            runOnUiThread(this::loadPlanForDate);
        });
    }

    private void confirmDelete(MealPlanEntry entry) {
        new AlertDialog.Builder(this)
                .setMessage("Remove " + entry.recipeName + " from the plan?")
                .setPositiveButton("Remove", (d, w) -> executor.execute(() -> {
                    accountDatabase.mealPlanDao().delete(entry);
                    CloudBackupManager.backupForUser(getApplicationContext(), accountUid);
                    runOnUiThread(this::loadPlanForDate);
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadPlanForDate() {
        String dateToLoad = selectedDate;
        executor.execute(() -> {
            List<MealPlanEntry> entries = accountDatabase.mealPlanDao().getForDate(dateToLoad);
            runOnUiThread(() -> {
                if (!isFinishing() && dateToLoad.equals(selectedDate)) adapter.submitList(entries);
            });
        });
    }

    private void changeDay(int offset) {
        selectedDay.add(Calendar.DAY_OF_YEAR, offset);
        updateSelectedDate();
        loadPlanForDate();
    }

    private void updateSelectedDate() {
        selectedDate = dateFormat.format(selectedDay.getTime());
        String label = isToday(selectedDay) ? "Today" : displayDateFormat.format(selectedDay.getTime());
        selectedDateLabel.setText(label);
    }

    private boolean isToday(Calendar day) {
        Calendar today = Calendar.getInstance();
        return day.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
