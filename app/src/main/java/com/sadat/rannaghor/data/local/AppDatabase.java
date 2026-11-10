package com.sadat.rannaghor.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.sadat.rannaghor.model.MealPlanEntry;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.model.ShoppingItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

// Personal Room databases keep each Firebase account's favourites, plan and
// shopping list isolated while retaining offline access for that account.
@Database(
        entities = {Recipe.class, MealPlanEntry.class, ShoppingItem.class},
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE meal_plan ADD COLUMN scheduledAtMillis INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE meal_plan ADD COLUMN durationMinutes INTEGER NOT NULL DEFAULT 30");
            db.execSQL("ALTER TABLE meal_plan ADD COLUMN status TEXT NOT NULL DEFAULT 'PLANNED'");
            db.execSQL("ALTER TABLE meal_plan ADD COLUMN customMealName TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE meal_plan ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE meal_plan ADD COLUMN instructions TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE meal_plan ADD COLUMN ingredientsRaw TEXT NOT NULL DEFAULT ''");
        }
    };

    public abstract RecipeDao recipeDao();
    public abstract MealPlanDao mealPlanDao();
    public abstract ShoppingDao shoppingDao();

    private static final Map<String, AppDatabase> INSTANCES = new HashMap<>();

    public static AppDatabase getInstance(Context context) {
        String uid = null;
        Context appContext = context.getApplicationContext();
        if (!FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) uid = auth.getCurrentUser().getUid();
        }
        return getInstanceForUser(appContext, uid);
    }

    /** Returns the database for an explicit account, used by async cloud operations. */
    public static AppDatabase getInstanceForUser(Context context, String uid) {
        Context appContext = context.getApplicationContext();
        String databaseName = uid == null || uid.isEmpty()
                ? "rannaghor_guest.db"
                : "rannaghor_user_" + stableHash(uid) + ".db";
        synchronized (AppDatabase.class) {
            AppDatabase instance = INSTANCES.get(databaseName);
            if (instance == null) {
                instance = Room.databaseBuilder(appContext, AppDatabase.class, databaseName)
                        .addMigrations(MIGRATION_1_2)
                        .build();
                INSTANCES.put(databaseName, instance);
            }
            return instance;
        }
    }

    private static String stableHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 12; i++) result.append(String.format(java.util.Locale.ROOT, "%02x", digest[i] & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
