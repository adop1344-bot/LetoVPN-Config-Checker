package com.letovpn.checker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.slider.Slider;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS = "letovpn_prefs";
    public static final String KEY_MODE = "mode";
    public static final String KEY_COUNT = "count";
    public static final String KEY_THREADS = "threads";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        MaterialRadioButton radioTcp = findViewById(R.id.radioTcp);
        MaterialRadioButton radioProxy = findViewById(R.id.radioProxy);
        Slider countSlider = findViewById(R.id.countSlider);
        TextView countValue = findViewById(R.id.countValue);
        Slider threadsSlider = findViewById(R.id.threadsSlider);
        TextView threadsValue = findViewById(R.id.threadsValue);
        MaterialButton btnTelegram = findViewById(R.id.btnTelegram);
        MaterialButton btnSave = findViewById(R.id.btnSaveSettings);

        String mode = prefs.getString(KEY_MODE, "TCP");
        if ("PROXY_GET".equals(mode)) {
            radioProxy.setChecked(true);
        } else {
            radioTcp.setChecked(true);
        }

        int count = prefs.getInt(KEY_COUNT, 50);
        countSlider.setValue(count);
        countValue.setText(String.valueOf(count));

        int threads = prefs.getInt(KEY_THREADS, 12);
        threadsSlider.setValue(threads);
        threadsValue.setText(String.valueOf(threads));

        countSlider.addOnChangeListener((s, value, fromUser) -> {
            countValue.setText(String.valueOf((int) value));
        });

        threadsSlider.addOnChangeListener((s, value, fromUser) -> {
            threadsValue.setText(String.valueOf((int) value));
        });

        btnTelegram.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/letovpn_free"));
            startActivity(i);
        });

        btnSave.setOnClickListener(v -> {
            String selectedMode = radioProxy.isChecked() ? "PROXY_GET" : "TCP";
            int selectedCount = (int) countSlider.getValue();
            int selectedThreads = (int) threadsSlider.getValue();

            prefs.edit()
                    .putString(KEY_MODE, selectedMode)
                    .putInt(KEY_COUNT, selectedCount)
                    .putInt(KEY_THREADS, selectedThreads)
                    .apply();

            finish();
        });
    }
}
