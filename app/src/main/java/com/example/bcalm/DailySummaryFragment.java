package com.example.bcalm;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

public class DailySummaryFragment extends Fragment {

    private String selectedDate;
    private TextView tvDailySummaryTitle;
    private ListView lvFeedings;
    private Button btnAddFeeding;

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

        tvDailySummaryTitle = view.findViewById(R.id.tvDailySummaryTitle);
        lvFeedings = view.findViewById(R.id.lvFeedings);
        btnAddFeeding = view.findViewById(R.id.btnAddFeeding);

        if (getArguments() != null) {
            selectedDate = getArguments().getString("selected_date");
        }

        if (selectedDate == null) {
            selectedDate = "";
        }

        tvDailySummaryTitle.setText("Daily Summary - " + selectedDate);

        btnAddFeeding.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("selected_date", selectedDate);

            Navigation.findNavController(v)
                    .navigate(R.id.action_dailySummaryFragment_to_pumpDetailsFragment, bundle);
        });

        return view;
    }
}