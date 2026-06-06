package com.example.bcalm;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ThemeUtil {

    private ThemeUtil() {
    }

    public static void applyBabyBackground(final View root) {
        if (root == null) {
            return;
        }

        Context ctx = root.getContext();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        String babyId = BabyManager.getActiveBabyId(ctx);
        if (babyId == null) {
            return;
        }

        FirebaseDatabase.getInstance().getReference()
                .child("babies")
                .child(user.getUid())
                .child(babyId)
                .child("sex")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String sex = snapshot.getValue(String.class);
                        if ("Boy".equals(sex)) {
                            root.setBackgroundResource(R.drawable.bg_gradient_boy);
                        } else {
                            root.setBackgroundResource(R.drawable.bg_gradient_girl);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }
}