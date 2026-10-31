package com.sadat.rannaghor.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "meal_plan")
public class MealPlanEntry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String date;       // "yyyy-MM-dd"
    public String mealType;   // "Breakfast" | "Lunch" | "Dinner"
    public String recipeId;   // matches Recipe.id
    public String recipeName; // cached so the planner list doesn't need a join
}
