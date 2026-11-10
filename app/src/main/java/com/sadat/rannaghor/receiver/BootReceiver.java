package com.sadat.rannaghor.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.sadat.rannaghor.util.ReminderScheduler;
import com.sadat.rannaghor.util.PlanReminderScheduler;

/**
 * AlarmManager alarms are cleared when the phone restarts, so this
 * receiver re-schedules the daily reminder on boot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ReminderScheduler.scheduleDailyReminder(context);
            PlanReminderScheduler.scheduleAll(context.getApplicationContext());
        }
    }
}
