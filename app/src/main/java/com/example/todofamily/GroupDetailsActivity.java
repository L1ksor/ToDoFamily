package com.example.todofamily;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.todofamily.databinding.ActivityGroupDetailsBinding;
import com.example.todofamily.databinding.ItemMemberBinding;
import com.example.todofamily.utils.WrapContentLinearLayoutManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class GroupDetailsActivity extends AppCompatActivity {

    private ActivityGroupDetailsBinding binding;
    private String groupId;
    private String groupName;
    private String currentUserId;
    
    private MemberAdapter adapter;
    private List<Member> memberList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGroupDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        groupId = getIntent().getStringExtra("groupId");
        groupName = getIntent().getStringExtra("groupName");
        currentUserId = FirebaseAuth.getInstance().getUid();

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(groupName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.groupNameDetailsTv.setText(groupName);
        binding.groupIdDetailsTv.setText("ID Группы: " + groupId);

        setupRecyclerView();
        loadGroupMembers();

        binding.copyGroupIdBtn.setOnClickListener(v -> copyGroupId());
        binding.leaveGroupBtn.setOnClickListener(v -> showLeaveGroupDialog());
    }

    private void setupRecyclerView() {
        adapter = new MemberAdapter(memberList);
        binding.groupMembersRv.setLayoutManager(new WrapContentLinearLayoutManager(this));
        binding.groupMembersRv.setAdapter(adapter);
    }

    private void loadGroupMembers() {
        FirebaseDatabase.getInstance().getReference().child("Groups").child(groupId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Group group = snapshot.getValue(Group.class);
                            if (group != null) {
                                binding.groupNameDetailsTv.setText(group.getName());
                                // Проверка на админа
                                if (currentUserId != null && currentUserId.equals(group.getAdmin())) {
                                    binding.editGroupNameBtn.setVisibility(View.VISIBLE);
                                    binding.editGroupNameBtn.setOnClickListener(v -> showEditGroupNameDialog(group.getName()));
                                } else {
                                    binding.editGroupNameBtn.setVisibility(View.GONE);
                                }
                            }
                        }

                        DataSnapshot membersSnap = snapshot.child("members");
                        memberList.clear();
                        for (DataSnapshot memberSnap : membersSnap.getChildren()) {
                            String uid = memberSnap.getKey();
                            if (uid != null && uid.equals(currentUserId)) continue; // Пропускаем себя
                            fetchMemberDetails(uid);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void showEditGroupNameDialog(String currentName) {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        com.google.android.material.textfield.TextInputLayout til = view.findViewById(R.id.dialog_til);
        android.widget.EditText et = view.findViewById(R.id.dialog_et);
        til.setHint("Новое название группы");
        et.setText(currentName);

        new AlertDialog.Builder(this)
                .setTitle("Редактировать группу")
                .setView(view)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    String newName = et.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateGroupName(newName);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateGroupName(String newName) {
        FirebaseDatabase.getInstance().getReference().child("Groups").child(groupId).child("name").setValue(newName)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        showCustomToast("Название обновлено");
                    }
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
                            
                            boolean exists = false;
                            for (int i = 0; i < memberList.size(); i++) {
                                if (memberList.get(i).getUid().equals(uid)) {
                                    memberList.set(i, member);
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) memberList.add(member);
                            adapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void copyGroupId() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Group ID", groupId);
        clipboard.setPrimaryClip(clip);
        showCustomToast("ID скопирован");
    }

    private void showLeaveGroupDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Покинуть группу?")
                .setMessage("Вы больше не будете видеть участников этой группы.")
                .setPositiveButton("Выйти", (dialog, which) -> leaveGroup())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void leaveGroup() {
        FirebaseDatabase.getInstance().getReference().child("Groups").child(groupId).child("members").child(currentUserId).removeValue();
        FirebaseDatabase.getInstance().getReference().child("Users").child(currentUserId).child("groups").child(groupId).removeValue()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        showCustomToast("Вы покинули группу");
                        finish();
                    }
                });
    }

    private void showCustomToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL, 0, 250);
        toast.show();
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
            ItemMemberBinding itemBinding = ItemMemberBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new MemberViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
            Member member = members.get(position);
            holder.binding.memberNameTv.setText(member.getUsername());
            holder.binding.memberEmailTv.setText(member.getEmail());

            if (member.getAvatarUrl() != null && !member.getAvatarUrl().isEmpty()) {
                Glide.with(GroupDetailsActivity.this)
                        .load(member.getAvatarUrl())
                        .circleCrop()
                        .into(holder.binding.memberAvatarIv);
            } else {
                holder.binding.memberAvatarIv.setImageResource(android.R.drawable.ic_menu_gallery);
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
                            for (DataSnapshot taskSnap : snapshot.getChildren()) {
                                Boolean completed = taskSnap.child("completed").getValue(Boolean.class);
                                String assignedBy = taskSnap.child("assignedBy").getValue(String.class);
                                if (completed != null && !completed && currentUserId.equals(assignedBy)) {
                                    count++;
                                }
                            }
                            if (count > 0) {
                                holder.binding.taskCountTv.setVisibility(View.VISIBLE);
                                holder.binding.taskCountTv.setText(String.valueOf(count));
                            } else {
                                holder.binding.taskCountTv.setVisibility(View.GONE);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(GroupDetailsActivity.this, MemberTasksActivity.class);
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
        ItemMemberBinding binding;
        public MemberViewHolder(ItemMemberBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}