package com.tresabhi.gyrocursor;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

public class EyeCandyActivity extends Activity {
    private static final String TAG = "GYRO_DEBUG_EYE";

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

        Button debug = findViewById(R.id.debugButton);
        debug.setOnClickListener(v -> {
            Log.d(TAG, "DEBUG BUTTON CLICKED");
            moveMouseSquare();
        });
    }

    private void moveMouseSquare() {
        Log.d(TAG, "moveMouseSquare entered");

        if (MainActivity.hid == null || MainActivity.target == null) {
            Log.e(TAG, "HID not ready: sharedHid=" + MainActivity.hid
                    + " sharedTarget=" + MainActivity.target);
            return;
        }

        Handler handler = new Handler();

        byte[][] moves = new byte[][]{
                new byte[]{0x00, 20, 0},
                new byte[]{0x00, 0, 20},
                new byte[]{0x00, -20, 0},
                new byte[]{0x00, 0, -20}
        };

        for (int dir = 0; dir < moves.length; dir++) {
            byte dx = moves[dir][1];
            byte dy = moves[dir][2];

            for (int step = 0; step < 5; step++) {

                int delay = (dir * 5 + step) * 8;

                handler.postDelayed(() -> {

                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }

                    MainActivity.hid.sendReport(
                            MainActivity.target,
                            (byte) 0x01,
                            new byte[]{
                                    0x00,
                                    (byte)(dx / 5),
                                    (byte)(dy / 5)
                            }
                    );

                }, delay);
            }
        }
    }
}