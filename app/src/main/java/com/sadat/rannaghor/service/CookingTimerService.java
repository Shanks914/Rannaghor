package com.sadat.rannaghor.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.ui.cooking.CookingActivity;

/** Runs the cooking countdown in the foreground and exposes controls in its notification. */
public class CookingTimerService extends Service {

    public static final String EXTRA_SECONDS = "extra_seconds";
    public static final String EXTRA_STEP_LABEL = "extra_step_label";
    public static final String EXTRA_TIMER_ONLY = "extra_timer_only";
    public static final String ACTION_TIMER_FINISHED = "com.sadat.rannaghor.TIMER_FINISHED";
    public static final String ACTION_TIMER_STATE_CHANGED = "com.sadat.rannaghor.TIMER_STATE_CHANGED";
    public static final String ACTION_PAUSE = "com.sadat.rannaghor.TIMER_PAUSE";
    public static final String ACTION_RESUME = "com.sadat.rannaghor.TIMER_RESUME";
    public static final String ACTION_RESTART = "com.sadat.rannaghor.TIMER_RESTART";
    public static final String ACTION_STOP = "com.sadat.rannaghor.TIMER_STOP";

    private static final String CHANNEL_ID = "cooking_timer_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFS = "cooking_timer_state";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_PAUSED = "paused";
    private static final String KEY_COMPLETED = "completed";
    private static final String KEY_TOTAL_MS = "total_ms";
    private static final String KEY_REMAINING_MS = "remaining_ms";
    private static final String KEY_END_ELAPSED_MS = "end_elapsed_ms";
    private static final String KEY_STEP_LABEL = "step_label";

