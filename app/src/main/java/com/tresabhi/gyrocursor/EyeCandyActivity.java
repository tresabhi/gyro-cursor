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

import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;

public class EyeCandyActivity extends Activity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor gyroscope;

    private Handler hidHandler;
    private HandlerThread hidThread;

    private float carryU = 0f;
    private float carryV = 0f;

    private TextView xView, yView, zView;
    private boolean hasBluetoothPermission = false;

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

        hasBluetoothPermission = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hidThread != null) {
            hidThread.quitSafely();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final float gyroX = event.values[0];
        final float gyroY = event.values[1];
        final float gyroZ = event.values[2];

        hidHandler.post(() -> processAndSendHid(gyroX, gyroY, gyroZ));

        runOnUiThread(() -> {
            xView.setText(String.valueOf(gyroX));
            yView.setText(String.valueOf(gyroY));
            zView.setText(String.valueOf(gyroZ));
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void processAndSendHid(float gx, float gy, float gz) {
        if (!hasBluetoothPermission || MainActivity.hid == null || MainActivity.target == null) {
            return;
        }

        float sensitivity = 1f;
        float rawY = gx * sensitivity;
        float rawZ = gz * sensitivity;

        carryU += rawZ;
        carryV += rawY;

        int du = (int) carryU;
        int dv = (int) carryV;

        carryU -= du;
        carryV -= dv;

        du *= -1;
        dv *= -1;

        if (du == 0 && dv == 0) {
            return;
        }

        MainActivity.hid.sendReport(
                MainActivity.target,
                (byte) 0x01,
                new byte[]{0x00, (byte) du, (byte) dv}
        );
    }
}