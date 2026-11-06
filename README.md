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
3. Choose **Skip for now** to use the app locally, or complete the Firebase setup below to sign in and sync your data.

This checkout has `gradle/wrapper/gradle-wrapper.properties` but is missing the Gradle wrapper scripts and JAR. Android Studio can use its configured Gradle installation; add the wrapper files if you need command-line builds or CI.

## Notes

- Recipe search needs internet for TheMealDB results; bundled recipes and saved favourites are local.
- The planner currently navigates one day at a time and offers bundled recipes plus saved favourites.
- `local.properties` and `app/google-services.json` are machine-specific and excluded from Git.
- The app has no automated tests yet.

## Enable Firebase login and cloud backup

1. Create a Firebase project and register an Android app with package name `com.sadat.rannaghor`.
2. Download `google-services.json` and place it in the `app/` folder. It is ignored by Git because it is specific to your Firebase project.
3. In Firebase Authentication, enable the **Email/Password** provider.
4. Create a Cloud Firestore database. In its **Rules** tab, paste the contents of [`firestore.rules`](firestore.rules) and publish them. The rules limit each signed-in user to their own backup.
5. Sync Gradle and run the app. Create an account or sign in. On first sign-in, local favourites, meal plans, and shopping items merge with the account backup.

Cloud data is stored in one user-scoped Firestore document. Keep the backup small; Firestore documents have a size limit.
