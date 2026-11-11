# Rannaghor

Rannaghor is a Java Android app that helps users find recipes from ingredients they have, plan meals, and keep a shopping list.

## Features

- Search TheMealDB by ingredients and rank results by ingredient matches.
- Search three bundled Bangladeshi recipes: Bhuna Khichuri, Begun Bhorta, and Dim Bhuna.
- View recipe details and save favourites locally with Room.
- Plan meals by day using favourites or bundled recipes.
- Generate a merged shopping list from this week’s meal plan, add items manually, check them off, and share the list.
- Follow cooking steps; a background timer appears only on steps with a stated duration.
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
3. In Firebase Authentication, enable **Email/Password** and **Google** providers.
4. Add the Android app's SHA-1 and SHA-256 signing fingerprints in Firebase Project Settings, then download the refreshed `google-services.json` into `app/`. The Google provider's Web client ID must be present in that file.
5. Create a Cloud Firestore database. Publish the contents of [`firestore.rules`](firestore.rules) in its **Rules** tab.
6. Sync Gradle and run the app. Existing email/password accounts can still log in normally. New email signups use Firebase's email verification link; Google sign-in uses Android Credential Manager.

### Email-code signup and account services

New email/password accounts are created through Firebase Authentication and receive Firebase's built-in email verification link. This works on the Spark plan and does not require an SMTP sender, Cloud Functions, or a billing upgrade. After tapping the link, return to the app and tap **I've verified**. The app's password-reset option also uses Firebase's reset link.

The repository still contains an optional Cloud Functions implementation for numeric six-digit codes. Do not deploy those functions while keeping the project on Spark; the app no longer calls them. Numeric codes require a server and email sender, and deploying Cloud Functions requires Blaze billing. For free-plan setup, skip the `functions` directory and do not run `firebase deploy --only functions`.

For local Android builds, register the App Check debug token from Logcat in the Firebase App Check debug-token settings. For Play Store/release builds, configure Play Integrity.

The account screen supports password changes, Firebase password-reset emails, Google profile photos, gallery profile photos, and account deletion. Account deletion asks for the current sign-in credential, then removes that account's Firestore backup, profile picture, reviews, Firebase Authentication user, and local account database on the device.

Previously created accounts are not automatically removed. They can still sign in with their existing credentials and delete themselves from **Home → account → Delete account**. Deleting an account permanently removes its saved data and reviews.

Cloud data is stored in one user-scoped Firestore document. Keep the backup small; Firestore documents have a size limit.
