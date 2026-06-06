package com.example.bcalm;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationUtil {

    public static final String CHANNEL_ID = "bcalm_reminders";
    public static final int REMINDER_NOTIFICATION_ID = 1001;
    public static final int LOW_INTAKE_NOTIFICATION_ID = 1002;

    private static final int REMINDER_REQUEST_CODE = 100;

    private static final String PREFS = "bcalm_prefs";
    private static final String KEY_ENABLED = "reminder_enabled";
    private static final String KEY_HOURS = "reminder_hours";

    private NotificationUtil() {
    }

    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Pump reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Reminders to pump and feeding alerts");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void showNotification(Context context, int id, String title, String text) {
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentPI = PendingIntent.getActivity(
                context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentPI)
                .setAutoCancel(true);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(id, builder.build());
        }
    }

    // ---- reminder scheduling ----

    public static void setReminder(Context context, int hours) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLED, true).putInt(KEY_HOURS, hours).apply();
        scheduleNext(context);
    }

    public static void cancelReminder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_ENABLED, false).apply();

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(buildPendingIntent(context));
        }
    }

    public static boolean isReminderEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static int getReminderHours(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_HOURS, 3);
    }

    /** Schedules the next single alarm; the receiver reschedules the one after it. */
    public static void scheduleNext(Context context) {
        if (!isReminderEnabled(context)) {
            return;
        }

        int hours = getReminderHours(context);
        long triggerAt = System.currentTimeMillis() + hours * 60L * 60L * 1000L;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, buildPendingIntent(context));
        }
    }

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        return PendingIntent.getBroadcast(
                context, REMINDER_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}