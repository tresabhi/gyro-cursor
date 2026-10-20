package com.tresabhi.gyrocursor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

public class ForwardChooserActivity extends Activity {
    private float[] selectedVector = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.forward_chooser);

        LinearLayout container = findViewById(R.id.buttonContainer);

        String[] labels = {"+X", "-X", "+Y", "-Y", "+Z", "-Z"};

        for (String label : labels) {
            Button btn = new Button(this);
            btn.setText(label);

            btn.setOnClickListener(v -> {
                selectedVector = toVector(label);

                Intent intent = new Intent(ForwardChooserActivity.this, EyeCandyActivity.class);
                intent.putExtra("vx", selectedVector[0]);
                intent.putExtra("vy", selectedVector[1]);
                intent.putExtra("vz", selectedVector[2]);

                startActivity(intent);
                finish();
            });

            container.addView(btn);
        }
    }

    private float[] toVector(String label) {
        switch (label) {
            case "+X":
                return new float[]{1f, 0f, 0f};
            case "-X":
                return new float[]{-1f, 0f, 0f};
            case "+Y":
                return new float[]{0f, 1f, 0f};
            case "-Y":
                return new float[]{0f, -1f, 0f};
            case "+Z":
                return new float[]{0f, 0f, 1f};
            case "-Z":
                return new float[]{0f, 0f, -1f};
            default:
                return new float[]{0f, 0f, 0f};
        }
    }
}