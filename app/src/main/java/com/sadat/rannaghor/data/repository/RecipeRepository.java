package com.sadat.rannaghor.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.data.remote.ApiCallback;
import com.sadat.rannaghor.data.remote.MealDbApi;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.model.Ingredient;
import com.sadat.rannaghor.util.LocalRecipeLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The single place the UI talks to for recipe data.
 * Decides whether to use the API, Room, or both - the Activities
 * never call MealDbApi or RecipeDao directly.
 */
public class RecipeRepository {

    public interface SearchCallback {
        void onResult(List<Recipe> rankedRecipes);
        void onError(String message);
    }

    public interface SingleRecipeCallback {
        void onResult(Recipe recipe);
        void onError(String message);
    }

    private final MealDbApi api = new MealDbApi();
    private final AppDatabase db;
    private final Context appContext;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    public RecipeRepository(Context context) {
        appContext = context.getApplicationContext();
        db = AppDatabase.getInstance(context);
    }

    /**
     * Searches TheMealDB once per ingredient (its free tier only supports
     * one ingredient per request), then combines the results itself:
     * a recipe that shows up for more of the user's ingredients ranks higher.
     */
    public void searchByIngredients(List<String> ingredients, SearchCallback callback) {
        if (ingredients == null || ingredients.isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }

        Map<String, Recipe> combined = new HashMap<>();
        Map<String, Integer> matchCount = new HashMap<>();
        List<String> requestedIngredients = new ArrayList<>();
        for (String ingredient : ingredients) {
            String cleaned = ingredient == null ? "" : ingredient.trim();
            if (!cleaned.isEmpty() && !requestedIngredients.contains(cleaned.toLowerCase(java.util.Locale.ROOT))) {
                requestedIngredients.add(cleaned.toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (requestedIngredients.isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }

        AtomicInteger pending = new AtomicInteger(requestedIngredients.size());
        AtomicInteger errors = new AtomicInteger(0);

        for (String ingredient : requestedIngredients) {
            api.fetchByIngredient(ingredient, new ApiCallback() {
                @Override
                public void onSuccess(List<Recipe> recipes) {
                    synchronized (combined) {
                        for (Recipe r : recipes) {
                            combined.put(r.id, r);
                            matchCount.merge(r.id, 1, Integer::sum);
                        }
                    }
                    finishOne();
                }

                @Override
                public void onError(String message) {
                    errors.incrementAndGet();
                    finishOne();
                }

                private void finishOne() {
                    if (pending.decrementAndGet() == 0) {
                        List<Recipe> localRecipes = LocalRecipeLoader.loadAll(appContext);
                        for (Recipe local : localRecipes) {
                            int localMatches = 0;
                            List<Ingredient> recipeIngredients = Ingredient.parseList(local.ingredientsRaw);
                            for (String requested : requestedIngredients) {
                                for (Ingredient recipeIngredient : recipeIngredients) {
                                    String name = recipeIngredient.name.toLowerCase(java.util.Locale.ROOT);
                                    if (name.equals(requested) || name.contains(requested) || requested.contains(name)) {
                                        localMatches++;
                                        break;
                                    }
                                }
                            }
                            if (localMatches > 0) {
                                local.matchedIngredientCount = localMatches;
                                local.totalIngredientCount = requestedIngredients.size();
                                combined.put(local.id, local);
                            }
                        }
                        if (combined.isEmpty() && errors.get() == requestedIngredients.size()) {
                            callback.onError("Could not reach the recipe service. Check your internet connection.");
                            return;
                        }
                        List<Recipe> ranked = new ArrayList<>(combined.values());
                        for (Recipe r : ranked) {
                            r.matchedIngredientCount = matchCount.getOrDefault(r.id, 0);
                            r.totalIngredientCount = requestedIngredients.size();
                        }
                        ranked.sort((a, b) -> b.matchedIngredientCount - a.matchedIngredientCount);
                        callback.onResult(ranked);
                    }
                }
            });
        }
    }

    /** Loads full recipe detail: from Room if it's already saved, otherwise from the API. */
    public void getRecipeDetail(String recipeId, SingleRecipeCallback callback) {
        dbExecutor.execute(() -> {
            Recipe local = db.recipeDao().getById(recipeId);
            if (local != null && local.instructions != null && !local.instructions.isEmpty()) {
                mainThread.post(() -> callback.onResult(local));
                return;
            }
            api.fetchRecipeDetail(recipeId, new ApiCallback() {
                @Override
                public void onSuccess(List<Recipe> recipes) {
                    if (recipes.isEmpty()) {
                        callback.onError("Recipe not found.");
                    } else {
                        callback.onResult(recipes.get(0));
                    }
                }

                @Override
                public void onError(String message) {
                    callback.onError(message);
                }
            });
        });
    }

    public void toggleFavorite(Recipe recipe, Runnable onDone) {
        dbExecutor.execute(() -> {
            recipe.isFavorite = !recipe.isFavorite;
            db.recipeDao().insert(recipe); // insert = REPLACE, acts as upsert
            CloudBackupManager.backup(appContext);
            if (onDone != null) mainThread.post(onDone);
        });
    }

    public void getFavorites(java.util.function.Consumer<List<Recipe>> callback) {
        dbExecutor.execute(() -> {
            List<Recipe> favorites = db.recipeDao().getFavorites();
            mainThread.post(() -> callback.accept(favorites));
        });
    }
}
