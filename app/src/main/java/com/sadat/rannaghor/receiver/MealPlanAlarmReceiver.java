package com.sadat.rannaghor.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.data.repository.CloudBackupManager;
import com.sadat.rannaghor.model.MealPlanEntry;
import com.sadat.rannaghor.ui.cooking.CookingActivity;
import com.sadat.rannaghor.ui.planner.PlannerActivity;
import com.sadat.rannaghor.util.PlanReminderScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Handles the prep reminder, cooking action, dismissal and missed meal deadline. */
public class MealPlanAlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "planned_meal_channel";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override public void onReceive(Context context, Intent intent) {
        long planId = intent.getLongExtra(PlanReminderScheduler.EXTRA_PLAN_ID, -1);
        String uid = intent.getStringExtra(PlanReminderScheduler.EXTRA_ACCOUNT_UID);
        String action = intent.getAction();
        if (planId <= 0 || uid == null || uid.isEmpty() || action == null) return;
        Context app = context.getApplicationContext();
        PendingResult pendingResult = goAsync();
        EXECUTOR.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstanceForUser(app, uid);
                MealPlanEntry entry = db.mealPlanDao().getById(planId);
                if (entry == null) return;
                if (PlanReminderScheduler.ACTION_PREPARE.equals(action)) {
                    if ("PLANNED".equals(entry.status) && System.currentTimeMillis() < entry.scheduledAtMillis) {
                        showNotification(app, entry, uid);
                    }
                } else if (PlanReminderScheduler.ACTION_DUE.equals(action)) {
                    if ("PLANNED".equals(entry.status) && System.currentTimeMillis() >= entry.scheduledAtMillis) {
                        db.mealPlanDao().setStatus(planId, "DISMISSED");
                        CloudBackupManager.backupForUser(app, uid);
                        cancelNotification(app, planId);
                    }
                } else if (PlanReminderScheduler.ACTION_DISMISS.equals(action)) {
                    if ("PLANNED".equals(entry.status)) {
                        db.mealPlanDao().setStatus(planId, "DISMISSED");
                        CloudBackupManager.backupForUser(app, uid);
                    }
                    cancelNotification(app, planId);
                }
            } catch (Exception ignored) {
                // The planner remains usable if an alarm cannot be processed.
            } finally {
                pendingResult.finish();
            }
        });
    }

    private void showNotification(Context context, MealPlanEntry entry, String uid) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    "Planned meal reminders", NotificationManager.IMPORTANCE_HIGH));
        }
        int id = notificationId(entry.id);
        Intent openPlanner = new Intent(context, PlannerActivity.class)
                .putExtra("extra_plan_date", entry.date);
        PendingIntent content = PendingIntent.getActivity(context, id + 700000, openPlanner,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent startCooking = cookingIntent(context, entry).putExtra(PlanReminderScheduler.EXTRA_ACCOUNT_UID, uid);
        PendingIntent start = PendingIntent.getActivity(context, id + 800000, startCooking,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent dismiss = PlanReminderScheduler.actionIntent(context, entry.id, uid, PlanReminderScheduler.ACTION_DISMISS);
        String meal = entry.customMealName == null || entry.customMealName.trim().isEmpty()
                ? entry.mealType : entry.customMealName;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Time to prepare " + meal)
                .setContentText("Start " + entry.recipeName + " now for your planned meal.")
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .addAction(R.drawable.ic_timer, "Start cooking", start)
                .addAction(R.drawable.ic_timer, "Dismiss", dismiss);
        manager.notify(id, notification.build());
    }

    private Intent cookingIntent(Context context, MealPlanEntry entry) {
        return new Intent(context, CookingActivity.class)
                .putExtra(RecipeTimerExtras.RECIPE_ID, entry.recipeId)
                .putExtra("extra_recipe_name", entry.recipeName)
                .putExtra("extra_instructions", entry.instructions)
                .putExtra(RecipeTimerExtras.PLAN_ID, entry.id);
    }

    private void cancelNotification(Context context, long planId) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(notificationId(planId));
        PlanReminderScheduler.cancel(context, planId);
    }

    private int notificationId(long planId) { return (int) (planId % Integer.MAX_VALUE); }

    /** Keeps intent keys shared with cooking completion without coupling to repository code. */
    public static final class RecipeTimerExtras {
        public static final String RECIPE_ID = "extra_recipe_id";
        public static final String PLAN_ID = "extra_plan_id";
        private RecipeTimerExtras() { }
    }
}
