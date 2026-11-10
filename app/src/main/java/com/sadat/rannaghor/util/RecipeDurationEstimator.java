package com.sadat.rannaghor.util;

import com.sadat.rannaghor.model.Recipe;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Estimates a conservative prep lead time from durations in recipe instructions. */
public final class RecipeDurationEstimator {
    private static final Pattern DURATION = Pattern.compile(
            "(?i)(\\d+)\\s*(hours?|hrs?|hr|h|minutes?|mins?|min|m)\\b");

    private RecipeDurationEstimator() { }

    public static int minimumMinutes(Recipe recipe) {
        if (recipe == null || recipe.instructions == null) return 30;
        String instructions = recipe.instructions.toLowerCase(Locale.ROOT)
                .replaceAll("\\bor\\s+for\\s+\\d+\\s*(minutes?|mins?|min|m|hours?|hrs?|hr|h)\\b", " ");
        Matcher matcher = DURATION.matcher(instructions);
        int total = 0;
        while (matcher.find()) {
            try {
                int amount = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2).toLowerCase(Locale.ROOT);
                total += (unit.startsWith("h") ? amount * 60 : amount);
            } catch (NumberFormatException ignored) { }
        }
        return total > 0 ? Math.max(5, Math.min(total, 24 * 60)) : 30;
    }
}
