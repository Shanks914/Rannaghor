package com.sadat.rannaghor.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.sadat.rannaghor.receiver.ReminderReceiver;

import java.util.Calendar;

/** Schedules the repeating "plan tomorrow's meal" alarm. Call once from HomeActivity's first run, and again from BootReceiver. */
public class ReminderScheduler {

    private static final int REQUEST_CODE = 500;

    public static void scheduleDailyReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 19); // 7 PM reminder - change to suit you
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent);
    }
}
