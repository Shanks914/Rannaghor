package com.sadat.rannaghor.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.data.remote.ApiCallback;
import com.sadat.rannaghor.data.remote.MealDbApi;
import com.sadat.rannaghor.model.Recipe;
import com.sadat.rannaghor.model.Ingredient;
import com.sadat.rannaghor.model.RecipeCategory;
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
    private final Context appContext;
    private final AppDatabase db;
    private final String accountUid;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    public RecipeRepository(Context context) {
        appContext = context.getApplicationContext();
        accountUid = CloudBackupManager.isConfigured(appContext)
                && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        db = AppDatabase.getInstanceForUser(appContext, accountUid);
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

    /** Search the API by dish name and include matching bundled recipes. */
    public void searchByName(String query, SearchCallback callback) {
        String cleaned = query == null ? "" : query.trim();
        if (cleaned.isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }
        api.searchByName(cleaned, new ApiCallback() {
            @Override public void onSuccess(List<Recipe> recipes) {
                dbExecutor.execute(() -> {
                    Map<String, Recipe> combined = new java.util.LinkedHashMap<>();
                    for (Recipe recipe : recipes) combined.put(recipe.id, recipe);
                    String needle = cleaned.toLowerCase(java.util.Locale.ROOT);
                    for (Recipe local : LocalRecipeLoader.loadAll(appContext)) {
                        if (local.name.toLowerCase(java.util.Locale.ROOT).contains(needle)) combined.put(local.id, local);
                    }
                    List<Recipe> result = new ArrayList<>(combined.values());
                    mainThread.post(() -> callback.onResult(result));
                });
            }
            @Override public void onError(String message) {
                dbExecutor.execute(() -> {
                    List<Recipe> localMatches = new ArrayList<>();
                    String needle = cleaned.toLowerCase(java.util.Locale.ROOT);
                    for (Recipe local : LocalRecipeLoader.loadAll(appContext)) {
                        if (local.name.toLowerCase(java.util.Locale.ROOT).contains(needle)) localMatches.add(local);
                    }
                    if (localMatches.isEmpty()) mainThread.post(() -> callback.onError(message));
                    else mainThread.post(() -> callback.onResult(localMatches));
                });
            }
        });
    }

    /** Loads recipes for a home-screen collection, combining relevant API searches with local dishes. */
    public void searchByCategory(String categoryId, SearchCallback callback) {
        List<java.util.function.Consumer<ApiCallback>> requests = new ArrayList<>();
        if (RecipeCategory.BANGLADESHI.equals(categoryId)) {
            requests.add(cb -> api.fetchByArea("Bangladeshi", cb));
        } else if (RecipeCategory.VEGETARIAN.equals(categoryId)) {
            requests.add(cb -> api.fetchByCategory("Vegetarian", cb));
        } else if (RecipeCategory.VEGAN.equals(categoryId)) {
            requests.add(cb -> api.fetchByCategory("Vegan", cb));
        } else if (RecipeCategory.HEALTHY.equals(categoryId)) {
            requests.add(cb -> api.fetchByCategory("Vegan", cb));
            requests.add(cb -> api.fetchByCategory("Vegetarian", cb));
            requests.add(cb -> api.fetchByCategory("Seafood", cb));
        } else if (RecipeCategory.DINNER.equals(categoryId)) {
            for (String value : new String[]{"Chicken", "Beef", "Seafood", "Lamb", "Pasta", "Vegetarian", "Vegan"}) {
                requests.add(cb -> api.fetchByCategory(value, cb));
            }
        } else if (RecipeCategory.QUICK_EASY.equals(categoryId)) {
            for (String value : new String[]{"quick", "easy"}) requests.add(cb -> api.searchByName(value, cb));
            requests.add(cb -> api.fetchByCategory("Pasta", cb));
            requests.add(cb -> api.fetchByCategory("Breakfast", cb));
        } else if (RecipeCategory.INSTANT_POT.equals(categoryId)) {
            requests.add(cb -> api.searchByName("instant pot", cb));
            requests.add(cb -> api.searchByName("pressure cooker", cb));
        } else if (RecipeCategory.SOUPS.equals(categoryId)) {
            requests.add(cb -> api.searchByName("soup", cb));
        } else if (RecipeCategory.SALADS.equals(categoryId)) {
            requests.add(cb -> api.searchByName("salad", cb));
        }

        if (requests.isEmpty()) {
            callback.onResult(new ArrayList<>());
            return;
        }

        Map<String, Recipe> results = new java.util.LinkedHashMap<>();
        AtomicInteger pending = new AtomicInteger(requests.size());
        AtomicInteger succeeded = new AtomicInteger();
        ApiCallback categoryRequest = new ApiCallback() {
            @Override public void onSuccess(List<Recipe> recipes) {
                succeeded.incrementAndGet();
                synchronized (results) {
                    for (Recipe recipe : recipes) results.put(recipe.id, recipe);
                }
                finishIfReady();
            }
            @Override public void onError(String message) { finishIfReady(); }
            private void finishIfReady() {
                if (pending.decrementAndGet() != 0) return;
                dbExecutor.execute(() -> {
                    for (Recipe local : LocalRecipeLoader.loadAll(appContext)) {
                        if (matchesLocalCategory(local, categoryId)) results.put(local.id, local);
                    }
                    if (succeeded.get() == 0 && results.isEmpty()) {
                        mainThread.post(() -> callback.onError("Could not reach the recipe service. Check your internet connection."));
                    } else {
                        List<Recipe> list = new ArrayList<>(results.values());
                        list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                        mainThread.post(() -> callback.onResult(list));
                    }
                });
            }
        };
        for (java.util.function.Consumer<ApiCallback> request : requests) request.accept(categoryRequest);
    }

    private boolean matchesLocalCategory(Recipe recipe, String categoryId) {
        if (RecipeCategory.BANGLADESHI.equals(categoryId) || RecipeCategory.DINNER.equals(categoryId)) return true;
        boolean vegetarian = true;
        for (Ingredient ingredient : Ingredient.parseList(recipe.ingredientsRaw)) {
            String name = ingredient.name.toLowerCase(java.util.Locale.ROOT).trim();
            if (name.matches(".*\\b(chicken|fish|beef|pork|mutton|meat|prawn|shrimp|egg|eggs)\\b.*")) {
                vegetarian = false;
                break;
            }
        }
        if (RecipeCategory.VEGETARIAN.equals(categoryId) || RecipeCategory.VEGAN.equals(categoryId) || RecipeCategory.HEALTHY.equals(categoryId)) return vegetarian;
        if (RecipeCategory.QUICK_EASY.equals(categoryId)) return true;
        return false;
    }

    /** Loads full recipe detail: from Room if it's already saved, otherwise from the API. */
    public void getRecipeDetail(String recipeId, SingleRecipeCallback callback) {
        dbExecutor.execute(() -> {
            Recipe local = db.recipeDao().getById(recipeId);
            if (local != null && local.instructions != null && !local.instructions.isEmpty()) {
                mainThread.post(() -> callback.onResult(local));
                return;
            }
            if (recipeId.startsWith("local_")) {
                for (Recipe bundled : LocalRecipeLoader.loadAll(appContext)) {
                    if (recipeId.equals(bundled.id)) {
                        mainThread.post(() -> callback.onResult(bundled));
                        return;
                    }
                }
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
            CloudBackupManager.backupForUser(appContext, accountUid);
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
