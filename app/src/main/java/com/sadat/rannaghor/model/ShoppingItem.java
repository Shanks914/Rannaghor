package com.sadat.rannaghor.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "shopping_item")
public class ShoppingItem {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String name;
    public String quantity;
    public boolean isChecked;
}
