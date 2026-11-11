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
6. Sync Gradle and run the app. Existing email/password accounts can still log in normally. New email signups use the verification-code flow; Google sign-in uses Android Credential Manager.

### Email-code signup and account services

Sending six-digit verification codes needs a secure server and an SMTP sender. Firebase Authentication's built-in email verification is a link, not a six-digit code. This repository includes callable Cloud Functions that generate, rate-limit, expire, and verify codes before creating an email/password account.

Cloud Functions deployment requires the Firebase **Blaze** billing plan. The plan is pay-as-you-go; no-cost quotas may cover light use, but charges are not guaranteed to be zero. No billing plan is enabled by this repository. Pick an SMTP provider and keep its credentials private.

1. Install Node.js 22 and the Firebase CLI, sign in with `firebase login`, and confirm `.firebaserc` points to your project.
2. In Firebase App Check, register the Android app with Play Integrity. For local debug builds, copy the debug App Check token from Logcat into the Firebase App Check debug-token settings.
3. Set SMTP secrets and a long random HMAC secret from a terminal; never commit them:

   ```text
   firebase functions:secrets:set SMTP_HOST
   firebase functions:secrets:set SMTP_PORT
   firebase functions:secrets:set SMTP_USER
   firebase functions:secrets:set SMTP_PASSWORD
   firebase functions:secrets:set SMTP_FROM
   firebase functions:secrets:set OTP_HMAC_SECRET
   ```

4. Deploy the server and security rules with `firebase deploy --only functions,firestore:rules`.
5. Build and install the Android app. New accounts receive a six-digit email code before the password screen appears.

The account screen supports password changes, Firebase password-reset emails, Google profile photos, gallery profile photos, and account deletion. Account deletion asks for the current sign-in credential, then removes that account's Firestore backup, profile picture, reviews, Firebase Authentication user, and local account database on the device.

Previously created accounts are not automatically removed. They can still sign in with their existing credentials and delete themselves from **Home → account → Delete account**. Deleting an account permanently removes its saved data and reviews.

Cloud data is stored in one user-scoped Firestore document. Keep the backup small; Firestore documents have a size limit.
