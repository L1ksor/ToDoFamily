package com.example.todofamily;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.todofamily.databinding.ActivityMemberTasksBinding;
import com.example.todofamily.databinding.ItemTaskBinding;
import com.example.todofamily.utils.WrapContentLinearLayoutManager;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

public class MemberTasksActivity extends AppCompatActivity {

    private ActivityMemberTasksBinding binding;
    private DatabaseReference memberTasksRef;
    private FirebaseRecyclerAdapter<Task, MainActivity.TaskViewHolder> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMemberTasksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String memberUid = getIntent().getStringExtra("memberUid");
        String memberName = getIntent().getStringExtra("memberName");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Задачи: " + memberName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        memberTasksRef = FirebaseDatabase.getInstance().getReference()
                .child("spaces")
                .child(memberUid)
                .child("tasks");

        binding.memberTasksRv.setLayoutManager(new WrapContentLinearLayoutManager(this));
        setupAdapter();
    }

    private void setupAdapter() {
        // Показываем только те задачи участника, которые выдал текущий пользователь
        String myUid = FirebaseAuth.getInstance().getUid();
        Query query = memberTasksRef.orderByChild("assignedBy").equalTo(myUid);

        FirebaseRecyclerOptions<Task> options = new FirebaseRecyclerOptions.Builder<Task>()
                .setQuery(query, Task.class)
                .build();

        adapter = new FirebaseRecyclerAdapter<Task, MainActivity.TaskViewHolder>(options) {
            @Override
            protected void onBindViewHolder(@NonNull MainActivity.TaskViewHolder holder, int position, @NonNull Task model) {
                holder.binding.taskTitleTv.setText(model.getTitle());
                holder.binding.taskDescTv.setText(model.getDescription());
                holder.binding.taskCheckbox.setChecked(model.isCompleted());
                holder.binding.taskCheckbox.setEnabled(false); // Только просмотр

                if (model.getImageUrl() != null && !model.getImageUrl().isEmpty()) {
                    holder.binding.taskPhotoIv.setVisibility(View.VISIBLE);
                    Glide.with(MemberTasksActivity.this).load(model.getImageUrl()).into(holder.binding.taskPhotoIv);
                } else {
                    holder.binding.taskPhotoIv.setVisibility(View.GONE);
                }

                if (model.getAssignedBy() != null) {
                    holder.binding.taskAssignerTv.setVisibility(View.VISIBLE);
                    holder.binding.taskAssignerTv.setText("От: " + model.getAssignedByName());
                } else {
                    holder.binding.taskAssignerTv.setVisibility(View.GONE);
                }

                if (model.isPhotoRequired()) {
                    holder.binding.photoRequiredIc.setVisibility(View.VISIBLE);
                } else {
                    holder.binding.photoRequiredIc.setVisibility(View.GONE);
                }
            }

            @NonNull
            @Override
            public MainActivity.TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ItemTaskBinding itemBinding = ItemTaskBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new MainActivity.TaskViewHolder(itemBinding);
            }
        };

        binding.memberTasksRv.setAdapter(adapter);
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