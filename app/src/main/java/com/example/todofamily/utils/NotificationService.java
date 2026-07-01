package com.example.todofamily.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class NotificationService extends Service {

    private static final String CHANNEL_ID = "foreground_service_channel";
    private DatabaseReference notificationsRef;
    private ValueEventListener listener;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ToDoFamily активен")
                .setContentText("Служба уведомлений работает в фоне")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);

        setupFirebaseListener();

        return START_STICKY;
    }

    private void setupFirebaseListener() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            stopSelf();
            return;
        }

        notificationsRef = FirebaseDatabase.getInstance().getReference().child("notifications").child(uid);
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot notifSnap : snapshot.getChildren()) {
                    Boolean notified = notifSnap.child("notifiedLocally").getValue(Boolean.class);
                    if (notified == null || !notified) {
                        String title = notifSnap.child("title").getValue(String.class);
                        String message = notifSnap.child("message").getValue(String.class);
                        
                        NotificationHelper.showNotification(getApplicationContext(), title, message);
                        
                        // Помечаем как показанное локально, чтобы не дублировать
                        notifSnap.getRef().child("notifiedLocally").setValue(true);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        notificationsRef.addValueEventListener(listener);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "ToDoFamily Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (notificationsRef != null && listener != null) {
            notificationsRef.removeEventListener(listener);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}