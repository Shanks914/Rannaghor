package com.sadat.rannaghor.ui.cooking;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.service.CookingTimerService;
import com.sadat.rannaghor.data.repository.MealPlanRepository;
import com.sadat.rannaghor.util.MealPlanIntents;
import com.sadat.rannaghor.util.PlanReminderScheduler;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Step-by-step cooking mode with a live view of the shared background timer. */
public class CookingActivity extends AppCompatActivity {

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?i)(?:\\d+\\s*[-–]\\s*)?(\\d+)\\s*[- ]?\\s*(seconds?|secs?|sec|seconden|s|minutes?|mins?|min|minuten|m|hours?|hrs?|hr|uur|h)\\b");

    private final List<String> steps = new ArrayList<>();
    private int currentStep;
    private boolean timerOnly;
    private boolean receiverRegistered;
    private TextView stepCounter, stepText, startTimerButton;
    private TextView timerStatus, timerCountdown, pauseResumeButton;
    private View timerPanel, restartButton, endButton, navigation;
    private TextView cookingTitle;
    private long plannedMealId = -1L;

    private final BroadcastReceiver timerStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { renderTimer(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cooking);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());
        cookingTitle = findViewById(R.id.textCookingTitle);
        plannedMealId = getIntent().getLongExtra(MealPlanIntents.PLAN_ID, -1L);
        String notificationUid = getIntent().getStringExtra(PlanReminderScheduler.EXTRA_ACCOUNT_UID);
        if (notificationUid != null && (FirebaseAuth.getInstance().getCurrentUser() == null
                || !notificationUid.equals(FirebaseAuth.getInstance().getCurrentUser().getUid()))) {
            finish();
            return;
        }
        if (plannedMealId > 0) {
            MealPlanRepository.setStatus(this, plannedMealId, "IN_PROGRESS");
            android.app.NotificationManager notifications = getSystemService(android.app.NotificationManager.class);
            if (notifications != null) notifications.cancel((int) (plannedMealId % Integer.MAX_VALUE));
        }
        stepCounter = findViewById(R.id.textStepCounter);
        stepText = findViewById(R.id.textStepContent);
        startTimerButton = findViewById(R.id.buttonStartTimer);
        timerPanel = findViewById(R.id.layoutTimerPanel);
        timerStatus = findViewById(R.id.textTimerStatus);
        timerCountdown = findViewById(R.id.textTimerCountdown);
        pauseResumeButton = findViewById(R.id.buttonPauseResumeTimer);
        restartButton = findViewById(R.id.buttonRestartTimer);
        endButton = findViewById(R.id.buttonEndTimer);

        TextView next = findViewById(R.id.buttonNextStep);
        TextView previous = findViewById(R.id.buttonPrevStep);
        navigation = (View) next.getParent();
        next.setOnClickListener(v -> {
            if (currentStep < steps.size() - 1) { currentStep++; renderStep(); }
            else finishCooking();
        });
        previous.setOnClickListener(v -> {
            if (currentStep > 0) { currentStep--; renderStep(); }
        });
        startTimerButton.setOnClickListener(v -> {
            int seconds = getCurrentStepDurationSeconds();
            if (seconds > 0) CookingTimerService.start(this, seconds, "Step " + (currentStep + 1));
        });
        pauseResumeButton.setOnClickListener(v -> {
            CookingTimerService.TimerSnapshot state = CookingTimerService.getSnapshot(this);
            if (state.paused) CookingTimerService.resume(this);
            else CookingTimerService.pause(this);
        });
        restartButton.setOnClickListener(v -> CookingTimerService.restart(this));
        endButton.setOnClickListener(v -> CookingTimerService.stop(this));

        updateMode(getIntent());
        renderStep();
    }

    private void finishCooking() {
        if (plannedMealId > 0) {
            MealPlanRepository.setStatus(this, plannedMealId, "COMPLETED", () -> {
                if (isFinishing()) return;
                android.widget.Toast.makeText(this, "Meal marked completed", android.widget.Toast.LENGTH_SHORT).show();
                finish();
            });
            return;
        }
        finish();
    }

    @Override protected void onResume() {
        super.onResume();
        ContextCompat.registerReceiver(this, timerStateReceiver,
                new IntentFilter(CookingTimerService.ACTION_TIMER_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
        renderTimer();
    }

    @Override protected void onPause() {
        if (receiverRegistered) {
            unregisterReceiver(timerStateReceiver);
            receiverRegistered = false;
        }
        super.onPause();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        updateMode(intent);
        renderStep();
        renderTimer();
    }

    private void updateMode(Intent intent) {
        timerOnly = intent.getBooleanExtra(CookingTimerService.EXTRA_TIMER_ONLY, false);
        if (timerOnly) {
            String label = intent.getStringExtra(CookingTimerService.EXTRA_STEP_LABEL);
            cookingTitle.setText("Cooking timer");
            stepCounter.setVisibility(View.GONE);
            navigation.setVisibility(View.GONE);
            startTimerButton.setVisibility(View.GONE);
            steps.clear();
            steps.add(label == null || label.trim().isEmpty() ? "Timer" : label);
        } else {
            cookingTitle.setText("Cooking steps");
            stepCounter.setVisibility(View.VISIBLE);
            navigation.setVisibility(View.VISIBLE);
            steps.clear();
            steps.addAll(splitIntoSteps(intent.getStringExtra("extra_instructions")));
        }
        currentStep = Math.min(currentStep, Math.max(0, steps.size() - 1));
    }

    private List<String> splitIntoSteps(String instructions) {
        List<String> result = new ArrayList<>();
        if (instructions == null || instructions.trim().isEmpty()) {
            result.add("No instructions available for this recipe.");
            return result;
        }
        for (String line : instructions.split("\\r\\n|\\n|\\. ")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) result.add(trimmed.endsWith(".") ? trimmed : trimmed + ".");
        }
        if (result.isEmpty()) result.add(instructions.trim());
        return result;
    }

    private void renderStep() {
        String step = steps.get(currentStep);
        stepCounter.setText((currentStep + 1) + " / " + steps.size());
        stepText.setText(step);
        TextView next = findViewById(R.id.buttonNextStep);
        next.setText(currentStep == steps.size() - 1 ? "Finish cooking" : getString(R.string.next_step));
        if (!timerOnly) {
            int seconds = getCurrentStepDurationSeconds();
            CookingTimerService.TimerSnapshot state = CookingTimerService.getSnapshot(this);
            boolean showStart = seconds > 0 && !state.active;
            startTimerButton.setVisibility(showStart ? View.VISIBLE : View.GONE);
            if (showStart) startTimerButton.setText(getString(R.string.start_timer_dynamic, formatDuration(seconds)));
        }
        renderTimer();
    }

    private void renderTimer() {
        CookingTimerService.TimerSnapshot state = CookingTimerService.getSnapshot(this);
        if (!state.active && !state.completed) {
            timerPanel.setVisibility(View.GONE);
            if (!timerOnly) {
                int seconds = getCurrentStepDurationSeconds();
                startTimerButton.setVisibility(seconds > 0 ? View.VISIBLE : View.GONE);
            }
            return;
        }

        timerPanel.setVisibility(View.VISIBLE);
        timerCountdown.setText(formatClock(state.remainingMs));
        String label = state.stepLabel == null ? "Cooking timer" : state.stepLabel;
        timerStatus.setText(state.completed ? label + " · Finished"
                : (state.paused ? label + " · Paused" : label + " · Running"));
        pauseResumeButton.setText(state.paused ? "Resume" : "Pause");
        pauseResumeButton.setVisibility(state.completed ? View.GONE : View.VISIBLE);
        restartButton.setVisibility(View.VISIBLE);
        endButton.setVisibility(View.VISIBLE);
        if (!timerOnly) startTimerButton.setVisibility(View.GONE);
    }

    private int getCurrentStepDurationSeconds() {
        if (steps.isEmpty()) return 0;
        Matcher matcher = DURATION_PATTERN.matcher(steps.get(currentStep));
        long totalSeconds = 0;
        while (matcher.find()) {
            int amount;
            try { amount = Integer.parseInt(matcher.group(1)); }
            catch (NumberFormatException error) { continue; }
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            if (unit.startsWith("hour") || unit.startsWith("hr") || unit.equals("uur") || unit.equals("h")) totalSeconds += (long) amount * 3600;
            else if (unit.startsWith("min") || unit.equals("m")) totalSeconds += (long) amount * 60;
            else totalSeconds += amount;
        }
        return (int) Math.min(totalSeconds, Integer.MAX_VALUE / 1000);
    }

    private String formatDuration(int seconds) {
        if (seconds >= 3600 && seconds % 3600 == 0) return (seconds / 3600) + " hr";
        if (seconds >= 60 && seconds % 60 == 0) return (seconds / 60) + " min";
        if (seconds > 60) return (seconds / 60) + " min " + (seconds % 60) + " sec";
        return seconds + " sec";
    }

    private String formatClock(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return hours > 0 ? String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainder)
                : String.format(Locale.getDefault(), "%02d:%02d", minutes, remainder);
    }
}
