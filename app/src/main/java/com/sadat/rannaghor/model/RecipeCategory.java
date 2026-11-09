package com.sadat.rannaghor.model;

/** Stable keys for the home-screen recipe collections. */
public final class RecipeCategory {
    public static final String QUICK_EASY = "quick_easy";
    public static final String DINNER = "dinner";
    public static final String VEGETARIAN = "vegetarian";
    public static final String HEALTHY = "healthy";
    public static final String INSTANT_POT = "instant_pot";
    public static final String VEGAN = "vegan";
    public static final String SOUPS = "soups";
    public static final String SALADS = "salads";
    public static final String BANGLADESHI = "bangladeshi";

    private RecipeCategory() { }

    public static String title(String key) {
        if (QUICK_EASY.equals(key)) return "Quick & Easy";
        if (DINNER.equals(key)) return "Dinner";
        if (VEGETARIAN.equals(key)) return "Vegetarian";
        if (HEALTHY.equals(key)) return "Healthy";
        if (INSTANT_POT.equals(key)) return "Instant Pot";
        if (VEGAN.equals(key)) return "Vegan";
        if (SOUPS.equals(key)) return "Soups";
        if (SALADS.equals(key)) return "Salads";
        if (BANGLADESHI.equals(key)) return "Bangladeshi";
        return "Recipes";
    }

    public static String subtitle(String key) {
        if (BANGLADESHI.equals(key)) return "Bangladeshi favorites, from the API and Rannaghor kitchen.";
        if (VEGETARIAN.equals(key)) return "Vegetable-forward meals for every day.";
        if (VEGAN.equals(key)) return "Plant-based recipes full of flavor.";
        if (HEALTHY.equals(key)) return "Fresh, balanced ideas for your table.";
        if (INSTANT_POT.equals(key)) return "Comforting recipes for pressure cooking.";
        if (SOUPS.equals(key)) return "Cozy bowls, broths, and one-pot favorites.";
        if (SALADS.equals(key)) return "Fresh and colorful salads to make any day brighter.";
        if (DINNER.equals(key)) return "Everyday main dishes for tonight's table.";
        return "Simple, satisfying recipes for busy days.";
    }
}
