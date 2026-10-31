package com.sadat.rannaghor.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

// A single recipe. Stored in Room so favourites work offline,
// and also used as a plain data holder for API search results
// that haven't been saved yet.
@Entity(tableName = "recipe")
public class Recipe {

    @PrimaryKey
    @NonNull
    public String id = "";      // TheMealDB idMeal, or "local_xxx" for our own recipes

    public String name = "";
    public String imageUrl = "";
    public String instructions = "";

    // Ingredients stored as "name1:amount1|name2:amount2" so Room doesn't
    // need a separate table just to show a recipe.
    public String ingredientsRaw = "";

    public boolean isFavorite = false;
    public int matchedIngredientCount = 0;   // only used while showing search results
    public int totalIngredientCount = 0;     // only used while showing search results

    public Recipe() {
    }

    public Recipe(@NonNull String id, String name, String imageUrl, String instructions, String ingredientsRaw) {
        this.id = id;
        this.name = name;
        this.imageUrl = imageUrl;
        this.instructions = instructions;
        this.ingredientsRaw = ingredientsRaw;
    }
}
