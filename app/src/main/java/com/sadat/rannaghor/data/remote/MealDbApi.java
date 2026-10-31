package com.sadat.rannaghor.data.remote;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sadat.rannaghor.model.Ingredient;
import com.sadat.rannaghor.model.Recipe;

/**
 * Talks to the free TheMealDB API (https://www.themealdb.com/api.php).
 * The free test key is "1" - no sign-up, no secret key needed.
 *
 * TheMealDB only lets you filter by ONE ingredient per request, so
 * RecipeRepository calls fetchByIngredient() once per ingredient the
 * user typed, then combines the results itself.
 *
 * All network calls run on a background thread (ExecutorService) and the
 * callback is posted back to the main thread with a Handler, because
 * Android does not allow network calls on the UI thread.
 */
public class MealDbApi {

    private static final String BASE_URL = "https://www.themealdb.com/api/json/v1/1";

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    /** Search for meals that use a single ingredient. */
    public void fetchByIngredient(String ingredient, ApiCallback callback) {
        String url = BASE_URL + "/filter.php?i=" + urlEncode(ingredient);
        executor.execute(() -> {
            try {
                String json = httpGet(url);
                List<Recipe> results = parseFilterResponse(json);
                postSuccess(callback, results);
            } catch (Exception e) {
                postError(callback, "Could not reach the recipe service: " + e.getMessage());
            }
        });
    }

    /** Fetch the full detail (ingredients + instructions) for one recipe id. */
    public void fetchRecipeDetail(String mealId, ApiCallback callback) {
        String url = BASE_URL + "/lookup.php?i=" + urlEncode(mealId);
        executor.execute(() -> {
            try {
                String json = httpGet(url);
                List<Recipe> results = parseLookupResponse(json);
                postSuccess(callback, results);
            } catch (Exception e) {
                postError(callback, "Could not load the recipe: " + e.getMessage());
            }
        });
    }

    // ---- parsing helpers -------------------------------------------------

    private List<Recipe> parseFilterResponse(String json) throws Exception {
        List<Recipe> list = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray meals = root.optJSONArray("meals");
        if (meals == null) return list; // no matches for this ingredient

        for (int i = 0; i < meals.length(); i++) {
            JSONObject m = meals.getJSONObject(i);
            Recipe r = new Recipe();
            r.id = m.optString("idMeal");
            r.name = m.optString("strMeal");
            r.imageUrl = m.optString("strMealThumb");
            list.add(r);
        }
        return list;
    }

    private List<Recipe> parseLookupResponse(String json) throws Exception {
        List<Recipe> list = new ArrayList<>();
        JSONObject root = new JSONObject(json);
        JSONArray meals = root.optJSONArray("meals");
        if (meals == null || meals.length() == 0) return list;

        JSONObject m = meals.getJSONObject(0);
        Recipe r = new Recipe();
        r.id = m.optString("idMeal");
        r.name = m.optString("strMeal");
        r.imageUrl = m.optString("strMealThumb");
        r.instructions = m.optString("strInstructions");

        // TheMealDB returns ingredients as strIngredient1..20 / strMeasure1..20,
        // most of them empty - collect only the non-empty pairs.
        List<Ingredient> ingredients = new ArrayList<>();
        for (int n = 1; n <= 20; n++) {
            String name = m.optString("strIngredient" + n, "").trim();
            String measure = m.optString("strMeasure" + n, "").trim();
            if (!name.isEmpty()) {
                ingredients.add(new Ingredient(name, measure));
            }
        }
        r.ingredientsRaw = Ingredient.toStorageString(ingredients);

        list.add(r);
        return list;
    }

    // ---- low level ---------------------------------------------------

    private String httpGet(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (InputStream in = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s.trim(), StandardCharsets.UTF_8);
    }

    private void postSuccess(ApiCallback callback, List<Recipe> results) {
        mainThread.post(() -> callback.onSuccess(results));
    }

    private void postError(ApiCallback callback, String message) {
        mainThread.post(() -> callback.onError(message));
    }
}
