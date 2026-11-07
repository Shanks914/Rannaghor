package com.sadat.rannaghor.ui.cooking;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.service.CookingTimerService;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step-by-step cooking mode. TheMealDB returns instructions as one block
 * of text, so this splits it into steps by sentence/newline - good enough
 * for a student project, not perfect for every recipe.
 */
public class CookingActivity extends AppCompatActivity {

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?i)(?:\\d+\\s*[-–]\\s*)?(\\d+)\\s*[- ]?\\s*(seconds?|secs?|sec|seconden|s|minutes?|mins?|min|minuten|m|hours?|hrs?|hr|uur|h)\\b");

    private List<String> steps = new ArrayList<>();
    private int currentStep = 0;
    private TextView stepCounter, stepText;
    private TextView startTimerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cooking);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());

        String instructions = getIntent().getStringExtra("extra_instructions");
        steps = splitIntoSteps(instructions);

        stepCounter = findViewById(R.id.textStepCounter);
        stepText = findViewById(R.id.textStepContent);

        TextView next = findViewById(R.id.buttonNextStep);
        TextView back = findViewById(R.id.buttonPrevStep);
        startTimerButton = findViewById(R.id.buttonStartTimer);

        next.setOnClickListener(v -> {
            if (currentStep < steps.size() - 1) {
                currentStep++;
                renderStep();
            }
        });
        back.setOnClickListener(v -> {
            if (currentStep > 0) {
                currentStep--;
                renderStep();
            }
        });
        startTimerButton.setOnClickListener(v -> {
            int seconds = getCurrentStepDurationSeconds();
            if (seconds <= 0) return;
            CookingTimerService.start(this, seconds, "Step " + (currentStep + 1));
            Toast.makeText(this,
                    getString(R.string.timer_started, formatDuration(seconds)),
                    Toast.LENGTH_SHORT).show();
        });

        renderStep();
    }

    private List<String> splitIntoSteps(String instructions) {
        List<String> result = new ArrayList<>();
        if (instructions == null || instructions.trim().isEmpty()) {
            result.add("No instructions available for this recipe.");
            return result;
        }
        String[] rawLines = instructions.split("\r\n|\n|\\. ");
        for (String line : rawLines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed.endsWith(".") ? trimmed : trimmed + ".");
            }
        }
        if (result.isEmpty()) result.add(instructions.trim());
        return result;
    }

    private void renderStep() {
        stepCounter.setText((currentStep + 1) + " / " + steps.size());
        stepText.setText(steps.get(currentStep));
        int seconds = getCurrentStepDurationSeconds();
        if (seconds <= 0) {
            startTimerButton.setVisibility(View.GONE);
        } else {
            startTimerButton.setVisibility(View.VISIBLE);
            startTimerButton.setText(getString(R.string.start_timer_dynamic, formatDuration(seconds)));
        }
    }

    private int getCurrentStepDurationSeconds() {
        Matcher matcher = DURATION_PATTERN.matcher(steps.get(currentStep));
        long totalSeconds = 0;
        while (matcher.find()) {
            int amount;
            try {
                amount = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException error) {
                continue;
            }
            String unit = matcher.group(2).toLowerCase(java.util.Locale.ROOT);
            if (unit.startsWith("hour") || unit.startsWith("hr") || unit.equals("uur") || unit.equals("h")) {
                totalSeconds += (long) amount * 3600;
            } else if (unit.startsWith("min") || unit.equals("m")) {
                totalSeconds += (long) amount * 60;
            } else {
                totalSeconds += amount;
            }
        }
        return (int) Math.min(totalSeconds, Integer.MAX_VALUE / 1000);
    }

    private String formatDuration(int seconds) {
        if (seconds >= 3600 && seconds % 3600 == 0) return (seconds / 3600) + " hr";
        if (seconds >= 60 && seconds % 60 == 0) return (seconds / 60) + " min";
        if (seconds > 60) return (seconds / 60) + " min " + (seconds % 60) + " sec";
        return seconds + " sec";
    }
}
