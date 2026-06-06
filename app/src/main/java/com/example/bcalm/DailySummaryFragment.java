package com.example.bcalm;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DailySummaryFragment extends Fragment {

    private String selectedDate;

    private TextView tvDailySummaryTitle;
    private TextView tvTotalFeedings;
    private TextView tvTotalMilk;
    private TextView tvAverageFeeding;
    private TextView tvBabyAge;
    private TextView tvExpectedDailyIntake;
    private TextView tvIntakeComparison;
    private TextView tvDiaperSummary;

    private ListView lvFeedings;
    private Button btnAddFeeding;
    private Button btnAddManualFeeding;
    private Button btnAddDiaper;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private ArrayList<String> feedings;
    private ArrayAdapter<String> adapter;

    private ArrayList<String> feedingKeys;
    private ArrayList<Float> feedingAmounts;
    private ArrayList<String> feedingTimes;

    private DatabaseReference babyRef;
    private ValueEventListener babyListener;
    private DatabaseReference feedingsRef;
    private ValueEventListener feedingsListener;
    private DatabaseReference diaperRef;
    private ValueEventListener diaperListener;

    private float totalMilkForDay = 0;
    private double babyWeightKg = 0;
    private String babyDateOfBirth = "";
    private String babySex = "";

    public DailySummaryFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_daily_summary, container, false);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        tvDailySummaryTitle = view.findViewById(R.id.tvDailySummaryTitle);
        tvTotalFeedings = view.findViewById(R.id.tvTotalFeedings);
        tvTotalMilk = view.findViewById(R.id.tvTotalMilk);
        tvAverageFeeding = view.findViewById(R.id.tvAverageFeeding);
        tvBabyAge = view.findViewById(R.id.tvBabyAge);
        tvExpectedDailyIntake = view.findViewById(R.id.tvExpectedDailyIntake);
        tvIntakeComparison = view.findViewById(R.id.tvIntakeComparison);
        tvDiaperSummary = view.findViewById(R.id.tvDiaperSummary);

        lvFeedings = view.findViewById(R.id.lvFeedings);
        btnAddFeeding = view.findViewById(R.id.btnAddFeeding);
        btnAddManualFeeding = view.findViewById(R.id.btnAddManualFeeding);
        btnAddDiaper = view.findViewById(R.id.btnAddDiaper);

        if (getArguments() != null) {
            selectedDate = getArguments().getString("selected_date");
        }

        if (selectedDate == null) {
            selectedDate = "";
        }

        tvDailySummaryTitle.setText("Daily Summary - " + selectedDate);

        feedings = new ArrayList<>();
        feedingKeys = new ArrayList<>();
        feedingAmounts = new ArrayList<>();
        feedingTimes = new ArrayList<>();

        adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                feedings
        );

        lvFeedings.setAdapter(adapter);

        lvFeedings.setOnItemClickListener((parent, v, position, id) -> {
            if (position < 0 || position >= feedingKeys.size()) {
                return;
            }
            String key = feedingKeys.get(position);
            if (key == null) {
                return;
            }
            showFeedingOptions(key, feedingTimes.get(position), feedingAmounts.get(position));
        });

        loadBabyProfileFromFirebase();
        loadFeedingsFromFirebase();
        loadDiapersFromFirebase();

        btnAddFeeding.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("selected_date", selectedDate);
            Navigation.findNavController(v)
                    .navigate(R.id.action_dailySummaryFragment_to_pumpDetailsFragment, bundle);
        });

        btnAddManualFeeding.setOnClickListener(v -> showManualFeedingDialog());
        btnAddDiaper.setOnClickListener(v -> showAddDiaperDialog());

        ThemeUtil.applyBabyBackground(view);

        return view;
    }

    private void loadBabyProfileFromFirebase() {
        if (mAuth.getCurrentUser() == null) {
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        String activeBabyId = BabyManager.getActiveBabyId(requireContext());
        if (activeBabyId == null) {
            updateBabyComparison();
            return;
        }

        babyRef = mDatabase.child("babies").child(userId).child(activeBabyId);
        babyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Double weight = snapshot.child("weight").getValue(Double.class);
                String dateOfBirth = snapshot.child("dateOfBirth").getValue(String.class);
                String sex = snapshot.child("sex").getValue(String.class);

                if (weight != null) {
                    babyWeightKg = weight;
                }
                if (dateOfBirth != null) {
                    babyDateOfBirth = dateOfBirth;
                }
                if (sex != null) {
                    babySex = sex;
                }

                updateBabyComparison();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(
                        getContext(),
                        "Error loading baby profile: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        };

        babyRef.addValueEventListener(babyListener);
    }

    private void loadFeedingsFromFirebase() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "User is not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        feedingsRef = mDatabase.child("feedings").child(userId).child(selectedDate);
        feedingsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                feedings.clear();
                feedingKeys.clear();
                feedingAmounts.clear();
                feedingTimes.clear();

                int totalFeedings = 0;
                float totalMilk = 0;

                if (!snapshot.exists()) {
                    feedings.add("No feedings recorded for this date");
                    feedingKeys.add(null);
                    feedingAmounts.add(0f);
                    feedingTimes.add("");
                } else {
                    for (DataSnapshot feedingSnapshot : snapshot.getChildren()) {
                        String key = feedingSnapshot.getKey();
                        String time = feedingSnapshot.child("time").getValue(String.class);
                        Float amountMl = feedingSnapshot.child("amountMl").getValue(Float.class);
                        Float durationSeconds = feedingSnapshot.child("durationSeconds").getValue(Float.class);
                        String side = feedingSnapshot.child("side").getValue(String.class);
                        String feedType = feedingSnapshot.child("feedType").getValue(String.class);

                        if (time == null) {
                            time = "--:--";
                        }
                        if (amountMl == null) {
                            amountMl = 0f;
                        }
                        if (durationSeconds == null) {
                            durationSeconds = 0f;
                        }

                        String sideText = (side != null && !side.isEmpty()) ? "  (" + side + ")" : "";
                        String typeText = (feedType != null && !feedType.isEmpty()) ? " · " + feedType : "";

                        StringBuilder sb = new StringBuilder();
                        sb.append("Time: ").append(time).append(sideText);
                        sb.append("\nAmount: ").append(Math.round(amountMl)).append(" ml").append(typeText);
                        if (durationSeconds > 0) {
                            sb.append("\nDuration: ").append(formatDuration(durationSeconds));
                        }

                        totalFeedings++;
                        totalMilk += amountMl;

                        feedings.add(sb.toString());
                        feedingKeys.add(key);
                        feedingAmounts.add(amountMl);
                        feedingTimes.add(time);
                    }
                }

                totalMilkForDay = totalMilk;

                float averageFeeding = 0;
                if (totalFeedings > 0) {
                    averageFeeding = totalMilk / totalFeedings;
                }

                tvTotalFeedings.setText("Total Feedings: " + totalFeedings);
                tvTotalMilk.setText(String.format(Locale.getDefault(), "Total Milk: %.0f ml", totalMilk));
                tvAverageFeeding.setText(String.format(Locale.getDefault(), "Average Feeding: %.0f ml", averageFeeding));

                updateBabyComparison();

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(
                        getContext(),
                        "Error loading feedings: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        };

        feedingsRef.addValueEventListener(feedingsListener);
    }

    private void loadDiapersFromFirebase() {
        if (mAuth.getCurrentUser() == null) {
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        diaperRef = mDatabase.child("diapers").child(userId).child(selectedDate);
        diaperListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int wet = 0, dirty = 0, total = 0;
                StringBuilder lines = new StringBuilder();

                for (DataSnapshot d : snapshot.getChildren()) {
                    String type = d.child("type").getValue(String.class);
                    String time = d.child("time").getValue(String.class);
                    String stool = d.child("stool").getValue(String.class);

                    if (type == null) {
                        type = "Wet";
                    }
                    if (time == null) {
                        time = "--:--";
                    }

                    total++;
                    if ("Wet".equals(type) || "Mixed".equals(type)) {
                        wet++;
                    }
                    if ("Dirty".equals(type) || "Mixed".equals(type)) {
                        dirty++;
                    }

                    String stoolText = (stool != null && !stool.isEmpty()) ? " (" + stool + ")" : "";
                    lines.append("\n").append(time).append(" · ").append(type).append(stoolText);
                }

                String header = "Today: " + total + "  (" + wet + " wet, " + dirty + " dirty)";
                tvDiaperSummary.setText(header + lines.toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(
                        getContext(),
                        "Error loading diapers: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        };

        diaperRef.addValueEventListener(diaperListener);
    }

    private void showManualFeedingDialog() {
        if (selectedDate == null || selectedDate.isEmpty()) {
            Toast.makeText(getContext(), "Missing selected date", Toast.LENGTH_SHORT).show();
            return;
        }

        Context ctx = requireContext();
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);

        EditText etTime = new EditText(ctx);
        etTime.setHint("Time (HH:mm)");
        etTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        etTime.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);

        EditText etAmount = new EditText(ctx);
        etAmount.setHint("Amount (ml)");
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        TextView typeLabel = new TextView(ctx);
        typeLabel.setText("Type");
        typeLabel.setPadding(0, pad, 0, 0);

        Spinner typeSpinner = new Spinner(ctx);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                ctx, android.R.layout.simple_spinner_item,
                new String[]{"Breast milk (bottle)", "Formula"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);

        layout.addView(etTime);
        layout.addView(etAmount);
        layout.addView(typeLabel);
        layout.addView(typeSpinner);

        new AlertDialog.Builder(ctx)
                .setTitle("Add manual feeding")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String time = etTime.getText().toString().trim();
                    String amountStr = etAmount.getText().toString().trim();
                    String type = (String) typeSpinner.getSelectedItem();

                    if (time.isEmpty() || amountStr.isEmpty()) {
                        Toast.makeText(ctx, "Please fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    float amount;
                    try {
                        amount = Float.parseFloat(amountStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(ctx, "Please enter a valid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    saveManualFeeding(time, amount, type);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveManualFeeding(String time, float amount, String type) {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "User is not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        Map<String, Object> feedingData = new HashMap<>();
        feedingData.put("time", time);
        feedingData.put("amountMl", amount);
        feedingData.put("durationSeconds", 0f);
        feedingData.put("date", selectedDate);
        feedingData.put("side", "");
        feedingData.put("feedType", type);
        feedingData.put("createdAt", System.currentTimeMillis());

        mDatabase.child("feedings").child(userId).child(selectedDate).push()
                .setValue(feedingData)
                .addOnSuccessListener(a ->
                        Toast.makeText(getContext(), "Feeding saved", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showAddDiaperDialog() {
        Context ctx = requireContext();
        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);

        TextView typeLabel = new TextView(ctx);
        typeLabel.setText("Type");

        Spinner typeSpinner = new Spinner(ctx);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                ctx, android.R.layout.simple_spinner_item,
                new String[]{"Wet", "Dirty", "Mixed"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);

        TextView stoolLabel = new TextView(ctx);
        stoolLabel.setText("Stool (for dirty)");
        stoolLabel.setPadding(0, pad, 0, 0);

        Spinner stoolSpinner = new Spinner(ctx);
        ArrayAdapter<String> stoolAdapter = new ArrayAdapter<>(
                ctx, android.R.layout.simple_spinner_item,
                new String[]{"—", "Normal", "Soft", "Watery", "Hard", "Mucousy"});
        stoolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        stoolSpinner.setAdapter(stoolAdapter);

        layout.addView(typeLabel);
        layout.addView(typeSpinner);
        layout.addView(stoolLabel);
        layout.addView(stoolSpinner);

        new AlertDialog.Builder(ctx)
                .setTitle("Add Diaper")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String type = (String) typeSpinner.getSelectedItem();
                    String stool = (String) stoolSpinner.getSelectedItem();

                    if ("Wet".equals(type) || "—".equals(stool)) {
                        stool = "";
                    }

                    saveDiaper(type, stool);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveDiaper(String type, String stool) {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "User is not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedDate == null || selectedDate.isEmpty()) {
            Toast.makeText(getContext(), "Missing selected date", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

        Map<String, Object> diaper = new HashMap<>();
        diaper.put("time", currentTime);
        diaper.put("type", type);
        diaper.put("stool", stool);
        diaper.put("createdAt", System.currentTimeMillis());

        mDatabase.child("diapers").child(userId).child(selectedDate).push()
                .setValue(diaper)
                .addOnSuccessListener(a ->
                        Toast.makeText(getContext(), "Diaper saved", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error saving diaper: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showFeedingOptions(String key, String time, float amount) {
        String[] options = {"Edit", "Delete"};

        new AlertDialog.Builder(requireContext())
                .setTitle("Feeding options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditDialog(key, time, amount);
                    } else {
                        confirmDelete(key);
                    }
                })
                .show();
    }

    private void confirmDelete(String key) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete feeding")
                .setMessage("Are you sure you want to delete this feeding?")
                .setPositiveButton("Delete", (d, w) -> deleteFeeding(key))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFeeding(String key) {
        if (mAuth.getCurrentUser() == null) {
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        mDatabase.child("feedings").child(userId).child(selectedDate).child(key)
                .removeValue()
                .addOnSuccessListener(a ->
                        Toast.makeText(getContext(), "Feeding deleted", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error deleting: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showEditDialog(String key, String currentTime, float currentAmount) {
        Context ctx = requireContext();

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        EditText etTime = new EditText(ctx);
        etTime.setHint("Time (HH:mm)");
        etTime.setText(currentTime);
        etTime.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);

        EditText etAmount = new EditText(ctx);
        etAmount.setHint("Amount (ml)");
        etAmount.setText(String.valueOf(Math.round(currentAmount)));
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        layout.addView(etTime);
        layout.addView(etAmount);

        new AlertDialog.Builder(ctx)
                .setTitle("Edit feeding")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String newTime = etTime.getText().toString().trim();
                    String amountStr = etAmount.getText().toString().trim();

                    if (newTime.isEmpty() || amountStr.isEmpty()) {
                        Toast.makeText(ctx, "Please fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    float newAmount;
                    try {
                        newAmount = Float.parseFloat(amountStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(ctx, "Please enter a valid amount", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    updateFeeding(key, newTime, newAmount);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateFeeding(String key, String newTime, float newAmount) {
        if (mAuth.getCurrentUser() == null) {
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        Map<String, Object> updates = new HashMap<>();
        updates.put("time", newTime);
        updates.put("amountMl", newAmount);

        mDatabase.child("feedings").child(userId).child(selectedDate).child(key)
                .updateChildren(updates)
                .addOnSuccessListener(a ->
                        Toast.makeText(getContext(), "Feeding updated", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error updating: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void updateBabyComparison() {
        if (babyWeightKg <= 0 || babyDateOfBirth.isEmpty()) {
            tvBabyAge.setText("Baby: Missing profile");
            tvExpectedDailyIntake.setText("Expected Daily Intake: --");
            tvIntakeComparison.setText("Comparison: Please select / update baby");
            return;
        }

        int ageInMonths = calculateAgeInMonths(babyDateOfBirth);

        String sexPrefix = babySex.isEmpty() ? "" : babySex + " · ";
        tvBabyAge.setText("Baby: " + sexPrefix + ageInMonths + " months");

        if (ageInMonths > 6) {
            tvExpectedDailyIntake.setText("Expected Daily Intake: Ask pediatrician");
            tvIntakeComparison.setText("Comparison: Recommendation depends on solids and feeding type");
            return;
        }

        double expectedDailyIntake = babyWeightKg * 150;
        double percentage = 0;

        if (expectedDailyIntake > 0) {
            percentage = (totalMilkForDay / expectedDailyIntake) * 100;
        }

        tvExpectedDailyIntake.setText(
                "Expected Daily Intake: about " + Math.round(expectedDailyIntake) + " ml (estimate)"
        );

        tvIntakeComparison.setText(
                "Comparison: " + Math.round(percentage) + "% of estimated daily intake"
        );
    }

    private int calculateAgeInMonths(String dateOfBirth) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date birthDate = sdf.parse(dateOfBirth);

            if (birthDate == null) {
                return 0;
            }

            long diffMillis = System.currentTimeMillis() - birthDate.getTime();
            long days = diffMillis / (1000L * 60L * 60L * 24L);

            return (int) (days / 30);

        } catch (Exception e) {
            return 0;
        }
    }

    private String formatDuration(float durationSeconds) {
        int totalSeconds = Math.round(durationSeconds);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        if (minutes > 0) {
            return minutes + " min " + seconds + " sec";
        } else {
            return seconds + " sec";
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (babyRef != null && babyListener != null) {
            babyRef.removeEventListener(babyListener);
        }
        if (feedingsRef != null && feedingsListener != null) {
            feedingsRef.removeEventListener(feedingsListener);
        }
        if (diaperRef != null && diaperListener != null) {
            diaperRef.removeEventListener(diaperListener);
        }
    }
}