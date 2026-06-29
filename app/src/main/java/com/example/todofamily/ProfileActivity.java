package com.example.todofamily;

import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
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
    private Uri avatarUri;

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    uploadAvatarToCloudinary(avatarUri);
                }
            });

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    uploadAvatarToCloudinary(result.getData().getData());
                }
            });

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

        binding.profileImage.setOnClickListener(v -> showAvatarSelectionDialog());

        binding.logoutBtn.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void showCustomToast(String message) {
        android.widget.Toast toast = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT);
        toast.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL, 0, 250);
        toast.show();
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

                                String avatarUrl = snapshot.child("avatarUrl").getValue(String.class);
                                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                    Glide.with(ProfileActivity.this)
                                            .load(avatarUrl)
                                            .circleCrop()
                                            .into(binding.profileImage);
                                }
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            showCustomToast("Ошибка загрузки данных");
                        }
                    });
        }
    }

    private void showAvatarSelectionDialog() {
        String[] options = {"Камера", "Галерея"};
        new AlertDialog.Builder(this)
                .setTitle("Сменить фото профиля")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) launchCamera();
                    else launchGallery();
                })
                .show();
    }

    private void launchCamera() {
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            showCustomToast("Ошибка создания файла");
        }
        if (photoFile != null) {
            avatarUri = FileProvider.getUriForFile(this, "com.example.todofamily.fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, avatarUri);
            cameraLauncher.launch(intent);
        }
    }

    private void launchGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "AVATAR_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(null);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void uploadAvatarToCloudinary(Uri uri) {
        showCustomToast("Загрузка фото...");
        MediaManager.get().upload(uri)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}
                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}
                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String imageUrl = (String) resultData.get("secure_url");
                        saveAvatarUrlToDatabase(imageUrl);
                    }
                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        showCustomToast("Ошибка: " + error.getDescription());
                    }
                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    private void saveAvatarUrlToDatabase(String url) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseDatabase.getInstance().getReference().child("Users").child(uid).child("avatarUrl").setValue(url)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            showCustomToast("Аватар обновлен");
                            Glide.with(ProfileActivity.this).load(url).circleCrop().into(binding.profileImage);
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

                if (model.isPhotoRequired()) {
                    holder.binding.photoRequiredIc.setVisibility(View.VISIBLE);
                } else {
                    holder.binding.photoRequiredIc.setVisibility(View.GONE);
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