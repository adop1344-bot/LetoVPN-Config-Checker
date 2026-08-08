package com.letovpn.checker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import java.util.HashSet;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS = "letovpn_prefs";
    public static final String KEY_METHOD = "method";
    public static final String KEY_COUNT = "count";
    public static final String KEY_THREADS = "threads";
    public static final String KEY_SOURCES = "custom_sources";

    private LinearLayout sourcesList;
    private Set<String> customSources;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // Theme radios
        MaterialRadioButton themeDark = findViewById(R.id.themeDark);
        MaterialRadioButton themeLight = findViewById(R.id.themeLight);
        MaterialRadioButton themeCustom = findViewById(R.id.themeCustom);
        MaterialRadioButton themeDynamic = findViewById(R.id.themeDynamic);

        String theme = prefs.getString(ThemeHelper.KEY_THEME, ThemeHelper.THEME_DARK);
        switch (theme) {
            case ThemeHelper.THEME_LIGHT: themeLight.setChecked(true); break;
            case ThemeHelper.THEME_CUSTOM: themeCustom.setChecked(true); break;
            case ThemeHelper.THEME_DYNAMIC: themeDynamic.setChecked(true); break;
            default: themeDark.setChecked(true); break;
        }

        // Method radios
        MaterialRadioButton methodFast = findViewById(R.id.methodFast);
        MaterialRadioButton methodBalanced = findViewById(R.id.methodBalanced);
        MaterialRadioButton methodAccurate = findViewById(R.id.methodAccurate);
        MaterialRadioButton methodPrecise = findViewById(R.id.methodPrecise);

        String method = prefs.getString(KEY_METHOD, "BALANCED");
        switch (method) {
            case "FAST": methodFast.setChecked(true); break;
            case "ACCURATE": methodAccurate.setChecked(true); break;
            case "PRECISE": methodPrecise.setChecked(true); break;
            default: methodBalanced.setChecked(true); break;
        }

        // Sliders
        Slider countSlider = findViewById(R.id.countSlider);
        TextView countValue = findViewById(R.id.countValue);
        Slider threadsSlider = findViewById(R.id.threadsSlider);
        TextView threadsValue = findViewById(R.id.threadsValue);

        int count = prefs.getInt(KEY_COUNT, 50);
        countSlider.setValue(count);
        countValue.setText(count == 0 ? "Все" : String.valueOf(count));

        int threads = prefs.getInt(KEY_THREADS, 12);
        threadsSlider.setValue(threads);
        threadsValue.setText(String.valueOf(threads));

        countSlider.addOnChangeListener((s, value, fromUser) -> {
            int v = (int) value;
            countValue.setText(v == 0 ? "Все" : String.valueOf(v));
        });

        threadsSlider.addOnChangeListener((s, value, fromUser) -> {
            threadsValue.setText(String.valueOf((int) value));
        });

        // Custom sources
        sourcesList = findViewById(R.id.sourcesList);
        TextInputEditText sourceInput = findViewById(R.id.sourceInput);
        MaterialButton btnAddSource = findViewById(R.id.btnAddSource);

        customSources = new HashSet<>(prefs.getStringSet(KEY_SOURCES, new HashSet<>()));
        refreshSourcesList();

        btnAddSource.setOnClickListener(v -> {
            String url = sourceInput.getText() != null ? sourceInput.getText().toString().trim() : "";
            if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                Toast.makeText(this, "Введите корректный URL", Toast.LENGTH_SHORT).show();
                return;
            }
            customSources.add(url);
            sourceInput.setText("");
            refreshSourcesList();
            Toast.makeText(this, "Источник добавлен", Toast.LENGTH_SHORT).show();
        });

        // Telegram
        findViewById(R.id.btnTelegram).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/letovpn_free")));
        });

        // Save
        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> {
            String selectedTheme = ThemeHelper.THEME_DARK;
            if (themeLight.isChecked()) selectedTheme = ThemeHelper.THEME_LIGHT;
            else if (themeCustom.isChecked()) selectedTheme = ThemeHelper.THEME_CUSTOM;
            else if (themeDynamic.isChecked()) selectedTheme = ThemeHelper.THEME_DYNAMIC;

            String selectedMethod = "BALANCED";
            if (methodFast.isChecked()) selectedMethod = "FAST";
            else if (methodAccurate.isChecked()) selectedMethod = "ACCURATE";
            else if (methodPrecise.isChecked()) selectedMethod = "PRECISE";

            prefs.edit()
                    .putString(ThemeHelper.KEY_THEME, selectedTheme)
                    .putString(KEY_METHOD, selectedMethod)
                    .putInt(KEY_COUNT, (int) countSlider.getValue())
                    .putInt(KEY_THREADS, (int) threadsSlider.getValue())
                    .putStringSet(KEY_SOURCES, customSources)
                    .apply();

            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
            // Restart main to apply theme
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }

    private void refreshSourcesList() {
        sourcesList.removeAllViews();
        for (String url : customSources) {
            TextView tv = new TextView(this);
            tv.setText("• " + (url.length() > 50 ? url.substring(0, 47) + "..." : url));
            tv.setTextSize(13f);
            tv.setPadding(0, 8, 0, 8);
            tv.setOnLongClickListener(v -> {
                customSources.remove(url);
                refreshSourcesList();
                Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
                return true;
            });
            sourcesList.addView(tv);
        }
        if (customSources.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет своих источников (долгое нажатие — удалить)");
            empty.setTextSize(13f);
            empty.setAlpha(0.6f);
            sourcesList.addView(empty);
        }
    }
}
