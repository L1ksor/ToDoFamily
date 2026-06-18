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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private DatabaseReference spaceRef;
    private FirebaseRecyclerAdapter<Task, TaskViewHolder> adapter;
    private String currentSpaceId;
    private String currentUserName;
    private String currentFamilyId;
    private long selectedDueDate = 0;

    private List<Member> familyMembers = new ArrayList<>();

    private Uri photoUri;
    private String currentProcessingTaskId;
    private String currentProcessingTaskTargetUid;
    private AlertDialog photoDialog;

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    onPhotoSelected(photoUri);
                }
            });

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    onPhotoSelected(result.getData().getData());
                }
            });

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

        initCloudinary();

        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        currentSpaceId = currentUserId;

        spaceRef = FirebaseDatabase.getInstance().getReference()
                .child("spaces")
                .child(currentSpaceId)
                .child("tasks");

        loadCurrentUserData();

        binding.tasksRv.setLayoutManager(new WrapContentLinearLayoutManager(this));

        setupAdapter();

        binding.addTaskFab.setOnClickListener(v -> showAddTaskDialog());

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_tasks) {
                return true;
            } else if (id == R.id.nav_family) {
                startActivity(new Intent(MainActivity.this, FamilyActivity.class));
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

    private void loadCurrentUserData() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseDatabase.getInstance().getReference().child("Users").child(uid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            currentUserName = snapshot.child("username").getValue(String.class);
                            currentFamilyId = snapshot.child("familyId").getValue(String.class);
                            loadFamilyMembers();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void loadFamilyMembers() {
        if (currentFamilyId == null) return;

        FirebaseDatabase.getInstance().getReference().child("Families").child(currentFamilyId).child("members")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        familyMembers.clear();
                        for (DataSnapshot memberSnap : snapshot.getChildren()) {
                            String uid = memberSnap.getKey();
                            fetchMemberDetails(uid);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void fetchMemberDetails(String uid) {
        FirebaseDatabase.getInstance().getReference().child("Users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Member member = new Member(
                                    uid,
                                    snapshot.child("username").getValue(String.class),
                                    snapshot.child("email").getValue(String.class)
                            );

                            boolean exists = false;
                            for (Member m : familyMembers) {
                                if (m.getUid().equals(uid)) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) familyMembers.add(member);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void initCloudinary() {
        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", "dk0bba3k9");
        config.put("api_key", "331839799785651");
        config.put("api_secret", "Hexwy5GxfSRkWnX9vEGJPu19uOo");
        try {
            MediaManager.init(this, config);
        } catch (IllegalStateException e) {
            // Уже инициализировано
        }
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

                if (model.getImageUrl() != null && !model.getImageUrl().isEmpty()) {
                    holder.binding.taskPhotoIv.setVisibility(View.VISIBLE);
                    Glide.with(MainActivity.this).load(model.getImageUrl()).into(holder.binding.taskPhotoIv);
                } else {
                    holder.binding.taskPhotoIv.setVisibility(View.GONE);
                }

                if (model.getAssignedBy() != null && !model.getAssignedBy().equals(FirebaseAuth.getInstance().getUid())) {
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

                holder.binding.taskCheckbox.setOnClickListener(v -> {
                    if (holder.binding.taskCheckbox.isChecked()) {
                        if (model.isPhotoRequired()) {
                            // Если фото ОБЯЗАТЕЛЬНО — открываем диалог подтверждения
                            showPhotoReportDialog(model.getId(), currentSpaceId, true);
                            // Временно возвращаем чекбокс в false, пока фото не загрузится
                            holder.binding.taskCheckbox.setChecked(false);
                        } else {
                            // Если фото НЕ ТРЕБУЕТСЯ — мгновенно закрываем задачу
                            spaceRef.child(model.getId()).child("completed").setValue(true);
                            Toast.makeText(MainActivity.this, "Задача выполнена!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        spaceRef.child(model.getId()).child("completed").setValue(false);
                    }
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
        Spinner assigneeSpinner = view.findViewById(R.id.assignee_spinner);
        com.google.android.material.materialswitch.MaterialSwitch photoRequiredSwitch = view.findViewById(R.id.photo_required_switch);

        selectedDueDate = 0;

        List<String> memberNames = new ArrayList<>();
        memberNames.add("Себе");
        for (Member m : familyMembers) {
            if (!m.getUid().equals(FirebaseAuth.getInstance().getUid())) {
                memberNames.add(m.getUsername());
            }
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, memberNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        assigneeSpinner.setAdapter(spinnerAdapter);

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
            int selectedIndex = assigneeSpinner.getSelectedItemPosition();
            boolean photoRequired = photoRequiredSwitch.isChecked();

            if (!title.isEmpty()) {
                String targetUid;
                if (selectedIndex == 0) {
                    targetUid = FirebaseAuth.getInstance().getUid();
                } else {
                    String selectedName = memberNames.get(selectedIndex);
                    targetUid = null;
                    for (Member m : familyMembers) {
                        if (m.getUsername().equals(selectedName)) {
                            targetUid = m.getUid();
                            break;
                        }
                    }
                }

                if (targetUid != null) {
                    DatabaseReference targetRef = FirebaseDatabase.getInstance().getReference()
                            .child("spaces")
                            .child(targetUid)
                            .child("tasks");

                    String id = targetRef.push().getKey();
                    Task newTask = new Task(id, title, desc, false, selectedDueDate,
                            FirebaseAuth.getInstance().getUid(), targetUid, currentUserName);
                    newTask.setPhotoRequired(photoRequired);
                    targetRef.child(id).setValue(newTask);

                    if (!targetUid.equals(FirebaseAuth.getInstance().getUid())) {
                        FirebaseDatabase.getInstance().getReference()
                                .child("sent_tasks")
                                .child(FirebaseAuth.getInstance().getUid())
                                .child(id)
                                .setValue(newTask);
                        Toast.makeText(this, "Задача назначена участнику " + memberNames.get(selectedIndex), Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showPhotoReportDialog(String taskId, String targetUid, boolean mandatory) {
        currentProcessingTaskId = taskId;
        currentProcessingTaskTargetUid = targetUid;

        View view = getLayoutInflater().inflate(R.layout.dialog_attach_photo, null);
        ImageView previewIv = view.findViewById(R.id.preview_iv);
        Button cameraBtn = view.findViewById(R.id.camera_btn);
        Button galleryBtn = view.findViewById(R.id.gallery_btn);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Завершить", null)
                .setNegativeButton("Отмена", null)
                .setCancelable(true)
                .create();

        dialog.setOnShowListener(d -> {
            Button positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveBtn.setEnabled(false);

            positiveBtn.setOnClickListener(v -> {
                if (photoUri != null) {
                    uploadPhotoToCloudinary(photoUri);
                    dialog.dismiss();
                }
            });
        });

        cameraBtn.setOnClickListener(v -> launchCamera());
        galleryBtn.setOnClickListener(v -> launchGallery());

        photoDialog = dialog;
        dialog.show();
    }

    private void launchCamera() {
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            Toast.makeText(this, "Ошибка создания файла", Toast.LENGTH_SHORT).show();
        }
        if (photoFile != null) {
            photoUri = FileProvider.getUriForFile(this, "com.example.todofamily.fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            cameraLauncher.launch(intent);
        }
    }

    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(null);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void onPhotoSelected(Uri uri) {
        photoUri = uri;
        if (photoDialog != null) {
            ImageView previewIv = photoDialog.findViewById(R.id.preview_iv);
            if (previewIv != null) {
                previewIv.setImageURI(uri);
            }
            photoDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
        }
    }

    private void uploadPhotoToCloudinary(Uri uri) {
        Toast.makeText(this, "Загрузка фото...", Toast.LENGTH_SHORT).show();
        MediaManager.get().upload(uri)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String imageUrl = (String) resultData.get("secure_url");
                        updateTaskCompletionWithPhoto(imageUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Toast.makeText(MainActivity.this, "Ошибка загрузки: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void updateTaskCompletionWithPhoto(String imageUrl) {
        DatabaseReference taskRef = FirebaseDatabase.getInstance().getReference()
                .child("spaces")
                .child(currentProcessingTaskTargetUid)
                .child("tasks")
                .child(currentProcessingTaskId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("completed", true);
        updates.put("imageUrl", imageUrl);

        taskRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Исправлен путь: запрашиваем обновленный таск из "spaces", а не из "Users"
                FirebaseDatabase.getInstance().getReference()
                        .child("spaces")
                        .child(currentProcessingTaskTargetUid)
                        .child("tasks")
                        .child(currentProcessingTaskId)
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    Task updatedTask = snapshot.getValue(Task.class);
                                    if (updatedTask != null && updatedTask.getAssignedBy() != null) {
                                        FirebaseDatabase.getInstance().getReference()
                                                .child("sent_tasks")
                                                .child(updatedTask.getAssignedBy())
                                                .child(updatedTask.getId())
                                                .setValue(updatedTask);
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });

                Toast.makeText(MainActivity.this, "Задача выполнена!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
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