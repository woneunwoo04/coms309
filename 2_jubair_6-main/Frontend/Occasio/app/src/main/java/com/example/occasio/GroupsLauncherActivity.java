package com.example.occasio;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

public class GroupsLauncherActivity extends Activity {
    
    private Button searchGroupsBtn;
    private Button myGroupsBtn;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_groups_launcher);
        
        initializeViews();
        setupClickListeners();
    }
    
    private void initializeViews() {
        searchGroupsBtn = findViewById(R.id.search_groups_btn);
        myGroupsBtn = findViewById(R.id.my_groups_btn);
    }
    
    private void setupClickListeners() {
        searchGroupsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(GroupsLauncherActivity.this, com.example.occasio.friends.SearchGroupsActivityVolley.class);
            startActivity(intent);
        });
        
        myGroupsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(GroupsLauncherActivity.this, com.example.occasio.friends.MyGroupsActivity.class);
            startActivity(intent);
        });
    }
}
