package com.example.webrtcmeetingroom;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    private EditText et_signal;
    private EditText et_port;
    private EditText et_room;

    private EditText edit_test_wss;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
        et_room = findViewById(R.id.et_room);
    }

    private void initView() {


    }

    public void JoinRoom(View view) {
        WebRTCManager.getInstance()
                .connect(this,et_room.getText().toString());

    }
}