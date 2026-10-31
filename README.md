# Rannaghor

Rannaghor is a Java Android app that helps users find recipes from ingredients they have, plan meals, and keep a shopping list.

## Features

- Search TheMealDB by ingredients and rank results by ingredient matches.
- Search three bundled Bangladeshi recipes: Bhuna Khichuri, Begun Bhorta, and Dim Bhuna.
- View recipe details and save favourites locally with Room.
- Plan meals by day using favourites or bundled recipes.
- Manage and share a shopping list.
- Follow cooking steps and run a background timer.
- Schedule a daily meal-planning reminder.

## Tech stack

Java, Android XML layouts, Room, TheMealDB REST API, Glide, AlarmManager, Android Services and BroadcastReceivers. Minimum SDK: 26; compile SDK: 34.

## Open and run

1. Open this folder in Android Studio.
2. Let Gradle sync, then select an emulator or connected Android device and press **Run**.
3. On the login screen, choose **Skip for now**; Firebase Authentication and cloud backup are not configured yet.

This checkout has `gradle/wrapper/gradle-wrapper.properties` but is missing the Gradle wrapper scripts and JAR. Android Studio can use its configured Gradle installation; add the wrapper files if you need command-line builds or CI.

## Notes

- Recipe search needs internet for TheMealDB results; bundled recipes and saved favourites are local.
- The planner currently navigates one day at a time and offers bundled recipes plus saved favourites.
- `local.properties` and `app/google-services.json` are machine-specific and excluded from Git.
- The app has no automated tests yet.
