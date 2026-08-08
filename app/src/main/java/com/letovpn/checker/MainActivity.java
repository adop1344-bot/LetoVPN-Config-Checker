package com.letovpn.checker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private static final String SOURCES_URL =
            "https://raw.githubusercontent.com/adop1344-bot/LetoVPN_free/refs/heads/main/sources.txt";

    private MaterialButton btnStart, btnSave, btnCopyTop;
    private ProgressBar progressBar;
    private TextView statusText;
    private RecyclerView recyclerView;
    private ConfigAdapter adapter;

    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isRunning = false;

    private final List<ConfigItem> workingConfigs = Collections.synchronizedList(new ArrayList<>());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        btnSave = findViewById(R.id.btnSave);
        btnCopyTop = findViewById(R.id.btnCopyTop);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        recyclerView = findViewById(R.id.recyclerView);

        adapter = new ConfigAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        LayoutAnimationController anim =
                AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down);
        recyclerView.setLayoutAnimation(anim);

        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        btnStart.setOnClickListener(v -> {
            if (isRunning) stopCheck();
            else startCheck();
        });

        btnSave.setOnClickListener(v -> saveAllToFile());
        btnCopyTop.setOnClickListener(v -> copyTop10());
    }

    private void startCheck() {
        isRunning = true;
        workingConfigs.clear();
        adapter.clear();
        btnStart.setText(R.string.stop);
        btnSave.setEnabled(false);
        btnCopyTop.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        statusText.setText("Загрузка источников...");

        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        int threads = prefs.getInt(SettingsActivity.KEY_THREADS, 12);

        if (executor != null && !executor.isShutdown()) executor.shutdownNow();
        executor = Executors.newFixedThreadPool(Math.max(2, Math.min(100, threads)));

        executor.execute(() -> {
            try {
                List<String> sources = new ArrayList<>();
                try {
                    sources.addAll(fetchLines(SOURCES_URL));
                } catch (Exception ignored) {}

                Set<String> custom = prefs.getStringSet(SettingsActivity.KEY_SOURCES, new HashSet<>());
                if (custom != null) sources.addAll(custom);

                List<String> allConfigs = new ArrayList<>();
                for (String src : sources) {
                    if (!isRunning) break;
                    try {
                        for (String line : fetchLines(src.trim())) {
                            line = line.trim();
                            if (line.startsWith("vless://") || line.startsWith("vmess://")
                                    || line.startsWith("trojan://")) {
                                allConfigs.add(line);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                List<String> unique = new ArrayList<>(new java.util.LinkedHashSet<>(allConfigs));

                int maxCount = prefs.getInt(SettingsActivity.KEY_COUNT, 50);
                String methodStr = prefs.getString(SettingsActivity.KEY_METHOD, "TCP_DNS");
                ConfigChecker.Method method;
                try {
                    method = ConfigChecker.Method.valueOf(methodStr);
                } catch (Exception e) {
                    method = ConfigChecker.Method.TCP_DNS;
                }

                if (maxCount > 0 && unique.size() > maxCount) {
                    unique = unique.subList(0, maxCount);
                }

                final int total = unique.size();
                final String methodLabel = ConfigChecker.methodName(method);
                mainHandler.post(() -> {
                    statusText.setText("Найдено: " + total + " | " + methodLabel + " | Потоков: " + threads);
                    progressBar.setMax(Math.max(1, total));
                });

                if (total == 0) {
                    mainHandler.post(this::finishCheck);
                    return;
                }

                AtomicInteger done = new AtomicInteger(0);
                final ConfigChecker.Method m = method;

                for (String raw : unique) {
                    if (!isRunning) break;

                    executor.execute(() -> {
                        ConfigItem item = new ConfigItem(raw);
                        long latency = ConfigChecker.test(item, m);
                        item.latency = latency;
                        item.working = latency > 0;

                        int current = done.incrementAndGet();

                        mainHandler.post(() -> {
                            if (item.working) {
                                workingConfigs.add(item);
                                Collections.sort(workingConfigs,
                                        (a, b) -> Long.compare(a.latency, b.latency));
                                adapter.setItems(new ArrayList<>(workingConfigs));
                                recyclerView.scheduleLayoutAnimation();
                            }
                            progressBar.setProgress(current);
                            statusText.setText("Проверено: " + current + "/" + total
                                    + " | Рабочих: " + workingConfigs.size());

                            if (current >= total || !isRunning) {
                                finishCheck();
                            }
                        });
                    });
                }

            } catch (Exception e) {
                mainHandler.post(() -> {
                    statusText.setText("Ошибка: " + e.getMessage());
                    finishCheck();
                });
            }
        });
    }

    private void stopCheck() {
        isRunning = false;
        finishCheck();
    }

    private void finishCheck() {
        isRunning = false;
        btnStart.setText(R.string.start_check);
        progressBar.setVisibility(View.GONE);
        boolean has = !workingConfigs.isEmpty();
        btnSave.setEnabled(has);
        btnCopyTop.setEnabled(has);
        if (has) {
            statusText.setText("Готово! Рабочих: " + workingConfigs.size());
        } else {
            String t = statusText.getText().toString();
            if (t.startsWith("Проверено") || t.startsWith("Найдено")) {
                statusText.setText("Готово. Рабочих конфигов нет");
            }
        }
    }

    private List<String> fetchLines(String urlStr) throws Exception {
        List<String> lines = new ArrayList<>();
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("User-Agent", "LetoVPN-Checker/1.4");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private void saveAllToFile() {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            File file = new File(dir, "letovpn_working_configs.txt");

            try (FileWriter writer = new FileWriter(file)) {
                for (ConfigItem item : workingConfigs) {
                    writer.write(item.raw + "\n");
                }
            }
            Toast.makeText(this, "Сохранено: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyTop10() {
        StringBuilder sb = new StringBuilder();
        int count = Math.min(10, workingConfigs.size());
        for (int i = 0; i < count; i++) {
            sb.append(workingConfigs.get(i).raw).append("\n");
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("top10", sb.toString()));
        Toast.makeText(this, "Скопировано топ-" + count, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (executor != null) executor.shutdownNow();
    }
}
