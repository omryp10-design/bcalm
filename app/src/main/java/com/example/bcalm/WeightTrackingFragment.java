package com.example.bcalm;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WeightTrackingFragment extends Fragment {

    private TextView tvLatestWeight, tvWeightChange;
    private LineChart weightChart;
    private Button btnAddWeight;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private DatabaseReference weightRef;
    private ValueEventListener weightListener;

    private static class Measurement {
        String date;
        float weight;

        Measurement(String date, float weight) {
            this.date = date;
            this.weight = weight;
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_weight_tracking, container, false);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        tvLatestWeight = view.findViewById(R.id.tvLatestWeight);
        tvWeightChange = view.findViewById(R.id.tvWeightChange);
        weightChart = view.findViewById(R.id.weightChart);
        btnAddWeight = view.findViewById(R.id.btnAddWeight);

        setupChartLook();
        loadWeights();

        btnAddWeight.setOnClickListener(v -> showAddWeightDialog());

        ThemeUtil.applyBabyBackground(view);

        return view;
    }

    private void setupChartLook() {
        weightChart.getDescription().setEnabled(false);
        weightChart.getLegend().setEnabled(false);
        weightChart.getAxisRight().setEnabled(false);
        weightChart.setNoDataText("No weight measurements yet");
        weightChart.setScaleEnabled(false);

        XAxis x = weightChart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setDrawGridLines(false);
    }

    private void loadWeights() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "User is not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        weightRef = mDatabase.child("weights").child(userId);
        weightListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<Measurement> list = new ArrayList<>();

                for (DataSnapshot m : snapshot.getChildren()) {
                    String date = m.child("date").getValue(String.class);
                    Float weight = m.child("weightKg").getValue(Float.class);
                    if (date != null && weight != null) {
                        list.add(new Measurement(date, weight));
                    }
                }

                Collections.sort(list, new Comparator<Measurement>() {
                    @Override
                    public int compare(Measurement a, Measurement b) {
                        return a.date.compareTo(b.date);
                    }
                });

                showChart(list);
                showSummary(list);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(
                        getContext(),
                        "Error loading weights: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        };

        weightRef.addValueEventListener(weightListener);
    }

    private void showChart(ArrayList<Measurement> list) {
        if (list.isEmpty()) {
            weightChart.clear();
            weightChart.invalidate();
            return;
        }

        ArrayList<Entry> entries = new ArrayList<>();
        final ArrayList<String> chartLabels = new ArrayList<>();

        SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat outFmt = new SimpleDateFormat("dd/MM", Locale.getDefault());

        for (int i = 0; i < list.size(); i++) {
            entries.add(new Entry(i, list.get(i).weight));

            String label = list.get(i).date;
            try {
                Date d = inFmt.parse(list.get(i).date);
                if (d != null) {
                    label = outFmt.format(d);
                }
            } catch (Exception ignored) {
            }
            chartLabels.add(label);
        }

        LineDataSet dataSet = new LineDataSet(entries, "Weight (kg)");
        dataSet.setColor(Color.parseColor("#EE8FB4"));
        dataSet.setCircleColor(Color.parseColor("#E1709F"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.parseColor("#3A4A5A"));

        LineData data = new LineData(dataSet);

        weightChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(chartLabels));
        weightChart.setData(data);
        weightChart.animateX(700);
        weightChart.invalidate();
    }

    private void showSummary(ArrayList<Measurement> list) {
        if (list.isEmpty()) {
            tvLatestWeight.setText("Latest: --");
            tvWeightChange.setText("Change: --");
            return;
        }

        Measurement last = list.get(list.size() - 1);
        tvLatestWeight.setText(String.format(Locale.getDefault(),
                "Latest: %.2f kg (%s)", last.weight, last.date));

        if (list.size() >= 2) {
            Measurement prev = list.get(list.size() - 2);
            float diff = last.weight - prev.weight;
            String sign = diff >= 0 ? "+" : "";
            tvWeightChange.setText(String.format(Locale.getDefault(),
                    "Change: %s%.2f kg since previous", sign, diff));
        } else {
            tvWeightChange.setText("Change: -- (need 2 measurements)");
        }
    }

    private void showAddWeightDialog() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "User is not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);

        final EditText etDate = new EditText(requireContext());
        etDate.setHint("Date (YYYY-MM-DD)");
        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
        etDate.setOnClickListener(v -> openDatePicker(etDate));

        final EditText etWeight = new EditText(requireContext());
        etWeight.setHint("Weight (kg)");
        etWeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        layout.addView(etDate);
        layout.addView(etWeight);

        new AlertDialog.Builder(requireContext())
                .setTitle("Add weight measurement")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String date = etDate.getText().toString().trim();
                    String weightStr = etWeight.getText().toString().trim();

                    if (date.isEmpty() || weightStr.isEmpty()) {
                        Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    float weight;
                    try {
                        weight = Float.parseFloat(weightStr);
                    } catch (NumberFormatException e) {
                        Toast.makeText(getContext(), "Please enter a valid weight", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    saveWeight(date, weight);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openDatePicker(final EditText target) {
        Calendar cal = Calendar.getInstance();

        DatePickerDialog dp = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar c = Calendar.getInstance();
                    c.set(year, month, dayOfMonth);
                    target.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(c.getTime()));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        );

        dp.getDatePicker().setMaxDate(System.currentTimeMillis());
        dp.show();
    }

    private void saveWeight(String date, float weight) {
        String userId = mAuth.getCurrentUser().getUid();

        Map<String, Object> measurement = new HashMap<>();
        measurement.put("date", date);
        measurement.put("weightKg", weight);
        measurement.put("createdAt", System.currentTimeMillis());

        mDatabase.child("weights").child(userId).push()
                .setValue(measurement)
                .addOnSuccessListener(a -> {
                    String activeBabyId = BabyManager.getActiveBabyId(requireContext());
                    if (activeBabyId != null) {
                        mDatabase.child("babies").child(userId).child(activeBabyId)
                                .child("weight").setValue((double) weight);
                    }
                    Toast.makeText(getContext(), "Weight saved", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error saving: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (weightRef != null && weightListener != null) {
            weightRef.removeEventListener(weightListener);
        }
    }
}