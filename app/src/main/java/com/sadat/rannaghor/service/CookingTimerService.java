package com.sadat.rannaghor.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.ui.cooking.CookingActivity;

/**
 * Runs a step timer in the background so it keeps counting even if the
 * user leaves the Cooking screen. Shows a notification, and fires a
 * broadcast through TimerReceiver when the time is up (see receiver/).
 */
public class CookingTimerService extends Service {

    public static final String EXTRA_SECONDS = "extra_seconds";
    public static final String EXTRA_STEP_LABEL = "extra_step_label";
    public static final String ACTION_TIMER_FINISHED = "com.sadat.rannaghor.TIMER_FINISHED";

    private static final String CHANNEL_ID = "cooking_timer_channel";
    private static final int NOTIFICATION_ID = 1001;

    private CountDownTimer countDownTimer;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int seconds = intent != null ? intent.getIntExtra(EXTRA_SECONDS, 60) : 60;
        String stepLabel = intent != null ? intent.getStringExtra(EXTRA_STEP_LABEL) : "Cooking step";

        createChannelIfNeeded();
        startForeground(NOTIFICATION_ID, buildNotification(stepLabel, seconds));

        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer((long) seconds * 1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                NotificationManager manager = getSystemService(NotificationManager.class);
                manager.notify(NOTIFICATION_ID, buildNotification(stepLabel, secondsLeft));
            }

            @Override
            public void onFinish() {
                Intent finished = new Intent(ACTION_TIMER_FINISHED);
                finished.setPackage(getPackageName());
                finished.putExtra(EXTRA_STEP_LABEL, stepLabel);
                sendBroadcast(finished);
                stopForeground(true);
                stopSelf();
            }
        }.start();

        return START_NOT_STICKY;
    }

    private Notification buildNotification(String stepLabel, int secondsLeft) {
        Intent openApp = new Intent(this, CookingActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(stepLabel)
                .setContentText(secondsLeft + "s remaining")
                .setSmallIcon(R.drawable.ic_timer)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Cooking timer", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // this service is only ever started, never bound
    }

    public static void start(Context context, int seconds, String stepLabel) {
        Intent intent = new Intent(context, CookingTimerService.class);
        intent.putExtra(EXTRA_SECONDS, seconds);
        intent.putExtra(EXTRA_STEP_LABEL, stepLabel);
        context.startForegroundService(intent);
    }
}