    private SharedPreferences preferences;
    private CountDownTimer countDownTimer;
    private long totalDurationMs;
    private long remainingMs;
    private long endElapsedMs;
    private String stepLabel = "Cooking step";
    private boolean active;
    private boolean paused;
    private boolean completed;
    private boolean foregroundStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        restoreState();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopTimer();
            return START_NOT_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            if (active) ensureForeground();
            if (active && !paused) pauseTimer();
            return START_STICKY;
        }
        if (ACTION_RESUME.equals(action)) {
            if (active) ensureForeground();
            if (active && paused) resumeTimer();
            return START_STICKY;
        }
        if (ACTION_RESTART.equals(action)) {
            if (totalDurationMs > 0) startTimer(totalDurationMs, stepLabel);
            return START_STICKY;
        }

        if (intent != null && intent.hasExtra(EXTRA_SECONDS)) {
            int seconds = Math.max(1, intent.getIntExtra(EXTRA_SECONDS, 1));
            startTimer((long) seconds * 1000L, intent.getStringExtra(EXTRA_STEP_LABEL));
        } else if (active) {
            ensureForeground();
            if (!paused) {
                remainingMs = Math.max(0, endElapsedMs - SystemClock.elapsedRealtime());
                if (remainingMs == 0) finishTimer();
                else startCountdown();
            } else {
                publishState();
                updateNotification();
            }
        }
        return active ? START_STICKY : START_NOT_STICKY;
    }

    private void startTimer(long durationMs, @Nullable String label) {
        cancelCountdown();
        totalDurationMs = Math.max(1000L, durationMs);
        remainingMs = totalDurationMs;
        endElapsedMs = SystemClock.elapsedRealtime() + remainingMs;
        stepLabel = label == null || label.trim().isEmpty() ? "Cooking step" : label;
        active = true;
        paused = false;
        completed = false;
        persistState();
        ensureForeground();
        publishState();
        updateNotification();
        startCountdown();
    }

    private void startCountdown() {
        cancelCountdown();
        remainingMs = Math.max(0, endElapsedMs - SystemClock.elapsedRealtime());
        if (remainingMs == 0) {
            finishTimer();
            return;
        }
        countDownTimer = new CountDownTimer(remainingMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                remainingMs = millisUntilFinished;
                persistState();
                publishState();
                updateNotification();
            }

            @Override
            public void onFinish() {
                finishTimer();
            }
        }.start();
    }

    private void pauseTimer() {
        remainingMs = Math.max(0, endElapsedMs - SystemClock.elapsedRealtime());
        if (remainingMs == 0) {
            finishTimer();
            return;
        }
        cancelCountdown();
        paused = true;
        persistState();
        publishState();
        updateNotification();
    }

    private void resumeTimer() {
        if (remainingMs <= 0) {
            finishTimer();
            return;
        }
        paused = false;
        endElapsedMs = SystemClock.elapsedRealtime() + remainingMs;
        persistState();
        ensureForeground();
        publishState();
        updateNotification();
        startCountdown();
    }

    private void finishTimer() {
        cancelCountdown();
        remainingMs = 0;
        endElapsedMs = 0;
        active = false;
        paused = false;
        completed = true;
        persistState();
        publishState();

        Intent finished = new Intent(ACTION_TIMER_FINISHED)
                .setPackage(getPackageName())
                .putExtra(EXTRA_STEP_LABEL, stepLabel);
        sendBroadcast(finished);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        if (foregroundStarted) {
            stopForeground(true);
            foregroundStarted = false;
        }
        stopSelf();
    }

    private void stopTimer() {
        cancelCountdown();
        active = false;
        paused = false;
        completed = false;
        totalDurationMs = 0;
        remainingMs = 0;
        endElapsedMs = 0;
        stepLabel = "Cooking step";
        persistState();
        publishState();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        if (foregroundStarted) {
            stopForeground(true);
            foregroundStarted = false;
        }
        stopSelf();
    }

    private void ensureForeground() {
        if (foregroundStarted) return;
        createNotificationChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        foregroundStarted = true;
    }

    private Notification buildNotification() {
        Intent openTimer = new Intent(this, CookingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_TIMER_ONLY, true)
                .putExtra(EXTRA_STEP_LABEL, stepLabel);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 0, openTimer,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle(stepLabel)
                .setContentText((paused ? "Paused · " : "") + formatClock(remainingMs) + " remaining")
                .setContentIntent(openPendingIntent)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setOnlyAlertOnce(true)
                .setOngoing(active);

        if (active) {
            builder.addAction(action(paused ? ACTION_RESUME : ACTION_PAUSE,
                    paused ? "Resume" : "Pause", 1002));
            builder.addAction(action(ACTION_RESTART, "Restart", 1003));
            builder.addAction(action(ACTION_STOP, "End", 1004));
        }
        return builder.build();
    }

    private NotificationCompat.Action action(String action, String title, int requestCode) {
        Intent intent = new Intent(this, CookingTimerService.class).setAction(action);
        PendingIntent pendingIntent = PendingIntent.getForegroundService(
                this, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Action.Builder(R.drawable.ic_timer, title, pendingIntent).build();
    }

    private void updateNotification() {
        if (!foregroundStarted) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Cooking timer", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows the active cooking timer and controls.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void publishState() {
        Intent update = new Intent(ACTION_TIMER_STATE_CHANGED)
                .setPackage(getPackageName())
                .putExtra(EXTRA_STEP_LABEL, stepLabel)
                .putExtra("timer_active", active)
                .putExtra("timer_paused", paused)
                .putExtra("timer_completed", completed)
                .putExtra("timer_remaining_ms", remainingMs)
                .putExtra("timer_total_ms", totalDurationMs);
        sendBroadcast(update);
    }

    private void persistState() {
        preferences.edit()
                .putBoolean(KEY_ACTIVE, active)
                .putBoolean(KEY_PAUSED, paused)
                .putBoolean(KEY_COMPLETED, completed)
                .putLong(KEY_TOTAL_MS, totalDurationMs)
                .putLong(KEY_REMAINING_MS, remainingMs)
                .putLong(KEY_END_ELAPSED_MS, endElapsedMs)
                .putString(KEY_STEP_LABEL, stepLabel)
                .apply();
    }

    private void restoreState() {
        active = preferences.getBoolean(KEY_ACTIVE, false);
        paused = preferences.getBoolean(KEY_PAUSED, false);
        completed = preferences.getBoolean(KEY_COMPLETED, false);
        totalDurationMs = preferences.getLong(KEY_TOTAL_MS, 0);
        remainingMs = preferences.getLong(KEY_REMAINING_MS, 0);
        endElapsedMs = preferences.getLong(KEY_END_ELAPSED_MS, 0);
        stepLabel = preferences.getString(KEY_STEP_LABEL, "Cooking step");
        if (active && !paused) remainingMs = Math.max(0, endElapsedMs - SystemClock.elapsedRealtime());
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private static String formatClock(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return String.format(java.util.Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    public static TimerSnapshot getSnapshot(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, MODE_PRIVATE);
        TimerSnapshot snapshot = new TimerSnapshot();
        snapshot.active = prefs.getBoolean(KEY_ACTIVE, false);
        snapshot.paused = prefs.getBoolean(KEY_PAUSED, false);
        snapshot.completed = prefs.getBoolean(KEY_COMPLETED, false);
        snapshot.totalMs = prefs.getLong(KEY_TOTAL_MS, 0);
        snapshot.remainingMs = prefs.getLong(KEY_REMAINING_MS, 0);
        snapshot.endElapsedMs = prefs.getLong(KEY_END_ELAPSED_MS, 0);
        snapshot.stepLabel = prefs.getString(KEY_STEP_LABEL, "");
        if (snapshot.active && !snapshot.paused) {
            snapshot.remainingMs = Math.max(0, snapshot.endElapsedMs - SystemClock.elapsedRealtime());
        }
        return snapshot;
    }

    public static void pause(Context context) { sendCommand(context, ACTION_PAUSE); }
    public static void resume(Context context) { sendCommand(context, ACTION_RESUME); }
    public static void restart(Context context) { sendCommand(context, ACTION_RESTART); }
    public static void stop(Context context) { sendCommand(context, ACTION_STOP); }

    private static void sendCommand(Context context, String action) {
        context.startService(new Intent(context, CookingTimerService.class).setAction(action));
    }

    public static void start(Context context, int seconds, String stepLabel) {
        Intent intent = new Intent(context, CookingTimerService.class)
                .putExtra(EXTRA_SECONDS, seconds)
                .putExtra(EXTRA_STEP_LABEL, stepLabel);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static class TimerSnapshot {
        public boolean active;
        public boolean paused;
        public boolean completed;
        public long totalMs;
        public long remainingMs;
        public long endElapsedMs;
        public String stepLabel;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelCountdown();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
