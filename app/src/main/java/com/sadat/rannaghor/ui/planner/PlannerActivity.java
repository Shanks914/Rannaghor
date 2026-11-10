package com.sadat.rannaghor.ui.planner;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.data.repository.CloudBackupManager;
import com.sadat.rannaghor.model.MealPlanEntry;
import com.sadat.rannaghor.ui.auth.AccountRequiredActivity;
import com.sadat.rannaghor.ui.detail.RecipeDetailActivity;
import com.sadat.rannaghor.ui.recipes.RecipesActivity;
import com.sadat.rannaghor.util.MealPlanIntents;
import com.sadat.rannaghor.util.PlanReminderScheduler;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Future-date meal planner. A selected meal slot continues into recipe search. */
public class PlannerActivity extends AccountRequiredActivity {
    private static final int REQUEST_RECIPE = 550;
    private static final int REQUEST_EXACT_ALARM = 552;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MealPlanAdapter adapter;
    private Calendar selectedDay;
    private String selectedDate;
    private String accountUid;
    private AppDatabase accountDatabase;
    private TextView selectedDateLabel, emptyState;
    private String pendingMealType, pendingCustomName, pendingMealDate;
    private long pendingMealTime;
    private boolean waitingForAlarmSettings;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!requireSignedInAccount()) return;
        accountUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        accountDatabase = AppDatabase.getInstanceForUser(getApplicationContext(), accountUid);
        setContentView(R.layout.activity_planner);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        selectedDay = Calendar.getInstance();
        String initialDate = getIntent().getStringExtra(MealPlanIntents.DATE);
        if (initialDate != null) {
            try {
                selectedDay.setTime(dateFormat.parse(initialDate));
                if (isBeforeToday(selectedDay)) selectedDay = Calendar.getInstance();
            } catch (Exception ignored) { selectedDay = Calendar.getInstance(); }
        }
        selectedDateLabel = findViewById(R.id.textSelectedDate);
        selectedDateLabel.setOnClickListener(v -> showDatePicker());
        findViewById(R.id.buttonAddMeal).setOnClickListener(v -> chooseMealType());

        RecyclerView recycler = findViewById(R.id.recyclerMealPlan);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MealPlanAdapter(this::openPlannedRecipe, this::confirmDelete);
        recycler.setAdapter(adapter);
        emptyState = findViewById(R.id.textPlannerEmpty);
        updateDateLabel();
        restoreAccountData(() -> {
            PlanReminderScheduler.scheduleAll(getApplicationContext());
            loadPlans();
        });
    }

    private void showDatePicker() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDay.set(year, month, day, 0, 0, 0);
            selectedDay.set(Calendar.MILLISECOND, 0);
            if (isBeforeToday(selectedDay)) selectedDay = today;
            updateDateLabel();
            loadPlans();
        }, selectedDay.get(Calendar.YEAR), selectedDay.get(Calendar.MONTH), selectedDay.get(Calendar.DAY_OF_MONTH));
        picker.getDatePicker().setMinDate(today.getTimeInMillis());
        picker.show();
    }

    private void chooseMealType() {
        String[] types = {"Breakfast", "Lunch", "Dinner", "Anything else"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("What are you planning?")
                .setItems(types, (dialog, which) -> {
                    if (which == 3) askCustomMealName();
                    else chooseTime(types[which], null);
                }).setNegativeButton("Cancel", null).show();
    }

    private void askCustomMealName() {
        EditText input = new EditText(this);
        input.setHint("e.g. Post workout meal");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Name this meal")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Next", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) Toast.makeText(this, "Enter a name for this meal", Toast.LENGTH_SHORT).show();
                    else chooseTime("Anything else", name);
                }).show();
    }

    private void chooseTime(String type, @Nullable String customName) {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (picker, hour, minute) -> {
            Calendar scheduled = (Calendar) selectedDay.clone();
            scheduled.set(Calendar.HOUR_OF_DAY, hour);
            scheduled.set(Calendar.MINUTE, minute);
            scheduled.set(Calendar.SECOND, 0);
            scheduled.set(Calendar.MILLISECOND, 0);
            if (scheduled.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(this, "Choose a future time for this meal", Toast.LENGTH_LONG).show();
                return;
            }
            pendingMealType = type;
            pendingCustomName = customName;
            pendingMealDate = dateFormat.format(selectedDay.getTime());
            pendingMealTime = scheduled.getTimeInMillis();
            continueIfAlarmAccessMissing();
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
    }

    private void continueIfAlarmAccessMissing() {
        if (PlanReminderScheduler.hasExactAlarmAccess(this)) {
            continueToRecipePicker();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Allow on-time meal reminders?")
                .setMessage("Rannaghor can use an exact alarm so the cooking reminder arrives before your planned meal time.")
                .setPositiveButton("Open settings", (d, w) -> {
                    try {
                        waitingForAlarmSettings = true;
                        startActivityForResult(PlanReminderScheduler.exactAlarmSettingsIntent(this), REQUEST_EXACT_ALARM);
                    }
                    catch (Exception unavailable) { continueToRecipePicker(); }
                })
                .setNegativeButton("Continue", (d, w) -> continueToRecipePicker())
                .show();
    }

    private void continueToRecipePicker() {
        if (pendingMealType == null || pendingMealDate == null || pendingMealTime <= 0) return;
        String mealType = pendingMealType;
        String customName = pendingCustomName;
        String mealDate = pendingMealDate;
        long mealTime = pendingMealTime;
        pendingMealType = null;
        pendingCustomName = null;
        pendingMealDate = null;
        pendingMealTime = 0L;
        Intent recipes = new Intent(this, RecipesActivity.class);
        recipes.putExtra(MealPlanIntents.FLOW, true);
        recipes.putExtra(MealPlanIntents.DATE, mealDate);
        recipes.putExtra(MealPlanIntents.TYPE, mealType);
        recipes.putExtra(MealPlanIntents.CUSTOM_NAME, customName);
        recipes.putExtra(MealPlanIntents.TIME, mealTime);
        startActivityForResult(recipes, REQUEST_RECIPE);
    }

    private void loadPlans() {
        selectedDate = dateFormat.format(selectedDay.getTime());
        updateDateLabel();
        executor.execute(() -> {
            String dateToLoad = selectedDate;
            List<MealPlanEntry> plans = accountDatabase.mealPlanDao().getForDate(dateToLoad);
            runOnUiThread(() -> {
                if (isFinishing() || !dateToLoad.equals(selectedDate)) return;
                adapter.submitList(plans);
                emptyState.setVisibility(plans.isEmpty() ? TextView.VISIBLE : TextView.GONE);
            });
        });
    }

    private void updateDateLabel() {
        selectedDate = dateFormat.format(selectedDay.getTime());
        Calendar today = Calendar.getInstance();
        selectedDateLabel.setText(isSameDay(selectedDay, today)
                ? "Today · " + displayDateFormat.format(selectedDay.getTime())
                : displayDateFormat.format(selectedDay.getTime()));
    }

    private boolean isBeforeToday(Calendar date) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        return date.before(today);
    }

    private boolean isSameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    private void openPlannedRecipe(MealPlanEntry entry) {
        Intent intent = new Intent(this, RecipeDetailActivity.class);
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, entry.recipeId);
        intent.putExtra("extra_recipe_name", entry.recipeName);
        intent.putExtra("extra_recipe_image", entry.imageUrl);
        intent.putExtra("extra_instructions", entry.instructions);
        intent.putExtra("extra_ingredients_raw", entry.ingredientsRaw);
        intent.putExtra(MealPlanIntents.PLAN_ID, entry.id);
        startActivity(intent);
    }

    private void confirmDelete(MealPlanEntry entry) {
        new MaterialAlertDialogBuilder(this)
                .setMessage("Remove " + entry.recipeName + " from this plan?")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Delete", (d, w) -> executor.execute(() -> {
                    accountDatabase.mealPlanDao().delete(entry);
                    PlanReminderScheduler.cancel(getApplicationContext(), entry.id, accountUid);
                    CloudBackupManager.backupForUser(getApplicationContext(), accountUid);
                    runOnUiThread(this::loadPlans);
                })).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RECIPE && resultCode == RESULT_OK) loadPlans();
        else if (requestCode == REQUEST_EXACT_ALARM) {
            waitingForAlarmSettings = false;
            continueToRecipePicker();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (waitingForAlarmSettings) {
            waitingForAlarmSettings = false;
            continueToRecipePicker();
        }
        if (adapter != null && accountDatabase != null) loadPlans();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && adapter != null && accountDatabase != null) loadPlans();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
