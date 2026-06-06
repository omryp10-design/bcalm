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
import android.widget.TextView;
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
    private Spinner spinnerCountry, spinnerSex;
    private Button btnSaveProfile;
    private TextView tvBabyLogo;

    private DatabaseReference mDatabase;
    private FirebaseAuth mAuth;

    private String[] countries = {"Israel", "USA", "UK", "Germany", "France", "Canada"};
    private ArrayAdapter<String> countryAdapter;

    private String[] sexes = {"Boy", "Girl"};
    private ArrayAdapter<String> sexAdapter;

    private String babyId;   // null when creating a new baby
    private boolean isNew;

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

        if (getArguments() != null) {
            isNew = getArguments().getBoolean("isNew", false);
        }

        if (!isNew) {
            babyId = BabyManager.getActiveBabyId(requireContext());
            if (babyId == null) {
                isNew = true; // no active baby yet -> behave as new
            }
        }

        etBabyName = view.findViewById(R.id.etBabyName);
        etDateOfBirth = view.findViewById(R.id.etDateOfBirth);
        etWeight = view.findViewById(R.id.etWeight);
        spinnerCountry = view.findViewById(R.id.spinnerCountry);
        spinnerSex = view.findViewById(R.id.spinnerSex);
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile);
        tvBabyLogo = view.findViewById(R.id.tvBabyLogo);

        etDateOfBirth.setOnClickListener(v -> showDatePicker());

        countryAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, countries);
        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCountry.setAdapter(countryAdapter);

        sexAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, sexes);
        sexAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSex.setAdapter(sexAdapter);

        spinnerSex.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) {
                boolean isBoy = "Boy".equals(sexes[position]);
                view.setBackgroundResource(isBoy ? R.drawable.bg_gradient_boy : R.drawable.bg_gradient_girl);
                tvBabyLogo.setBackgroundResource(isBoy ? R.drawable.bg_logo_circle_boy : R.drawable.bg_logo_circle_girl);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        if (!isNew) {
            loadBabyProfile();
        }

        btnSaveProfile.setOnClickListener(v -> saveBabyProfile());

        return view;
    }

    private void loadBabyProfile() {
        if (mAuth.getCurrentUser() == null || babyId == null) {
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        mDatabase.child("babies").child(userId).child(babyId)
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
                        String sex = snapshot.child("sex").getValue(String.class);

                        if (fullName != null) etBabyName.setText(fullName);
                        if (dateOfBirth != null) etDateOfBirth.setText(dateOfBirth);
                        if (weight != null) etWeight.setText(String.valueOf(weight));

                        if (country != null) {
                            int p = countryAdapter.getPosition(country);
                            if (p >= 0) spinnerCountry.setSelection(p);
                        }
                        if (sex != null) {
                            int p = sexAdapter.getPosition(sex);
                            if (p >= 0) spinnerSex.setSelection(p);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(getContext(),
                                "Error loading profile: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
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
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
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
        String sex = spinnerSex.getSelectedItem().toString();
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

        if (isNew || babyId == null) {
            babyId = mDatabase.child("babies").child(userId).push().getKey();
        }

        if (babyId == null) {
            Toast.makeText(getContext(), "Could not create baby id", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> babyData = new HashMap<>();
        babyData.put("fullName", name);
        babyData.put("dateOfBirth", dateOfBirth);
        babyData.put("weight", weight);
        babyData.put("country", country);
        babyData.put("sex", sex);
        babyData.put("lastUpdated", System.currentTimeMillis());

        final String savedBabyId = babyId;

        mDatabase.child("babies").child(userId).child(babyId).setValue(babyData)
                .addOnSuccessListener(aVoid -> {
                    BabyManager.setActiveBabyId(requireContext(), savedBabyId);
                    Toast.makeText(getContext(), "Profile saved", Toast.LENGTH_SHORT).show();
                    getParentFragmentManager().popBackStack();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}