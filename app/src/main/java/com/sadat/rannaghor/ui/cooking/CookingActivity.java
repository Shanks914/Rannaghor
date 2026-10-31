package com.sadat.rannaghor.ui.cooking;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.service.CookingTimerService;

import java.util.ArrayList;
import java.util.List;

/**
 * Step-by-step cooking mode. TheMealDB returns instructions as one block
 * of text, so this splits it into steps by sentence/newline - good enough
 * for a student project, not perfect for every recipe.
 */
public class CookingActivity extends AppCompatActivity {

    private List<String> steps = new ArrayList<>();
    private int currentStep = 0;
    private TextView stepCounter, stepText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cooking);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());

        String instructions = getIntent().getStringExtra("extra_instructions");
        steps = splitIntoSteps(instructions);

        stepCounter = findViewById(R.id.textStepCounter);
        stepText = findViewById(R.id.textStepContent);

        Button next = findViewById(R.id.buttonNextStep);
        Button back = findViewById(R.id.buttonPrevStep);
        Button startTimer = findViewById(R.id.buttonStartTimer);

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
        startTimer.setOnClickListener(v ->
                CookingTimerService.start(this, 300, "Step " + (currentStep + 1))); // default 5 min, adjust per step

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
    }
}
