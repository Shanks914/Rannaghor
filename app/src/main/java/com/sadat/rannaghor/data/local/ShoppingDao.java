package com.sadat.rannaghor.data.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.sadat.rannaghor.model.ShoppingItem;

import java.util.List;

@Dao
public interface ShoppingDao {

    @Insert
    void insert(ShoppingItem item);

    @Update
    void update(ShoppingItem item);

    @Delete
    void delete(ShoppingItem item);

    @Query("DELETE FROM shopping_item")
    void clearAll();

    @Query("SELECT * FROM shopping_item ORDER BY isChecked ASC, name ASC")
    List<ShoppingItem> getAll();

    @Query("SELECT * FROM shopping_item WHERE name = :name LIMIT 1")
    ShoppingItem findByName(String name);
}
