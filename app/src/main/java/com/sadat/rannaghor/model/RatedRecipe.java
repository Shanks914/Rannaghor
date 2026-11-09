package com.sadat.rannaghor.model;

/** Recipe card plus its community rating, kept separate from the Room entity. */
public class RatedRecipe {
    public String id = "";
    public String name = "";
    public String imageUrl = "";
    public double averageRating;
    public int reviewCount;
}
