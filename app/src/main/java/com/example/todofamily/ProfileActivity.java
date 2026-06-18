package com.example.todofamily;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.todofamily.databinding.ActivityProfileBinding;
import com.example.todofamily.databinding.ItemTaskBinding;
import com.example.todofamily.utils.WrapContentLinearLayoutManager;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private DatabaseReference spaceRef;
    private FirebaseRecyclerAdapter<Task, MainActivity.TaskViewHolder> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Профиль");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        spaceRef = FirebaseDatabase.getInstance().getReference()
                .child("spaces")
                .child(currentUserId)
                .child("tasks");

        loadUserInfo();
        setupHistoryAdapter();

        binding.logoutBtn.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void loadUserInfo() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            binding.emailTv.setText(user.getEmail());

            FirebaseDatabase.getInstance().getReference()
                    .child("Users")
                    .child(user.getUid())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String username = snapshot.child("username").getValue(String.class);
                                binding.usernameTv.setText(username);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(ProfileActivity.this, "Ошибка загрузки данных", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void setupHistoryAdapter() {
        Query query = spaceRef.orderByChild("completed").equalTo(true);

        FirebaseRecyclerOptions<Task> options = new FirebaseRecyclerOptions.Builder<Task>()
                .setQuery(query, Task.class)
                .build();

        adapter = new FirebaseRecyclerAdapter<Task, MainActivity.TaskViewHolder>(options) {
            @Override
            protected void onBindViewHolder(@NonNull MainActivity.TaskViewHolder holder, int position, @NonNull Task model) {
                holder.binding.taskTitleTv.setText(model.getTitle());
                holder.binding.taskDescTv.setText(model.getDescription());
                holder.binding.taskCheckbox.setChecked(model.isCompleted());

                if (model.getDueDate() > 0) {
                    holder.binding.taskDateTv.setVisibility(View.VISIBLE);
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                    holder.binding.taskDateTv.setText(sdf.format(new Date(model.getDueDate())));
                    holder.binding.taskDateTv.setTextColor(ContextCompat.getColor(ProfileActivity.this, R.color.gray));
                } else {
                    holder.binding.taskDateTv.setVisibility(View.GONE);
                }

                if (model.getImageUrl() != null && !model.getImageUrl().isEmpty()) {
                    holder.binding.taskPhotoIv.setVisibility(View.VISIBLE);
                    Glide.with(ProfileActivity.this).load(model.getImageUrl()).into(holder.binding.taskPhotoIv);
                } else {
                    holder.binding.taskPhotoIv.setVisibility(View.GONE);
                }

                if (model.getAssignedBy() != null && !model.getAssignedBy().equals(FirebaseAuth.getInstance().getUid())) {
                    holder.binding.taskAssignerTv.setVisibility(View.VISIBLE);
                    holder.binding.taskAssignerTv.setText("От: " + model.getAssignedByName());
                } else {
                    holder.binding.taskAssignerTv.setVisibility(View.GONE);
                }

                holder.binding.taskCheckbox.setOnClickListener(v -> {
                    spaceRef.child(model.getId()).child("completed").setValue(holder.binding.taskCheckbox.isChecked());
                });

                holder.itemView.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(ProfileActivity.this)
                            .setTitle("Удалить из истории?")
                            .setPositiveButton("Да", (dialog, which) -> spaceRef.child(model.getId()).removeValue())
                            .setNegativeButton("Нет", null)
                            .show();
                    return true;
                });
            }

            @NonNull
            @Override
            public MainActivity.TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ItemTaskBinding itemBinding = ItemTaskBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new MainActivity.TaskViewHolder(itemBinding);
            }
        };

        binding.historyRv.setLayoutManager(new WrapContentLinearLayoutManager(this));
        binding.historyRv.setAdapter(adapter);
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
}