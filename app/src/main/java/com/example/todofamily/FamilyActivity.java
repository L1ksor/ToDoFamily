package com.example.todofamily;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.todofamily.databinding.ActivityFamilyBinding;
import com.example.todofamily.databinding.ItemGroupBinding;
import com.example.todofamily.utils.WrapContentLinearLayoutManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FamilyActivity extends AppCompatActivity {

    private ActivityFamilyBinding binding;
    private DatabaseReference userGroupsRef;
    private DatabaseReference groupsRef;
    private String currentUserId;
    
    private GroupAdapter adapter;
    private List<Group> groupList = new ArrayList<>();
    private Set<String> expandedGroupIds = new HashSet<>();
    private android.content.SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFamilyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences("ToDoFamilyPrefs", MODE_PRIVATE);
        expandedGroupIds = new HashSet<>(prefs.getStringSet("expandedGroups", new HashSet<>()));

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Мои Группы");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userGroupsRef = FirebaseDatabase.getInstance().getReference().child("Users").child(currentUserId).child("groups");
        groupsRef = FirebaseDatabase.getInstance().getReference().child("Groups");

        setupRecyclerView();
        loadUserGroups();

        binding.addGroupFab.setOnClickListener(v -> showAddGroupOptions());
    }

    private void setupRecyclerView() {
        adapter = new GroupAdapter(groupList);
        binding.groupsRv.setLayoutManager(new WrapContentLinearLayoutManager(this));
        binding.groupsRv.setAdapter(adapter);
    }

    private void loadUserGroups() {
        userGroupsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                groupList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot groupSnap : snapshot.getChildren()) {
                        String groupId = groupSnap.getKey();
                        fetchGroupDetails(groupId);
                    }
                } else {
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchGroupDetails(String groupId) {
        groupsRef.child(groupId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Group group = snapshot.getValue(Group.class);
                    if (group != null) {
                        group.setId(snapshot.getKey());
                        
                        boolean exists = false;
                        for (int i = 0; i < groupList.size(); i++) {
                            if (groupList.get(i).getId().equals(group.getId())) {
                                groupList.set(i, group);
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) groupList.add(group);
                        adapter.notifyDataSetChanged();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showAddGroupOptions() {
        String[] options = {"Создать группу", "Присоединиться по ID"};
        new AlertDialog.Builder(this)
                .setTitle("Добавить группу")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showCreateGroupDialog();
                    else showJoinGroupDialog();
                })
                .show();
    }

    private void showCreateGroupDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        com.google.android.material.textfield.TextInputLayout til = view.findViewById(R.id.dialog_til);
        EditText et = view.findViewById(R.id.dialog_et);
        til.setHint("Название группы");

        new AlertDialog.Builder(this)
                .setTitle("Создать группу")
                .setView(view)
                .setPositiveButton("Создать", (dialog, which) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) createGroup(name);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void createGroup(String name) {
        String groupId = generateShortId();
        Map<String, Boolean> members = new HashMap<>();
        members.put(currentUserId, true);

        Group newGroup = new Group(groupId, name, currentUserId, members);
        groupsRef.child(groupId).setValue(newGroup);
        
        userGroupsRef.child(groupId).setValue(true);
        showCustomToast("Группа создана!");
    }

    private void showJoinGroupDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_text, null);
        com.google.android.material.textfield.TextInputLayout til = view.findViewById(R.id.dialog_til);
        EditText et = view.findViewById(R.id.dialog_et);
        til.setHint("ID группы");

        new AlertDialog.Builder(this)
                .setTitle("Присоединиться к группе")
                .setView(view)
                .setPositiveButton("Войти", (dialog, which) -> {
                    String groupId = et.getText().toString().trim();
                    if (!groupId.isEmpty()) joinGroup(groupId);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void joinGroup(String groupId) {
        groupsRef.child(groupId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    groupsRef.child(groupId).child("members").child(currentUserId).setValue(true);
                    userGroupsRef.child(groupId).setValue(true);
                    showCustomToast("Вы присоединились к группе!");
                } else {
                    showCustomToast("Группа с таким ID не найдена");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private String generateShortId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder sb = new StringBuilder();
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        while (sb.length() < 8) {
            int index = rnd.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    private void saveExpandedState() {
        prefs.edit().putStringSet("expandedGroups", expandedGroupIds).apply();
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

    private static class GroupViewHolder extends RecyclerView.ViewHolder {
        ItemGroupBinding binding;
        public GroupViewHolder(ItemGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private class GroupAdapter extends RecyclerView.Adapter<GroupViewHolder> {
        private List<Group> groups;

        public GroupAdapter(List<Group> groups) {
            this.groups = groups;
        }

        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemGroupBinding itemBinding = ItemGroupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new GroupViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            Group group = groups.get(position);
            holder.binding.groupNameTv.setText(group.getName());
            holder.binding.groupIdTv.setText("ID: " + group.getId());

            boolean isExpanded = expandedGroupIds.contains(group.getId());
            holder.binding.nestedMembersRv.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            holder.binding.toggleMembersBtn.setRotation(isExpanded ? 180 : 0);

            if (isExpanded) {
                setupNestedMembers(holder.binding.nestedMembersRv, group);
            }

            holder.binding.toggleMembersBtn.setOnClickListener(v -> {
                if (expandedGroupIds.contains(group.getId())) {
                    expandedGroupIds.remove(group.getId());
                } else {
                    expandedGroupIds.add(group.getId());
                }
                saveExpandedState();
                notifyItemChanged(position);
            });

            holder.binding.groupHeaderLayout.setOnClickListener(v -> {
                Intent intent = new Intent(FamilyActivity.this, GroupDetailsActivity.class);
                intent.putExtra("groupId", group.getId());
                intent.putExtra("groupName", group.getName());
                startActivity(intent);
            });
        }

        private void setupNestedMembers(RecyclerView rv, Group group) {
            List<Member> members = new ArrayList<>();
            MemberAdapter memberAdapter = new MemberAdapter(members, true); // True for nested
            rv.setLayoutManager(new WrapContentLinearLayoutManager(FamilyActivity.this));
            rv.setAdapter(memberAdapter);

            for (String uid : group.getMembers().keySet()) {
                if (uid.equals(currentUserId)) continue; // Пропускаем себя

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
                                    members.add(member);
                                    memberAdapter.notifyDataSetChanged();
                                }
                            }
                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
            }
        }

        @Override
        public int getItemCount() {
            return groups.size();
        }
    }

    private class MemberAdapter extends RecyclerView.Adapter<MemberViewHolder> {
        private List<Member> members;
        private boolean isNested;

        public MemberAdapter(List<Member> members, boolean isNested) {
            this.members = members;
            this.isNested = isNested;
        }

        @NonNull
        @Override
        public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            com.example.todofamily.databinding.ItemMemberBinding itemBinding = 
                com.example.todofamily.databinding.ItemMemberBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new MemberViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
            Member member = members.get(position);
            holder.binding.memberNameTv.setText(member.getUsername());
            holder.binding.memberEmailTv.setText(member.getEmail());

            if (member.getAvatarUrl() != null && !member.getAvatarUrl().isEmpty()) {
                Glide.with(FamilyActivity.this)
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
                            String myUid = FirebaseAuth.getInstance().getUid();
                            for (DataSnapshot taskSnap : snapshot.getChildren()) {
                                Boolean completed = taskSnap.child("completed").getValue(Boolean.class);
                                String assignedBy = taskSnap.child("assignedBy").getValue(String.class);
                                if (completed != null && !completed && myUid != null && myUid.equals(assignedBy)) {
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
        com.example.todofamily.databinding.ItemMemberBinding binding;
        public MemberViewHolder(com.example.todofamily.databinding.ItemMemberBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}