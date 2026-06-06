package com.example.bcalm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationUtil.showNotification(
                context,
                NotificationUtil.REMINDER_NOTIFICATION_ID,
                "Time to pump 🍼",
                "Tap to open bcalm and log your session."
        );

        // Reschedule so the reminder keeps repeating.
        NotificationUtil.scheduleNext(context);
    }
}