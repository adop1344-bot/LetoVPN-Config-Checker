package com.letovpn.checker;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
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
    private MaterialRadioButton methodTcp, methodTcpDns, methodProxyGet, methodDeep, methodXray, methodXraySpeed;
    private Slider countSlider, threadsSlider, stopAfterSlider;
    private SwitchCompat onlyVlessSwitch;

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
        methodXraySpeed = findViewById(R.id.methodXraySpeed);

        countSlider = findViewById(R.id.countSlider);
        threadsSlider = findViewById(R.id.threadsSlider);
        stopAfterSlider = findViewById(R.id.stopAfterSlider);
        onlyVlessSwitch = findViewById(R.id.onlyVlessSwitch);

        TextView countValue = findViewById(R.id.countValue);
        TextView threadsValue = findViewById(R.id.threadsValue);
        TextView stopAfterValue = findViewById(R.id.stopAfterValue);
        TextView versionText = findViewById(R.id.versionText);

        versionText.setText("LetoVPN Checker v1.6");

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
            case "XRAY_SPEED": methodXraySpeed.setChecked(true); break;
            default: methodTcpDns.setChecked(true); break;
        }

        int count = Prefs.getCount(this);
        countSlider.setValue(count);
        countValue.setText(count == 0 ? "Все" : String.valueOf(count));

        int threads = Math.min(100, Math.max(4, Prefs.getThreads(this)));
        threadsSlider.setValue(threads);
        threadsValue.setText(String.valueOf(threads));

        int stopAfter = Prefs.getStopAfter(this);
        stopAfterSlider.setValue(stopAfter);
        stopAfterValue.setText(stopAfter == 0 ? "Без лимита" : String.valueOf(stopAfter));

        onlyVlessSwitch.setChecked(Prefs.getOnlyVless(this));

        countSlider.addOnChangeListener((s, value, fromUser) -> {
            int v = (int) value;
            countValue.setText(v == 0 ? "Все" : String.valueOf(v));
            if (fromUser) Prefs.setCount(this, v);
        });
        threadsSlider.addOnChangeListener((s, value, fromUser) -> {
            threadsValue.setText(String.valueOf((int) value));
            if (fromUser) Prefs.setThreads(this, (int) value);
        });
        stopAfterSlider.addOnChangeListener((s, value, fromUser) -> {
            int v = (int) value;
            stopAfterValue.setText(v == 0 ? "Без лимита" : String.valueOf(v));
            if (fromUser) Prefs.setStopAfter(this, v);
        });
        onlyVlessSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                Prefs.setOnlyVless(this, isChecked));

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
            Prefs.setSourceEnabled(this, url, true);
            sourceInput.setText("");
            refreshSourcesList();
            Toast.makeText(this, "Источник добавлен", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnTelegram).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/letovpn_free"))));

        findViewById(R.id.btnExportSub).setOnClickListener(v -> {
            // placeholder — export from main; here just tip
            Toast.makeText(this, "Экспорт подписки — на главном экране после проверки", Toast.LENGTH_LONG).show();
        });

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
        saveAll();
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
        else if (methodXraySpeed.isChecked()) selectedMethod = "XRAY_SPEED";

        Prefs.setTheme(this, selectedTheme);
        Prefs.setMethod(this, selectedMethod);
        Prefs.setCount(this, (int) countSlider.getValue());
        Prefs.setThreads(this, (int) threadsSlider.getValue());
        Prefs.setStopAfter(this, (int) stopAfterSlider.getValue());
        Prefs.setOnlyVless(this, onlyVlessSwitch.isChecked());
        Prefs.setSources(this, customSources);
    }

    private void refreshSourcesList() {
        sourcesList.removeAllViews();

        // Системный источник
        addSourceRow(Prefs.SYSTEM_SOURCE, "Системный (LetoVPN sources.txt)", true);

        if (customSources.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Нет своих источников\n(долгое нажатие — удалить свой)");
            empty.setTextSize(13f);
            empty.setAlpha(0.6f);
            empty.setPadding(0, 12, 0, 0);
            sourcesList.addView(empty);
        } else {
            for (String url : new HashSet<>(customSources)) {
                String label = url.length() > 45 ? url.substring(0, 42) + "..." : url;
                addSourceRow(url, label, false);
            }
        }
    }

    private void addSourceRow(String url, String label, boolean system) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        SwitchCompat sw = new SwitchCompat(this);
        sw.setChecked(Prefs.isSourceEnabled(this, url));
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                Prefs.setSourceEnabled(this, url, isChecked));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13f);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tv.setPadding(12, 0, 8, 0);

        if (!system) {
            tv.setOnLongClickListener(v -> {
                customSources.remove(url);
                Prefs.setSources(this, customSources);
                Set<String> disabled = Prefs.getDisabledSources(this);
                disabled.remove(url);
                Prefs.setDisabledSources(this, disabled);
                refreshSourcesList();
                Toast.makeText(this, "Удалено", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        row.addView(sw);
        row.addView(tv);
        sourcesList.addView(row);
    }
}
