package com.sadat.rannaghor.model;

import java.util.ArrayList;
import java.util.List;

public class Ingredient {
    public String name;
    public String measure;

    public Ingredient(String name, String measure) {
        this.name = name;
        this.measure = measure;
    }

    public static List<Ingredient> parseList(String raw) {
        List<Ingredient> list = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return list;
        for (String part : raw.split("\\|")) {
            String[] bits = part.split(":", 2);
            String name = bits.length > 0 ? bits[0].trim() : "";
            String measure = bits.length > 1 ? bits[1].trim() : "";
            if (!name.isEmpty()) {
                list.add(new Ingredient(name, measure));
            }
        }
        return list;
    }

    public static String toStorageString(List<Ingredient> ingredients) {
        StringBuilder sb = new StringBuilder();
        for (Ingredient i : ingredients) {
            if (sb.length() > 0) sb.append("|");
            sb.append(i.name.trim()).append(":").append(i.measure.trim());
        }
        return sb.toString();
    }
}
