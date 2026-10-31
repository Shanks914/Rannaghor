package com.sadat.rannaghor.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.sadat.rannaghor.model.Recipe;

import java.util.List;

@Dao
public interface RecipeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Recipe recipe);

    @Update
    void update(Recipe recipe);

    @Delete
    void delete(Recipe recipe);

    @Query("SELECT * FROM recipe WHERE id = :id LIMIT 1")
    Recipe getById(String id);

    @Query("SELECT * FROM recipe WHERE isFavorite = 1 ORDER BY name ASC")
    List<Recipe> getFavorites();

    @Query("SELECT * FROM recipe")
    List<Recipe> getAll();
}
