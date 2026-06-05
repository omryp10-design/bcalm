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

public class Home_Fragment extends Fragment {

    private CalendarView calendarView;
    private Button btnUpdateBaby;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        calendarView = view.findViewById(R.id.calendarView);
        btnUpdateBaby = view.findViewById(R.id.btnUpdateBaby);

        calendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(
                    @NonNull CalendarView view,
                    int year,
                    int month,
                    int dayOfMonth
            ) {
                String selectedDate = dayOfMonth + "/" + (month + 1) + "/" + year;
                navigateToDailySummary(view, selectedDate);
            }
        });

        btnUpdateBaby.setOnClickListener(v -> {
            Navigation.findNavController(v)
                    .navigate(R.id.action_home_Fragment_to_babyProfileFragment);
        });

        return view;
    }

    private void navigateToDailySummary(View view, String date) {
        Bundle bundle = new Bundle();
        bundle.putString("selected_date", date);

        Navigation.findNavController(view)
                .navigate(R.id.action_home_Fragment_to_dailySummaryFragment, bundle);
    }
}