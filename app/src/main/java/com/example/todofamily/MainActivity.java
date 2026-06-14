package com.example.todofamily;

import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.todofamily.databinding.ActivityMainBinding;
import com.example.todofamily.databinding.ItemTaskBinding;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.example.todofamily.utils.WrapContentLinearLayoutManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private DatabaseReference spaceRef;
    private FirebaseRecyclerAdapter<Task, TaskViewHolder> adapter;
    private String currentSpaceId; 
    private long selectedDueDate = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        currentSpaceId = currentUserId; 

        spaceRef = FirebaseDatabase.getInstance().getReference()
                .child("spaces")
                .child(currentSpaceId)
                .child("tasks");

        binding.tasksRv.setLayoutManager(new WrapContentLinearLayoutManager(this));

        setupAdapter();

        binding.addTaskFab.setOnClickListener(v -> showAddTaskDialog());

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_tasks) {
                return true;
            } else if (id == R.id.nav_family) {
                Toast.makeText(this, "Раздел 'Семья' скоро появится", Toast.LENGTH_SHORT).show();
                return false;
            } else if (id == R.id.nav_notifications) {
                Toast.makeText(this, "Уведомления скоро появятся", Toast.LENGTH_SHORT).show();
                return false;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
                return false;
            }
            return false;
        });
    }

    private void setupAdapter() {
        Query query = spaceRef.orderByChild("completed").equalTo(false);

        FirebaseRecyclerOptions<Task> options = new FirebaseRecyclerOptions.Builder<Task>()
                .setQuery(query, Task.class)
                .build();

        adapter = new FirebaseRecyclerAdapter<Task, TaskViewHolder>(options) {
            @Override
            protected void onBindViewHolder(@NonNull TaskViewHolder holder, int position, @NonNull Task model) {
                holder.binding.taskTitleTv.setText(model.getTitle());
                holder.binding.taskDescTv.setText(model.getDescription());
                holder.binding.taskCheckbox.setChecked(model.isCompleted());

                if (model.getDueDate() > 0) {
                    updateDateUI(holder, model.getDueDate());
                } else {
                    holder.binding.taskDateTv.setVisibility(View.GONE);
                }

                holder.binding.taskCheckbox.setOnClickListener(v -> {
                    spaceRef.child(model.getId()).child("completed").setValue(holder.binding.taskCheckbox.isChecked());
                });

                holder.itemView.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Удалить задачу?")
                            .setPositiveButton("Да", (dialog, which) -> spaceRef.child(model.getId()).removeValue())
                            .setNegativeButton("Нет", null)
                            .show();
                    return true;
                });
            }

            @NonNull
            @Override
            public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ItemTaskBinding itemBinding = ItemTaskBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new TaskViewHolder(itemBinding);
            }
        };

        binding.tasksRv.setAdapter(adapter);
    }

    private void updateDateUI(TaskViewHolder holder, long dueDate) {
        holder.binding.taskDateTv.setVisibility(View.VISIBLE);
        
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        long todayStart = now.getTimeInMillis();

        Calendar taskDate = Calendar.getInstance();
        taskDate.setTimeInMillis(dueDate);
        taskDate.set(Calendar.HOUR_OF_DAY, 0);
        taskDate.set(Calendar.MINUTE, 0);
        taskDate.set(Calendar.SECOND, 0);
        taskDate.set(Calendar.MILLISECOND, 0);
        long taskDayStart = taskDate.getTimeInMillis();

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        String dateText = sdf.format(new Date(dueDate));

        if (taskDayStart < todayStart) {
            holder.binding.taskDateTv.setText(dateText);
            holder.binding.taskDateTv.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.red));
        } else if (taskDayStart == todayStart) {
            holder.binding.taskDateTv.setText("Сегодня");
            holder.binding.taskDateTv.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.yellow));
        } else {
            holder.binding.taskDateTv.setText(dateText);
            holder.binding.taskDateTv.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.green));
        }
    }

    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Новая задача");

        View view = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        EditText titleEt = view.findViewById(R.id.task_title_et);
        EditText descEt = view.findViewById(R.id.task_desc_et);
        Button dateBtn = view.findViewById(R.id.set_date_btn);

        selectedDueDate = 0;

        dateBtn.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view1, year, month, dayOfMonth) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, dayOfMonth);
                selectedDueDate = selected.getTimeInMillis();
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                dateBtn.setText(sdf.format(selected.getTime()));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        builder.setView(view);
        builder.setPositiveButton("Добавить", (dialog, which) -> {
            String title = titleEt.getText().toString();
            String desc = descEt.getText().toString();

            if (!title.isEmpty()) {
                String id = spaceRef.push().getKey();
                Task newTask = new Task(id, title, desc, false, selectedDueDate);
                spaceRef.child(id).setValue(newTask);
            } else {
                Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // При возврате на главный экран убеждаемся, что выбрана иконка задач
        binding.bottomNavigation.setSelectedItemId(R.id.nav_tasks);
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

    public static class TaskViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        ItemTaskBinding binding;
        public TaskViewHolder(ItemTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}