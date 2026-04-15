package com.tresabhi.gyrocursor;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class ConnectingActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.connecting);

        String name = getIntent().getStringExtra("device_name");

        TextView deviceName = findViewById(R.id.deviceName);
        deviceName.setText(name != null ? name : "Unknown device");
    }
}