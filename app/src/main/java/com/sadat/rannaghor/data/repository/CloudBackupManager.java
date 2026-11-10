package com.sadat.rannaghor.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.model.MealPlanEntry;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.model.ShoppingItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Merges local Room data with a private Firestore backup for the signed-in user. */
public final class CloudBackupManager {

    public interface SyncCallback {
        void onComplete(@Nullable Exception error);
    }

    private static final ExecutorService DATABASE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_THREAD = new Handler(Looper.getMainLooper());
    private static final String BACKUP_DOCUMENT = "current";

    private CloudBackupManager() { }

    public static boolean isConfigured(Context context) {
        return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();
    }

    /** Restores cloud-only rows, merges local rows, then uploads the merged snapshot. */
    public static void restoreAndBackup(Context context, SyncCallback callback) {
        Context appContext = context.getApplicationContext();
        if (!isConfigured(appContext)) {
            complete(callback, new IllegalStateException("Firebase is not configured."));
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            complete(callback, new IllegalStateException("No signed-in Firebase user."));
            return;
        }

        DocumentReference backup = backupDocument(user.getUid());
        AppDatabase db = AppDatabase.getInstanceForUser(appContext, user.getUid());
        backup.get().addOnSuccessListener(snapshot -> DATABASE_EXECUTOR.execute(() -> {
            try {
                Map<String, Recipe> favorites = mergeFavorites(snapshot, db);
                Map<String, MealPlanEntry> plans = mergeMealPlans(snapshot, db);
                Map<String, ShoppingItem> shoppingItems = mergeShoppingItems(snapshot, db);

                db.runInTransaction(() -> {
                    db.recipeDao().deleteFavorites();
                    for (Recipe recipe : favorites.values()) {
                        recipe.isFavorite = true;
                        db.recipeDao().insert(recipe);
                    }
                    db.mealPlanDao().clearAll();
                    for (MealPlanEntry entry : plans.values()) db.mealPlanDao().insert(entry);
                    db.shoppingDao().clearAll();
                    for (ShoppingItem item : shoppingItems.values()) db.shoppingDao().insert(item);
                });

                backup.set(makeBackup(favorites.values(), plans.values(), shoppingItems.values()))
                        .addOnSuccessListener(unused -> complete(callback, null))
                        .addOnFailureListener(error -> complete(callback, error));
            } catch (Exception error) {
                complete(callback, error);
            }
        })).addOnFailureListener(error -> complete(callback, error));
    }

    /** Writes the current local snapshot after a favorite, plan, or list change. */
    public static void backup(Context context) {
        Context appContext = context.getApplicationContext();
        if (!isConfigured(appContext)) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        backupForUser(appContext, user.getUid());
    }

    /** Writes a specific account snapshot; callers should capture the uid at the start of an edit. */
    public static void backupForUser(Context context, String uid) {
        Context appContext = context.getApplicationContext();
        if (uid == null || uid.isEmpty() || !isConfigured(appContext)) return;
        AppDatabase db = AppDatabase.getInstanceForUser(appContext, uid);

        DATABASE_EXECUTOR.execute(() -> {
            try {
                List<Recipe> favorites = db.recipeDao().getFavorites();
                List<MealPlanEntry> plans = db.mealPlanDao().getAll();
                List<ShoppingItem> items = db.shoppingDao().getAll();
                backupDocument(uid).set(makeBackup(favorites, plans, items));
            } catch (Exception ignored) {
                // Local Room data remains available; a later update or sign-in retries the backup.
            }
        });
    }

    private static DocumentReference backupDocument(String uid) {
        return FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("backups").document(BACKUP_DOCUMENT);
    }

    private static Map<String, Recipe> mergeFavorites(DocumentSnapshot snapshot, AppDatabase db) {
        Map<String, Recipe> merged = new LinkedHashMap<>();
        for (Map<String, Object> values : readMapList(snapshot.get("favorites"))) {
            Recipe recipe = recipeFrom(values);
            if (!recipe.id.isEmpty()) merged.put(recipe.id, recipe);
        }
        for (Recipe recipe : db.recipeDao().getFavorites()) merged.put(recipe.id, recipe);
        return merged;
    }

    private static Map<String, MealPlanEntry> mergeMealPlans(DocumentSnapshot snapshot, AppDatabase db) {
        Map<String, MealPlanEntry> merged = new LinkedHashMap<>();
        for (Map<String, Object> values : readMapList(snapshot.get("mealPlans"))) {
            MealPlanEntry entry = mealPlanFrom(values);
            if (validPlan(entry)) merged.put(planKey(entry), entry);
        }
        for (MealPlanEntry entry : db.mealPlanDao().getAll()) merged.put(planKey(entry), copyPlan(entry));
        return merged;
    }

    private static Map<String, ShoppingItem> mergeShoppingItems(DocumentSnapshot snapshot, AppDatabase db) {
        Map<String, ShoppingItem> merged = new LinkedHashMap<>();
        for (Map<String, Object> values : readMapList(snapshot.get("shoppingItems"))) {
            ShoppingItem item = shoppingItemFrom(values);
            if (item.name != null && !item.name.trim().isEmpty()) merged.put(shoppingKey(item), item);
        }
        for (ShoppingItem item : db.shoppingDao().getAll()) merged.put(shoppingKey(item), copyShoppingItem(item));
        return merged;
    }

