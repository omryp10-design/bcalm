package com.example.bcalm;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.util.Locale;

public class DailySummaryFragment extends Fragment {

    private String selectedDate;

    private TextView tvDailySummaryTitle;
    private TextView tvTotalFeedings;
    private TextView tvTotalMilk;
    private TextView tvAverageFeeding;
    private TextView tvBabyAge;
    private TextView tvExpectedDailyIntake;
    private TextView tvIntakeComparison;

    private ListView lvFeedings;
    private Button btnAddFeeding;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private ArrayList<String> feedings;
    private ArrayAdapter<String> adapter;

    private float totalMilkForDay = 0;
    private double babyWeightKg = 0;
    private String babyDateOfBirth = "";

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

        lvFeedings = view.findViewById(R.id.lvFeedings);
        btnAddFeeding = view.findViewById(R.id.btnAddFeeding);

        if (getArguments() != null) {
            selectedDate = getArguments().getString("selected_date");
        }

        if (selectedDate == null) {
            selectedDate = "";
        }

        tvDailySummaryTitle.setText("Daily Summary - " + selectedDate);

        feedings = new ArrayList<>();

        adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                feedings
        );

        lvFeedings.setAdapter(adapter);

        loadBabyProfileFromFirebase();
        loadFeedingsFromFirebase();

        btnAddFeeding.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("selected_date", selectedDate);

            Navigation.findNavController(v)
                    .navigate(R.id.action_dailySummaryFragment_to_pumpDetailsFragment, bundle);
        });

        return view;
    }

    private void loadBabyProfileFromFirebase() {
        if (mAuth.getCurrentUser() == null) {
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        mDatabase
                .child("babies")
                .child(userId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Double weight = snapshot.child("weight").getValue(Double.class);
                        String dateOfBirth = snapshot.child("dateOfBirth").getValue(String.class);

                        if (weight != null) {
                            babyWeightKg = weight;
                        }

                        if (dateOfBirth != null) {
                            babyDateOfBirth = dateOfBirth;
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
                });
    }

    private void loadFeedingsFromFirebase() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "User is not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        mDatabase
                .child("feedings")
                .child(userId)
                .child(selectedDate)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        feedings.clear();

                        int totalFeedings = 0;
                        float totalMilk = 0;

                        if (!snapshot.exists()) {
                            feedings.add("No feedings recorded for this date");
                        } else {
                            for (DataSnapshot feedingSnapshot : snapshot.getChildren()) {
                                String time = feedingSnapshot.child("time").getValue(String.class);
                                Float amountMl = feedingSnapshot.child("amountMl").getValue(Float.class);
                                Float durationSeconds = feedingSnapshot.child("durationSeconds").getValue(Float.class);

                                if (time == null) {
                                    time = "--:--";
                                }

                                if (amountMl == null) {
                                    amountMl = 0f;
                                }

                                if (durationSeconds == null) {
                                    durationSeconds = 0f;
                                }

                                totalFeedings++;
                                totalMilk += amountMl;

                                String feedingText =
                                        "Time: " + time +
                                                "\nAmount: " + amountMl + " ml" +
                                                "\nDuration: " + formatDuration(durationSeconds);

                                feedings.add(feedingText);
                            }
                        }

                        totalMilkForDay = totalMilk;

                        float averageFeeding = 0;

                        if (totalFeedings > 0) {
                            averageFeeding = totalMilk / totalFeedings;
                        }

                        tvTotalFeedings.setText("Total Feedings: " + totalFeedings);
                        tvTotalMilk.setText("Total Milk: " + totalMilk + " ml");
                        tvAverageFeeding.setText("Average Feeding: " + averageFeeding + " ml");

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
                });
    }

    private void updateBabyComparison() {
        if (babyWeightKg <= 0 || babyDateOfBirth.isEmpty()) {
            tvBabyAge.setText("Baby Age: Missing baby profile");
            tvExpectedDailyIntake.setText("Expected Daily Intake: --");
            tvIntakeComparison.setText("Comparison: Please update baby's profile");
            return;
        }

        int ageInMonths = calculateAgeInMonths(babyDateOfBirth);

        tvBabyAge.setText("Baby Age: " + ageInMonths + " months");

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
                "Expected Daily Intake: about " + Math.round(expectedDailyIntake) + " ml"
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
}