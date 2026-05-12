package com.tresabhi.gyrocursor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

public class EyeCandyActivity extends Activity implements SensorEventListener {
    private static final String TAG = "GYRO_DEBUG_EYE";

    private SensorManager sensorManager;
    private Sensor gyroscope;

    private Handler hidHandler;
    private HandlerThread hidThread;

    private volatile float gyroX = 0f;
    private volatile float gyroY = 0f;

    private TextView xView;
    private TextView yView;
    private TextView zView;

    private boolean running = false;
    private Runnable mouseLoop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.eye_candy);

        xView = findViewById(R.id.xValue);
        yView = findViewById(R.id.yValue);
        zView = findViewById(R.id.zValue);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        hidThread = new HandlerThread("hid-thread");
        hidThread.start();

        hidHandler = new Handler(hidThread.getLooper());
    }

    @Override
    protected void onResume() {
        super.onResume();

        sensorManager.registerListener(
                this,
                gyroscope,
                SensorManager.SENSOR_DELAY_GAME
        );

        startMouseLoop();
    }

    @Override
    protected void onPause() {
        super.onPause();

        sensorManager.unregisterListener(this);
        stopMouseLoop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopMouseLoop();

        if (hidHandler != null) {
            hidHandler.removeCallbacksAndMessages(null);
        }

        if (hidThread != null) {
            hidThread.quitSafely();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        gyroX = event.values[0];
        gyroY = event.values[1];

        runOnUiThread(() -> {
            xView.setText(String.valueOf(gyroX));
            yView.setText(String.valueOf(gyroY));
            zView.setText(String.valueOf(event.values[2]));
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void startMouseLoop() {

        if (running) return;

        running = true;

        mouseLoop = new Runnable() {
            @Override
            public void run() {

                if (!running) return;

                sendGyroMouseReport();

                hidHandler.postDelayed(this, 8);
            }
        };

        hidHandler.post(mouseLoop);
    }

    private void stopMouseLoop() {

        running = false;

        if (hidHandler != null && mouseLoop != null) {
            hidHandler.removeCallbacks(mouseLoop);
        }
    }

    private void sendGyroMouseReport() {

        if (MainActivity.hid == null || MainActivity.target == null) {
            return;
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        float sensitivity = 12f;

        int dx = (int) (gyroY * sensitivity);
        int dy = (int) (gyroX * sensitivity);

        dx = clamp(dx, -15, 15);
        dy = clamp(dy, -15, 15);

//        dx *= -1;
        dy *= -1;

        if (dx == 0 && dy == 0) {
            return;
        }

        MainActivity.hid.sendReport(
                MainActivity.target,
                (byte) 0x01,
                new byte[]{
                        0x00,
                        (byte) dx,
                        (byte) dy
                }
        );
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}