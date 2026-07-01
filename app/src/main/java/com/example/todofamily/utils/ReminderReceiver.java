package com.example.todofamily.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("taskTitle");
        String message = "Пора выполнить задачу!";
        
        NotificationHelper.showNotification(context, title, message);
    }
}