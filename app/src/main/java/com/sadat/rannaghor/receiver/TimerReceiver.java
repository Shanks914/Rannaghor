package com.sadat.rannaghor.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.sadat.rannaghor.service.CookingTimerService;

/**
 * Listens for the broadcast CookingTimerService sends when a step timer
 * finishes. Kept separate from the Service itself so other parts of the
 * app (or a future widget) could also react to "timer finished" without
 * depending on the Service class directly.
 */
public class TimerReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (CookingTimerService.ACTION_TIMER_FINISHED.equals(intent.getAction())) {
            Toast.makeText(context, "Timer finished!", Toast.LENGTH_LONG).show();
        }
    }
}
