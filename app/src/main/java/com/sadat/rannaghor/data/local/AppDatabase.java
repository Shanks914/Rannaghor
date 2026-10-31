package com.sadat.rannaghor.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.sadat.rannaghor.model.MealPlanEntry;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.model.ShoppingItem;

// Single Room database for the whole app: favourites, the meal plan
// and the shopping list all live here, on the phone, so they work offline.
@Database(
        entities = {Recipe.class, MealPlanEntry.class, ShoppingItem.class},
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract RecipeDao recipeDao();
    public abstract MealPlanDao mealPlanDao();
    public abstract ShoppingDao shoppingDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "rannaghor.db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
