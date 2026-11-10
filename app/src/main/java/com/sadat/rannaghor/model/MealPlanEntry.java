package com.sadat.rannaghor.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "meal_plan")
public class MealPlanEntry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String date;       // "yyyy-MM-dd"
    public String mealType;   // "Breakfast" | "Lunch" | "Dinner"
    public String recipeId;   // matches Recipe.id
    public String recipeName; // cached so the planner list doesn't need a join

    @ColumnInfo(defaultValue = "0")
    public long scheduledAtMillis;
    @ColumnInfo(defaultValue = "30")
    public int durationMinutes;
    @ColumnInfo(defaultValue = "'PLANNED'")
    public String status = "PLANNED";
    @ColumnInfo(defaultValue = "''")
    public String customMealName = "";
    @ColumnInfo(defaultValue = "''")
    public String imageUrl = "";
    @ColumnInfo(defaultValue = "''")
    public String instructions = "";
    @ColumnInfo(defaultValue = "''")
    public String ingredientsRaw = "";
}
