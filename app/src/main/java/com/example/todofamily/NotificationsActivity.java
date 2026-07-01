package com.example.todofamily;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.todofamily.databinding.ActivityNotificationsBinding;
import com.example.todofamily.databinding.ItemNotificationBinding;
import com.example.todofamily.utils.WrapContentLinearLayoutManager;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationsActivity extends AppCompatActivity {

    private ActivityNotificationsBinding binding;
    private FirebaseRecyclerAdapter<Notification, NotificationViewHolder> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Уведомления");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.notificationsRv.setLayoutManager(new WrapContentLinearLayoutManager(this));
        setupAdapter();
    }

    private void setupAdapter() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference().child("notifications").child(uid);
        Query query = ref.orderByChild("timestamp");

        FirebaseRecyclerOptions<Notification> options = new FirebaseRecyclerOptions.Builder<Notification>()
                .setQuery(query, Notification.class)
                .build();

        adapter = new FirebaseRecyclerAdapter<Notification, NotificationViewHolder>(options) {
            @Override
            protected void onBindViewHolder(@NonNull NotificationViewHolder holder, int position, @NonNull Notification model) {
                holder.binding.notificationTitleTv.setText(model.getTitle());
                holder.binding.notificationMessageTv.setText(model.getMessage());
                
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
                holder.binding.notificationTimeTv.setText(sdf.format(new Date(model.getTimestamp())));
            }

            @NonNull
            @Override
            public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ItemNotificationBinding itemBinding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new NotificationViewHolder(itemBinding);
            }

            @Override
            public void onDataChanged() {
                super.onDataChanged();
                if (getItemCount() == 0) {
                    binding.emptyNotificationsTv.setVisibility(View.VISIBLE);
                } else {
                    binding.emptyNotificationsTv.setVisibility(View.GONE);
                }
            }
        };

        binding.notificationsRv.setAdapter(adapter);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (adapter != null) adapter.startListening();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adapter != null) adapter.stopListening();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public static class NotificationViewHolder extends RecyclerView.ViewHolder {
        ItemNotificationBinding binding;
        public NotificationViewHolder(ItemNotificationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}