    private static Map<String, Object> makeBackup(Iterable<Recipe> favorites,
                                                   Iterable<MealPlanEntry> plans,
                                                   Iterable<ShoppingItem> items) {
        List<Map<String, Object>> favoriteValues = new ArrayList<>();
        for (Recipe recipe : favorites) {
            Map<String, Object> value = new HashMap<>();
            value.put("id", recipe.id);
            value.put("name", recipe.name);
            value.put("imageUrl", recipe.imageUrl);
            value.put("instructions", recipe.instructions);
            value.put("ingredientsRaw", recipe.ingredientsRaw);
            value.put("isFavorite", true);
            favoriteValues.add(value);
        }
        List<Map<String, Object>> planValues = new ArrayList<>();
        for (MealPlanEntry entry : plans) {
            Map<String, Object> value = new HashMap<>();
            value.put("date", entry.date);
            value.put("mealType", entry.mealType);
            value.put("recipeId", entry.recipeId);
            value.put("recipeName", entry.recipeName);
            value.put("scheduledAtMillis", entry.scheduledAtMillis);
            value.put("durationMinutes", entry.durationMinutes);
            value.put("status", entry.status == null ? "PLANNED" : entry.status);
            value.put("customMealName", entry.customMealName);
            value.put("imageUrl", entry.imageUrl);
            value.put("instructions", entry.instructions);
            value.put("ingredientsRaw", entry.ingredientsRaw);
            planValues.add(value);
        }
        List<Map<String, Object>> shoppingValues = new ArrayList<>();
        for (ShoppingItem item : items) {
            Map<String, Object> value = new HashMap<>();
            value.put("name", item.name == null ? "" : item.name);
            value.put("quantity", item.quantity == null ? "" : item.quantity);
            value.put("isChecked", item.isChecked);
            shoppingValues.add(value);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("favorites", favoriteValues);
        result.put("mealPlans", planValues);
        result.put("shoppingItems", shoppingValues);
        result.put("updatedAt", System.currentTimeMillis());
        return result;
    }

    private static List<Map<String, Object>> readMapList(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(raw instanceof List<?>)) return result;
        for (Object item : (List<?>) raw) {
            if (item instanceof Map<?, ?>) {
                Map<String, Object> value = new HashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                    if (entry.getKey() instanceof String) value.put((String) entry.getKey(), entry.getValue());
                }
                result.add(value);
            }
        }
        return result;
    }

    private static Recipe recipeFrom(Map<String, Object> values) {
        Recipe recipe = new Recipe();
        recipe.id = stringValue(values.get("id"));
        recipe.name = stringValue(values.get("name"));
        recipe.imageUrl = stringValue(values.get("imageUrl"));
        recipe.instructions = stringValue(values.get("instructions"));
        recipe.ingredientsRaw = stringValue(values.get("ingredientsRaw"));
        recipe.isFavorite = true;
        return recipe;
    }

    private static MealPlanEntry mealPlanFrom(Map<String, Object> values) {
        MealPlanEntry entry = new MealPlanEntry();
        entry.date = stringValue(values.get("date"));
        entry.mealType = stringValue(values.get("mealType"));
        entry.recipeId = stringValue(values.get("recipeId"));
        entry.recipeName = stringValue(values.get("recipeName"));
        entry.scheduledAtMillis = longValue(values.get("scheduledAtMillis"));
        entry.durationMinutes = intValue(values.get("durationMinutes"), 30);
        entry.status = stringValue(values.get("status"));
        if (entry.status.isEmpty()) entry.status = "PLANNED";
        entry.customMealName = stringValue(values.get("customMealName"));
        entry.imageUrl = stringValue(values.get("imageUrl"));
        entry.instructions = stringValue(values.get("instructions"));
        entry.ingredientsRaw = stringValue(values.get("ingredientsRaw"));
        return entry;
    }

    private static ShoppingItem shoppingItemFrom(Map<String, Object> values) {
        ShoppingItem item = new ShoppingItem();
        item.name = stringValue(values.get("name"));
        item.quantity = stringValue(values.get("quantity"));
        item.isChecked = Boolean.TRUE.equals(values.get("isChecked"));
        return item;
    }

    private static MealPlanEntry copyPlan(MealPlanEntry source) {
        MealPlanEntry copy = new MealPlanEntry();
        copy.date = source.date;
        copy.mealType = source.mealType;
        copy.recipeId = source.recipeId;
        copy.recipeName = source.recipeName;
        copy.scheduledAtMillis = source.scheduledAtMillis;
        copy.durationMinutes = source.durationMinutes;
        copy.status = source.status;
        copy.customMealName = source.customMealName;
        copy.imageUrl = source.imageUrl;
        copy.instructions = source.instructions;
        copy.ingredientsRaw = source.ingredientsRaw;
        return copy;
    }

    private static ShoppingItem copyShoppingItem(ShoppingItem source) {
        ShoppingItem copy = new ShoppingItem();
        copy.name = source.name;
        copy.quantity = source.quantity;
        copy.isChecked = source.isChecked;
        return copy;
    }

    private static boolean validPlan(MealPlanEntry entry) {
        return entry.date != null && !entry.date.isEmpty()
                && entry.mealType != null && !entry.mealType.isEmpty()
                && entry.recipeName != null && !entry.recipeName.isEmpty();
    }

    private static String planKey(MealPlanEntry entry) {
        return safe(entry.date) + "|" + safe(entry.mealType) + "|" + safe(entry.customMealName)
                + "|" + entry.scheduledAtMillis + "|" + safe(entry.recipeId) + "|" + safe(entry.recipeName);
    }

    private static String shoppingKey(ShoppingItem item) {
        return safe(item.name).trim().toLowerCase(Locale.ROOT);
    }

    private static String stringValue(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static void complete(@Nullable SyncCallback callback, @Nullable Exception error) {
        if (callback != null) MAIN_THREAD.post(() -> callback.onComplete(error));
    }
}
