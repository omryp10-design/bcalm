package com.example.bcalm;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class Home_Fragment extends Fragment {

    private CalendarView calendarView;
    private Button btnLogout;
    private Button btnWeeklyTrends;
    private Button btnGrowth;
    private Button btnManageBabies;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        calendarView = view.findViewById(R.id.calendarView);
        btnLogout = view.findViewById(R.id.btnLogout);
        btnWeeklyTrends = view.findViewById(R.id.btnWeeklyTrends);
        btnGrowth = view.findViewById(R.id.btnGrowth);
        btnManageBabies = view.findViewById(R.id.btnManageBabies);

        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(
                    @NonNull CalendarView view,
                    int year, int month, int dayOfMonth) {
                String selectedDate = formatDateKey(year, month, dayOfMonth);
                navigateToDailySummary(view, selectedDate);
            }
        });

        btnManageBabies.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_home_Fragment_to_babiesFragment));

        btnWeeklyTrends.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_home_Fragment_to_weeklyTrendsFragment));

        btnGrowth.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_home_Fragment_to_weightTrackingFragment));

        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Navigation.findNavController(v).navigate(R.id.login_Fragment);
        });

        updateBabiesButtonLabel();

        ThemeUtil.applyBabyBackground(view);

        return view;
    }

    private void updateBabiesButtonLabel() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseDatabase.getInstance().getReference()
                .child("babies").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int count = 0;
                        for (DataSnapshot c : snapshot.getChildren()) {
                            if (c.hasChild("fullName")) {
                                count++;
                            }
                        }

                        if (!isAdded() || btnManageBabies == null) {
                            return;
                        }

                        btnManageBabies.setText(count > 1 ? "👶  My Babies" : "👶  My Baby");
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });
    }

    private String formatDateKey(int year, int month, int dayOfMonth) {
        int realMonth = month + 1;
        return String.format("%04d-%02d-%02d", year, realMonth, dayOfMonth);
    }

    private void navigateToDailySummary(View view, String date) {
        Bundle bundle = new Bundle();
        bundle.putString("selected_date", date);
        Navigation.findNavController(view)
                .navigate(R.id.action_home_Fragment_to_dailySummaryFragment, bundle);
    }
}