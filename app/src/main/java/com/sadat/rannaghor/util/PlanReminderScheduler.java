package com.sadat.rannaghor.util;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.model.MealPlanEntry;
import com.sadat.rannaghor.receiver.MealPlanAlarmReceiver;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Schedules a pre-cooking reminder and an end-time status check for each planned meal. */
public final class PlanReminderScheduler {
    public static final String ACTION_PREPARE = "com.sadat.rannaghor.PLAN_PREPARE";
    public static final String ACTION_DUE = "com.sadat.rannaghor.PLAN_DUE";
    public static final String ACTION_DISMISS = "com.sadat.rannaghor.PLAN_DISMISS";
    public static final String EXTRA_PLAN_ID = "extra_plan_id";
    public static final String EXTRA_ACCOUNT_UID = "extra_account_uid";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private PlanReminderScheduler() { }

    public static boolean hasExactAlarmAccess(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
    }

    public static Intent exactAlarmSettingsIntent(Context context) {
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + context.getPackageName()));
    }

    public static void schedule(Context context, MealPlanEntry entry) {
        if (FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) schedule(context, entry, user.getUid());
    }

    public static void schedule(Context context, MealPlanEntry entry, String uid) {
        if (entry == null || entry.id <= 0 || entry.scheduledAtMillis <= 0
                || uid == null || uid.isEmpty()
                || "COMPLETED".equals(entry.status) || "DISMISSED".equals(entry.status)) return;
        long dueAt = entry.scheduledAtMillis;
        long prepareAt = Math.max(System.currentTimeMillis() + 1000L,
                dueAt - Math.max(5, entry.durationMinutes) * 60_000L);
        setAlarm(context, entry.id, uid, ACTION_PREPARE, prepareAt);
        setAlarm(context, entry.id, uid, ACTION_DUE, dueAt);
    }

    public static void cancel(Context context, long planId) {
        if (FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) cancel(context, planId, user.getUid());
    }

    public static void cancel(Context context, long planId, String uid) {
        cancelAlarm(context, planId, uid, ACTION_PREPARE);
        cancelAlarm(context, planId, uid, ACTION_DUE);
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications != null) notifications.cancel((int) (planId % Integer.MAX_VALUE));
    }

    public static void cancelAllForUser(Context context, String uid) {
        if (uid == null || uid.isEmpty()) return;
        EXECUTOR.execute(() -> {
            List<MealPlanEntry> plans = AppDatabase.getInstanceForUser(context, uid).mealPlanDao().getAll();
            for (MealPlanEntry plan : plans) {
                cancel(context, plan.id, uid);
            }
        });
    }

    public static void scheduleAll(Context context) {
        if (FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();
        EXECUTOR.execute(() -> {
            List<MealPlanEntry> plans = AppDatabase.getInstanceForUser(context, uid).mealPlanDao().getAll();
            for (MealPlanEntry plan : plans) schedule(context, plan, uid);
        });
    }

    private static void setAlarm(Context context, long planId, String uid, String action, long triggerAt) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pending = pendingIntent(context, planId, uid, action);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            else manager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        } catch (SecurityException exactAlarmUnavailable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            else manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        }
    }

    private static void cancelAlarm(Context context, long planId, String uid, String action) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(pendingIntent(context, planId, uid, action));
    }

    public static PendingIntent actionIntent(Context context, long planId, String uid, String action) {
        return pendingIntent(context, planId, uid, action);
    }

    private static PendingIntent pendingIntent(Context context, long planId, String uid, String action) {
        Intent intent = new Intent(context, MealPlanAlarmReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_PLAN_ID, planId)
                .putExtra(EXTRA_ACCOUNT_UID, uid);
        int requestCode = (int) ((planId * 31 + uid.hashCode()) * 31 + action.hashCode());
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
