package com.sadat.rannaghor.util;

import android.content.Intent;

/** Intent fields shared by the planner, recipe browser and recipe details. */
public final class MealPlanIntents {
    public static final String FLOW = "extra_planning_flow";
    public static final String DATE = "extra_plan_date";
    public static final String TYPE = "extra_plan_type";
    public static final String CUSTOM_NAME = "extra_plan_custom_name";
    public static final String TIME = "extra_plan_time";
    public static final String PLAN_ID = "extra_plan_id";
    private MealPlanIntents() { }

    public static void copyDraft(Intent from, Intent to) {
        to.putExtra(FLOW, from.getBooleanExtra(FLOW, false));
        to.putExtra(DATE, from.getStringExtra(DATE));
        to.putExtra(TYPE, from.getStringExtra(TYPE));
        to.putExtra(CUSTOM_NAME, from.getStringExtra(CUSTOM_NAME));
        to.putExtra(TIME, from.getLongExtra(TIME, 0L));
    }
}
