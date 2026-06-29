package com.example.todofamily;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.todofamily.databinding.ActivityFamilyBinding;
import com.example.todofamily.utils.WrapContentLinearLayoutManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FamilyActivity extends AppCompatActivity {

    private ActivityFamilyBinding binding;
    private DatabaseReference userRef;
    private DatabaseReference familyRef;
    private String currentUserId;
    private String currentFamilyId;
    
    private MemberAdapter adapter;
    private List<Member> memberList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFamilyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Моя Группа");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userRef = FirebaseDatabase.getInstance().getReference().child("Users").child(currentUserId);
        familyRef = FirebaseDatabase.getInstance().getReference().child("Families");

        setupRecyclerView();
        checkFamilyStatus();

        binding.createFamilyBtn.setOnClickListener(v -> showCreateFamilyDialog());
        binding.joinFamilyBtn.setOnClickListener(v -> showJoinFamilyDialog());
        binding.copyIdBtn.setOnClickListener(v -> copyFamilyId());
        
        binding.leaveFamilyBtn.setOnClickListener(v -> showLeaveFamilyDialog());

        // Удаляем старую кнопку выданных задач
        binding.viewSentTasksBtn.setVisibility(View.GONE);
    }

    private void setupRecyclerView() {
        adapter = new MemberAdapter(memberList);
        binding.membersRv.setLayoutManager(new WrapContentLinearLayoutManager(this));
        binding.membersRv.setAdapter(adapter);
    }

    private void checkFamilyStatus() {
        userRef.child("familyId").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    currentFamilyId = snapshot.getValue(String.class);
                    showInFamilyLayout();
                } else {
                    showNoFamilyLayout();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showNoFamilyLayout() {
        binding.noFamilyLayout.setVisibility(View.VISIBLE);
        binding.inFamilyLayout.setVisibility(View.GONE);
    }

    private void showInFamilyLayout() {
        binding.noFamilyLayout.setVisibility(View.GONE);
        binding.inFamilyLayout.setVisibility(View.VISIBLE);
        binding.familyIdTv.setText("ID: " + currentFamilyId);

        familyRef.child(currentFamilyId).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    binding.familyNameTitle.setText(snapshot.getValue(String.class));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
        
        loadFamilyMembers();
    }

    private void loadFamilyMembers() {
        familyRef.child(currentFamilyId).child("members").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                memberList.clear();
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
                                    snapshot.child("email").getValue(String.class),
                                    snapshot.child("avatarUrl").getValue(String.class)
                            );
                            
                            // Проверяем на дубликаты перед добавлением
                            boolean exists = false;
                            for (int i = 0; i < memberList.size(); i++) {
                                if (memberList.get(i).getUid().equals(uid)) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                memberList.add(member);
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void showCreateFamilyDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        EditText et = view.findViewById(R.id.dialog_et);
        et.setHint("Название группы");

        new AlertDialog.Builder(this)
                .setTitle("Создать группу")
                .setView(view)
                .setPositiveButton("Создать", (dialog, which) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) {
                        createFamily(name);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showCustomToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL, 0, 250);
        toast.show();
    }

    private void createFamily(String name) {
        String familyId = generateShortId();
        familyRef.child(familyId).child("name").setValue(name);
        familyRef.child(familyId).child("members").child(currentUserId).setValue(true);
        
        userRef.child("familyId").setValue(familyId);
        showCustomToast("Группа создана!");
    }

    private String generateShortId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        while (sb.length() < 8) {
            int index = (int) (rnd.nextFloat() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    private void showJoinFamilyDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        EditText et = view.findViewById(R.id.dialog_et);
        et.setHint("ID группы");

        new AlertDialog.Builder(this)
                .setTitle("Присоединиться к группе")
                .setView(view)
                .setPositiveButton("Войти", (dialog, which) -> {
                    String familyId = et.getText().toString().trim();
                    if (!familyId.isEmpty()) {
                        joinFamily(familyId);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void joinFamily(String familyId) {
        familyRef.child(familyId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    familyRef.child(familyId).child("members").child(currentUserId).setValue(true);
                    userRef.child("familyId").setValue(familyId);
                    showCustomToast("Вы присоединились к группе!");
                } else {
                    showCustomToast("Группа с таким ID не найдена");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void copyFamilyId() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Family ID", currentFamilyId);
        clipboard.setPrimaryClip(clip);
        showCustomToast("ID скопирован в буфер обмена");
    }

    private void showLeaveFamilyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Покинуть группу?")
                .setMessage("Вы больше не будете видеть участников и общие задачи.")
                .setPositiveButton("Выйти", (dialog, which) -> leaveFamily())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void leaveFamily() {
        if (currentFamilyId == null) return;

        // 1. Удаляем себя из списка участников семьи
        familyRef.child(currentFamilyId).child("members").child(currentUserId).removeValue();

        // 2. Сбрасываем familyId у пользователя
        userRef.child("familyId").removeValue().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                currentFamilyId = null;
                showCustomToast("Вы покинули группу");
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private class MemberAdapter extends RecyclerView.Adapter<MemberViewHolder> {
        private List<Member> members;

        public MemberAdapter(List<Member> members) {
            this.members = members;
        }

        @NonNull
        @Override
        public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
            return new MemberViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
            Member member = members.get(position);
            holder.nameTv.setText(member.getUsername());
            holder.emailTv.setText(member.getEmail());

            if (member.getAvatarUrl() != null && !member.getAvatarUrl().isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(member.getAvatarUrl())
                        .circleCrop()
                        .into(holder.avatarIv);
            } else {
                holder.avatarIv.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            // Слушаем количество активных задач участника, выданных ТЕКУЩИМ пользователем
            FirebaseDatabase.getInstance().getReference()
                    .child("spaces")
                    .child(member.getUid())
                    .child("tasks")
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            int count = 0;
                            String myUid = FirebaseAuth.getInstance().getUid();
                            for (DataSnapshot taskSnap : snapshot.getChildren()) {
                                Boolean completed = taskSnap.child("completed").getValue(Boolean.class);
                                String assignedBy = taskSnap.child("assignedBy").getValue(String.class);
                                
                                if (completed != null && !completed && myUid != null && myUid.equals(assignedBy)) {
                                    count++;
                                }
                            }
                            if (count > 0) {
                                holder.taskCountTv.setVisibility(View.VISIBLE);
                                holder.taskCountTv.setText(String.valueOf(count));
                            } else {
                                holder.taskCountTv.setVisibility(View.GONE);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(FamilyActivity.this, MemberTasksActivity.class);
                intent.putExtra("memberUid", member.getUid());
                intent.putExtra("memberName", member.getUsername());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return members.size();
        }
    }

    private static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView nameTv, emailTv, taskCountTv;
        ImageView avatarIv;
        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTv = itemView.findViewById(R.id.member_name_tv);
            emailTv = itemView.findViewById(R.id.member_email_tv);
            taskCountTv = itemView.findViewById(R.id.task_count_tv);
            avatarIv = itemView.findViewById(R.id.member_avatar_iv);
        }
    }
}