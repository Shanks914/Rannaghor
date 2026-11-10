package com.sadat.rannaghor.ui.shopping;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.data.repository.CloudBackupManager;
import com.sadat.rannaghor.model.Ingredient;
import com.sadat.rannaghor.model.MealPlanEntry;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.model.ShoppingItem;
import com.sadat.rannaghor.util.LocalRecipeLoader;
import com.sadat.rannaghor.ui.auth.AccountRequiredActivity;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shopping list with manual entry, sharing, and ingredient generation from this week's plan. */
public class ShoppingListActivity extends AccountRequiredActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ShoppingAdapter adapter;
    private Calendar selectedWeekStart;
    private TextView selectedWeekLabel;
    private AppDatabase accountDatabase;
    private String accountUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireSignedInAccount()) return;
        accountUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        accountDatabase = AppDatabase.getInstanceForUser(getApplicationContext(), accountUid);
        setContentView(R.layout.activity_shopping_list);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        selectedWeekStart = getWeekStart(Calendar.getInstance());
        selectedWeekLabel = findViewById(R.id.textSelectedWeek);
        findViewById(R.id.buttonPreviousWeek).setOnClickListener(v -> changeWeek(-1));
        findViewById(R.id.buttonNextWeek).setOnClickListener(v -> changeWeek(1));
        updateWeekLabel();

        RecyclerView recyclerView = findViewById(R.id.recyclerShopping);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ShoppingAdapter(this::onToggleItem);
        recyclerView.setAdapter(adapter);

        EditText itemInput = findViewById(R.id.editShoppingItem);
        findViewById(R.id.buttonAddItem).setOnClickListener(v -> {
            String text = itemInput.getText().toString().trim();
            if (!text.isEmpty()) {
                addItem(text);
                itemInput.setText("");
            }
        });

        findViewById(R.id.buttonShareList).setOnClickListener(v -> shareList());
        findViewById(R.id.buttonGenerateFromPlan).setOnClickListener(v -> generateFromSelectedWeek());

        restoreAccountData(this::loadItems);
    }

    private void addItem(String name) {
        ShoppingItem item = new ShoppingItem();
        item.name = name;
        item.quantity = "";
        item.isChecked = false;
        executor.execute(() -> {
            accountDatabase.shoppingDao().insert(item);
            CloudBackupManager.backupForUser(getApplicationContext(), accountUid);
            runOnUiThread(this::loadItems);
        });
    }

    private void onToggleItem(ShoppingItem item, boolean checked) {
        item.isChecked = checked;
        executor.execute(() -> {
            accountDatabase.shoppingDao().update(item);
            CloudBackupManager.backupForUser(getApplicationContext(), accountUid);
        });
    }

    private void loadItems() {
        executor.execute(() -> {
            List<ShoppingItem> items = accountDatabase.shoppingDao().getAll();
            runOnUiThread(() -> adapter.submitList(items));
        });
    }

    private void generateFromSelectedWeek() {
        executor.execute(() -> {
            String[] week = getSelectedWeekRange();
            List<MealPlanEntry> plannedMeals = accountDatabase.mealPlanDao().getForWeek(week[0], week[1]);
            if (plannedMeals.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, R.string.no_meals_this_week, Toast.LENGTH_SHORT).show());
                return;
            }

            Map<String, Recipe> localRecipes = new LinkedHashMap<>();
            for (Recipe recipe : LocalRecipeLoader.loadAll(getApplicationContext())) {
                localRecipes.put(recipe.id, recipe);
            }

            Map<String, ShoppingItem> mergedItems = new LinkedHashMap<>();
            for (ShoppingItem item : accountDatabase.shoppingDao().getAll()) {
                mergedItems.put(normalize(item.name), item);
            }

            int recipesProcessed = 0;
            Set<String> generatedIngredients = new HashSet<>();
            for (MealPlanEntry entry : plannedMeals) {
                Recipe recipe = entry.recipeId == null || entry.recipeId.isEmpty()
                        ? null : accountDatabase.recipeDao().getById(entry.recipeId);
                if (recipe == null || recipe.ingredientsRaw == null || recipe.ingredientsRaw.trim().isEmpty()) {
                    recipe = localRecipes.get(entry.recipeId);
                }
                if ((recipe == null || recipe.ingredientsRaw == null || recipe.ingredientsRaw.trim().isEmpty())
                        && entry.ingredientsRaw != null && !entry.ingredientsRaw.trim().isEmpty()) {
                    recipe = new Recipe(entry.recipeId, entry.recipeName, entry.imageUrl,
                            entry.instructions, entry.ingredientsRaw);
                }
                if (recipe == null) continue;

                List<Ingredient> ingredients = Ingredient.parseList(recipe.ingredientsRaw);
                if (ingredients.isEmpty()) continue;
                recipesProcessed++;
                for (Ingredient ingredient : ingredients) {
                    String key = normalize(ingredient.name);
                    if (key.isEmpty()) continue;
                    generatedIngredients.add(key);
                    ShoppingItem item = mergedItems.get(key);
                    if (item == null) {
                        item = new ShoppingItem();
                        item.name = ingredient.name.trim();
                        item.quantity = ingredient.measure == null ? "" : ingredient.measure.trim();
                        item.isChecked = false;
                        mergedItems.put(key, item);
                    } else {
                        item.quantity = mergeMeasures(item.quantity, ingredient.measure);
                    }
                }
            }

            if (recipesProcessed == 0) {
                runOnUiThread(() -> Toast.makeText(this, R.string.no_ingredients_in_meals, Toast.LENGTH_SHORT).show());
                return;
            }

            accountDatabase.runInTransaction(() -> {
                for (ShoppingItem item : mergedItems.values()) {
                    if (item.id == 0) accountDatabase.shoppingDao().insert(item);
                    else accountDatabase.shoppingDao().update(item);
                }
            });
            CloudBackupManager.backupForUser(getApplicationContext(), accountUid);
            int ingredientCount = generatedIngredients.size();
            int mealCount = recipesProcessed;
            runOnUiThread(() -> {
                loadItems();
                Toast.makeText(this,
                        getString(R.string.shopping_list_generated, mealCount, ingredientCount),
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private Calendar getWeekStart(Calendar day) {
        Calendar monday = (Calendar) day.clone();
        int daysSinceMonday = (monday.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        monday.add(Calendar.DAY_OF_YEAR, -daysSinceMonday);
        monday.set(Calendar.HOUR_OF_DAY, 0);
        monday.set(Calendar.MINUTE, 0);
        monday.set(Calendar.SECOND, 0);
        monday.set(Calendar.MILLISECOND, 0);
        return monday;
    }

    private String[] getSelectedWeekRange() {
        Calendar monday = (Calendar) selectedWeekStart.clone();
        Calendar sunday = (Calendar) monday.clone();
        sunday.add(Calendar.DAY_OF_YEAR, 6);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return new String[]{format.format(monday.getTime()), format.format(sunday.getTime())};
    }

    private void changeWeek(int offset) {
        selectedWeekStart.add(Calendar.WEEK_OF_YEAR, offset);
        updateWeekLabel();
    }

    private void updateWeekLabel() {
        Calendar end = (Calendar) selectedWeekStart.clone();
        end.add(Calendar.DAY_OF_YEAR, 6);
        String startText = new SimpleDateFormat("MMM d", Locale.getDefault()).format(selectedWeekStart.getTime());
        String endText = new SimpleDateFormat("MMM d", Locale.getDefault()).format(end.getTime());
        String year = new SimpleDateFormat("yyyy", Locale.getDefault()).format(end.getTime());
        if (selectedWeekStart.get(Calendar.YEAR) == end.get(Calendar.YEAR)
                && selectedWeekStart.get(Calendar.MONTH) == end.get(Calendar.MONTH)) {
            startText = new SimpleDateFormat("MMM d", Locale.getDefault()).format(selectedWeekStart.getTime());
            endText = new SimpleDateFormat("d", Locale.getDefault()).format(end.getTime());
        } else if (selectedWeekStart.get(Calendar.YEAR) != end.get(Calendar.YEAR)) {
            year = new SimpleDateFormat("yyyy", Locale.getDefault()).format(selectedWeekStart.getTime()) + " – " + year;
        }
        selectedWeekLabel.setText(startText + " – " + endText + ", " + year);
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String mergeMeasures(String current, String addition) {
        String oldMeasure = current == null ? "" : current.trim();
        String newMeasure = addition == null ? "" : addition.trim();
        if (newMeasure.isEmpty()) return oldMeasure;
        if (oldMeasure.isEmpty()) return newMeasure;
        for (String part : oldMeasure.split("\\s*\\+\\s*")) {
            if (part.equalsIgnoreCase(newMeasure)) return oldMeasure;
        }
        return oldMeasure + " + " + newMeasure;
    }

    /** Builds a plain-text list and hands it to any app the user picks (ACTION_SEND). */
    private void shareList() {
        executor.execute(() -> {
            List<ShoppingItem> items = accountDatabase.shoppingDao().getAll();
            StringBuilder sb = new StringBuilder("Shopping list (Rannaghor):\n");
            for (ShoppingItem item : items) {
                sb.append(item.isChecked ? "[x] " : "[ ] ").append(item.name);
                if (item.quantity != null && !item.quantity.isEmpty()) {
                    sb.append(" (").append(item.quantity).append(")");
                }
                sb.append("\n");
            }
            runOnUiThread(() -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
                startActivity(Intent.createChooser(shareIntent, "Share shopping list"));
            });
        });
    }
}
