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
import com.sadat.rannaghor.service.CookingTimerService;
import com.sadat.rannaghor.ui.cooking.CookingActivity;

/**
 * Listens for the broadcast CookingTimerService sends when a step timer
 * finishes. Kept separate from the Service itself so other parts of the
 * app (or a future widget) could also react to "timer finished" without
 * depending on the Service class directly.
 */
public class TimerReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "cooking_timer_finished_channel";
    private static final int NOTIFICATION_ID = 2002;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (CookingTimerService.ACTION_TIMER_FINISHED.equals(intent.getAction())) {
            createNotificationChannel(context);
            String step = intent.getStringExtra(CookingTimerService.EXTRA_STEP_LABEL);
            Intent openCooking = new Intent(context, CookingActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context, 1, openCooking,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_timer)
                    .setContentTitle("Cooking timer finished")
                    .setContentText(step == null || step.isEmpty() ? "Your cooking step is ready." : step + " is ready.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, notification.build());
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Cooking timer finished", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts you when a cooking step timer ends.");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
