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

import java.util.HashMap;
import java.util.Map;

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

                // Логика проверки (Approval)
                if (model.getStatus() == 1) { // WAITING_APPROVAL
                    holder.binding.approvalLayout.setVisibility(View.VISIBLE);
                    holder.binding.approveBtn.setOnClickListener(v -> approveTask(model));
                    holder.binding.rejectBtn.setOnClickListener(v -> showRejectDialog(model));
                } else {
                    holder.binding.approvalLayout.setVisibility(View.GONE);
                }
                
                if (model.getStatus() == 2) {
                    holder.binding.rejectionCommentTv.setVisibility(View.VISIBLE);
                    holder.binding.rejectionCommentTv.setText("Вы отклонили: " + model.getRejectionComment());
                } else {
                    holder.binding.rejectionCommentTv.setVisibility(View.GONE);
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

    private void approveTask(Task task) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", 3); // COMPLETED
        updates.put("completed", true);
        updates.put("rejectionComment", null);

        memberTasksRef.child(task.getId()).updateChildren(updates).addOnCompleteListener(t -> {
            if (t.isSuccessful()) {
                // Обновляем в sent_tasks автора
                FirebaseDatabase.getInstance().getReference()
                        .child("sent_tasks")
                        .child(task.getAssignedBy())
                        .child(task.getId())
                        .updateChildren(updates);
                
                // Уведомление исполнителю
                sendNotification(task.getAssignedTo(), 
                    "Задание принято", 
                    "Ваш отчет по задаче '" + task.getTitle() + "' принят!", 
                    "TASK_APPROVED");
            }
        });
    }

    private void sendNotification(String toUid, String title, String message, String type) {
        if (toUid == null) return;
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference().child("notifications").child(toUid);
        String id = ref.push().getKey();
        Notification notification = new Notification(id, title, message, System.currentTimeMillis(), type);
        if (id != null) ref.child(id).setValue(notification);
    }

    private void showRejectDialog(Task task) {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        android.widget.EditText et = view.findViewById(R.id.dialog_et);
        et.setHint("Причина отклонения");

        new android.app.AlertDialog.Builder(this)
                .setTitle("Отклонить задачу")
                .setView(view)
                .setPositiveButton("Отправить", (dialog, which) -> {
                    String comment = et.getText().toString().trim();
                    rejectTask(task, comment);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void rejectTask(Task task, String comment) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", 2); // REJECTED
        updates.put("completed", false);
        updates.put("rejectionComment", comment);

        memberTasksRef.child(task.getId()).updateChildren(updates).addOnCompleteListener(t -> {
            if (t.isSuccessful()) {
                FirebaseDatabase.getInstance().getReference()
                        .child("sent_tasks")
                        .child(task.getAssignedBy())
                        .child(task.getId())
                        .updateChildren(updates);
                
                // Уведомление исполнителю
                sendNotification(task.getAssignedTo(), 
                    "Задача отклонена", 
                    "Ваше задание '" + task.getTitle() + "' отклонено: " + comment, 
                    "TASK_REJECTED");
            }
        });
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