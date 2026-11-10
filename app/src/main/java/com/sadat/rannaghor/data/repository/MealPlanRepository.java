package com.sadat.rannaghor.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.model.MealPlanEntry;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.util.PlanReminderScheduler;
import com.sadat.rannaghor.util.RecipeDurationEstimator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Creates and updates account-owned planner entries and their alarms. */
public final class MealPlanRepository {
    public interface SaveCallback { void onSaved(MealPlanEntry entry, Exception error); }
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MealPlanRepository() { }

    public static void save(Context context, Recipe recipe, String date, String type,
                            String customName, long scheduledAt, SaveCallback callback) {
        Context app = context.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            MAIN.post(() -> callback.onSaved(null, new IllegalStateException("Sign in to add meals to your planner.")));
            return;
        }
        String uid = user.getUid();
        MealPlanEntry entry = new MealPlanEntry();
        entry.date = date;
        entry.mealType = type;
        entry.customMealName = customName == null ? "" : customName;
        entry.recipeId = recipe.id;
        entry.recipeName = recipe.name;
        entry.imageUrl = recipe.imageUrl;
        entry.instructions = recipe.instructions;
        entry.ingredientsRaw = recipe.ingredientsRaw;
        entry.scheduledAtMillis = scheduledAt;
        entry.durationMinutes = RecipeDurationEstimator.minimumMinutes(recipe);
        entry.status = "PLANNED";
        EXECUTOR.execute(() -> {
            try {
                AppDatabase.getInstanceForUser(app, uid).mealPlanDao().insert(entry);
                PlanReminderScheduler.schedule(app, entry, uid);
                CloudBackupManager.backupForUser(app, uid);
                MAIN.post(() -> callback.onSaved(entry, null));
            } catch (Exception error) {
                MAIN.post(() -> callback.onSaved(null, error));
            }
        });
    }

    public static void setStatus(Context context, long planId, String status) {
        setStatus(context, planId, status, null);
    }

    public static void setStatus(Context context, long planId, String status, Runnable onComplete) {
        Context app = context.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || planId <= 0) {
            if (onComplete != null) MAIN.post(onComplete);
            return;
        }
        String uid = user.getUid();
        EXECUTOR.execute(() -> {
            AppDatabase.getInstanceForUser(app, uid).mealPlanDao().setStatus(planId, status);
            if ("COMPLETED".equals(status) || "DISMISSED".equals(status)) PlanReminderScheduler.cancel(app, planId, uid);
            CloudBackupManager.backupForUser(app, uid);
            if (onComplete != null) MAIN.post(onComplete);
        });
    }

    public static String dateString(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
    }
}
