package com.example.bcalm;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class WeeklyTrendsFragment extends Fragment {

    private TextView tvWeekTotal, tvWeekAverage;
    private BarChart weeklyChart;
    private Button btnExportPdf;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private final String[] dateKeys = new String[7];
    private final String[] labels = new String[7];

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_weekly_trends, container, false);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        tvWeekTotal = view.findViewById(R.id.tvWeekTotal);
        tvWeekAverage = view.findViewById(R.id.tvWeekAverage);
        weeklyChart = view.findViewById(R.id.weeklyChart);
        btnExportPdf = view.findViewById(R.id.btnExportPdf);

        buildLast7Days();
        setupChartLook();
        loadWeeklyData();

        btnExportPdf.setOnClickListener(v -> exportPdfReport());

        ThemeUtil.applyBabyBackground(view);

        return view;
    }

    private void buildLast7Days() {
        SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat labelFmt = new SimpleDateFormat("dd/MM", Locale.getDefault());

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);

        for (int i = 0; i < 7; i++) {
            dateKeys[i] = keyFmt.format(cal.getTime());
            labels[i] = labelFmt.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private void setupChartLook() {
        weeklyChart.getDescription().setEnabled(false);
        weeklyChart.getLegend().setEnabled(false);
        weeklyChart.getAxisRight().setEnabled(false);
        weeklyChart.setNoDataText("No data for the last 7 days");
        weeklyChart.setFitBars(true);
        weeklyChart.setScaleEnabled(false);

        XAxis x = weeklyChart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setGranularity(1f);
        x.setDrawGridLines(false);
        x.setValueFormatter(new IndexAxisValueFormatter(labels));

        weeklyChart.getAxisLeft().setAxisMinimum(0f);
    }

    private void loadWeeklyData() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "User is not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        mDatabase.child("feedings").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        ArrayList<BarEntry> entries = new ArrayList<>();
                        float weekTotal = 0;

                        for (int i = 0; i < 7; i++) {
                            float dayTotal = 0;

                            DataSnapshot daySnapshot = snapshot.child(dateKeys[i]);
                            for (DataSnapshot feeding : daySnapshot.getChildren()) {
                                Float amountMl = feeding.child("amountMl").getValue(Float.class);
                                if (amountMl != null) {
                                    dayTotal += amountMl;
                                }
                            }

                            entries.add(new BarEntry(i, dayTotal));
                            weekTotal += dayTotal;
                        }

                        showChart(entries);

                        tvWeekTotal.setText(String.format(Locale.getDefault(), "Weekly Total: %.0f ml", weekTotal));
                        tvWeekAverage.setText(String.format(Locale.getDefault(), "Daily Average: %.0f ml", weekTotal / 7f));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(
                                getContext(),
                                "Error loading data: " + error.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void showChart(ArrayList<BarEntry> entries) {
        BarDataSet dataSet = new BarDataSet(entries, "Milk per day (ml)");
        dataSet.setColor(Color.parseColor("#5DA9DC"));
        dataSet.setValueTextColor(Color.parseColor("#3A4A5A"));
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        weeklyChart.setData(data);
        weeklyChart.animateY(800);
        weeklyChart.invalidate();
    }

    // ---------- PDF export ----------

    private void exportPdfReport() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(getContext(), "User is not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getContext(), "Preparing report...", Toast.LENGTH_SHORT).show();

        String userId = mAuth.getCurrentUser().getUid();

        String activeBabyId = BabyManager.getActiveBabyId(requireContext());
        DatabaseReference babyNode = (activeBabyId != null)
                ? mDatabase.child("babies").child(userId).child(activeBabyId)
                : mDatabase.child("babies").child(userId);

        babyNode.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot babySnap) {
                final String name = valueOr(babySnap.child("fullName").getValue(String.class), "-");
                final String dob = valueOr(babySnap.child("dateOfBirth").getValue(String.class), "-");
                final String sex = valueOr(babySnap.child("sex").getValue(String.class), "-");
                Double wgt = babySnap.child("weight").getValue(Double.class);
                final String weight = (wgt != null) ? String.valueOf(wgt) : "-";

                mDatabase.child("feedings").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot feedSnap) {
                        mDatabase.child("diapers").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot diaperSnap) {
                                generateAndShare(name, dob, sex, weight, feedSnap, diaperSnap);
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                generateAndShare(name, dob, sex, weight, feedSnap, null);
                            }
                        });
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private String valueOr(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }

    private void generateAndShare(String name, String dob, String sex, String weight,
                                  DataSnapshot feedSnap, DataSnapshot diaperSnap) {
        int[] feeds = new int[7];
        float[] milk = new float[7];
        int[] wet = new int[7];
        int[] dirty = new int[7];
        float weekMilk = 0;

        for (int i = 0; i < 7; i++) {
            DataSnapshot day = feedSnap.child(dateKeys[i]);
            for (DataSnapshot f : day.getChildren()) {
                Float a = f.child("amountMl").getValue(Float.class);
                feeds[i]++;
                if (a != null) {
                    milk[i] += a;
                    weekMilk += a;
                }
            }

            if (diaperSnap != null) {
                DataSnapshot dday = diaperSnap.child(dateKeys[i]);
                for (DataSnapshot d : dday.getChildren()) {
                    String t = d.child("type").getValue(String.class);
                    if ("Wet".equals(t) || "Mixed".equals(t)) wet[i]++;
                    if ("Dirty".equals(t) || "Mixed".equals(t)) dirty[i]++;
                }
            }
        }

        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = doc.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        int x = 40;
        float y = 60;

        paint.setColor(Color.parseColor("#5DA9DC"));
        paint.setTextSize(24);
        paint.setFakeBoldText(true);
        canvas.drawText("bcalm — Feeding Report", x, y, paint);
        y += 28;

        paint.setColor(Color.DKGRAY);
        paint.setTextSize(11);
        paint.setFakeBoldText(false);
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        canvas.drawText("Generated: " + now, x, y, paint);
        y += 28;

        paint.setColor(Color.BLACK);
        paint.setTextSize(13);
        paint.setFakeBoldText(true);
        canvas.drawText("Baby details", x, y, paint);
        y += 18;
        paint.setFakeBoldText(false);
        canvas.drawText("Name: " + name, x, y, paint); y += 16;
        canvas.drawText("Date of birth: " + dob, x, y, paint); y += 16;
        canvas.drawText("Sex: " + sex, x, y, paint); y += 16;
        canvas.drawText("Weight: " + weight + " kg", x, y, paint); y += 30;

        paint.setFakeBoldText(true);
        paint.setTextSize(13);
        canvas.drawText("Last 7 days", x, y, paint);
        y += 20;

        int[] xs = {40, 210, 290, 400, 470};
        drawRow(canvas, paint, y, new String[]{"Date", "Feeds", "Milk(ml)", "Wet", "Dirty"}, xs);
        y += 6;
        paint.setColor(Color.LTGRAY);
        canvas.drawLine(40, y, 555, y, paint);
        paint.setColor(Color.BLACK);
        y += 16;

        paint.setFakeBoldText(false);
        for (int i = 0; i < 7; i++) {
            drawRow(canvas, paint, y, new String[]{
                    labels[i],
                    String.valueOf(feeds[i]),
                    String.valueOf(Math.round(milk[i])),
                    String.valueOf(wet[i]),
                    String.valueOf(dirty[i])
            }, xs);
            y += 18;
        }

        y += 12;
        paint.setFakeBoldText(true);
        canvas.drawText("Weekly total: " + Math.round(weekMilk) + " ml", x, y, paint);
        y += 18;
        canvas.drawText("Daily average: " + Math.round(weekMilk / 7f) + " ml", x, y, paint);
        y += 30;

        paint.setFakeBoldText(false);
        paint.setTextSize(9);
        paint.setColor(Color.GRAY);
        canvas.drawText("This report is an estimate and not a substitute for medical advice.", x, y, paint);

        doc.finishPage(page);

        try {
            File dir = new File(requireContext().getCacheDir(), "reports");
            dir.mkdirs();
            File file = new File(dir, "bcalm_report.pdf");

            FileOutputStream fos = new FileOutputStream(file);
            doc.writeTo(fos);
            fos.close();
            doc.close();

            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    file
            );

            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Share report"));

        } catch (Exception e) {
            doc.close();
            Toast.makeText(getContext(), "Error creating PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void drawRow(Canvas c, Paint p, float y, String[] cols, int[] xs) {
        for (int i = 0; i < cols.length && i < xs.length; i++) {
            c.drawText(cols[i], xs[i], y, p);
        }
    }
}