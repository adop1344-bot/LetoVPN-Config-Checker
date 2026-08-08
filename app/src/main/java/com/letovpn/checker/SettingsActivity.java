package com.letovpn.checker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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

    private LinearLayout sourcesList;
    private Set<String> customSources;

    private MaterialRadioButton themeDark, themeLight, themeCustom, themeDynamic;
    private MaterialRadioButton methodTcp, methodTcpDns, methodProxyGet, methodDeep, methodXray;
    private Slider countSlider, threadsSlider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        themeDark = findViewById(R.id.themeDark);
        themeLight = findViewById(R.id.themeLight);
        themeCustom = findViewById(R.id.themeCustom);
        themeDynamic = findViewById(R.id.themeDynamic);

        methodTcp = findViewById(R.id.methodTcp);
        methodTcpDns = findViewById(R.id.methodTcpDns);
        methodProxyGet = findViewById(R.id.methodProxyGet);
        methodDeep = findViewById(R.id.methodDeep);
        methodXray = findViewById(R.id.methodXray);

        countSlider = findViewById(R.id.countSlider);
        threadsSlider = findViewById(R.id.threadsSlider);
        TextView countValue = findViewById(R.id.countValue);
        TextView threadsValue = findViewById(R.id.threadsValue);

        // Load
        String theme = Prefs.getTheme(this);
        switch (theme) {
            case ThemeHelper.THEME_LIGHT: themeLight.setChecked(true); break;
            case ThemeHelper.THEME_CUSTOM: themeCustom.setChecked(true); break;
            case ThemeHelper.THEME_DYNAMIC: themeDynamic.setChecked(true); break;
            default: themeDark.setChecked(true); break;
        }

        String method = Prefs.getMethod(this);
        switch (method) {
            case "TCP": methodTcp.setChecked(true); break;
            case "PROXY_GET": methodProxyGet.setChecked(true); break;
            case "DEEP": methodDeep.setChecked(true); break;
            case "XRAY": methodXray.setChecked(true); break;
            default: methodTcpDns.setChecked(true); break;
        }

        int count = Prefs.getCount(this);
        countSlider.setValue(count);
        countValue.setText(count == 0 ? "Все" : String.valueOf(count));

        int threads = Math.min(100, Math.max(4, Prefs.getThreads(this)));
        threadsSlider.setValue(threads);
        threadsValue.setText(String.valueOf(threads));

        countSlider.addOnChangeListener((s, value, fromUser) -> {
            int v = (int) value;
            countValue.setText(v == 0 ? "Все" : String.valueOf(v));
            if (fromUser) Prefs.setCount(this, v);
        });

        threadsSlider.addOnChangeListener((s, value, fromUser) -> {
            threadsValue.setText(String.valueOf((int) value));
            if (fromUser) Prefs.setThreads(this, (int) value);
        });

        sourcesList = findViewById(R.id.sourcesList);
        TextInputEditText sourceInput = findViewById(R.id.sourceInput);
        customSources = Prefs.getSources(this);
        refreshSourcesList();

        findViewById(R.id.btnAddSource).setOnClickListener(v -> {
            String url = sourceInput.getText() != null ? sourceInput.getText().toString().trim() : "";
            if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                Toast.makeText(this, "Введите корректный URL", Toast.LENGTH_SHORT).show();
                return;
            }
            customSources.add(url);
            Prefs.setSources(this, customSources);
            sourceInput.setText("");
            refreshSourcesList();
            Toast.makeText(this, "Источник добавлен", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnTelegram).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/letovpn_free"))));

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> {
            saveAll();
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveAll(); // автосохранение при выходе
    }

    private void saveAll() {
        String selectedTheme = ThemeHelper.THEME_DARK;
        if (themeLight.isChecked()) selectedTheme = ThemeHelper.THEME_LIGHT;
        else if (themeCustom.isChecked()) selectedTheme = ThemeHelper.THEME_CUSTOM;
        else if (themeDynamic.isChecked()) selectedTheme = ThemeHelper.THEME_DYNAMIC;

        String selectedMethod = "TCP_DNS";
        if (methodTcp.isChecked()) selectedMethod = "TCP";
        else if (methodProxyGet.isChecked()) selectedMethod = "PROXY_GET";
        else if (methodDeep.isChecked()) selectedMethod = "DEEP";
        else if (methodXray.isChecked()) selectedMethod = "XRAY";

        Prefs.setTheme(this, selectedTheme);
        Prefs.setMethod(this, selectedMethod);
        Prefs.setCount(this, (int) countSlider.getValue());
        Prefs.setThreads(this, (int) threadsSlider.getValue());
        Prefs.setSources(this, customSources);
    }

    private void refreshSourcesList() {
        sourcesList.removeAllViews();
        if (customSources.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет своих источников\n(долгое нажатие — удалить)");
            empty.setTextSize(13f);
            empty.setAlpha(0.6f);
            sourcesList.addView(empty);
            return;
        }
        for (String url : new HashSet<>(customSources)) {
            TextView tv = new TextView(this);
            String shown = url.length() > 55 ? url.substring(0, 52) + "..." : url;
            tv.setText("• " + shown);
            tv.setTextSize(13f);
            tv.setPadding(0, 10, 0, 10);
            tv.setOnLongClickListener(v -> {
                customSources.remove(url);
                Prefs.setSources(this, customSources);
                refreshSourcesList();
                Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
                return true;
            });
            sourcesList.addView(tv);
        }
    }
}
