package com.sadat.rannaghor.ui.planner;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.model.MealPlanEntry;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Weekly meal planner. A real build would let the user pick a recipe from
 * search/favourites to plan - to keep this starter file short, "Add" lets
 * you type a recipe name directly. Swap that dialog for a recipe picker
 * once your search screen is solid.
 */
public class PlannerActivity extends AppCompatActivity {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private MealPlanAdapter adapter;
    private String selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_planner);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        selectedDate = dateFormat.format(Calendar.getInstance().getTime());

        RecyclerView recyclerView = findViewById(R.id.recyclerMealPlan);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MealPlanAdapter(entry -> confirmDelete(entry));
        recyclerView.setAdapter(adapter);

        findViewById(R.id.buttonAddMeal).setOnClickListener(v -> showAddDialog());

        loadPlanForDate();
    }

    private void showAddDialog() {
        String[] mealTypes = {"Breakfast", "Lunch", "Dinner"};
        android.widget.EditText nameInput = new android.widget.EditText(this);
        nameInput.setHint("Recipe name");

        new AlertDialog.Builder(this)
                .setTitle("Add to " + selectedDate)
                .setView(nameInput)
                .setItems(mealTypes, (dialog, which) -> {
                    String recipeName = nameInput.getText().toString().trim();
                    if (recipeName.isEmpty()) {
                        Toast.makeText(this, "Enter a recipe name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveEntry(mealTypes[which], recipeName);
                })
                .show();
    }

    private void saveEntry(String mealType, String recipeName) {
        MealPlanEntry entry = new MealPlanEntry();
        entry.date = selectedDate;
        entry.mealType = mealType;
        entry.recipeName = recipeName;
        entry.recipeId = ""; // link to a real Recipe.id once using the recipe picker

        executor.execute(() -> {
            AppDatabase.getInstance(this).mealPlanDao().insert(entry);
            runOnUiThread(this::loadPlanForDate);
        });
    }

    private void confirmDelete(MealPlanEntry entry) {
        new AlertDialog.Builder(this)
                .setMessage("Remove " + entry.recipeName + " from the plan?")
                .setPositiveButton("Remove", (d, w) -> executor.execute(() -> {
                    AppDatabase.getInstance(this).mealPlanDao().delete(entry);
                    runOnUiThread(this::loadPlanForDate);
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadPlanForDate() {
        executor.execute(() -> {
            List<MealPlanEntry> entries = AppDatabase.getInstance(this).mealPlanDao().getForDate(selectedDate);
            runOnUiThread(() -> adapter.submitList(entries));
        });
    }
}
