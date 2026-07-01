package com.example.todofamily;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.todofamily.databinding.ActivityMemberTasksBinding;
import com.example.todofamily.databinding.ItemTaskBinding;
import com.example.todofamily.utils.NotificationHelper;
import com.example.todofamily.utils.WrapContentLinearLayoutManager;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MemberTasksActivity extends AppCompatActivity {

    private ActivityMemberTasksBinding binding;
    private DatabaseReference memberTasksRef;
    private FirebaseRecyclerAdapter<Task, MainActivity.TaskViewHolder> adapter;
    private String memberUid;
    private String memberName;
    private String currentUserId;
    private String currentUserName;
    private long selectedDueDate = 0;
    private List<Integer> selectedRepeatDays = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMemberTasksBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        memberUid = getIntent().getStringExtra("memberUid");
        memberName = getIntent().getStringExtra("memberName");
        currentUserId = FirebaseAuth.getInstance().getUid();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Задачи: " + memberName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        memberTasksRef = FirebaseDatabase.getInstance().getReference()
                .child("spaces")
                .child(memberUid)
                .child("tasks");

        loadCurrentUserName();

        binding.memberTasksRv.setLayoutManager(new WrapContentLinearLayoutManager(this));
        setupAdapter();

        binding.addTaskToMemberFab.setOnClickListener(v -> showAddTaskDialog());
    }

    private void loadCurrentUserName() {
        FirebaseDatabase.getInstance().getReference().child("Users").child(currentUserId)
                .child("username").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            currentUserName = snapshot.getValue(String.class);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void setupAdapter() {
        Query query = memberTasksRef.orderByChild("assignedBy").equalTo(currentUserId);

        FirebaseRecyclerOptions<Task> options = new FirebaseRecyclerOptions.Builder<Task>()
                .setQuery(query, Task.class)
                .build();

        adapter = new FirebaseRecyclerAdapter<Task, MainActivity.TaskViewHolder>(options) {
            @Override
            protected void onBindViewHolder(@NonNull MainActivity.TaskViewHolder holder, int position, @NonNull Task model) {
                holder.binding.taskTitleTv.setText(model.getTitle());
                holder.binding.taskDescTv.setText(model.getDescription());
                holder.binding.taskCheckbox.setChecked(model.isCompleted());
                holder.binding.taskCheckbox.setEnabled(false);

                if (model.getDueDate() > 0) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
                    holder.binding.taskDateTv.setText(sdf.format(new Date(model.getDueDate())));
                }

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

                if (model.getStatus() == 1) {
                    holder.binding.taskActionBtn.setVisibility(View.VISIBLE);
                    holder.binding.taskActionBtn.setOnClickListener(v -> showTaskActionBottomSheet(model));
                } else {
                    holder.binding.taskActionBtn.setVisibility(View.GONE);
                }
                
                if (model.getStatus() == 2 || model.getStatus() == 4) {
                    holder.binding.rejectionCommentTv.setVisibility(View.VISIBLE);
                    String prefix = model.getStatus() == 4 ? "Доработка: " : "Отклонено: ";
                    holder.binding.rejectionCommentTv.setText(prefix + model.getRejectionComment());
                    int color = model.getStatus() == 4 ? 
                            ContextCompat.getColor(MemberTasksActivity.this, R.color.yellow) : 
                            ContextCompat.getColor(MemberTasksActivity.this, R.color.red);
                    holder.binding.rejectionCommentTv.setTextColor(color);
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

            @Override
            public void onDataChanged() {
                super.onDataChanged();
                if (getItemCount() == 0) {
                    binding.emptyMemberTasksTv.setVisibility(View.VISIBLE);
                } else {
                    binding.emptyMemberTasksTv.setVisibility(View.GONE);
                }
            }
        };

        binding.memberTasksRv.setAdapter(adapter);
    }

    private void showTaskActionBottomSheet(Task task) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_task_actions, null);
        
        view.findViewById(R.id.bs_approve_tv).setOnClickListener(v -> {
            approveTask(task);
            dialog.dismiss();
        });
        
        view.findViewById(R.id.bs_rework_tv).setOnClickListener(v -> {
            showReworkDialog(task);
            dialog.dismiss();
        });
        
        view.findViewById(R.id.bs_reject_tv).setOnClickListener(v -> {
            showRejectDialog(task);
            dialog.dismiss();
        });
        
        dialog.setContentView(view);
        dialog.show();
    }

    private void approveTask(Task task) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", 3);
        updates.put("completed", true);
        updates.put("rejectionComment", null);

        memberTasksRef.child(task.getId()).updateChildren(updates).addOnCompleteListener(t -> {
            if (t.isSuccessful()) {
                if (task.getRepeatDays() != null && !task.getRepeatDays().isEmpty()) {
                    handleRecurringTask(task);
                }
                FirebaseDatabase.getInstance().getReference()
                        .child("sent_tasks")
                        .child(task.getAssignedBy())
                        .child(task.getId())
                        .updateChildren(updates);
                
                sendNotification(task.getAssignedTo(), "Задание принято", 
                    "Ваш отчет по задаче '" + task.getTitle() + "' принят!", "TASK_APPROVED");
            }
        });
    }

    private void showReworkDialog(Task task) {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        com.google.android.material.textfield.TextInputLayout til = view.findViewById(R.id.dialog_til);
        EditText et = view.findViewById(R.id.dialog_et);
        til.setHint("Что нужно исправить?");

        new AlertDialog.Builder(this)
                .setTitle("На доработку")
                .setView(view)
                .setPositiveButton("Отправить", (dialog, which) -> {
                    String comment = et.getText().toString().trim();
                    reworkTask(task, comment);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void reworkTask(Task task, String comment) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", 4);
        updates.put("completed", false);
        updates.put("rejectionComment", comment);

        memberTasksRef.child(task.getId()).updateChildren(updates).addOnCompleteListener(t -> {
            if (t.isSuccessful()) {
                FirebaseDatabase.getInstance().getReference()
                        .child("sent_tasks")
                        .child(task.getAssignedBy())
                        .child(task.getId())
                        .updateChildren(updates);
                
                sendNotification(task.getAssignedTo(), "Задание на доработке", 
                    "Нужно исправить задачу '" + task.getTitle() + "': " + comment, "TASK_REWORK");
            }
        });
    }

    private void handleRecurringTask(Task completedTask) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(completedTask.getDueDate());
        List<Integer> days = completedTask.getRepeatDays();
        if (days == null || days.isEmpty()) return;

        int currentDay = cal.get(Calendar.DAY_OF_WEEK);
        int daysToAdd = -1;
        for (int i = 1; i <= 7; i++) {
            int nextDay = ((currentDay + i - 1) % 7) + 1;
            if (days.contains(nextDay)) {
                daysToAdd = i;
                break;
            }
        }
        if (daysToAdd == -1) return;
        cal.add(Calendar.DAY_OF_YEAR, daysToAdd);

        DatabaseReference targetRef = FirebaseDatabase.getInstance().getReference()
                .child("spaces")
                .child(completedTask.getAssignedTo())
                .child("tasks");

        String newId = targetRef.push().getKey();
        if (newId == null) return;
        Task nextTask = new Task(newId, completedTask.getTitle(), completedTask.getDescription(),
                false, cal.getTimeInMillis(), completedTask.getAssignedBy(),
                completedTask.getAssignedTo(), completedTask.getAssignedByName());
        nextTask.setPhotoRequired(completedTask.isPhotoRequired());
        nextTask.setRepeatDays(days);
        nextTask.setStatus(0);

        targetRef.child(newId).setValue(nextTask);
        FirebaseDatabase.getInstance().getReference().child("sent_tasks")
                .child(completedTask.getAssignedBy()).child(newId).setValue(nextTask);
    }

    private void showRejectDialog(Task task) {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        com.google.android.material.textfield.TextInputLayout til = view.findViewById(R.id.dialog_til);
        EditText et = view.findViewById(R.id.dialog_et);
        til.setHint("Причина отклонения");

        new AlertDialog.Builder(this)
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
        updates.put("status", 2);
        updates.put("completed", false);
        updates.put("rejectionComment", comment);

        memberTasksRef.child(task.getId()).updateChildren(updates).addOnCompleteListener(t -> {
            if (t.isSuccessful()) {
                FirebaseDatabase.getInstance().getReference()
                        .child("sent_tasks")
                        .child(task.getAssignedBy())
                        .child(task.getId())
                        .updateChildren(updates);
                
                sendNotification(task.getAssignedTo(), "Задача отклонена", 
                    "Ваше задание '" + task.getTitle() + "' отклонено: " + comment, "TASK_REJECTED");
            }
        });
    }

    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выдать задачу: " + memberName);

        View view = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        EditText titleEt = view.findViewById(R.id.task_title_et);
        EditText descEt = view.findViewById(R.id.task_desc_et);
        Button dateBtn = view.findViewById(R.id.set_date_btn);
        Button timeBtn = view.findViewById(R.id.set_time_btn);
        Button repeatBtn = view.findViewById(R.id.set_repeat_btn);
        com.google.android.material.materialswitch.MaterialSwitch photoRequiredSwitch = view.findViewById(R.id.photo_required_switch);

        view.findViewById(R.id.assignee_spinner).setVisibility(View.GONE);
        View label = view.findViewById(R.id.assignee_label);
        if (label != null) label.setVisibility(View.GONE);

        final Calendar selectedCalendar = Calendar.getInstance();
        selectedDueDate = 0;
        selectedRepeatDays = new ArrayList<>();

        repeatBtn.setOnClickListener(v -> showRepeatDaysBottomSheet(repeatBtn));

        dateBtn.setOnClickListener(v -> {
            new DatePickerDialog(this, (view1, year, month, dayOfMonth) -> {
                selectedCalendar.set(Calendar.YEAR, year);
                selectedCalendar.set(Calendar.MONTH, month);
                selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                selectedDueDate = selectedCalendar.getTimeInMillis();
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                dateBtn.setText(sdf.format(selectedCalendar.getTime()));
            }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), selectedCalendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        timeBtn.setOnClickListener(v -> {
            new TimePickerDialog(this, (view1, hourOfDay, minute) -> {
                selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                selectedCalendar.set(Calendar.MINUTE, minute);
                selectedCalendar.set(Calendar.SECOND, 0);
                selectedCalendar.set(Calendar.MILLISECOND, 0);
                selectedDueDate = selectedCalendar.getTimeInMillis();
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                timeBtn.setText(sdf.format(selectedCalendar.getTime()));
            }, selectedCalendar.get(Calendar.HOUR_OF_DAY), selectedCalendar.get(Calendar.MINUTE), true).show();
        });

        builder.setView(view);
        builder.setPositiveButton("Выдать", (dialog, which) -> {
            String title = titleEt.getText().toString();
            String desc = descEt.getText().toString();
            boolean photoRequired = photoRequiredSwitch.isChecked();

            if (!title.isEmpty()) {
                String id = memberTasksRef.push().getKey();
                if (id == null) return;
                
                String authorName = currentUserName != null ? currentUserName : "Участник";
                
                Task newTask = new Task(id, title, desc, false, selectedDueDate,
                        currentUserId, memberUid, authorName);
                newTask.setPhotoRequired(photoRequired);
                newTask.setRepeatDays(selectedRepeatDays);
                newTask.setReminderSet(selectedDueDate > 0);
                newTask.setStatus(0);

                memberTasksRef.child(id).setValue(newTask);
                
                // Напоминание (локальное на устройстве создателя) - теперь автоматически
                if (selectedDueDate > System.currentTimeMillis()) {
                    NotificationHelper.scheduleReminder(MemberTasksActivity.this, newTask);
                }

                FirebaseDatabase.getInstance().getReference().child("sent_tasks")
                        .child(currentUserId).child(id).setValue(newTask);

                sendNotification(memberUid, "Новое задание", 
                    currentUserName + " выдал вам задачу: " + title, "NEW_TASK");

                showCustomToast("Задание выдано");
            } else {
                Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showRepeatDaysBottomSheet(Button repeatBtn) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_repeat_days, null);

        android.widget.CheckBox[] cbs = {
                view.findViewById(R.id.cb_mon), view.findViewById(R.id.cb_tue),
                view.findViewById(R.id.cb_wed), view.findViewById(R.id.cb_thu),
                view.findViewById(R.id.cb_fri), view.findViewById(R.id.cb_sat),
                view.findViewById(R.id.cb_sun)
        };

        int[] calendarDays = {
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        };

        for (int i = 0; i < cbs.length; i++) {
            if (selectedRepeatDays.contains(calendarDays[i])) {
                cbs[i].setChecked(true);
            }
        }

        view.findViewById(R.id.btn_done_repeat).setOnClickListener(v -> {
            selectedRepeatDays.clear();
            for (int i = 0; i < cbs.length; i++) {
                if (cbs[i].isChecked()) {
                    selectedRepeatDays.add(calendarDays[i]);
                }
            }
            updateRepeatButtonText(repeatBtn);
            dialog.dismiss();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void updateRepeatButtonText(Button repeatBtn) {
        if (selectedRepeatDays.isEmpty()) {
            repeatBtn.setText("Настроить повтор: Нет");
        } else {
            String[] shortDays = {"Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"};
            StringBuilder sb = new StringBuilder("Повтор: ");
            for (int i = 0; i < selectedRepeatDays.size(); i++) {
                sb.append(shortDays[selectedRepeatDays.get(i) - 1]);
                if (i < selectedRepeatDays.size() - 1) sb.append(", ");
            }
            repeatBtn.setText(sb.toString());
        }
    }

    private void sendNotification(String toUid, String title, String message, String type) {
        if (toUid == null) return;
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference().child("notifications").child(toUid);
        String id = ref.push().getKey();
        Notification notification = new Notification(id, title, message, System.currentTimeMillis(), type);
        if (id != null) ref.child(id).setValue(notification);
    }

    private void showCustomToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL, 0, 250);
        toast.show();
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