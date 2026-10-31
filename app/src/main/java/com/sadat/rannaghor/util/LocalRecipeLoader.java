package com.sadat.rannaghor.util;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.sadat.rannaghor.model.Recipe;

/**
 * Loads the bundled Bangladeshi recipes from assets/local_recipes.json.
 * This is how the app covers JSON parsing for data that ISN'T from the
 * network, and it fills the gap left by TheMealDB having very few
 * Bangladeshi dishes.
 *
 * The recipes are merged into ingredient search and are also available
 * directly in the meal planner's recipe picker.
 */
public class LocalRecipeLoader {

    public static List<Recipe> loadAll(Context context) {
        List<Recipe> recipes = new ArrayList<>();
        try (InputStream is = context.getAssets().open("local_recipes.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONArray array = new JSONArray(sb.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Recipe r = new Recipe();
                r.id = obj.optString("id");
                r.name = obj.optString("name");
                r.imageUrl = obj.optString("imageUrl");
                r.instructions = obj.optString("instructions");
                r.ingredientsRaw = obj.optString("ingredients");
                recipes.add(r);
            }
        } catch (Exception e) {
            // If the file is missing or malformed, just return what we have
            // (possibly empty) instead of crashing the search screen.
        }
        return recipes;
    }
}
