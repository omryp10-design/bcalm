package com.example.bcalm;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BabiesFragment extends Fragment {

    private ListView lvBabies;
    private Button btnAddBaby;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private DatabaseReference babiesRef;
    private ValueEventListener babiesListener;

    private final ArrayList<String> babyIds = new ArrayList<>();
    private final ArrayList<String> names = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_babies, container, false);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        lvBabies = view.findViewById(R.id.lvBabies);
        btnAddBaby = view.findViewById(R.id.btnAddBaby);

        adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, names);
        lvBabies.setAdapter(adapter);

        lvBabies.setOnItemClickListener((parent, v, position, id) -> {
            if (position < 0 || position >= babyIds.size()) {
                return;
            }
            String babyId = babyIds.get(position);
            if (babyId == null) {
                return;
            }
            BabyManager.setActiveBabyId(requireContext(), babyId);
            Toast.makeText(getContext(), "Active baby set", Toast.LENGTH_SHORT).show();
            Navigation.findNavController(v).popBackStack();
        });

        lvBabies.setOnItemLongClickListener((parent, v, position, id) -> {
            if (position < 0 || position >= babyIds.size()) {
                return true;
            }
            String babyId = babyIds.get(position);
            if (babyId == null) {
                return true;
            }
            confirmDeleteBaby(babyId);
            return true;
        });

        btnAddBaby.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("isNew", true);
            Navigation.findNavController(v)
                    .navigate(R.id.action_babiesFragment_to_babyProfileFragment, args);
        });

        loadBabies();

        ThemeUtil.applyBabyBackground(view);

        return view;
    }

    private void loadBabies() {
        if (mAuth.getCurrentUser() == null) {
            return;
        }

        final String userId = mAuth.getCurrentUser().getUid();
        babiesRef = mDatabase.child("babies").child(userId);

        babiesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Migrate OLD flat format if present
                if (snapshot.exists()
                        && snapshot.hasChild("fullName")
                        && snapshot.child("fullName").getValue() instanceof String) {
                    migrateFlatBaby(userId, snapshot);
                    return;
                }

                babyIds.clear();
                names.clear();

                ArrayList<String> rawNames = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (!child.hasChild("fullName")) {
                        continue;
                    }
                    String babyName = child.child("fullName").getValue(String.class);
                    if (babyName == null || babyName.isEmpty()) {
                        babyName = "(no name)";
                    }
                    babyIds.add(child.getKey());
                    rawNames.add(babyName);
                }

                // Determine the active baby; auto-select if missing/deleted
                String active = BabyManager.getActiveBabyId(requireContext());
                if ((active == null || !babyIds.contains(active)) && !babyIds.isEmpty()) {
                    active = babyIds.get(0);
                    BabyManager.setActiveBabyId(requireContext(), active);
                }

                if (babyIds.isEmpty()) {
                    babyIds.add(null);
                    names.add("No babies yet — tap + Add New Baby");
                } else {
                    for (int i = 0; i < babyIds.size(); i++) {
                        String mark = babyIds.get(i).equals(active) ? "   ✓ active" : "";
                        names.add(rawNames.get(i) + mark);
                    }
                }

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        };

        babiesRef.addValueEventListener(babiesListener);
    }

    private void confirmDeleteBaby(final String babyId) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete baby")
                .setMessage("Delete this baby's profile? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteBaby(babyId))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteBaby(final String babyId) {
        if (mAuth.getCurrentUser() == null) {
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        mDatabase.child("babies").child(userId).child(babyId)
                .removeValue()
                .addOnSuccessListener(a -> {
                    // If we deleted the active baby, clear it; the listener will reselect one
                    String active = BabyManager.getActiveBabyId(requireContext());
                    if (babyId.equals(active)) {
                        BabyManager.setActiveBabyId(requireContext(), null);
                    }
                    Toast.makeText(getContext(), "Baby deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error deleting: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void migrateFlatBaby(final String userId, DataSnapshot snapshot) {
        final String newId = mDatabase.child("babies").child(userId).push().getKey();
        if (newId == null) {
            return;
        }

        Double weight = null;
        Object wObj = snapshot.child("weight").getValue();
        if (wObj instanceof Number) {
            weight = ((Number) wObj).doubleValue();
        }

        Map<String, Object> baby = new HashMap<>();
        baby.put("fullName", snapshot.child("fullName").getValue(String.class));
        baby.put("dateOfBirth", snapshot.child("dateOfBirth").getValue(String.class));
        baby.put("weight", weight);
        baby.put("country", snapshot.child("country").getValue(String.class));
        baby.put("sex", snapshot.child("sex").getValue(String.class));
        baby.put("lastUpdated", System.currentTimeMillis());

        Map<String, Object> updates = new HashMap<>();
        updates.put(newId, baby);
        updates.put("fullName", null);
        updates.put("dateOfBirth", null);
        updates.put("weight", null);
        updates.put("country", null);
        updates.put("sex", null);
        updates.put("lastUpdated", null);

        mDatabase.child("babies").child(userId).updateChildren(updates)
                .addOnSuccessListener(a -> BabyManager.setActiveBabyId(requireContext(), newId));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (babiesRef != null && babiesListener != null) {
            babiesRef.removeEventListener(babiesListener);
        }
    }
}