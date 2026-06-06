package com.example.bcalm;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BabyProfileFragment extends Fragment {

    private EditText etBabyName, etDateOfBirth, etWeight;
    private Spinner spinnerCountry;
    private Button btnSaveProfile;

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private String[] countries = {"Israel", "USA", "UK", "Germany", "France", "Canada"};
    private ArrayAdapter<String> countryAdapter;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_baby_profile, container, false);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        etBabyName = view.findViewById(R.id.etBabyName);
        etDateOfBirth = view.findViewById(R.id.etDateOfBirth);
        etWeight = view.findViewById(R.id.etWeight);
        spinnerCountry = view.findViewById(R.id.spinnerCountry);
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile);

        etDateOfBirth.setOnClickListener(v -> showDatePicker());

        countryAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                countries
        );

        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCountry.setAdapter(countryAdapter);

        loadBabyProfile();

        btnSaveProfile.setOnClickListener(v -> saveBabyProfile());

        return view;
    }

    private void loadBabyProfile() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "User is not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        mDatabase.child("babies").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) {
                            return;
                        }

                        String fullName = snapshot.child("fullName").getValue(String.class);
                        String dateOfBirth = snapshot.child("dateOfBirth").getValue(String.class);
                        Double weight = snapshot.child("weight").getValue(Double.class);
                        String country = snapshot.child("country").getValue(String.class);

                        if (fullName != null) {
                            etBabyName.setText(fullName);
                        }

                        if (dateOfBirth != null) {
                            etDateOfBirth.setText(dateOfBirth);
                        }

                        if (weight != null) {
                            etWeight.setText(String.valueOf(weight));
                        }

                        if (country != null) {
                            int countryPosition = countryAdapter.getPosition(country);

                            if (countryPosition >= 0) {
                                spinnerCountry.setSelection(countryPosition);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(
                                getContext(),
                                "Error loading profile: " + error.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedDate = Calendar.getInstance();
                    selectedDate.set(year, month, dayOfMonth);

                    SimpleDateFormat sdf = new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                    );

                    etDateOfBirth.setText(sdf.format(selectedDate.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }

    private void saveBabyProfile() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "User is not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = etBabyName.getText().toString().trim();
        String dateOfBirth = etDateOfBirth.getText().toString().trim();
        String weightStr = etWeight.getText().toString().trim();
        String country = spinnerCountry.getSelectedItem().toString();
        String userId = mAuth.getCurrentUser().getUid();

        if (name.isEmpty() || dateOfBirth.isEmpty() || weightStr.isEmpty()) {
            Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double weight;

        try {
            weight = Double.parseDouble(weightStr);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Please enter a valid weight", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> babyData = new HashMap<>();
        babyData.put("fullName", name);
        babyData.put("dateOfBirth", dateOfBirth);
        babyData.put("weight", weight);
        babyData.put("country", country);
        babyData.put("lastUpdated", System.currentTimeMillis());

        mDatabase.child("babies").child(userId).setValue(babyData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show();

                    if (getFragmentManager() != null) {
                        getFragmentManager().popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}