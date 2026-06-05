package com.example.bcalm;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Regions;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PumpDetailsFragment extends Fragment {

    private String selectedDate;
    private TextView dateTitle;
    private Button btnToggle, btnReset, btnShowFlowChart;
    private LineChart reportingChart, flowRateChart;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private static final String CUSTOMER_SPECIFIC_ENDPOINT = "afcbrkaevro2d-ats.iot.eu-north-1.amazonaws.com";
    private static final String COGNITO_POOL_ID = "eu-north-1:a406de84-53ac-4327-8d00-905b85ae049b";
    private static final Regions MY_REGION = Regions.EU_NORTH_1;

    private AWSIotMqttManager mqttManager;
    private String clientId;
    private boolean isConnected = false;
    private boolean isPumping = false;
    private boolean isFlowChartVisible = false;

    private List<Entry> volumeEntries = new ArrayList<>();
    private List<Entry> flowEntries = new ArrayList<>();

    private float lastKnownAmount = 0;
    private float totalSecondsElapsed = 0;
    private long lastUpdateTimestamp = 0;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_pump_details, container, false);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        if (getArguments() != null) {
            selectedDate = getArguments().getString("selected_date");
        }

        if (selectedDate == null) {
            selectedDate = "";
        }

        dateTitle = view.findViewById(R.id.dateTitle);
        btnToggle = view.findViewById(R.id.btnToggle);
        btnReset = view.findViewById(R.id.btnReset);
        btnShowFlowChart = view.findViewById(R.id.btnShowFlowChart);
        reportingChart = view.findViewById(R.id.reportingChart);
        flowRateChart = view.findViewById(R.id.flowRateChart);

        dateTitle.setText("Data for: " + selectedDate);

        resetLocalData();

        btnToggle.setOnClickListener(v -> togglePumpingAction());
        btnReset.setOnClickListener(v -> resetPumpingAction());

        btnShowFlowChart.setOnClickListener(v -> {
            isFlowChartVisible = !isFlowChartVisible;
            flowRateChart.setVisibility(isFlowChartVisible ? View.VISIBLE : View.GONE);
            btnShowFlowChart.setText(isFlowChartVisible ? "Hide Flow Rate Chart" : "Show Flow Rate Chart");
        });

        setupCharts();
        initAWS();

        return view;
    }

    private void initAWS() {
        if (getContext() == null) return;

        clientId = UUID.randomUUID().toString();

        CognitoCachingCredentialsProvider credentialsProvider =
                new CognitoCachingCredentialsProvider(
                        getContext().getApplicationContext(),
                        COGNITO_POOL_ID,
                        MY_REGION
                );

        mqttManager = new AWSIotMqttManager(clientId, CUSTOMER_SPECIFIC_ENDPOINT);

        new Thread(() -> {
            try {
                mqttManager.connect(credentialsProvider, (status, throwable) -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (status == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected) {
                                isConnected = true;
                                subscribeToSensorData();

                                mqttManager.publishString(
                                        "{\"action\":\"reset\"}",
                                        "esp32/sub",
                                        AWSIotMqttQos.QOS0
                                );

                                Toast.makeText(
                                        getContext(),
                                        "Session Started & Hardware Reset",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        });
                    }
                });
            } catch (Exception e) {
                Log.e("AWS_IOT", "Error", e);
            }
        }).start();
    }

    private void subscribeToSensorData() {
        mqttManager.subscribeToTopic("esp32/pub", AWSIotMqttQos.QOS0, (topic, data) -> {
            if (!isPumping) return;

            String message = new String(data, StandardCharsets.UTF_8);

            try {
                JSONObject json = new JSONObject(message);

                float totalMl = (float) json.getDouble("total_ml");
                float flowRate = (float) json.getDouble("flow_rate");

                if (totalMl < 0) return;

                lastKnownAmount = totalMl;

                long currentTime = System.currentTimeMillis();

                if (lastUpdateTimestamp > 0) {
                    float delta = (currentTime - lastUpdateTimestamp) / 1000f;
                    totalSecondsElapsed += delta;
                }

                lastUpdateTimestamp = currentTime;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        updateVolumeChart(totalSecondsElapsed, totalMl);
                        updateFlowChart(totalSecondsElapsed, flowRate);
                    });
                }

            } catch (Exception e) {
                Log.e("AWS_IOT", "JSON Error", e);
            }
        });
    }

    private void setupCharts() {
        reportingChart.getDescription().setEnabled(false);
        reportingChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        reportingChart.getAxisRight().setEnabled(false);
        reportingChart.setNoDataText("Press START to see data");

        flowRateChart.getDescription().setEnabled(false);
        flowRateChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        flowRateChart.getAxisRight().setEnabled(false);
        flowRateChart.setNoDataText("Press START to see data");
    }

    private void updateVolumeChart(float time, float amount) {
        volumeEntries.add(new Entry(time, amount));

        LineDataSet dataSet;

        if (reportingChart.getData() != null && reportingChart.getData().getDataSetCount() > 0) {
            dataSet = (LineDataSet) reportingChart.getData().getDataSetByIndex(0);
            dataSet.setValues(volumeEntries);
            reportingChart.getData().notifyDataChanged();
            reportingChart.notifyDataSetChanged();
        } else {
            dataSet = new LineDataSet(volumeEntries, "Total Volume (ml)");
            dataSet.setColor(Color.BLUE);
            dataSet.setDrawFilled(true);
            dataSet.setFillColor(Color.BLUE);
            dataSet.setFillAlpha(50);
            reportingChart.setData(new LineData(dataSet));
        }

        reportingChart.invalidate();
    }

    private void updateFlowChart(float time, float flowRate) {
        flowEntries.add(new Entry(time, flowRate));

        LineDataSet dataSet;

        if (flowRateChart.getData() != null && flowRateChart.getData().getDataSetCount() > 0) {
            dataSet = (LineDataSet) flowRateChart.getData().getDataSetByIndex(0);
            dataSet.setValues(flowEntries);
            flowRateChart.getData().notifyDataChanged();
            flowRateChart.notifyDataSetChanged();
        } else {
            dataSet = new LineDataSet(flowEntries, "Flow Rate (L/min)");
            dataSet.setColor(Color.parseColor("#FF9800"));
            dataSet.setLineWidth(2f);
            dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            flowRateChart.setData(new LineData(dataSet));
        }

        flowRateChart.invalidate();
    }

    private void togglePumpingAction() {
        if (!isConnected) {
            Toast.makeText(getContext(), "Device is not connected yet", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isPumping) {
            isPumping = true;
            lastUpdateTimestamp = System.currentTimeMillis();

            btnToggle.setText("|| STOP");
            btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.RED));

            mqttManager.publishString(
                    "{\"action\":\"start\"}",
                    "esp32/sub",
                    AWSIotMqttQos.QOS0
            );

        } else {
            isPumping = false;
            lastUpdateTimestamp = 0;

            btnToggle.setText("▶ START");
            btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.BLUE));

            mqttManager.publishString(
                    "{\"action\":\"stop\"}",
                    "esp32/sub",
                    AWSIotMqttQos.QOS0
            );

            saveFeedingToFirebase();
        }
    }

    private void resetLocalData() {
        isPumping = false;
        totalSecondsElapsed = 0;
        lastUpdateTimestamp = 0;
        lastKnownAmount = 0;

        volumeEntries.clear();
        flowEntries.clear();

        if (reportingChart != null) {
            reportingChart.clear();
        }

        if (flowRateChart != null) {
            flowRateChart.clear();
        }
    }

    private void resetPumpingAction() {
        resetLocalData();

        btnToggle.setText("▶ START");
        btnToggle.setBackgroundTintList(ColorStateList.valueOf(Color.BLUE));

        if (isConnected) {
            mqttManager.publishString(
                    "{\"action\":\"reset\"}",
                    "esp32/sub",
                    AWSIotMqttQos.QOS0
            );
        }

        reportingChart.invalidate();
        flowRateChart.invalidate();
    }

    private void saveFeedingToFirebase() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "User is not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedDate == null || selectedDate.isEmpty()) {
            Toast.makeText(getContext(), "Missing selected date", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lastKnownAmount <= 0) {
            Toast.makeText(getContext(), "No feeding amount to save", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        String currentTime = new SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
        ).format(new Date());

        Map<String, Object> feedingData = new HashMap<>();
        feedingData.put("time", currentTime);
        feedingData.put("amountMl", lastKnownAmount);
        feedingData.put("durationSeconds", totalSecondsElapsed);
        feedingData.put("date", selectedDate);
        feedingData.put("createdAt", System.currentTimeMillis());

        mDatabase
                .child("feedings")
                .child(userId)
                .child(selectedDate)
                .push()
                .setValue(feedingData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(
                            getContext(),
                            "Feeding saved successfully",
                            Toast.LENGTH_SHORT
                    ).show();

                    if (getView() != null) {
                        Navigation.findNavController(getView()).popBackStack();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(
                            getContext(),
                            "Error saving feeding: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }
}