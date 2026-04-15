package com.tresabhi.gyrocursor;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class EyeCandyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.eye_candy);

        float vx = getIntent().getFloatExtra("vx", 0f);
        float vy = getIntent().getFloatExtra("vy", 0f);
        float vz = getIntent().getFloatExtra("vz", 0f);

        TextView x = findViewById(R.id.xValue);
        TextView y = findViewById(R.id.yValue);
        TextView z = findViewById(R.id.zValue);

        x.setText(String.valueOf(vx));
        y.setText(String.valueOf(vy));
        z.setText(String.valueOf(vz));
    }
}