package com.example.bcalm;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class BabyManager {

    private static final String PREFS = "bcalm_baby_prefs";

    private BabyManager() {
    }

    public static String getActiveBabyId(Context ctx) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            return null;
        }
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("active_" + u.getUid(), null);
    }

    public static void setActiveBabyId(Context ctx, String babyId) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) {
            return;
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("active_" + u.getUid(), babyId) // null clears it
                .apply();
    }
}