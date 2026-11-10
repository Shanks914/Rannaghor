package com.sadat.rannaghor.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.sadat.rannaghor.model.MealPlanEntry;

import java.util.List;

@Dao
public interface MealPlanDao {

    @Insert
    void insert(MealPlanEntry entry);

    @Delete
    void delete(MealPlanEntry entry);

    @Query("SELECT * FROM meal_plan WHERE date = :date ORDER BY CASE mealType WHEN 'Breakfast' THEN 0 WHEN 'Lunch' THEN 1 WHEN 'Dinner' THEN 2 ELSE 3 END, customMealName ASC, scheduledAtMillis ASC")
    List<MealPlanEntry> getForDate(String date);

    @Query("SELECT * FROM meal_plan WHERE id = :id LIMIT 1")
    MealPlanEntry getById(long id);

    @Query("UPDATE meal_plan SET status = :status WHERE id = :id")
    void setStatus(long id, String status);

    @Query("SELECT * FROM meal_plan WHERE date BETWEEN :startDate AND :endDate")
    List<MealPlanEntry> getForWeek(String startDate, String endDate);

    @Query("SELECT * FROM meal_plan")
    List<MealPlanEntry> getAll();

    @Query("DELETE FROM meal_plan")
    void clearAll();
}
