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
//                new byte[]{0x00, 0, 20},
//                new byte[]{0x00, -20, 0},
//                new byte[]{0x00, 0, -20}
        };

        for (int i = 0; i < moves.length; i++) {
            int index = i;
            handler.postDelayed(() -> {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }

                int state = MainActivity.hid.getConnectionState(MainActivity.target);
                Log.d(TAG, "Connection state = " + state);

                Log.d(TAG, "Sending HID move index=" + index);

                MainActivity.hid.sendReport(
                        MainActivity.target,
                        (byte) 0x00,
                        moves[index]
                );
            }, i * 200);
        }
    }
}