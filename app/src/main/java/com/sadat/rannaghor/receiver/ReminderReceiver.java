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
import com.sadat.rannaghor.ui.planner.PlannerActivity;

/**
 * Fired by an AlarmManager alarm (see ReminderScheduler) to show the daily
 * "plan tomorrow's meal" notification.
 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "daily_reminder_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Daily meal reminder", NotificationManager.IMPORTANCE_DEFAULT);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        Intent openPlanner = new Intent(context, PlannerActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, openPlanner,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer)
                .setContentTitle("Plan tomorrow's meals")
                .setContentText("Open Rannaghor to set up tomorrow's recipes")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        context.getSystemService(NotificationManager.class).notify(2001, notification.build());
    }
}
