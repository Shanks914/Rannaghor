# Rannaghor — Setup and Build Guide

Rannaghor is an incremental Android coursework project. The basic app has been run
through Step 3. This guide tracks the remaining setup and features; Firebase still
requires a project-specific configuration from the Firebase console.

Read this whole file once before you touch anything.

## What's already written for you

- Home screen: add ingredients, search TheMealDB, ranked results, and bundled local recipes
- Recipe detail screen: ingredients + instructions, favourite toggle
- Room database: favourites, meal plan, shopping list (all local/offline)
- Planner screen (browse days, add/remove saved or bundled recipes by meal slot)
- Shopping list screen (add/check off/share via Intent)
- Cooking mode with step-by-step instructions
- CookingTimerService (background timer) + TimerReceiver (notifies when done)
- ReminderReceiver + BootReceiver + ReminderScheduler (daily alarm, survives reboot)
- ShoppingListProvider (a basic, read-only ContentProvider)
- local_recipes.json (3 sample Bangladeshi recipes) + LocalRecipeLoader, merged into ingredient search

## What is NOT done yet — this is your real work

1. **Firebase is not wired up.** `LoginActivity` has a "Skip" button so you can run the
   app immediately. You still need to do Step 8 below to add real login + backup.
2. **The planner can be expanded further.** It navigates one day at a time and offers
   favourites plus bundled recipes. A full week-at-a-glance grid and recent search
   results picker would be useful follow-up improvements.
3. **The planner uses saved favourites and bundled recipes.** Recipes from new
   TheMealDB searches must be saved as favourites before they appear in the picker.
4. **Icons are placeholder vector shapes.** Swap them for nicer ones (Android Studio's
   Vector Asset tool, from the Material Icons set) once the app runs.
5. **No automated tests.** Not required by your syllabus, but mention in your report
   that you tested manually (list what you tested).
6. **Gradle wrapper files are absent.** The wrapper properties exist, but the wrapper
   scripts and JAR are missing. Open the project through Android Studio or restore the
   Gradle wrapper before building from a terminal.

---

## Step-by-step: from zip to running app

### Step 1 — Unzip and open
1. Unzip `Rannaghor.zip` somewhere permanent, e.g. `C:\Users\you\AndroidStudioProjects\Rannaghor`.
2. Open Android Studio → **Open** → select the `Rannaghor` folder (the one with `settings.gradle` in it).
3. Wait for Gradle sync. First sync is slow — don't interrupt it.

### Step 2 — Fix whatever Gradle sync complains about
Common first-sync issues and fixes:
- **"SDK not found"**: Tools → SDK Manager → install the SDK version shown in `app/build.gradle` (`compileSdk 34`).
- **Red underlines on imports**: File → Invalidate Caches / Restart.
- **Gradle plugin version mismatch**: Android Studio will usually offer an "Update" button — accept it.

### Step 3 — Run it
1. Connect your phone (enable Developer Options + USB debugging) or create an emulator
   (Device Manager → Create Device → a recent Pixel + API 34 image).
2. Click the green Run ▶ button.
3. You should land on the Login screen → tap "Skip for now" → you're on Home.
4. Type an ingredient (try `chicken`), tap the + button, tap "Find recipes".
   If you see results with photos, your networking + JSON parsing is working.

### Step 4 — Git
The project is now initialized as a Git repository. Continue committing each working
feature or fix with a clear message:
```
git init
git add .
git commit -m "Initial starter project - runs, search works"
```
Then create a GitHub repo and push. From here on, **commit after every feature you add
or fix** — your course explicitly checks for this.

### Step 5 — Push through each remaining screen
Open the app, tap through Favourites, Planner, Shopping list, and a recipe's
"Start Cooking" button. Fix anything that crashes. Commit after each fix.

### Step 6 — Merge local recipes into search
Done: local recipes are parsed from the bundled JSON, matched against entered
ingredients, and included in ranked results.

### Step 7 — Notifications permission and receivers
The app requests notification permission on Android 13+, schedules the daily reminder
when Home opens, declares the reminder receiver, and registers the timer completion
receiver. The cooking timer service declares its Android 14 foreground-service type.

### Step 8 — Firebase (do this once the rest works)
1. Go to https://console.firebase.google.com → Add project → name it Rannaghor →
   you do NOT need to enable Google Analytics for this project.
2. On the project page, click the Android icon → register your app using the
   **exact** package name `com.sadat.rannaghor`.
3. Get your debug SHA-1: in Android Studio, open the Gradle panel (right sidebar) →
   `Rannaghor > app > Tasks > android > signingReport` → double-click it → copy the
   SHA1 value from the Run panel output. Paste it into the Firebase Android app setup.
4. Download `google-services.json` and put it inside the `app/` folder
   (same level as `build.gradle` for the app module, i.e. `app/google-services.json`).
5. In `build.gradle` (project root, the top-level one), nothing changes — it's
   already declared as `apply false`.
6. In `app/build.gradle`, uncomment the line:
   `id 'com.google.gms.google-services'`
   and uncomment the three Firebase `implementation` lines.
7. In Firebase console → Authentication → Sign-in method → enable **Email/Password**
   (simplest for a beginner; Google Sign-In is more setup but gives a one-tap experience).
8. In Firebase console → Firestore Database → Create database → start in **test mode**
   for now (fine for a student project; tighten the rules before submitting if you can).
9. Sync Gradle again.
10. In `LoginActivity`, replace the TODO with real Firebase Authentication calls,
    e.g. `FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pass)` for
    sign-up and `.signInWithEmailAndPassword(...)` for login.
11. In `RecipeRepository.toggleFavorite()`, after saving to Room, also write the
    favourite to Firestore under the signed-in user's UID so it backs up online.
12. Make sure the **Spark (free) plan** is selected for the project — it is, by default.
    You never need to enter a card for anything this app does.
13. Commit.

### Step 9 — Polish for the demo
- Add loading spinners and "no internet" messages where missing.
- Swap placeholder icons for real ones.
- Test on a fresh install (uninstall, reinstall) to be sure first-run works.
- Re-read the lecture plan's "Demonstration Readiness" section and be ready to explain:
  architecture (the three layers: UI → Repository → API/Room/Firebase), the ingredient-
  matching algorithm, the background Service/Receiver flow, and why you chose Room
  alongside Firebase.

---

## Project structure
```
app/src/main/java/com/sadat/rannaghor/
  model/        Recipe, Ingredient, MealPlanEntry, ShoppingItem
  data/local/   Room: AppDatabase, RecipeDao, MealPlanDao, ShoppingDao
  data/remote/  MealDbApi (TheMealDB), ApiCallback
  data/repository/ RecipeRepository (the only thing the UI talks to)
  ui/           One package per screen: login, home, detail, favorites, planner, shopping, cooking
  service/      CookingTimerService
  receiver/     TimerReceiver, ReminderReceiver, BootReceiver
  provider/     ShoppingListProvider
  util/         ReminderScheduler, LocalRecipeLoader
app/src/main/assets/local_recipes.json   bundled Bangladeshi recipes
app/src/main/res/                        all layouts, colors, strings, icons
```

## API reference
TheMealDB free endpoint, no key needed:
- `https://www.themealdb.com/api/json/v1/1/filter.php?i=chicken` — search by ingredient
- `https://www.themealdb.com/api/json/v1/1/lookup.php?i=52772` — full recipe by id

Test these in your browser first if something looks broken — if the browser shows JSON,
the network code isn't the problem.

Good luck — ask me anything as you work through each step.
