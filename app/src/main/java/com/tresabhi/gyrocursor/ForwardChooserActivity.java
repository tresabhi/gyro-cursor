package com.tresabhi.gyrocursor;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

public class ForwardChooserActivity extends Activity {
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
                // handle press
            });

            container.addView(btn);
        }
    }
}